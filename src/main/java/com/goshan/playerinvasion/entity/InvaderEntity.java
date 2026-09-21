package com.goshan.playerinvasion.entity;

import com.goshan.playerinvasion.PIConfig;
import com.goshan.playerinvasion.entity.ai.CombatTricksGoal;
import com.goshan.playerinvasion.entity.ai.EatGoldenAppleGoal;
import com.goshan.playerinvasion.entity.ai.ElytraChaseGoal;
import com.goshan.playerinvasion.entity.ai.HuntGoal;
import com.goshan.playerinvasion.entity.ai.InvaderBowGoal;
import com.goshan.playerinvasion.entity.ai.InvaderMeleeGoal;
import com.goshan.playerinvasion.entity.ai.ObstacleSolver;
import com.goshan.playerinvasion.invasion.BotChat;
import com.goshan.playerinvasion.invasion.InvasionManager;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * A fake player. Server side it is an ordinary pathfinding mob with a player's
 * numbers (20 hp, 3-block reach, player walking and sprinting speed, attack
 * cooldown from the weapon), a real inventory and a set of PvP goals. Client side
 * it is rendered with the player model and the skin of the account whose
 * nickname it borrowed.
 */
public class InvaderEntity extends PathfinderMob {

    private static final EntityDataAccessor<CompoundTag> DATA_PROFILE =
            SynchedEntityData.defineId(InvaderEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Integer> DATA_TIER =
            SynchedEntityData.defineId(InvaderEntity.class, EntityDataSerializers.INT);

    private static final EntityDimensions CROUCH_DIMENSIONS = EntityDimensions.scalable(0.6F, 1.5F);
    private static final EntityDimensions FLYING_DIMENSIONS = EntityDimensions.scalable(0.6F, 0.6F);

    /** Player reach in survival: 3 blocks from the eyes to the target's hitbox. */
    public static final double MELEE_REACH = 3.0D;
    /** A player walks at ~4.3 m/s. Mob speed is applied squared, so sqrt(0.1). */
    public static final double BASE_SPEED = 0.316D;
    /** Sprint attribute modifier is +30% (squared to +69%); scale it back to ~6 m/s. */
    public static final double SPRINT_NAV_MODIFIER = 0.9D;
    public static final double SNEAK_NAV_MODIFIER = 0.55D;

    private final InvaderInventory inventory = new InvaderInventory(36);
    private final ObstacleSolver obstacleSolver = new ObstacleSolver(this);

    // --- identity (server) ---------------------------------------------------
    @Nullable
    private UUID personId;
    private String botName = "";
    @Nullable
    private GameProfile profile;

    // --- combat state (server) -----------------------------------------------
    private int attackCooldown;
    private int shieldDisabledUntil;
    private int lastHurtTick = -1000;
    private int regenTimer;
    private int noTargetTicks;
    private int sinceLastSeenPlayer;
    private boolean hadTarget;
    private boolean lowHealthSaid;
    private boolean eating;
    private boolean drawingBow;
    private boolean wantSprint;
    private boolean wantSneak;
    private int sprintResumeAt;
    private int idleChatTimer = -1;
    private boolean flightAim;
    private float flightYaw;
    private float flightPitch;

    // --- client cache ----------------------------------------------------------
    public boolean clientProfileDirty = true;
    @Nullable
    public Object clientSkin;
    public boolean clientSlim;

    public InvaderEntity(EntityType<? extends InvaderEntity> type, Level level) {
        super(type, level);
        this.setCanPickUpLoot(true);
        this.setPersistenceRequired();
        this.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, 16.0F);
        this.setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.LAVA, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.WATER, 2.0F);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            // > 1.0 makes vanilla drop the item without randomly wrecking its durability
            this.setDropChance(slot, 2.0F);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, BASE_SPEED)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.ATTACK_SPEED, 4.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0D);
    }

    // ------------------------------------------------------------------------
    // Setup
    // ------------------------------------------------------------------------

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_PROFILE, new CompoundTag());
        this.entityData.define(DATA_TIER, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new EatGoldenAppleGoal(this));
        this.goalSelector.addGoal(2, new ElytraChaseGoal(this));
        this.goalSelector.addGoal(3, new CombatTricksGoal(this));
        this.goalSelector.addGoal(4, new InvaderBowGoal(this));
        this.goalSelector.addGoal(5, new InvaderMeleeGoal(this));
        this.goalSelector.addGoal(6, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(7, new HuntGoal(this));
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, InvaderEntity.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, null));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        GroundPathNavigation nav = new GroundPathNavigation(this, level);
        nav.setCanOpenDoors(true);
        nav.setCanPassDoors(true);
        nav.setCanFloat(true);
        return nav;
    }

    // ------------------------------------------------------------------------
    // Identity
    // ------------------------------------------------------------------------

    /** A bot created by /summon or another mod gets a name, a kit and a tab-list entry like everyone else. */
    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData spawnData, @Nullable CompoundTag tag) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, spawnData, tag);
        if (personId == null) {
            InvasionManager manager = InvasionManager.current();
            if (manager != null) {
                manager.adopt(this);
            }
        }
        return result;
    }

    public void setIdentity(UUID personId, GameProfile profile) {
        this.personId = personId;
        this.profile = profile;
        this.botName = profile.getName();
        this.entityData.set(DATA_PROFILE, NbtUtils.writeGameProfile(new CompoundTag(), profile));
    }

    @Nullable
    public UUID getPersonId() {
        return personId;
    }

    public String getBotName() {
        return botName;
    }

    @Nullable
    public GameProfile getProfile() {
        if (profile == null) {
            CompoundTag tag = this.entityData.get(DATA_PROFILE);
            if (!tag.isEmpty()) {
                profile = NbtUtils.readGameProfile(tag);
                if (profile != null && botName.isEmpty()) {
                    botName = profile.getName();
                }
            }
        }
        return profile;
    }

    @Override
    public Component getName() {
        if (!botName.isEmpty()) {
            return Component.literal(botName);
        }
        GameProfile p = getProfile();
        return p != null && p.getName() != null ? Component.literal(p.getName()) : super.getName();
    }

    public int getTier() {
        return this.entityData.get(DATA_TIER);
    }

    public void setTier(int tier) {
        tier = Loadouts.clamp(tier);
        this.entityData.set(DATA_TIER, tier);
        Loadouts.apply(this, tier);
    }

    public InvaderInventory getInventory() {
        return inventory;
    }

    /** Towering / bridging / digging state, shared by the chase and hunt goals so a goal switch does not restart a half-built tower. */
    public ObstacleSolver getObstacleSolver() {
        return obstacleSolver;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_PROFILE.equals(key)) {
            this.profile = null;
            this.clientProfileDirty = true;
        }
    }

    // ------------------------------------------------------------------------
    // Persistence (only used for cross-dimension copies; chunks never save us)
    // ------------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (personId != null) {
            tag.putUUID("PersonId", personId);
        }
        tag.putString("BotName", botName);
        tag.put("Profile", this.entityData.get(DATA_PROFILE).copy());
        tag.putInt("Tier", getTier());
        tag.put("Bag", inventory.save());
        tag.putInt("SinceLastSeenPlayer", sinceLastSeenPlayer);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("PersonId")) {
            personId = tag.getUUID("PersonId");
        }
        botName = tag.getString("BotName");
        if (tag.contains("Profile", 10)) {
            this.entityData.set(DATA_PROFILE, tag.getCompound("Profile"));
            profile = null;
        }
        this.entityData.set(DATA_TIER, tag.getInt("Tier"));
        if (tag.contains("Bag", 9)) {
            inventory.load(tag.getList("Bag", 10));
        }
        sinceLastSeenPlayer = tag.getInt("SinceLastSeenPlayer");
    }

    @Override
    public boolean shouldBeSaved() {
        // Never persisted inside chunks: if our chunk unloads we "log off" instead.
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public void checkDespawn() {
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    // ------------------------------------------------------------------------
    // Player-like body
    // ------------------------------------------------------------------------

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        if (pose == Pose.CROUCHING) {
            return CROUCH_DIMENSIONS;
        }
        if (pose == Pose.FALL_FLYING || pose == Pose.SWIMMING) {
            return FLYING_DIMENSIONS;
        }
        return super.getDimensions(pose);
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return switch (pose) {
            case CROUCHING -> 1.27F;
            case FALL_FLYING, SWIMMING -> 0.4F;
            default -> 1.62F;
        };
    }

    // --- elytra ------------------------------------------------------------

    /** Spreads the wings (needs an elytra in the chest slot and to be airborne, like a player). */
    public void startGliding() {
        setSharedFlag(7, true);
    }

    public void stopGliding() {
        setSharedFlag(7, false);
        flightAim = false;
    }

    /** Where to steer while gliding; applied right before the physics step so the look control cannot override it. */
    public void setFlightAim(float yaw, float pitch) {
        flightAim = true;
        flightYaw = yaw;
        flightPitch = pitch;
    }

    @Override
    public void travel(Vec3 input) {
        if (flightAim && isFallFlying()) {
            setYRot(flightYaw);
            setXRot(flightPitch);
            yBodyRot = flightYaw;
            yHeadRot = flightYaw;
        } else if ((isInWater() || isInLava()) && !level().isClientSide) {
            // mobs feed their (small) speed attribute as the movement input and crawl through water;
            // a player pushes the stick all the way, so normalise the horizontal input like a player
            double len = Math.sqrt(input.x * input.x + input.z * input.z);
            if (len > 1.0E-4D) {
                input = new Vec3(input.x / len, input.y, input.z / len);
            }
        }
        super.travel(input);
    }


    /** The shield-blocking arc and anything else that asks where we "look" uses the head, like a player. */
    @Override
    public float getViewYRot(float partialTicks) {
        return this.yHeadRot;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.PLAYERS;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    public Fallsounds getFallSounds() {
        return new Fallsounds(SoundEvents.PLAYER_SMALL_FALL, SoundEvents.PLAYER_BIG_FALL);
    }

    @Override
    public int getExperienceReward() {
        return Math.min(100, 7 * (1 + getTier() * 6));
    }

    // ------------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || isDeadOrDying()) {
            return;
        }
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        tickRegen();
        tickEating();
        tickPose();
        tickOffhand();
        if (tickCount % 10 == 0) {
            ElytraChaseGoal.restoreChestplateIfLanded(this);
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        LivingEntity target = getTarget();

        if (target != null) {
            noTargetTicks = 0;
            if (!hadTarget) {
                hadTarget = true;
                onTargetAcquired(target);
            }
            if (getSensing().hasLineOfSight(target)) {
                sinceLastSeenPlayer = 0;
            } else {
                sinceLastSeenPlayer++;
            }
        } else {
            noTargetTicks++;
            sinceLastSeenPlayer++;
            hadTarget = false;
            lowHealthSaid = false;
        }

        tickShield(target);
        tickSprint(target);
        tickIdleChat();
    }

    private void onTargetAcquired(LivingEntity target) {
        InvasionManager manager = InvasionManager.current();
        if (manager != null && target instanceof Player player) {
            manager.onBotSpotted(this, player);
        }
    }

    private void tickRegen() {
        if (getHealth() < getMaxHealth() && ++regenTimer >= 40 && tickCount - lastHurtTick > 60) {
            regenTimer = 0;
            heal(1.0F);
        }
    }

    private void tickPose() {
        Pose wanted;
        if (isFallFlying()) {
            wanted = Pose.FALL_FLYING;
        } else if (isSwimming()) {
            wanted = Pose.SWIMMING;
        } else {
            wanted = wantSneak && onGround() && !isInWater() ? Pose.CROUCHING : Pose.STANDING;
        }
        if (getPose() != wanted) {
            setPose(wanted);
            refreshDimensions();
        }
        if (isShiftKeyDown() != wantSneak) {
            setShiftKeyDown(wantSneak);
        }
    }

    /** A popped totem is replaced from the bag; otherwise a spare shield goes into the empty hand. */
    private void tickOffhand() {
        if (!getOffhandItem().isEmpty() || tickCount % 10 != 0) {
            return;
        }
        ItemStack spare = inventory.takeFirst(s -> s.is(Items.TOTEM_OF_UNDYING));
        if (spare.isEmpty()) {
            spare = inventory.takeFirst(s -> s.is(Items.SHIELD));
        }
        if (!spare.isEmpty()) {
            setItemSlot(EquipmentSlot.OFFHAND, spare);
        }
    }

    private void tickSprint(@Nullable LivingEntity target) {
        boolean sprint = wantSprint && target != null && !isUsingItem() && !wantSneak
                && tickCount >= sprintResumeAt && getHealth() > 0;
        if (isSprinting() != sprint) {
            setSprinting(sprint);
        }
    }

    private void tickIdleChat() {
        if (!PIConfig.loaded() || PIConfig.IDLE_CHAT_MINUTES.get() <= 0) {
            return;
        }
        if (idleChatTimer < 0) {
            idleChatTimer = randomIdleChatDelay();
        }
        if (--idleChatTimer <= 0) {
            idleChatTimer = randomIdleChatDelay();
            if (getTarget() == null) {
                say(BotChat.Occasion.IDLE);
            }
        }
    }

    private int randomIdleChatDelay() {
        int avg = PIConfig.IDLE_CHAT_MINUTES.get() * 1200;
        return avg / 2 + random.nextInt(Math.max(1, avg));
    }

    // ------------------------------------------------------------------------
    // Shield
    // ------------------------------------------------------------------------

    private void tickShield(@Nullable LivingEntity target) {
        boolean holdingShield = getOffhandItem().is(Items.SHIELD);
        boolean blockingNow = isUsingItem() && getUsedItemHand() == InteractionHand.OFF_HAND;
        if (!holdingShield || eating || drawingBow || tickCount < shieldDisabledUntil) {
            if (blockingNow) {
                stopUsingItem();
            }
            return;
        }

        boolean wantBlock = false;
        if (target != null && distanceToSqr(target) < 20.0D && attackCooldown > 2) {
            Vec3 toMe = position().subtract(target.position());
            toMe = new Vec3(toMe.x, 0.0D, toMe.z).normalize();
            Vec3 look = target.getViewVector(1.0F);
            boolean facingMe = new Vec3(look.x, 0.0D, look.z).normalize().dot(toMe) > 0.6D;
            boolean axeUser = target.getMainHandItem().getItem() instanceof AxeItem;
            wantBlock = facingMe && (!axeUser || random.nextInt(4) == 0);
        }
        if (!wantBlock) {
            wantBlock = arrowIncoming();
        }

        if (wantBlock && !isUsingItem()) {
            startUsingItem(InteractionHand.OFF_HAND);
        } else if (!wantBlock && blockingNow) {
            stopUsingItem();
        }
    }

    private boolean arrowIncoming() {
        List<AbstractArrow> arrows = level().getEntitiesOfClass(AbstractArrow.class, getBoundingBox().inflate(12.0D),
                a -> a.getOwner() != this && !a.onGround());
        for (AbstractArrow arrow : arrows) {
            Vec3 vel = arrow.getDeltaMovement();
            if (vel.lengthSqr() < 0.05D) {
                continue;
            }
            Vec3 toMe = getEyePosition().subtract(arrow.position());
            if (vel.normalize().dot(toMe.normalize()) > 0.85D) {
                return true;
            }
        }
        return false;
    }

    public boolean isShieldDisabled() {
        return tickCount < shieldDisabledUntil;
    }

    @Override
    protected void blockUsingShield(LivingEntity attacker) {
        super.blockUsingShield(attacker);
        if (attacker.getMainHandItem().canDisableShield(this.useItem, this, attacker)) {
            stopUsingItem();
            shieldDisabledUntil = tickCount + 100;
            level().broadcastEntityEvent(this, (byte) 30);
        }
    }

    @Override
    protected void hurtCurrentlyUsedShield(float amount) {
        if (!this.useItem.is(Items.SHIELD) || amount < 3.0F) {
            return;
        }
        int damage = 1 + Mth.floor(amount);
        InteractionHand hand = getUsedItemHand();
        this.useItem.hurtAndBreak(damage, this, e -> e.broadcastBreakEvent(hand));
        if (this.useItem.isEmpty()) {
            setItemInHand(hand, ItemStack.EMPTY);
            this.useItem = ItemStack.EMPTY;
            playSound(SoundEvents.SHIELD_BREAK, 0.8F, 0.8F + level().random.nextFloat() * 0.4F);
        }
    }

    @Override
    protected void hurtArmor(DamageSource source, float amount) {
        if (amount <= 0.0F) {
            return;
        }
        amount /= 4.0F;
        if (amount < 1.0F) {
            amount = 1.0F;
        }
        int points = (int) amount;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = getItemBySlot(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem)) {
                continue;
            }
            if (source.is(DamageTypeTags.IS_FIRE) && stack.getItem().isFireResistant()) {
                continue;
            }
            stack.hurtAndBreak(points, this, e -> e.broadcastBreakEvent(slot));
        }
    }

    // ------------------------------------------------------------------------
    // Attacking like a player
    // ------------------------------------------------------------------------

    public boolean isAttackReady() {
        return attackCooldown <= 0;
    }

    public int getAttackCooldownTicks() {
        double speed = getAttributeValue(Attributes.ATTACK_SPEED);
        return (int) Math.ceil(20.0D / Math.max(0.5D, speed));
    }

    public void resetAttackCooldown() {
        attackCooldown = getAttackCooldownTicks();
    }

    /** Distance from our eyes to the closest point of the target's hitbox. */
    public double reachDistance(Entity target) {
        Vec3 eye = getEyePosition();
        AABB box = target.getBoundingBox();
        double dx = Mth.clamp(eye.x, box.minX, box.maxX) - eye.x;
        double dy = Mth.clamp(eye.y, box.minY, box.maxY) - eye.y;
        double dz = Mth.clamp(eye.z, box.minZ, box.maxZ) - eye.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public boolean canReach(Entity target) {
        return reachDistance(target) <= MELEE_REACH;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        float knockback = (float) getAttributeValue(Attributes.ATTACK_KNOCKBACK);
        ItemStack weapon = getMainHandItem();
        LivingEntity living = target instanceof LivingEntity l ? l : null;
        if (living != null) {
            damage += EnchantmentHelper.getDamageBonus(weapon, living.getMobType());
            knockback += EnchantmentHelper.getKnockbackBonus(this);
        }

        boolean crit = living != null && fallDistance > 0.0F && !onGround() && !onClimbable() && !isInWater()
                && !hasEffect(MobEffects.BLINDNESS) && !isPassenger() && !isSprinting();
        if (crit) {
            damage *= 1.5F;
        }
        boolean sprintHit = isSprinting();
        if (sprintHit) {
            knockback += 1.0F;
        }

        int fire = EnchantmentHelper.getFireAspect(this);
        if (fire > 0) {
            target.setSecondsOnFire(fire * 4);
        }

        boolean hurt = target.hurt(damageSources().mobAttack(this), damage);
        if (hurt) {
            if (knockback > 0.0F && living != null) {
                living.knockback(knockback * 0.5F,
                        Mth.sin(getYRot() * ((float) Math.PI / 180F)),
                        -Mth.cos(getYRot() * ((float) Math.PI / 180F)));
                setDeltaMovement(getDeltaMovement().multiply(0.6D, 1.0D, 0.6D));
            }
            if (crit) {
                if (level() instanceof ServerLevel serverLevel) {
                    serverLevel.getChunkSource().broadcastAndSend(target,
                            new ClientboundAnimatePacket(target, ClientboundAnimatePacket.CRITICAL_HIT));
                }
                playSound(SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 1.0F);
            } else if (sprintHit) {
                playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 1.0F);
            } else {
                playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 1.0F);
            }
            if (living != null) {
                doEnchantDamageEffects(this, living);
                if (!weapon.isEmpty()) {
                    weapon.getItem().hurtEnemy(weapon, living, this);
                }
            }
            setLastHurtMob(target);
            if (sprintHit) {
                // "W-tap": drop the sprint for a moment, the goal picks it up again
                setSprinting(false);
                sprintResumeAt = tickCount + 4;
            }
        } else {
            playSound(SoundEvents.PLAYER_ATTACK_NODAMAGE, 1.0F, 1.0F);
        }
        return hurt;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) {
            return super.hurt(source, amount);
        }
        boolean result = super.hurt(source, amount);
        if (result && !isInvulnerableTo(source)) {
            lastHurtTick = tickCount;
            if (!lowHealthSaid && getHealth() <= 7.0F && isAlive()) {
                lowHealthSaid = true;
                say(BotChat.Occasion.LOW_HEALTH);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------------
    // Hands, inventory, consumables
    // ------------------------------------------------------------------------

    public void setWantSprint(boolean sprint) {
        this.wantSprint = sprint;
    }

    public void setWantSneak(boolean sneak) {
        this.wantSneak = sneak;
    }

    public boolean isEating() {
        return eating;
    }

    public void setDrawingBow(boolean drawing) {
        this.drawingBow = drawing;
    }

    public boolean isDrawingBow() {
        return drawingBow;
    }

    public int ticksSinceSawPlayer() {
        return sinceLastSeenPlayer;
    }

    public int ticksWithoutTarget() {
        return noTargetTicks;
    }

    public double navSpeed() {
        if (wantSneak) {
            return SNEAK_NAV_MODIFIER;
        }
        return isSprinting() ? SPRINT_NAV_MODIFIER : 1.0D;
    }

    /** Puts the first inventory stack matching the predicate into the main hand; the current item goes to the bag. */
    public boolean holdFromInventory(Predicate<ItemStack> predicate) {
        if (predicate.test(getMainHandItem())) {
            return true;
        }
        ItemStack stack = inventory.takeFirst(predicate);
        if (stack.isEmpty()) {
            return false;
        }
        ItemStack current = getMainHandItem();
        setItemSlot(EquipmentSlot.MAINHAND, stack);
        inventory.stash(current);
        return true;
    }

    /** Best melee weapon (by attack damage) from hand + bag goes to the main hand. */
    public void equipBestWeapon() {
        ItemStack current = getMainHandItem();
        double best = isWeapon(current) ? weaponDamage(current) : -1.0D;
        ItemStack candidate = ItemStack.EMPTY;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.get(i);
            if (isWeapon(stack) && weaponDamage(stack) > best) {
                best = weaponDamage(stack);
                candidate = stack;
            }
        }
        if (!candidate.isEmpty()) {
            ItemStack found = candidate;
            holdFromInventory(s -> s == found);
        } else if (!isWeapon(current)) {
            // holding a bucket, apple, block... put it away and take any weapon
            ItemStack any = inventory.takeFirst(InvaderEntity::isWeapon);
            setItemSlot(EquipmentSlot.MAINHAND, any);
            inventory.stash(current);
        }
    }

    public static boolean isWeapon(ItemStack stack) {
        return stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem;
    }

    private static double weaponDamage(ItemStack stack) {
        double d = 0.0D;
        var mods = stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE);
        for (var m : mods) {
            d += m.getAmount();
        }
        return d + EnchantmentHelper.getDamageBonus(stack, net.minecraft.world.entity.MobType.UNDEFINED);
    }

    public boolean hasAxe() {
        return getMainHandItem().getItem() instanceof AxeItem || !inventory.find(s -> s.getItem() instanceof AxeItem).isEmpty();
    }

    public boolean startEating(ItemStack food) {
        if (eating || isUsingItem() || food.isEmpty()) {
            return false;
        }
        ItemStack one = food.copy();
        one.setCount(1);
        food.shrink(1);
        ItemStack current = getMainHandItem();
        setItemSlot(EquipmentSlot.MAINHAND, one);
        inventory.stash(current);
        startUsingItem(InteractionHand.MAIN_HAND);
        eating = isUsingItem();
        if (!eating) {
            // could not start (event cancelled?) - give the apple back
            inventory.add(one);
            equipBestWeapon();
        }
        return eating;
    }

    private void tickEating() {
        if (eating && !isUsingItem()) {
            eating = false;
            ItemStack left = getMainHandItem();
            if (!left.isEmpty() && left.isEdible()) {
                inventory.add(left);
                setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }
            equipBestWeapon();
        }
    }

    public void swingAndPlaySound(SoundEvent sound) {
        swing(InteractionHand.MAIN_HAND);
        if (sound != null) {
            playSound(sound, 1.0F, 1.0F);
        }
    }

    // ------------------------------------------------------------------------
    // Loot pickup
    // ------------------------------------------------------------------------

    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        if (!canPickUpLoot() || !PIConfig.loaded() || !PIConfig.PICK_UP_LOOT.get()) {
            return false;
        }
        EquipmentSlot slot = getEquipmentSlotForItem(stack);
        if (slot.getType() == EquipmentSlot.Type.ARMOR && canReplaceCurrentItem(stack, getItemBySlot(slot))) {
            return true;
        }
        return inventory.canAdd(stack);
    }

    @Override
    protected void pickUpItem(ItemEntity itemEntity) {
        ItemStack stack = itemEntity.getItem();
        int count = stack.getCount();
        EquipmentSlot slot = getEquipmentSlotForItem(stack);
        if (slot.getType() == EquipmentSlot.Type.ARMOR && canReplaceCurrentItem(stack, getItemBySlot(slot))) {
            ItemStack old = getItemBySlot(slot);
            setItemSlot(slot, stack.copy());
            inventory.stash(old);
            stack.setCount(0);
        } else if (isWeapon(stack) && weaponDamage(stack) > weaponDamage(getMainHandItem())) {
            ItemStack old = getMainHandItem();
            setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
            inventory.stash(old);
            stack.setCount(0);
        } else {
            inventory.add(stack);
        }
        int taken = count - stack.getCount();
        if (taken > 0) {
            onItemPickup(itemEntity);
            take(itemEntity, taken);
            if (stack.isEmpty()) {
                itemEntity.discard();
            }
        }
    }

    // ------------------------------------------------------------------------
    // Death: everything drops, like a real player
    // ------------------------------------------------------------------------

    @Override
    public void die(DamageSource cause) {
        Component deathMessage = null;
        if (!level().isClientSide && !this.dead) {
            deathMessage = getCombatTracker().getDeathMessage();
        }
        boolean wasDead = this.dead;
        super.die(cause);
        if (!level().isClientSide && !wasDead && this.dead) {
            stopUsingItem();
            InvasionManager manager = InvasionManager.current();
            if (manager != null) {
                manager.onBotDied(this, cause, deathMessage);
            }
        }
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = getItemBySlot(slot);
            if (!stack.isEmpty() && !EnchantmentHelper.hasVanishingCurse(stack)) {
                spawnAtLocation(stack);
            }
            setItemSlot(slot, ItemStack.EMPTY);
        }
        inventory.dropAll(this);
    }

    @Override
    protected void dropEquipment() {
    }

    // ------------------------------------------------------------------------
    // Chat
    // ------------------------------------------------------------------------

    public void say(BotChat.Occasion occasion) {
        InvasionManager manager = InvasionManager.current();
        if (manager != null) {
            manager.botSay(this, occasion);
        }
    }

    /** Nearest real, survival-mode player in range - used to fill {player} in chat lines and to hunt. */
    @Nullable
    public Player nearestRealPlayer(double range) {
        Player best = null;
        double bestDist = range * range;
        for (Player player : level().players()) {
            if (player.isSpectator() || player.isCreative() || !player.isAlive()) {
                continue;
            }
            double d = distanceToSqr(player);
            if (d < bestDist) {
                bestDist = d;
                best = player;
            }
        }
        return best;
    }

    public BlockPos feetPos() {
        return blockPosition();
    }
}
