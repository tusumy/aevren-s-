package dev.linjian.peek;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.util.Base64;
import android.view.View;

import java.util.Random;

/** Resource-frame desk pet with soft on-screen polish. */
public class DeskPetView extends View {
    private static final int FRAME_IDLE = 0;
    private static final int FRAME_BLINK = 1;
    private static final int FRAME_WAKE = 2;
    private static final int FRAME_WATCH = 3;
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
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        decodeSheet();
        handler.post(blinkLoop);
        handler.post(breatheLoop);
        handler.post(ambientLoop);
    }

    private void decodeSheet() {
        try {
            byte[] bytes = Base64.decode(DeskPetSpriteData.base64(), Base64.DEFAULT);
            sheet = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (RuntimeException ignored) {
            sheet = null;
        }
    }

    public void lookAtUser(long durationMs) {
        looking = true;
        peeking = false;
        frame = FRAME_WATCH;
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
        frame = FRAME_WATCH;
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
                }, 125L);
            }
            handler.postDelayed(this, 2600L + random.nextInt(3300));
        }
    };

    private final Runnable ambientLoop = new Runnable() {
        @Override public void run() {
            if (released) return;
            if (!looking && !peeking) {
                int roll = random.nextInt(7);
                if (roll == 0) {
                    frame = FRAME_WAKE;
                    invalidate();
                    handler.postDelayed(() -> {
                        if (!released && !looking && !peeking) {
                            frame = FRAME_IDLE;
                            invalidate();
                        }
                    }, 850L);
                } else if (roll == 1) {
                    frame = FRAME_HAPPY;
                    invalidate();
                    handler.postDelayed(() -> {
                        if (!released && !looking && !peeking) {
                            frame = FRAME_IDLE;
                            invalidate();
                        }
                    }, 720L);
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
            breath += breathUp ? .055f : -.055f;
            if (breath >= 1f) { breath = 1f; breathUp = false; }
            if (breath <= 0f) { breath = 0f; breathUp = true; }
            invalidate();
            handler.postDelayed(this, 90L);
        }
    };

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (sheet == null || sheet.isRecycled()) return;

        int fw = DeskPetSpriteData.FRAME_WIDTH;
        int fh = DeskPetSpriteData.FRAME_HEIGHT;
        int safeFrame = Math.max(0, Math.min(frame, DeskPetSpriteData.FRAME_COUNT - 1));
        Rect src = new Rect(safeFrame * fw, 0, (safeFrame + 1) * fw, fh);

        float bob = breath * getHeight() * .0065f;
        float peekShift = peeking ? getHeight() * .16f : 0f;
        float insetX = getWidth() * .025f;
        float insetY = getHeight() * .025f;
        RectF dst = new RectF(
                insetX,
                insetY + bob + peekShift,
                getWidth() - insetX,
                getHeight() - insetY + bob + peekShift);

        paint.setAlpha(255);
        paint.setShadowLayer(getHeight() * .045f, 0f, getHeight() * .022f, 0x42000000);
        canvas.drawBitmap(sheet, src, dst, paint);
        paint.clearShadowLayer();
    }
}
