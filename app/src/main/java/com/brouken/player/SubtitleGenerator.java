package com.brouken.player;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegKitConfig;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;
import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizerResult;
import com.k2fsa.sherpa.onnx.OfflineStream;
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig;
import com.k2fsa.sherpa.onnx.SileroVadModelConfig;
import com.k2fsa.sherpa.onnx.SpeechSegment;
import com.k2fsa.sherpa.onnx.Vad;
import com.k2fsa.sherpa.onnx.VadModelConfig;
import com.k2fsa.sherpa.onnx.WaveReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class SubtitleGenerator {
    private static final String TAG = "SubtitleGenerator";
    private static final int SAMPLE_RATE = 16000;
    private static final int CHUNK_MS = 8000;
    private static final int MAX_SPEECH_SAMPLES = SAMPLE_RATE * 8;
    private static final String CACHE_VERSION = "packs-v4-";
    private static final java.util.concurrent.ThreadPoolExecutor EXECUTOR = new java.util.concurrent.ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new java.util.concurrent.LinkedBlockingQueue<>(), task -> new Thread(() -> {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        task.run();
    }, "OfflineCaptions"));
    private static final java.util.Set<Job> JOBS = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<Job, Boolean>());
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface SubtitleCallback {
        void onCues(List<SubtitleTimeline.Cue> cues, long rangeStartMs, long rangeEndMs, int percent);
        void onSubtitlesReady(Uri subtitleUri);
        void onFailed(String reason);
        default void onCancelled() { }
    }

    public static final class Job {
        private final CaptionPack pack;
        private final Context context;
        private final Uri media;
        private final SubtitleCallback callback;
        private final String token = java.util.UUID.randomUUID().toString();
        private final CountDownLatch released = new CountDownLatch(1);
        private final boolean force, background;
        private volatile boolean retryAfterCancel;
        private Runnable terminal;
        private Runnable task;
        private Job(Context context, Uri media, CaptionPack pack, boolean force, boolean background, SubtitleCallback callback) {
            this.context=context; this.media=media; this.pack=pack; this.force=force; this.background=background; this.callback=callback;
        }
        public boolean isCancelling() { return cancelled && released.getCount()>0; }
        public void cancelForRetry() { retryAfterCancel=true; cancel(); }
        private synchronized void finish() {
            if(released.getCount()==0)return;
            if(cancelled) {
                new CaptionWorkStore(context).update(media,pack,token,retryAfterCancel ? CaptionWorkStore.Status.QUEUED : CaptionWorkStore.Status.CANCELLED,0,"");
                MAIN.post(callback::onCancelled);
            }
            JOBS.remove(this); released.countDown();
            if (!cancelled && terminal != null) deliver(terminal);
        }
        private volatile boolean cancelled;
        private volatile long sessionId = -1;
        private volatile long playbackPositionMs;

        public void updatePlaybackPosition(long positionMs) {
            playbackPositionMs = Math.max(0, positionMs);
        }

        public synchronized void cancel() {
            if (released.getCount()==0) { cancelled=true;
                new CaptionWorkStore(context).update(media,pack,token,CaptionWorkStore.Status.CANCELLED,0,"");
                MAIN.post(callback::onCancelled); return; }
            cancelled = true;
            long activeSession = sessionId;
            if (activeSession >= 0) FFmpegKit.cancel(activeSession);
            if (task != null && EXECUTOR.remove(task)) finish();
        }

        private void checkCancelled() {
            if (cancelled) throw new CancellationException();
        }

        private void deliver(Runnable callback) {
            MAIN.post(() -> { if (!cancelled) callback.run(); });
        }
    }

    public static File getSubtitlesCacheDir(Context context) {
        File dir = new File(context.getCacheDir(), "subtitles");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("Cannot create subtitle cache");
        return dir;
    }

    public static File getCachedSubtitleFile(Context context, Uri uri, String path) {
        return getCachedSubtitleFile(context, uri, path, CaptionPackages.get(context).selected());
    }

    public static File getCachedSubtitleFile(Context context, Uri uri, String path, CaptionPack pack) {
        String identity = pack.cacheIdentity() + ":" + (uri != null ? uri.toString() : String.valueOf(path));
        if (path != null) {
            File media = new File(path);
            identity += ":" + media.length() + ":" + media.lastModified();
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder();
            for (byte b : digest) hash.append(String.format(Locale.US, "%02x", b));
            return new File(getSubtitlesCacheDir(context), CACHE_VERSION + hash + ".srt");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean isObsoleteCache(Uri uri) {
        return isGeneratedCache(uri)
                && !new File(uri.getPath()).getName().startsWith(CACHE_VERSION);
    }

    public static boolean isGeneratedCache(Uri uri) {
        return uri != null && "file".equals(uri.getScheme()) && uri.getPath() != null
                && uri.getPath().contains("/cache/subtitles/");
    }

    public static File findLocalSubtitle(String videoPath) {
        if (videoPath == null || videoPath.isEmpty()) return null;
        File video = new File(videoPath);
        File parent = video.getParentFile();
        if (parent == null) return null;
        String name = video.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        for (String extension : new String[]{".srt", ".vtt", ".ass", ".ssa", ".en.srt"}) {
            File subtitle = new File(parent, name + extension);
            if (subtitle.isFile() && subtitle.length() > 0) return subtitle;
        }
        return null;
    }

    public static String resolveRealPath(Context context, Uri uri) {
        if (uri == null) return null;
        if ("file".equals(uri.getScheme())) return uri.getPath();
        if (!"content".equals(uri.getScheme())) return null;
        try (android.database.Cursor cursor = context.getContentResolver().query(uri,
                new String[]{android.provider.MediaStore.Video.Media.DATA}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(android.provider.MediaStore.Video.Media.DATA);
                String path = column >= 0 ? cursor.getString(column) : null;
                if (path != null && new File(path).isFile()) return path;
            }
        } catch (RuntimeException e) {
            Log.d(TAG, "Using content URI for audio extraction", e);
        }
        return null;
    }

    public static Job generateSubtitlesAsync(Context context, Uri mediaUri, String mediaPath,
                                              long positionMs, SubtitleCallback callback) {
        return generateSubtitlesAsync(context,mediaUri,mediaPath,positionMs,CaptionPackages.get(context).selected(),false,false,callback);
    }

    public static Job generateSubtitlesAsync(Context context, Uri mediaUri, String mediaPath, long positionMs,
                                              CaptionPack pack, boolean force, boolean background, SubtitleCallback callback) {
        Context application=context.getApplicationContext();
        CaptionWorkStore.initialize(application);
        if(!background) {
            CaptionAutoJobs.removeQueued(application,mediaUri,pack);
            for(Job old:JOBS)if(old.background)old.cancelForRetry();
        }
        Job job=new Job(application,mediaUri,pack,force,background,callback);
        new CaptionWorkStore(application).begin(mediaUri,pack,job.token);
        job.updatePlaybackPosition(positionMs);
        job.task=() -> generate(application,mediaUri,mediaPath,job,callback);
        JOBS.add(job);
        EXECUTOR.execute(job.task);
        return job;
    }

    public static void cancelAndAwait(Uri media) throws IOException, InterruptedException {
        java.util.List<Job> jobs=new ArrayList<>();
        for(Job job:JOBS)if(media.equals(job.media)) { jobs.add(job);job.cancel(); }
        for(Job job:jobs)if(!job.released.await(60,TimeUnit.SECONDS))throw new IOException("Subtitle generation is still stopping. Please retry deletion.");
    }

    public static void preemptBackground() { for(Job job:JOBS)if(job.background)job.cancelForRetry(); }

    private static void generate(Context context, Uri uri, String path, Job job, SubtitleCallback callback) {
        File workDir = null;
        OfflineRecognizer recognizer = null;
        Vad vad = null;
        CaptionPackages.Lease models = null;
        try {
            job.checkCancelled();
            if (new CaptionWorkStore(context).isDeleting(uri)) { job.cancelled=true; throw new CancellationException(); }
            new CaptionWorkStore(context).update(uri,job.pack,job.token,CaptionWorkStore.Status.GENERATING,0,"");
            if (uri == null || !("content".equals(uri.getScheme()) || "file".equals(uri.getScheme()))) {
                throw new IOException("Offline captions require a video stored on this device");
            }
            String resolved = path != null && new File(path).isFile() ? path : resolveRealPath(context, uri);
            File cached = getCachedSubtitleFile(context, uri, resolved, job.pack);
            if (!job.force && cached.isFile() && cached.length() > 0) {
                cached.setLastModified(System.currentTimeMillis());
                new CaptionWorkStore(context).update(uri,job.pack,job.token,CaptionWorkStore.Status.READY,100,"");
                job.terminal = () -> callback.onSubtitlesReady(Uri.fromFile(cached));
                return;
            }
            trimCache(context);
            long durationMs = readDuration(context, uri);
            String input = resolved != null ? resolved : FFmpegKitConfig.getSafParameterForRead(context, uri);
            workDir = new File(getSubtitlesCacheDir(context), "job-" + System.nanoTime());
            if (!workDir.mkdirs()) throw new IOException("Cannot create caption workspace");
            CaptionPackages packages = CaptionPackages.get(context);
            models = packages.acquire(job.pack);
            packages.verifyInstalled(models);
            job.checkCancelled();
            recognizer = createRecognizer(models, context);
            vad = createVad(models);
            SubtitleTimeline timeline = new SubtitleTimeline();
            int chunkCount = (int) ((durationMs + CHUNK_MS - 1) / CHUNK_MS);
            boolean[] done = new boolean[chunkCount];
            File checkpoint=new File(cached.getPath()+".partial");
            if(job.force)checkpoint.delete();
            int restored=0;
            for(CaptionCheckpoint.Chunk saved:CaptionCheckpoint.read(checkpoint,chunkCount)) {
                done[saved.index]=true;restored++;timeline.addAll(saved.cues);
                long savedStart=(long)saved.index*CHUNK_MS;
                final int progress=restored*100/chunkCount;
                job.deliver(()->callback.onCues(saved.cues,savedStart,Math.min(durationMs,savedStart+CHUNK_MS),progress));
            }
            for (int count = restored; count < chunkCount; count++) {
                job.checkCancelled();
                int chunk = chooseChunk(done, job.playbackPositionMs);
                long startMs = (long) chunk * CHUNK_MS;
                long lengthMs = Math.min(CHUNK_MS, durationMs - startMs);
                File wave = new File(workDir, "audio.wav");
                extractAudio(input, wave, startMs, lengthMs, job);
                float[] samples = WaveReader.Companion.readWave(wave.getAbsolutePath()).getSamples();
                List<SubtitleTimeline.Cue> cues = transcribe(vad, recognizer, samples, startMs, job);
                timeline.addAll(cues);
                CaptionCheckpoint.append(checkpoint,chunk,cues);
                done[chunk] = true;
                int percent = (count + 1) * 100 / chunkCount;
                new CaptionWorkStore(context).update(uri,job.pack,job.token,CaptionWorkStore.Status.GENERATING,percent,"");
                job.deliver(() -> callback.onCues(cues, startMs, startMs + lengthMs, percent));
            }
            job.checkCancelled();
            if (timeline.isEmpty()) throw new IOException("No speech detected. Try another video.");
            File completed = new File(workDir, "completed.srt");
            writeSrt(completed, timeline.snapshot());
            job.checkCancelled();
            if (!completed.renameTo(cached)) throw new IOException("Could not save generated captions");
            checkpoint.delete();
            trimCache(context);
            if (job.force) CaptionLibrary.removeSaved(context,uri,job.pack);
            new CaptionWorkStore(context).update(uri,job.pack,job.token,CaptionWorkStore.Status.READY,100,"");
            Log.i(TAG, "Saved " + timeline.snapshot().size() + " timed cues");
            job.terminal = () -> callback.onSubtitlesReady(Uri.fromFile(cached));
        } catch (CancellationException ignored) {
            Log.d(TAG, "Caption job cancelled");
        } catch (Exception | LinkageError e) {
            Log.e(TAG, "Caption generation failed", e);
            String message = e.getMessage() != null ? e.getMessage() : "Unable to create offline captions";
            new CaptionWorkStore(context).update(uri,job.pack,job.token,CaptionWorkStore.Status.FAILED,0,message);
            job.terminal = () -> callback.onFailed(message);
        } finally {
            try {
            try { if (vad != null) vad.release(); } catch (RuntimeException | LinkageError e) { Log.w(TAG,"VAD cleanup",e); }
            try { if (recognizer != null) recognizer.release(); } catch (RuntimeException | LinkageError e) { Log.w(TAG,"Recognizer cleanup",e); }
            if (models != null) models.close();
            if (workDir != null) {
                File[] files = workDir.listFiles();
                if (files != null) for (File file : files) file.delete();
                workDir.delete();
            }
            } finally { job.finish(); }
        }
    }

    private static int chooseChunk(boolean[] done, long positionMs) {
        int current = (int) Math.min(done.length - 1, positionMs / CHUNK_MS);
        for (int i = current; i < done.length; i++) if (!done[i]) return i;
        for (int i = 0; i < current; i++) if (!done[i]) return i;
        throw new IllegalStateException("All audio already processed");
    }

    private static void trimCache(Context context) {
        File directory = getSubtitlesCacheDir(context);
        CacheFiles.trim(directory, ".srt", 128L*1024*1024,
                System.currentTimeMillis()-30L*24*60*60*1000);
        CacheFiles.trim(directory,".partial",32L*1024*1024,System.currentTimeMillis()-7L*24*60*60*1000);
        File[] abandoned = directory.listFiles(file -> file.isDirectory() && file.getName().startsWith("job-")
                && file.lastModified() < System.currentTimeMillis()-24L*60*60*1000);
        if (abandoned != null) for (File job : abandoned) { CacheFiles.clearFlat(job); job.delete(); }
    }

    public static void clearCache(Context context, Runnable finished) {
        Context app=context.getApplicationContext();
        EXECUTOR.execute(() -> {
            File directory=getSubtitlesCacheDir(app);
            CacheFiles.clearFlat(directory);
            File[] jobs=directory.listFiles(file -> file.isDirectory() && file.getName().startsWith("job-"));
            if(jobs!=null)for(File job:jobs) { CacheFiles.clearFlat(job); job.delete(); }
            new CaptionWorkStore(app).reconcileReady();
            MAIN.post(finished);
        });
    }

    private static long readDuration(Context context, Uri uri) throws IOException {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long duration = value != null ? Long.parseLong(value) : 0;
            if (duration <= 0) throw new IOException("Video duration is unavailable");
            return duration;
        } finally {
            retriever.release();
        }
    }

    private static void extractAudio(String input, File wave, long startMs, long lengthMs, Job job)
            throws IOException, InterruptedException {
        CountDownLatch finished = new CountDownLatch(1);
        String[] arguments = {"-y", "-ss", seconds(startMs), "-i", input, "-t", seconds(lengthMs),
                "-map", "0:a:0", "-vn", "-acodec", "pcm_s16le", "-ac", "1", "-ar", "16000", wave.getAbsolutePath()};
        FFmpegSession session = FFmpegKit.executeWithArgumentsAsync(arguments, result -> finished.countDown());
        job.sessionId = session.getSessionId();
        try {
            while (!finished.await(100, TimeUnit.MILLISECONDS)) job.checkCancelled();
            job.checkCancelled();
            if (!ReturnCode.isSuccess(session.getReturnCode()) || !wave.isFile() || wave.length() <= 44) {
                throw new IOException("Cannot read this video's audio track");
            }
        } finally {
            if (job.cancelled) {
                FFmpegKit.cancel(session.getSessionId());
                finished.await(); // A cancelled job is released only after its own extraction actually exits.
            }
            job.sessionId = -1;
        }
    }

    private static String seconds(long millis) {
        return String.format(Locale.US, "%.3f", millis / 1000.0);
    }

    private static OfflineRecognizer createRecognizer(CaptionPackages.Lease models, Context context) {
        Prefs prefs = new Prefs(context);
        OfflineWhisperModelConfig whisper = new OfflineWhisperModelConfig();
        whisper.setEncoder(new File(models.directory, "encoder.onnx").getAbsolutePath());
        whisper.setDecoder(new File(models.directory, "decoder.onnx").getAbsolutePath());

        String lang = prefs.captionLanguage;
        if (lang == null || "auto".equalsIgnoreCase(lang)) {
            lang = ""; // Empty string instructs Whisper to auto-detect the spoken language!
        }
        whisper.setLanguage(lang);

        String task = prefs.captionTranslate ? "translate" : "transcribe";
        whisper.setTask(task);

        whisper.setEnableTokenTimestamps(models.pack.aligned);
        whisper.setEnableSegmentTimestamps(false);
        OfflineModelConfig model = new OfflineModelConfig();
        model.setWhisper(whisper);
        model.setTokens(new File(models.directory, "tokens.txt").getAbsolutePath());
        model.setNumThreads(Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() - 1)));
        model.setProvider("cpu");
        model.setModelType("whisper");
        OfflineRecognizerConfig config = new OfflineRecognizerConfig();
        config.setFeatConfig(new FeatureConfig(SAMPLE_RATE, 80, 0.0f));
        config.setModelConfig(model);
        config.setDecodingMethod("greedy_search");
        return new OfflineRecognizer(null, config);
    }

    private static List<SubtitleTimeline.Cue> transcribe(Vad vad, OfflineRecognizer recognizer,
                                                         float[] samples, long startMs, Job job) {
        List<SubtitleTimeline.Cue> cues = new ArrayList<>();
        if (samples == null || samples.length == 0) return cues;
        vad.reset();
        for (int i = 0; i < samples.length; i += 512) {
            job.checkCancelled();
            float[] window = new float[512];
            System.arraycopy(samples, i, window, 0, Math.min(512, samples.length - i));
            vad.acceptWaveform(window);
            drainSpeech(vad, recognizer, startMs, samples.length, cues, job);
        }
        vad.flush();
        drainSpeech(vad, recognizer, startMs, samples.length, cues, job);
        return cues;
    }

    private static Vad createVad(CaptionPackages.Lease models) {
        SileroVadModelConfig silero = new SileroVadModelConfig();
        silero.setModel(new File(models.directory, "vad.onnx").getAbsolutePath());
        silero.setThreshold(0.4f);
        silero.setMinSilenceDuration(0.3f);
        silero.setMinSpeechDuration(0.2f);
        silero.setMaxSpeechDuration(models.pack.aligned ? 8.0f : 4.0f);
        silero.setWindowSize(512);
        VadModelConfig config = new VadModelConfig();
        config.setSileroVadModelConfig(silero);
        config.setSampleRate(SAMPLE_RATE);
        config.setNumThreads(1);
        config.setProvider("cpu");
        return new Vad(null, config);
    }

    private static void drainSpeech(Vad vad, OfflineRecognizer recognizer, long offsetMs, int sampleCount,
                                    List<SubtitleTimeline.Cue> cues, Job job) {
        while (!vad.empty()) {
            SpeechSegment speech = vad.front();
            float[] audio = speech.getSamples();
            int speechStart = speech.getStart();
            vad.pop();
            if (audio == null) continue;
            // Bound recognition independently of VAD so a backend cannot create a minute-long cue.
            int available = Math.min(audio.length, sampleCount - speechStart);
            int maximum = job.pack.aligned ? MAX_SPEECH_SAMPLES : SAMPLE_RATE * 4;
            for (int start = 0; start < available; start += maximum) {
                job.checkCancelled();
                int length = Math.min(maximum, available - start);
                if (length < SAMPLE_RATE / 10) continue;
                float[] bounded = Arrays.copyOfRange(audio, start, start + length);
                OfflineStream stream = recognizer.createStream();
                try {
                    stream.acceptWaveform(bounded, SAMPLE_RATE);
                    recognizer.decode(stream);
                    job.checkCancelled();
                    OfflineRecognizerResult result = recognizer.getResult(stream);
                    String text = result.getText().trim();
                    if (text.isEmpty() || text.equals(".")) continue;
                    long absoluteStart = offsetMs + (speechStart + start) * 1000L / SAMPLE_RATE;
                    cues.addAll(SubtitleTimeline.fromRecognition(text, result.getTokens(), result.getTimestamps(),
                            result.getDurations(), absoluteStart, length * 1000L / SAMPLE_RATE));
                } finally {
                    stream.release();
                }
            }
        }
    }

    private static void writeSrt(File output, List<SubtitleTimeline.Cue> cues) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(output))) {
            for (int i = 0; i < cues.size(); i++) {
                SubtitleTimeline.Cue cue = cues.get(i);
                writer.write((i + 1) + "\n" + SubtitleTimeline.timestamp(cue.startMs) + " --> "
                        + SubtitleTimeline.timestamp(cue.endMs) + "\n" + cue.text + "\n\n");
            }
        }
    }

}
