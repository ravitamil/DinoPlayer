package com.brouken.player;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.widget.*;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Deletes one or multiple videos cleanly. Native caption cleanup and watch progress removal come first. */
public final class VideoDeletionActivity extends AppCompatActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayList<Uri> mediaUris = new ArrayList<>();
    private final ArrayList<String> mediaPaths = new ArrayList<>();
    private boolean captions = true;
    private boolean waiting = false;
    private final ArrayList<String> associatedFiles = new ArrayList<>();
    private TextView message;

    private final ActivityResultLauncher<IntentSenderRequest> approvalLauncher = registerForActivityResult(
            new ActivityResultContracts.StartIntentSenderForResult(),
            result -> {
                waiting = false;
                if (result.getResultCode() == RESULT_OK) {
                    deleteVideos(true);
                } else {
                    failure("Deletion cancelled");
                }
            });

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (waiting) {
                    finish();
                }
            }
        });
        CaptionWorkStore.initialize(this);

        List<Uri> extraUris = getIntent().getParcelableArrayListExtra("uris");
        if (extraUris != null) {
            mediaUris.addAll(extraUris);
        } else if (getIntent().getData() != null) {
            mediaUris.add(getIntent().getData());
        }

        List<String> extraPaths = getIntent().getStringArrayListExtra("paths");
        if (extraPaths != null) {
            mediaPaths.addAll(extraPaths);
        } else if (getIntent().getStringExtra("path") != null) {
            mediaPaths.add(getIntent().getStringExtra("path"));
        }

        captions = getIntent().getBooleanExtra("captions", true);

        message = new TextView(this);
        message.setTextSize(16);
        message.setGravity(android.view.Gravity.CENTER);
        message.setPadding(32, 32, 32, 32);
        setContentView(message);
        message.setText("Preparing deletion…");

        if (mediaUris.isEmpty()) {
            finish();
            return;
        }

        if (state != null) {
            waiting = state.getBoolean("waiting");
            ArrayList<String> savedAssociated = state.getStringArrayList("associated");
            if (savedAssociated != null) {
                associatedFiles.addAll(savedAssociated);
            }
        }

        if (associatedFiles.isEmpty()) {
            for (int i = 0; i < mediaUris.size(); i++) {
                Uri u = mediaUris.get(i);
                String p = (i < mediaPaths.size()) ? mediaPaths.get(i) : null;
                for (File file : CaptionLibrary.associated(this, u, p)) {
                    associatedFiles.add(file.getAbsolutePath());
                }
            }
        }

        CaptionWorkStore store = new CaptionWorkStore(this);
        for (Uri u : mediaUris) {
            store.blockDeletion(u, true);
        }

        if (waiting) {
            message.setText("Waiting for confirmation…");
        } else {
            deleteVideos(false);
        }
    }

    private void deleteVideos(boolean approved) {
        worker.execute(() -> {
            try {
                // Check if MediaStore approval is needed first on API 30+
                if (!approved && Build.VERSION.SDK_INT >= 30) {
                    List<Uri> mediaStoreUris = new ArrayList<>();
                    for (Uri u : mediaUris) {
                        if ("media".equals(u.getAuthority())) {
                            mediaStoreUris.add(u);
                        }
                    }
                    if (!mediaStoreUris.isEmpty()) {
                        try {
                            PendingIntent pendingIntent = MediaStore.createDeleteRequest(getContentResolver(), mediaStoreUris);
                            main.post(() -> launchApproval(pendingIntent.getIntentSender()));
                            return;
                        } catch (Exception ignored) {
                            // Fall through to direct deletion attempt
                        }
                    }
                }

                CaptionWorkStore store = new CaptionWorkStore(this);
                WatchHistory history = new WatchHistory(this);

                for (int i = 0; i < mediaUris.size(); i++) {
                    Uri media = mediaUris.get(i);
                    String path = (i < mediaPaths.size()) ? mediaPaths.get(i) : null;

                    CaptionAutoJobs.removeQueued(this, media, null);
                    SubtitleGenerator.cancelAndAwait(media);

                    if (!isAbsent(media)) {
                        if ("file".equals(media.getScheme())) {
                            File file = new File(media.getPath());
                            if (!file.delete() && file.exists()) {
                                throw new IOException("Unable to delete: " + file.getName());
                            }
                            android.media.MediaScannerConnection.scanFile(this, new String[]{file.getPath()}, null, null);
                        } else if (DocumentsContract.isDocumentUri(this, media)) {
                            if (!DocumentsContract.deleteDocument(getContentResolver(), media) && !isAbsent(media)) {
                                throw new IOException("Document provider could not delete file");
                            }
                        } else {
                            try {
                                getContentResolver().delete(media, null, null);
                            } catch (SecurityException sec) {
                                if (!approved) {
                                    main.post(() -> requestSingleApproval(sec, media));
                                    return;
                                }
                            }
                        }
                    }

                    if (path != null && !path.startsWith("content:") && new File(path).isFile()) {
                        new File(path).delete();
                    }

                    store.forget(media);
                    store.blockDeletion(media, false);
                    PlaylistManager.remove(this, media);
                    history.forget(media);
                }

                if (captions) {
                    for (String filePath : associatedFiles) {
                        File target = new File(filePath);
                        if (target.exists()) {
                            target.delete();
                        }
                    }
                }

                main.post(() -> {
                    if (isDestroyed()) return;
                    setResult(RESULT_OK);
                    int count = mediaUris.size();
                    Toast.makeText(this, count == 1 ? "Video deleted" : (count + " videos deleted"), Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (Exception error) {
                main.post(() -> failure(error.getMessage()));
            }
        });
    }

    private boolean isAbsent(Uri uri) {
        if ("file".equals(uri.getScheme())) {
            return !new File(uri.getPath()).exists();
        }
        try (Cursor cursor = getContentResolver().query(uri, new String[]{android.provider.BaseColumns._ID}, null, null, null)) {
            return cursor != null && !cursor.moveToFirst();
        } catch (Exception e) {
            try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r")) {
                return false;
            } catch (FileNotFoundException missing) {
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    private void launchApproval(IntentSender sender) {
        if (isDestroyed()) return;
        waiting = true;
        message.setText("Confirm deletion in the system dialog…");
        approvalLauncher.launch(new IntentSenderRequest.Builder(sender).build());
    }

    private void requestSingleApproval(SecurityException error, Uri uri) {
        if (isDestroyed()) return;
        try {
            IntentSender sender = null;
            if (Build.VERSION.SDK_INT >= 29 && error instanceof RecoverableSecurityException) {
                sender = ((RecoverableSecurityException) error).getUserAction().getActionIntent().getIntentSender();
            } else if (Build.VERSION.SDK_INT >= 30 && "media".equals(uri.getAuthority())) {
                sender = MediaStore.createDeleteRequest(getContentResolver(), Collections.singletonList(uri)).getIntentSender();
            }
            if (sender != null) {
                launchApproval(sender);
            } else {
                failure("Storage write permission is required to delete this video");
            }
        } catch (Exception e) {
            failure("Unable to request deletion permission");
        }
    }

    private void failure(String reason) {
        if (isDestroyed()) return;
        CaptionWorkStore store = new CaptionWorkStore(this);
        for (Uri u : mediaUris) {
            store.blockDeletion(u, false);
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Deletion Cancelled")
                .setMessage(reason == null ? "Deletion failed" : reason)
                .setPositiveButton(android.R.string.ok, (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("waiting", waiting);
        out.putStringArrayList("associated", associatedFiles);
    }

    @Override
    protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }
}
