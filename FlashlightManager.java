package com.stalker;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LightBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Vanilla has no dynamic lighting, so the beam is faked: while a flashlight is on, invisible light blocks
 * follow the spot where you are aiming (one at the end of the beam, a dimmer one part-way along).
 * They are removed the moment you aim elsewhere, switch off, drop it, log out or the server stops.
 */
public final class FlashlightManager {
    private static final class Tracked {
        final RegistryKey<World> dim;
        List<BlockPos> lights = new ArrayList<>();
        Tracked(RegistryKey<World> dim) { this.dim = dim; }
    }

    private static final Map<UUID, Tracked> LIT = new HashMap<>();

    private FlashlightManager() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int t = server.getTicks();
            if (t % 2 != 0) return;
            boolean drain = t % 20 == 0;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                update(server, p, drain);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> release(server, handler.getPlayer().getUuid()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (UUID id : new ArrayList<>(LIT.keySet())) release(server, id);
        });
    }

    /** True if this player's flashlight is on and the beam points at the entity (used by the Stalker's AI). */
    public static boolean isShiningOn(PlayerEntity player, LivingEntity mob) {
        if (!LIT.containsKey(player.getUuid())) return false;
        Vec3d eye = player.getEyePos();
        Vec3d to = mob.getPos().add(0, mob.getHeight() * 0.5, 0).subtract(eye);
        double d = to.length();
        if (d > 22 || d < 0.01) return d < 0.01;
        if (player.getRotationVec(1f).dotProduct(to.normalize()) < 0.93) return false;
        return StalkerUtil.hasLineOfSight(player.getWorld(), eye, mob.getEyePos(), player);
    }

    private static ItemStack findActive(ServerPlayerEntity p) {
        for (Hand h : Hand.values()) {
            ItemStack s = p.getStackInHand(h);
            if (s.getItem() instanceof FlashlightItem && FlashlightItem.isOn(s)) return s;
        }
        return null;
    }

    private static void update(MinecraftServer server, ServerPlayerEntity p, boolean drain) {
        ItemStack stack = p.isAlive() && !p.isSpectator() ? findActive(p) : null;
        if (stack != null && !FlashlightItem.hasCharge(stack)) { // battery dead
            FlashlightItem.setOn(stack, false);
            p.getServerWorld().playSound(null, p.getX(), p.getY(), p.getZ(),
                    SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.PLAYERS, 0.5f, 1.0f);
            stack = null;
        }
        if (stack == null) {
            if (LIT.containsKey(p.getUuid())) release(server, p.getUuid());
            return;
        }
        if (drain) stack.setDamage(stack.getDamage() + 1);

        ServerWorld w = p.getServerWorld();
        Tracked t = LIT.get(p.getUuid());
        if (t != null && t.dim != w.getRegistryKey()) {
            removeLights(server, t, List.of());
            t = null;
        }
        if (t == null) {
            t = new Tracked(w.getRegistryKey());
            LIT.put(p.getUuid(), t);
        }

        List<BlockPos> want = computeLights(w, p, t.lights);
        for (int i = 0; i < want.size(); i++) {
            BlockPos pos = want.get(i);
            BlockState desired = Blocks.LIGHT.getDefaultState().with(LightBlock.LEVEL_15, i == 0 ? 15 : 10);
            if (!w.getBlockState(pos).equals(desired)) w.setBlockState(pos, desired, Block.NOTIFY_LISTENERS);
        }
        removeLights(server, t, want);
    }

    private static List<BlockPos> computeLights(ServerWorld w, ServerPlayerEntity p, List<BlockPos> mine) {
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1f);
        BlockHitResult hit = w.raycast(new RaycastContext(eye, eye.add(look.multiply(22)),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        double reach = hit.getType() == HitResult.Type.MISS ? 22 : eye.distanceTo(hit.getPos());

        List<BlockPos> out = new ArrayList<>(2);
        BlockPos far = spot(w, eye, look, Math.max(1.0, reach - 0.6), mine);
        if (far != null) out.add(far);
        BlockPos near = spot(w, eye, look, Math.min(reach * 0.4, 7.0), mine);
        if (near != null && !near.equals(far)) out.add(near);
        return out;
    }

    private static BlockPos spot(ServerWorld w, Vec3d eye, Vec3d look, double dist, List<BlockPos> mine) {
        for (int i = 0; i < 4; i++) {
            BlockPos pos = BlockPos.ofFloored(eye.add(look.multiply(Math.max(0.5, dist - i * 0.5))));
            BlockState st = w.getBlockState(pos);
            if (st.isAir() || (st.isOf(Blocks.LIGHT) && mine.contains(pos))) return pos;
        }
        return null;
    }

    private static void removeLights(MinecraftServer server, Tracked t, List<BlockPos> keep) {
        ServerWorld w = server.getWorld(t.dim);
        if (w != null) {
            for (BlockPos pos : t.lights) {
                if (!keep.contains(pos) && w.getBlockState(pos).isOf(Blocks.LIGHT)) {
                    w.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }
        t.lights = new ArrayList<>(keep);
    }

    private static void release(MinecraftServer server, UUID id) {
        Tracked t = LIT.remove(id);
        if (t != null) removeLights(server, t, List.of());
    }
}
