"""Keep single screenshot and 12-second capture as two distinct controls in 看见."""
from pathlib import Path
root = Path(__file__).resolve().parents[2]
layout = (root / "android/app/src/main/res/layout/activity_main.xml").read_text(encoding="utf-8")
activity = (root / "android/app/src/main/java/dev/linjian/peek/MainActivity.java").read_text(encoding="utf-8")
assert 'android:id="@+id/testButton"' in layout
assert 'android:text="只截当前这一张"' in layout
assert 'android:id="@+id/watchFromSeeButton"' in layout
assert 'android:text="一起看 · 去 X/其他应用录12秒"' in layout
assert 'findViewById(R.id.watchFromSeeButton).setOnClickListener' in activity
assert 'startActivity(new Intent(this, TogetherWatchActivity.class))' in activity
assert 'testButton.setOnClickListener(v -> testScreenshot())' in activity
print("Separate screenshot/watch entry regression passed")
