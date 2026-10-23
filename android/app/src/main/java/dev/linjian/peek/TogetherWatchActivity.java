package dev.linjian.peek;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.content.pm.ResolveInfo;
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
    private boolean forX = true;
    private MediaProjectionManager manager;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        new AlertDialog.Builder(this)
            .setTitle("一起看 · 先选画面")
            .setMessage("我录的是你要看的应用，不是砚团首页。先选择去哪里，授权后我会自动帮你切过去；等目标应用显示出来，才开始12秒采集。")
            .setItems(new String[]{"去 X 看视频", "去其他应用"}, (dialog, choice) -> {
                forX = choice == 0;
                askAudioMode();
            })
            .setNegativeButton("取消", (dialog, choice) -> finish())
            .setOnCancelListener(dialog -> finish())
            .show();
    }

    private void askAudioMode() {
        new AlertDialog.Builder(this)
            .setTitle("接下来录 12 秒")
            .setMessage("每隔2秒采一张画面，最多6张。你允许的话还可以收取应用开放的播放声音；不会录麦克风。\n\n系统会再次要求屏幕共享授权，请选「整个屏幕」或目标应用，别选砚团。录制有通知，随时可停；请避开密码和私人消息。")
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
        Intent target;
        try {
            if (forX) {
                target = getPackageManager().getLaunchIntentForPackage("com.twitter.android");
                if (target == null) {
                    target = new Intent(Intent.ACTION_VIEW, Uri.parse("https://x.com/home"));
                }
            } else {
                // Let the user pick an actual launchable application, not the 砚团 page.
                Intent apps = new Intent(Intent.ACTION_MAIN);
                apps.addCategory(Intent.CATEGORY_LAUNCHER);
                target = Intent.createChooser(apps, "选择要一起看的应用");
            }
            target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开目标应用，本次没有录屏", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        Intent service = new Intent(this, TogetherWatchService.class);
        service.setAction(TogetherWatchService.ACTION_START);
        service.putExtra(TogetherWatchService.EXTRA_RESULT_CODE, result);
        service.putExtra(TogetherWatchService.EXTRA_RESULT_DATA, data);
        service.putExtra(TogetherWatchService.EXTRA_APP_AUDIO, withAppAudio);
        service.putExtra(TogetherWatchService.EXTRA_TARGET_X, forX);
        if (forX) {
            android.content.ComponentName component = target.resolveActivity(getPackageManager());
            if (component != null) {
                service.putExtra(TogetherWatchService.EXTRA_TARGET_PACKAGE, component.getPackageName());
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
        else startService(service);
        try {
            startActivity(target);
            Toast.makeText(this, "已切换目标应用，等画面稳定后才会采集；可从通知停止", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            stopService(new Intent(this, TogetherWatchService.class));
            Toast.makeText(this, "目标应用打开失败，本次没有录屏", Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
