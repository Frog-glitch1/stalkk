package com.stalker.entity;

import com.stalker.FlashlightManager;
import com.stalker.StalkerConfig;
import com.stalker.StalkerManager;
import com.stalker.StalkerUtil;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.UUID;

/**
 * The Stalker's whole brain. One goal, four behaviours, chosen by the player's stage of dread:
 *
 *  0 OBSERVE  - stands far away in plain sight. Stare at it and it vanishes.
 *  1 STALK    - follows at a distance, ducks behind cover whenever you look at it.
 *  2 HUNT     - freezes while you look at it, creeps closer the moment you look away,
 *               lunges if you let it reach you. Stare it down long enough and it retreats.
 *  3 KILL     - drops to all fours and chases relentlessly. Light slows it down.
 */
public class StalkerBrainGoal extends Goal {
    private final StalkerEntity mob;
    private PlayerEntity target;

    private int observedTicks;
    private int moveCooldown;
    private int attackCooldown;
    private int coverRefresh;
    private int progressCheck;
    private int stuck;
    private Vec3d cover;
    private Vec3d lastPos = Vec3d.ZERO;

    public StalkerBrainGoal(StalkerEntity mob) {
        this.mob = mob;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override public boolean canStart() { return resolveTarget(); }
    @Override public boolean shouldContinue() { return resolveTarget(); }
    @Override public boolean shouldRunEveryTick() { return true; }

    private boolean resolveTarget() {
        UUID id = mob.getTargetPlayer();
        if (id == null) return false;
        PlayerEntity p = mob.getWorld().getPlayerByUuid(id);
        if (p == null || !p.isAlive() || p.isSpectator()) return false;
        this.target = p;
        return true;
    }

    @Override
    public void start() {
        lastPos = mob.getPos();
        progressCheck = 20;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        mob.setCrawling(false);
    }

    @Override
    public void tick() {
        if (mob.isApparition()) { // nightmare copy: stare, fade if looked at
            mob.setStage(0);
            mob.setCrawling(false);
            stopMoving();
            mob.getLookControl().lookAt(target, 45.0f, 45.0f);
            observedTicks = StalkerUtil.isLookedAt(target, mob) ? observedTicks + 1 : Math.max(0, observedTicks - 1);
            if (observedTicks > 30) StalkerManager.dismiss(mob);
            return;
        }

        int stage = StalkerManager.getStage(target.getUuid());
        mob.setStage(stage);
        mob.setCrawling(stage >= 3);

        double dist = mob.distanceTo(target);
        boolean observed = StalkerUtil.isLookedAt(target, mob);
        observedTicks = observed ? observedTicks + 1 : Math.max(0, observedTicks - 3);
        if (attackCooldown > 0) attackCooldown--;
        if (moveCooldown > 0) moveCooldown--;

        mob.getLookControl().lookAt(target, 45.0f, 45.0f);

        // Hurt badly -> it backs off for a while.
        if (mob.getHealth() < mob.getMaxHealth() * 0.3f) {
            StalkerManager.onWounded(mob);
            return;
        }

        switch (stage) {
            case 0 -> observe(dist, observed);
            case 1 -> stalk(dist, observed);
            case 2 -> hunt(dist, observed);
            default -> kill(dist);
        }
    }

    // ---- behaviours ------------------------------------------------------------

    private void observe(double dist, boolean observed) {
        if (dist < 28) {
            retreat(1.2);
        } else if (observed) {
            stopMoving();
            if (observedTicks > 50) mob.vanish(30, 80); // stared at too long: gone
        } else if (dist > 46) {
            approach(0.9);
        } else {
            stopMoving();
        }
    }

    private void stalk(double dist, boolean observed) {
        if (observed) {
            if (observedTicks > 140 && dist < 26) { // couldn't hide in time
                mob.vanish(20, 50);
                return;
            }
            seekCover(1.35);
        } else {
            cover = null;
            if (dist < 14) retreat(1.0);
            else if (dist > 18) approach(0.95);
            else stopMoving();
        }
    }

    private void hunt(double dist, boolean observed) {
        if (observed) {
            stopMoving(); // frozen, like a statue
            if (observedTicks > 200 && dist < 16) mob.vanish(15, 35); // you stared it down
            return;
        }
        if (dist < 2.8 && attackCooldown == 0) { // you looked away one second too long
            mob.swingHand(Hand.MAIN_HAND);
            mob.tryAttack(target);
            mob.vanish(25, 50);
            return;
        }
        approach(1.15);
    }

    private void kill(double dist) {
        double speed = StalkerConfig.HUNT_SPEED;
        if (mob.getWorld().getLightLevel(mob.getBlockPos()) >= 12) speed *= 0.7; // bright light slows it
        boolean beamed = FlashlightManager.isShiningOn(target, mob); // a flashlight in its face
        if (beamed) speed *= 0.4;
        approach(speed);

        if (dist < 2.6 && attackCooldown == 0) {
            attackCooldown = beamed ? 40 : 20;
            mob.swingHand(Hand.MAIN_HAND);
            mob.tryAttack(target);
        }

        // Can't reach you (tower, 1-wide hole...)? Give up this approach and re-enter from elsewhere.
        if (--progressCheck <= 0) {
            progressCheck = 20;
            if (dist > 3.5 && mob.getPos().squaredDistanceTo(lastPos) < 0.25) stuck++;
            else stuck = 0;
            lastPos = mob.getPos();
            if (stuck >= 6) mob.vanish(3, 6);
        }
    }

    // ---- movement helpers --------------------------------------------------------

    private void stopMoving() {
        mob.getNavigation().stop();
    }

    private void approach(double speed) {
        if (moveCooldown <= 0) {
            moveCooldown = 6;
            mob.getNavigation().startMovingTo(target, speed);
        }
    }

    private void moveTo(Vec3d pos, double speed) {
        if (moveCooldown <= 0) {
            moveCooldown = 8;
            mob.getNavigation().startMovingTo(pos.x, pos.y, pos.z, speed);
        }
    }

    private void retreat(double speed) {
        Vec3d away = mob.getPos().subtract(target.getPos());
        away = new Vec3d(away.x, 0, away.z);
        if (away.lengthSquared() < 1.0E-4) away = new Vec3d(1, 0, 0);
        moveTo(mob.getPos().add(away.normalize().multiply(10)), speed);
    }

    private void seekCover(double speed) {
        boolean stale = cover == null || --coverRefresh <= 0
                || StalkerUtil.hasLineOfSight(mob.getWorld(), target.getEyePos(), cover.add(0, 2.5, 0), target);
        if (stale) {
            cover = StalkerUtil.findCover(mob, target, 24, 10.0);
            coverRefresh = 40;
            moveCooldown = 0;
        }
        if (cover != null) moveTo(cover, speed);
        else retreat(speed);
    }
}
