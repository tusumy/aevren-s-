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

/** Head-only, front-facing silly black cat desk pet. */
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

        float bob = breath * h * .005f;
        float peekDrop = peeking ? h * .06f : 0f;
        canvas.save();
        canvas.translate(0f, bob + peekDrop);
        drawAccent(canvas, w, h);
        drawHead(canvas, w, h);
        drawFace(canvas, w, h);
        drawCollar(canvas, w, h);
        canvas.restore();
    }

    private void drawAccent(Canvas c, float w, float h) {
        stroke.setStrokeWidth(h * .014f);
        stroke.setColor(0xFF8FC8A4);
        c.drawLine(w * .12f, h * .32f, w * .18f, h * .25f, stroke);
        c.drawLine(w * .16f, h * .38f, w * .24f, h * .31f, stroke);
        c.drawLine(w * .82f, h * .31f, w * .88f, h * .25f, stroke);
        c.drawLine(w * .80f, h * .39f, w * .89f, h * .34f, stroke);
    }

    private void drawHead(Canvas c, float w, float h) {
        float cx = w * .50f;
        float cy = h * .56f;
        float rx = w * .30f;
        float ry = h * .34f;
        float twitch = earTwitch * h * .018f;

        Path ears = new Path();
        ears.moveTo(cx - rx * .88f, cy - ry * .18f);
        ears.lineTo(cx - rx * .62f, cy - ry * 1.10f - twitch);
        ears.lineTo(cx - rx * .18f, cy - ry * .60f);
        ears.lineTo(cx + rx * .18f, cy - ry * .60f);
        ears.lineTo(cx + rx * .62f, cy - ry * 1.08f + twitch);
        ears.lineTo(cx + rx * .88f, cy - ry * .18f);
        ears.close();

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF24272B);
        c.drawPath(ears, p);

        Path innerL = new Path();
        innerL.moveTo(cx - rx * .60f, cy - ry * .96f);
        innerL.lineTo(cx - rx * .47f, cy - ry * .54f);
        innerL.lineTo(cx - rx * .25f, cy - ry * .66f);
        innerL.close();
        p.setColor(0xFF5B4E53);
        c.drawPath(innerL, p);

        Path innerR = new Path();
        innerR.moveTo(cx + rx * .60f, cy - ry * .94f);
        innerR.lineTo(cx + rx * .47f, cy - ry * .54f);
        innerR.lineTo(cx + rx * .25f, cy - ry * .66f);
        innerR.close();
        c.drawPath(innerR, p);

        p.setColor(0xFF292D31);
        p.setShadowLayer(h * .022f, 0f, h * .008f, 0x33000000);
        c.drawOval(new RectF(cx - rx, cy - ry * .68f, cx + rx, cy + ry * .82f), p);
        p.clearShadowLayer();

        p.setColor(0xFF353A3F);
        c.drawOval(new RectF(cx - rx * .72f, cy - ry * .04f, cx + rx * .72f, cy + ry * .62f), p);
    }

    private void drawFace(Canvas c, float w, float h) {
        float cx = w * .50f;
        float cy = h * .55f;
        float eyeY = cy - h * .035f;
        float eyeDX = w * .115f;
        boolean closed = state == STATE_BLINK || state == STATE_SLEEP;

        if (closed) {
            stroke.setStrokeWidth(h * .018f);
            stroke.setColor(0xFF9FD8B0);
            c.drawLine(cx - eyeDX - w * .040f, eyeY, cx - eyeDX + w * .040f, eyeY, stroke);
            c.drawLine(cx + eyeDX - w * .040f, eyeY, cx + eyeDX + w * .040f, eyeY, stroke);
        } else {
            drawEye(c, cx - eyeDX, eyeY, w * .067f, h * .082f);
            drawEye(c, cx + eyeDX, eyeY, w * .067f, h * .082f);
        }

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFB98C95);
        Path nose = new Path();
        nose.moveTo(cx, cy + h * .085f);
        nose.lineTo(cx - w * .021f, cy + h * .061f);
        nose.lineTo(cx + w * .021f, cy + h * .061f);
        nose.close();
        c.drawPath(nose, p);

        stroke.setStrokeWidth(h * .010f);
        stroke.setColor(0xFF8E989B);
        c.drawLine(cx - w * .060f, cy + h * .104f, cx - w * .190f, cy + h * .078f, stroke);
        c.drawLine(cx - w * .060f, cy + h * .132f, cx - w * .190f, cy + h * .145f, stroke);
        c.drawLine(cx + w * .060f, cy + h * .104f, cx + w * .190f, cy + h * .078f, stroke);
        c.drawLine(cx + w * .060f, cy + h * .132f, cx + w * .190f, cy + h * .145f, stroke);

        stroke.setColor(0xFFA8B0B2);
        if (state == STATE_HAPPY) {
            RectF smile = new RectF(cx - w * .044f, cy + h * .095f, cx + w * .044f, cy + h * .175f);
            c.drawArc(smile, 18f, 144f, false, stroke);
        } else {
            c.drawLine(cx, cy + h * .086f, cx, cy + h * .130f, stroke);
            c.drawArc(new RectF(cx - w * .034f, cy + h * .120f, cx, cy + h * .158f), 205f, 90f, false, stroke);
            c.drawArc(new RectF(cx, cy + h * .120f, cx + w * .034f, cy + h * .158f), 245f, 90f, false, stroke);
        }
    }

    private void drawEye(Canvas c, float cx, float cy, float rx, float ry) {
        Path eye = new Path();
        eye.moveTo(cx - rx, cy);
        eye.cubicTo(cx - rx * .94f, cy - ry, cx + rx * .94f, cy - ry, cx + rx, cy);
        eye.cubicTo(cx + rx * .94f, cy + ry, cx - rx * .94f, cy + ry, cx - rx, cy);
        eye.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF93D6A2);
        p.setShadowLayer(ry * .45f, 0f, 0f, 0x6693D6A2);
        c.drawPath(eye, p);
        p.clearShadowLayer();
        p.setColor(0xFF173C25);
        c.drawOval(new RectF(cx - rx * .17f, cy - ry * .72f, cx + rx * .17f, cy + ry * .72f), p);
        p.setColor(Color.WHITE);
        c.drawCircle(cx - rx * .28f, cy - ry * .33f, Math.min(rx, ry) * .18f, p);
        c.drawCircle(cx + rx * .12f, cy + ry * .17f, Math.min(rx, ry) * .08f, p);
    }

    private void drawCollar(Canvas c, float w, float h) {
        float cx = w * .50f;
        float y = h * .825f;
        stroke.setStrokeWidth(h * .012f);
        stroke.setColor(0xFFCACFD5);
        c.drawLine(cx - w * .055f, y - h * .012f, cx - w * .018f, y + h * .004f, stroke);
        c.drawLine(cx + w * .055f, y - h * .012f, cx + w * .018f, y + h * .004f, stroke);

        RectF tag = new RectF(cx - h * .028f, y, cx + h * .028f, y + h * .060f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFE6E9ED);
        c.drawRoundRect(tag, h * .010f, h * .010f, p);
        stroke.setStrokeWidth(h * .005f);
        stroke.setColor(0xFF9AA3AA);
        c.drawRoundRect(tag, h * .010f, h * .010f, stroke);
        p.setColor(0xFF506E5D);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(h * .045f);
        p.setFakeBoldText(true);
        c.drawText("Y", cx, y + h * .045f, p);
        p.setFakeBoldText(false);
    }
}
