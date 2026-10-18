package com.brouken.player;

import android.content.Context;
import android.net.Uri;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ProgressBar;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class FolderBrowserAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static class BrowserItem {
        public static final int TYPE_FOLDER = 1;
        public static final int TYPE_VIDEO = 2;

        public final int type;
        public final Uri uri;
        public final DocumentFile file;
        public final String name;
        public final String path;
        public final long size;
        public final long lastModified;
        public final long duration;
        public final boolean isHidden;
        public final int videoCount;
        public final MediaScanner.VideoFolder folder;

        public BrowserItem(int type, Uri uri, String name, String path, long size, long lastModified, long duration, boolean isHidden, int videoCount, MediaScanner.VideoFolder folder) {
            this.type = type;
            this.uri = uri;
            this.file = null;
            this.name = name;
            this.path = path;
            this.size = size;
            this.lastModified = lastModified;
            this.duration = duration;
            this.isHidden = isHidden;
            this.videoCount = videoCount;
            this.folder = folder;
        }

        public BrowserItem(int type, DocumentFile file, String name, long size, long lastModified, boolean isHidden) {
            this.type = type;
            this.uri = file != null ? file.getUri() : null;
            this.file = file;
            this.name = name;
            this.path = file != null ? file.getUri().toString() : "";
            this.size = size;
            this.lastModified = lastModified;
            this.duration = 0;
            this.isHidden = isHidden;
            this.videoCount = 0;
            this.folder = null;
        }
    }

    public interface OnItemClickListener {
        void onFolderClick(BrowserItem folderItem);
        void onVideoClick(BrowserItem videoItem);
        void onVideoActions(BrowserItem videoItem);
    }

    private final Context context;
    private final List<BrowserItem> items = new ArrayList<>();
    private final OnItemClickListener listener;
    private final DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);
    private Map<String, WatchHistory.Entry> history;
    private String lastWatched;
    private Map<String,CaptionWorkStore.Entry> captionStates=new java.util.HashMap<>();
    private java.util.Set<String> historyScope;

    public FolderBrowserAdapter(Context context, OnItemClickListener listener) {
        this.context = context;
        this.listener = listener;
        history = new WatchHistory(context).snapshot();
    }

    public void setItems(List<BrowserItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        captionStates=new CaptionWorkStore(context).snapshot(CaptionPackages.get(context).selected(),items);
        updateLastWatched();
        notifyDataSetChanged();
    }

    public void refreshHistory() {
        history = new WatchHistory(context).snapshot();
        captionStates=new CaptionWorkStore(context).snapshot(CaptionPackages.get(context).selected(),items);
        updateLastWatched();
        notifyDataSetChanged();
    }

    public void refreshCaptionStates() {
        captionStates=new CaptionWorkStore(context).snapshot(CaptionPackages.get(context).selected(),items);
        if(!items.isEmpty())notifyItemRangeChanged(0,items.size(),"captions");
    }

    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder,int position,java.util.List<Object> payloads) {
        if(!payloads.isEmpty() && holder instanceof VideoViewHolder)((VideoViewHolder)holder).bindCaption(items.get(position));
        else onBindViewHolder(holder,position);
    }

    public void setHistoryScope(List<BrowserItem> scope) {
        historyScope = scope == null ? null : new java.util.HashSet<>();
        if (scope != null) for (BrowserItem item : scope) {
            if (item.uri != null) historyScope.add(item.uri.toString());
        }
        updateLastWatched();
    }

    private void updateLastWatched() {
        lastWatched = null;
        long latest = 0;
        for (Map.Entry<String, WatchHistory.Entry> saved : history.entrySet()) {
            String key = saved.getKey();
            WatchHistory.Entry entry = saved.getValue();
            if ((historyScope == null || historyScope.contains(key)) && entry.updated > latest) {
                latest = entry.updated;
                lastWatched = key;
            }
        }
    }

    public List<BrowserItem> getItems() {
        return items;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == BrowserItem.TYPE_FOLDER) {
            View view = inflater.inflate(R.layout.item_browser_folder, parent, false);
            return new FolderViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_browser_video, parent, false);
            return new VideoViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        BrowserItem item = items.get(position);
        if (holder instanceof FolderViewHolder) {
            ((FolderViewHolder) holder).bind(item);
        } else if (holder instanceof VideoViewHolder) {
            ((VideoViewHolder) holder).bind(item);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class FolderViewHolder extends RecyclerView.ViewHolder {
        private final TextView textName;
        private final TextView textInfo;
        private final ImageView imagePoster;
        private final ImageView imageIcon;

        FolderViewHolder(@NonNull View itemView) {
            super(itemView);
            textName = itemView.findViewById(R.id.text_folder_name);
            textInfo = itemView.findViewById(R.id.text_folder_info);
            imagePoster = itemView.findViewById(R.id.image_folder_poster);
            imageIcon = itemView.findViewById(R.id.image_folder_icon);
        }

        void bind(BrowserItem item) {
            textName.setText(item.name);

            StringBuilder info = new StringBuilder();
            if (item.videoCount > 0) {
                info.append(item.videoCount).append(item.videoCount == 1 ? " video" : " videos");
            } else {
                info.append(context.getString(R.string.folder));
            }

            if (item.isHidden) {
                info.append(" • ").append(context.getString(R.string.hidden_folder));
                textInfo.setTextColor(context.getResources().getColor(R.color.player_accent));
            } else {
                textInfo.setTextColor(context.getResources().getColor(R.color.app_text_secondary));
            }
            textInfo.setText(info.toString());

            if (item.uri != null) {
                ThumbnailLoader.getInstance().load(context, item.uri, imagePoster, null, null, null, thumbnailVersion(item));
                imagePoster.setVisibility(View.VISIBLE);
                imageIcon.setAlpha(0.6f);
            } else {
                imagePoster.setTag(null);
                imagePoster.setImageDrawable(null);
                imagePoster.setVisibility(View.GONE);
                imageIcon.setAlpha(1.0f);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onFolderClick(item);
                }
            });
        }
    }

    class VideoViewHolder extends RecyclerView.ViewHolder {
        private final TextView textName;
        private final TextView textInfo;
        private final ImageView imageThumbnail;
        private final TextView textDuration;
        private final ProgressBar watchProgress;
        private final TextView captionStatus;

        VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            textName = itemView.findViewById(R.id.text_video_name);
            textInfo = itemView.findViewById(R.id.text_video_info);
            imageThumbnail = itemView.findViewById(R.id.image_video_thumbnail);
            textDuration = itemView.findViewById(R.id.text_video_duration);
            watchProgress = itemView.findViewById(R.id.watch_progress);
            captionStatus=itemView.findViewById(R.id.caption_work_status);
        }

        void bindCaption(BrowserItem item) {
            CaptionWorkStore.Entry state=item.uri==null?null:captionStates.get(item.uri.toString());
            captionStatus.setText(state==null?"":state.label());captionStatus.setVisibility(state==null?View.GONE:View.VISIBLE);
        }

        void bind(BrowserItem item) {
            textName.setText(item.name);
            bindCaption(item);
            itemView.findViewById(R.id.video_actions).setOnClickListener(v -> listener.onVideoActions(item));
            itemView.setOnLongClickListener(v -> { listener.onVideoActions(item);return true; });

            StringBuilder info = new StringBuilder();
            if (item.isHidden) {
                info.append(".hidden • ");
            }
            if (item.size > 0) {
                info.append(Formatter.formatFileSize(context, item.size));
            }
            if (item.lastModified > 0) {
                if (info.length() > 0 && !info.toString().endsWith("• ")) {
                    info.append(" • ");
                }
                info.append(dateFormat.format(new Date(item.lastModified)));
            }

            Uri videoUri = item.uri != null ? item.uri : (item.file != null ? item.file.getUri() : null);
            WatchHistory.Entry watched = videoUri == null ? null : history.get(videoUri.toString());
            boolean last = videoUri != null && videoUri.toString().equals(lastWatched);
            boolean completed = watched != null && watched.completed;
            itemView.setBackgroundResource(last ? R.drawable.library_row_highlight : R.drawable.library_row_background);
            itemView.setAlpha(completed && !last ? 0.70f : 1f);
            imageThumbnail.setAlpha(completed && !last ? 0.65f : 1f);
            textName.setTextColor(context.getResources().getColor(last ? R.color.player_accent : R.color.app_text));
            watchProgress.setProgress(watched == null ? 0 : WatchProgress.percent(watched.position, watched.duration, completed));
            String accessibility = item.name;
            if (watched != null) {
                String status = watched.completed ? context.getString(R.string.watched)
                        : context.getString(R.string.continue_from, formatPosition(watched.position));
                if (last) {
                    status = context.getString(R.string.last_watched) + " · " + status;
                }
                accessibility += ", " + status;
            }
            itemView.setContentDescription(accessibility);
            ThumbnailLoader.getInstance().load(context, videoUri, imageThumbnail, textDuration, textInfo, info.toString(), thumbnailVersion(item));

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onVideoClick(item);
                }
            });
        }
    }

    private static String thumbnailVersion(BrowserItem item) {
        if (item.folder != null) for (MediaScanner.VideoItem video : item.folder.videos) {
            if (video.uri.equals(item.uri)) return video.size + ":" + video.dateAdded;
        }
        return item.size + ":" + item.lastModified;
    }

    private static String formatPosition(long millis) {
        long seconds = millis / 1000;
        return seconds >= 3600 ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d",
                seconds / 3600, seconds / 60 % 60, seconds % 60)
                : String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
}
