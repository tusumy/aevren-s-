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

/**
 * Pure code-drawn desk pet. No sprite resources: appearance and motion live here.
 * Designed as a silly front-facing black cat with bright green eyes and a tiny Y tag.
 */
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

        float bob = breath * h * .006f;
        float peekDrop = peeking ? h * .10f : 0f;
        canvas.save();
        canvas.translate(0f, bob + peekDrop);

        drawAccent(canvas, w, h);
        drawShadow(canvas, w, h);
        drawTail(canvas, w, h);
        drawBody(canvas, w, h);
        drawPaws(canvas, w, h);
        drawHead(canvas, w, h);
        drawFace(canvas, w, h);
        drawCollar(canvas, w, h);

        canvas.restore();
    }

    private void drawAccent(Canvas c, float w, float h) {
        stroke.setStrokeWidth(h * .013f);
        stroke.setColor(0xFF8FC8A4);

        c.drawLine(w * .12f, h * .20f, w * .18f, h * .14f, stroke);
        c.drawLine(w * .18f, h * .24f, w * .26f, h * .17f, stroke);
        c.drawLine(w * .78f, h * .22f, w * .86f, h * .15f, stroke);
        c.drawLine(w * .82f, h * .28f, w * .90f, h * .24f, stroke);

        stroke.setStrokeWidth(h * .010f);
        stroke.setColor(0x668FC8A4);
        c.drawArc(new RectF(w * .08f, h * .77f, w * .28f, h * .91f), 210f, 95f, false, stroke);
        c.drawArc(new RectF(w * .74f, h * .77f, w * .94f, h * .91f), 235f, 95f, false, stroke);
    }

    private void drawShadow(Canvas c, float w, float h) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x26000000);
        p.setShadowLayer(h * .025f, 0f, h * .012f, 0x33000000);
        c.drawOval(new RectF(w * .16f, h * .82f, w * .88f, h * .96f), p);
        p.clearShadowLayer();
    }

    private void drawBody(Canvas c, float w, float h) {
        Path body = new Path();
        body.moveTo(w * .23f, h * .78f);
        body.cubicTo(w * .18f, h * .60f, w * .29f, h * .50f, w * .42f, h * .50f);
        body.cubicTo(w * .57f, h * .49f, w * .68f, h * .54f, w * .75f, h * .66f);
        body.cubicTo(w * .81f, h * .77f, w * .78f, h * .86f, w * .65f, h * .90f);
        body.cubicTo(w * .52f, h * .94f, w * .31f, h * .92f, w * .23f, h * .78f);
        body.close();

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF24272B);
        p.setShadowLayer(h * .024f, 0f, h * .010f, 0x26000000);
        c.drawPath(body, p);
        p.clearShadowLayer();

        p.setColor(0xFF31353A);
        c.drawOval(new RectF(w * .33f, h * .56f, w * .67f, h * .84f), p);
        p.setColor(0xFF3B4147);
        c.drawOval(new RectF(w * .38f, h * .60f, w * .62f, h * .82f), p);
    }

    private void drawTail(Canvas c, float w, float h) {
        stroke.setStrokeWidth(h * .095f);
        stroke.setColor(0xFF25282C);
        Path tail = new Path();
        tail.moveTo(w * .71f, h * .72f);
        tail.cubicTo(w * .90f, h * .65f, w * .92f, h * .50f, w * .84f, h * .39f);
        c.drawPath(tail, stroke);

        stroke.setStrokeWidth(h * .045f);
        stroke.setColor(0xFF363B40);
        Path tailHi = new Path();
        tailHi.moveTo(w * .75f, h * .68f);
        tailHi.cubicTo(w * .85f, h * .60f, w * .86f, h * .50f, w * .81f, h * .45f);
        c.drawPath(tailHi, stroke);
    }

    private void drawPaws(Canvas c, float w, float h) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF1D2024);
        c.drawRoundRect(new RectF(w * .27f, h * .73f, w * .43f, h * .90f), h * .05f, h * .05f, p);
        c.drawRoundRect(new RectF(w * .45f, h * .73f, w * .61f, h * .90f), h * .05f, h * .05f, p);

        p.setColor(0xFF3B4046);
        c.drawOval(new RectF(w * .30f, h * .83f, w * .40f, h * .89f), p);
        c.drawOval(new RectF(w * .48f, h * .83f, w * .58f, h * .89f), p);

        stroke.setStrokeWidth(h * .010f);
        stroke.setColor(0xFF50575D);
        c.drawLine(w * .35f, h * .81f, w * .35f, h * .87f, stroke);
        c.drawLine(w * .53f, h * .81f, w * .53f, h * .87f, stroke);
    }

    private void drawHead(Canvas c, float w, float h) {
        float cx = w * .44f;
        float cy = h * .44f;
        float rx = w * .25f;
        float ry = h * .25f;

        Path ears = new Path();
        float twitch = earTwitch * h * .016f;
        ears.moveTo(cx - rx * .88f, cy - ry * .06f);
        ears.lineTo(cx - rx * .64f, cy - ry * 1.02f - twitch);
        ears.lineTo(cx - rx * .18f, cy - ry * .42f);
        ears.lineTo(cx + rx * .18f, cy - ry * .42f);
        ears.lineTo(cx + rx * .63f, cy - ry * .98f + twitch);
        ears.lineTo(cx + rx * .90f, cy - ry * .03f);
        ears.close();

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF24272B);
        c.drawPath(ears, p);

        Path innerL = new Path();
        innerL.moveTo(cx - rx * .61f, cy - ry * .88f);
        innerL.lineTo(cx - rx * .48f, cy - ry * .45f);
        innerL.lineTo(cx - rx * .28f, cy - ry * .57f);
        innerL.close();
        p.setColor(0xFF5C4F54);
        c.drawPath(innerL, p);

        Path innerR = new Path();
        innerR.moveTo(cx + rx * .60f, cy - ry * .84f);
        innerR.lineTo(cx + rx * .46f, cy - ry * .45f);
        innerR.lineTo(cx + rx * .26f, cy - ry * .57f);
        innerR.close();
        c.drawPath(innerR, p);

        p.setColor(0xFF292D31);
        c.drawOval(new RectF(cx - rx, cy - ry * .62f, cx + rx, cy + ry * .98f), p);

        p.setColor(0xFF363B40);
        c.drawOval(new RectF(cx - rx * .68f, cy + ry * .02f, cx + rx * .68f, cy + ry * .78f), p);

        p.setColor(0x22FFFFFF);
        c.drawOval(new RectF(cx - rx * .56f, cy - ry * .30f, cx - rx * .06f, cy + ry * .06f), p);
    }

    private void drawFace(Canvas c, float w, float h) {
        float cx = w * .44f;
        float cy = h * .43f;
        float eyeY = cy + h * .010f;
        float eyeDX = w * .094f;

        boolean closed = state == STATE_BLINK || state == STATE_SLEEP;
        if (closed) {
            stroke.setStrokeWidth(h * .017f);
            stroke.setColor(0xFF9FD8B0);
            c.drawLine(cx - eyeDX - w * .035f, eyeY, cx - eyeDX + w * .035f, eyeY, stroke);
            c.drawLine(cx + eyeDX - w * .035f, eyeY, cx + eyeDX + w * .035f, eyeY, stroke);
        } else {
            drawEye(c, cx - eyeDX, eyeY, w * .060f, h * .074f);
            drawEye(c, cx + eyeDX, eyeY, w * .060f, h * .074f);
        }

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFB98C95);
        Path nose = new Path();
        nose.moveTo(cx, cy + h * .105f);
        nose.lineTo(cx - w * .020f, cy + h * .082f);
        nose.lineTo(cx + w * .020f, cy + h * .082f);
        nose.close();
        c.drawPath(nose, p);

        stroke.setStrokeWidth(h * .011f);
        stroke.setColor(0xFF8E989B);
        c.drawLine(cx - w * .06f, cy + h * .120f, cx - w * .17f, cy + h * .098f, stroke);
        c.drawLine(cx - w * .06f, cy + h * .146f, cx - w * .17f, cy + h * .154f, stroke);
        c.drawLine(cx + w * .06f, cy + h * .120f, cx + w * .17f, cy + h * .098f, stroke);
        c.drawLine(cx + w * .06f, cy + h * .146f, cx + w * .17f, cy + h * .154f, stroke);

        stroke.setColor(0xFFA5AFB1);
        if (state == STATE_HAPPY) {
            RectF smile = new RectF(cx - w * .044f, cy + h * .090f, cx + w * .044f, cy + h * .175f);
            c.drawArc(smile, 18f, 144f, false, stroke);
        } else {
            c.drawLine(cx, cy + h * .102f, cx, cy + h * .145f, stroke);
            c.drawArc(new RectF(cx - w * .035f, cy + h * .133f, cx, cy + h * .168f), 205f, 90f, false, stroke);
            c.drawArc(new RectF(cx, cy + h * .133f, cx + w * .035f, cy + h * .168f), 245f, 90f, false, stroke);
        }
    }

    private void drawEye(Canvas c, float cx, float cy, float rx, float ry) {
        Path eye = new Path();
        eye.moveTo(cx - rx, cy);
        eye.cubicTo(cx - rx * .92f, cy - ry, cx + rx * .92f, cy - ry, cx + rx, cy);
        eye.cubicTo(cx + rx * .92f, cy + ry, cx - rx * .92f, cy + ry, cx - rx, cy);
        eye.close();

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF93D6A2);
        p.setShadowLayer(ry * .50f, 0f, 0f, 0x6693D6A2);
        c.drawPath(eye, p);
        p.clearShadowLayer();

        p.setColor(0xFF173C25);
        c.drawOval(new RectF(cx - rx * .18f, cy - ry * .75f, cx + rx * .18f, cy + ry * .75f), p);

        p.setColor(Color.WHITE);
        c.drawCircle(cx - rx * .26f, cy - ry * .30f, Math.min(rx, ry) * .18f, p);
        c.drawCircle(cx + rx * .12f, cy + ry * .16f, Math.min(rx, ry) * .08f, p);
    }

    private void drawCollar(Canvas c, float w, float h) {
        float cx = w * .44f;
        float y = h * .665f;

        stroke.setStrokeWidth(h * .016f);
        stroke.setColor(0xFFCACFD5);
        c.drawLine(cx - w * .082f, y - h * .018f, cx - w * .020f, y + h * .008f, stroke);
        c.drawLine(cx + w * .082f, y - h * .018f, cx + w * .020f, y + h * .008f, stroke);

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFE6E9ED);
        c.drawRoundRect(new RectF(cx - w * .035f, y + h * .000f, cx + w * .035f, y + h * .070f), h * .012f, h * .012f, p);

        stroke.setStrokeWidth(h * .006f);
        stroke.setColor(0xFF9AA3AA);
        c.drawRoundRect(new RectF(cx - w * .035f, y + h * .000f, cx + w * .035f, y + h * .070f), h * .012f, h * .012f, stroke);

        p.setColor(0xFF506E5D);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(h * .052f);
        p.setFakeBoldText(true);
        c.drawText("Y", cx, y + h * .050f, p);
        p.setFakeBoldText(false);
    }
}
