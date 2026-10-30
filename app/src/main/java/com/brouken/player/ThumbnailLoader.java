package com.brouken.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.LruCache;
import android.util.Size;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThumbnailLoader {

    public static class ThumbnailResult {
        public final Bitmap bitmap;
        public final String durationText;
        public final String resolution;

        public ThumbnailResult(Bitmap bitmap, String durationText, String resolution) {
            this.bitmap = bitmap;
            this.durationText = durationText;
            this.resolution = resolution;
        }
    }

    private static ThumbnailLoader sInstance;
    private final LruCache<String, ThumbnailResult> mMemoryCache;
    private final ExecutorService mExecutor = Executors.newFixedThreadPool(2);
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final java.util.LinkedHashMap<String, Request> pending = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, Request> active = new java.util.HashMap<>();
    private final Object diskLock = new Object();
    private volatile int generation;
    private int workers;

    private static final class Target {
        final java.lang.ref.WeakReference<ImageView> image;
        final java.lang.ref.WeakReference<TextView> duration, resolution, info;
        final String baseInfo;
        Target(ImageView image, TextView duration, TextView resolution, TextView info, String baseInfo) {
            this.image=new java.lang.ref.WeakReference<>(image);
            this.duration=new java.lang.ref.WeakReference<>(duration);
            this.resolution=new java.lang.ref.WeakReference<>(resolution);
            this.info=new java.lang.ref.WeakReference<>(info);
            this.baseInfo=baseInfo;
        }
    }
    private static final class Request {
        final Context context;
        final Uri uri;
        final String key;
        final String diskKey;
        final int generation;
        final java.util.List<Target> targets=new java.util.ArrayList<>();
        Request(Context context, Uri uri, String key, int generation) {
            this.context=context; this.uri=uri; this.key=key; this.generation=generation;
            diskKey=key.substring(0,key.lastIndexOf('|'));
        }
    }
    private ThumbnailLoader() {
        int kb=(int)Math.min(32*1024, Math.max(4*1024,Runtime.getRuntime().maxMemory()/1024/16));
        mMemoryCache=new LruCache<String,ThumbnailResult>(kb) {
            @Override protected int sizeOf(String key, ThumbnailResult value) {
                return value.bitmap==null ? 1 : Math.max(1,value.bitmap.getByteCount()/1024);
            }
        };
    }
    public static synchronized ThumbnailLoader getInstance() {
        if(sInstance==null)sInstance=new ThumbnailLoader();
        return sInstance;
    }
    public void load(Context context,Uri uri,ImageView image,TextView duration,TextView info,String baseInfo) {
        load(context,uri,image,duration,info,baseInfo, "");
    }
    public void load(Context context,Uri uri,ImageView image,TextView duration,TextView info,String baseInfo,String version) {
        load(context, uri, image, duration, null, info, baseInfo, version);
    }
    public void load(Context context,Uri uri,ImageView image,TextView duration,TextView resolution,TextView info,String baseInfo,String version) {
        if(image==null)return;
        if(uri==null) {
            image.setTag(null); image.setImageDrawable(null);
            if(duration!=null)duration.setVisibility(View.GONE);
            if(resolution!=null)resolution.setVisibility(View.GONE);
            if(info!=null)info.setText(baseInfo);
            return;
        }
        String key=uri+"|"+version+"|"+generation;
        image.setTag(key);
        ThumbnailResult cached=mMemoryCache.get(key);
        if(cached!=null) { applyResult(cached,image,duration,resolution,info,baseInfo); return; }
        image.setImageDrawable(null);
        if(duration!=null)duration.setVisibility(View.GONE);
        if(resolution!=null)resolution.setVisibility(View.GONE);
        if(info!=null)info.setText(baseInfo);
        synchronized(this) {
            Request request=active.get(key);
            if(request==null)request=pending.get(key);
            if(request==null) {
                // Discard the oldest queued offscreen work rather than retaining a long scroll backlog.
                if(pending.size()>=64)pending.remove(pending.keySet().iterator().next());
                request=new Request(context.getApplicationContext(),uri,key,generation);
                pending.put(key,request);
            }
            request.targets.add(new Target(image,duration,resolution,info,baseInfo));
            if(workers<2) { workers++; mExecutor.execute(this::drainRequests); }
        }
    }
    private void drainRequests() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        while(true) {
            Request request;
            synchronized(this) {
                if(pending.isEmpty()) { workers--; return; }
                String key=pending.keySet().iterator().next();
                request=pending.remove(key); active.put(key,request);
            }
            ThumbnailResult result=readDisk(request);
            if(result==null) {
                result=extractThumbnail(request.context,request.uri);
                writeDisk(request,result);
            }
            final ThumbnailResult ready=result;
            java.util.List<Target> targets;
            synchronized(this) {
                active.remove(request.key);
                targets=new java.util.ArrayList<>(request.targets);
                if(request.generation==generation)mMemoryCache.put(request.key,ready);
            }
            mMainHandler.post(() -> {
                if(request.generation!=generation)return;
                for(Target target:targets) {
                    ImageView view=target.image.get();
                    if(view!=null && request.key.equals(view.getTag()))
                        applyResult(ready,view,target.duration.get(),target.resolution.get(),target.info.get(),target.baseInfo);
                }
            });
        }
    }
    private java.io.File diskFile(Request request) throws java.io.IOException {
        java.io.File directory=new java.io.File(request.context.getCacheDir(),"video_previews");
        if(!directory.isDirectory() && !directory.mkdirs())throw new java.io.IOException("No preview cache");
        try {
            byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(request.diskKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder name=new StringBuilder();
            for(byte b:hash)name.append(String.format(Locale.ROOT,"%02x",b&255));
            return new java.io.File(directory,name+".preview");
        } catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private ThumbnailResult readDisk(Request request) {
        synchronized(diskLock) {
            try {
                java.io.File file=diskFile(request);
                if(!file.isFile() || file.length()>2*1024*1024)return null;
                try(java.io.DataInputStream input=new java.io.DataInputStream(new java.io.FileInputStream(file))) {
                    if(input.readInt()!=1)return null;
                    String duration=input.readUTF(), resolution=input.readUTF();
                    int count=input.readInt();
                    if(count<0 || count>2*1024*1024)return null;
                    Bitmap image=null;
                    if(count>0) {
                        byte[] bytes=new byte[count]; input.readFully(bytes);
                        android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();
                        bounds.inJustDecodeBounds=true;
                        android.graphics.BitmapFactory.decodeByteArray(bytes,0,count,bounds);
                        if(bounds.outWidth>480 || bounds.outHeight>288 || bounds.outWidth<=0)return null;
                        image=android.graphics.BitmapFactory.decodeByteArray(bytes,0,count);
                        if(image==null)return null;
                    }
                    file.setLastModified(System.currentTimeMillis());
                    return new ThumbnailResult(image,duration.isEmpty()?null:duration,resolution.isEmpty()?null:resolution);
                }
            } catch(java.io.IOException e) { return null; }
        }
    }
    private void writeDisk(Request request,ThumbnailResult result) {
        synchronized(diskLock) {
            if(request.generation!=generation)return;
            java.io.File temporary=null;
            try {
                java.io.File file=diskFile(request);
                temporary=new java.io.File(file.getPath()+".tmp");
                java.io.ByteArrayOutputStream image=new java.io.ByteArrayOutputStream();
                if(result.bitmap!=null)result.bitmap.compress(Bitmap.CompressFormat.JPEG,80,image);
                byte[] bytes=image.toByteArray();
                try(java.io.DataOutputStream output=new java.io.DataOutputStream(new java.io.FileOutputStream(temporary))) {
                    output.writeInt(1); output.writeUTF(result.durationText==null?"":result.durationText);
                    output.writeUTF(result.resolution==null?"":result.resolution);
                    output.writeInt(bytes.length); output.write(bytes);
                }
                if(!temporary.renameTo(file))return;
                CacheFiles.trim(file.getParentFile(),".preview",64L*1024*1024,System.currentTimeMillis()-7L*24*60*60*1000);
            } catch(java.io.IOException ignored) { }
            finally { if(temporary!=null)temporary.delete(); }
        }
    }
    public void clearCache(Context context, Runnable finished) {
        synchronized(this) { generation++; pending.clear(); mMemoryCache.evictAll(); }
        Context app=context.getApplicationContext();
        mExecutor.execute(() -> {
            synchronized(diskLock) { CacheFiles.clearFlat(new java.io.File(app.getCacheDir(),"video_previews")); }
            mMainHandler.post(finished);
        });
    }

    private void applyResult(ThumbnailResult result, ImageView imageView, TextView durationView, TextView infoView, String baseInfo) {
        applyResult(result, imageView, durationView, null, infoView, baseInfo);
    }

    private void applyResult(ThumbnailResult result, ImageView imageView, TextView durationView, TextView resolutionView, TextView infoView, String baseInfo) {
        if (result.bitmap != null) {
            imageView.setImageBitmap(result.bitmap);
        } else {
            imageView.setImageDrawable(null);
        }

        if (durationView != null) {
            if (result.durationText != null && !result.durationText.isEmpty()) {
                durationView.setText(result.durationText);
                durationView.setVisibility(View.VISIBLE);
            } else {
                durationView.setVisibility(View.GONE);
            }
        }

        if (resolutionView != null) {
            if (result.resolution != null && !result.resolution.isEmpty()) {
                resolutionView.setText(result.resolution);
                resolutionView.setVisibility(View.VISIBLE);
            } else {
                resolutionView.setVisibility(View.GONE);
            }
        }

        if (infoView != null) {
            StringBuilder sb = new StringBuilder();
            if (result.resolution != null && !result.resolution.isEmpty() && resolutionView == null) {
                sb.append(result.resolution).append(" • ");
            }
            if (baseInfo != null) {
                sb.append(baseInfo);
            }
            infoView.setText(sb.toString());
        }
    }

    private ThumbnailResult extractThumbnail(Context context, Uri uri) {
        Bitmap bitmap = null;
        String durationText = null;
        String resolution = null;

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            boolean sourceSet = false;
            try {
                retriever.setDataSource(context, uri);
                sourceSet = true;
            } catch (Exception e) {
                try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r")) {
                    if (pfd != null) {
                        retriever.setDataSource(pfd.getFileDescriptor());
                        sourceSet = true;
                    }
                } catch (Exception ignored) {}
            }

            if (sourceSet) {
                // Extract duration
                String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (durationStr != null) {
                    try {
                        long dur = Long.parseLong(durationStr);
                        durationText = formatDuration(dur);
                    } catch (NumberFormatException ignored) {}
                }

                // Extract resolution
                String widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                String heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                if (widthStr != null && heightStr != null) {
                    try {
                        int w = Integer.parseInt(widthStr);
                        int h = Integer.parseInt(heightStr);
                        resolution = formatResolution(w, h);
                    } catch (NumberFormatException ignored) {}
                }

                // Extract scaled frame (240x144 gives crisp 16:9 thumbnail on high DPI screens)
                long timeUs = 1500000; // 1.5 seconds into the video to avoid dark start frames
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    bitmap = retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 240, 144);
                    if (bitmap == null) {
                        bitmap = retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 240, 144);
                    }
                } else {
                    Bitmap raw = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    if (raw == null) {
                        raw = retriever.getFrameAtTime();
                    }
                    if (raw != null) {
                        bitmap = Bitmap.createScaledBitmap(raw, 240, 144, true);
                        if (raw != bitmap) {
                            raw.recycle();
                        }
                    }
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            try {
                retriever.release();
            } catch (Throwable ignored) {}
        }

        // Fallback to ContentResolver.loadThumbnail on Android Q+ if frame extraction was empty
        if (bitmap == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                bitmap = context.getContentResolver().loadThumbnail(uri, new Size(240, 144), null);
            } catch (Throwable ignored) {}
        }

        return new ThumbnailResult(bitmap, durationText, resolution);
    }

    public static String formatDuration(long millis) {
        if (millis <= 0) return null;
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }

    public static String formatResolution(int width, int height) {
        if (width <= 0 || height <= 0) return null;
        int minDim = Math.min(width, height);
        if (minDim >= 2160) return "4K";
        if (minDim >= 1440) return "2K";
        if (minDim >= 1080) return "FHD";
        if (minDim >= 720) return "HD";
        if (minDim >= 480) return "480p";
        return minDim + "p";
    }
}
