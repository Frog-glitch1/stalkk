package com.stalker;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

public final class StalkerUtil {
    private StalkerUtil() {}

    /** True if nothing solid blocks the line between the two points (glass counts as see-through). */
    public static boolean hasLineOfSight(World world, Vec3d from, Vec3d to, Entity context) {
        return world.raycast(new RaycastContext(from, to,
                RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, context))
                .getType() == HitResult.Type.MISS;
    }

    /** True if the mob is inside the player's field of view AND there is a clear line to it. */
    public static boolean isLookedAt(PlayerEntity player, LivingEntity mob) {
        Vec3d eye = player.getEyePos();
        Vec3d mid = mob.getPos().add(0, mob.getHeight() * 0.5, 0);
        Vec3d toMob = mid.subtract(eye);
        double dist = toMob.length();
        if (dist < 0.01) return true;
        if (dist > 128) return false;
        if (player.getRotationVec(1.0F).dotProduct(toMob.normalize()) < 0.55) return false;
        return hasLineOfSight(player.getWorld(), eye, mob.getEyePos(), player)
                || hasLineOfSight(player.getWorld(), eye, mid, player);
    }

    /** Finds a spot a 3-block-tall creature can stand on, searching up/down from the given position. */
    public static BlockPos findStandable(World w, BlockPos around, int range) {
        for (int k = 0; k <= range; k++) {
            int[] offsets = k == 0 ? new int[]{0} : new int[]{k, -k};
            for (int s : offsets) {
                BlockPos p = around.up(s);
                BlockPos below = p.down();
                if (!w.getBlockState(below).isSideSolidFullSquare(w, below, Direction.UP)) continue;
                if (!w.getBlockState(p).getCollisionShape(w, p).isEmpty()) continue;
                if (!w.getBlockState(p.up()).getCollisionShape(w, p.up()).isEmpty()) continue;
                if (!w.getBlockState(p.up(2)).getCollisionShape(w, p.up(2)).isEmpty()) continue;
                if (!w.getFluidState(p).isEmpty()) continue;
                return p;
            }
        }
        return null;
    }

    /** Picks a nearby standable spot the player cannot see, preferring ones that keep the Stalker close. */
    public static Vec3d findCover(Entity mob, PlayerEntity target, int tries, double minDist) {
        World w = mob.getWorld();
        Random r = w.getRandom();
        Vec3d best = null;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < tries; i++) {
            BlockPos base = mob.getBlockPos().add(r.nextInt(17) - 8, 0, r.nextInt(17) - 8);
            BlockPos ground = findStandable(w, base, 3);
            if (ground == null) continue;
            Vec3d pos = Vec3d.ofBottomCenter(ground);
            double distToPlayer = pos.distanceTo(target.getPos());
            if (distToPlayer < minDist) continue;
            Vec3d eye = target.getEyePos();
            if (hasLineOfSight(w, eye, pos.add(0, 2.5, 0), target)) continue;
            if (hasLineOfSight(w, eye, pos.add(0, 1.0, 0), target)) continue;
            double score = pos.squaredDistanceTo(mob.getPos()) + distToPlayer * 2.0;
            if (score < bestScore) {
                bestScore = score;
                best = pos;
            }
        }
        return best;
    }

    /** Plays a sound only the given player hears, as if it came from pos. */
    public static void playFor(ServerPlayerEntity player, SoundEvent sound, Vec3d pos, float volume, float pitch) {
        player.networkHandler.sendPacket(new PlaySoundS2CPacket(
                Registries.SOUND_EVENT.getEntry(sound), SoundCategory.AMBIENT,
                pos.x, pos.y, pos.z, volume, pitch, player.getWorld().getRandom().nextLong()));
    }
}
