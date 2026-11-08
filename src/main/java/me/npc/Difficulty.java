package me.npc;

/** How well a bot fights. */
public enum Difficulty {
    //      reaction cooldown aimErr turn reach crit  strafe wtap  detect jitter
    EASY   (20, 1.00f, 12.0f, 10f, 2.6, 0.00, 0.00, false, 16, 8),
    NORMAL (10, 1.00f,  6.0f, 20f, 3.0, 0.15, 0.40, false, 24, 4),
    HARD   ( 5, 1.00f,  2.5f, 40f, 3.0, 0.40, 0.80, true,  32, 2),
    EXPERT ( 2, 0.95f,  0.6f, 90f, 3.0, 0.80, 1.00, true,  48, 0);

    public final int reaction;        // ticks of hesitation before engaging a new target
    public final float cooldown;      // attack-cooldown fraction required before swinging
    public final float aimError;      // random aim wobble, degrees (bigger = more misses)
    public final float turnSpeed;     // max degrees the bot can turn per tick
    public final double reach;        // how close it needs to be
    public final double critChance;   // chance to jump-crit
    public final double strafe;       // how often it circle-strafes
    public final boolean wtap;        // sprint-reset after hits for extra knockback
    public final double detect;       // how far it notices targets
    public final int jitter;          // random extra delay between swings

    Difficulty(int reaction, float cooldown, float aimError, float turnSpeed, double reach,
               double critChance, double strafe, boolean wtap, double detect, int jitter) {
        this.reaction = reaction; this.cooldown = cooldown; this.aimError = aimError;
        this.turnSpeed = turnSpeed; this.reach = reach; this.critChance = critChance;
        this.strafe = strafe; this.wtap = wtap; this.detect = detect; this.jitter = jitter;
    }

    public Difficulty next() { return values()[(ordinal() + 1) % values().length]; }
    public Difficulty prev() { return values()[(ordinal() + values().length - 1) % values().length]; }

    public static Difficulty parse(String s) {
        if (s == null) return null;
        try { return valueOf(s.toUpperCase()); } catch (IllegalArgumentException e) { return null; }
    }
}
