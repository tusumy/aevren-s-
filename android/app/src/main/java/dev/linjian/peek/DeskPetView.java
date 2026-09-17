package dev.linjian.peek;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.view.View;

import java.util.Random;

/** Low-pixel loaf cat: compact body, tucked paws, green eyes, tiny Y bell. */
public class DeskPetView extends View {
    private final Paint paint = new Paint();
    private final Handler handler = new Handler();
    private final Random random = new Random();

    private boolean eyesOpen = true;
    private boolean looking = false;
    private boolean earUp = true;
    private float breath = 0f;
    private boolean breathUp = true;

    public DeskPetView(Context context) {
        super(context);
        paint.setAntiAlias(false);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        handler.post(blinkLoop);
        handler.post(breatheLoop);
    }

    public void lookAtUser(long durationMs) {
        looking = true;
        eyesOpen = true;
        invalidate();
        handler.removeCallbacks(stopLooking);
        handler.postDelayed(stopLooking, durationMs);
    }

    public void earTwitch() {
        earUp = false;
        invalidate();
        handler.postDelayed(() -> {
            earUp = true;
            invalidate();
        }, 160);
    }

    public void setWatchMode(boolean watchMode) {
        if (watchMode) lookAtUser(2200);
    }

    public void release() {
        handler.removeCallbacksAndMessages(null);
    }

    private final Runnable stopLooking = () -> {
        looking = false;
        invalidate();
    };

    private final Runnable blinkLoop = new Runnable() {
        @Override public void run() {
            if (!looking) {
                eyesOpen = false;
                invalidate();
                handler.postDelayed(() -> {
                    eyesOpen = true;
                    invalidate();
                }, 110);
            }
            handler.postDelayed(this, 2600 + random.nextInt(3200));
        }
    };

    private final Runnable breatheLoop = new Runnable() {
        @Override public void run() {
            breath += breathUp ? .08f : -.08f;
            if (breath >= 1f) { breath = 1f; breathUp = false; }
            if (breath <= 0f) { breath = 0f; breathUp = true; }
            invalidate();
            handler.postDelayed(this, 75);
        }
    };

    private void block(Canvas c, int color, float l, float t, float r, float b, float px, float py) {
        paint.setColor(color);
        c.drawRect(l * px, t * py, r * px, b * py, paint);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float px = getWidth() / 24f;
        float py = getHeight() / 16f;
        float bob = breath * .18f * py;
        canvas.save();
        canvas.translate(0, -bob);

        int outline = Color.rgb(25, 27, 31);
        int fur = Color.rgb(48, 51, 57);
        int fur2 = Color.rgb(57, 60, 67);
        int green = Color.rgb(92, 216, 134);
        int greenDark = Color.rgb(35, 82, 53);
        int gold = Color.rgb(202, 161, 67);
        int goldDark = Color.rgb(92, 68, 24);
        int nose = Color.rgb(170, 111, 122);

        block(canvas, outline, 18, 10, 23, 12, px, py);
        block(canvas, fur,     18,  9, 22, 11, px, py);
        block(canvas, fur,     20,  8, 23, 10, px, py);

        block(canvas, outline, 4, 7, 20, 14, px, py);
        block(canvas, fur,     5, 7, 19, 13, px, py);
        block(canvas, fur2,    6, 8, 18, 12, px, py);
        block(canvas, outline, 6, 13, 18, 14, px, py);

        block(canvas, fur,     7, 12, 10, 13, px, py);
        block(canvas, fur,    14, 12, 17, 13, px, py);
        block(canvas, outline, 9, 12, 10, 13, px, py);
        block(canvas, outline,14, 12, 15, 13, px, py);

        block(canvas, outline, 7, 3, 17, 10, px, py);
        block(canvas, fur,     8, 4, 16,  9, px, py);
        block(canvas, fur2,    9, 4, 15,  8, px, py);

        block(canvas, outline, 7, 1, 10, 5, px, py);
        block(canvas, fur,     8, 2, 10, 5, px, py);
        if (earUp) {
            block(canvas, outline,14, 1, 17, 5, px, py);
            block(canvas, fur,   14, 2, 16, 5, px, py);
        } else {
            block(canvas, outline,14, 2, 17, 5, px, py);
            block(canvas, fur,   14, 3, 16, 5, px, py);
        }

        if (eyesOpen) {
            int eyeH = looking ? 2 : 1;
            block(canvas, green, 9, 6, 11, 6 + eyeH, px, py);
            block(canvas, green,13, 6, 15, 6 + eyeH, px, py);
            if (looking) {
                block(canvas, greenDark,10, 6, 11, 8, px, py);
                block(canvas, greenDark,13, 6, 14, 8, px, py);
            }
        } else {
            block(canvas, greenDark, 9, 7, 11, 8, px, py);
            block(canvas, greenDark,13, 7, 15, 8, px, py);
        }

        block(canvas, nose,   11, 8, 13, 9, px, py);
        block(canvas, outline,11, 9, 13,10, px, py);

        // Collar is mostly hidden in the fur. The bell is deliberately tiny:
        // a small identity detail under the chin rather than a medal on the chest.
        block(canvas, outline, 10, 10, 14, 10.6f, px, py);
        paint.setColor(gold);
        canvas.drawRect(11.35f * px, 10.45f * py, 12.65f * px, 11.75f * py, paint);
        paint.setColor(goldDark);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(Math.max(4f, .72f * py));
        paint.setFakeBoldText(true);
        canvas.drawText("Y", 12f * px, 11.48f * py, paint);
        paint.setFakeBoldText(false);

        canvas.restore();
    }
}
