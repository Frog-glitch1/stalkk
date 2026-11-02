package com.stalker;

import com.stalker.entity.StalkerEntity;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks each player's "dread", decides the Stalker's stage, spawns/relocates it,
 * and runs the psychological layer (mimicked sounds, heartbeat, darkness, snuffed torches).
 */
public final class StalkerManager {
    private static final Map<UUID, PlayerData> DATA = new HashMap<>();

    private static final SoundEvent[] MIMICS = {
            SoundEvents.BLOCK_WOODEN_DOOR_OPEN,
            SoundEvents.BLOCK_WOODEN_DOOR_CLOSE,
            SoundEvents.BLOCK_IRON_DOOR_OPEN,
            SoundEvents.BLOCK_CHEST_CLOSE,
            SoundEvents.BLOCK_GRAVEL_STEP,
            SoundEvents.BLOCK_WOOD_STEP,
            SoundEvents.BLOCK_STONE_BREAK,
            SoundEvents.ENTITY_CREEPER_PRIMED,
            SoundEvents.ENTITY_ZOMBIE_AMBIENT,
            SoundEvents.ENTITY_SKELETON_AMBIENT
    };

    private static final class PlayerData {
        int dread = 0;
        int huntSeconds = -1;
        int respawnDelay = 25;
        int mimicCooldown = 40;
        int spawnFails = 0;
        UUID stalker = null;
    }

    private StalkerManager() {}

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(StalkerManager::onWorldTick);
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) reset(newPlayer);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity p = handler.getPlayer();
            PlayerData d = DATA.remove(p.getUuid());
            if (d != null && d.stalker != null) {
                Entity e = p.getServerWorld().getEntity(d.stalker);
                if (e != null) e.discard();
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> DATA.clear());
    }

    // ------------------------------------------------------------------ queries

    public static int getStage(UUID player) {
        PlayerData d = DATA.get(player);
        return d == null ? 0 : stageOf(d);
    }

    public static int getDread(ServerPlayerEntity player) {
        PlayerData d = DATA.get(player.getUuid());
        return d == null ? 0 : d.dread;
    }

    public static void addDread(ServerPlayerEntity player, int amount) {
        PlayerData d = DATA.computeIfAbsent(player.getUuid(), k -> new PlayerData());
        d.dread = MathHelper.clamp(d.dread + amount, 0, StalkerConfig.STAGE3_DREAD);
    }

    private static int stageOf(PlayerData d) {
        if (d.dread >= StalkerConfig.STAGE3_DREAD) return 3;
        if (d.dread >= StalkerConfig.STAGE2_DREAD) return 2;
        if (d.dread >= StalkerConfig.STAGE1_DREAD) return 1;
        return 0;
    }

    private static StalkerEntity findStalker(ServerWorld world, PlayerData d) {
        if (d.stalker == null) return null;
        Entity e = world.getEntity(d.stalker);
        return (e instanceof StalkerEntity s && s.isAlive()) ? s : null;
    }

    // ------------------------------------------------------------------ main tick (once per second per player)

    private static void onWorldTick(ServerWorld world) {
        if (world.getRegistryKey() != World.OVERWORLD || world.getTime() % 20 != 0) return;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator() || !player.isAlive()) continue;
            tickPlayer(world, player, DATA.computeIfAbsent(player.getUuid(), k -> new PlayerData()));
        }
    }

    private static void tickPlayer(ServerWorld world, ServerPlayerEntity player, PlayerData d) {
        updateDread(world, player, d);
        int stage = stageOf(d);

        // The hunt has a time limit - survive it and the Stalker loses interest.
        if (stage == 3) {
            if (d.huntSeconds < 0) {
                d.huntSeconds = StalkerConfig.HUNT_SECONDS;
                StalkerUtil.playFor(player, SoundEvents.ENTITY_WARDEN_NEARBY_CLOSEST, player.getPos(), 1.0f, 0.7f);
            }
            if (--d.huntSeconds <= 0) {
                d.dread = StalkerConfig.STAGE1_DREAD + 10;
                d.huntSeconds = -1;
                StalkerEntity s = findStalker(world, d);
                if (s != null) vanish(s, 90, 150);
                else d.respawnDelay = Math.max(d.respawnDelay, 90);
                return;
            }
        } else {
            d.huntSeconds = -1;
        }

        // Sounds that make you doubt yourself - they play even when it isn't visible.
        if (stage >= 1 && --d.mimicCooldown <= 0) {
            d.mimicCooldown = stage >= 2 ? 10 + world.random.nextInt(25) : 25 + world.random.nextInt(50);
            mimic(world, player);
        }

        StalkerEntity stalker = findStalker(world, d);
        if (stalker == null) {
            d.stalker = null;
            if (d.respawnDelay > 0) {
                d.respawnDelay--;
                if (stage == 3) d.respawnDelay = Math.min(d.respawnDelay, 4);
            } else if (d.dread >= StalkerConfig.SIGHTING_DREAD) {
                if (!trySpawn(world, player, d, stage)) d.spawnFails++;
            }
            return;
        }

        double dist = stalker.distanceTo(player);
        if (stage >= 2 && dist < 26) {
            float closeness = (float) (1.0 - dist / 26.0);
            StalkerUtil.playFor(player, SoundEvents.ENTITY_WARDEN_HEARTBEAT, player.getPos(),
                    0.5f + closeness, 0.9f + closeness * 0.5f);
        }
        if (stage >= 2 && dist < 14 && StalkerConfig.DARKNESS_PULSE) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 50, 0, true, false, false));
        }
        if (stage >= 2 && dist < 12 && StalkerConfig.EXTINGUISH_TORCHES) {
            extinguish(world, stalker);
        }
    }

    private static void updateDread(ServerWorld world, ServerPlayerEntity player, PlayerData d) {
        if (d.huntSeconds > 0) { // during the hunt your fear can't drop
            d.dread = Math.max(d.dread, StalkerConfig.STAGE3_DREAD);
            return;
        }
        BlockPos pos = player.getBlockPos();
        boolean sky = world.isSkyVisible(pos);
        int light = world.getLightLevel(pos);
        int gain = 0;
        if (!sky) gain++;
        if (light <= 6) gain++;
        if (sky && world.isNight()) gain++;
        if (sky && light >= 12 && world.isDay()) gain = -2;
        if (player.isSleeping()) gain = -3;
        boolean company = world.getPlayers().stream().anyMatch(o ->
                o != player && !o.isSpectator() && o.squaredDistanceTo(player) < 24 * 24);
        if (company) gain = Math.min(gain, 0);
        d.dread = MathHelper.clamp(d.dread + gain, 0, StalkerConfig.STAGE3_DREAD);
    }

    // ------------------------------------------------------------------ spawning

    private static boolean trySpawn(ServerWorld world, ServerPlayerEntity player, PlayerData d, int stage) {
        double min, max;
        switch (stage) {
            case 0 -> { min = 36; max = 56; }
            case 1 -> { min = 20; max = 34; }
            case 2 -> { min = 12; max = 22; }
            default -> { min = 16; max = 26; }
        }
        boolean relaxed = d.spawnFails > 15; // in open terrain, allow spawning in the dark even if visible
        Random r = world.getRandom();

        for (int i = 0; i < 30; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double dist = min + r.nextDouble() * (max - min);
            int x = MathHelper.floor(player.getX() + Math.cos(ang) * dist);
            int z = MathHelper.floor(player.getZ() + Math.sin(ang) * dist);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;

            BlockPos ground = StalkerUtil.findStandable(world, new BlockPos(x, player.getBlockY(), z), 10);
            if (ground == null) continue;
            Vec3d pos = Vec3d.ofBottomCenter(ground);
            Box box = StalkerMod.STALKER.getDimensions().getBoxAt(pos);
            if (!world.isSpaceEmpty(box) || world.containsFluid(box)) continue;

            int light = world.getLightLevel(ground);
            if (stage > 0 && light > 11) continue;
            boolean seen = StalkerUtil.hasLineOfSight(world, player.getEyePos(), pos.add(0, 2.5, 0), player)
                    || StalkerUtil.hasLineOfSight(world, player.getEyePos(), pos.add(0, 1.0, 0), player);
            if (stage > 0 && seen && !(relaxed && light <= 7)) continue;

            StalkerEntity e = StalkerMod.STALKER.create(world);
            if (e == null) return false;
            float yaw = (float) (MathHelper.atan2(player.getZ() - pos.z, player.getX() - pos.x) * 57.29578) - 90f;
            e.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0f);
            e.setHeadYaw(yaw);
            e.setBodyYaw(yaw);
            e.setTargetPlayer(player.getUuid());
            e.setStage(stage);
            world.spawnEntity(e);
            d.stalker = e.getUuid();
            d.respawnDelay = 0;
            d.spawnFails = 0;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ psychological layer

    private static void mimic(ServerWorld world, ServerPlayerEntity player) {
        Random r = world.getRandom();
        Vec3d look = player.getRotationVec(1.0f);
        double behind = Math.atan2(-look.z, -look.x);
        double ang = behind + (r.nextDouble() - 0.5) * 2.0;
        double dist = 8 + r.nextDouble() * 7;
        Vec3d pos = player.getPos().add(Math.cos(ang) * dist, 0, Math.sin(ang) * dist);
        SoundEvent s = MIMICS[r.nextInt(MIMICS.length)];
        StalkerUtil.playFor(player, s, pos, 0.6f + r.nextFloat() * 0.4f, 0.8f + r.nextFloat() * 0.3f);
    }

    private static void extinguish(ServerWorld world, StalkerEntity stalker) {
        extinguishNear(world, stalker.getBlockPos(), 40, 10, 1);
    }

    /** Snuffs out up to {@code max} torches / lit campfires found by random probing around the centre. */
    public static void extinguishNear(ServerWorld world, BlockPos c, int attempts, int radius, int max) {
        Random r = world.getRandom();
        int done = 0;
        for (int i = 0; i < attempts && done < max; i++) {
            BlockPos p = c.add(r.nextInt(radius * 2 + 1) - radius, r.nextInt(9) - 3, r.nextInt(radius * 2 + 1) - radius);
            BlockState st = world.getBlockState(p);
            boolean torch = st.isOf(Blocks.TORCH) || st.isOf(Blocks.WALL_TORCH)
                    || st.isOf(Blocks.SOUL_TORCH) || st.isOf(Blocks.SOUL_WALL_TORCH);
            boolean fire = st.isOf(Blocks.CAMPFIRE) && st.get(CampfireBlock.LIT);
            if (!torch && !fire) continue;
            if (torch) world.removeBlock(p, false);
            else world.setBlockState(p, st.with(CampfireBlock.LIT, false));
            world.playSound(null, p, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.6f, 0.8f);
            done++;
        }
    }

    // ------------------------------------------------------------------ entity callbacks

    /** The Stalker fades away (lost interest, was stared down, relocating...). It will come back after the delay. */
    public static void vanish(StalkerEntity e, int minSeconds, int maxSeconds) {
        if (!(e.getWorld() instanceof ServerWorld w)) return;
        PlayerData d = e.getTargetPlayer() == null ? null : DATA.get(e.getTargetPlayer());
        if (d != null && e.getUuid().equals(d.stalker)) {
            d.stalker = null;
            d.respawnDelay = minSeconds + w.random.nextInt(Math.max(1, maxSeconds - minSeconds + 1));
        }
        w.spawnParticles(ParticleTypes.LARGE_SMOKE, e.getX(), e.getY() + 1.2, e.getZ(), 25, 0.3, 0.9, 0.3, 0.01);
        e.discard();
    }

    /** Removes a nightmare apparition without touching anyone's dread. */
    public static void dismiss(StalkerEntity e) {
        if (e.getWorld() instanceof ServerWorld w) {
            w.spawnParticles(ParticleTypes.LARGE_SMOKE, e.getX(), e.getY() + 1.2, e.getZ(), 25, 0.3, 0.9, 0.3, 0.01);
        }
        e.discard();
    }

    /** Hurt badly: it retreats, and your dread falls back to "being watched". */
    public static void onWounded(StalkerEntity e) {
        PlayerData d = e.getTargetPlayer() == null ? null : DATA.get(e.getTargetPlayer());
        if (d != null && e.getUuid().equals(d.stalker)) {
            d.dread = StalkerConfig.SIGHTING_DREAD + 10;
            d.huntSeconds = -1;
        }
        vanish(e, 120, 200);
    }

    /** You killed it. It will be back. */
    public static void onKilled(StalkerEntity e) {
        PlayerData d = e.getTargetPlayer() == null ? null : DATA.get(e.getTargetPlayer());
        if (d != null && e.getUuid().equals(d.stalker)) {
            d.dread = 0;
            d.huntSeconds = -1;
            d.stalker = null;
            d.respawnDelay = 600;
        }
    }

    // ------------------------------------------------------------------ commands / helpers

    public static void reset(ServerPlayerEntity player) {
        PlayerData d = DATA.computeIfAbsent(player.getUuid(), k -> new PlayerData());
        discardStalker(player, d);
        d.dread = 0;
        d.huntSeconds = -1;
        d.respawnDelay = 30;
        d.spawnFails = 0;
    }

    public static void setStage(ServerPlayerEntity player, int stage) {
        PlayerData d = DATA.computeIfAbsent(player.getUuid(), k -> new PlayerData());
        discardStalker(player, d);
        d.dread = switch (stage) {
            case 0 -> StalkerConfig.SIGHTING_DREAD;
            case 1 -> StalkerConfig.STAGE1_DREAD;
            case 2 -> StalkerConfig.STAGE2_DREAD;
            default -> StalkerConfig.STAGE3_DREAD;
        };
        d.huntSeconds = -1;
        d.respawnDelay = 0;
    }

    public static boolean forceSpawn(ServerPlayerEntity player) {
        PlayerData d = DATA.computeIfAbsent(player.getUuid(), k -> new PlayerData());
        discardStalker(player, d);
        d.spawnFails = 100;
        return trySpawn(player.getServerWorld(), player, d, stageOf(d));
    }

    private static void discardStalker(ServerPlayerEntity player, PlayerData d) {
        StalkerEntity s = findStalker(player.getServerWorld(), d);
        if (s != null) s.discard();
        d.stalker = null;
    }
}
