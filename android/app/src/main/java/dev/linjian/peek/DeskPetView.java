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

/** Low-profile front-facing black cat desk pet, lying on a screen edge with paws forward. */
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
        drawShadow(canvas, w, h);
        drawTail(canvas, w, h);
        drawBody(canvas, w, h);
        drawPaws(canvas, w, h);
        drawHead(canvas, w, h);
        drawFace(canvas, w, h);
        drawYMark(canvas, w, h);
        canvas.restore();
    }

    private void drawAccent(Canvas c, float w, float h) {
        stroke.setStrokeWidth(h * .010f);
        stroke.setColor(0xFF86C99A);
        c.drawLine(w * .16f, h * .43f, w * .12f, h * .39f, stroke);
        c.drawLine(w * .17f, h * .48f, w * .11f, h * .47f, stroke);
        c.drawLine(w * .80f, h * .42f, w * .85f, h * .37f, stroke);
        c.drawLine(w * .80f, h * .48f, w * .87f, h * .46f, stroke);
    }

    private void drawShadow(Canvas c, float w, float h) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x22000000);
        c.drawOval(new RectF(w * .24f, h * .76f, w * .79f, h * .84f), p);
    }

    private void drawTail(Canvas c, float w, float h) {
        stroke.setStrokeWidth(h * .075f);
        stroke.setColor(0xFF23262A);
        Path tail = new Path();
        tail.moveTo(w * .66f, h * .66f);
        tail.cubicTo(w * .80f, h * .67f, w * .88f, h * .58f, w * .82f, h * .50f);
        tail.cubicTo(w * .77f, h * .43f, w * .69f, h * .48f, w * .74f, h * .54f);
        c.drawPath(tail, stroke);

        stroke.setStrokeWidth(h * .020f);
        stroke.setColor(0xFF34383D);
        Path hi = new Path();
        hi.moveTo(w * .69f, h * .63f);
        hi.cubicTo(w * .78f, h * .62f, w * .82f, h * .56f, w * .78f, h * .51f);
        c.drawPath(hi, stroke);
    }

    private void drawBody(Canvas c, float w, float h) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF25282C);

        // Body stays low and mostly hidden behind the head, like a cat lying on a ledge.
        Path body = new Path();
        body.moveTo(w * .30f, h * .55f);
        body.cubicTo(w * .29f, h * .64f, w * .34f, h * .72f, w * .45f, h * .75f);
        body.cubicTo(w * .56f, h * .78f, w * .69f, h * .73f, w * .73f, h * .64f);
        body.cubicTo(w * .74f, h * .58f, w * .68f, h * .53f, w * .60f, h * .52f);
        body.cubicTo(w * .48f, h * .50f, w * .36f, h * .51f, w * .30f, h * .55f);
        body.close();
        c.drawPath(body, p);

        p.setColor(0xFF34383D);
        c.drawOval(new RectF(w * .39f, h * .60f, w * .63f, h * .73f), p);
    }

    private void drawPaws(Canvas c, float w, float h) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF2A2D31);

        // Two soft front paws resting on the screen edge.
        c.drawOval(new RectF(w * .34f, h * .65f, w * .47f, h * .78f), p);
        c.drawOval(new RectF(w * .50f, h * .65f, w * .63f, h * .78f), p);

        p.setColor(0xFF393D42);
        c.drawOval(new RectF(w * .365f, h * .70f, w * .445f, h * .765f), p);
        c.drawOval(new RectF(w * .525f, h * .70f, w * .605f, h * .765f), p);

        stroke.setStrokeWidth(h * .006f);
        stroke.setColor(0xFF52585D);
        c.drawLine(w * .385f, h * .735f, w * .385f, h * .762f, stroke);
        c.drawLine(w * .415f, h * .735f, w * .415f, h * .762f, stroke);
        c.drawLine(w * .545f, h * .735f, w * .545f, h * .762f, stroke);
        c.drawLine(w * .575f, h * .735f, w * .575f, h * .762f, stroke);
    }

    private void drawHead(Canvas c, float w, float h) {
        float cx = w * .485f;
        float cy = h * .43f;
        float rx = w * .245f;
        float ry = h * .235f;
        float twitch = earTwitch * h * .012f;

        Path head = new Path();
        head.moveTo(cx - rx * .88f, cy - ry * .18f);
        head.lineTo(cx - rx * .66f, cy - ry * 1.02f - twitch);
        head.lineTo(cx - rx * .28f, cy - ry * .68f);
        head.cubicTo(cx - rx * .08f, cy - ry * .78f, cx + rx * .08f, cy - ry * .78f, cx + rx * .28f, cy - ry * .68f);
        head.lineTo(cx + rx * .64f, cy - ry * 1.02f + twitch);
        head.lineTo(cx + rx * .88f, cy - ry * .18f);
        head.lineTo(cx + rx * .96f, cy + ry * .18f);
        head.lineTo(cx + rx * .84f, cy + ry * .32f);
        head.lineTo(cx + rx * .92f, cy + ry * .40f);
        head.lineTo(cx + rx * .72f, cy + ry * .46f);
        head.cubicTo(cx + rx * .45f, cy + ry * .70f, cx - rx * .45f, cy + ry * .70f, cx - rx * .72f, cy + ry * .46f);
        head.lineTo(cx - rx * .92f, cy + ry * .40f);
        head.lineTo(cx - rx * .84f, cy + ry * .32f);
        head.lineTo(cx - rx * .96f, cy + ry * .18f);
        head.close();

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF24272B);
        p.setShadowLayer(h * .014f, 0f, h * .004f, 0x2A000000);
        c.drawPath(head, p);
        p.clearShadowLayer();

        Path innerL = new Path();
        innerL.moveTo(cx - rx * .62f, cy - ry * .90f);
        innerL.lineTo(cx - rx * .49f, cy - ry * .58f);
        innerL.lineTo(cx - rx * .31f, cy - ry * .69f);
        innerL.close();
        p.setColor(0xFF65545C);
        c.drawPath(innerL, p);

        Path innerR = new Path();
        innerR.moveTo(cx + rx * .60f, cy - ry * .90f);
        innerR.lineTo(cx + rx * .48f, cy - ry * .58f);
        innerR.lineTo(cx + rx * .30f, cy - ry * .69f);
        innerR.close();
        c.drawPath(innerR, p);

        // Tiny messy fringe to keep it from looking like a geometric mascot.
        p.setColor(0xFF1F2226);
        Path tuft = new Path();
        tuft.moveTo(cx - w * .050f, cy - ry * .66f);
        tuft.lineTo(cx - w * .018f, cy - ry * .82f);
        tuft.lineTo(cx, cy - ry * .69f);
        tuft.lineTo(cx + w * .020f, cy - ry * .84f);
        tuft.lineTo(cx + w * .054f, cy - ry * .66f);
        tuft.close();
        c.drawPath(tuft, p);

        p.setColor(0xFF34383D);
        c.drawOval(new RectF(cx - rx * .54f, cy + ry * .02f, cx + rx * .54f, cy + ry * .50f), p);
    }

    private void drawFace(Canvas c, float w, float h) {
        float cx = w * .485f;
        float cy = h * .43f;
        float eyeY = cy - h * .022f;
        float eyeDX = w * .083f;
        boolean closed = state == STATE_BLINK || state == STATE_SLEEP;

        if (closed) {
            // Soft sleepy eyelids: dark, tapered curves instead of bright green flat bars.
            // The shape follows the reference cat: slightly lowered inner corners,
            // a gentle dip through the middle, and a tiny lift toward the outer edge.
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF111215);

            float lidHalf = w * .034f;
            float lidThick = h * .010f;
            float lidDip = h * .010f;

            Path leftLid = new Path();
            leftLid.moveTo(cx - eyeDX - lidHalf, eyeY - h * .002f);
            leftLid.cubicTo(
                    cx - eyeDX - w * .010f, eyeY + lidDip,
                    cx - eyeDX + w * .014f, eyeY + lidDip,
                    cx - eyeDX + lidHalf, eyeY - h * .005f);
            leftLid.cubicTo(
                    cx - eyeDX + w * .012f, eyeY + lidDip + lidThick,
                    cx - eyeDX - w * .013f, eyeY + lidDip + lidThick,
                    cx - eyeDX - lidHalf, eyeY - h * .002f);
            leftLid.close();
            c.drawPath(leftLid, p);

            Path rightLid = new Path();
            rightLid.moveTo(cx + eyeDX - lidHalf, eyeY - h * .005f);
            rightLid.cubicTo(
                    cx + eyeDX - w * .014f, eyeY + lidDip,
                    cx + eyeDX + w * .010f, eyeY + lidDip,
                    cx + eyeDX + lidHalf, eyeY - h * .002f);
            rightLid.cubicTo(
                    cx + eyeDX + w * .013f, eyeY + lidDip + lidThick,
                    cx + eyeDX - w * .012f, eyeY + lidDip + lidThick,
                    cx + eyeDX - lidHalf, eyeY - h * .005f);
            rightLid.close();
            c.drawPath(rightLid, p);
        } else {
            drawEye(c, cx - eyeDX, eyeY, w * .040f, h * .050f);
            drawEye(c, cx + eyeDX, eyeY, w * .040f, h * .050f);
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
        float cx = w * .485f;
        float topY = h * .615f;
        float splitY = h * .632f;
        float bottomY = h * .656f;

        // Y is the neck detail itself, not a hanging tag.
        stroke.setStrokeWidth(h * .006f);
        stroke.setColor(0xFFD5DADD);
        c.drawLine(cx - w * .017f, topY, cx, splitY, stroke);
        c.drawLine(cx + w * .017f, topY, cx, splitY, stroke);
        c.drawLine(cx, splitY, cx, bottomY, stroke);

        stroke.setStrokeWidth(h * .0025f);
        stroke.setColor(0xFF79C18E);
        c.drawLine(cx - w * .008f, splitY + h * .006f, cx, splitY + h * .014f, stroke);
        c.drawLine(cx + w * .008f, splitY + h * .006f, cx, splitY + h * .014f, stroke);
    }
}
