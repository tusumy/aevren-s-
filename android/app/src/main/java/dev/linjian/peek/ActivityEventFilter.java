package dev.linjian.peek;

final class ActivityEventFilter {
    enum Category { PHONE, COMPANION }

    private ActivityEventFilter() { }

    static boolean matches(String source, String type, String localDate, String today, boolean todayOnly, Category category) {
        if (todayOnly && !today.equals(localDate)) return false;
        if (category == Category.PHONE) {
            return "phone".equals(source) || "app_open".equals(type) || "guidian_return".equals(type) || "screen_break_trigger".equals(type);
        }
        return "linche".equals(source) || "companion".equals(source) || "assistant".equals(source)
                || "command".equals(type) || "notification".equals(type) || "weather".equals(type)
                || "calendar".equals(type) || "status_check".equals(type);
    }
}
