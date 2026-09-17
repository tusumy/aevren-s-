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
 * Lightweight drawn desk-pet: dark animal, green eyes and a small Y bell.
 * No bitmap atlas is required, so blink/look/ear-twitch states stay deterministic.
 */
public class DeskPetView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler();
    private final Random random = new Random();

    private boolean eyesOpen = true;
    private boolean looking = false;
    private float earTwitch = 0f;
    private float breath = 0f;
    private boolean breathUp = true;

    public DeskPetView(Context context) {
        super(context);
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
        earTwitch = 1f;
        invalidate();
        handler.postDelayed(() -> {
            earTwitch = 0f;
            invalidate();
        }, 180);
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
                }, 115);
            }
            handler.postDelayed(this, 2600 + random.nextInt(3300));
        }
    };

    private final Runnable breatheLoop = new Runnable() {
        @Override public void run() {
            breath += breathUp ? 0.08f : -0.08f;
            if (breath >= 1f) { breath = 1f; breathUp = false; }
            if (breath <= 0f) { breath = 0f; breathUp = true; }
            invalidate();
            handler.postDelayed(this, 70);
        }
    };

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float bob = -2f * breath;
        canvas.save();
        canvas.translate(0f, bob);

        // Body.
        paint.setColor(Color.rgb(45, 47, 52));
        RectF body = new RectF(w * .23f, h * .43f, w * .77f, h * .91f);
        canvas.drawOval(body, paint);

        // Head.
        paint.setColor(Color.rgb(38, 40, 45));
        RectF head = new RectF(w * .18f, h * .18f, w * .82f, h * .66f);
        canvas.drawOval(head, paint);

        // Ears.
        float twitch = earTwitch * 5f;
        Path leftEar = new Path();
        leftEar.moveTo(w * .27f, h * .28f);
        leftEar.lineTo(w * .30f - twitch, h * .05f);
        leftEar.lineTo(w * .45f, h * .22f);
        leftEar.close();
        canvas.drawPath(leftEar, paint);

        Path rightEar = new Path();
        rightEar.moveTo(w * .73f, h * .28f);
        rightEar.lineTo(w * .70f + twitch, h * .05f);
        rightEar.lineTo(w * .55f, h * .22f);
        rightEar.close();
        canvas.drawPath(rightEar, paint);

        // Eyes: green visual anchor. Looking mode keeps them wider and brighter.
        paint.setColor(Color.rgb(105, 214, 132));
        float eyeY = h * .39f;
        float eyeW = looking ? w * .105f : w * .09f;
        float eyeH = eyesOpen ? (looking ? h * .045f : h * .032f) : h * .006f;
        canvas.drawOval(new RectF(cx - w * .19f - eyeW/2f, eyeY-eyeH, cx - w * .19f + eyeW/2f, eyeY+eyeH), paint);
        canvas.drawOval(new RectF(cx + w * .19f - eyeW/2f, eyeY-eyeH, cx + w * .19f + eyeW/2f, eyeY+eyeH), paint);

        // Tiny unimpressed mouth.
        paint.setColor(Color.rgb(25, 26, 30));
        paint.setStrokeWidth(Math.max(2f, w * .018f));
        canvas.drawLine(cx - w * .045f, h * .50f, cx + w * .045f, h * .50f, paint);

        // Collar.
        paint.setColor(Color.rgb(24, 25, 29));
        paint.setStrokeWidth(Math.max(3f, w * .035f));
        canvas.drawLine(w * .31f, h * .61f, w * .69f, h * .61f, paint);

        // Small bell + Y.
        paint.setColor(Color.rgb(194, 159, 79));
        canvas.drawCircle(cx, h * .665f, w * .075f, paint);
        paint.setColor(Color.rgb(62, 48, 22));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(w * .085f);
        paint.setFakeBoldText(true);
        canvas.drawText("Y", cx, h * .69f, paint);
        paint.setFakeBoldText(false);

        // Paws.
        paint.setColor(Color.rgb(38, 40, 45));
        canvas.drawOval(new RectF(w * .25f, h * .82f, w * .46f, h * .96f), paint);
        canvas.drawOval(new RectF(w * .54f, h * .82f, w * .75f, h * .96f), paint);

        canvas.restore();
    }
}
