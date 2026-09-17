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

/** Quiet prone/loaf cat desk-pet: dark fur, green eyes and a small Y bell. */
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
        float cx = w * .50f;
        float bob = -1.2f * breath;
        canvas.save();
        canvas.translate(0f, bob);

        int fur = Color.rgb(43, 45, 50);
        int furDark = Color.rgb(34, 36, 41);
        int line = Color.rgb(25, 27, 31);

        // Long, low body: belly is visually on the floor, not standing on four legs.
        paint.setColor(fur);
        canvas.drawOval(new RectF(w * .08f, h * .43f, w * .92f, h * .90f), paint);

        // Soft haunches flattened to either side.
        paint.setColor(Color.rgb(40, 42, 47));
        canvas.drawOval(new RectF(w * .06f, h * .58f, w * .37f, h * .91f), paint);
        canvas.drawOval(new RectF(w * .63f, h * .58f, w * .94f, h * .91f), paint);

        // Head sits slightly forward and low into the body.
        paint.setColor(furDark);
        canvas.drawOval(new RectF(w * .27f, h * .16f, w * .73f, h * .64f), paint);

        // Ears: compact triangles, one can twitch.
        float twitch = earTwitch * 4f;
        Path leftEar = new Path();
        leftEar.moveTo(w * .31f, h * .31f);
        leftEar.lineTo(w * .35f - twitch, h * .06f);
        leftEar.lineTo(w * .47f, h * .24f);
        leftEar.close();
        canvas.drawPath(leftEar, paint);

        Path rightEar = new Path();
        rightEar.moveTo(w * .69f, h * .31f);
        rightEar.lineTo(w * .65f + twitch, h * .06f);
        rightEar.lineTo(w * .53f, h * .24f);
        rightEar.close();
        canvas.drawPath(rightEar, paint);

        // Tucked forepaws: only the little ends show under the chest.
        paint.setColor(furDark);
        canvas.drawOval(new RectF(w * .34f, h * .66f, w * .49f, h * .79f), paint);
        canvas.drawOval(new RectF(w * .51f, h * .66f, w * .66f, h * .79f), paint);
        paint.setColor(line);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f, w * .009f));
        canvas.drawArc(new RectF(w * .37f, h * .70f, w * .47f, h * .77f), 15, 150, false, paint);
        canvas.drawArc(new RectF(w * .53f, h * .70f, w * .63f, h * .77f), 15, 150, false, paint);
        paint.setStyle(Paint.Style.FILL);

        // Green eyes, slightly narrowed by default, rounder when watching A-Mao.
        paint.setColor(Color.rgb(103, 219, 137));
        float eyeY = h * .38f;
        float eyeW = looking ? w * .065f : w * .055f;
        float eyeH = eyesOpen ? (looking ? h * .045f : h * .028f) : h * .006f;
        canvas.drawOval(new RectF(cx - w * .105f - eyeW, eyeY - eyeH, cx - w * .105f + eyeW, eyeY + eyeH), paint);
        canvas.drawOval(new RectF(cx + w * .105f - eyeW, eyeY - eyeH, cx + w * .105f + eyeW, eyeY + eyeH), paint);

        // Small nose and unimpressed mouth.
        paint.setColor(Color.rgb(142, 102, 109));
        Path nose = new Path();
        nose.moveTo(cx - w * .018f, h * .48f);
        nose.lineTo(cx + w * .018f, h * .48f);
        nose.lineTo(cx, h * .505f);
        nose.close();
        canvas.drawPath(nose, paint);
        paint.setColor(line);
        paint.setStrokeWidth(Math.max(1.5f, w * .009f));
        canvas.drawLine(cx, h * .505f, cx, h * .54f, paint);
        canvas.drawLine(cx, h * .54f, cx - w * .032f, h * .555f, paint);
        canvas.drawLine(cx, h * .54f, cx + w * .032f, h * .555f, paint);

        // Collar buried in the fur; bell sits right below the chin.
        paint.setColor(Color.rgb(24, 25, 29));
        canvas.drawRoundRect(new RectF(w * .38f, h * .58f, w * .62f, h * .615f), h * .02f, h * .02f, paint);
        paint.setColor(Color.rgb(194, 159, 79));
        canvas.drawCircle(cx, h * .635f, w * .035f, paint);
        paint.setColor(Color.rgb(62, 48, 22));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(w * .038f);
        paint.setFakeBoldText(true);
        canvas.drawText("Y", cx, h * .648f, paint);
        paint.setFakeBoldText(false);

        // Tail curled along the side, keeping the whole silhouette low and cat-like.
        paint.setColor(furDark);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(w * .055f);
        Path tail = new Path();
        tail.moveTo(w * .78f, h * .72f);
        tail.cubicTo(w * .93f, h * .68f, w * .93f, h * .86f, w * .82f, h * .85f);
        canvas.drawPath(tail, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeCap(Paint.Cap.BUTT);

        canvas.restore();
    }
}
