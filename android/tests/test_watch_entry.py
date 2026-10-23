"""The real, dynamic 看见 card must open a choice, not upload instantly."""
from pathlib import Path
root = Path(__file__).resolve().parents[2]
layout = (root / "android/app/src/main/res/layout/activity_main.xml").read_text(encoding="utf-8")
activity = (root / "android/app/src/main/java/dev/linjian/peek/MainActivity.java").read_text(encoding="utf-8")
assert 'android:id="@+id/testButton"' in layout
assert 'android:text="只截当前这一张"' in layout
assert 'this::showSeeChooser' in activity
assert 'this::testScreenshot), marginBottom(10)' not in activity
chooser = activity.split("private void showSeeChooser()", 1)[1].split("private void showCompanionHomePage()", 1)[0]
assert "一起看 · 去 X 或其他应用录12秒" in chooser
assert "只截当前屏幕这一张" in chooser
assert "startActivity(new Intent(this, TogetherWatchActivity.class))" in chooser
assert "testScreenshot();" in chooser
assert 'watchFromSeeButton' not in activity
print("Dynamic See choice and distinct capture actions passed")
