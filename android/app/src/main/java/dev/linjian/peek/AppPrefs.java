package dev.linjian.peek;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class AppPrefs {
    public static final String PREFS = "linjian_peek";
    public static final String APP_VERSION_NAME = "0.3.8.4";
    public static final int APP_VERSION_CODE = 30804;
    public static final String KEY_SERVER = "server_url";
    public static final String KEY_TOKEN = "token";
    public static final String KEY_DEVICE = "device_id";
    public static final String KEY_INTERVAL = "poll_interval_ms";
    public static final int DEFAULT_POLL_INTERVAL_MS = 3000;
    public static final int MIN_POLL_INTERVAL_MS = 2500;
    public static final int MAX_POLL_INTERVAL_MS = 15000;
    public static final int STATE_UPLOAD_INTERVAL_MS = 10000;
    public static final int ACCESSIBILITY_FALLBACK_INTERVAL_MS = 12000;
    public static final String KEY_CITY = "life_city";
    public static final String KEY_WEATHER_NOTE = "life_weather_note";
    public static final String KEY_WEATHER_LOCATIONS = "weather_locations_lines";
    public static final String KEY_THEME = "ui_theme";
    public static final String KEY_ACTIVE_REMINDERS = "active_reminders_enabled";
    public static final String KEY_RULE_BATTERY = "rule_battery_enabled";
    public static final String KEY_BATTERY_THRESHOLD = "rule_battery_threshold";
    public static final String KEY_RULE_SCREEN = "rule_screen_enabled";
    public static final String KEY_SCREEN_THRESHOLD_MIN = "rule_screen_threshold_min";
    public static final String KEY_RULE_WATER = "rule_water_enabled";
    public static final String KEY_WATER_INTERVAL_MIN = "rule_water_interval_min";
    public static final String KEY_RULE_REST = "rule_rest_enabled";
    public static final String KEY_REST_INTERVAL_MIN = "rule_rest_interval_min";
    public static final String KEY_CYCLE_ENABLED = "cycle_enabled";
    public static final String KEY_LAST_PERIOD_START = "cycle_last_period_start";
    public static final String KEY_CYCLE_LENGTH = "cycle_length_days";
    public static final String KEY_PERIOD_LENGTH = "cycle_period_length_days";
    public static final String KEY_CYCLE_REMIND_BEFORE = "cycle_remind_before_days";
    public static final String KEY_USER_NAME = "user_name";
    public static final String KEY_COMPANION_NAME = "companion_name";
    public static final String KEY_TARGET_APPS = "target_apps_lines";
    public static final String KEY_JOURNEY_ENABLED = "today_journey_enabled";
    public static final String KEY_SHOW_COMPANION_ACTIONS = "show_companion_actions";
    public static final String KEY_COMPANION_FIRST_DAY = "companion_first_day_ms";
    public static final String DEFAULT_USER_NAME = "宝宝";
    public static final String DEFAULT_COMPANION_NAME = "陪伴者";
    // 仅用于从旧公开版平滑迁移，新的 UI 和业务逻辑不再写入这两个键。
    public static final String KEY_USER_NICKNAME = "user_nickname";
    public static final String KEY_PARTNER_NICKNAME = "partner_nickname";

    public static final String KEY_FOREGROUND_POPUP = "foreground_popup_enabled";
    public static final String KEY_DESK_PET_ENABLED = "desk_pet_enabled";
    public static final String KEY_DESK_PET_X = "desk_pet_x";
    public static final String KEY_DESK_PET_Y = "desk_pet_y";
    public static final String KEY_CUSTOM_APPS = "custom_apps_lines";
    public static final String KEY_HOME_MODE_ENABLED = "home_mode_enabled";
    public static final String KEY_HOME_MODE_FORCE = "home_mode_force";
    public static final String KEY_HOME_WATCH_PACKAGES = "home_mode_watch_packages";
    public static final String KEY_HOME_THRESHOLD_MIN = "home_mode_threshold_min";
    public static final String KEY_HOME_COOLDOWN_MIN = "home_mode_cooldown_min";
    public static final String KEY_HOME_TARGET_PACKAGE = "home_mode_target_package";
    public static final String DEFAULT_HOME_TARGET_PACKAGE = "";

    public static SharedPreferences get(Context ctx) { return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    /** User-supplied public deployment address; no built-in private endpoint. */
    public static String cleanServer(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.equalsIgnoreCase("null")) return "";
        int query = value.indexOf('?');
        if (query >= 0) value = value.substring(0, query);
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    public static boolean migrateLegacyConfig(Context ctx) { return false; }
    public static String cleanUrl(String raw) { return cleanServer(raw); }
    public static String[] candidateServers(Context ctx) {
        String server = server(ctx);
        return server.length() == 0 ? new String[0] : new String[]{server};
    }
    public static String server(Context ctx) { return cleanServer(get(ctx).getString(KEY_SERVER, "")); }
    public static String token(Context ctx) { return get(ctx).getString(KEY_TOKEN, ""); }
    public static String device(Context ctx) { return get(ctx).getString(KEY_DEVICE, "android-phone"); }
    public static int interval(Context ctx) {
        int saved = get(ctx).getInt(KEY_INTERVAL, DEFAULT_POLL_INTERVAL_MS);
        if (saved < MIN_POLL_INTERVAL_MS) return DEFAULT_POLL_INTERVAL_MS;
        if (saved > MAX_POLL_INTERVAL_MS) return MAX_POLL_INTERVAL_MS;
        return saved;
    }

    /** 把旧公开版称呼和回家模式观察列表迁移到通用模板配置。 */
    public static boolean migrateTemplateConfig(Context ctx) {
        SharedPreferences prefs = get(ctx);
        SharedPreferences.Editor editor = prefs.edit();
        boolean changed = false;
        if (!prefs.contains(KEY_USER_NAME)) {
            editor.putString(KEY_USER_NAME, safeName(prefs.getString(KEY_USER_NICKNAME, ""), DEFAULT_USER_NAME));
            changed = true;
        }
        if (!prefs.contains(KEY_COMPANION_NAME)) {
            editor.putString(KEY_COMPANION_NAME, safeName(prefs.getString(KEY_PARTNER_NICKNAME, ""), DEFAULT_COMPANION_NAME));
            changed = true;
        }
        if (!prefs.contains(KEY_TARGET_APPS)) {
            StringBuilder migrated = new StringBuilder();
            String oldPackages = prefs.getString(KEY_HOME_WATCH_PACKAGES, "");
            for (String raw : oldPackages.split("[,\\n]")) {
                String pkg = raw == null ? "" : raw.trim();
                if (!isPackageLike(pkg)) continue;
                String label = pkg;
                for (Map.Entry<String, String> app : allApps(ctx).entrySet()) {
                    if (pkg.equals(app.getValue())) { label = app.getKey(); break; }
                }
                migrated.append(label).append("|").append(pkg).append("\n");
            }
            editor.putString(KEY_TARGET_APPS, migrated.toString());
            changed = true;
        }
        if (changed) editor.apply();
        return changed;
    }

    public static String userName(Context ctx) {
        SharedPreferences prefs = get(ctx);
        String v = prefs.getString(KEY_USER_NAME, prefs.getString(KEY_USER_NICKNAME, ""));
        return safeName(v, DEFAULT_USER_NAME);
    }
    public static String companionName(Context ctx) {
        SharedPreferences prefs = get(ctx);
        String v = prefs.getString(KEY_COMPANION_NAME, prefs.getString(KEY_PARTNER_NICKNAME, ""));
        return safeName(v, DEFAULT_COMPANION_NAME);
    }
    /** 兼容旧公开版调用；新代码统一使用 companionName。 */
    public static String partnerName(Context ctx) {
        return companionName(ctx);
    }

    private static String safeName(String raw, String fallback) {
        return raw == null || raw.trim().isEmpty() ? fallback : raw.trim();
    }

    public static LinkedHashMap<String, String> targetApps(Context ctx) {
        LinkedHashMap<String, String> apps = new LinkedHashMap<>();
        String raw = get(ctx).getString(KEY_TARGET_APPS, "");
        for (String line : raw.split("\\n")) {
            String value = line == null ? "" : line.trim();
            if (value.isEmpty()) continue;
            String label;
            String pkg;
            if (value.contains("|")) {
                String[] parts = value.split("\\|", 2);
                label = parts[0].trim();
                pkg = parts.length > 1 ? parts[1].trim() : "";
            } else {
                label = value;
                pkg = value;
            }
            if (!isPackageLike(pkg)) continue;
            if (label.isEmpty()) label = pkg;
            apps.put(label, pkg);
        }
        return apps;
    }

    public static String targetAppsText(Context ctx) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> app : targetApps(ctx).entrySet()) {
            out.append(app.getKey()).append("|").append(app.getValue()).append("\n");
        }
        return out.toString().trim();
    }

    public static String normalizeTargetApps(String raw) {
        LinkedHashMap<String, String> apps = new LinkedHashMap<>();
        String source = raw == null ? "" : raw.replace(',', '\n');
        for (String line : source.split("\\n")) {
            String value = line == null ? "" : line.trim();
            if (value.isEmpty()) continue;
            String[] parts = value.split("\\|", 2);
            String label = parts[0].trim();
            String pkg = parts.length > 1 ? parts[1].trim() : label;
            if (!isPackageLike(pkg)) continue;
            apps.put(label.isEmpty() ? pkg : label, pkg);
        }
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> app : apps.entrySet()) out.append(app.getKey()).append("|").append(app.getValue()).append("\n");
        return out.toString();
    }

    public static boolean isTargetPackage(Context ctx, String packageName) {
        String pkg = packageName == null ? "" : packageName.trim();
        if (pkg.isEmpty()) return false;
        for (String target : targetApps(ctx).values()) if (pkg.equals(target)) return true;
        return false;
    }

    public static String homeTargetPackage(Context ctx) {
        String raw = get(ctx).getString(KEY_HOME_TARGET_PACKAGE, DEFAULT_HOME_TARGET_PACKAGE);
        if (raw == null || raw.trim().isEmpty()) {
            for (String pkg : targetApps(ctx).values()) return pkg;
            return DEFAULT_HOME_TARGET_PACKAGE;
        }
        String resolved = packageForApp(ctx, raw.trim());
        return (resolved == null || resolved.trim().isEmpty()) ? raw.trim() : resolved.trim();
    }

    public static String homeTargetLabel(Context ctx) {
        String target = homeTargetPackage(ctx);
        if (target.isEmpty()) return "未设置";
        for (Map.Entry<String, String> e : targetApps(ctx).entrySet()) {
            if (target.equals(e.getValue())) return e.getKey();
        }
        for (Map.Entry<String, String> e : allApps(ctx).entrySet()) {
            if (target.equals(e.getValue())) return e.getKey();
        }
        return target;
    }

    public static String returnButtonText(Context ctx) {
        return "回到" + companionName(ctx) + "这里";
    }

    public static String seeButtonText(Context ctx) {
        return "给" + companionName(ctx) + "看一眼";
    }

    public static String saveHomeTarget(Context ctx, String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty()) return "";
        String pkg = packageForApp(ctx, v);
        if (pkg != null && pkg.trim().length() > 0) return pkg.trim();
        return v;
    }


    public static Map<String, String> defaultApps() {
        LinkedHashMap<String, String> apps = new LinkedHashMap<>();
        apps.put("小红书", "com.xingin.xhs");
        apps.put("微信", "com.tencent.mm");
        apps.put("QQ", "com.tencent.mobileqq");
        apps.put("抖音", "com.ss.android.ugc.aweme");
        apps.put("微博", "com.sina.weibo");
        apps.put("X", "com.twitter.android");
        return apps;
    }

    public static Map<String, String> allApps(Context ctx) {
        LinkedHashMap<String, String> apps = new LinkedHashMap<>(defaultApps());
        String custom = get(ctx).getString(KEY_CUSTOM_APPS, "");
        String[] lines = custom.split("\\n");
        for (String line : lines) {
            if (line == null) continue;
            String s = line.trim();
            if (s.isEmpty() || !s.contains("|")) continue;
            String[] parts = s.split("\\|", 2);
            String alias = parts[0].trim();
            String pkg = parts.length > 1 ? parts[1].trim() : "";
            if (!alias.isEmpty() && isPackageLike(pkg)) apps.put(alias, pkg);
        }
        return apps;
    }

    public static void saveCustomApp(Context ctx, String alias, String pkg) {
        alias = alias == null ? "" : alias.trim();
        pkg = pkg == null ? "" : pkg.trim();
        if (alias.isEmpty() || !isPackageLike(pkg)) return;
        LinkedHashMap<String, String> custom = new LinkedHashMap<>();
        String old = get(ctx).getString(KEY_CUSTOM_APPS, "");
        for (String line : old.split("\\n")) {
            if (line == null || !line.contains("|")) continue;
            String[] parts = line.trim().split("\\|", 2);
            if (parts.length == 2 && !parts[0].trim().isEmpty() && isPackageLike(parts[1].trim())) custom.put(parts[0].trim(), parts[1].trim());
        }
        custom.put(alias, pkg);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : custom.entrySet()) sb.append(e.getKey()).append("|").append(e.getValue()).append("\n");
        get(ctx).edit().putString(KEY_CUSTOM_APPS, sb.toString()).putString("pkg_" + alias.toLowerCase(Locale.US), pkg).apply();
    }

    public static String knownAppsText(Context ctx) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : allApps(ctx).entrySet()) {
            sb.append(e.getKey()).append("  →  ").append(e.getValue().isEmpty() ? "未设置" : e.getValue()).append("\n");
        }
        return sb.toS…61752 tokens truncated…tate.append(this, (makeCurrent ? "已设置当前天气地区：" : "已保存天气地区：") + (alias.isEmpty() ? city : alias));
        Toast.makeText(this, makeCurrent ? "已设为当前地区" : "已保存地区", Toast.LENGTH_SHORT).show();
        updateUI();
    }


    private void chooseGuidianAvatar() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("image/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(i, REQ_GUIDIAN_AVATAR);
        } catch (Exception e) { Toast.makeText(this, "系统相册没有接住选择头像", Toast.LENGTH_SHORT).show(); }
    }

    private void chooseDiaryExport() {
        try {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.setType("application/json"); i.addCategory(Intent.CATEGORY_OPENABLE);
            i.putExtra(Intent.EXTRA_TITLE, "掌心窗-TA的日记-" + new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date()) + ".json");
            startActivityForResult(i, REQ_DIARY_EXPORT);
        } catch (Exception e) { Toast.makeText(this, "系统文件管理器没有接住导出", Toast.LENGTH_SHORT).show(); }
    }

    private void chooseDiaryImport() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("application/json"); i.addCategory(Intent.CATEGORY_OPENABLE); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(i, REQ_DIARY_IMPORT);
        } catch (Exception e) { Toast.makeText(this, "系统文件管理器没有接住导入", Toast.LENGTH_SHORT).show(); }
    }

    private void exportDiaryTo(Uri uri) {
        try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
            if (output == null) throw new IllegalStateException("output_unavailable");
            output.write(DiaryState.exportBundle(this).toString(2).getBytes(StandardCharsets.UTF_8)); output.flush();
            Toast.makeText(this, "日记备份已导出", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { Toast.makeText(this, "导出失败：" + ScreenshotService.shortMsg(e), Toast.LENGTH_LONG).show(); }
    }

    private void importDiaryFrom(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            if (input == null) throw new IllegalStateException("input_unavailable");
            byte[] buffer = new byte[8192]; int n; while ((n = input.read(buffer)) >= 0) { bytes.write(buffer, 0, n); if (bytes.size() > 16 * 1024 * 1024) throw new IllegalStateException("backup_too_large"); }
            String raw = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            new AlertDialog.Builder(this).setTitle("导入日记备份？").setMessage("会把备份中的日记本和纸页合并到本机；相同 id 的内容不会重复导入。").setNegativeButton("取消", null).setPositiveButton("导入", (d, w) -> { JSONObject result = DiaryState.importBundle(this, raw); Toast.makeText(this, result.optBoolean("ok") ? "日记备份已导入" : ("导入失败：" + result.optString("error")), Toast.LENGTH_LONG).show(); }).show();
        } catch (Exception e) { Toast.makeText(this, "导入失败：" + ScreenshotService.shortMsg(e), Toast.LENGTH_LONG).show(); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_GUIDIAN_AVATAR && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
            AppPrefs.get(this).edit().putString(GuidianState.KEY_AVATAR_URI, uri.toString()).apply();
            Toast.makeText(this, AppPrefs.companionName(this) + "的头像已换好", Toast.LENGTH_SHORT).show();
            updateUI();
        }
        if (requestCode == REQ_DIARY_COVER && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
            DiaryState.updateCover(this, diaryBookId, "local_image", uri.toString());
            Toast.makeText(this, "日记本封面已换好", Toast.LENGTH_SHORT).show(); showDiaryHomePage();
        } else if (requestCode == REQ_DIARY_EXPORT && resultCode == RESULT_OK && data != null && data.getData() != null) exportDiaryTo(data.getData());
        else if (requestCode == REQ_DIARY_IMPORT && resultCode == RESULT_OK && data != null && data.getData() != null) importDiaryFrom(data.getData());
    }

    private void startCompanionService() {
        saveSettings();
        String url = serverUrl == null ? "" : serverUrl.getText().toString().trim(); String token = tokenInput == null ? "" : tokenInput.getText().toString().trim();
        if (url.isEmpty() || token.isEmpty()) { Toast.makeText(this, "请填写服务器地址和 Token", Toast.LENGTH_SHORT).show(); return; }
        if (ScreenshotService.getInstance() == null) { DebugState.append(this, "启动失败：无障碍服务未连接"); Toast.makeText(this, "请先开启掌心窗无障碍服务", Toast.LENGTH_LONG).show(); openAccessibilitySettings(); return; }
        getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE).edit().putBoolean("user_stopped", false).apply(); requestIgnoreBatteryOptimization();
        Intent intent = new Intent(this, CompanionService.class); intent.putExtra("server_url", url); intent.putExtra("token", token);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent); else startService(intent);
        DebugState.append(this, "已请求启动前台服务：公开版 v0.3.8.4 右侧 love 线稿花枝已启用"); serviceRunning = true; updateUI();
    }

    private void stopCompanionService() { getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE).edit().putBoolean("user_stopped", true).apply(); stopService(new Intent(this, CompanionService.class)); DebugState.append(this, "已停止服务"); serviceRunning = false; updateUI(); }

    private void testScreenshot() {
        saveSettings(); String url = serverUrl == null ? "" : serverUrl.getText().toString().trim(); String token = tokenInput == null ? "" : tokenInput.getText().toString().trim(); ScreenshotService ss = ScreenshotService.getInstance();
        if (url.isEmpty() || token.isEmpty()) { Toast.makeText(this, "先填服务器地址和 Token", Toast.LENGTH_SHORT).show(); return; }
        if (ss == null) { DebugState.append(this, "测试失败：无障碍服务未连接"); Toast.makeText(this, "先开启无障碍服务", Toast.LENGTH_LONG).show(); openAccessibilitySettings(); return; }
        DebugState.append(this, "给" + AppPrefs.companionName(this) + "看一眼：开始截图上传"); ss.doScreenshot(url, token); Toast.makeText(this, "正在上传截图", Toast.LENGTH_SHORT).show(); updateUI();
    }
    private void testAlarm() { Calendar c = Calendar.getInstance(); c.add(Calendar.MINUTE, 1); try { Intent i = new Intent(AlarmClock.ACTION_SET_ALARM); i.putExtra(AlarmClock.EXTRA_HOUR, c.get(Calendar.HOUR_OF_DAY)); i.putExtra(AlarmClock.EXTRA_MINUTES, c.get(Calendar.MINUTE)); i.putExtra(AlarmClock.EXTRA_MESSAGE, "掌心窗测试闹钟：" + AppPrefs.userName(this)); i.putExtra(AlarmClock.EXTRA_VIBRATE, true); i.putExtra(AlarmClock.EXTRA_SKIP_UI, true); startActivity(i); DebugState.append(this, "已请求设置一分钟后的测试闹钟"); } catch (Exception e) { DebugState.append(this, "测试闹钟失败：" + e.getClass().getSimpleName()); Toast.makeText(this, "闹钟 App 没接住请求", Toast.LENGTH_SHORT).show(); } }
    private void testNotification() { saveSettings(); boolean ok = CompanionService.showReminderNotification(this, "掌心窗悬浮横幅测试", AppPrefs.userName(this) + "看到了顶部横幅，就说明通知通道正常。"); DebugState.append(this, ok ? "已发送悬浮横幅测试提醒" : "悬浮横幅/通知失败：请允许掌心窗发送通知"); Toast.makeText(this, ok ? "已发送横幅测试" : "请先允许通知权限", Toast.LENGTH_SHORT).show(); updateUI(); }
    private void addPackageAlias() { String alias = appAliasInput == null ? "" : appAliasInput.getText().toString().trim(); String pkg = appPackageInput == null ? "" : appPackageInput.getText().toString().trim(); if (alias.isEmpty()) { Toast.makeText(this, "先填应用名/昵称", Toast.LENGTH_SHORT).show(); return; } if (!AppPrefs.isPackageLike(pkg)) { Toast.makeText(this, "包名格式不对，例如 com.xingin.xhs", Toast.LENGTH_LONG).show(); return; } AppPrefs.saveCustomApp(this, alias, pkg); DebugState.append(this, "已保存可打开应用：" + alias + " → " + pkg); Toast.makeText(this, "已添加包名", Toast.LENGTH_SHORT).show(); updateUI(); }
    private void addGateApp() { String alias = gateAliasInput == null ? "" : gateAliasInput.getText().toString().trim(); String pkg = gatePackageInput == null ? "" : gatePackageInput.getText().toString().trim(); if (alias.isEmpty()) { Toast.makeText(this, "先填应用名/昵称", Toast.LENGTH_SHORT).show(); return; } if (!AppPrefs.isPackageLike(pkg)) { Toast.makeText(this, "包名格式不对，例如 com.xingin.xhs", Toast.LENGTH_LONG).show(); return; } AppGate.addGateApp(this, alias, pkg); DebugState.append(this, "已保存门禁应用：" + alias + " → " + pkg); Toast.makeText(this, "已添加到应用门禁", Toast.LENGTH_SHORT).show(); updateUI(); }
    private void testCustomPackage() { String pkg = appPackageInput == null ? "" : appPackageInput.getText().toString().trim(); if (!AppPrefs.isPackageLike(pkg)) { Toast.makeText(this, "先填正确包名", Toast.LENGTH_SHORT).show(); return; } openPackage(pkg); }
    private void testLocalSequence() { boolean ok1 = CompanionService.showReminderNotification(this, "掌心窗连招测试", "先发悬浮横幅，再回目标 APP。日志会写清每一步。"); String result = CompanionService.openPackageResult(this, AppPrefs.homeTargetPackage(this)); DebugState.append(this, "本机连招测试：popup=" + ok1 + "；open=" + result); updateUI(); }
    private boolean openPackage(String pkg) { String result = CompanionService.openPackageResult(this, pkg); boolean ok = result.startsWith("opened_"); DebugState.append(this, "本机打开 App：" + result); Toast.makeText(this, ok ? "已尝试打开" : ("打开失败：" + result), Toast.LENGTH_SHORT).show(); updateUI(); return ok; }
    private void updateVersionUi() {
        boolean hasNew = latestVersionCode > AppPrefs.APP_VERSION_CODE;
        if (versionStatusText != null) {
            versionStatusText.setText("当前版本：" + AppPrefs.APP_VERSION_NAME + "（" + AppPrefs.APP_VERSION_CODE + "）\n" +
                    (hasNew ? "发现新版本：" + latestVersionName + "（" + latestVersionCode + "）" : "已是最新版本"));
        }
        if (updateChangelogText != null) updateChangelogText.setText(latestChangelog.isEmpty() ? "点击检查更新，可查看更新日志并前往下载最新版。" : latestChangelog);
        if (licenseSummaryText != null) licenseSummaryText.setText("本公开版保留原项目许可：可阅读、学习、个人自用部署和本地修改；二次分发、商业用途及移除作者说明须取得许可。详见源码包 LICENSE。");
        if (downloadUpdateButton != null) downloadUpdateButton.setEnabled(hasNew && !latestApkUrl.isEmpty());
    }

    private void checkForUpdates(boolean manual) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(DEFAULT_UPDATE_URL).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("User-Agent", "Zhangxinchuang-Public/" + AppPrefs.APP_VERSION_NAME);
                if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) throw new IllegalStateException("HTTP " + connection.getResponseCode());
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream in = connection.getInputStream()) {
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = in.read(buffer)) >= 0) bytes.write(buffer, 0, count);
                }
                JSONObject info = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                latestVersionCode = info.optInt("latest_version_code", AppPrefs.APP_VERSION_CODE);
                latestVersionName = info.optString("latest_version_name", AppPrefs.APP_VERSION_NAME);
                latestApkUrl = info.optString("apk_url", "").trim();
                JSONArray changes = info.optJSONArray("changelog");
                StringBuilder text = new StringBuilder();
                if (changes != null) for (int i = 0; i < changes.length(); i++) text.append("• ").append(changes.optString(i)).append("\n");
                latestChangelog = text.toString().trim();
                runOnUiThread(() -> { updateVersionUi(); if (manual) Toast.makeText(this, latestVersionCode > AppPrefs.APP_VERSION_CODE ? "发现新版本 " + latestVersionName : "当前已是最新版本", Toast.LENGTH_SHORT).show(); });
            } catch (Exception error) {
                runOnUiThread(() -> { updateVersionUi(); if (manual) Toast.makeText(this, "检查更新失败，请稍后重试", Toast.LENGTH_SHORT).show(); });
            } finally { if (connection != null) connection.disconnect(); }
        }, "public-update-check").start();
    }

    private void downloadLatestApk() {
        if (latestApkUrl.isEmpty()) { Toast.makeText(this, "请先检查更新", Toast.LENGTH_SHORT).show(); return; }
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(latestApkUrl))); }
        catch (Exception e) { Toast.makeText(this, "无法打开下载地址", Toast.LENGTH_SHORT).show(); }
    }

    private void toast(boolean ok) { Toast.makeText(this, ok ? "执行成功" : "执行失败，请检查权限/包名", Toast.LENGTH_SHORT).show(); updateUI(); }
    private int parseInterval(String raw) {
        try {
            int v = Integer.parseInt(raw);
            if (v < AppPrefs.MIN_POLL_INTERVAL_MS) return AppPrefs.DEFAULT_POLL_INTERVAL_MS;
            if (v > AppPrefs.MAX_POLL_INTERVAL_MS) return AppPrefs.MAX_POLL_INTERVAL_MS;
            return v;
        } catch (Exception e) { return AppPrefs.DEFAULT_POLL_INTERVAL_MS; }
    }
    private int parseInt(String raw, int def, int min, int max) { try { int v = Integer.parseInt(raw); if (v < min) return min; if (v > max) return max; return v; } catch (Exception e) { return def; } }
    private void openAccessibilitySettings() {
        try {
            getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE).edit().putLong(PREF_A11Y_SETTINGS_OPENED_AT, System.currentTimeMillis()).apply();
            Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(i);
            Toast.makeText(this, "开启“掌心窗服务”后返回；若返回仍未开启，请先允许受限设置", Toast.LENGTH_LONG).show();
            scheduleAccessibilityFollowupChecks();
        } catch (Exception e) {
            Toast.makeText(this, "设置 → 应用 → 掌心窗 → 允许受限设置；再到无障碍开启掌心窗服务", Toast.LENGTH_LONG).show();
        }
    }

    private void openAppDetailsSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
            Toast.makeText(this, "如有右上角菜单，请先点“允许受限设置”，再回无障碍开启掌心窗服务", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "设置 → 应用 → 掌心窗 → 右上角 → 允许受限设置", Toast.LENGTH_LONG).show();
        }
    }

    private void showAccessibilityHelpDialog() {
        try {
            new AlertDialog.Builder(this)
                    .setTitle("无障碍还没真正连上")
                    .setMessage("如果你在系统无障碍里打开后，回到掌心窗又变成未开启，通常是系统还没完成绑定，或 Android/部分国产系统拦截了侧载 APK 的无障碍权限。\n\n请先等 5-10 秒；如果仍未开启，到“应用信息 → 掌心窗 → 右上角菜单”允许受限设置，然后再回无障碍开启“掌心窗服务”。")
                    .setPositiveButton("去无障碍设置", (d, w) -> openAccessibilitySettings())
                    .setNegativeButton("去应用信息", (d, w) -> openAppDetailsSettings())
                    .setNeutralButton("我知道了", null)
                    .show();
        } catch (Exception e) {
            Toast.makeText(this, "先允许受限设置，再开启掌心窗服务", Toast.LENGTH_LONG).show();
        }
    }

    private void scheduleAccessibilityFollowupChecks() {
        long[] delays = new long[] { 600L, 1500L, 3000L, 6000L, 12000L };
        for (long delay : delays) {
            uiHandler.postDelayed(() -> { serviceRunning = CompanionService.isRunning(); updateUI(); }, delay);
        }
    }

    private boolean recentlyOpenedAccessibilitySettings() {
        try {
            long t = getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE).getLong(PREF_A11Y_SETTINGS_OPENED_AT, 0L);
            return t > 0 && System.currentTimeMillis() - t < A11Y_CONFIRM_WINDOW_MS;
        } catch (Exception ignored) { return false; }
    }

    private String accessibilityComponentLong() {
        return new ComponentName(this, ScreenshotService.class).flattenToString();
    }

    private String accessibilityComponentShort() {
        return getPackageName() + "/." + ScreenshotService.class.getSimpleName();
    }

    private boolean classMatchesAccessibilityService(String cls) {
        if (cls == null) return false;
        String c = cls.trim();
        return ScreenshotService.class.getName().equals(c)
                || c.endsWith("." + ScreenshotService.class.getSimpleName())
                || ScreenshotService.class.getSimpleName().equals(c);
    }

    private boolean matchesAccessibilityComponent(String raw) {
        if (raw == null) return false;
        String item = raw.trim();
        if (item.length() == 0) return false;
        String expectedLong = accessibilityComponentLong();
        String expectedShort = accessibilityComponentShort();
        if (expectedLong.equalsIgnoreCase(item) || expectedShort.equalsIgnoreCase(item)) return true;
        try {
            ComponentName cn = ComponentName.unflattenFromString(item);
            if (cn != null && getPackageName().equals(cn.getPackageName()) && classMatchesAccessibilityService(cn.getClassName())) return true;
        } catch (Exception ignored) { }
        String lower = item.toLowerCase(Locale.ROOT).replace(" ", "");
        String pkg = getPackageName().toLowerCase(Locale.ROOT);
        String full = ScreenshotService.class.getName().toLowerCase(Locale.ROOT);
        String simple = ScreenshotService.class.getSimpleName().toLowerCase(Locale.ROOT);
        return lower.contains(pkg + "/") && (lower.contains(full) || lower.contains("/.") && lower.contains(simple) || lower.endsWith("/" + simple));
    }

    private String rawEnabledAccessibilityServices() {
        try {
            String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return enabled == null ? "" : enabled;
        } catch (Exception ignored) { return ""; }
    }

    private boolean isAccessibilityServiceEnabledInSettings() {
        try {
            String enabled = rawEnabledAccessibilityServices();
            if (enabled.length() == 0) return false;
            TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
            splitter.setString(enabled);
            while (splitter.hasNext()) {
                if (matchesAccessibilityComponent(splitter.next())) return true;
            }
            // Some OEM ROMs store accessibility components in a slightly non-standard shape.
            // If the secure setting clearly contains this package and service class, treat it as enabled.
            if (matchesAccessibilityComponent(enabled)) return true;
        } catch (Exception ignored) { }
        return false;
    }

    private boolean isAccessibilityServiceEnabledByManager() {
        try {
            AccessibilityManager manager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
            if (manager == null) return false;
            List<AccessibilityServiceInfo> services = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            if (services == null) return false;
            for (AccessibilityServiceInfo info : services) {
                if (info == null || info.getResolveInfo() == null || info.getResolveInfo().serviceInfo == null) continue;
                String pkg = info.getResolveInfo().serviceInfo.packageName;
                String cls = info.getResolveInfo().serviceInfo.name;
                if (getPackageName().equals(pkg) && classMatchesAccessibilityService(cls)) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    private boolean isAccessibilityServiceEnabled() {
        boolean enabled = isAccessibilityServiceEnabledInSettings() || isAccessibilityServiceEnabledByManager();
        if (enabled && getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE).getLong(PREF_A11Y_SETTINGS_OPENED_AT, 0L) > 0) {
            getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE).edit().remove(PREF_A11Y_SETTINGS_OPENED_AT).apply();
        }
        return enabled;
    }

    private void openUsageAccessSettings() { try { startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); } catch (Exception e) { Toast.makeText(this, "设置 → 应用 → 特殊权限 → 使用情况访问", Toast.LENGTH_LONG).show(); } }
    private void openNotificationListenerSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            Toast.makeText(this, "开启“掌心窗媒体状态”后返回；仅用于此刻卡片显示正在播放的音频", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "设置 → 应用 → 特殊权限 → 通知使用权 → 掌心窗媒体状态", Toast.LENGTH_LONG).show();
        }
    }
    private void requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= 23 && !NowState.hasLocationPermission(this)) requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 24);
        else Toast.makeText(this, "定位权限已开启", Toast.LENGTH_SHORT).show();
        updateUI();
    }
    private void openOverlayPermissionSettings() {
        try {
            if (Build.VERSION.SDK_INT >= 23) startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            else Toast.makeText(this, "当前系统无需单独开启悬浮窗权限", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { Toast.makeText(this, "设置 → 应用 → 特殊权限 → 悬浮窗", Toast.LENGTH_LONG).show(); }
    }
    private void requestIgnoreBatteryOptimization() { if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return; try { PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE); if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) { Intent bi = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS); bi.setData(Uri.parse("package:" + getPackageName())); startActivity(bi); } } catch (Exception ignored) { } }

    private void updateUI() {
        boolean accessibilityEnabled = isAccessibilityServiceEnabled();
        boolean accessibilityConnected = accessibilityEnabled && ScreenshotService.getInstance() != null;
        boolean accessibilityConfirming = !accessibilityEnabled && recentlyOpenedAccessibilitySettings();
        boolean accessibilityOk = accessibilityEnabled;
        boolean usageOk = LifeState.hasUsagePermission(this);
        UITheme visualTheme = UITheme.current(this);
        updateHeader(currentTab);
        if (serviceRunning) { if (statusText != null) { statusText.setText(accessibilityOk ? "●  窗已打开 · 陪伴和守护都在" : (accessibilityConfirming ? "●  生活小窗已打开 · 正在确认无障碍" : "●  生活小窗已打开 · 无障碍待开启")); statusText.setTextColor(accessibilityOk ? visualTheme.primary : 0xFFCF8A62); } if (toggleButton != null) { toggleButton.setText("停止服务"); toggleButton.setBackgroundResource(R.drawable.pill_danger); } }
        else { if (statusText != null) { statusText.setText(accessibilityOk ? "○  感官已准备 · 服务等待开启" : (accessibilityConfirming ? "○  正在确认无障碍状态" : "○  天气可用 · 无障碍待开启")); statusText.setTextColor(accessibilityConfirming ? 0xFFCF8A62 : visualTheme.subtext); } if (toggleButton != null) { toggleButton.setText("启动服务"); toggleButton.setBackgroundResource(R.drawable.pill_primary); } }
        if (accessibilityButton != null) accessibilityButton.setText(accessibilityOk ? (accessibilityConnected ? "无障碍权限：已开启" : "无障碍权限：系统已开启，等待连接") : (accessibilityConfirming ? "无障碍权限：正在确认，点此查看提示" : "打开无障碍设置"));
        if (usageAccessButton != null) usageAccessButton.setText(usageOk ? "使用情况权限：已开启" : "打开使用情况访问权限");
        if (locationPermissionButton != null) locationPermissionButton.setText(NowState.hasLocationPermission(this) ? "定位权限：已开启" : "打开定位权限");
        if (overlayPermissionButton != null) overlayPermissionButton.setText((Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)) ? "悬浮窗权限：已开启" : "打开悬浮窗权限");
        if (notificationListenerButton != null) notificationListenerButton.setText(MediaState.hasNotificationListenerAccess(this) ? "媒体状态权限：已开启" : "打开媒体状态权限");
        if (nowStatePermissionText != null) nowStatePermissionText.setText("此刻状态：" + (NowState.hasLocationPermission(this) ? "定位已授权" : "定位未授权") + " · " + ((Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)) ? "悬浮窗已授权" : "悬浮窗未授权") + " · " + (MediaState.hasNotificationListenerAccess(this) ? "媒体已授权" : "媒体未授权") + "\n用于状态卡片、应用门禁悬浮页和正在播放媒体显示。权限均由用户在本机开启。");
        try {
            JSONObject s = LifeState.collect(this);
            int battery = s.optInt("battery_percent", -1); boolean charging = s.optBoolean("charging", false);
            if (overviewBatteryText != null) overviewBatteryText.setText(battery >= 0 ? battery + "%" : "-");
            if (overviewBatteryDetail != null) overviewBatteryDetail.setText(charging ? "正在充电" : "未充电");
            if (overviewAppText != null) overviewAppText.setText(s.optString("current_app", "-").isEmpty() ? "暂未识别" : s.optString("current_app", "-"));
            if (overviewAppDetail != null) overviewAppDetail.setText(s.optBoolean("screen_on") ? "屏幕亮着" : "屏幕已熄灭");
            int mins = s.optInt("screen_time_today_minutes", 0);
            if (overviewScreenText != null) overviewScreenText.setText(formatMinutes(mins));
            if (overviewScreenDetail != null) overviewScreenDetail.setText("解锁 " + s.optInt("unlock_count_today", 0) + " 次");
            JSONObject w = s.optJSONObject("current_weather_location");
            updateWeatherOverview(w);
            updateHeroOverview(s);
            updateJourney(s);
            updateGuardOverview(s);
            if (nowStateText != null) nowStateText.setText(NowState.pretty(this));
        } catch (Exception ignored) { }
        if (lifeSummaryText != null) lifeSummaryText.setText(lifeSummary());
        if (lifeStatusText != null) lifeStatusText.setText(LifeState.pretty(this));
        if (calendarSummaryText != null) calendarSummaryText.setText("把重要日子轻轻放进窗边。");
        if (calendarDetailText != null) calendarDetailText.setText("标题、日期、分组和备注填好后保存。农历生日、七夕和中秋记得勾选农历日期。");
        updateGuardianCalendarView();
        if (drawerCalendarButton != null && (drawerCalendar == null || drawerCalendar.getVisibility() != View.VISIBLE)) drawerCalendarButton.setText(CalendarState.summaryLine(this) + "  ›");
        if (drawerWeatherButton != null && (drawerWeather == null || drawerWeather.getVisibility() != View.VISIBLE)) drawerWeatherButton.setText(WeatherState.summaryLine(this) + "  ›");
        if (weatherLocationsText != null) weatherLocationsText.setText(WeatherState.locationsText(this));
        if (knownAppsText != null) knownAppsText.setText(AppPrefs.knownAppsText(this));
        if (homeModeStatusText != null) homeModeStatusText.setText(HomeMode.pretty(this));
        if (drawerAppGateButton != null && (drawerAppGate == null || drawerAppGate.getVisibility() != View.VISIBLE)) drawerAppGateButton.setText(AppGate.summaryLine(this) + "  ›");
        if (gateStatusText != null) gateStatusText.setText(AppGate.prettyClean(this));
        if (debugText != null) debugText.setText(DebugState.get(this));
        if (themeText != null) themeText.setText("当前主题：" + AppPrefs.get(this).getString(AppPrefs.KEY_THEME, "白桃粉") + "\n点击后即时切换背景、卡片、按钮和底部导航。守护日历主色：#B8A8D8。");
        if (guidianSummaryText != null) guidianSummaryText.setText(GuidianState.summaryText(this));
        if (guidianDetailText != null) guidianDetailText.setText(GuidianState.detailText(this));
        if (guidianSettingsStatusText != null) guidianSettingsStatusText.setText(GuidianState.detailText(this));
        if (guidianAvatarText != null) {
            String avatar = AppPrefs.get(this).getString(GuidianState.KEY_AVATAR_URI, "");
            guidianAvatarText.setText(avatar == null || avatar.length() == 0 ? "当前使用默认头像。" : "已使用你选择的头像。\n" + avatar);
        }
        renderCompanionAvatar();
        updateCompanionDays();
        updateCompanionAnniversary(nearestCalendarEvent(null));
        renderCompanionState(CompanionWindowState.cached(this));
        long now = System.currentTimeMillis();
        if (now - lastCompanionSyncAt > 30_000L && AppPrefs.server(this) != null && !AppPrefs.server(this).trim().isEmpty()) {
            lastCompanionSyncAt = now;
            CompanionWindowState.sync(this, 20, (state, error) -> runOnUiThread(() -> renderCompanionState(state)));
        }
        if (drawerGuidianButton != null && (drawerGuidian == null || drawerGuidian.getVisibility() != View.VISIBLE)) drawerGuidianButton.setText("归电  ›");
        if (drawerGuidianSettingsButton != null && (drawerGuidianSettings == null || drawerGuidianSettings.getVisibility() != View.VISIBLE)) drawerGuidianSettingsButton.setText("归电设置  ›");
        updateVersionUi();
        applyVisualTheme();
        updateGuardianCalendarView();
    }

    private void renderCompanionAvatar() {
        if (companionAvatarView == null) return;
        String raw = AppPrefs.get(this).getString(GuidianState.KEY_AVATAR_URI, "");
        if (raw == null || raw.isEmpty()) {
            companionAvatarView.clearImage();
            Drawable fallback = getDrawable(R.drawable.ic_heart_wave).mutate();
            fallback.setTint(UITheme.current(this).primary);
            companionAvatarView.setFallback(fallback);
            return;
        }
        try {
            companionAvatarView.setImageUri(Uri.parse(raw));
        } catch (Exception ignored) { companionAvatarView.clearImage(); }
    }

    private void renderCompanionState(JSONObject state) {
        if (state == null) state = CompanionWindowState.cached(this);
        JSONObject whisper = state.optJSONObject("whisper");
        if (whisper != null) {
            if (sharedWhisperText != null) sharedWhisperText.setText("“" + whisper.optString("content", "把今天，轻轻收进窗里。") + "”");
            if (sharedWhisperMetaText != null) {
                String at = CompanionWindowState.elapsed(whisper.optString("updated_at", ""));
                sharedWhisperMetaText.setText(whisper.optString("author", AppPrefs.companionName(this)) + "修改" + (at.isEmpty() ? "" : "于 " + at));
            }
        }
        JSONArray actions = CompanionWindowState.actions(this);
        boolean visible = AppPrefs.get(this).getBoolean(AppPrefs.KEY_SHOW_COMPANION_ACTIONS, true);
        if (companionActionsPreview != null) {
            if (!visible) companionActionsPreview.setText("行动记录已在设置中隐藏。");
            else if (actions.length() == 0) companionActionsPreview.setText(AppPrefs.companionName(this) + "现在安安静静的，暂时没有新的行动。");
            else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(5, actions.length()); i++) {
                    JSONObject a = actions.optJSONObject(i);
                    if (a == null) continue;
                    sb.append("●  ").append(a.optString("title", "完成了一次行动")).append("\n    ").append(CompanionWindowState.elapsed(a.optString("created_at", a.optString("at", ""))));
                    if (i + 1 < Math.min(5, actions.length())) sb.append("\n\n");
                }
                companionActionsPreview.setText(sb.toString());
            }
        }
        if (companionRestArt != null) companionRestArt.setVisibility(visible && actions.length() == 0 ? View.VISIBLE : View.GONE);
        if (companionPresenceText != null) {
            if (!visible || actions.length() == 0) companionPresenceText.setText("今天还没有新的行动");
            else {
                JSONObject latest = actions.optJSONObject(0);
                companionPresenceText.setText((latest == null ? "刚刚来过" : CompanionWindowState.elapsed(latest.optString("created_at", latest.optString("at", ""))) + " · " + latest.optString("title", "来过窗边")));
            }
        }
    }

    private void updateJourney(JSONObject state) {
        if (todayJourneyText == null) return;
        JSONArray journey = CompanionWindowState.journey(this);
        StringBuilder sb = new StringBuilder();
        int count = Math.min(5, journey.length());
        for (int i = 0; i < count; i++) {
            JSONObject item = journey.optJSONObject(i);
            if (item == null) continue;
            sb.append(item.optString("local_time", item.optString("time", "--:--"))).append("   ●  ").append(item.optString("title", "今日记录"));
            String detail = item.optString("subtitle", item.optString("detail", ""));
            if (!detail.isEmpty()) sb.append("\n           ").append(detail);
            if (i + 1 < count) sb.append("\n\n");
        }
        if (sb.length() == 0) sb.append("今天还没有留下轨迹。");
        todayJourneyText.setText(sb.toString());
    }

    private void updateCompanionDays() {
        if (companionDaysText == null) return;
        long now = System.currentTimeMillis();
        long first = AppPrefs.get(this).getLong(AppPrefs.KEY_COMPANION_FIRST_DAY, 0L);
        if (first <= 0L) {
            first = now;
            AppPrefs.get(this).edit().putLong(AppPrefs.KEY_COMPANION_FIRST_DAY, first).apply();
        }
        long days = Math.max(1L, (now - first) / 86_400_000L + 1L);
        companionDaysText.setText("第 " + days + " 天");
        if (companionSinceText != null) companionSinceText.setText(new SimpleDateFormat("yyyy年M月d日开始", Locale.CHINA).format(new Date(first)));
    }

    private void updateGuardOverview(JSONObject state) {
        if (guardOverviewText == null) return;
        String online = serviceRunning ? "设备在线" : "服务等待开启";
        String network = state.optString("network_type", "unknown");
        int battery = state.optInt("battery_percent", -1);
        guardOverviewText.setText(online + "，今日没有异常提醒\n" + (battery >= 0 ? "电量 " + battery + "%" : "电量未读取") + "  ·  " + network);
        if (guardDeviceStatusText != null) guardDeviceStatusText.setText((serviceRunning ? "在线" : "等待连接") + " · " + network + (battery >= 0 ? " · 电量 " + battery + "%" : ""));
        if (guardRecordText != null) guardRecordText.setText(
                (serviceRunning ? "✓   设备保持在线" : "○   服务等待开启") + "\n      自动检查持续进行\n\n" +
                "⌂   常用地点状态已同步\n      天气与生活状态保持更新\n\n" +
                "ϟ   " + (battery >= 0 ? "当前电量 " + battery + "%" : "电量提醒规则正在守护"));
        if (guardianRing != null) { guardianRing.setProgress(battery >= 0 ? Math.max(.18f, battery / 100f) : (serviceRunning ? .82f : .28f)); guardianRing.invalidate(); }
        updateCompanionAnniversary(nearestCalendarEvent(state));
    }

    private void updateWeatherOverview(JSONObject w) {
        if (overviewWeatherText == null) return;
        String name = w == null ? "当前地区" : w.optString("name", "当前地区");
        String city = w == null ? "" : w.optString("city", "");
        JSONObject live = WeatherLive.cached(this, city);
        if (live != null && live.optBoolean("ok")) {
            overviewWeatherText.setText(live.optInt("temperature", 0) + "℃");
            if (overviewWeatherDetail != null) overviewWeatherDetail.setText(name + " · " + live.optString("condition", "天气"));
        } else {
            overviewWeatherText.setText(name);
            if (overviewWeatherDetail != null) overviewWeatherDetail.setText(city.isEmpty() ? "未设城市" : city);
        }
        long now = System.currentTimeMillis();
        if (!city.isEmpty() && !weatherFetching && !WeatherLive.isFresh(this, city, 45L * 60L * 1000L) && now - lastWeatherFetchAt > 45_000L) {
            weatherFetching = true;
            lastWeatherFetchAt = now;
            WeatherLive.refreshAsync(this, city, weather -> runOnUiThread(() -> {
                weatherFetching = false;
                if (weather != null && weather.optBoolean("ok")) {
                    overviewWeatherText.setText(weather.optInt("temperature", 0) + "℃");
                    if (overviewWeatherDetail != null) overviewWeatherDetail.setText(name + " · " + weather.optString("condition", "天气"));
                    try { updateHeroOverview(LifeState.collect(this)); } catch (Exception ignored) { }
                }
            }));
        }
    }

    private void updateHeroOverview(JSONObject s) {
        if (s == null) return;
        int battery = s.optInt("battery_percent", -1);
        boolean charging = s.optBoolean("charging", false);
        int screenMinutes = s.optInt("screen_time_today_minutes", 0);
        JSONObject calendar = s.optJSONObject("calendar_state");
        JSONObject nearest = firstCalendarItem(calendar, "active_banners");
        if (nearest == null) nearest = firstCalendarItem(calendar, "nearest");

        String primary = CompanionWindowState.whisper(this).optString("content", "把今天，轻轻收进窗里。");

        String secondary;
        if (battery >= 0 && battery <= 40 && !charging) secondary = "找个顺手的时刻，让手机慢慢充上电。";
        else if (screenMinutes >= 480) secondary = "让眼睛离开屏幕半分钟，看看远一点。";
        else {
            String app = s.optString("current_app", "").trim();
            secondary = app.isEmpty() ? "窗外安安静静，状态都在轻轻更新。" : "此刻在 " + app + "，掌心窗替你看着今天。";
        }

        if (overviewAdviceText != null) overviewAdviceText.setText(formatHeroMessage(primary));
        if (overviewSecondaryText != null) overviewSecondaryText.setText(secondary);
        if (overviewMetaText != null) overviewMetaText.setText(weatherBrief(s) + "   ·   " + calendarBrief(nearest));
        if (todayNextTitle != null) todayNextTitle.setText("下一件事");
        if (todayNextDetail != null) todayNextDetail.setText(nearest == null ? "晚间无安排" : nearest.optString("title", "临近日子"));
    }

    private JSONObject firstCalendarItem(JSONObject calendar, String key) {
        if (calendar == null) return null;
        org.json.JSONArray items = calendar.optJSONArray(key);
        return items == null || items.length() == 0 ? null : items.optJSONObject(0);
    }

    private JSONObject nearestCalendarEvent(JSONObject state) {
        JSONObject calendar = state == null ? null : state.optJSONObject("calendar_state");
        JSONObject nearest = firstCalendarItem(calendar, "nearest");
        if (nearest == null) nearest = firstCalendarItem(calendar, "active_banners");
        if (nearest != null) return nearest;
        try {
            JSONArray upcoming = CalendarState.upcomingOccurrences(this, 1);
            if (upcoming != null && upcoming.length() > 0) return upcoming.optJSONObject(0);
        } catch (Exception ignored) { }
        return null;
    }

    private void updateCompanionAnniversary(JSONObject event) {
        if (companionAnniversaryText == null) return;
        if (event == null) event = nearestCalendarEvent(null);
        companionAnniversaryText.setText(formatCompanionAnniversary(event));
    }

    private String formatCompanionAnniversary(JSONObject event) {
        if (event == null) return "暂无临近日子";
        String title = event.optString("title", "临近日子").trim();
        String days = event.optString("days_text", "").trim();
        if (title.isEmpty()) title = "临近日子";
        if (days.isEmpty()) return title;
        return title + "\n" + days;
    }


    private String formatHeroMessage(String text) {
        if (text == null || text.trim().isEmpty()) return "把今天，\n轻轻收进窗里。";
        String clean = text.trim();
        if (clean.contains("\n") || clean.length() <= 11) return clean;
        int comma = clean.indexOf('，');
        if (comma >= 2 && comma < 10) return clean.substring(0, comma + 1) + "\n" + clean.substring(comma + 1);
        return clean;
    }

    private String calendarBrief(JSONObject event) {
        if (event == null) return "日历 · 暂无临近日子";
        return "日历 · " + event.optString("title", "重要日子") + " " + event.optString("days_text", "");
    }

    private String weatherBrief(JSONObject s) {
        JSONObject location = s.optJSONObject("current_weather_location");
        if (location == null) return "天气 · 未设地区";
        String name = location.optString("name", "当前地区");
        String city = location.optString("city", "");
        JSONObject live = WeatherLive.cached(this, city);
        if (live != null && live.optBoolean("ok")) return name + " · " + live.optInt("temperature", 0) + "℃ " + live.optString("condition", "");
        return name + " · " + (city.isEmpty() ? "待设置" : city);
    }

    private String lifeSummary() {
        try {
            JSONObject s = LifeState.collect(this);
            int battery = s.optInt("battery_percent", -1);
            String b = battery >= 0 ? (battery + "%" + (s.optBoolean("charging", false) ? " · 充电中" : " · 未充电")) : "-";
            JSONObject w = s.optJSONObject("current_weather_location");
            String loc = "未设地区";
            if (w != null) { loc = w.optString("name", "当前地区"); String city = w.optString("city", ""); if (city.length() > 0) loc += " · " + city; }
            return "时间：" + s.optString("local_time", "-") + "\n电量：" + b + "\n网络：" + s.optString("network_type", "-") + "\n当前地区：" + loc;
        } catch (Exception e) { return "生活细节加载中…"; }
    }

    private String makeAdvice(JSONObject s) {
        StringBuilder sb = new StringBuilder();
        int battery = s.optInt("battery_percent", -1); boolean charging = s.optBoolean("charging", false);
        if (battery >= 0 && battery <= 40 && !charging) sb.append("电量 ").append(battery).append("%，该充电了。\n");
        int mins = s.optInt("screen_time_today_minutes", 0);
        if (mins >= 480) sb.append("屏幕时间有点长，眼睛歇半分钟。\n");
        JSONObject cal = s.optJSONObject("calendar_state");
        if (cal != null) {
            org.json.JSONArray banners = cal.optJSONArray("active_banners");
            if (banners != null && banners.length() > 0) sb.append(banners.optJSONObject(0).optString("banner_text", "")).append("\n");
            else {
                org.json.JSONArray nearest = cal.optJSONArray("nearest");
                if (nearest != null && nearest.length() > 0) {
                    JSONObject n = nearest.optJSONObject(0);
                    if (n != null && n.optInt("days_left", 999) <= 14) sb.append(n.optString("title", "重要日子")).append(" ").append(n.optString("days_text", "")).append("。\n");
                }
            }
        }
        JSONObject w = s.optJSONObject("current_weather_location");
        if (w != null) {
            String city = w.optString("city", "");
            JSONObject live = WeatherLive.cached(this, city);
            if (live != null && live.optBoolean("ok")) sb.append(WeatherLive.advice(live, w.optString("name", "当前地区"))).append("\n");
            else sb.append(WeatherState.localAdvice(w.optString("note", ""))).append("\n");
        }
        if (sb.length() == 0) sb.append("状态还好，").append(AppPrefs.companionName(this)).append("继续陪着你。");
        return sb.toString().trim();
    }
    private String formatMinutes(int minutes) { if (minutes < 60) return minutes + " 分钟"; return (minutes / 60) + "h " + (minutes % 60) + "m"; }
}
