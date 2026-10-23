package dev.linjian.peek;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.content.Context;
import android.widget.Toast;

/**
 * A recording session can only begin following an explicit tap and Android's
 * system MediaProjection consent dialog. No remote command can trigger it.
 */
public class TogetherWatchActivity extends Activity {
    private static final int REQUEST_CAPTURE = 9431;
    private static final int REQUEST_AUDIO = 9432;
    private boolean withAppAudio = false;
    private MediaProjectionManager manager;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        new AlertDialog.Builder(this)
            .setTitle("一起看 · 录12秒")
            .setMessage("录制你接下来切换到的手机画面，每隔2秒采集一张，共最多6张。可选捕获允许共享的应用播放声音，不使用麦克风。画面会上传到你设置的砚团服务器并自动过期。请勿录制密码或私人消息。\n\n录制全程有系统授权与常驻通知，随时可以停止。")
            .setPositiveButton("画面＋应用声音", (d, w) -> begin(true))
            .setNeutralButton("只录画面", (d, w) -> begin(false))
            .setNegativeButton("取消", (d, w) -> finish())
            .setOnCancelListener(d -> finish())
            .show();
    }

    private void begin(boolean audio) {
        withAppAudio = audio && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
        if (withAppAudio && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO);
            return;
        }
        requestProjection();
    }

    @Override public void onRequestPermissionsResult(int req, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(req, permissions, results);
        if (req == REQUEST_AUDIO) {
            if (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED) {
                withAppAudio = false;
                Toast.makeText(this, "录音权限未授予，改为只录画面", Toast.LENGTH_LONG).show();
            }
            requestProjection();
        }
    }

    private void requestProjection() {
        if (manager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Toast.makeText(this, "本机不支持屏幕录制", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE);
    }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req != REQUEST_CAPTURE) return;
        if (result != RESULT_OK || data == null) {
            Toast.makeText(this, "已取消录制", Toast.LENGTH_SHORT).show();
            finish(); return;
        }
        Intent service = new Intent(this, TogetherWatchService.class);
        service.setAction(TogetherWatchService.ACTION_START);
        service.putExtra(TogetherWatchService.EXTRA_RESULT_CODE, result);
        service.putExtra(TogetherWatchService.EXTRA_RESULT_DATA, data);
        service.putExtra(TogetherWatchService.EXTRA_APP_AUDIO, withAppAudio);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
        else startService(service);
        Toast.makeText(this, "3秒后开始录制12秒，切回想看的视频；点通知可停止", Toast.LENGTH_LONG).show();
        finish();
    }
}
