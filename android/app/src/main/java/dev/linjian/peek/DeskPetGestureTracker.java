package dev.linjian.peek;

/** Turns raw drag samples into a small set of deliberate desk-pet gestures. */
final class DeskPetGestureTracker {
    enum Outcome { NONE, GENTLE, SPIN, SHAKE, HIT_LEFT, HIT_RIGHT, HIT_TOP, HIT_BOTTOM }

    private final float density;
    private float lastX, lastY, lastDx, lastDy;
    private float pathLength, accumulatedTurn, velocityX, velocityY;
    private long startedAt, lastAt;
    private int horizontalDirection, reversals;

    DeskPetGestureTracker(float density) {
        this.density = Math.max(1f, density);
    }

    void begin(float x, float y, long at) {
        lastX = x;
        lastY = y;
        lastDx = lastDy = 0f;
        pathLength = accumulatedTurn = velocityX = velocityY = 0f;
        horizontalDirection = reversals = 0;
        startedAt = lastAt = at;
    }

    void move(float x, float y, long at) {
        float dx = x - lastX;
        float dy = y - lastY;
        float distance = (float) Math.hypot(dx, dy);
        long elapsed = Math.max(1L, at - lastAt);
        if (distance >= density * 2f) {
            if (Math.hypot(lastDx, lastDy) >= density * 2f) {
                float cross = lastDx * dy - lastDy * dx;
                float dot = lastDx * dx + lastDy * dy;
                accumulatedTurn += (float) Math.atan2(cross, dot);
            }
            if (Math.abs(dx) >= density * 7f && Math.abs(dx) > Math.abs(dy) * .8f) {
                int direction = dx > 0f ? 1 : -1;
                if (horizontalDirection != 0 && direction != horizontalDirection) reversals++;
                horizontalDirection = direction;
            }
            pathLength += distance;
            velocityX = dx * 1000f / elapsed;
            velocityY = dy * 1000f / elapsed;
            lastDx = dx;
            lastDy = dy;
            lastX = x;
            lastY = y;
            lastAt = at;
        }
    }

    Outcome finish(float x, float y, long at, boolean left, boolean right,
                   boolean top, boolean bottom) {
        move(x, y, at);
        float speed = at - lastAt > 160L ? 0f : (float) Math.hypot(velocityX, velocityY);
        long duration = at - startedAt;
        if (Math.abs(accumulatedTurn) >= Math.PI * 2.6f && pathLength >= density * 240f) {
            return Outcome.SPIN;
        }
        if (reversals >= 4 && duration <= 2200L && pathLength >= density * 120f) {
            return Outcome.SHAKE;
        }
        if (speed >= density * 850f) {
            if (top && velocityY < 0f) return Outcome.HIT_TOP;
            if (bottom && velocityY > 0f) return Outcome.HIT_BOTTOM;
            if (left && velocityX < 0f) return Outcome.HIT_LEFT;
            if (right && velocityX > 0f) return Outcome.HIT_RIGHT;
        }
        if (pathLength >= density * 8f && speed <= density * 260f) return Outcome.GENTLE;
        return Outcome.NONE;
    }
}
