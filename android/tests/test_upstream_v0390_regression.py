"""Selective upstream-sync guardrails: preserve the pet while enabling v0.3.9.0 fixes."""
from pathlib import Path

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
    assert "drawSleepMarks(" in view
    assert "sanitizeMcpRequestBody(req.body)" in mcp
    assert "contentResult(" in mcp and "walletResult(" in mcp
    assert "peekScreenMcp(" in cf and "latestScreenResult(" in cf
    assert "diary_annotation_whitelist_fix" in server
    assert "notifyWalletUpdated(ctx)" in companion
    assert "walletUpdatedReceiver" in wallet
    assert 'android:versionCode="30901"' in source("android/app/src/main/AndroidManifest.xml")
    assert 'APP_VERSION_CODE = 30901' in source("android/app/src/main/java/dev/linjian/peek/AppPrefs.java")

if __name__ == "__main__":
    test_selective_sync()
    print("selective upstream sync regression checks passed")
