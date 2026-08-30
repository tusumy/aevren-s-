package dev.linjian.peek;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.Toast;

public class DeskPetService extends Service {
    public static final String ACTION_STOP = "dev.linjian.peek.STOP_DESK_PET";
    private static final String CHANNEL_ID = "zhangxinchuang_desk_pet";
    private static final int NOTIFICATION_ID = 20260830;
    private static volatile boolean running;

    private final Handler handler = new Handler();
    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private ImageView pet;
    private static final int EDGE_BOTTOM = 0;
    private static final int EDGE_RIGHT = 1;
    private static final int EDGE_TOP = 2;
    private static final int EDGE_LEFT = 3;

    private int edge = EDGE_BOTTOM;
    private int tick;
    private boolean dragging;
    private float downRawX, downRawY;
    private int downX, downY;

    public static boolean isRunning() { return running; }
    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID, notification());
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) { stopSelf(); return; }
        showPet();
        running = true;
        handler.post(walkLoop);
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
        windowManager = (WindowManager)getSystemService(WINDOW_SERVICE);
        pet = new ImageView(this);
        pet.setImageResource(R.drawable.pet_xuanyan);
        pet.setScaleType(ImageView.ScaleType.FIT_CENTER);
        pet.setContentDescription("Yanya 桌宠");
        int width = dp(112), height = dp(166);
        params = new WindowManager.LayoutParams(
                width, height,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.START | Gravity.TOP;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        params.x = clamp(AppPrefs.get(this).getInt(AppPrefs.KEY_DESK_PET_X, screenW - width), 0, Math.max(0, screenW - width));
        params.y = clamp(AppPrefs.get(this).getInt(AppPrefs.KEY_DESK_PET_Y, screenH - height - dp(80)), dp(24), Math.max(dp(24), screenH - height));
        edge = nearestEdge(params.x, params.y, screenW - width, screenH - height);
        applyEdgePose();
        pet.setOnTouchListener(this::onTouch);
        try {
            windowManager.addView(pet, params);
        } catch (RuntimeException error) {
            AppPrefs.get(this).edit().putBoolean(AppPrefs.KEY_DESK_PET_ENABLED, false).apply();
            Toast.makeText(this, "Yanya 没能出来，请重新允许悬浮窗权限", Toast.LENGTH_LONG).show();
            pet = null;
            stopSelf();
            return;
        }
        breathe();
    }

    private boolean onTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = false;
                downRawX = event.getRawX(); downRawY = event.getRawY();
                downX = params.x; downY = params.y;
                pet.animate().scaleX(1.06f).scaleY(1.06f).setDuration(90).start();
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downRawX, dy = event.getRawY() - downRawY;
                if (Math.abs(dx) + Math.abs(dy) > dp(8)) dragging = true;
                if (dragging) {
                    int maxX = Math.max(0, getResources().getDisplayMetrics().widthPixels - pet.getWidth());
                    int maxY = Math.max(dp(24), getResources().getDisplayMetrics().heightPixels - pet.getHeight());
                    params.x = clamp(downX + Math.round(dx), 0, maxX);
                    params.y = clamp(downY + Math.round(dy), dp(24), maxY);
                    windowManager.updateViewLayout(pet, params);
                }
                return true;
            case MotionEvent.ACTION_UP:
                pet.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                if (dragging) { snapToNearestEdge(); savePosition(); } else react();
                dragging = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                pet.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                dragging = false;
                return true;
            default: return false;
        }
    }

    private final Runnable walkLoop = new Runnable() {
        @Override public void run() {
            if (pet == null) return;
            if (!dragging) {
                tick++;
                if (tick % 220 < 176) {
                    walkOneStep();
                } else if (tick % 220 == 178) {
                    breathe();
                } else if (tick % 220 == 195) {
                    sleep();
                } else if (tick % 220 == 214) {
                    wakeUp();
                }
            }
            handler.postDelayed(this, 55);
        }
    };

    private void walkOneStep() {
        int maxX = Math.max(0, getResources().getDisplayMetrics().widthPixels - pet.getWidth());
        int maxY = Math.max(dp(24), getResources().getDisplayMetrics().heightPixels - pet.getHeight());
        int step = dp(1);

        switch (edge) {
            case EDGE_BOTTOM:
                params.x += step;
                if (params.x >= maxX) { params.x = maxX; edge = EDGE_RIGHT; }
                break;
            case EDGE_RIGHT:
                params.y -= step;
                if (params.y <= dp(24)) { params.y = dp(24); edge = EDGE_TOP; }
                break;
            case EDGE_TOP:
                params.x -= step;
                if (params.x <= 0) { params.x = 0; edge = EDGE_LEFT; }
                break;
            default:
                params.y += step;
                if (params.y >= maxY) { params.y = maxY; edge = EDGE_BOTTOM; }
                break;
        }

        applyEdgePose();
        pet.setTranslationY((tick % 8 < 4) ? -dp(2) : 0);
        windowManager.updateViewLayout(pet, params);
    }

    private void applyEdgePose() {
        if (pet == null) return;
        float rotation;
        switch (edge) {
            case EDGE_RIGHT: rotation = -90f; break;
            case EDGE_TOP: rotation = 180f; break;
            case EDGE_LEFT: rotation = 90f; break;
            default: rotation = 0f; break;
        }
        pet.setRotation(rotation);
        pet.setScaleX(1f);
    }

    private void breathe() {
        if (pet == null) return;
        pet.animate().translationY(-dp(3)).scaleY(1.015f).setDuration(700)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> { if (pet != null && !dragging) pet.animate().translationY(0).scaleY(1f).setDuration(700).start(); })
                .start();
    }

    private void react() {
        tick = 108;
        pet.animate().rotation(-7f).translationY(-dp(13)).setDuration(150)
                .withEndAction(() -> pet.animate().rotation(0f).translationY(0).setDuration(240).start()).start();
        Toast.makeText(this, "Yanya 被你戳醒了。", Toast.LENGTH_SHORT).show();
    }

    private void sleep() {
        if (pet == null || dragging) return;
        pet.animate().rotation(7f).translationY(dp(18)).scaleY(.78f).alpha(.86f).setDuration(650).start();
    }

    private void wakeUp() {
        if (pet == null || dragging) return;
        pet.animate().rotation(0f).translationY(0).scaleY(1f).alpha(1f).setDuration(350).start();
    }

    private void snapToNearestEdge() {
        int maxX = Math.max(0, getResources().getDisplayMetrics().widthPixels - pet.getWidth());
        int maxY = Math.max(dp(24), getResources().getDisplayMetrics().heightPixels - pet.getHeight());
        edge = nearestEdge(params.x, params.y, maxX, maxY);
        switch (edge) {
            case EDGE_RIGHT: params.x = maxX; break;
            case EDGE_TOP: params.y = dp(24); break;
            case EDGE_LEFT: params.x = 0; break;
            default: params.y = maxY; break;
        }
        applyEdgePose();
        windowManager.updateViewLayout(pet, params);
    }

    private int nearestEdge(int x, int y, int maxX, int maxY) {
        int bottom = Math.abs(maxY - y);
        int right = Math.abs(maxX - x);
        int top = Math.abs(y - dp(24));
        int left = Math.abs(x);
        int nearest = Math.min(Math.min(bottom, right), Math.min(top, left));
        if (nearest == right) return EDGE_RIGHT;
        if (nearest == top) return EDGE_TOP;
        if (nearest == left) return EDGE_LEFT;
        return EDGE_BOTTOM;
    }

    private void savePosition() {
        AppPrefs.get(this).edit().putInt(AppPrefs.KEY_DESK_PET_X, params.x).putInt(AppPrefs.KEY_DESK_PET_Y, params.y).apply();
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (windowManager != null && pet != null) { try { windowManager.removeView(pet); } catch (Exception ignored) { } }
        pet = null; running = false;
        super.onDestroy();
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (pet == null || params == null || windowManager == null) return;
        int maxX = Math.max(0, getResources().getDisplayMetrics().widthPixels - pet.getWidth());
        int maxY = Math.max(dp(24), getResources().getDisplayMetrics().heightPixels - pet.getHeight());
        params.x = clamp(params.x, 0, maxX);
        params.y = clamp(params.y, dp(24), maxY);
        snapToNearestEdge();
        savePosition();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "掌心窗桌宠", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("让 Yanya 留在手机桌面");
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
    }

    private Notification notification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, DeskPetService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return b.setSmallIcon(R.drawable.ic_heart_wave).setContentTitle("Yanya 在桌面陪你")
                .setContentText("点开掌心窗调整；拖动 Yanya 可以换位置")
                .setContentIntent(openPi).addAction(0, "收回", stopPi).setOngoing(true).build();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
