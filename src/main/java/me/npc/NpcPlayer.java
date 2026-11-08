package me.npc;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** A real ServerPlayer with no client behind it. Every tick it runs its "brain". */
public class NpcPlayer extends ServerPlayer {

    // ---- manual controls ----
    public float forward, strafe;
    public boolean sprint, autoJump, jumpOnce;

    // ---- behaviours ----
    public UUID followTarget;
    public UUID formationLeader;
    public double[] slot;                 // local formation slot [right, forward]
    public String attackSpec;             // selector of who to attack, e.g. "Steve", "@a", "@enemy"
    public Difficulty difficulty = Difficulty.NORMAL;

    // ---- remembered so a bot can be re-created after death ----
    public int chunkRadius = 4;
    public String gearTier = "none";
    public Location home;
    public Collection<ProfileProperty> skin = List.of();

    // ---- combat state ----
    private final Random rng = new Random();
    private Player target;
    private int retarget, react, aimTimer, strafeTimer, attackWait, wtap, critTimer, strafeDir = 1;
    private boolean strafing, critPending;
    private float errYaw, errPitch;

    // ---- this tick's output ----
    private float cf, cs;
    private boolean cspr, cjump;

    public NpcPlayer(MinecraftServer server, ServerLevel level, GameProfile profile) {
        super(server, level, profile, ClientInformation.createDefault());
    }

    @Override
    public void tick() {
        if (server.getTickCount() % 10 == 0) {
            connection.resetPosition();
            ((ServerLevel) level()).getChunkSource().move(this); // keeps chunks loading around the bot
        }
        if (!isDeadOrDying()) runControls();
        super.tick();
        doTick();
    }

    // =====================================================================
    private void runControls() {
        Player me = getBukkitEntity();
        cf = forward; cs = strafe; cspr = sprint; cjump = autoJump || jumpOnce;
        jumpOnce = false;

        if (!combat(me)) {
            Player leader = formationLeader == null ? null : Bukkit.getPlayer(formationLeader);
            Player followed = followTarget == null ? null : Bukkit.getPlayer(followTarget);
            if (leader != null && slot != null && leader.getWorld().equals(me.getWorld())) {
                double[] w = Formation.toWorld(leader.getLocation(), slot);
                if (goTo(me, w[0], w[1], 0.8)) face(leader.getLocation().getYaw(), 0f);
            } else if (followed != null && followed.getWorld().equals(me.getWorld())) {
                Location l = followed.getLocation();
                goTo(me, l.getX(), l.getZ(), 3.0);
            }
        }

        this.zza = cf;
        this.xxa = cs;
        setSprinting(cspr && cf > 0);
        setJumping(cjump);
    }

    /** Walks toward x/z. Returns true when already close enough. */
    private boolean goTo(Player me, double x, double z, double stop) {
        double dx = x - getX(), dz = z - getZ();
        double dist = Math.hypot(dx, dz);
        if (dist > 40) {
            Location t = me.getLocation();
            t.setX(x); t.setZ(z);
            me.teleport(t);
            return true;
        }
        if (dist <= stop) { cf = 0; cspr = false; return true; }
        face((float) Math.toDegrees(Math.atan2(-dx, dz)), 0f);
        cf = 1;
        cspr = dist > 7;
        if (horizontalCollision) cjump = true;
        return false;
    }

    private void face(float yaw, float pitch) {
        setYRot(yaw);
        setXRot(pitch);
        setYHeadRot(yaw);
    }

    // =====================================================================
    //  Combat AI. Returns true if it is fighting something this tick.
    // =====================================================================
    private boolean combat(Player me) {
        if (attackSpec == null) { target = null; return false; }

        if (--retarget <= 0) { retarget = 10; pickTarget(me); }
        if (target == null || !valid(me, target)) { target = null; return false; }

        Difficulty df = difficulty;
        Vector d = target.getBoundingBox().getCenter().subtract(me.getEyeLocation().toVector());
        double horiz = Math.hypot(d.getX(), d.getZ());

        // --- aiming: wobble + limited turn speed (this is what makes easy bots miss) ---
        if (--aimTimer <= 0) {
            aimTimer = 8;
            errYaw = (rng.nextFloat() * 2 - 1) * df.aimError;
            errPitch = (rng.nextFloat() * 2 - 1) * df.aimError * 0.6f;
        }
        float tYaw = (float) Math.toDegrees(Math.atan2(-d.getX(), d.getZ())) + errYaw;
        float tPitch = (float) -Math.toDegrees(Math.atan2(d.getY(), horiz)) + errPitch;
        float yaw = getYRot() + clamp(wrap(tYaw - getYRot()), -df.turnSpeed, df.turnSpeed);
        float pitch = clamp(getXRot() + clamp(tPitch - getXRot(), -df.turnSpeed, df.turnSpeed), -90f, 90f);
        face(yaw, pitch);

        // --- reaction delay ---
        if (react > 0) { react--; cf = 0; cs = 0; cspr = false; return true; }

        // --- movement ---
        cf = horiz > df.reach - 0.5 ? 1 : 0;
        cspr = horiz > (df == Difficulty.EASY ? 6 : 4) && wtap <= 0;
        if (horizontalCollision) cjump = true;
        cs = 0;
        if (df.strafe > 0 && horiz < 6) {
            if (--strafeTimer <= 0) {
                strafeTimer = 8 + rng.nextInt(20);
                strafeDir = rng.nextBoolean() ? 1 : -1;
                strafing = rng.nextDouble() < df.strafe;
            }
            if (strafing) cs = strafeDir;
        }

        // --- attacking ---
        if (wtap > 0) { wtap--; cspr = false; }
        if (attackWait > 0) attackWait--;
        if (critPending) critTimer++;

        if (me.getAttackCooldown() >= df.cooldown && attackWait <= 0 && horiz < df.reach + 1.5) {
            if (df.critChance > 0 && !critPending && onGround() && rng.nextDouble() < df.critChance) {
                critPending = true;
                critTimer = 0;
                cjump = true;
            }
            boolean ready = !critPending || (fallDistance > 0 && !onGround()) || critTimer > 14;
            if (ready) {
                Entity hit = me.getTargetEntity((int) Math.ceil(df.reach));
                if (hit instanceof Player p && valid(me, p)) me.attack(p);
                me.swingMainHand();
                attackWait = df.jitter == 0 ? 0 : rng.nextInt(df.jitter + 1);
                critPending = false;
                if (df.wtap) wtap = 2;
            }
        }
        return true;
    }

    private void pickTarget(Player me) {
        Player best = null;
        double bestD = difficulty.detect * difficulty.detect;
        for (Player p : NpcPlugin.get().resolve(attackSpec, this)) {
            if (!valid(me, p)) continue;
            double dist = p.getLocation().distanceSquared(me.getLocation());
            if (dist < bestD) { bestD = dist; best = p; }
        }
        if (best != null && target == null) react = difficulty.reaction;
        target = best;
    }

    private boolean valid(Player me, Player p) {
        if (p.equals(me) || !p.isOnline() || p.isDead() || !p.getWorld().equals(me.getWorld())) return false;
        GameMode g = p.getGameMode();
        if (g != GameMode.SURVIVAL && g != GameMode.ADVENTURE) return false;
        return !Teams.same(me, p) || NpcPlugin.get().settings().friendlyFire;
    }

    private static float wrap(float a) {
        a = a % 360f;
        if (a >= 180f) a -= 360f;
        if (a < -180f) a += 360f;
        return a;
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    public void stopAll() {
        forward = 0; strafe = 0; sprint = false; autoJump = false;
        followTarget = null; formationLeader = null; slot = null;
        attackSpec = null; target = null;
    }
}
