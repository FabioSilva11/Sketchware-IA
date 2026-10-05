package pro.sketchware.chat;


import pro.sketchware.R;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

/**
 * Três pontos pulsantes usados durante o processamento do chat.
 *
 * A implementação de onMeasure() é obrigatória: um View customizado vazio com
 * layout_width/layout_height="wrap_content" pode consumir todo o espaço
 * disponível, fazendo o painel de status cobrir a tela inteira.
 */
public class KelivoTypingDotsView extends View {

    private static final int DOT_COUNT = 3;
    private static final long ANIM_DURATION_MS = 1100L;
    private static final float DOT_SIZE_DP = 9f;
    private static final float DOT_SPACING_DP = 6f;
    private static final float VIEW_HEIGHT_DP = 16f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator animator;
    private float animProgress;
    private final int dotColor;
    private final float dotRadius;
    private final float dotSpacing;

    public KelivoTypingDotsView(Context context) {
        this(context, null);
    }

    public KelivoTypingDotsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        dotColor = context.getColor(R.color.chat_accent);
        float density = context.getResources().getDisplayMetrics().density;
        dotRadius = DOT_SIZE_DP * density / 2f;
        dotSpacing = DOT_SPACING_DP * density;
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;
        int desiredHeight = Math.round(VIEW_HEIGHT_DP * density);
        int desiredWidth = Math.round(
                DOT_COUNT * dotRadius * 2f + (DOT_COUNT - 1) * dotSpacing
        );

        int measuredWidth = resolveSize(desiredWidth, widthMeasureSpec);
        int measuredHeight = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float centerY = getHeight() / 2f;
        float x = dotRadius;

        for (int i = 0; i < DOT_COUNT; i++) {
            float wave = dotValue(i);
            float scale = 0.85f + 0.15f * wave;
            float opacity = 0.45f + 0.45f * wave;
            paint.setColor(dotColor);
            paint.setAlpha(Math.round(opacity * 255f));
            canvas.drawCircle(x, centerY, dotRadius * scale, paint);
            x += dotRadius * 2f + dotSpacing;
        }
    }

    private float dotValue(int index) {
        double phase = (animProgress - index * 0.22f) * 2d * Math.PI;
        return (float) ((Math.sin(phase) + 1d) / 2d);
    }

    public void startAnimation() {
        if (animator != null && animator.isRunning()) {
            return;
        }

        if (animator == null) {
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(ANIM_DURATION_MS);
            animator.setInterpolator(new LinearInterpolator());
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.addUpdateListener(animation -> {
                animProgress = (float) animation.getAnimatedValue();
                invalidate();
            });
        }

        animator.start();
    }

    public void stopAnimation() {
        if (animator != null) {
            animator.cancel();
        }
        animProgress = 0f;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }
}
