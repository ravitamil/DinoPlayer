package com.brouken.player;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** One drawing surface, with animation running only while the loading view is visible. */
public final class LibrarySkeletonView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator animator;
    private float phase;

    public LibrarySkeletonView(Context context, AttributeSet attrs) { super(context, attrs); }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); updateAnimation(); }
    @Override protected void onDetachedFromWindow() { stopAnimation(); super.onDetachedFromWindow(); }
    @Override protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        updateAnimation();
    }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateAnimation();
    }

    private void updateAnimation() {
        if (!isAttachedToWindow() || !isShown() || getWindowVisibility() != VISIBLE) {
            stopAnimation();
        } else if (animator == null) {
            animator = ValueAnimator.ofFloat(-1, 2);
            animator.setDuration(1500);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.addUpdateListener(value -> { phase = (float) value.getAnimatedValue(); invalidate(); });
            animator.start();
        }
    }

    private void stopAnimation() {
        if (animator != null) { animator.cancel(); animator = null; }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float d = getResources().getDisplayMetrics().density;
        float width = getWidth();
        canvas.drawColor(getResources().getColor(R.color.app_background));
        for (float y = 8 * d; y < getHeight(); y += 106 * d) {
            paint.setShader(null);
            paint.setColor(0xff1c1c1c);
            canvas.drawRoundRect(16*d, y, width-16*d, y+96*d, 20*d, 20*d, paint);
            float x = phase * width;
            paint.setShader(new LinearGradient(x-width/2, 0, x+width/2, 0,
                    new int[]{0xff292929, 0xff383838, 0xff292929}, null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(28*d, y+12*d, 120*d, y+84*d, 12*d, 12*d, paint);
            canvas.drawRoundRect(136*d, y+24*d, Math.max(140*d, width-50*d), y+38*d, 6*d, 6*d, paint);
            canvas.drawRoundRect(136*d, y+52*d, Math.max(140*d, width-85*d), y+62*d, 5*d, 5*d, paint);
        }
    }
}
