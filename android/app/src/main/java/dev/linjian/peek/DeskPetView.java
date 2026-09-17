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
import android.util.Base64;
import android.view.View;

import java.util.Random;

/** High-resolution Screen Feel desk pet with transparent resource frames. */
public class DeskPetView extends View {
    private static final int FRAME_IDLE = 0;
    private static final int FRAME_BLINK = 1;
    private static final int FRAME_SLEEP = 2;
    private static final int FRAME_PEEK = 3;
    private static final int FRAME_HAPPY = 4;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler();
    private final Random random = new Random();

    private Bitmap sheet;
    private int frame = FRAME_IDLE;
    private boolean looking;
    private boolean released;
    private boolean peeking;
    private float breath;
    private boolean breathUp = true;

    public DeskPetView(Context context) {
        super(context);
        setWillNotDraw(false);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setBackgroundColor(Color.TRANSPARENT);
        decodeSheet();
        handler.post(blinkLoop);
        handler.post(breatheLoop);
        handler.post(ambientLoop);
    }

    private void decodeSheet() {
        try {
            byte[] bytes = Base64.decode(DeskPetSpriteData.base64(), Base64.DEFAULT);
            sheet = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (sheet != null) sheet.setHasAlpha(true);
        } catch (RuntimeException ignored) {
            sheet = null;
        }
    }

    public void lookAtUser(long durationMs) {
        looking = true;
        peeking = false;
        frame = FRAME_PEEK;
        invalidate();
        handler.removeCallbacks(stopLooking);
        handler.postDelayed(stopLooking, durationMs);
    }

    public void earTwitch() {
        if (looking) return;
        frame = FRAME_HAPPY;
        invalidate();
        handler.postDelayed(() -> {
            if (!released && !looking) {
                frame = FRAME_IDLE;
                invalidate();
            }
        }, 420L);
    }

    public void setWatchMode(boolean watchMode) {
        if (watchMode) lookAtUser(2200L);
    }

    public void peek(long durationMs) {
        if (looking) return;
        peeking = true;
        frame = FRAME_PEEK;
        invalidate();
        handler.postDelayed(() -> {
            if (!released) {
                peeking = false;
                if (!looking) frame = FRAME_IDLE;
                invalidate();
            }
        }, durationMs);
    }

    public void release() {
        released = true;
        handler.removeCallbacksAndMessages(null);
        if (sheet != null) {
            sheet.recycle();
            sheet = null;
        }
    }

    private final Runnable stopLooking = () -> {
        looking = false;
        frame = FRAME_IDLE;
        invalidate();
    };

    private final Runnable blinkLoop = new Runnable() {
        @Override public void run() {
            if (released) return;
            if (!looking && !peeking) {
                frame = FRAME_BLINK;
                invalidate();
                handler.postDelayed(() -> {
                    if (!released && !looking && !peeking) {
                        frame = FRAME_IDLE;
                        invalidate();
                    }
                }, 120L);
            }
            handler.postDelayed(this, 2600L + random.nextInt(3300));
        }
    };

    private final Runnable ambientLoop = new Runnable() {
        @Override public void run() {
            if (released) return;
            if (!looking && !peeking) {
                int roll = random.nextInt(8);
                if (roll == 0) {
                    frame = FRAME_SLEEP;
                    invalidate();
                    handler.postDelayed(() -> {
                        if (!released && !looking && !peeking) {
                            frame = FRAME_IDLE;
                            invalidate();
                        }
                    }, 900L);
                } else if (roll == 1) {
                    frame = FRAME_HAPPY;
                    invalidate();
                    handler.postDelayed(() -> {
                        if (!released && !looking && !peeking) {
                            frame = FRAME_IDLE;
                            invalidate();
                        }
                    }, 760L);
                } else if (roll == 2) {
                    peek(1100L);
                }
            }
            handler.postDelayed(this, 5200L + random.nextInt(5200));
        }
    };

    private final Runnable breatheLoop = new Runnable() {
        @Override public void run() {
            if (released) return;
            breath += breathUp ? .05f : -.05f;
            if (breath >= 1f) { breath = 1f; breathUp = false; }
            if (breath <= 0f) { breath = 0f; breathUp = true; }
            invalidate();
            handler.postDelayed(this, 95L);
        }
    };

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (sheet == null || sheet.isRecycled()) {
            drawFallbackCat(canvas);
            return;
        }

        int fw = DeskPetSpriteData.FRAME_WIDTH;
        int fh = DeskPetSpriteData.FRAME_HEIGHT;
        int safeFrame = Math.max(0, Math.min(frame, DeskPetSpriteData.FRAME_COUNT - 1));
        Rect src = new Rect(safeFrame * fw, 0, (safeFrame + 1) * fw, fh);

        float bob = breath * getHeight() * .0055f;
        float peekShift = peeking ? getHeight() * .13f : 0f;
        float insetX = getWidth() * .015f;
        float insetY = getHeight() * .015f;
        RectF dst = new RectF(
                insetX,
                insetY + bob + peekShift,
                getWidth() - insetX,
                getHeight() - insetY + bob + peekShift);

        paint.setAlpha(255);
        paint.setColorFilter(null);
        paint.setShadowLayer(getHeight() * .035f, 0f, getHeight() * .018f, 0x33000000);
        canvas.drawBitmap(sheet, src, dst, paint);
        paint.clearShadowLayer();
    }

    /** Visible emergency fallback: never leave only a speech bubble on screen. */
    private void drawFallbackCat(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        fallbackPaint.setStyle(Paint.Style.FILL);
        fallbackPaint.setColor(0xFF292B2F);
        fallbackPaint.setShadowLayer(h * .04f, 0f, h * .02f, 0x3D000000);

        RectF body = new RectF(w * .16f, h * .38f, w * .90f, h * .88f);
        canvas.drawOval(body, fallbackPaint);

        Path head = new Path();
        head.moveTo(w * .24f, h * .60f);
        head.lineTo(w * .28f, h * .18f);
        head.lineTo(w * .42f, h * .37f);
        head.lineTo(w * .58f, h * .37f);
        head.lineTo(w * .70f, h * .18f);
        head.lineTo(w * .76f, h * .60f);
        head.close();
        canvas.drawPath(head, fallbackPaint);
        canvas.drawOval(new RectF(w * .24f, h * .34f, w * .76f, h * .82f), fallbackPaint);
        fallbackPaint.clearShadowLayer();

        fallbackPaint.setColor(0xFFB7E8C1);
        canvas.drawOval(new RectF(w * .35f, h * .50f, w * .44f, h * .60f), fallbackPaint);
        canvas.drawOval(new RectF(w * .56f, h * .50f, w * .65f, h * .60f), fallbackPaint);

        fallbackPaint.setColor(0xFFD7B15A);
        canvas.drawCircle(w * .50f, h * .75f, h * .052f, fallbackPaint);
    }
}
