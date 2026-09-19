package dev.linjian.peek;

public final class DeskPetGestureTrackerTest {
    public static void main(String[] args) {
        detectsSpin();
        detectsShake();
        detectsEdgeFling();
        detectsEveryEdgeDirection();
        doesNotFlingAfterPause();
        detectsGentlePlacementAfterPause();
        ignoresOrdinaryDrag();
    }

    private static void detectsSpin() {
        DeskPetGestureTracker tracker = new DeskPetGestureTracker(1f);
        tracker.begin(100, 50, 0);
        long at = 0;
        for (int turn = 0; turn < 2; turn++) {
            for (int degree = 12; degree <= 360; degree += 12) {
                at += 12;
                double angle = Math.toRadians(degree);
                tracker.move(100 + (float) Math.cos(angle) * 50,
                        100 + (float) Math.sin(angle) * 50, at);
            }
        }
        assertEquals(DeskPetGestureTracker.Outcome.SPIN,
                tracker.finish(150, 100, at + 12, false, false, false, false));
    }

    private static void detectsShake() {
        DeskPetGestureTracker tracker = new DeskPetGestureTracker(1f);
        tracker.begin(100, 100, 0);
        tracker.move(150, 100, 100);
        tracker.move(70, 100, 200);
        tracker.move(155, 100, 300);
        tracker.move(65, 100, 400);
        tracker.move(160, 100, 500);
        assertEquals(DeskPetGestureTracker.Outcome.SHAKE,
                tracker.finish(60, 100, 600, false, false, false, false));
    }

    private static void detectsEdgeFling() {
        DeskPetGestureTracker tracker = new DeskPetGestureTracker(1f);
        tracker.begin(200, 200, 0);
        tracker.move(120, 200, 50);
        tracker.move(20, 200, 100);
        assertEquals(DeskPetGestureTracker.Outcome.HIT_LEFT,
                tracker.finish(0, 200, 110, true, false, false, false));
    }

    private static void detectsEveryEdgeDirection() {
        assertEdge(100, 0, false, false, true, false,
                DeskPetGestureTracker.Outcome.HIT_TOP);
        assertEdge(200, 300, false, false, false, true,
                DeskPetGestureTracker.Outcome.HIT_BOTTOM);
        assertEdge(300, 200, false, true, false, false,
                DeskPetGestureTracker.Outcome.HIT_RIGHT);
    }

    private static void assertEdge(float endX, float endY, boolean left, boolean right,
                                   boolean top, boolean bottom,
                                   DeskPetGestureTracker.Outcome expected) {
        DeskPetGestureTracker tracker = new DeskPetGestureTracker(1f);
        tracker.begin(100, 100, 0);
        tracker.move((100 + endX) / 2f, (100 + endY) / 2f, 40);
        tracker.move(endX, endY, 80);
        assertEquals(expected, tracker.finish(endX, endY, 90, left, right, top, bottom));
    }

    private static void doesNotFlingAfterPause() {
        DeskPetGestureTracker tracker = new DeskPetGestureTracker(1f);
        tracker.begin(200, 200, 0);
        tracker.move(100, 200, 50);
        tracker.move(0, 200, 100);
        assertEquals(DeskPetGestureTracker.Outcome.GENTLE,
                tracker.finish(0, 200, 400, true, false, false, false));
    }

    private static void ignoresOrdinaryDrag() {
        DeskPetGestureTracker tracker = new DeskPetGestureTracker(1f);
        tracker.begin(20, 20, 0);
        tracker.move(70, 40, 200);
        assertEquals(DeskPetGestureTracker.Outcome.NONE,
                tracker.finish(90, 50, 260, false, false, false, false));
    }

    private static void detectsGentlePlacementAfterPause() {
        DeskPetGestureTracker tracker = new DeskPetGestureTracker(1f);
        tracker.begin(20, 20, 0);
        tracker.move(90, 50, 120);
        assertEquals(DeskPetGestureTracker.Outcome.GENTLE,
                tracker.finish(90, 50, 400, false, false, false, false));
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }
}
