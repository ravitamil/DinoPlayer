package com.brouken.player;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Never reports success until the provider/file deletion succeeds. Native caption cleanup comes first. */
public final class VideoDeletionActivity extends AppCompatActivity {
    private static final int APPROVE=4101;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private Uri media;private String path;private boolean captions,waiting;
    private ArrayList<String> associated;
    private TextView message;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        CaptionWorkStore.initialize(this);
        media=getIntent().getData();path=getIntent().getStringExtra("path");captions=getIntent().getBooleanExtra("captions",true);
        message=new TextView(this);message.setTextSize(16);message.setGravity(android.view.Gravity.CENTER);
        message.setPadding(24,24,24,24);setContentView(message);message.setText("Stopping subtitle generation…");
        if(media==null) { finish();return; }
        associated=state==null?new ArrayList<>():state.getStringArrayList("associated");
        if(associated==null)associated=new ArrayList<>();
        if(associated.isEmpty())for(File file:CaptionLibrary.associated(this,media,path))associated.add(file.getAbsolutePath());
        waiting=state!=null && state.getBoolean("waiting");
        new CaptionWorkStore(this).blockDeletion(media,true);
        if(waiting)message.setText("Waiting for Android’s deletion approval…");else delete(false);
    }
    private void delete(boolean approved) {
        worker.execute(() -> {
            try {
                CaptionAutoJobs.removeQueued(this,media,null);
                SubtitleGenerator.cancelAndAwait(media);
                if(!absent()) {
                    if("file".equals(media.getScheme())) {
                        File file=new File(media.getPath());
                        if(!file.delete())throw new IOException("The video file could not be deleted");
                        android.media.MediaScannerConnection.scanFile(this,new String[]{file.getPath()},null,null);
                    } else if(DocumentsContract.isDocumentUri(this,media)) {
                        if(!DocumentsContract.deleteDocument(getContentResolver(),media))throw new IOException("The document provider did not delete the video");
                    } else if(getContentResolver().delete(media,null,null)==0 && !absent()) {
                        throw new IOException("The video could not be deleted");
                    }
                }
                if(path!=null && !path.startsWith("content:") && new File(path).isFile())throw new IOException("The video file is still present; refresh and try again");
                boolean clean=true;
                if(captions)for(String file:associated) { File target=new File(file);if(target.exists() && !target.delete())clean=false; }
                CaptionWorkStore store=new CaptionWorkStore(this);store.forget(media);store.blockDeletion(media,false);
                PlaylistManager.remove(this,media);new WatchHistory(this).forget(media);
                final boolean cleaned=clean;
                main.post(() -> { if(isDestroyed())return;setResult(RESULT_OK,new Intent().setData(media));
                    Toast.makeText(this,cleaned?"Video deleted":"Video deleted; some cached subtitles could not be removed",Toast.LENGTH_LONG).show();finish(); });
            } catch(SecurityException denied) {
                main.post(() -> requestApproval(denied,approved));
            } catch(Exception error) { main.post(() -> failure(error.getMessage())); }
        });
    }
    private boolean absent() throws IOException {
        if("file".equals(media.getScheme()))return !new File(media.getPath()).exists();
        try(Cursor cursor=getContentResolver().query(media,new String[]{android.provider.BaseColumns._ID},null,null,null)) {
            return cursor!=null && !cursor.moveToFirst();
        } catch(IllegalArgumentException e) {
            // Document providers may not expose _id; use a descriptor instead.
            try(android.os.ParcelFileDescriptor ignored=getContentResolver().openFileDescriptor(media,"r")) { return false; }
            catch(FileNotFoundException missing) { return true; }
        }
    }
    private void requestApproval(SecurityException error,boolean approved) {
        if(isDestroyed())return;
        if(approved) { failure("Android did not grant permission to delete this video");return; }
        try {
            IntentSender sender;
            if(Build.VERSION.SDK_INT>=29 && error instanceof RecoverableSecurityException)
                sender=((RecoverableSecurityException)error).getUserAction().getActionIntent().getIntentSender();
            else if(Build.VERSION.SDK_INT>=30 && "media".equals(media.getAuthority()))
                sender=MediaStore.createDeleteRequest(getContentResolver(),Collections.singletonList(media)).getIntentSender();
            else { failure("DinoPlayer cannot delete this video. Grant write access to its folder in Settings.");return; }
            waiting=true;message.setText("Approve deletion in the Android dialog");
            startIntentSenderForResult(sender,APPROVE,null,0,0,0);
        } catch(Exception e) { failure("Unable to request deletion permission"); }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==APPROVE) { waiting=false;if(result==RESULT_OK)delete(true);else failure("Deletion cancelled. Video kept."); }
    }
    private void failure(String reason) {
        if(isDestroyed())return;
        new CaptionWorkStore(this).blockDeletion(media,false);
        new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Video kept")
                .setMessage(reason==null?"Deletion failed":reason).setPositiveButton(android.R.string.ok,(d,w)->finish())
                .setOnCancelListener(d->finish()).show();
    }
    @Override public void onBackPressed() { if(waiting)super.onBackPressed(); /* Do not abandon a file mutation mid-operation. */ }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);out.putBoolean("waiting",waiting);out.putStringArrayList("associated",associated);
    }
    @Override protected void onDestroy() { worker.shutdown();super.onDestroy(); }
}
