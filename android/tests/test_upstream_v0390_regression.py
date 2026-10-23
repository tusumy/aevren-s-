"""Selective upstream-sync guardrails: preserve the pet while enabling v0.3.9.0 fixes."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
def source(path):
    return (ROOT / path).read_text(encoding="utf-8")

def test_selective_sync():
    pet = source("android/app/src/main/java/dev/linjian/peek/DeskPetService.java")
    view = source("android/app/src/main/java/dev/linjian/peek/DeskPetView.java")
    mcp = source("mcp/server.js")
    cf = source("server/cloudflare-worker/worker.js")
    server = source("server/linjian_server.py")
    wallet = source("android/app/src/main/java/dev/linjian/peek/WalletActivity.java")
    companion = source("android/app/src/main/java/dev/linjian/peek/CompanionService.java")
    assert "dockToSide(" in pet and "undockPet(" in pet
    assert "DOCK_PEEK_WIDTH_DP = 60" in pet and "DOCK_PEEK_HEIGHT_DP = 92" in pet
    assert "params.width = dp(DOCK_PEEK_WIDTH_DP)" in pet
    assert "params.height = dp(DOCK_PEEK_HEIGHT_DP)" in pet
    assert "params.x = side == EDGE_LEFT ? 0 : screenW - params.width" in pet
    assert "private static final class DockPeekView" in pet
    assert "visibleBounds(awake)" in pet and "cropHalf(frame)" in pet
    assert "float scale = Math.min(normalW / frame.getWidth(), normalH / frame.getHeight())" in pet
    assert "dockPreview.sleepNow();" in pet
    assert "dockPreview.scheduleSleep();" not in pet
    assert "draggedIntoSide" in pet and "outwardSwipe" in pet
    assert "drawSleepMarks(" in view
    assert "sanitizeMcpRequestBody(req.body)" in mcp
    assert "contentResult(" in mcp and "walletResult(" in mcp
    assert "peekScreenMcp(" in cf and "latestScreenResult(" in cf
    assert "diary_annotation_whitelist_fix" in server
    assert "notifyWalletUpdated(ctx)" in companion
    assert "walletUpdatedReceiver" in wallet
    manifest = source("android/app/src/main/AndroidManifest.xml")
    prefs = source("android/app/src/main/java/dev/linjian/peek/AppPrefs.java")
    manifest_code = re.search(r'android:versionCode="([0-9]+)"', manifest)
    app_code = re.search(r'APP_VERSION_CODE = ([0-9]+)', prefs)
    assert manifest_code and app_code
    assert int(manifest_code.group(1)) == int(app_code.group(1)) >= 30904

if __name__ == "__main__":
    test_selective_sync()
    print("selective upstream sync regression checks passed")
