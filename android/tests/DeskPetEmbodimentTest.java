package dev.linjian.peek;

public final class DeskPetEmbodimentTest {
    public static void main(String[] args) {
        roughHandlingLeavesAftertaste();
        gentleHandlingSoftensState();
        activationFadesFasterThanBond();
        reunionRequiresARealGap();
        repeatedHitsEscalateToGrumpy();
    }

    private static void roughHandlingLeavesAftertaste() {
        DeskPetEmbodiment body = new DeskPetEmbodiment();
        long t = 1_000_000L;
        DeskPetEmbodiment.Snapshot hit = body.react(DeskPetEmbodiment.Event.HIT_EDGE, t);
        DeskPetEmbodiment.Snapshot later = body.sample(t + 20_000L);
        assertTrue(hit.stress > .5f, "edge hit should raise stress");
        assertTrue(later.stress > .22f, "stress should still be above baseline after 20s");
    }

    private static void gentleHandlingSoftensState() {
        DeskPetEmbodiment body = new DeskPetEmbodiment();
        long t = 2_000_000L;
        body.react(DeskPetEmbodiment.Event.SHAKE, t);
        float roughStress = body.sample(t).stress;
        DeskPetEmbodiment.Snapshot gentle =
                body.react(DeskPetEmbodiment.Event.GENTLE, t + 100L);
        assertTrue(gentle.stress < roughStress, "gentle placement should lower stress");
        assertTrue(gentle.bond > .58f, "gentle placement should raise bond");
    }

    private static void activationFadesFasterThanBond() {
        DeskPetEmbodiment body = new DeskPetEmbodiment();
        long t = 3_000_000L;
        DeskPetEmbodiment.Snapshot start = body.react(DeskPetEmbodiment.Event.REUNION, t);
        DeskPetEmbodiment.Snapshot later = body.sample(t + 120_000L);
        float activationDrop = start.activation - later.activation;
        float bondDrop = start.bond - later.bond;
        assertTrue(activationDrop > bondDrop, "activation should fade faster than bond");
    }

    private static void reunionRequiresARealGap() {
        DeskPetEmbodiment body = new DeskPetEmbodiment();
        long t = 4_000_000L;
        body.react(DeskPetEmbodiment.Event.TAP, t);
        assertTrue(!body.reunionDue(t + 20 * 60_000L), "20 minutes is not a reunion");
        assertTrue(body.reunionDue(t + 91 * 60_000L), "91 minutes should count as reunion");
    }

    private static void repeatedHitsEscalateToGrumpy() {
        DeskPetEmbodiment body = new DeskPetEmbodiment();
        long t = 5_000_000L;
        body.react(DeskPetEmbodiment.Event.HIT_EDGE, t);
        DeskPetEmbodiment.Snapshot second =
                body.react(DeskPetEmbodiment.Event.HIT_EDGE, t + 500L);
        assertEquals(DeskPetEmbodiment.Mood.GRUMPY, second.mood);
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }
}
