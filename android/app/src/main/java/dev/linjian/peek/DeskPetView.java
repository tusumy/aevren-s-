package dev.linjian.peek;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.view.View;

import java.util.Random;

/** Soft illustrated black-cat desk pet with lightweight blink and idle animation. */
public class DeskPetView extends View {
    private static final int STATE_IDLE = 0;
    private static final int STATE_BLINK = 1;
    private static final int STATE_SLEEP = 2;
    private static final int STATE_PEEK = 3;
    private static final int STATE_HAPPY = 4;

    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler();
    private final Random random = new Random();
    private final Bitmap cat;
    private final RectF destination = new RectF();

    private int state = STATE_IDLE;
    private boolean looking;
    private boolean peeking;
    private boolean released;
    private float breath;
    private boolean breathUp = true;
    private float earTwitch;

    public DeskPetView(Context context) {
        super(context);
        cat = BitmapFactory.decodeResource(getResources(), R.drawable.pet_xuanyan_fluffy);
        setWillNotDraw(false);
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
                }, 150L);
            }
            handler.postDelayed(this, 2600L + random.nextInt(3600));
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
            breath += breathUp ? .08f : -.08f;
            if (breath >= 1f) { breath = 1f; breathUp = false; }
            if (breath <= 0f) { breath = 0f; breathUp = true; }
            invalidate();
            handler.postDelayed(this, 180L);
        }
    };

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0 || cat == null || cat.isRecycled()) return;

        float bob = breath * h * .008f + (peeking ? h * .025f : 0f);
        float twitch = earTwitch * 1.2f;
        canvas.save();
        canvas.translate(0f, bob);
        canvas.rotate(twitch, w * .44f, h * .42f);
        drawAttentionMarks(canvas, w, h);
        float availableW = w * .97f;
        float availableH = h * .945f;
        float imageAspect = (float) cat.getWidth() / cat.getHeight();
        float drawW = availableW;
        float drawH = drawW / imageAspect;
        if (drawH > availableH) {
            drawH = availableH;
            drawW = drawH * imageAspect;
        }
        float left = (w - drawW) * .5f;
        float top = (h - drawH) * .5f;
        destination.set(left, top, left + drawW, top + drawH);
        canvas.drawBitmap(cat, null, destination, imagePaint);
        if (state == STATE_BLINK || state == STATE_SLEEP) drawClosedEyes(canvas, destination);
        canvas.restore();
    }

    private void drawAttentionMarks(Canvas canvas, float w, float h) {
        if (state != STATE_HAPPY && state != STATE_PEEK) return;
        stroke.setStrokeWidth(h * .026f);
        stroke.setColor(0xFF78D9A2);
        canvas.drawLine(w * .08f, h * .46f, w * .025f, h * .41f, stroke);
        canvas.drawLine(w * .075f, h * .54f, w * .015f, h * .54f, stroke);
        canvas.drawLine(w * .90f, h * .46f, w * .955f, h * .41f, stroke);
        canvas.drawLine(w * .905f, h * .54f, w * .965f, h * .54f, stroke);
    }

    private void drawClosedEyes(Canvas canvas, RectF catBounds) {
        drawLid(canvas, catBounds, .362f, .694f);
        drawLid(canvas, catBounds, .542f, .694f);
    }

    private void drawLid(Canvas canvas, RectF catBounds, float normalizedX, float normalizedY) {
        float w = catBounds.width();
        float h = catBounds.height();
        float cx = catBounds.left + w * normalizedX;
        float cy = catBounds.top + h * normalizedY;
        float halfWidth = w * .052f;
        float halfHeight = h * .058f;
        Path cover = new Path();
        cover.moveTo(cx - halfWidth, cy);
        cover.cubicTo(cx - w * .030f, cy - halfHeight, cx + w * .030f, cy - halfHeight,
                cx + halfWidth, cy);
        cover.cubicTo(cx + w * .030f, cy + halfHeight, cx - w * .030f, cy + halfHeight,
                cx - halfWidth, cy);
        cover.close();

        Rect source = new Rect(
                Math.round(cat.getWidth() * (normalizedX - .052f)),
                Math.round(cat.getHeight() * .510f),
                Math.round(cat.getWidth() * (normalizedX + .052f)),
                Math.round(cat.getHeight() * .626f));
        RectF patchBounds = new RectF(cx - halfWidth, cy - halfHeight,
                cx + halfWidth, cy + halfHeight);
        canvas.save();
        canvas.clipPath(cover);
        canvas.drawBitmap(cat, source, patchBounds, imagePaint);
        canvas.restore();

        stroke.setStrokeWidth(h * .013f);
        stroke.setColor(0xFF17151B);
        RectF arc = new RectF(cx - w * .043f, cy - h * .018f,
                cx + w * .043f, cy + h * .032f);
        canvas.drawArc(arc, 192f, 156f, false, stroke);
    }
}
