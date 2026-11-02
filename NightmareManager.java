package com.stalker;

import com.stalker.entity.StalkerEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sleeping is no longer safe. Each time you get into bed there is a chance you will have a nightmare
 * (higher the more dread you carry): sounds creep closer while you sleep, then you are yanked awake
 * to something wrong - it standing at the foot of your bed, the lights gone out, or it outside the window.
 * Waking from a nightmare does not skip the night, and it feeds your dread.
 */
public final class NightmareManager {
    private static final class Dream {
        boolean active;
        int triggerAt;
    }

    private static final Map<UUID, Dream> DREAMS = new HashMap<>();
    private static final Map<UUID, Long> LAST_NIGHTMARE_DAY = new HashMap<>();

    private static final String[] AT_BED = {
            "It was standing at the foot of your bed.",
            "It was there the whole time.",
            "You were not dreaming."
    };
    private static final String[] DARK = {
            "Don't turn around.",
            "Who put out the lights?",
            "Something is breathing in the room."
    };
    private static final String[] WINDOW = {
            "It was outside, looking in.",
            "It knows where you sleep.",
            "It has been watching since you lay down."
    };

    private NightmareManager() {}

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(NightmareManager::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            DREAMS.remove(handler.getPlayer().getUuid());
            LAST_NIGHTMARE_DAY.remove(handler.getPlayer().getUuid());
        });
    }

    private static void tick(ServerWorld world) {
        for (ServerPlayerEntity p : world.getPlayers()) {
            UUID id = p.getUuid();
            if (!p.isSleeping()) {
                DREAMS.remove(id);
                continue;
            }
            Random r = world.getRandom();
            Dream dream = DREAMS.get(id);
            if (dream == null) {
                dream = new Dream();
                double chance = 0.12 + 0.70 * (StalkerManager.getDread(p) / (double) StalkerConfig.STAGE3_DREAD);
                long day = world.getTimeOfDay() / 24000L;
                if (LAST_NIGHTMARE_DAY.getOrDefault(id, -1L) == day) chance = 0.05; // let them sleep after a scare
                dream.active = r.nextDouble() < chance;
                dream.triggerAt = 45 + r.nextInt(40); // always before the 100 ticks needed to skip the night
                DREAMS.put(id, dream);
            }
            if (!dream.active) continue;

            int t = p.getSleepTimer();
            if (t >= dream.triggerAt) {
                wakeScare(world, p);
                DREAMS.remove(id);
            } else if (t > 0 && t % 15 == 0) {
                cue(world, p, t / 15);
            }
        }
    }

    /** Quiet things happening around the bed while you sleep. */
    private static void cue(ServerWorld world, ServerPlayerEntity p, int step) {
        Random r = world.getRandom();
        double ang = r.nextDouble() * Math.PI * 2;
        double dist = Math.max(2.5, 9.0 - step * 1.8);
        Vec3d pos = p.getPos().add(Math.cos(ang) * dist, 0, Math.sin(ang) * dist);
        switch (step % 3) {
            case 1 -> StalkerUtil.playFor(p, SoundEvents.BLOCK_WOODEN_DOOR_OPEN, pos, 0.5f, 0.7f);
            case 2 -> StalkerUtil.playFor(p, SoundEvents.BLOCK_WOOD_STEP, pos, 0.8f, 0.55f);
            default -> StalkerUtil.playFor(p, SoundEvents.ENTITY_WARDEN_HEARTBEAT, p.getPos(), 0.4f + step * 0.2f, 0.9f);
        }
    }

    private static void wakeScare(ServerWorld world, ServerPlayerEntity p) {
        Random r = world.getRandom();
        LAST_NIGHTMARE_DAY.put(p.getUuid(), world.getTimeOfDay() / 24000L);
        p.wakeUp(false, true);
        StalkerManager.addDread(p, 30);

        StalkerUtil.playFor(p, SoundEvents.ENTITY_WARDEN_NEARBY_CLOSEST, p.getPos(), 1.0f, 0.6f);
        StalkerUtil.playFor(p, SoundEvents.ENTITY_WARDEN_HEARTBEAT, p.getPos(), 1.0f, 1.3f);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 80, 0, true, false, false));

        int roll = r.nextInt(10);
        String[] lines;
        if (roll < 5) {
            lines = AT_BED;
            apparition(world, p, 2.5, 6.0, 100);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 60, 0, true, false, false));
        } else if (roll < 8) {
            lines = DARK;
            StalkerManager.extinguishNear(world, p.getBlockPos(), 120, 9, 12);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 50, 0, true, false, false));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 140, 0, true, false, false));
        } else {
            lines = WINDOW;
            apparition(world, p, 9.0, 16.0, 140);
        }
        p.sendMessage(Text.literal(lines[r.nextInt(lines.length)]).formatted(Formatting.DARK_GRAY, Formatting.ITALIC), true);
    }

    /** A harmless, silent copy of the Stalker that stares at you and fades if you look at it. */
    private static boolean apparition(ServerWorld world, ServerPlayerEntity p, double min, double max, int ticks) {
        Random r = world.getRandom();
        for (int i = 0; i < 40; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double dist = min + r.nextDouble() * (max - min);
            int x = MathHelper.floor(p.getX() + Math.cos(ang) * dist);
            int z = MathHelper.floor(p.getZ() + Math.sin(ang) * dist);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            BlockPos g = StalkerUtil.findStandable(world, new BlockPos(x, p.getBlockY(), z), 3);
            if (g == null) continue;
            Vec3d pos = Vec3d.ofBottomCenter(g);
            Box box = StalkerMod.STALKER.getDimensions().getBoxAt(pos);
            if (!world.isSpaceEmpty(box)) continue;
            if (!StalkerUtil.hasLineOfSight(world, p.getEyePos(), pos.add(0, 2.5, 0), p)) continue;

            StalkerEntity e = StalkerMod.STALKER.create(world);
            if (e == null) return false;
            float yaw = (float) (MathHelper.atan2(p.getZ() - pos.z, p.getX() - pos.x) * 57.29578) - 90f;
            e.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0f);
            e.setHeadYaw(yaw);
            e.setBodyYaw(yaw);
            e.setTargetPlayer(p.getUuid());
            e.setApparition(ticks);
            world.spawnEntity(e);
            return true;
        }
        return false;
    }
}
