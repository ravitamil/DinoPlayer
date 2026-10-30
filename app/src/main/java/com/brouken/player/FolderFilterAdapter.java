package com.brouken.player;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.checkbox.MaterialCheckBox;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FolderFilterAdapter extends RecyclerView.Adapter<FolderFilterAdapter.ViewHolder> {

    public interface OnItemRemovedListener {
        void onItemRemoved(String path);
    }

    private final List<String> folderPaths = new ArrayList<>();
    private final Set<String> selectedPaths = new HashSet<>();
    private final boolean showRemoveButton;
    private final OnItemRemovedListener removeListener;

    public FolderFilterAdapter(List<String> paths, Set<String> initialSelected) {
        this(paths, initialSelected, false, null);
    }

    public FolderFilterAdapter(List<String> paths, Set<String> initialSelected, boolean showRemoveButton, OnItemRemovedListener removeListener) {
        if (paths != null) {
            for (String p : paths) {
                if (p != null && !this.folderPaths.contains(p)) {
                    this.folderPaths.add(p);
                }
            }
        }
        if (initialSelected != null) {
            this.selectedPaths.addAll(initialSelected);
        }
        this.showRemoveButton = showRemoveButton;
        this.removeListener = removeListener;
    }

    public Set<String> getSelectedPaths() {
        return new HashSet<>(selectedPaths);
    }

    public void setSelectedPaths(Set<String> newSelected) {
        selectedPaths.clear();
        if (newSelected != null) {
            selectedPaths.addAll(newSelected);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sheet_folder, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String path = folderPaths.get(position);
        holder.bind(path);
    }

    @Override
    public int getItemCount() {
        return folderPaths.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCheckBox checkBox;
        private final TextView textName;
        private final TextView textPath;
        private final ImageButton buttonRemove;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.checkbox_folder);
            textName = itemView.findViewById(R.id.text_folder_name);
            textPath = itemView.findViewById(R.id.text_folder_path);
            buttonRemove = itemView.findViewById(R.id.button_remove_folder);
        }

        void bind(String path) {
            File file = new File(path);
            String name = file.getName();
            if (name.isEmpty()) {
                name = path;
            }
            textName.setText(name);
            textPath.setText(path);

            String norm = Prefs.normalizeFolderPath(path);
            boolean isChecked = false;
            for (String sel : selectedPaths) {
                if (Prefs.normalizeFolderPath(sel).equals(norm)) {
                    isChecked = true;
                    break;
                }
            }
            checkBox.setOnCheckedChangeListener(null);
            checkBox.setChecked(isChecked);

            checkBox.setOnCheckedChangeListener((buttonView, checked) -> {
                if (checked) {
                    selectedPaths.add(norm);
                } else {
                    Set<String> toRemove = new HashSet<>();
                    for (String s : selectedPaths) {
                        if (Prefs.normalizeFolderPath(s).equals(norm)) {
                            toRemove.add(s);
                        }
                    }
                    selectedPaths.removeAll(toRemove);
                }
            });

            itemView.setOnClickListener(v -> checkBox.toggle());

            if (buttonRemove != null) {
                buttonRemove.setVisibility(showRemoveButton ? View.VISIBLE : View.GONE);
                buttonRemove.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION && pos < folderPaths.size()) {
                        String removed = folderPaths.remove(pos);
                        String normRemoved = Prefs.normalizeFolderPath(removed);
                        Set<String> toRemove = new HashSet<>();
                        for (String s : selectedPaths) {
                            if (Prefs.normalizeFolderPath(s).equals(normRemoved)) {
                                toRemove.add(s);
                            }
                        }
                        selectedPaths.removeAll(toRemove);
                        notifyItemRemoved(pos);
                        if (removeListener != null) {
                            removeListener.onItemRemoved(removed);
                        }
                    }
                });
            }
        }
    }
}
