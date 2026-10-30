package com.brouken.player;

import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.card.MaterialCardView;
import java.util.ArrayList;
import java.util.List;

public class SubtitleBottomSheetDialogFragment extends BottomSheetDialogFragment {

    public static class SubtitleTrackItem {
        public final int trackIndex;
        public final String name;
        public final String language;
        public final String codec;
        public final boolean isSelected;

        public SubtitleTrackItem(int trackIndex, String name, String language, String codec, boolean isSelected) {
            this.trackIndex = trackIndex;
            this.name = name;
            this.language = language;
            this.codec = codec;
            this.isSelected = isSelected;
        }
    }

    public interface SubtitleDialogListener {
        void onSelectTrack(int trackIndex);
        void onLoadExternalFile();
        void onToggleAiCaptions();
        void onOffsetChanged(long newOffsetMs);
        void onOpenAppearance();
        long getCurrentOffsetMs();
        int getCurrentTrackIndex();
        boolean isAiCaptionsActive();
        boolean isAiCaptionsGenerating();
        int getAiCaptionsPercent();
        Uri getExternalUri();
        List<SubtitleTrackItem> getAvailableTracks();
    }

    private SubtitleDialogListener mListener;
    private TextView mTextDelayValue;
    private long mCurrentOffset = 0;

    public static SubtitleBottomSheetDialogFragment newInstance() {
        return new SubtitleBottomSheetDialogFragment();
    }

    public void setListener(SubtitleDialogListener listener) {
        this.mListener = listener;
    }

    @Override
    public int getTheme() {
        return R.style.ThemeOverlay_App_BottomSheetDialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context themedContext = new androidx.appcompat.view.ContextThemeWrapper(requireContext(), R.style.Theme_FolderBrowser);
        LayoutInflater themedInflater = inflater.cloneInContext(themedContext);
        return themedInflater.inflate(R.layout.sheet_subtitles, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageButton btnClose = view.findViewById(R.id.button_sheet_close);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }

        if (mListener != null) {
            mCurrentOffset = mListener.getCurrentOffsetMs();
        }

        mTextDelayValue = view.findViewById(R.id.text_delay_value);
        updateDelayLabel();

        // Timing Adjustment buttons
        View btnMinus500 = view.findViewById(R.id.button_delay_minus_500);
        if (btnMinus500 != null) btnMinus500.setOnClickListener(v -> adjustOffset(-500));

        View btnMinus100 = view.findViewById(R.id.button_delay_minus_100);
        if (btnMinus100 != null) btnMinus100.setOnClickListener(v -> adjustOffset(-100));

        View btnPlus100 = view.findViewById(R.id.button_delay_plus_100);
        if (btnPlus100 != null) btnPlus100.setOnClickListener(v -> adjustOffset(100));

        View btnPlus500 = view.findViewById(R.id.button_delay_plus_500);
        if (btnPlus500 != null) btnPlus500.setOnClickListener(v -> adjustOffset(500));

        View btnReset = view.findViewById(R.id.button_delay_reset);
        if (btnReset != null) {
            btnReset.setOnClickListener(v -> {
                mCurrentOffset = 0;
                updateDelayLabel();
                if (mListener != null) mListener.onOffsetChanged(0);
            });
        }

        View btnAppearance = view.findViewById(R.id.button_subtitle_appearance);
        if (btnAppearance != null) {
            btnAppearance.setOnClickListener(v -> {
                if (mListener != null) mListener.onOpenAppearance();
                dismiss();
            });
        }

        populateTracks(view);
    }

    private void adjustOffset(long delta) {
        mCurrentOffset += delta;
        updateDelayLabel();
        if (mListener != null) {
            mListener.onOffsetChanged(mCurrentOffset);
        }
    }

    private void updateDelayLabel() {
        if (mTextDelayValue != null) {
            String sign = mCurrentOffset > 0 ? "+" : "";
            mTextDelayValue.setText("Offset: " + sign + mCurrentOffset + " ms");
        }
    }

    private void populateTracks(View rootView) {
        LinearLayout tracksContainer = rootView.findViewById(R.id.layout_subtitle_tracks);
        if (tracksContainer == null || getContext() == null) return;
        tracksContainer.removeAllViews();

        Context themedContext = new androidx.appcompat.view.ContextThemeWrapper(rootView.getContext(), R.style.Theme_FolderBrowser);
        LayoutInflater inflater = LayoutInflater.from(themedContext);
        Context ctx = themedContext;

        int selectedTrack = mListener != null ? mListener.getCurrentTrackIndex() : Integer.MIN_VALUE;
        boolean aiActive = mListener != null && mListener.isAiCaptionsActive();
        Uri externalUri = mListener != null ? mListener.getExternalUri() : null;

        // 1. "Off" item
        boolean isOff = !aiActive && externalUri == null && (selectedTrack == Integer.MIN_VALUE || selectedTrack < 0);
        addTrackRow(tracksContainer, inflater, ctx, "Off", null, null, isOff, () -> {
            if (mListener != null) mListener.onSelectTrack(Integer.MIN_VALUE);
            dismiss();
        });

        // 2. Embedded tracks
        List<SubtitleTrackItem> tracks = mListener != null ? mListener.getAvailableTracks() : new ArrayList<>();
        for (SubtitleTrackItem track : tracks) {
            boolean isSelected = !aiActive && externalUri == null && selectedTrack == track.trackIndex;
            String name = track.name != null ? track.name : ("Track " + (track.trackIndex + 1));
            addTrackRow(tracksContainer, inflater, ctx, name, track.language, track.codec, isSelected, () -> {
                if (mListener != null) mListener.onSelectTrack(track.trackIndex);
                dismiss();
            });
        }

        // 3. External Subtitle File
        boolean isExtSelected = !aiActive && externalUri != null;
        String extLabel = externalUri != null ? ("External: " + SubtitleUtils.getFileName(ctx, externalUri)) : "Load external subtitle file…";
        addTrackRow(tracksContainer, inflater, ctx, extLabel, "File", "EXT", isExtSelected, () -> {
            if (mListener != null) mListener.onLoadExternalFile();
            dismiss();
        });

        // 4. Offline AI Captions (Whisper)
        boolean generating = mListener != null && mListener.isAiCaptionsGenerating();
        int percent = mListener != null ? mListener.getAiCaptionsPercent() : 0;
        String aiLabel = generating ? ("Offline AI Captions (" + percent + "%)") : "Offline AI Captions (Whisper)";
        Prefs prefs = new Prefs(ctx);
        String subDetails = (prefs.captionTranslate ? "Translate to English" : "Original speech") + " • 99 languages";
        addTrackRow(tracksContainer, inflater, ctx, aiLabel, subDetails, "AI", aiActive, () -> {
            if (mListener != null) mListener.onToggleAiCaptions();
            dismiss();
        });
    }

    private void addTrackRow(LinearLayout parent, LayoutInflater inflater, Context ctx,
                             String title, String subtitle, String badge, boolean isSelected, Runnable onClick) {
        View row = inflater.inflate(R.layout.item_subtitle_track, parent, false);
        MaterialCardView card = row.findViewById(R.id.card_track);
        RadioButton radio = row.findViewById(R.id.radio_track_selected);
        TextView textName = row.findViewById(R.id.text_track_name);
        TextView textDetails = row.findViewById(R.id.text_track_details);
        TextView textBadge = row.findViewById(R.id.text_track_badge);

        textName.setText(title);
        radio.setChecked(isSelected);

        if (subtitle != null && !subtitle.isEmpty()) {
            textDetails.setText(subtitle);
            textDetails.setVisibility(View.VISIBLE);
        } else {
            textDetails.setVisibility(View.GONE);
        }

        if (badge != null && !badge.isEmpty()) {
            textBadge.setText(badge);
            textBadge.setVisibility(View.VISIBLE);
        } else {
            textBadge.setVisibility(View.GONE);
        }

        if (isSelected) {
            card.setStrokeColor(ContextCompat.getColor(ctx, R.color.player_accent));
            card.setStrokeWidth(Utils.dpToPx(2));
            card.setCardBackgroundColor(0x223DDC84);
        } else {
            card.setStrokeColor(ContextCompat.getColor(ctx, R.color.app_border));
            card.setStrokeWidth(Utils.dpToPx(1));
            card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.app_surface));
        }

        card.setOnClickListener(v -> onClick.run());
        parent.addView(row);
    }
}
