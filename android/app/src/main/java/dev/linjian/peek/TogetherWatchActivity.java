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
 * A screen-sharing session can only begin following an explicit tap and Android's
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
            .setTitle("一起看 · 请选择要看的应用")
            // Android's AlertDialog hides setItems when setMessage is also configured.
            // Keep the list as the actual dialog content so both choices are visible.
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
            .setTitle("开始持续屏幕共享")
            .setMessage("每2秒更新画面，云端只保留最近6张；应用允许时也会每8秒更新一段播放声音，不录麦克风。\n\n系统会要求屏幕共享授权，请选目标应用或整个屏幕，不要选砚团。通知栏可随时停止并清除服务器临时内容；最长1小时自动停止。请避开密码和私人消息。")
            .setPositiveButton("共享画面＋声音", (d, w) -> begin(true))
            .setNeutralButton("只共享画面", (d, w) -> begin(false))
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
                Toast.makeText(this, "声音权限未授予，改为只共享画面", Toast.LENGTH_LONG).show();
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
            Toast.makeText(this, "已取消共享", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "无法打开目标应用，本次没有共享", Toast.LENGTH_LONG).show();
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
            Toast.makeText(this, "已切换目标应用，画面稳定后开始共享；通知栏可停止", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            stopService(new Intent(this, TogetherWatchService.class));
            Toast.makeText(this, "目标应用打开失败，本次没有共享", Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
