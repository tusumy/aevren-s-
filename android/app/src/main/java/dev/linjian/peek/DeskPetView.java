package dev.linjian.peek;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.view.View;

import java.util.Random;

/** Small head-only, front-facing silly black cat desk pet. */
public class DeskPetView extends View {
    private static final int STATE_IDLE = 0;
    private static final int STATE_BLINK = 1;
    private static final int STATE_SLEEP = 2;
    private static final int STATE_PEEK = 3;
    private static final int STATE_HAPPY = 4;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler();
    private final Random random = new Random();

    private int state = STATE_IDLE;
    private boolean looking;
    private boolean peeking;
    private boolean released;
    private float breath;
    private boolean breathUp = true;
    private float earTwitch;

    public DeskPetView(Context context) {
        super(context);
        setWillNotDraw(false);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setBackgroundColor(Color.TRANSPARENT);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        handler.post(blinkLoop);
        handler.post(breatheLoop);
        handler.post(ambientLoop);
    }

    public void lookAtUser(long durationMs) {
        looking = true;
        peeking = false;
        state = STATE_PEEK;
        invalidate();
        handler.removeCallbacks(stopLooking);
        handler.postDelayed(stopLooking, durationMs);
    }

    public void earTwitch() {
        if (released) return;
        earTwitch = 1f;
        if (!looking) state = STATE_HAPPY;
        invalidate();
        handler.postDelayed(() -> {
            if (released) return;
            earTwitch = 0f;
            if (!looking && !peeking) state = STATE_IDLE;
            invalidate();
        }, 420L);
    }

    public void setWatchMode(boolean watchMode) {
        if (watchMode) lookAtUser(2200L);
    }

    public void peek(long durationMs) {
        if (released || looking) return;
        peeking = true;
        state = STATE_PEEK;
        invalidate();
        handler.postDelayed(() -> {
            if (released) return;
            peeking = false;
            if (!looking) state = STATE_IDLE;
            invalidate();
        }, durationMs);
    }

    public void release() {
        released = true;
        handler.removeCallbacksAndMessages(null);
    }

    private final Runnable stopLooking = () -> {
        looking = false;
        if (!peeking) state = STATE_IDLE;
        invalidate();
    };

    private final Runnable blinkLoop = new Runnable() {
        @Override public void run() {
            if (released) return;
            if (!looking && !peeking && state == STATE_IDLE) {
                state = STATE_BLINK;
                invalidate();
                handler.postDelayed(() -> {
                    if (!released && !looking && !peeking && state == STATE_BLINK) {
                        state = STATE_IDLE;
                        invalidate();
                    }
                }, 130L);
            }
            handler.postDelayed(this, 2500L + random.nextInt(3500));
        }
    };

    private final Runnable ambientLoop = new Runnable() {
        @Override public void run() {
            if (released) return;
            if (!looking && !peeking && state == STATE_IDLE) {
                int roll = random.nextInt(9);
                if (roll == 0) {
                    state = STATE_SLEEP;
                    invalidate();
                    handler.postDelayed(() -> {
                        if (!released && !looking && !peeking && state == STATE_SLEEP) {
                            state = STATE_IDLE;
                            invalidate();
                        }
                    }, 950L);
                } else if (roll == 1) {
                    earTwitch();
                } else if (roll == 2) {
                    peek(1050L);
                }
            }
            handler.postDelayed(this, 5000L + random.nextInt(5200));
        }
    };

    private final Runnable breatheLoop = new Runnable() {
        @Override public void run() {
            if (released) return;
            breath += breathUp ? .045f : -.045f;
            if (breath >= 1f) { breath = 1f; breathUp = false; }
            if (breath <= 0f) { breath = 0f; breathUp = true; }
            invalidate();
            handler.postDelayed(this, 95L);
        }
    };

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float bob = breath * h * .004f;
        float peekDrop = peeking ? h * .045f : 0f;
        canvas.save();
        canvas.translate(0f, bob + peekDrop);
        drawAccent(canvas, w, h);
        drawHead(canvas, w, h);
        drawFace(canvas, w, h);
        drawYMark(canvas, w, h);
        canvas.restore();
    }

    private void drawAccent(Canvas c, float w, float h) {
        stroke.setStrokeWidth(h * .011f);
        stroke.setColor(0xFF8FC8A4);
        c.drawLine(w * .25f, h * .40f, w * .20f, h * .35f, stroke);
        c.drawLine(w * .27f, h * .45f, w * .20f, h * .43f, stroke);
        c.drawLine(w * .75f, h * .40f, w * .80f, h * .35f, stroke);
        c.drawLine(w * .73f, h * .45f, w * .80f, h * .43f, stroke);
    }

    private void drawHead(Canvas c, float w, float h) {
        float cx = w * .50f;
        float cy = h * .64f;
        float rx = w * .225f;
        float ry = h * .245f;
        float twitch = earTwitch * h * .014f;

        Path ears = new Path();
        ears.moveTo(cx - rx * .86f, cy - ry * .36f);
        ears.lineTo(cx - rx * .60f, cy - ry * 1.28f - twitch);
        ears.lineTo(cx - rx * .18f, cy - ry * .70f);
        ears.lineTo(cx + rx * .18f, cy - ry * .70f);
        ears.lineTo(cx + rx * .60f, cy - ry * 1.28f + twitch);
        ears.lineTo(cx + rx * .86f, cy - ry * .36f);
        ears.close();

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF23262A);
        c.drawPath(ears, p);

        Path innerL = new Path();
        innerL.moveTo(cx - rx * .58f, cy - ry * 1.12f);
        innerL.lineTo(cx - rx * .45f, cy - ry * .68f);
        innerL.lineTo(cx - rx * .26f, cy - ry * .80f);
        innerL.close();
        p.setColor(0xFF5B4E53);
        c.drawPath(innerL, p);

        Path innerR = new Path();
        innerR.moveTo(cx + rx * .58f, cy - ry * 1.12f);
        innerR.lineTo(cx + rx * .45f, cy - ry * .68f);
        innerR.lineTo(cx + rx * .26f, cy - ry * .80f);
        innerR.close();
        c.drawPath(innerR, p);

        p.setColor(0xFF292D31);
        p.setShadowLayer(h * .016f, 0f, h * .006f, 0x33000000);
        c.drawOval(new RectF(cx - rx, cy - ry * .72f, cx + rx, cy + ry * .72f), p);
        p.clearShadowLayer();

        p.setColor(0xFF33383D);
        c.drawOval(new RectF(cx - rx * .48f, cy + ry * .08f, cx + rx * .48f, cy + ry * .46f), p);
    }

    private void drawFace(Canvas c, float w, float h) {
        float cx = w * .50f;
        float cy = h * .635f;
        float eyeY = cy - h * .020f;
        float eyeDX = w * .072f;
        boolean closed = state == STATE_BLINK || state == STATE_SLEEP;

        if (closed) {
            stroke.setStrokeWidth(h * .014f);
            stroke.setColor(0xFF9FD8B0);
            c.drawLine(cx - eyeDX - w * .028f, eyeY, cx - eyeDX + w * .028f, eyeY, stroke);
            c.drawLine(cx + eyeDX - w * .028f, eyeY, cx + eyeDX + w * .028f, eyeY, stroke);
        } else {
            drawEye(c, cx - eyeDX, eyeY, w * .039f, h * .048f);
            drawEye(c, cx + eyeDX, eyeY, w * .039f, h * .048f);
        }

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFB98C95);
        Path nose = new Path();
        nose.moveTo(cx, cy + h * .055f);
        nose.lineTo(cx - w * .013f, cy + h * .040f);
        nose.lineTo(cx + w * .013f, cy + h * .040f);
        nose.close();
        c.drawPath(nose, p);

        stroke.setStrokeWidth(h * .008f);
        stroke.setColor(0xFF8E989B);
        c.drawLine(cx - w * .038f, cy + h * .070f, cx - w * .118f, cy + h * .058f, stroke);
        c.drawLine(cx - w * .038f, cy + h * .088f, cx - w * .116f, cy + h * .096f, stroke);
        c.drawLine(cx + w * .038f, cy + h * .070f, cx + w * .118f, cy + h * .058f, stroke);
        c.drawLine(cx + w * .038f, cy + h * .088f, cx + w * .116f, cy + h * .096f, stroke);

        stroke.setStrokeWidth(h * .009f);
        stroke.setColor(0xFFA8B0B2);
        if (state == STATE_HAPPY) {
            RectF smile = new RectF(cx - w * .030f, cy + h * .064f, cx + w * .030f, cy + h * .118f);
            c.drawArc(smile, 18f, 144f, false, stroke);
        } else {
            c.drawLine(cx, cy + h * .056f, cx, cy + h * .082f, stroke);
            c.drawArc(new RectF(cx - w * .022f, cy + h * .076f, cx, cy + h * .100f), 205f, 90f, false, stroke);
            c.drawArc(new RectF(cx, cy + h * .076f, cx + w * .022f, cy + h * .100f), 245f, 90f, false, stroke);
        }
    }

    private void drawEye(Canvas c, float cx, float cy, float rx, float ry) {
        Path eye = new Path();
        eye.moveTo(cx - rx, cy);
        eye.cubicTo(cx - rx * .70f, cy - ry, cx + rx * .70f, cy - ry, cx + rx, cy);
        eye.cubicTo(cx + rx * .70f, cy + ry, cx - rx * .70f, cy + ry, cx - rx, cy);
        eye.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF93D6A2);
        p.setShadowLayer(ry * .35f, 0f, 0f, 0x5593D6A2);
        c.drawPath(eye, p);
        p.clearShadowLayer();
        p.setColor(0xFF173C25);
        c.drawOval(new RectF(cx - rx * .15f, cy - ry * .66f, cx + rx * .15f, cy + ry * .66f), p);
        p.setColor(Color.WHITE);
        c.drawCircle(cx - rx * .28f, cy - ry * .30f, Math.min(rx, ry) * .16f, p);
    }

    private void drawYMark(Canvas c, float w, float h) {
        float cx = w * .50f;
        float topY = h * .806f;
        float splitY = h * .830f;
        float bottomY = h * .868f;

        stroke.setStrokeWidth(h * .009f);
        stroke.setColor(0xFFD9DEE1);
        c.drawLine(cx - w * .032f, topY, cx, splitY, stroke);
        c.drawLine(cx + w * .032f, topY, cx, splitY, stroke);
        c.drawLine(cx, splitY, cx, bottomY, stroke);

        stroke.setStrokeWidth(h * .0035f);
        stroke.setColor(0xFF79C18E);
        c.drawLine(cx - w * .014f, splitY + h * .012f, cx, splitY + h * .026f, stroke);
        c.drawLine(cx + w * .014f, splitY + h * .012f, cx, splitY + h * .026f, stroke);
    }
}
