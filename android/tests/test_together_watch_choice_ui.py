"""Do not silently hide Together Watch choices behind an AlertDialog message."""
from pathlib import Path

root = Path(__file__).resolve().parents[2]
watch = (root / "android/app/src/main/java/dev/linjian/peek/TogetherWatchActivity.java").read_text(encoding="utf-8")
companion = (root / "android/app/src/main/java/dev/linjian/peek/MainActivity.java").read_text(encoding="utf-8")
first_dialog = watch.split("new AlertDialog.Builder(this)", 1)[1].split(".show();", 1)[0]
assert ".setItems(new String[]{\"去 X 看视频\", \"去其他应用\"}" in first_dialog
assert ".setMessage(" not in first_dialog, "AlertDialog.setMessage overrides the list; choices would be hidden"
assert "this::showSeeChooser" in companion
assert "startActivity(new Intent(this, TogetherWatchActivity.class))" in companion
print("Together Watch choice dialog + dynamic button regression passed")
