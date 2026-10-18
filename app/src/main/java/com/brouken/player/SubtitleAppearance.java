package com.brouken.player;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Layout;
import android.util.TypedValue;
import android.view.accessibility.CaptioningManager;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.ui.CaptionStyleCompat;
import com.google.android.exoplayer2.ui.SubtitleView;
import java.util.ArrayList;
import java.util.List;

/** One presentation policy for live, cached, edited and imported text captions. */
public final class SubtitleAppearance {
    private SubtitleAppearance() { }

    public static void apply(Context context,SubtitleView view,boolean pip,boolean controlsVisible) {
        if(view==null)return;
        CaptioningManager captions=(CaptioningManager)context.getSystemService(Context.CAPTIONING_SERVICE);
        SubtitleStyleOptions options=new SubtitleStylePreferences(context).read();
        boolean userStyle=options.useAndroid && captions!=null;
        CaptionStyleCompat style=userStyle ? CaptionStyleCompat.createFromCaptionStyle(captions.getUserStyle())
                : new CaptionStyleCompat(options.textColor(),options.backgroundColor(),Color.TRANSPARENT,
                    options.outline?CaptionStyleCompat.EDGE_TYPE_OUTLINE:CaptionStyleCompat.EDGE_TYPE_NONE,Color.BLACK,Typeface.create("sans-serif",Typeface.NORMAL));
        view.setStyle(style);
        view.setApplyEmbeddedStyles(false);
        view.setApplyEmbeddedFontSizes(false);
        float scale=userStyle?captions.getFontScale():1f;
        view.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP,options.textSizeSp(pip,scale));
        view.setBottomPaddingFraction(options.bottomFraction(pip));
        view.setPadding(0,0,0,controlsVisible && !pip?Utils.dpToPx(104):0);
    }

    public static Cue text(CharSequence text) {
        return new Cue.Builder().setText(text.toString()).setTextAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMultiRowAlignment(Layout.Alignment.ALIGN_CENTER)
                .setPosition(0.5f).setPositionAnchor(Cue.ANCHOR_TYPE_MIDDLE).setSize(0.9f).build();
    }

    public static List<Cue> normalize(List<Cue> cues) {
        List<Cue> result=new ArrayList<>(cues.size());
        for(Cue cue:cues) {
            // Bitmap subtitles contain their own artwork; text styling cannot change their font.
            result.add(cue.text==null?cue:text(cue.text));
        }
        return result;
    }
}
