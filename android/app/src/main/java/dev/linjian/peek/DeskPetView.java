package dev.linjian.peek;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.util.Base64;
import android.view.View;

import java.util.Random;

/** High-resolution Screen Feel desk pet with transparent PNG resource frames. */
public class DeskPetView extends View {
    private static final int FRAME_IDLE = 0;
    private static final int FRAME_BLINK = 1;
    private static final int FRAME_SLEEP = 2;
    private static final int FRAME_PEEK = 3;
    private static final int FRAME_HAPPY = 4;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
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
        sheet = null;
        try {
            byte[] bytes = Base64.decode(DeskPetSpriteData.base64(), Base64.DEFAULT);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            opts.inScaled = false;
            Bitmap decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
            int minWidth = DeskPetSpriteData.FRAME_WIDTH * DeskPetSpriteData.FRAME_COUNT;
            if (decoded != null && decoded.getWidth() >= minWidth && decoded.getHeight() >= DeskPetSpriteData.FRAME_HEIGHT) {
                decoded.setHasAlpha(true);
                sheet = decoded;
            } else if (decoded != null) {
                decoded.recycle();
            }
        } catch (Throwable ignored) {
            sheet = null;
        }
        invalidate();
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
        if (sheet == null || sheet.isRecycled()) return;

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
}
