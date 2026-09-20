package dev.linjian.peek;

/**
 * Small persistent-feeling state layer for the desk pet.
 *
 * The shape is inspired by Cheiineeey/companion-embodiment (MIT): events do not map
 * straight to canned lines; they first alter slow/fast internal signals, and those
 * signals influence the next reaction. This implementation is Android-independent so
 * it can be tested with plain javac.
 */
final class DeskPetEmbodiment {
    enum Event {
        TAP, HOLD, GENTLE, SPIN, SHAKE, HIT_EDGE, WATCH_ON, WATCH_OFF, REUNION
    }

    enum Mood { CALM, SOFT, CLINGY, WIRED, GRUMPY }

    static final class Snapshot {
        final float stress;
        final float joy;
        final float bond;
        final float activation;
        final Mood mood;

        Snapshot(float stress, float joy, float bond, float activation, Mood mood) {
            this.stress = stress;
            this.joy = joy;
            this.bond = bond;
            this.activation = activation;
            this.mood = mood;
        }

        long lookDuration(long baseMs) {
            float multiplier = 0.92f + bond * .42f - stress * .12f + activation * .10f;
            return Math.max(700L, Math.round(baseMs * multiplier));
        }

        int idleTwitchDenominator() {
            if (activation >= .62f) return 4;
            if (stress >= .62f) return 5;
            if (bond >= .76f) return 6;
            return 8;
        }
    }

    private static final float BASE_STRESS = .22f;
    private static final float BASE_JOY = .46f;
    private static final float BASE_BOND = .58f;
    private static final float BASE_ACTIVATION = .22f;

    private float stress;
    private float joy;
    private float bond;
    private float activation;
    private long lastUpdatedAt;
    private long lastInteractionAt;

    DeskPetEmbodiment() {
        this(BASE_STRESS, BASE_JOY, BASE_BOND, BASE_ACTIVATION, 0L, 0L);
    }

    DeskPetEmbodiment(float stress, float joy, float bond, float activation,
                      long lastUpdatedAt, long lastInteractionAt) {
        this.stress = clamp(stress);
        this.joy = clamp(joy);
        this.bond = clamp(bond);
        this.activation = clamp(activation);
        this.lastUpdatedAt = Math.max(0L, lastUpdatedAt);
        this.lastInteractionAt = Math.max(0L, lastInteractionAt);
    }

    Snapshot sample(long now) {
        decay(now);
        return snapshot();
    }

    Snapshot react(Event event, long now) {
        decay(now);
        switch (event) {
            case TAP:
                joy += .08f;
                bond += .05f;
                activation += .07f;
                stress -= .03f;
                break;
            case HOLD:
                bond += .025f;
                activation += .13f;
                stress += .055f;
                break;
            case GENTLE:
                joy += .13f;
                bond += .09f;
                activation -= .04f;
                stress -= .11f;
                break;
            case SPIN:
                joy += .015f;
                activation += .28f;
                stress += .18f;
                break;
            case SHAKE:
                joy -= .045f;
                activation += .27f;
                stress += .21f;
                break;
            case HIT_EDGE:
                joy -= .10f;
                activation += .36f;
                stress += .33f;
                break;
            case WATCH_ON:
                joy += .07f;
                bond += .11f;
                activation += .05f;
                stress -= .025f;
                break;
            case WATCH_OFF:
                activation -= .035f;
                stress -= .02f;
                break;
            case REUNION:
                joy += .22f;
                bond += .17f;
                activation += .14f;
                stress -= .06f;
                break;
        }
        stress = clamp(stress);
        joy = clamp(joy);
        bond = clamp(bond);
        activation = clamp(activation);
        lastUpdatedAt = now;
        lastInteractionAt = now;
        return snapshot();
    }

    boolean reunionDue(long now) {
        return lastInteractionAt > 0L && now - lastInteractionAt >= 90L * 60L * 1000L;
    }

    float stress() { return stress; }
    float joy() { return joy; }
    float bond() { return bond; }
    float activation() { return activation; }
    long lastUpdatedAt() { return lastUpdatedAt; }
    long lastInteractionAt() { return lastInteractionAt; }

    private void decay(long now) {
        if (lastUpdatedAt <= 0L) {
            lastUpdatedAt = now;
            return;
        }
        long elapsedMs = Math.max(0L, now - lastUpdatedAt);
        if (elapsedMs == 0L) return;
        float seconds = elapsedMs / 1000f;

        // Fast activation fades first; stress leaves an aftertaste; bond is deliberately slow.
        activation = approach(activation, BASE_ACTIVATION, seconds, 18f);
        stress = approach(stress, BASE_STRESS, seconds, 55f);
        joy = approach(joy, BASE_JOY, seconds, 95f);
        bond = approach(bond, BASE_BOND, seconds, 900f);
        lastUpdatedAt = now;
    }

    private Snapshot snapshot() {
        Mood mood;
        if (stress >= .68f) mood = Mood.GRUMPY;
        else if (activation >= .66f) mood = Mood.WIRED;
        else if (bond >= .79f && joy >= .54f) mood = Mood.CLINGY;
        else if (joy >= .62f && stress < .42f) mood = Mood.SOFT;
        else mood = Mood.CALM;
        return new Snapshot(stress, joy, bond, activation, mood);
    }

    private static float approach(float current, float target, float seconds, float halfLifeSeconds) {
        if (seconds <= 0f) return current;
        double keep = Math.pow(.5d, seconds / Math.max(1f, halfLifeSeconds));
        return clamp((float) (target + (current - target) * keep));
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
