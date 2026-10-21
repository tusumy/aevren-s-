package dev.linjian.peek;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * Short, explicitly-authorized screen frame sampler (not a video recording).
 * The MediaProjection belongs to AudioClipService; samples are held in RAM only.
 */
final class VideoFrameSampler implements AutoCloseable {
    private static final int MAX_FRAMES = 6;
    private static final long FRAME_INTERVAL_MS = 1900L;
    private static final int JPEG_WIDTH = 480;
    private static final int JPEG_QUALITY = 48;
    private static final int MAX_JPEG_BYTES = 170 * 1024;

    private final Object lock = new Object();
    private final JSONArray frames = new JSONArray();
    private final long startedAt = SystemClock.elapsedRealtime();
    private long lastFrameAt = 0L;
    private boolean closed;
    private final ImageReader reader;
    private final VirtualDisplay display;
    private final HandlerThread worker;

    VideoFrameSampler(Context context, MediaProjection projection) {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getRealMetrics(metrics);
        int width = Math.max(1, metrics.widthPixels);
        int height = Math.max(1, metrics.heightPixels);
        worker = new HandlerThread("ListenTogetherFrames");
        worker.start();
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        reader.setOnImageAvailableListener(this::onFrame, new Handler(worker.getLooper()));
        display = projection.createVirtualDisplay("ListenTogetherFrames", width, height,
                metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.getSurface(), null, null);
    }

    private void onFrame(ImageReader from) {
        Image image = null;
        try {
            image = from.acquireLatestImage();
            if (image == null) return;
            long now = SystemClock.elapsedRealtime();
            synchronized (lock) {
                if (closed || frames.length() >= MAX_FRAMES
                        || (now - startedAt) - lastFrameAt < FRAME_INTERVAL_MS) return;
            }
            Image.Plane plane = image.getPlanes()[0];
            int width = image.getWidth();
            int height = image.getHeight();
            int rowBytes = plane.getRowStride();
            int pixelBytes = plane.getPixelStride();
            if (pixelBytes != 4 || rowBytes % 4 != 0) return;
            Bitmap padded = Bitmap.createBitmap(rowBytes / 4, height, Bitmap.Config.ARGB_8888);
            ByteBuffer pixels = plane.getBuffer();
            pixels.rewind();
            padded.copyPixelsFromBuffer(pixels);
            Bitmap cropped = Bitmap.createBitmap(padded, 0, 0, width, height);
            padded.recycle();
            int scaledWidth = Math.min(width, JPEG_WIDTH);
            Bitmap small = Bitmap.createScaledBitmap(cropped, scaledWidth,
                    Math.max(1, Math.round((float) height * scaledWidth / width)), true);
            cropped.recycle();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            small.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, bytes);
            small.recycle();
            if (bytes.size() < 100 || bytes.size() > MAX_JPEG_BYTES) return;
            JSONObject frame = new JSONObject();
            frame.put("offset_ms", now - startedAt);
            frame.put("jpeg_b64", Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP));
            synchronized (lock) {
                if (!closed && frames.length() < MAX_FRAMES
                        && (now - startedAt) - lastFrameAt >= FRAME_INTERVAL_MS) {
                    frames.put(frame);
                    lastFrameAt = now - startedAt;
                }
            }
        } catch (Exception e) {
            // Secure / DRM video can return blank or unavailable buffers; never bypass it.
        } finally {
            if (image != null) image.close();
        }
    }

    JSONArray snapshot() {
        synchronized (lock) {
            JSONArray copy = new JSONArray();
            for (int i = 0; i < frames.length(); i++) copy.put(frames.optJSONObject(i));
            return copy;
        }
    }

    @Override public void close() {
        synchronized (lock) { if (closed) return; closed = true; }
        reader.setOnImageAvailableListener(null, null);
        display.release();
        reader.close();
        worker.quitSafely();
    }
}
