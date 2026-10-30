package com.brouken.player;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class CastBottomSheetDialogFragment extends BottomSheetDialogFragment {

    private static final String ARG_VIDEO_URI = "video_uri";
    private static final String ARG_VIDEO_TITLE = "video_title";

    public static CastBottomSheetDialogFragment newInstance(Uri videoUri, String videoTitle) {
        CastBottomSheetDialogFragment fragment = new CastBottomSheetDialogFragment();
        Bundle args = new Bundle();
        if (videoUri != null) args.putParcelable(ARG_VIDEO_URI, videoUri);
        if (videoTitle != null) args.putString(ARG_VIDEO_TITLE, videoTitle);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public int getTheme() {
        return R.style.ThemeOverlay_App_BottomSheetDialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context themedContext = new ContextThemeWrapper(requireContext(), R.style.Theme_FolderBrowser);
        LayoutInflater themedInflater = inflater.cloneInContext(themedContext);
        return themedInflater.inflate(R.layout.sheet_cast, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String videoTitle = getArguments() != null ? getArguments().getString(ARG_VIDEO_TITLE) : null;
        Uri videoUri = getArguments() != null ? getArguments().getParcelable(ARG_VIDEO_URI) : null;

        TextView textTitle = view.findViewById(R.id.text_cast_video_title);
        if (textTitle != null && videoTitle != null && !videoTitle.isEmpty()) {
            textTitle.setText(videoTitle);
        }

        View btnClose = view.findViewById(R.id.button_sheet_close);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }

        // 1. Wireless Display / Screen Mirroring
        View actionWireless = view.findViewById(R.id.action_cast_wireless);
        if (actionWireless != null) {
            actionWireless.setOnClickListener(v -> {
                dismiss();
                launchWirelessDisplaySettings(requireContext());
            });
        }

        // 2. Stream via DLNA / External Cast Player
        View actionDlna = view.findViewById(R.id.action_cast_dlna);
        if (actionDlna != null) {
            actionDlna.setOnClickListener(v -> {
                dismiss();
                if (videoUri != null) {
                    launchDlnaStreamIntent(requireContext(), videoUri, videoTitle);
                } else {
                    Toast.makeText(requireContext(), "No active video stream to cast", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    public static void launchWirelessDisplaySettings(Context context) {
        Intent[] castIntents = new Intent[] {
            new Intent("android.settings.CAST_SETTINGS"),
            new Intent("android.settings.WIFI_DISPLAY_SETTINGS"),
            new Intent("com.samsung.wfd.LAUNCH_WFD_PICKER_DLG"),
            new Intent(Settings.ACTION_DISPLAY_SETTINGS)
        };

        for (Intent intent : castIntents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                try {
                    context.startActivity(intent);
                    return;
                } catch (Exception ignored) {
                }
            }
        }
        Toast.makeText(context, "Wireless display settings not found on this device", Toast.LENGTH_SHORT).show();
    }

    public static void launchDlnaStreamIntent(Context context, Uri videoUri, String videoTitle) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(videoUri, "video/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            Intent chooser = Intent.createChooser(intent, "Cast video to TV / Receiver");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
        } catch (Exception e) {
            Toast.makeText(context, "Could not launch cast player: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
