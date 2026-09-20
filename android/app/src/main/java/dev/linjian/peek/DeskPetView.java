package dev.linjian.peek;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
    private final Bitmap closedCat;
    private final RectF destination = new RectF();

    private int state = STATE_IDLE;
    private boolean looking;
    private boolean peeking;
    private boolean released;
    private float breath;
    private boolean breathUp = true;
    private float earTwitch;
    private float activationLevel = .22f;

    public DeskPetView(Context context) {
        super(context);
        cat = BitmapFactory.decodeResource(getResources(), R.drawable.pet_xuanyan_fluffy);
        closedCat = BitmapFactory.decodeResource(
                getResources(), R.drawable.pet_xuanyan_fluffy_closed);
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

    /** 0..1 activation from the embodiment layer; only changes animation timing. */
    public void setActivationLevel(float level) {
        activationLevel = Math.max(0f, Math.min(1f, level));
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
            long base = 3000L - Math.round(activationLevel * 1200L);
            int spread = 3800 - Math.round(activationLevel * 1300f);
            handler.postDelayed(this, base + random.nextInt(Math.max(1200, spread)));
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
        Bitmap frame = (state == STATE_BLINK || state == STATE_SLEEP)
                && closedCat != null && !closedCat.isRecycled() ? closedCat : cat;
        float imageAspect = (float) frame.getWidth() / frame.getHeight();
        float drawW = availableW;
        float drawH = drawW / imageAspect;
        if (drawH > availableH) {
            drawH = availableH;
            drawW = drawH * imageAspect;
        }
        float left = (w - drawW) * .5f;
        float top = (h - drawH) * .5f;
        destination.set(left, top, left + drawW, top + drawH);
        canvas.drawBitmap(frame, null, destination, imagePaint);
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

}
