package dev.linjian.peek;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One-shot synchronized system-playback and screen-frame capture, started by local Android consent.
 * Never exposed through remote commands. No microphone fallback or local media files.
 */
public class AudioClipService extends Service {
    public static final String ACTION_START = "dev.linjian.peek.LISTEN_TOGETHER_START";
    public static final String ACTION_STOP = "dev.linjian.peek.LISTEN_TOGETHER_STOP";
    public static final String EXTRA_RESULT_CODE = "media_projection_result";
    public static final String EXTRA_PROJECTION_DATA = "media_projection_data";
    private static final String CHANNEL = "listen_together_capture";
    private static final int NOTIFICATION_ID = 8217;
    private static final int SAMPLE_RATE = 16000;
    private static final int SECONDS = 12;
    private final AtomicBoolean capturing = new AtomicBoolean(false);

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            capturing.set(false);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(intent.getAction()) || Build.VERSION.SDK_INT < 29
                || checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        // A second tap must never terminate an already running capture.
        if (!capturing.compareAndSet(false, true)) return START_NOT_STICKY;
        createChannel();
        Notification notification = notification();
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            DebugState.append(this, "一起看：启动捕获通知失败 " + e.getClass().getSimpleName());
            capturing.set(false);
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        final int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        final Intent data = intent.getParcelableExtra(EXTRA_PROJECTION_DATA);
        new Thread(() -> recordOnce(resultCode, data, startId), "ListenTogetherCapture").start();
        return START_NOT_STICKY;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "一起看·录取短片段画面和声音", NotificationManager.IMPORTANCE_LOW);
            c.setDescription("用户授权后截取12秒画面关键帧和媒体播放声音");
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }

    private Notification notification() {
        Intent stop = new Intent(this, AudioClipService.class).setAction(ACTION_STOP);
        PendingIntent cancel = PendingIntent.getService(this, 782, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return builder.setContentTitle("砚团正在看这段剧情")
                .setContentText("正在取12秒画面与声音，结束后自动停止")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_delete, "立即停止", cancel)
                .build();
    }

    private void recordOnce(int resultCode, Intent consentData, int startId) {
        MediaProjection projection = null;
        AudioRecord recorder = null;
        VideoFrameSampler sampler = null;
        try {
            if (consentData == null || resultCode != android.app.Activity.RESULT_OK) {
                throw new IllegalStateException("缺少屏幕录制授权");
            }
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(resultCode, consentData);
            if (projection == null) throw new IllegalStateException("未获取系统录制权限");
            // Android 14 requires registering an onStop callback before a VirtualDisplay is created.
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { capturing.set(false); }
            }, new Handler(Looper.getMainLooper()));
            long captureStartedMs = System.currentTimeMillis();
            long end = System.nanoTime() + SECONDS * 1_000_000_000L;

            try {
                sampler = new VideoFrameSampler(this, projection);
            } catch (Exception e) {
                DebugState.append(this, "一起看：屏幕关键帧初始化失败 " + ScreenshotService.shortMsg(e));
            }

            ByteArrayOutputStream pcm = new ByteArrayOutputStream(SAMPLE_RATE * SECONDS * 2);
            boolean signal = false;
            try {
                AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(projection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .build();
                AudioFormat format = new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();
                int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                if (minBuffer <= 0) throw new IllegalStateException("音频缓存不可用");
                recorder = new AudioRecord.Builder().setAudioFormat(format)
                        .setBufferSizeInBytes(Math.max(4096, minBuffer))
                        .setAudioPlaybackCaptureConfig(config).build();
                if (recorder.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("音频录制无法初始化");
                recorder.startRecording();
                byte[] buffer = new byte[4096];
                while (capturing.get() && System.nanoTime() < end && pcm.size() < SAMPLE_RATE * SECONDS * 2) {
                    int n = recorder.read(buffer, 0, buffer.length, AudioRecord.READ_BLOCKING);
                    if (n < 0) throw new IllegalStateException("系统声音读取失败 " + n);
                    if (n == 0) continue;
                    for (int i = 0; i + 1 < n && !signal; i += 2) {
                        int sample = (short) ((buffer[i] & 0xff) | (buffer[i + 1] << 8));
                        if (Math.abs(sample) > 30) signal = true;
                    }
                    pcm.write(buffer, 0, n);
                }
            } catch (Exception e) {
                // Playback capture can be disabled by a video app. Still collect visual frames.
                DebugState.append(this, "一起看：声音不可用，继续采集画面：" + ScreenshotService.shortMsg(e));
                while (capturing.get() && System.nanoTime() < end) {
                    try { Thread.sleep(100); } catch (InterruptedException ignored) { break; }
                }
            } finally {
                if (recorder != null) {
                    try { recorder.stop(); } catch (Exception ignored) { }
                    recorder.release();
                    recorder = null;
                }
            }

            JSONArray frames = new JSONArray();
            if (sampler != null) {
                frames = sampler.snapshot();
                sampler.close();
                sampler = null;
            }
            boolean fullyCaptured = capturing.get();
            // Capture is over before any network upload: no background recording.
            if (projection != null) {
                projection.stop();
                projection = null;
            }
            if (!fullyCaptured) {
                DebugState.append(this, "一起看：用户或系统已停止本次画面与声音采集，不上传");
                return;
            }

            // Upload two independent media types: silence must not discard usable frames.
            if (frames.length() > 0) {
                try {
                    uploadFrames(frames, captureStartedMs);
                    DebugState.append(this, "一起看：已上传 " + frames.length() + " 张关键帧");
                } catch (Exception e) {
                    DebugState.append(this, "一起看：画面上传失败 " + ScreenshotService.shortMsg(e));
                }
            } else {
                DebugState.append(this, "一起看：没有截到画面（可能是受保护的视频或屏幕）");
            }
            if (pcm.size() >= 3200 && signal) {
                try {
                    upload(asWav(pcm.toByteArray()));
                    DebugState.append(this, "一起看：已上传约 " + Math.round(pcm.size() / 32000f) + " 秒播放声音");
                } catch (Exception e) {
                    DebugState.append(this, "一起看：声音上传失败 " + ScreenshotService.shortMsg(e));
                }
            } else {
                DebugState.append(this, "一起看：无可用播放声音，仍可分析画面");
            }
        } catch (Exception e) {
            DebugState.append(this, "一起看失败：" + ScreenshotService.shortMsg(e));
            Log.w("ListenTogether", "capture failed", e);
        } finally {
            capturing.set(false);
            if (sampler != null) {
                try { sampler.close(); } catch (Exception ignored) { }
            }
            if (recorder != null) {
                try { recorder.stop(); } catch (Exception ignored) { }
                recorder.release();
            }
            if (projection != null) projection.stop();
            stopForeground(true);
            stopSelf(startId);
        }
    }

    private void uploadFrames(JSONArray frames, long captureStartedMs) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("capture_id", UUID.randomUUID().toString());
        payload.put("captured_at_ms", captureStartedMs);
        payload.put("duration_seconds", SECONDS);
        payload.put("frames", frames);
        byte[] data = payload.toString().getBytes(StandardCharsets.UTF_8);
        String endpoint = ScreenshotService.normalizeUrl(AppPrefs.server(this));
        String token = AppPrefs.token(this);
        if (endpoint.isEmpty() || token.isEmpty()) throw new IllegalStateException("请先配置服务器连接");
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint + "/api/video/clip").openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(12000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Auth-Token", token);
        conn.setRequestProperty("X-Device-Id", AppPrefs.device(this));
        conn.setFixedLengthStreamingMode(data.length);
        try {
            try (OutputStream output = conn.getOutputStream()) { output.write(data); }
            if (conn.getResponseCode() != 200)
                throw new IllegalStateException("服务器视频帧上传失败 HTTP " + conn.getResponseCode());
        } finally { conn.disconnect(); }
    }

    private void upload(byte[] audio) throws Exception {
        String endpoint = ScreenshotService.normalizeUrl(AppPrefs.server(this));
        String token = AppPrefs.token(this);
        if (endpoint.isEmpty() || token.isEmpty()) throw new IllegalStateException("请先配置服务器连接");
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint + "/api/audio/clip").openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(12000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "audio/wav");
        conn.setRequestProperty("X-Auth-Token", token);
        conn.setRequestProperty("X-Device-Id", AppPrefs.device(this));
        conn.setFixedLengthStreamingMode(audio.length);
        try {
            try (OutputStream output = conn.getOutputStream()) { output.write(audio); }
            if (conn.getResponseCode() != 200) throw new IllegalStateException("服务器收音失败 HTTP " + conn.getResponseCode());
        } finally { conn.disconnect(); }
    }

    private static void write32(ByteArrayOutputStream stream, int value) {
        for (int i = 0; i < 4; i++) stream.write((value >>> (8 * i)) & 0xff);
    }
    private static void write16(ByteArrayOutputStream stream, int value) {
        stream.write(value & 0xff);
        stream.write((value >>> 8) & 0xff);
    }
    private static byte[] asWav(byte[] pcm) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(pcm.length + 44);
        try {
            out.write(new byte[]{'R','I','F','F'});
            write32(out, pcm.length + 36);
            out.write(new byte[]{'W','A','V','E','f','m','t',' '});
            write32(out, 16); write16(out, 1); write16(out, 1);
            write32(out, SAMPLE_RATE); write32(out, SAMPLE_RATE * 2);
            write16(out, 2); write16(out, 16);
            out.write(new byte[]{'d','a','t','a'});
            write32(out, pcm.length);
            out.write(pcm);
        } catch (Exception e) { throw new IllegalStateException(e); }
        return out.toByteArray();
    }

    @Override public void onDestroy() {
        capturing.set(false);
        super.onDestroy();
    }
}
