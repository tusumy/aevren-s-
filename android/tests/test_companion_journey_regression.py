from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def source(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def test_companion_journey_uses_companion_events_from_today():
    store = source("android/app/src/main/java/dev/linjian/peek/ActivityEventStore.java")
    window = source("android/app/src/main/java/dev/linjian/peek/CompanionWindowState.java")
    main = source("android/app/src/main/java/dev/linjian/peek/MainActivity.java")

    assert "public static JSONArray todayCompanionJourney(Context ctx, int limit)" in store
    assert "ActivityEventStore.todayCompanionJourney(ctx, 500)" in window
    assert 'KEY_JOURNEY = "today_journey_v1"' not in window
    assert 'KEY_JOURNEY_DAY = "today_journey_day_v1"' not in window

    journey_method = window.split("public static JSONArray journey(Context ctx)", 1)[1].split(
        "private static String eventType", 1
    )[0]
    assert "getString(KEY_JOURNEY" not in journey_method
    assert "ActivityEventStore.todayJourney(this, 500)" in main
    assert "renderCompanionState(state);\n                updateJourney(state);" in main


if __name__ == "__main__":
    test_companion_journey_uses_companion_events_from_today()
