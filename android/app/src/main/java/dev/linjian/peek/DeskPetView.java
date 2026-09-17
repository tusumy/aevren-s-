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
 * Designed as a low, fluffy black cat with bright green eyes and a tiny Y bell.
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

        drawShadow(canvas, w, h);
        drawTail(canvas, w, h);
        drawBody(canvas, w, h);
        drawPaws(canvas, w, h);
        drawHead(canvas, w, h);
        drawFace(canvas, w, h);
        drawCollar(canvas, w, h);

        canvas.restore();
    }

    private void drawShadow(Canvas c, float w, float h) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x26000000);
        p.setShadowLayer(h * .025f, 0f, h * .012f, 0x33000000);
        c.drawOval(new RectF(w * .12f, h * .78f, w * .92f, h * .95f), p);
        p.clearShadowLayer();
    }

    private void drawBody(Canvas c, float w, float h) {
        Path body = new Path();
        body.moveTo(w * .19f, h * .68f);
        body.cubicTo(w * .22f, h * .46f, w * .43f, h * .39f, w * .66f, h * .46f);
        body.cubicTo(w * .87f, h * .51f, w * .92f, h * .70f, w * .83f, h * .82f);
        body.cubicTo(w * .73f, h * .91f, w * .38f, h * .90f, w * .23f, h * .83f);
        body.cubicTo(w * .16f, h * .79f, w * .15f, h * .73f, w * .19f, h * .68f);
        body.close();

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF25282C);
        p.setShadowLayer(h * .028f, 0f, h * .012f, 0x30000000);
        c.drawPath(body, p);
        p.clearShadowLayer();

        p.setColor(0xFF31353A);
        c.drawOval(new RectF(w * .35f, h * .52f, w * .73f, h * .77f), p);
    }

    private void drawTail(Canvas c, float w, float h) {
        stroke.setStrokeWidth(h * .12f);
        stroke.setColor(0xFF26292D);
        Path tail = new Path();
        tail.moveTo(w * .72f, h * .73f);
        tail.cubicTo(w * .91f, h * .61f, w * .95f, h * .45f, w * .84f, h * .37f);
        c.drawPath(tail, stroke);

        stroke.setStrokeWidth(h * .055f);
        stroke.setColor(0xFF383C41);
        Path tailHi = new Path();
        tailHi.moveTo(w * .77f, h * .67f);
        tailHi.cubicTo(w * .89f, h * .57f, w * .90f, h * .48f, w * .84f, h * .43f);
        c.drawPath(tailHi, stroke);
    }

    private void drawPaws(Canvas c, float w, float h) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF202327);
        c.drawOval(new RectF(w * .28f, h * .72f, w * .45f, h * .88f), p);
        c.drawOval(new RectF(w * .43f, h * .72f, w * .60f, h * .88f), p);

        stroke.setStrokeWidth(h * .012f);
        stroke.setColor(0xFF454A50);
        c.drawLine(w * .36f, h * .80f, w * .35f, h * .86f, stroke);
        c.drawLine(w * .51f, h * .80f, w * .50f, h * .86f, stroke);
    }

    private void drawHead(Canvas c, float w, float h) {
        float cx = w * .43f;
        float cy = h * .49f;
        float rx = w * .22f;
        float ry = h * .28f;

        Path ears = new Path();
        float twitch = earTwitch * h * .018f;
        ears.moveTo(cx - rx * .78f, cy - ry * .45f);
        ears.lineTo(cx - rx * .58f, cy - ry * 1.08f - twitch);
        ears.lineTo(cx - rx * .12f, cy - ry * .68f);
        ears.lineTo(cx + rx * .22f, cy - ry * .70f);
        ears.lineTo(cx + rx * .66f, cy - ry * 1.03f + twitch);
        ears.lineTo(cx + rx * .80f, cy - ry * .36f);
        ears.close();

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF24272B);
        c.drawPath(ears, p);

        Path innerL = new Path();
        innerL.moveTo(cx - rx * .58f, cy - ry * .93f);
        innerL.lineTo(cx - rx * .46f, cy - ry * .58f);
        innerL.lineTo(cx - rx * .25f, cy - ry * .69f);
        innerL.close();
        p.setColor(0xFF5A4B50);
        c.drawPath(innerL, p);

        Path innerR = new Path();
        innerR.moveTo(cx + rx * .62f, cy - ry * .89f);
        innerR.lineTo(cx + rx * .50f, cy - ry * .57f);
        innerR.lineTo(cx + rx * .30f, cy - ry * .69f);
        innerR.close();
        c.drawPath(innerR, p);

        p.setColor(0xFF292D31);
        c.drawOval(new RectF(cx - rx, cy - ry * .70f, cx + rx, cy + ry), p);

        p.setColor(0xFF34393E);
        c.drawOval(new RectF(cx - rx * .62f, cy - ry * .30f, cx + rx * .58f, cy + ry * .52f), p);
    }

    private void drawFace(Canvas c, float w, float h) {
        float cx = w * .43f;
        float cy = h * .50f;
        float eyeY = cy - h * .035f;
        float eyeDX = w * .072f;

        boolean closed = state == STATE_BLINK || state == STATE_SLEEP;
        if (closed) {
            stroke.setStrokeWidth(h * .018f);
            stroke.setColor(0xFF9FCFAC);
            c.drawLine(cx - eyeDX - w * .035f, eyeY, cx - eyeDX + w * .035f, eyeY, stroke);
            c.drawLine(cx + eyeDX - w * .035f, eyeY, cx + eyeDX + w * .035f, eyeY, stroke);
        } else {
            drawEye(c, cx - eyeDX, eyeY, w * .050f, h * .055f);
            drawEye(c, cx + eyeDX, eyeY, w * .050f, h * .055f);
        }

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFB68B92);
        Path nose = new Path();
        nose.moveTo(cx, cy + h * .055f);
        nose.lineTo(cx - w * .020f, cy + h * .035f);
        nose.lineTo(cx + w * .020f, cy + h * .035f);
        nose.close();
        c.drawPath(nose, p);

        stroke.setStrokeWidth(h * .010f);
        stroke.setColor(0xFF8E989B);
        c.drawLine(cx - w * .07f, cy + h * .075f, cx - w * .18f, cy + h * .055f, stroke);
        c.drawLine(cx - w * .07f, cy + h * .095f, cx - w * .18f, cy + h * .105f, stroke);
        c.drawLine(cx + w * .07f, cy + h * .075f, cx + w * .18f, cy + h * .055f, stroke);
        c.drawLine(cx + w * .07f, cy + h * .095f, cx + w * .18f, cy + h * .105f, stroke);

        if (state == STATE_HAPPY) {
            stroke.setStrokeWidth(h * .012f);
            stroke.setColor(0xFFA8B0B2);
            RectF smile = new RectF(cx - w * .035f, cy + h * .055f, cx + w * .035f, cy + h * .13f);
            c.drawArc(smile, 15f, 150f, false, stroke);
        }
    }

    private void drawEye(Canvas c, float cx, float cy, float rx, float ry) {
        Path eye = new Path();
        eye.moveTo(cx - rx, cy);
        eye.cubicTo(cx - rx * .45f, cy - ry, cx + rx * .45f, cy - ry, cx + rx, cy);
        eye.cubicTo(cx + rx * .45f, cy + ry, cx - rx * .45f, cy + ry, cx - rx, cy);
        eye.close();

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF93D6A2);
        p.setShadowLayer(ry * .55f, 0f, 0f, 0x6693D6A2);
        c.drawPath(eye, p);
        p.clearShadowLayer();

        p.setColor(0xFF173C25);
        c.drawOval(new RectF(cx - rx * .17f, cy - ry * .72f, cx + rx * .17f, cy + ry * .72f), p);

        p.setColor(Color.WHITE);
        c.drawCircle(cx - rx * .26f, cy - ry * .35f, Math.min(rx, ry) * .16f, p);
    }

    private void drawCollar(Canvas c, float w, float h) {
        float cx = w * .43f;
        float y = h * .685f;

        stroke.setStrokeWidth(h * .016f);
        stroke.setColor(0xFFD4AF5D);
        c.drawLine(cx - w * .075f, y - h * .025f, cx - w * .018f, y + h * .005f, stroke);
        c.drawLine(cx + w * .075f, y - h * .025f, cx + w * .018f, y + h * .005f, stroke);

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFE0B857);
        c.drawCircle(cx, y + h * .025f, h * .055f, p);

        p.setColor(0xFF4E452E);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(h * .065f);
        p.setFakeBoldText(true);
        c.drawText("Y", cx, y + h * .048f, p);
        p.setFakeBoldText(false);
    }
}
