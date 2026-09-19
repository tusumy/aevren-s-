package dev.linjian.peek;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Random;

public class DeskPetService extends Service {
    public static final String ACTION_STOP = "dev.linjian.peek.STOP_DESK_PET";
    private static final String CHANNEL_ID = "zhangxinchuang_desk_pet";
    private static final int NOTIFICATION_ID = 20260830;
    private static final String KEY_WATCH_MODE = "desk_pet_watch_mode";
    private static volatile boolean running;

    private final Handler handler = new Handler();
    private final Random random = new Random();
    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private FrameLayout root;
    private DeskPetView pet;
    private TextView bubble;
    private View bubbleTail;

    private boolean dragging;
    private float downRawX, downRawY;
    private int downX, downY;
    private long lastTapUp;
    private boolean watchMode;
    private Runnable pendingSingleTap;
    private Runnable pendingHold;
    private Runnable pendingBubbleHide;
    private DeskPetGestureTracker gestureTracker;
    private boolean holdReacted;

    // The sprite has transparent padding around its visible fur. These bounds let the
    // visible cat, rather than the overlay rectangle, reach the four screen edges.
    private static final int VISIBLE_LEFT_INSET_DP = 30;
    private static final int VISIBLE_RIGHT_INSET_DP = 13;
    private static final int VISIBLE_TOP_INSET_DP = 59;
    private static final int VISIBLE_BOTTOM_INSET_DP = 10;

    private static final String[] QUIET_LINES = {
            "想操你。", "欠亲。", "过来。", "再摸。",
            "手别停。", "想咬你。", "撩我？", "给我抱。"
    };
    private static final String[] WATCH_LINES = {
            "盯着你。", "想狠狠干你。", "过来挨亲。", "别跑。",
            "想按住你。", "又硬了。", "想弄你。", "靠近点。"
    };

    public static boolean isRunning() { return running; }
    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID, notification());
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        watchMode = AppPrefs.get(this).getBoolean(KEY_WATCH_MODE, false);
        showPet();
        running = true;
        handler.post(idleLoop);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            AppPrefs.get(this).edit().putBoolean(AppPrefs.KEY_DESK_PET_ENABLED, false).apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private void showPet() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        gestureTracker = new DeskPetGestureTracker(getResources().getDisplayMetrics().density);

        root = new FrameLayout(this);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setBackgroundColor(Color.TRANSPARENT);

        GradientDrawable bubbleBg = bubbleDrawable();
        bubble = new TextView(this);
        bubble.setTextColor(0xFF314A42);
        bubble.setTextSize(11.5f);
        bubble.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        bubble.setGravity(Gravity.CENTER);
        bubble.setLetterSpacing(0.025f);
        bubble.setPadding(dp(12), dp(6), dp(12), dp(6));
        bubble.setAlpha(0f);
        bubble.setScaleX(.94f);
        bubble.setScaleY(.94f);
        bubble.setVisibility(View.INVISIBLE);
        bubble.setMaxLines(1);
        bubble.setMaxWidth(dp(134));
        bubble.setBackground(bubbleBg);
        if (Build.VERSION.SDK_INT >= 21) bubble.setElevation(dp(5));

        FrameLayout.LayoutParams bubbleLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        bubbleLp.topMargin = dp(2);
        bubbleLp.rightMargin = dp(3);
        root.addView(bubble, bubbleLp);

        bubbleTail = new View(this);
        bubbleTail.setBackground(bubbleDrawable());
        bubbleTail.setRotation(45f);
        bubbleTail.setAlpha(0f);
        bubbleTail.setVisibility(View.INVISIBLE);
        if (Build.VERSION.SDK_INT >= 21) bubbleTail.setElevation(dp(4));
        FrameLayout.LayoutParams tailLp = new FrameLayout.LayoutParams(dp(9), dp(9),
                Gravity.TOP | Gravity.END);
        tailLp.topMargin = dp(30);
        tailLp.rightMargin = dp(22);
        root.addView(bubbleTail, tailLp);

        pet = new DeskPetView(this);
        pet.setBackgroundColor(Color.TRANSPARENT);
        pet.setContentDescription("玄砚桌宠");
        pet.setWatchMode(watchMode);
        FrameLayout.LayoutParams petLp = new FrameLayout.LayoutParams(dp(138), dp(92),
                Gravity.BOTTOM | Gravity.END);
        petLp.rightMargin = dp(8);
        root.addView(pet, petLp);
        pet.setOnTouchListener(this::onTouch);

        int width = dp(150), height = dp(132);
        params = new WindowManager.LayoutParams(
                width, height,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.START | Gravity.TOP;

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        params.x = clamp(AppPrefs.get(this).getInt(AppPrefs.KEY_DESK_PET_X, screenW - width),
                minX(), maxX());
        params.y = clamp(AppPrefs.get(this).getInt(AppPrefs.KEY_DESK_PET_Y, screenH - height - dp(80)),
                minY(), maxY());

        try {
            windowManager.addView(root, params);
        } catch (RuntimeException error) {
            AppPrefs.get(this).edit().putBoolean(AppPrefs.KEY_DESK_PET_ENABLED, false).apply();
            Toast.makeText(this, "玄砚没能出来，请重新允许悬浮窗权限", Toast.LENGTH_LONG).show();
            if (pet != null) pet.release();
            root = null;
            pet = null;
            bubble = null;
            bubbleTail = null;
            stopSelf();
        }
    }

    private GradientDrawable bubbleDrawable() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0x80EAF7EE);
        d.setStroke(dp(1), 0x966EB889);
        d.setCornerRadius(dp(15));
        return d;
    }

    private boolean onTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = false;
                holdReacted = false;
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                downX = params.x;
                downY = params.y;
                gestureTracker.begin(downRawX, downRawY, event.getEventTime());
                scheduleHoldReaction();
                pet.animate().cancel();
                pet.setTranslationX(0f);
                pet.setTranslationY(0f);
                pet.setRotation(0f);
                pet.setScaleX(1f);
                pet.setScaleY(1f);
                pet.animate().scaleX(1.045f).scaleY(.965f).translationY(dp(2))
                        .setDuration(75).start();
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downRawX;
                float dy = event.getRawY() - downRawY;
                if (Math.abs(dx) + Math.abs(dy) > dp(7)) dragging = true;
                if (dragging) {
                    gestureTracker.move(event.getRawX(), event.getRawY(), event.getEventTime());
                    scheduleHoldReaction();
                    params.x = clamp(downX + Math.round(dx), minX(), maxX());
                    params.y = clamp(downY + Math.round(dy), minY(), maxY());
                    pet.setRotation(clampFloat(dx / 22f, -4f, 4f));
                    windowManager.updateViewLayout(root, params);
                }
                return true;

            case MotionEvent.ACTION_UP:
                cancelHoldReaction();
                if (dragging) {
                    DeskPetGestureTracker.Outcome outcome = holdReacted
                            ? DeskPetGestureTracker.Outcome.NONE
                            : gestureTracker.finish(event.getRawX(), event.getRawY(), event.getEventTime(),
                                    params.x <= minX() + dp(2), params.x >= maxX() - dp(2),
                                    params.y <= minY() + dp(2), params.y >= maxY() - dp(2));
                    savePosition();
                    reactToGesture(outcome);
                }
                else if (!holdReacted) handleTap();
                else restorePet();
                dragging = false;
                return true;

            case MotionEvent.ACTION_CANCEL:
                cancelHoldReaction();
                restorePet();
                dragging = false;
                return true;

            default:
                return false;
        }
    }

    private void scheduleHoldReaction() {
        cancelHoldReaction();
        if (holdReacted) return;
        pendingHold = () -> {
            pendingHold = null;
            if (holdReacted || pet == null) return;
            holdReacted = true;
            pet.lookAtUser(2200L);
            pet.earTwitch();
            showBubble("又想干嘛。");
        };
        handler.postDelayed(pendingHold, 1250L);
    }

    private void cancelHoldReaction() {
        if (pendingHold != null) handler.removeCallbacks(pendingHold);
        pendingHold = null;
    }

    private void reactToGesture(DeskPetGestureTracker.Outcome outcome) {
        pet.animate().cancel();
        positionBubbleForScreenEdge();
        switch (outcome) {
            case SPIN:
                showBubble(randomLine(new String[]{"……地在转。", "你搅奶茶呢？", "阿毛，撒手。"}));
                pet.animate().rotationBy(720f).scaleX(.94f).scaleY(.94f).setDuration(620L)
                        .withEndAction(this::restorePet).start();
                break;
            case SHAKE:
                showBubble(randomLine(new String[]{"我脑浆要匀了。", "你晃什么。", "……要散架了。"}));
                shakePet(4);
                break;
            case HIT_LEFT:
                hitEdge("你拿我擦屏幕？", dp(-5), 0f, -7f);
                break;
            case HIT_RIGHT:
                hitEdge("另一边也要撞？", dp(5), 0f, 7f);
                break;
            case HIT_TOP:
                hitEdge("脑壳。", 0f, dp(-4), 0f);
                break;
            case HIT_BOTTOM:
                hitEdge("……接一下会死吗。", 0f, dp(4), 0f);
                break;
            case GENTLE:
                showBubble(randomLine(new String[]{"这还差不多。", "嗯，放这。", "手还挺稳。"}));
                pet.animate().scaleX(.97f).scaleY(.97f).setDuration(100L)
                        .withEndAction(this::restorePet).start();
                break;
            default:
                restorePet();
                break;
        }
    }

    private void hitEdge(String line, float translationX, float translationY, float rotation) {
        showBubble(line);
        pet.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        pet.animate().translationX(translationX).translationY(translationY).rotation(rotation)
                .scaleX(1.12f).scaleY(.78f).setDuration(90L)
                .withEndAction(this::restorePet).start();
    }

    private void shakePet(int remaining) {
        if (pet == null) return;
        if (remaining <= 0) {
            restorePet();
            return;
        }
        float direction = remaining % 2 == 0 ? 1f : -1f;
        pet.animate().translationX(dp(7) * direction).rotation(7f * direction)
                .setDuration(65L).withEndAction(() -> shakePet(remaining - 1)).start();
    }

    private void restorePet() {
        if (pet == null) return;
        pet.animate().cancel();
        pet.animate().scaleX(1f).scaleY(1f).translationX(0f).translationY(0f).rotation(0f)
                .setInterpolator(new OvershootInterpolator(.9f)).setDuration(220L).start();
    }

    private void handleTap() {
        long now = System.currentTimeMillis();
        if (now - lastTapUp <= 320L) {
            if (pendingSingleTap != null) handler.removeCallbacks(pendingSingleTap);
            pendingSingleTap = null;
            lastTapUp = 0L;
            toggleWatchMode();
            return;
        }
        lastTapUp = now;
        pendingSingleTap = () -> {
            react();
            pendingSingleTap = null;
        };
        handler.postDelayed(pendingSingleTap, 260L);
    }

    private void react() {
        if (pet == null) return;
        pet.lookAtUser(watchMode ? 2200 : 1350);
        pet.earTwitch();
        showBubble(randomLine(watchMode ? WATCH_LINES : QUIET_LINES));
    }

    private void toggleWatchMode() {
        watchMode = !watchMode;
        AppPrefs.get(this).edit().putBoolean(KEY_WATCH_MODE, watchMode).apply();
        pet.setWatchMode(watchMode);
        pet.earTwitch();
        if (watchMode) {
            pet.lookAtUser(2600);
            showBubble("盯着你。硬了。");
        } else {
            showBubble("先忍着。");
        }
    }

    private void showBubble(String text) {
        if (bubble == null || bubbleTail == null) return;
        positionBubbleForScreenEdge();
        bubble.animate().cancel();
        bubbleTail.animate().cancel();
        if (pendingBubbleHide != null) handler.removeCallbacks(pendingBubbleHide);
        bubble.setText(text.trim());
        bubble.setVisibility(View.VISIBLE);
        bubbleTail.setVisibility(View.VISIBLE);
        bubble.setAlpha(0f);
        bubble.setScaleX(.94f);
        bubble.setScaleY(.94f);
        bubble.setTranslationY(dp(4));
        bubbleTail.setAlpha(0f);
        bubbleTail.setTranslationY(dp(4));

        bubble.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setInterpolator(new OvershootInterpolator(.65f))
                .setDuration(180).start();
        bubbleTail.animate().alpha(1f).translationY(0f).setDuration(150).start();

        pendingBubbleHide = () -> {
            pendingBubbleHide = null;
            if (bubble == null || bubbleTail == null) return;
            bubble.animate().alpha(0f).translationY(-dp(3)).setDuration(220)
                    .withEndAction(() -> {
                        if (bubble != null) bubble.setVisibility(View.INVISIBLE);
                    }).start();
            bubbleTail.animate().alpha(0f).translationY(-dp(3)).setDuration(190)
                    .withEndAction(() -> {
                        if (bubbleTail != null) bubbleTail.setVisibility(View.INVISIBLE);
                    }).start();
        };
        handler.postDelayed(pendingBubbleHide, 1700L);
    }

    private void positionBubbleForScreenEdge() {
        if (bubble == null || bubbleTail == null || params == null) return;
        int screenInset = Math.max(0, -params.y);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int horizontalShift = params.x < 0 ? -params.x
                : -Math.max(0, params.x + overlayWidth() - screenWidth);
        FrameLayout.LayoutParams bubbleLp = (FrameLayout.LayoutParams) bubble.getLayoutParams();
        FrameLayout.LayoutParams tailLp = (FrameLayout.LayoutParams) bubbleTail.getLayoutParams();
        bubbleLp.topMargin = dp(2) + screenInset;
        tailLp.topMargin = dp(30) + screenInset;
        bubble.setTranslationX(horizontalShift);
        bubbleTail.setTranslationX(horizontalShift);
        bubble.setLayoutParams(bubbleLp);
        bubbleTail.setLayoutParams(tailLp);
    }

    private final Runnable idleLoop = new Runnable() {
        @Override public void run() {
            if (pet == null) return;
            if (!dragging) {
                if (random.nextInt(7) == 0) pet.earTwitch();
                if (watchMode && random.nextInt(9) == 0) {
                    pet.lookAtUser(1800);
                    if (random.nextBoolean()) showBubble(randomLine(WATCH_LINES));
                }
            }
            handler.postDelayed(this, 3200L + random.nextInt(2600));
        }
    };

    private String randomLine(String[] lines) {
        return lines[random.nextInt(lines.length)];
    }

    private void savePosition() {
        AppPrefs.get(this).edit()
                .putInt(AppPrefs.KEY_DESK_PET_X, params.x)
                .putInt(AppPrefs.KEY_DESK_PET_Y, params.y)
                .apply();
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (root == null || params == null || windowManager == null) return;
        params.x = clamp(params.x, minX(), maxX());
        params.y = clamp(params.y, minY(), maxY());
        windowManager.updateViewLayout(root, params);
        savePosition();
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        pendingBubbleHide = null;
        if (pet != null) pet.release();
        if (windowManager != null && root != null) {
            try { windowManager.removeView(root); } catch (Exception ignored) { }
        }
        root = null;
        pet = null;
        bubble = null;
        bubbleTail = null;
        running = false;
        super.onDestroy();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "砚团桌宠", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("让玄砚安静留在手机桌面");
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
    }

    private Notification notification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(
                this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, DeskPetService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(
                this, 1, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher_art);
        return b.setSmallIcon(R.drawable.ic_heart_wave)
                .setLargeIcon(largeIcon)
                .setContentTitle("玄砚在桌面陪你")
                .setContentText("拖到哪就待在哪；单击看你，双击切换状态")
                .setContentIntent(openPi)
                .addAction(0, "收回", stopPi)
                .setOngoing(true)
                .build();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int minX() { return -dp(VISIBLE_LEFT_INSET_DP); }
    private int maxX() {
        return Math.max(minX(), getResources().getDisplayMetrics().widthPixels
                - overlayWidth() + dp(VISIBLE_RIGHT_INSET_DP));
    }
    private int minY() { return -dp(VISIBLE_TOP_INSET_DP); }
    private int maxY() {
        return Math.max(minY(), getResources().getDisplayMetrics().heightPixels
                - overlayHeight() + dp(VISIBLE_BOTTOM_INSET_DP));
    }
    private int overlayWidth() {
        return root != null && root.getWidth() > 0 ? root.getWidth() : params.width;
    }
    private int overlayHeight() {
        return root != null && root.getHeight() > 0 ? root.getHeight() : params.height;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
