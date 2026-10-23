"""Guard against regressions that accidentally capture the 砚团 home page."""
from pathlib import Path

root = Path(__file__).resolve().parents[2]
activity = (root / "android/app/src/main/java/dev/linjian/peek/TogetherWatchActivity.java").read_text(encoding="utf-8")
service = (root / "android/app/src/main/java/dev/linjian/peek/TogetherWatchService.java").read_text(encoding="utf-8")
accessibility = (root / "android/app/src/main/java/dev/linjian/peek/ScreenshotService.java").read_text(encoding="utf-8")

assert "先选画面" in activity
assert "去 X 看视频" in activity and "去其他应用" in activity
assert "EXTRA_TARGET_X" in activity
assert "startActivity(target)" in activity
assert "waitForTarget(wantAudio)" in service
assert "permittedForeground(foreground)" in service
assert "targetVisibleSince" in service
assert "visibleForegroundPackage()" in service and "visibleForegroundPackage()" in accessibility
assert "pkg.equals(getPackageName())" in service
assert "pkg.startsWith(\"com.android.systemui\")" in service
assert 'capturedPackage.equals(foregroundPackage())' in service
assert 'waitForTarget(wantAudio), 1200L' in service
assert "worker.postDelayed(() -> startFrames(wantAudio), 3000L)" not in service
print("Together Watch target flow checks passed")
