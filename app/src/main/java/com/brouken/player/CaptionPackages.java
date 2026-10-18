package com.brouken.player;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** System-managed downloads survive navigation and process death. Only verified complete packs become visible. */
public final class CaptionPackages {
    public static final class State {
        public final String text;
        public final boolean installed, downloading, verifying;
        public final int percent;
        State(String text, boolean installed, boolean downloading, boolean verifying, int percent) {
            this.text=text; this.installed=installed; this.downloading=downloading;
            this.verifying=verifying; this.percent=percent;
        }
    }
    public final class Lease implements AutoCloseable {
        public final CaptionPack pack;
        public final File directory;
        private boolean closed;
        private Lease(CaptionPack pack) { this.pack=pack; directory=installedDir(pack); }
        @Override public void close() {
            synchronized (CaptionPackages.this) {
                if (!closed) { users.put(pack.id, users.get(pack.id)-1); closed=true; }
            }
        }
    }
    private static CaptionPackages instance;
    public static synchronized CaptionPackages get(Context context) {
        if (instance == null) instance = new CaptionPackages(context.getApplicationContext());
        return instance;
    }
    private final Context context;
    private final SharedPreferences prefs;
    private final DownloadManager downloads;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> new Thread(() -> {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        task.run();
    }, "CaptionPackages"));
    private final Set<String> installing = new HashSet<>();
    private final Map<String,Integer> users = new HashMap<>();
    private CaptionPackages(Context context) {
        this.context=context;
        prefs=context.getSharedPreferences("caption_packages", Context.MODE_PRIVATE);
        downloads=(DownloadManager)context.getSystemService(Context.DOWNLOAD_SERVICE);
        File legacy=new File(context.getFilesDir(),"sherpa_models/tiny.en-decoder.attention.int8.onnx");
        if(!isInstalled(CaptionPack.ENGLISH) && legacy.isFile() && ids(CaptionPack.ENGLISH).length==0) {
            installing.add(CaptionPack.ENGLISH.id);
            executor.execute(this::migrateEnglish);
        }
    }
    public CaptionPack selected() { return CaptionPack.find(prefs.getString("selected", CaptionPack.ENGLISH.id)); }
    public void select(CaptionPack pack) { prefs.edit().putString("selected",pack.id).apply(); }
    private File installedDir(CaptionPack pack) { return new File(context.getNoBackupFilesDir(), "caption-packs/"+pack.id); }
    private File downloadDir(CaptionPack pack) throws IOException {
        File base=context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (base==null) throw new IOException("Download storage is unavailable");
        String attempt=prefs.getString(pack.id+".attempt","legacy");
        if(!attempt.matches("[0-9a-f]+|legacy"))throw new IOException("Invalid download state");
        return new File(base,"caption-packs/"+pack.id+"-"+attempt);
    }
    private long expectedSize(CaptionPack pack, CaptionPack.Asset asset) {
        return pack.aligned && asset.name.equals("decoder.onnx") ? CaptionPack.ALIGNED_DECODER_BYTES : asset.bytes;
    }
    public boolean isInstalled(CaptionPack pack) {
        File dir=installedDir(pack);
        if (!new File(dir,"ready").isFile()) return false;
        for (CaptionPack.Asset asset:pack.assets) {
            File f=new File(dir,asset.name);
            if (!f.isFile() || f.length()!=expectedSize(pack,asset)) return false;
        }
        return true;
    }
    public synchronized Lease acquire(CaptionPack pack) throws IOException {
        if (!isInstalled(pack)) throw new IOException("Install the "+pack.title+" pack in Settings first.");
        users.put(pack.id,(users.get(pack.id) == null ? 0 : users.get(pack.id))+1);
        return new Lease(pack);
    }
    public void verifyInstalled(Lease lease) throws IOException {
        try {
            for (CaptionPack.Asset asset:lease.pack.assets) {
                String hash=lease.pack.aligned && asset.name.equals("decoder.onnx")
                        ? CaptionPack.ALIGNED_DECODER_SHA256 : asset.sha256;
                VerifiedModelFiles.verify(new File(lease.directory,asset.name),expectedSize(lease.pack,asset),hash);
            }
        } catch (IOException error) {
            new File(lease.directory,"ready").delete();
            prefs.edit().putString(lease.pack.id+".error","Package failed verification. Tap to download again.").apply();
            throw error;
        }
    }
    private long[] ids(CaptionPack pack) {
        String value=prefs.getString(pack.id,"");
        if (value.isEmpty()) return new long[0];
        try {
            String[] parts=value.split(","); long[] result=new long[parts.length];
            for(int i=0;i<parts.length;i++) result[i]=Long.parseLong(parts[i]);
            return result;
        } catch(NumberFormatException e) { return new long[0]; }
    }
    private void saveIds(CaptionPack pack,List<Long> ids) {
        StringBuilder value=new StringBuilder();
        for(long id:ids) { if(value.length()>0)value.append(','); value.append(id); }
        prefs.edit().putString(pack.id,value.toString()).commit();
    }
    public synchronized void start(CaptionPack pack, boolean mobileData) throws IOException {
        if (isInstalled(pack) || ids(pack).length>0 || installing.contains(pack.id)) return;
        // Allow the verified staging copy and final pack to coexist during installation.
        if (new StatFs(context.getNoBackupFilesDir().getAbsolutePath()).getAvailableBytes() < pack.downloadBytes()*2+32*1024*1024L)
            throw new IOException("Free at least "+(pack.downloadBytes()*2/1000000+32)+" MB to install this pack.");
        prefs.edit().putString(pack.id+".attempt",Long.toHexString(System.nanoTime())).commit();
        File dir=downloadDir(pack);
        deleteFlat(dir);
        if (!dir.mkdirs() && !dir.isDirectory()) throw new IOException("Cannot prepare package download");
        List<Long> enqueued=new ArrayList<>();
        prefs.edit().remove(pack.id+".error").apply();
        try {
            for(CaptionPack.Asset asset:pack.assets) {
                DownloadManager.Request request=new DownloadManager.Request(Uri.parse(asset.url))
                        .setTitle("DinoPlayer · "+pack.title)
                        .setDescription("Offline subtitle package")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                        .setAllowedOverMetered(mobileData).setAllowedOverRoaming(false)
                        .setDestinationUri(Uri.fromFile(new File(dir,asset.name)));
                if(!mobileData) request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);
                enqueued.add(downloads.enqueue(request));
                saveIds(pack,enqueued);
            }
        } catch (RuntimeException e) {
            cancel(pack);
            throw new IOException("Cannot start the download. Check that Android Download Manager is enabled.",e);
        }
    }
    public synchronized State state(CaptionPack pack) {
        if(isInstalled(pack)) return new State("Installed · Tap to manage",true,false,false,100);
        if(installing.contains(pack.id)) return new State("Verifying and installing…",false,true,true,100);
        long[] ids=ids(pack);
        if(ids.length==0) return new State(prefs.getString(pack.id+".error","Tap to download · Wi-Fi by default"),false,false,false,0);
        if(ids.length!=pack.assets.length) { fail(pack,"Download interrupted. Tap to retry."); return state(pack); }
        long bytes=0; int complete=0; boolean waiting=false, running=false;
        try {
            for(long id:ids) {
                try(Cursor cursor=downloads.query(new DownloadManager.Query().setFilterById(id))) {
                    if(cursor==null || !cursor.moveToFirst()) { fail(pack,"Download was removed. Tap to retry."); return state(pack); }
                    int status=cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    if(status==DownloadManager.STATUS_FAILED) { fail(pack,"Download failed. Check storage and connection, then retry."); return state(pack); }
                    bytes+=Math.max(0,cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)));
                    if(status==DownloadManager.STATUS_SUCCESSFUL)complete++;
                    if(status==DownloadManager.STATUS_RUNNING)running=true;
                    if(status==DownloadManager.STATUS_PAUSED || status==DownloadManager.STATUS_PENDING)waiting=true;
                }
            }
        } catch(RuntimeException e) { return new State("Download status unavailable",false,true,false,0); }
        if(complete==ids.length) {
            installing.add(pack.id);
            executor.execute(() -> install(pack));
            return new State("Verifying and installing…",false,true,true,100);
        }
        int percent=(int)Math.min(99,bytes*100/pack.downloadBytes());
        return new State((waiting && !running?"Waiting for network · ":"Downloading · ")+percent+"% · Tap to cancel",false,true,false,percent);
    }
    public void reconcile() { for(CaptionPack pack:CaptionPack.ALL) state(pack); }
    private void migrateEnglish() {
        CaptionPack pack=CaptionPack.ENGLISH;
        File old=new File(context.getFilesDir(),"sherpa_models");
        File stage=new File(context.getNoBackupFilesDir(),"caption-packs/"+pack.id+".staging");
        String[] names={"tiny.en-encoder.int8.onnx","tiny.en-decoder.attention.int8.onnx","tiny.en-tokens.txt","silero_vad.onnx"};
        try {
            deleteFlat(stage);
            if(!stage.mkdirs())throw new IOException("Cannot migrate installed English pack");
            for(int i=0;i<pack.assets.length;i++) {
                CaptionPack.Asset asset=pack.assets[i];
                String hash=asset.name.equals("decoder.onnx")
                        ? CaptionPack.ALIGNED_DECODER_SHA256 : asset.sha256;
                File source=new File(old,names[i]);
                VerifiedModelFiles.verify(source,expectedSize(pack,asset),hash);
                File target=new File(stage,asset.name);
                try(InputStream input=new FileInputStream(source);FileOutputStream output=new FileOutputStream(target)) {
                    VerifiedModelFiles.copy(input,output); output.getFD().sync();
                }
                VerifiedModelFiles.verify(target,expectedSize(pack,asset),hash);
            }
            if(!new File(stage,"ready").createNewFile())throw new IOException("Cannot complete package migration");
            synchronized(this) {
                deleteFlat(installedDir(pack));
                if(!stage.renameTo(installedDir(pack)))throw new IOException("Cannot activate installed English pack");
            }
            for(String name:names)new File(old,name).delete();
            old.delete();
        } catch(Exception e) {
            prefs.edit().putString(pack.id+".error","Existing English pack could not be reused. Tap to download.").apply();
        } finally {
            deleteFlat(stage);
            synchronized(this) { installing.remove(pack.id); }
            CaptionAutoJobs.kick(context);
        }
    }
    private void install(CaptionPack pack) {
        File stage=new File(context.getNoBackupFilesDir(),"caption-packs/"+pack.id+".staging");
        try {
            deleteFlat(stage);
            if(!stage.mkdirs())throw new IOException("Cannot prepare subtitle package");
            File source=downloadDir(pack);
            for(CaptionPack.Asset asset:pack.assets) {
                File input=new File(source,asset.name);
                VerifiedModelFiles.verify(input,asset.bytes,asset.sha256);
                File output=new File(stage,asset.name);
                if(pack.aligned && asset.name.equals("decoder.onnx")) {
                    JSONObject patch;
                    try(InputStream in=context.getAssets().open("caption_patches/english-decoder.json")) {
                        ByteArrayOutputStream json=new ByteArrayOutputStream(); VerifiedModelFiles.copy(in,json);
                        patch=new JSONObject(json.toString("UTF-8"));
                    }
                    try(InputStream head=context.getAssets().open("caption_patches/english-decoder-head.bin");
                        InputStream tail=context.getAssets().open("caption_patches/english-decoder-tail.bin")) {
                        VerifiedModelFiles.patch(input,output,patch.getLong("source_start"),patch.getLong("source_end"),head,tail);
                    }
                    VerifiedModelFiles.verify(output,patch.getLong("target_size"),patch.getString("target_sha256"));
                } else {
                    try(InputStream in=new FileInputStream(input); FileOutputStream out=new FileOutputStream(output)) {
                        VerifiedModelFiles.copy(in,out); out.getFD().sync();
                    }
                    VerifiedModelFiles.verify(output,asset.bytes,asset.sha256);
                }
            }
            if(!new File(stage,"ready").createNewFile())throw new IOException("Cannot finish subtitle installation");
            synchronized(this) {
                File target=installedDir(pack); deleteFlat(target);
                if(!stage.renameTo(target))throw new IOException("Cannot activate subtitle package");
                cancelDownloads(pack);
                prefs.edit().remove(pack.id+".error").apply();
            }
        } catch(Exception e) {
            synchronized(this) { fail(pack,e.getMessage()==null?"Installation failed. Please retry.":e.getMessage()); }
        } finally {
            deleteFlat(stage);
            synchronized(this) { installing.remove(pack.id); }
            CaptionAutoJobs.kick(context);
        }
    }
    private void fail(CaptionPack pack,String reason) {
        cancelDownloads(pack);
        prefs.edit().putString(pack.id+".error",reason).apply();
    }
    public synchronized void cancel(CaptionPack pack) throws IOException {
        if(installing.contains(pack.id))throw new IOException("Please wait for verification to finish.");
        cancelDownloads(pack);
        prefs.edit().remove(pack.id+".error").apply();
    }
    private void cancelDownloads(CaptionPack pack) {
        long[] ids=ids(pack);
        if(ids.length>0)downloads.remove(ids);
        prefs.edit().remove(pack.id).commit();
        try { deleteFlat(downloadDir(pack)); } catch(IOException ignored) { }
        prefs.edit().remove(pack.id+".attempt").apply();
    }
    public synchronized void remove(CaptionPack pack) throws IOException {
        if((users.get(pack.id) == null ? 0 : users.get(pack.id))>0)throw new IOException("Close the playing video before removing its subtitle pack.");
        cancel(pack); deleteFlat(installedDir(pack));
    }
    private static void deleteFlat(File directory) {
        File[] files=directory.listFiles();
        if(files!=null)for(File file:files)if(file.isFile())file.delete();
        directory.delete();
    }
}
