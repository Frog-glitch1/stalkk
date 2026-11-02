package com.stalker;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Right-click to toggle. While on, it drains one point of durability per second (600 = 10 minutes).
 * Recharge by combining it with redstone in an anvil. It never breaks - it just goes dark.
 */
public class FlashlightItem extends Item {
    public static final int MAX_CHARGE = 600;

    public FlashlightItem(Settings settings) {
        super(settings);
    }

    public static boolean isOn(ItemStack stack) {
        return stack.hasNbt() && stack.getNbt().getBoolean("On");
    }

    public static void setOn(ItemStack stack, boolean on) {
        stack.getOrCreateNbt().putBoolean("On", on);
    }

    public static boolean hasCharge(ItemStack stack) {
        return stack.getDamage() < stack.getMaxDamage() - 1;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        boolean turnOn = !isOn(stack);
        if (turnOn && !hasCharge(stack)) {
            if (!world.isClient) {
                world.playSound(null, user.getX(), user.getY(), user.getZ(),
                        SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.6f, 1.6f);
            }
            return TypedActionResult.fail(stack);
        }
        setOn(stack, turnOn);
        if (!world.isClient) {
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.PLAYERS, 0.5f, turnOn ? 1.6f : 1.3f);
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public boolean canRepair(ItemStack stack, ItemStack ingredient) {
        return ingredient.isOf(Items.REDSTONE);
    }
}
