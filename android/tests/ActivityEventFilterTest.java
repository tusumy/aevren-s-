package dev.linjian.peek;

public final class ActivityEventFilterTest {
    public static void main(String[] args) {
        String today = "2026-10-02";

        expect(true, ActivityEventFilter.matches("phone", "app_open", today, today, true, ActivityEventFilter.Category.PHONE));
        expect(false, ActivityEventFilter.matches("phone", "app_open", today, today, true, ActivityEventFilter.Category.COMPANION));
        expect(true, ActivityEventFilter.matches("linche", "status_check", today, today, true, ActivityEventFilter.Category.COMPANION));
        expect(true, ActivityEventFilter.matches("linche", "activity", today, today, true, ActivityEventFilter.Category.COMPANION));
        expect(false, ActivityEventFilter.matches("linche", "activity", "2026-10-01", today, true, ActivityEventFilter.Category.COMPANION));
        expect(true, ActivityEventFilter.matches("linche", "activity", "2026-10-01", today, false, ActivityEventFilter.Category.COMPANION));
        expect(false, ActivityEventFilter.matches("phone", "phone_activity", "", "", false, ActivityEventFilter.Category.COMPANION));
        expect(false, ActivityEventFilter.matches("phone", "app_open", "", "", false, ActivityEventFilter.Category.COMPANION));
        expect(true, ActivityEventFilter.matches("companion", "activity", "", "", false, ActivityEventFilter.Category.COMPANION));
        expect(true, ActivityEventFilter.matches("", "command", "", "", false, ActivityEventFilter.Category.COMPANION));
    }

    private static void expect(boolean expected, boolean actual) {
        if (expected != actual) throw new AssertionError("expected " + expected + " but was " + actual);
    }
}
