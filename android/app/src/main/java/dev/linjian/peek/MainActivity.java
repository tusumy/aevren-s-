Warning: truncated output (original token count: 55117)
Total output lines: 2894

package dev.linjian.peek;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.Context;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Toast;

import org.json.JSONObject;
import org.json.JSONArray;

import java.util.Calendar;
import java.util.Locale;
import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MainActivity extends Activity {
    private static final String PREF_A11Y_SETTINGS_OPENED_AT = "a11y_settings_opened_at";
    private static final long A11Y_CONFIRM_WINDOW_MS = 30000L;
    private static final String DEFAULT_UPDATE_URL = "https://raw.githubusercontent.com/linzhi-524/linjian-peek-public/main/update.json";
    private int latestVersionCode = AppPrefs.APP_VERSION_CODE;
    private String latestVersionName = AppPrefs.APP_VERSION_NAME;
    private String latestApkUrl = "";
    private String latestChangelog = "";
    private TextView brandText, headerTitle, headerSubtitle, statusText, debugText, lifeStatusText, lifeSummaryText, knownAppsText, homeModeStatusText, gateStatusText, nowStateText, nowStatePermissionText;
    private TextView heroLabelText, overviewAdviceText, overviewSecondaryText, overviewMetaText, overviewBatteryText, overviewBatteryDetail, overviewAppText, overviewAppDetail, overviewScreenText, overviewScreenDetail, overviewWeatherText, overviewWeatherDetail, weatherLocationsText, themeText, calendarSummaryText, calendarDetailText;
    private TextView overviewBatteryLabel, overviewAppLabel, overviewScreenLabel, overviewWeatherLabel, quickSeeTitle, quickSeeDetail, quickSeeArrow, quickGuardTitle, quickGuardDetail, quickGuardArrow;
    private ImageView quickSeeIcon, quickGuardIcon;
    private TextView guidianSummaryText, guidianDetailText, guidianSettingsStatusText, guidianAvatarText, versionStatusText, updateChangelogText, licenseSummaryText;
    private Button toggleButton, accessibilityButton, usageAccessButton, testButton, openXhsButton, openTargetAppButton, homeButton, backButton, recentsButton, alarmTestButton, notifyTestButton, refreshLifeButton;
    private Button addPackageButton, testPackageButton, sequenceTestButton, refreshGateButton, addGateAppButton, addWeatherLocationButton, setCurrentWeatherButton;
    private Button testGuidianButton, saveGuidianSettingsButton, chooseGuidianAvatarButton, guidianThemeDuskButton, guidianThemeCloudButton, guidianThemeBerryButton;
    private Button themeCreamButton, themeBlueButton, themePeachButton, themeNightButton, themeMintButton, themePurpleButton, drawerThemeButton, drawerNowStateButton, locationPermissionButton, overlayPermissionButton, notificationListenerButton;
    private Button drawerConnectionButton, drawerPermissionButton, drawerControlTestButton, drawerKnownAppsButton, drawerHomeModeButton, drawerGateAddButton, drawerReminderButton, drawerCycleButton, drawerDebugButton, drawerLifeDetailsButton, drawerAppGateButton, drawerWeatherButton, drawerVersionButton, checkUpdateButton, downloadUpdateButton;
    private Button drawerGuidianButton, drawerGuidianSettingsButton, drawerCalendarButton, saveCalendarEventButton;
    private CheckBox remindersEnabled, batteryRuleEnabled, screenRuleEnabled, waterRuleEnabled, restRuleEnabled, cycleEnabled, foregroundPopupEnabled, homeModeEnabled, homeModeForceEnabled, appGateEnabled;
    private CheckBox guidianEnabled, guidianRemoteEnabled, guidianFullscreenEnabled, guidianQuietEnabled, calendarLunarEnabled, calendarRepeatEnabled, calendarBannerEnabled;
    private Button tabSettings, tabSee, tabControl, tabLife, tabGate, tabDebug;
    private View quickSeeButton, quickGuardButton;
    private View sectionSettings, sectionSee, sectionControl, sectionLife, sectionGate, sectionDebug;
    private View heroCard, bottomNav, topHeader;
    private View drawerTheme, drawerNowState, drawerConnection, drawerPermission, drawerControlTest, drawerKnownApps, drawerHomeMode, drawerGateAdd, drawerReminder, drawerCycle, drawerDebug, drawerAppGate, drawerWeather, drawerVersion;
    private View drawerGuidian, drawerGuidianSettings, drawerCalendar;
    private EditText serverUrl, tokenInput, deviceInput, intervalInput, cityInput, weatherInput, userNameInput, companionNameInput;
    private EditText weatherAliasInput, weatherCityInput, weatherNoteInput, calendarTitleInput, calendarDateInput, calendarGroupInput, calendarNoteInput;
    private EditText batteryThresholdInput, screenThresholdInput, waterIntervalInput, restIntervalInput;
    private EditText lastPeriodStartInput, cycleLengthInput, periodLengthInput, cycleRemindBeforeInput;
    private EditText appAliasInput, appPackageInput, targetAppsInput, homeThresholdInput, homeCooldownInput, homeTargetInput, gateAliasInput, gatePackageInput;
    private EditText guidianIntervalInput, guidianCooldownInput, guidianDailyMaxInput, guidianQuietStartInput, guidianQuietEndInput, guidianTargetPackageInput, guidianPromptInput, guidianReasonInput;
    private boolean serviceRunning = false;
    private String currentTab = "life";
    private boolean weatherFetching = false;
    private long lastWeatherFetchAt = 0L;
    private static final int REQ_GUIDIAN_AVATAR = 230723;
    private static final int REQ_DIARY_COVER = 230724;
    private static final int REQ_DIARY_EXPORT = 230725;
    private static final int REQ_DIARY_IMPORT = 230726;
    private static boolean openingShownForProcess = false;
    private SoftAvatarView companionAvatarView;
    private ImageView companionRestArt;
    private TextView companionPresenceText, sharedWhisperText, sharedWhisperMetaText, companionActionsPreview, todayJourneyText, guardOverviewText;
    private TextView todayNextTitle, todayNextDetail, companionDaysText, companionSinceText, companionAnniversaryText, guardDeviceStatusText, guardRecordText;
    private TextView calendarHeroTitle, calendarHeroDetail, calendarMonthTitle, calendarSelectedTitle, calendarSelectedDetail;
    private LinearLayout calendarGrid, calendarSelectedEventsContainer;
    private Calendar calendarVisibleMonth = Calendar.getInstance();
    private int calendarSelectedDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
    private boolean guardianCalendarDetailOpen = false;
    private boolean diaryPageOpen = false, diaryContentOpen = false;
    private String diaryBookId = "", diarySelectedDate = "", diaryCurrentEntryId = "";
    private View diaryExpandedPaperView;
    private TextView diaryExpandedContentView, diaryExpandedHintView;
    private FrameLayout diaryDateDrawerOverlay;
    private LinearLayout diaryDateDrawerPanel;
    private GuardianRingView guardianRing;
    private long lastCompanionSyncAt = 0L;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private final Runnable refreshTick = new Runnable() {
        @Override public void run() { serviceRunning = CompanionService.isRunning(); updateUI(); uiHandler.postDelayed(this, 1500); }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        buildMagazinePages();
        loadSettings();
        NowState.start(this);

        DebugState.append(this, "掌心窗公开版 v0.3.8.4 已打开");
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 13);
        serviceRunning = CompanionService.isRunning();
        updateUI();

        if (accessibilityButton != null) accessibilityButton.setOnClickListener(v -> { if (recentlyOpenedAccessibilitySettings() && !isAccessibilityServiceEnabled()) showAccessibilityHelpDialog(); else openAccessibilitySettings(); });
        if (usageAccessButton != null) usageAccessButton.setOnClickListener(v -> openUsageAccessSettings());
        if (locationPermissionButton != null) locationPermissionButton.setOnClickListener(v -> requestLocationPermission());
        if (overlayPermissionButton != null) overlayPermissionButton.setOnClickListener(v -> openOverlayPermissionSettings());
        if (notificationListenerButton != null) notificationListenerButton.setOnClickListener(v -> openNotificationListenerSettings());
        if (toggleButton != null) toggleButton.setOnClickListener(v -> { if (serviceRunning) stopCompanionService(); else startCompanionService(); });
        if (refreshLifeButton != null) refreshLifeButton.setOnClickListener(v -> { saveSettings(); updateUI(); Toast.makeText(this, "已刷新生活总览", Toast.LENGTH_SHORT).show(); });
        if (testButton != null) testButton.setOnClickListener(v -> testScreenshot());
        if (openXhsButton != null) openXhsButton.setOnClickListener(v -> openPackage(AppPrefs.packageForApp(this, "小红书")));
        if (openTargetAppButton != null) openTargetAppButton.setOnClickListener(v -> openPackage(AppPrefs.homeTargetPackage(this)));
        if (homeButton != null) homeButton.setOnClickListener(v -> { ScreenshotService svc = ScreenshotService.getInstance(); toast(svc != null && svc.doHome()); });
        if (backButton != null) backButton.setOnClickListener(v -> { ScreenshotService svc = ScreenshotService.getInstance(); toast(svc != null && svc.doBack()); });
        if (recentsButton != null) recentsButton.setOnClickListener(v -> { ScreenshotService svc = ScreenshotService.getInstance(); toast(svc != null && svc.doRecents()); });
        if (alarmTestButton != null) alarmTestButton.setOnClickListener(v -> testAlarm());
        if (notifyTestButton != null) notifyTestButton.setOnClickListener(v -> testNotification());
        if (addPackageButton != null) addPackageButton.setOnClickListener(v -> addPackageAlias());
        if (testPackageButton != null) testPackageButton.setOnClickListener(v -> testCustomPackage());
        if (sequenceTestButton != null) sequenceTestButton.setOnClickListener(v -> testLocalSequence());
        if (refreshGateButton != null) refreshGateButton.setOnClickListener(v -> { updateUI(); Toast.makeText(this, "已刷新守护状态", Toast.LENGTH_SHORT).show(); });
        if (addGateAppButton != null) addGateAppButton.setOnClickListener(v -> addGateApp());
        if (addWeatherLocationButton != null) addWeatherLocationButton.setOnClickListener(v -> addWeatherLocation(false));
        if (setCurrentWeatherButton != null) setCurrentWeatherButton.setOnClickListener(v -> addWeatherLocation(true));
        if (checkUpdateButton != null) checkUpdateButton.setOnClickListener(v -> checkForUpdates(true));
        if (downloadUpdateButton != null) downloadUpdateButton.setOnClickListener(v -> downloadLatestApk());
        if (testGuidianButton != null) testGuidianButton.setOnClickListener(v -> { saveSettings(); GuidianState.showPrompt(this, true); CompanionWindowState.recordJourney(this, "回应归电", "回到" + AppPrefs.companionName(this) + "的窗边"); updateUI(); });
        if (saveCalendarEventButton != null) saveCalendarEventButton.setOnClickListener(v -> saveCalendarEvent());
        if (saveGuidianSettingsButton != null) saveGuidianSettingsButton.setOnClickListener(v -> { saveSettings(); Toast.makeText(this, "归电设置已保存", Toast.LENGTH_SHORT).show(); updateUI(); });
        if (chooseGuidianAvatarButton != null) chooseGuidianAvatarButton.setOnClickListener(v -> chooseGuidianAvatar());
        bindGuidianThemeButton(guidianThemeDuskButton, "粉色"); bindGuidianThemeButton(guidianThemeCloudButton, "白色"); bindGuidianThemeButton(guidianThemeBerryButton, "黑色");
        if (userNameInput != null) userNameInput.setOnFocusChangeListener((v, focused) -> { if (!focused) { saveSettings(); buildMagazinePages(); updateUI(); } });
        if (companionNameInput != null) companionNameInput.setOnFocusChangeListener((v, focused) -> { if (!focused) { saveSettings(); buildMagazinePages(); updateUI(); } });
        if (targetAppsInput != null) targetAppsInput.setOnFocusChangeListener((v, focused) -> { if (!focused) { saveSettings(); updateUI(); } });
        bindConnectionAutoSave();

        bindThemeButton(themeCreamButton, "奶油绿"); bindThemeButton(themeBlueButton, "雾蓝白"); bindThemeButton(themePeachButton, "白桃粉"); bindThemeButton(themeNightButton, "夜航黑"); bindThemeButton(themeMintButton, "薄荷透明"); bindThemeButton(themePurpleButton, "星云紫");

        bindDrawer(drawerLifeDetailsButton, lifeStatusText, "展开详情");
        bindDrawer(drawerThemeButton, drawerTheme, "主题");
        bindDrawer(drawerNowStateButton, drawerNowState, "此刻状态");
        bindDrawer(drawerAppGateButton, drawerAppGate, "应用门禁");
        bindDrawer(drawerWeatherButton, drawerWeather, "天气地区");
        bindDrawer(drawerConnectionButton, drawerConnection, "连接设置");
        bindDrawer(drawerPermissionButton, drawerPermission, "权限与运行");
        bindDrawer(drawerControlTestButton, drawerControlTest, "本机测试抽屉");
        bindDrawer(drawerKnownAppsButton, drawerKnownApps, "应用包名抽屉");
        bindDrawer(drawerHomeModeButton, drawerHomeMode, "回家模式抽屉");
        bindDrawer(drawerGateAddButton, drawerGateAdd, "添加可锁 App");
        bindDrawer(drawerReminderButton, drawerReminder, "主动提醒规则");
        bindDrawer(drawerCycleButton, drawerCycle, "生理期提醒");
        bindDrawer(drawerDebugButton, drawerDebug, "高级调试日志");
        bindDrawer(drawerCalendarButton, drawerCalendar, "守护日历");
        bindDrawer(drawerGuidianButton, drawerGuidian, "归电");
        bindDrawer(drawerGuidianSettingsButton, drawerGuidianSettings, "归电设置");
        bindDrawer(drawerVersionButton, drawerVersion, "版本、更新与许可");

        if (tabSettings != null) tabSettings.setOnClickListener(v -> showTab("settings"));
        if (tabSee != null) tabSee.setOnClickListener(v -> showTab("see"));
        if (tabControl != null) tabControl.setOnClickListener(v -> showTab("settings"));
        if (tabLife != null) tabLife.setOnClickListener(v -> showTab("life"));
        if (tabGate != null) tabGate.setOnClickListener(v -> showTab("gate"));
        if (tabDebug != null) tabDebug.setOnClickListener(v -> showTab("settings"));
        if (quickSeeButton != null) quickSeeButton.setOnClickListener(v -> showTab("see"));
        if (quickGuardButton != null) quickGuardButton.setOnClickListener(v -> showTab("gate"));
        CompanionWindowState.recordJourney(this, "打开掌心窗", "回到今天的窗边");
        showTab("life");
        applyBottomNavigationInsets();
        playOpeningWindowAnimation();
        checkForUpdates(false);
    }

    private void bindViews() {
        topHeader = findViewById(R.id.topHeader); brandText = findViewById(R.id.brandText); headerTitle = findViewById(R.id.headerTitle); headerSubtitle = findViewById(R.id.headerSubtitle); statusText = findViewById(R.id.statusText); debugText = findViewById(R.id.debugText); lifeStatusText = findViewById(R.id.lifeStatusText); lifeSummaryText = findViewById(R.id.lifeSummaryText); knownAppsText = findViewById(R.id.knownAppsText); homeModeStatusText = findViewById(R.id.homeModeStatusText); gateStatusText = findViewById(R.id.gateStatusText); nowStatePermissionText = findViewById(R.id.nowStatePermissionText);
        heroLabelText = findViewById(R.id.heroLabelText); overviewAdviceText = findViewById(R.id.overviewAdviceText); overviewSecondaryText = findViewById(R.id.overviewSecondaryText); overviewMetaText = findViewById(R.id.overviewMetaText); overviewBatteryText = findViewById(R.id.overviewBatteryText); overviewBatteryDetail = findViewById(R.id.overviewBatteryDetail); overviewAppText = findViewById(R.id.overviewAppText); overviewAppDetail = findViewById(R.id.overviewAppDetail); overviewScreenText = findViewById(R.id.overviewScreenText); overviewScreenDetail = findViewById(R.id.overviewScreenDetail); overviewWeatherText = findViewById(R.id.overviewWeatherText); overviewWeatherDetail = findViewById(R.id.overviewWeatherDetail); weatherLocationsText = findViewById(R.id.weatherLocationsText); themeText = findViewById(R.id.themeText); calendarSummaryText = findViewById(R.id.calendarSummaryText); calendarDetailText = findViewById(R.id.calendarDetailText);
        overviewBatteryLabel = findViewById(R.id.overviewBatteryLabel); overviewAppLabel = findViewById(R.id.overviewAppLabel); overviewScreenLabel = findViewById(R.id.overviewScreenLabel); overviewWeatherLabel = findViewById(R.id.overviewWeatherLabel); quickSeeTitle = findViewById(R.id.quickSeeTitle); quickSeeDetail = findViewById(R.id.quickSeeDetail); quickSeeArrow = findViewById(R.id.quickSeeArrow); quickGuardTitle = findViewById(R.id.quickGuardTitle); quickGuardDetail = findViewById(R.id.quickGuardDetail); quickGuardArrow = findViewById(R.id.quickGuardArrow); quickSeeIcon = findViewById(R.id.quickSeeIcon); quickGuardIcon = findViewById(R.id.quickGuardIcon);
        guidianSummaryText = findViewById(R.id.guidianSummaryText); guidianDetailText = findViewById(R.id.guidianDetailText); guidianSettingsStatusText = findViewById(R.id.guidianSettingsStatusText); guidianAvatarText = findViewById(R.id.guidianAvatarText); versionStatusText = findViewById(R.id.versionStatusText); updateChangelogText = findViewById(R.id.updateChangelogText); licenseSummaryText = findViewById(R.id.licenseSummaryText);
        toggleButton = findViewById(R.id.toggleButton); accessibilityButton = findViewById(R.id.accessibilityButton); usageAccessButton = findViewById(R.id.usageAccessButton); testButton = findViewById(R.id.testButton); openXhsButton = findViewById(R.id.openXhsButton); openTargetAppButton = findViewById(R.id.openTargetAppButton); homeButton = findViewById(R.id.homeButton); backButton = findViewById(R.id.backButton); recentsButton = findViewById(R.id.recentsButton); alarmTestButton = findViewById(R.id.alarmTestButton); notifyTestButton = findViewById(R.id.notifyTestButton); refreshLifeButton = findViewById(R.id.refreshLifeButton);
        addPackageButton = findViewById(R.id.addPackageButton); testPackageButton = findViewById(R.id.testPackageButton); sequenceTestButton = findViewById(R.id.sequenceTestButton); refreshGateButton = findViewById(R.id.refreshGateButton); addGateAppButton = findViewById(R.id.addGateAppButton); addWeatherLocationButton = findViewById(R.id.addWeatherLocationButton); setCurrentWeatherButton = findViewById(R.id.setCurrentWeatherButton);
        testGuidianButton = findViewById(R.id.testGuidianButton); saveGuidianSettingsButton = findViewById(R.id.saveGuidianSettingsButton); chooseGuidianAvatarButton = findViewById(R.id.chooseGuidianAvatarButton); guidianThemeDuskButton = findViewById(R.id.guidianThemeDuskButton); guidianThemeCloudButton = findViewById(R.id.guidianThemeCloudButton); guidianThemeBerryButton = findViewById(R.id.guidianThemeBerryButton);
        themeCreamButton = findViewById(R.id.themeCreamButton); themeBlueButton = findViewById(R.id.themeBlueButton); themePeachButton = findViewById(R.id.themePeachButton); themeNightButton = findViewById(R.id.themeNightButton); themeMintButton = findViewById(R.id.themeMintButton); themePurpleButton = findViewById(R.id.themePurpleButton); drawerThemeButton = findViewById(R.id.drawerThemeButton); drawerNowStateButton = findViewById(R.id.drawerNowStateButton); locationPermissionButton = findViewById(R.id.locationPermissionButton); overlayPermissionButton = findViewById(R.id.overlayPermissionButton); notificationListenerButton = findViewById(R.id.notificationListenerButton);
        drawerConnectionButton = findViewById(R.id.drawerConnectionButton); drawerPermissionButton = findViewById(R.id.drawerPermissionButton); drawerControlTestButton = findViewById(R.id.drawerControlTestButton); drawerKnownAppsButton = findViewById(R.id.drawerKnownAppsButton); drawerHomeModeButton = findViewById(R.id.drawerHomeModeButton); drawerGateAddButton = findViewById(R.id.drawerGateAddButton); drawerReminderButton = findViewById(R.id.drawerReminderButton); drawerCycleButton = findViewById(R.id.drawerCycleButton); drawerDebugButton = findViewById(R.id.drawerDebugButton); drawerLifeDetailsButton = findViewById(R.id.drawerLifeDetailsButton); drawerAppGateButton = findViewById(R.id.drawerAppGateButton); drawerWeatherButton = findViewById(R.id.drawerWeatherButton); drawerVersionButton = findViewById(R.id.drawerVersionButton); checkUpdateButton = findViewById(R.id.checkUpdateButton); downloadUpdateButton = findViewById(R.id.downloadUpdateButton);
        drawerGuidianButton = findViewById(R.id.drawerGuidianButton); drawerGuidianSettingsButton = findViewById(R.id.drawerGuidianSettingsButton); drawerCalendarButton = findViewById(R.id.drawerCalendarButton); saveCalendarEventButton = findViewById(R.id.saveCalendarEventButton);
        remindersEnabled = findViewById(R.id.remindersEnabled); batteryRuleEnabled = findViewById(R.id.batteryRuleEnabled); screenRuleEnabled = findViewById(R.id.screenRuleEnabled); waterRuleEnabled = findViewById(R.id.waterRuleEnabled); restRuleEnabled = findViewById(R.id.restRuleEnabled); cycleEnabled = findViewById(R.id.cycleEnabled); foregroundPopupEnabled = findViewById(R.id.foregroundPopupEnabled); homeModeEnabled = findViewById(R.id.homeModeEnabled); homeModeForceEnabled = findViewById(R.id.homeModeForceEnabled); appGateEnabled = findViewById(R.id.appGateEnabled);
        guidianEnabled = findViewById(R.id.guidianEnabled); guidianRemoteEnabled = findViewById(R.id.guidianRemoteEnabled); guidianFullscreenEnabled = findViewById(R.id.guidianFullscreenEnabled); guidianQuietEnabled = findViewById(R.id.guidianQuietEnabled); calendarLunarEnabled = findViewById(R.id.calendarLunarEnabled); calendarRepeatEnabled = findViewById(R.id.calendarRepeatEnabled); calendarBannerEnabled = findViewById(R.id.calendarBannerEnabled);
        tabSettings = findViewById(R.id.tabSettings); tabSee = findViewById(R.id.tabSee); tabControl = findViewById(R.id.tabControl); tabLife = findViewById(R.id.tabLife); tabGate = findViewById(R.id.tabGate); tabDebug = findViewById(R.id.tabDebug); quickSeeButton = findViewById(R.id.quickSeeButton); quickGuardButton = findViewById(R.id.quickGuardButton);
        sectionSettings = findViewById(R.id.sectionSettings); sectionSee = findViewById(R.id.sectionSee); sectionControl = findViewById(R.id.sectionControl); sectionLife = findViewById(R.id.sectionLife); sectionGate = findViewById(R.id.sectionGate); sectionDebug = findViewById(R.id.sectionDebug);
        heroCard = findViewById(R.id.heroCard); bottomNav = findViewById(R.id.bottomNav);
        drawerTheme = findViewById(R.id.drawerTheme); drawerNowState = findViewById(R.id.drawerNowState); drawerConnection = findViewById(R.id.drawerConnection); drawerPermission = findViewById(R.id.drawerPermission); drawerControlTest = findViewById(R.id.drawerControlTest); drawerKnownApps = findViewById(R.id.drawerKnownApps); drawerHomeMode = findViewById(R.id.drawerHomeMode); drawerGateAdd = findViewById(R.id.drawerGateAdd); drawerReminder = findViewById(R.id.drawerReminder); drawerCycle = findViewById(R.id.drawerCycle); drawerDebug = findViewById(R.id.drawerDebug); drawerAppGate = findViewById(R.id.drawerAppGate); drawerWeather = findViewById(R.id.drawerWeather); drawerVersion = findViewById(R.id.drawerVersion);
        drawerGuidian = findViewById(R.id.drawerGuidian); drawerGuidianSettings = findViewById(R.id.drawerGuidianSettings); drawerCalendar = findViewById(R.id.drawerCalendar);
        serverUrl = findViewById(R.id.serverUrl); tokenInput = findViewById(R.id.tokenInput); deviceInput = findViewById(R.id.deviceInput); intervalInput = findViewById(R.id.intervalInput); cityInput = findViewById(R.id.cityInput); weatherInput = findViewById(R.id.weatherInput); userNameInput = findViewById(R.id.userNameInput); companionNameInput = findViewById(R.id.companionNameInput);
        weatherAliasInput = findViewById(R.id.weatherAliasInput); weatherCityInput = findViewById(R.id.weatherCityInput); weatherNoteInput = findViewById(R.id.weatherNoteInput); calendarTitleInput = findViewById(R.id.calendarTitleInput); calendarDateInput = findViewById(R.id.calendarDateInput); calendarGroupInput = findViewById(R.id.calendarGroupInput); calendarNoteInput = findViewById(R.id.calendarNoteInput);
        batteryThresholdInput = findViewById(R.id.batteryThresholdInput); screenThresholdInput = findViewById(R.id.screenThresholdInput); waterIntervalInput = findViewById(R.id.waterIntervalInput); restIntervalInput = findViewById(R.id.restIntervalInput);
        lastPeriodStartInput = findViewById(R.id.lastPeriodStartInput); cycleLengthInput = findViewById(R.id.cycleLengthInput); periodLengthInput = findViewById(R.id.periodLengthInput); cycleRemindBeforeInput = findViewById(R.id.cycleRemindBeforeInput);
        appAliasInput = findViewById(R.id.appAliasInput); appPackageInput = findViewById(R.id.appPackageInput); targetAppsInput = findViewById(R.id.targetAppsInput); homeThresholdInput = findViewById(R.id.homeThresholdInput); homeCooldownInput = findViewById(R.id.homeCooldownInput); homeTargetInput = findViewById(R.id.homeTargetInput); gateAliasInput = findViewById(R.id.gateAliasInput); gatePackageInput = findViewById(R.id.gatePackageInput);
        guidianIntervalInput = findViewById(R.id.guidianIntervalInput); guidianCooldownInput = findViewById(R.id.guidianCooldownInput); guidianDailyMaxInput = findViewById(R.id.guidianDailyMaxInput); guidianQuietStartInput = findViewById(R.id.guidianQuietStartInput); guidianQuietEndInput = findViewById(R.id.guidianQuietEndInput); guidianTargetPackageInput = findViewById(R.id.guidianTargetPackageInput); guidianPromptInput = findViewById(R.id.guidianPromptInput); guidianReasonInput = findViewById(R.id.guidianReasonInput);
    }

    private void buildMagazinePages() {
        View today = buildTodayMagazine();
        View companion = buildCompanionMagazine();
        View guard = buildGuardMagazine();
        replaceScrollContent(sectionLife, today);
        replaceScrollContent(sectionSee, companion);
        replaceScrollContent(sectionGate, guard);
        if (sectionSee != null) sectionSee.setVisibility(View.GONE);
        if (sectionGate != null) sectionGate.setVisibility(View.GONE);
        if (saveCalendarEventButton != null) saveCalendarEventButton.setOnClickListener(v -> saveCalendarEvent());
        LinearLayout settings = scrollColumn(sectionSettings);
        if (settings != null) {
            for (int i = settings.getChildCount() - 1; i >= 0; i--) {
                Object tag = settings.getChildAt(i).getTag();
                if ("dynamic_privacy".equals(tag) || "dynamic_diary_backup".equals(tag) || "dynamic_desk_pet".equals(tag)) settings.removeViewAt(i);
            }
            settings.setPadding(0, 0, 0, dp(42));
            Button deskPetButton = actionButton("桌面宠物  ›", false);
            deskPetButton.setTag("dynamic_desk_pet");
            LinearLayout deskPet = cardColumn();
            deskPet.setTag("dynamic_desk_pet");
            deskPet.setVisibility(View.GONE);
            deskPet.addView(title("玄砚桌宠", 15));
            deskPet.addView(body("让玄砚常驻手机桌面：会沿屏幕边缘散步、发呆，支持点击和拖动。", 9), matchWrapTop(6));
            CheckBox deskPetToggle = new CheckBox(this);
            deskPetToggle.setText("显示在手机桌面");
            deskPetToggle.setTextSize(11);
            deskPetToggle.setChecked(AppPrefs.get(this).getBoolean(AppPrefs.KEY_DESK_PET_ENABLED, false));
            deskPetToggle.setOnCheckedChangeListener((button, checked) -> setDeskPetEnabled(checked, deskPetToggle));
            deskPet.addView(deskPetToggle, matchWrapTop(8));
            Button deskPetPermission = actionButton((Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)) ? "悬浮窗权限：已开启" : "打开悬浮窗权限", false);
            deskPetPermission.setOnClickListener(v -> openOverlayPermissionSettings());
            deskPet.addView(deskPetPermission, matchWrapTop(6));
            Button deskPetStop = actionButton("把玄砚收回掌心窗", false);
            deskPetStop.setOnClickListener(v -> { deskPetToggle.setChecked(false); stopService(new Intent(this, DeskPetService.class)); });
            deskPet.addView(deskPetStop, matchWrapTop…40117 tokens truncated…开启，到“应用信息 → 掌心窗 → 右上角菜单”允许受限设置，然后再回无障碍开启“掌心窗服务”。")
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
