from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def source(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def test_guidian_has_a_real_default_target():
    prefs = source("android/app/src/main/java/dev/linjian/peek/AppPrefs.java")
    assert 'DEFAULT_HOME_TARGET_PACKAGE = "com.openai.chatgpt"' in prefs
    assert 'apps.put("ChatGPT", "com.openai.chatgpt")' in prefs


def test_all_notifications_use_custom_launcher_art():
    companion = source("android/app/src/main/java/dev/linjian/peek/CompanionService.java")
    desk_pet = source("android/app/src/main/java/dev/linjian/peek/DeskPetService.java")
    guidian = source("android/app/src/main/java/dev/linjian/peek/GuidianState.java")
    assert companion.count(".setLargeIcon(largeIcon)") == 2
    assert desk_pet.count(".setLargeIcon(largeIcon)") == 1
    assert guidian.count(".setLargeIcon(largeIcon)") == 1


if __name__ == "__main__":
    test_guidian_has_a_real_default_target()
    test_all_notifications_use_custom_launcher_art()
