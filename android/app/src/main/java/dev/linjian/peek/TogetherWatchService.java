package dev.linjian.peek;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.DisplayMetrics;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * User-initiated live screen sharing through periodically updated snapshots.
 * Server holds only six rolling JPEG frames and the most recent playback-audio
 * chunk (when available). Sharing starts only with Android projection consent,
 * ends with a visible notification action, and cannot start from an MCP call.
 */
public class TogetherWatchService extends Service {
    public static final String ACTION_START = "dev.linjian.peek.WATCH_START";
    public static final String ACTION_STOP = "dev.linjian.peek.WATCH_STOP";
    public static final String EXTRA_RESULT_CODE = "capture_result_code";
    public static final String EXTRA_RESULT_DATA = "capture_result_data";
    public static final String EXTRA_APP_AUDIO = "capture_app_audio";
    public static final String EXTRA_TARGET_X = "capture_target_x";
    public static final String EXTRA_TARGET_PACKAGE = "capture_target_package";

    private static final String CHANNEL = "together_watch_live";
    private static final int NOTIFICATION_ID = 12012;
    private static final long FRAME_INTERVAL_MS = 2000L;
    private static final long AUDIO_INTERVAL_MS = 8000L;
    // Safety stop if the user forgets to end a session (or a notification is lost).
    private static final long MAX_SESSION_MS = 60L * 60L * 1000L;

    private final ExecutorService uploadQueue = Executors.newSingleThreadExecutor();
    private final java.util.concurrent.atomic.AtomicInteger pendingFrames = new java.util.concurrent.atomic.AtomicInteger();
    private HandlerThread captureThread;
    private Handler worker;
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private AudioRecord playbackAudio;
    private Thread audioThread;
    private final ByteArrayOutputStream soundBuffer = new ByteArrayOutputStream();
    private volatile boolean recordingAudio;
    private volatile boolean stopping;
    private boolean running;
    private String sessionId, serverUrl, token;
    private long startedAt, lastFrameAt;
    private int frameCount;
    private boolean targetX;
    private String expectedTargetPackage = "";
    private String capturedPackage = "";
    private long targetVisibleSince = 0L;
    private long waitingStartedAt;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        if (ACTION_STOP.equals(intent.getAction())) {
            endCapture(); return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(intent.getAction()) || running) return START_NOT_STICKY;
        serverUrl = ScreenshotService.normalizeUrl(AppPrefs.server(this));
        token = AppPrefs.token(this);
        if (serverUrl.isEmpty() || token == null || token.isEmpty()) {
            DebugState.append(this, "一起看：砚团服务器地址或 Token 未设置");
            stopSelf(); return START_NOT_STICKY;
        }
        createNotificationChannel();
        Notification n = notification("等待你打开目标应用，不会拍砚团页面");
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else startForeground(NOTIFICATION_ID, n);

        running = true;
        sessionId = UUID.randomUUID().toString();
        captureThread = new HandlerThread("TogetherWatchCapture");
        captureThread.start();
        worker = new Handler(captureThread.getLooper());
        final int code = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        final Intent data = Build.VERSION.SDK_INT >= 33
            ? intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class)
            : (Intent) intent.getParcelableExtra(EXTRA_RESULT_DATA);
        final boolean wantAudio = intent.getBooleanExtra(EXTRA_APP_AUDIO, false);
        targetX = intent.getBooleanExtra(EXTRA_TARGET_X, true);
        expectedTargetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE);
        if (expectedTargetPackage == null || expectedTargetPackage.equals("android")) expectedTargetPackage = "";
        worker.post(() -> {
            try {
                if (data == null || code == 0) throw new IllegalStateException("no projection consent");
                MediaProjectionManager manager =
                    (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                projection = manager.getMediaProjection(code, data);
                if (projection == null) throw new IllegalStateException("projection denied");
                projection.registerCallback(new MediaProjection.Callback() {
                    @Override public void onStop() { if (worker != null) worker.post(() -> endCapture()); }
                }, worker);
                // Never record the 砚团 page. Capture only after a different, user-selected
                // application is visibly foreground, stable for at least 1.5 seconds.
                waitingStartedAt = SystemClock.elapsedRealtime();
                updateNotification("请打开想看的视频，确认目标应用后才会共享");
                worker.postDelayed(() -> waitForTarget(wantAudio), 1200L);
            } catch (Exception e) {
                DebugState.append(this, "一起看授权失败：" + e.getMessage());
                endCapture();
            }
        });
        return START_NOT_STICKY;
    }

    private boolean permittedForeground(String pkg) {
        if (pkg == null || pkg.isEmpty() || pkg.equals(getPackageName())) return false;
        // Reject system permission sheets, app choosers, homescreens and status shade.
        if (pkg.equals("android") || pkg.startsWith("com.android.systemui") ||
            pkg.contains("permissioncontroller") || pkg.contains("packageinstaller") ||
            pkg.contains("launcher") || pkg.contains("resolver")) return false;
        if (!targetX) return true;
        return pkg.equals(expectedTargetPackage) || pkg.equals("com.twitter.android") ||
            pkg.equals("com.android.chrome") || pkg.equals("com.chrome.beta") ||
            pkg.equals("org.mozilla.firefox") || pkg.equals("com.microsoft.emmx") ||
            pkg.equals("com.brave.browser") || pkg.equals("com.sec.android.app.sbrowser") ||
            pkg.equals("com.opera.browser") || pkg.equals("com.vivaldi.browser") ||
            pkg.equals("com.kiwibrowser.browser") || pkg.equals("com.google.android.googlequicksearchbox");
    }

    private String foregroundPackage() {
        ScreenshotService svc = ScreenshotService.getInstance();
        return svc == null ? "" : svc.visibleForegroundPackage();
    }

    private void waitForTarget(boolean wantAudio) {
        if (stopping) return;
        long now = SystemClock.elapsedRealtime();
        if (now - waitingStartedAt > 30000L) {
            DebugState.append(this, "一起看：30秒内没有确认目标应用，未采集也未上传");
            updateNotification("没有检测到目标应用，本次未录制");
            endCapture();
            return;
        }
        String foreground = foregroundPackage();
        if (!permittedForeground(foreground)) {
            targetVisibleSince = 0L;
            capturedPackage = "";
            worker.postDelayed(() -> waitForTarget(wantAudio), 600L);
            return;
        }
        if (!foreground.equals(capturedPackage)) {
            capturedPackage = foreground;
            targetVisibleSince = now;
        }
        if (now - targetVisibleSince >= 1500L) {
            updateNotification("正在共享画面 · 点通知栏立即停止");
            startFrames(wantAudio);
        } else worker.postDelayed(() -> waitForTarget(wantAudio), 600L);
    }

    private void startFrames(boolean wantAudio) {
        if (stopping || projection == null) return;
        try {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int width = metrics.widthPixels, height = metrics.heightPixels;
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
            reader.setOnImageAvailableListener(this::receiveFrame, worker);
            display = projection.createVirtualDisplay("TogetherWatchLive", width, height,
                metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.getSurface(), null, worker);
            startedAt = SystemClock.elapsedRealtime();
            lastFrameAt = startedAt - FRAME_INTERVAL_MS;
            if (wantAudio && Build.VERSION.SDK_INT >= 29 &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startPlaybackAudio();
            }
            updateNotification("屏幕共享中 · 每2秒更新画面 · 点击停止");
            worker.postDelayed(this::endCapture, MAX_SESSION_MS);
            if (recordingAudio) worker.postDelayed(this::flushAudioChunk, AUDIO_INTERVAL_MS);
            DebugState.append(this, "屏幕共享开始，session=" + sessionId);
        } catch (Exception e) {
            DebugState.append(this, "一起看采集失败：" + e.getMessage());
            endCapture();
        }
    }

    private void receiveFrame(ImageReader source) {
        Image image = null;
        try {
            image = source.acquireLatestImage();
            if (image == null) return;
            long now = SystemClock.elapsedRealtime();
            if (stopping || now - lastFrameAt < FRAME_INTERVAL_MS || pendingFrames.get() >= 2) return;
            if (!capturedPackage.equals(foregroundPackage())) return;
            lastFrameAt = now;
            Image.Plane plane = image.getPlanes()[0];
            int width = image.getWidth(), height = image.getHeight();
            int stride = plane.getRowStride(), pixel = plane.getPixelStride();
            Bitmap padded = Bitmap.createBitmap(width + (stride - pixel * width) / pixel,
                height, Bitmap.Config.ARGB_8888);
            ByteBuffer buffer = plane.getBuffer();
            buffer.rewind();
            padded.copyPixelsFromBuffer(buffer);
            Bitmap cropped = Bitmap.createBitmap(padded, 0, 0, width, height);
            int scaledWidth = Math.min(480, width);
            int scaledHeight = Math.max(1, (int) Math.round((double) height * scaledWidth / width));
            Bitmap scaled = Bitmap.createScaledBitmap(cropped, scaledWidth, scaledHeight, true);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, 62, out);
            padded.recycle(); cropped.recycle(); scaled.recycle();
            int index = frameCount % 6;
            int sequence = frameCount++;
            byte[] bytes = out.toByteArray();
            pendingFrames.incrementAndGet();
            uploadQueue.execute(() -> {
                try {
                    upload("/api/watch/frame?session_id=" + sessionId
                        + "&device_id=" + AppPrefs.device(this) + "&index=" + index
                        + "&sequence=" + sequence, "image/jpeg", bytes);
                } finally { pendingFrames.decrementAndGet(); }
            });
        } catch (Exception e) {
            DebugState.append(this, "一起看帧失败：" + e.getMessage());
        } finally {
            if (image != null) image.close();
        }
    }

    private void startPlaybackAudio() {
        try {
            android.media.AudioPlaybackCaptureConfiguration config =
                new android.media.AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .build();
            AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(16000)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build();
            int bufSize = Math.max(4096, AudioRecord.getMinBufferSize(16000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT));
            playbackAudio = new AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufSize)
                .setAudioPlaybackCaptureConfig(config).build();
            if (playbackAudio.getState() != AudioRecord.STATE_INITIALIZED) {
                playbackAudio.release(); playbackAudio = null; return;
            }
            playbackAudio.startRecording();
            recordingAudio = true;
            audioThread = new Thread(() -> {
                byte[] chunk = new byte[2048];
                while (recordingAudio) {
                    int n = playbackAudio.read(chunk, 0, chunk.length);
                    if (n > 0) synchronized (soundBuffer) {
                        if (soundBuffer.size() + n < 900_000) soundBuffer.write(chunk, 0, n);
                    }
                    else if (n < 0) break;
                }
            }, "TogetherWatchAudio");
            audioThread.start();
        } catch (Exception e) {
            recordingAudio = false;
            DebugState.append(this, "应用播放声音不可捕获：" + e.getMessage());
        }
    }

    private void flushAudioChunk() {
        if (stopping || !recordingAudio) return;
        final byte[] pcm;
        synchronized (soundBuffer) {
            pcm = soundBuffer.toByteArray();
            soundBuffer.reset();
        }
        if (pcm.length > 1600) {
            final byte[] wav = toWav(pcm);
            uploadQueue.execute(() -> upload("/api/watch/audio?session_id=" + sessionId
                + "&device_id=" + AppPrefs.device(this), "audio/wav", wav));
        }
        if (!stopping && worker != null) worker.postDelayed(this::flushAudioChunk, AUDIO_INTERVAL_MS);
    }

    private synchronized void endCapture() {
        if (stopping) return;
        stopping = true;
        recordingAudio = false;
        try { if (playbackAudio != null) playbackAudio.stop(); } catch (Exception ignored) {}
        try { if (audioThread != null) audioThread.join(400); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        try { if (playbackAudio != null) playbackAudio.release(); } catch (Exception ignored) {}
        playbackAudio = null;
        // Stop sharing means revoke the server snapshot too. Drain already queued
        // uploads before deleting them so no late upload resurrects a session.
        synchronized (soundBuffer) { soundBuffer.reset(); }
        if (sessionId != null && serverUrl != null && !serverUrl.isEmpty()) {
            uploadQueue.execute(() -> upload("/api/watch/stop?session_id=" + sessionId,
                "application/octet-stream", new byte[0]));
        }
        if (display != null) { display.release(); display = null; }
        if (reader != null) { reader.close(); reader = null; }
        if (projection != null) { projection.stop(); projection = null; }
        DebugState.append(this, "屏幕共享结束：" + frameCount + "次画面采样；服务器缓存清理已排队");
        uploadQueue.shutdown();
        if (captureThread != null) captureThread.quitSafely();
        stopForeground(true);
        stopSelf();
    }

    private static byte[] toWav(byte[] pcm) {
        int len = pcm.length, size = len + 36;
        ByteArrayOutputStream wav = new ByteArrayOutputStream(len + 44);
        try {
            wav.write(new byte[]{'R','I','F','F'});
            writeLe(wav, size, 4); wav.write(new byte[]{'W','A','V','E','f','m','t',' '});
            writeLe(wav, 16, 4); writeLe(wav, 1, 2); writeLe(wav, 1, 2);
            writeLe(wav, 16000, 4); writeLe(wav, 32000, 4);
            writeLe(wav, 2, 2); writeLe(wav, 16, 2);
            wav.write(new byte[]{'d','a','t','a'}); writeLe(wav, len, 4); wav.write(pcm);
        } catch (Exception ignored) {}
        return wav.toByteArray();
    }
    private static void writeLe(ByteArrayOutputStream out, int value, int bytes) {
        for (int i = 0; i < bytes; i++) out.write((value >>> (8 * i)) & 255);
    }
    private void upload(String path, String mime, byte[] data) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(serverUrl + path).openConnection();
            con.setConnectTimeout(10000); con.setReadTimeout(15000);
            con.setRequestMethod("POST");
            con.setRequestProperty("X-Auth-Token", token);
            con.setRequestProperty("Content-Type", mime);
            con.setDoOutput(true);
            con.setFixedLengthStreamingMode(data.length);
            try (OutputStream out = con.getOutputStream()) { out.write(data); }
            int code = con.getResponseCode();
            if (code < 200 || code >= 300) DebugState.append(this, "屏幕共享请求失败：" + code);
        } catch (Exception e) { DebugState.append(this, "一起看上传异常：" + e.getMessage()); }
        finally { if (con != null) con.disconnect(); }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "一起看共享",
                NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                .createNotificationChannel(channel);
        }
    }
    private Notification notification(String text) {
        Intent stop = new Intent(this, TogetherWatchService.class).setAction(ACTION_STOP);
        PendingIntent action = PendingIntent.getService(this, 12, stop,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("砚团 · 一起看")
            .setContentText(text)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "立即停止", action)
            .build();
    }
    private void updateNotification(String text) {
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
            .notify(NOTIFICATION_ID, notification(text));
    }
    @Override public void onDestroy() { endCapture(); super.onDestroy(); }
}
