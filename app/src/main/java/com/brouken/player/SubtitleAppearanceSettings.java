package com.brouken.player;

import android.content.Context;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import java.util.Collections;

public final class SubtitleAppearanceSettings {
    private final Context context;
    private final SubtitleStylePreferences preferences;
    private final Runnable changed;
    private final LinearLayout content,custom;
    private final SubtitleView preview;
    private final SwitchMaterial androidStyle,outline;
    private final TextView size,color,opacityLabel,bottomLabel;
    private final SeekBar opacity,bottom;
    private SubtitleStyleOptions value;
    private boolean binding;

    public static void show(Context context,Runnable changed) { new SubtitleAppearanceSettings(context,changed).show(); }
    private SubtitleAppearanceSettings(Context context,Runnable changed) {
        this.context=context;this.changed=changed;preferences=new SubtitleStylePreferences(context);value=preferences.read();
        content=new LinearLayout(context);content.setOrientation(LinearLayout.VERTICAL);int space=dp(20);content.setPadding(space,dp(8),space,dp(8));
        FrameLayout stage=new FrameLayout(context);stage.setBackgroundColor(0xff263238);
        preview=new SubtitleView(context);stage.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        content.addView(stage,new LinearLayout.LayoutParams(-1,dp(144)));
        TextView hint=label(context.getString(R.string.subtitle_preview_hint));content.addView(hint);
        androidStyle=toggle(R.string.subtitle_use_android,content);
        TextView systemHint=label(context.getString(R.string.subtitle_android_hint));content.addView(systemHint);
        custom=new LinearLayout(context);custom.setOrientation(LinearLayout.VERTICAL);content.addView(custom);
        size=choice(R.string.subtitle_text_size,custom,()->chooseSize());
        color=choice(R.string.subtitle_text_color,custom,()->chooseColor());
        opacityLabel=label("");custom.addView(opacityLabel);opacity=slider(custom,100);
        outline=toggle(R.string.subtitle_outline,custom);
        bottomLabel=label("");custom.addView(bottomLabel);bottom=slider(custom,21);
        androidStyle.setOnCheckedChangeListener((button,on)-> { if(!binding)update(new SubtitleStyleOptions(on,value.size,value.color,value.opacity,value.outline,value.bottom)); });
        outline.setOnCheckedChangeListener((button,on)-> { if(!binding)update(new SubtitleStyleOptions(value.useAndroid,value.size,value.color,value.opacity,on,value.bottom)); });
        opacity.setOnSeekBarChangeListener(listener(()->update(new SubtitleStyleOptions(value.useAndroid,value.size,value.color,opacity.getProgress(),value.outline,value.bottom))));
        bottom.setOnSeekBarChangeListener(listener(()->update(new SubtitleStyleOptions(value.useAndroid,value.size,value.color,value.opacity,value.outline,bottom.getProgress()+4))));
        bind();
    }
    private int dp(int value) { return Math.round(value*context.getResources().getDisplayMetrics().density); }
    private TextView label(String text) {
        TextView view=new TextView(context);view.setText(text);view.setTextSize(14);view.setPadding(0,dp(8),0,dp(4));return view;
    }
    private SwitchMaterial toggle(int title,LinearLayout parent) {
        SwitchMaterial view=new SwitchMaterial(context);view.setText(title);view.setTextSize(16);view.setMinHeight(dp(48));parent.addView(view);return view;
    }
    private TextView choice(int title,LinearLayout parent,Runnable action) {
        TextView view=label(context.getString(title));view.setTextSize(16);view.setMinHeight(dp(56));view.setGravity(android.view.Gravity.CENTER_VERTICAL);
        android.util.TypedValue ripple=new android.util.TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground,ripple,true);
        view.setBackgroundResource(ripple.resourceId);
        view.setFocusable(true);view.setClickable(true);parent.addView(view,new LinearLayout.LayoutParams(-1,-2));view.setOnClickListener(v->action.run());return view;
    }
    private SeekBar slider(LinearLayout parent,int max) {
        SeekBar slider=new SeekBar(context);slider.setMax(max);parent.addView(slider,new LinearLayout.LayoutParams(-1,dp(48)));return slider;
    }
    private SeekBar.OnSeekBarChangeListener listener(Runnable action) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean user) { if(user && !binding)action.run(); }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        };
    }
    private void update(SubtitleStyleOptions next) { value=next;preferences.save(value);bind();changed.run(); }
    private void bind() {
        binding=true;
        androidStyle.setChecked(value.useAndroid);outline.setChecked(value.outline);
        opacity.setProgress(value.opacity);bottom.setProgress(value.bottom-4);
        size.setText(context.getString(R.string.subtitle_setting_value,context.getString(R.string.subtitle_text_size),context.getResources().getStringArray(R.array.subtitle_sizes)[value.size]));
        color.setText(context.getString(R.string.subtitle_setting_value,context.getString(R.string.subtitle_text_color),context.getResources().getStringArray(R.array.subtitle_colors)[value.color]));
        opacityLabel.setText(context.getString(R.string.subtitle_opacity_value,value.opacity));bottomLabel.setText(context.getString(R.string.subtitle_bottom_value,value.bottom));
        opacity.setContentDescription(opacityLabel.getText());bottom.setContentDescription(bottomLabel.getText());
        custom.setVisibility(value.useAndroid?View.GONE:View.VISIBLE);
        SubtitleAppearance.apply(context,preview,false,false);
        preview.setCues(Collections.singletonList(SubtitleAppearance.text(context.getString(R.string.subtitle_preview_text))));
        binding=false;
    }
    private void chooseSize() {
        new AlertDialog.Builder(context).setTitle(R.string.subtitle_text_size).setSingleChoiceItems(R.array.subtitle_sizes,value.size,(dialog,index)-> {
            update(new SubtitleStyleOptions(value.useAndroid,index,value.color,value.opacity,value.outline,value.bottom));dialog.dismiss();
        }).setNegativeButton(android.R.string.cancel,null).show();
    }
    private void chooseColor() {
        new AlertDialog.Builder(context).setTitle(R.string.subtitle_text_color).setSingleChoiceItems(R.array.subtitle_colors,value.color,(dialog,index)-> {
            update(new SubtitleStyleOptions(value.useAndroid,value.size,index,value.opacity,value.outline,value.bottom));dialog.dismiss();
        }).setNegativeButton(android.R.string.cancel,null).show();
    }
    private void show() {
        ScrollView scroll=new ScrollView(context);scroll.setFillViewport(false);scroll.addView(content);
        AlertDialog dialog=new AlertDialog.Builder(context).setTitle(R.string.subtitle_appearance).setView(scroll)
                .setPositiveButton(R.string.subtitle_done,null).setNeutralButton(R.string.subtitle_reset,null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->update(SubtitleStyleOptions.defaults())));
        dialog.show();
    }
}
