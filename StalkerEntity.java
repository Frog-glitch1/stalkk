package com.stalker.entity;

import com.stalker.StalkerManager;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.OpenDoorGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.UUID;

public class StalkerEntity extends HostileEntity {
    private static final TrackedData<Integer> STAGE =
            DataTracker.registerData(StalkerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> CRAWLING =
            DataTracker.registerData(StalkerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final EntityDimensions CRAWL_DIMENSIONS = EntityDimensions.fixed(0.8f, 1.8f);

    private UUID targetPlayer;
    private int apparitionTicks;

    public StalkerEntity(EntityType<? extends StalkerEntity> type, World world) {
        super(type, world);
        this.setPersistent();
        this.experiencePoints = 0;
        if (this.getNavigation() instanceof MobNavigation nav) {
            nav.setCanPathThroughDoors(true);
        }
    }

    public static DefaultAttributeContainer.Builder createStalkerAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 80.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 8.0)
                .add(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, 0.5)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.6)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 96.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new StalkerBrainGoal(this));
        this.goalSelector.add(2, new OpenDoorGoal(this, false));
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(STAGE, 0);
        this.dataTracker.startTracking(CRAWLING, false);
    }

    // ---- state ---------------------------------------------------------------

    public int getStage() { return this.dataTracker.get(STAGE); }

    public void setStage(int stage) { this.dataTracker.set(STAGE, stage); }

    public boolean isCrawling() { return this.dataTracker.get(CRAWLING); }

    /** Switches between standing (2.9 tall) and the low four-legged crawl used while hunting. */
    public void setCrawling(boolean crawl) {
        if (crawl == isCrawling()) return;
        if (!crawl) {
            Box standing = this.getType().getDimensions().getBoxAt(this.getPos());
            if (!this.getWorld().isSpaceEmpty(this, standing)) return; // no room to stand up
        }
        this.dataTracker.set(CRAWLING, crawl);
    }

    public UUID getTargetPlayer() { return targetPlayer; }

    public void setTargetPlayer(UUID id) { this.targetPlayer = id; }

    /** An apparition is a harmless nightmare copy: it only stares, then fades. */
    public boolean isApparition() { return apparitionTicks > 0; }

    public void setApparition(int ticks) { this.apparitionTicks = ticks; }

    public void vanish(int minSeconds, int maxSeconds) {
        StalkerManager.vanish(this, minSeconds, maxSeconds);
    }

    // ---- dimensions ------------------------------------------------------------

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        if (CRAWLING.equals(data)) this.calculateDimensions();
        super.onTrackedDataSet(data);
    }

    @Override
    public EntityDimensions getDimensions(EntityPose pose) {
        return isCrawling() ? CRAWL_DIMENSIONS : super.getDimensions(pose);
    }

    @Override
    protected float getActiveEyeHeight(EntityPose pose, EntityDimensions dimensions) {
        return isCrawling() ? 1.5f : 2.6f;
    }

    // ---- lifecycle -------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!this.getWorld().isClient && apparitionTicks > 0 && --apparitionTicks == 0) {
            StalkerManager.dismiss(this);
            return;
        }
        if (!this.getWorld().isClient && this.age % 40 == 0) {
            if (targetPlayer == null || this.getWorld().getPlayerByUuid(targetPlayer) == null) {
                this.discard();
            }
        }
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (isApparition()) {
            if (!this.getWorld().isClient) StalkerManager.dismiss(this);
            return false;
        }
        return super.damage(source, amount);
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        if (!this.getWorld().isClient) StalkerManager.onKilled(this);
    }

    /** The manager owns the Stalker; it is never saved with the world. */
    @Override
    public boolean shouldSave() { return false; }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) { return false; }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    // ---- sounds (it is mostly silent on purpose) --------------------------------

    @Override
    protected SoundEvent getAmbientSound() { return null; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.ENTITY_WARDEN_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.ENTITY_WARDEN_DEATH; }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.BLOCK_BONE_BLOCK_STEP, 0.35f, 0.5f + this.random.nextFloat() * 0.2f);
    }
}
