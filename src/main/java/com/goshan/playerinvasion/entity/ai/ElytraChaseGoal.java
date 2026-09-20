package com.goshan.playerinvasion.entity.ai;

import com.goshan.playerinvasion.PIConfig;
import com.goshan.playerinvasion.PlayerInvasion;
import com.goshan.playerinvasion.entity.InvaderEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumSet;

/**
 * Elytra chase: swap the chestplate for the elytra, jump, spread the wings, boost
 * with firework rockets, steer at the target, drop on it (a hit on the way down is
 * a crit), then put the chestplate back on. Used when the target is far away or
 * high up on something a tower would take too long to reach.
 */
public class ElytraChaseGoal extends Goal {

    private static final int TAKEOFF = 0;
    private static final int FLY = 1;
    private static final int LAND = 2;

    private final InvaderEntity bot;
    private int phase = -1;
    private int timer;
    private int cooldown;
    private int boostTimer;
    private LivingEntity target;

    public ElytraChaseGoal(InvaderEntity bot) {
        this.bot = bot;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    private static boolean validTarget(LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return false;
        }
        return !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    private boolean hasGear() {
        boolean elytra = bot.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA) || bot.getInventory().has(Items.ELYTRA);
        return elytra && bot.getInventory().has(Items.FIREWORK_ROCKET);
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        LivingEntity t = bot.getTarget();
        if (!PIConfig.loaded() || !PIConfig.USE_ELYTRA.get() || !validTarget(t) || bot.isEating()) {
            return false;
        }
        if (!hasGear() || !bot.onGround() || bot.isInWater() || bot.ticksSinceSawPlayer() > 40) {
            return false;
        }
        double dist = bot.distanceTo(t);
        double dy = t.getY() - bot.getY();
        if (dist < 4.0D || dist > 96.0D) {
            return false;
        }
        boolean far = dist > 20.0D;
        boolean high = dy > 6.0D;
        if (!far && !high) {
            return false;
        }
        // room to take off: three free blocks over the head
        BlockPos feet = bot.blockPosition();
        return Placing.isFree(bot.level(), feet.above(2)) && Placing.isFree(bot.level(), feet.above(3))
                && Placing.isFree(bot.level(), feet.above(4));
    }

    @Override
    public boolean canContinueToUse() {
        return phase >= 0 && (phase == LAND || validTarget(target));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        target = bot.getTarget();
        phase = TAKEOFF;
        timer = 0;
        boostTimer = 0;
        bot.setWantSprint(false);
        bot.setWantSneak(false);
        bot.getNavigation().stop();
        bot.getMoveControl().strafe(0.0F, 0.0F);
        if (bot.isUsingItem()) {
            bot.stopUsingItem();
        }
        wearElytra();
        bot.getJumpControl().jump();
        PlayerInvasion.LOGGER.debug("{} takes off on elytra towards {} ({} blocks away)", bot.getBotName(),
                target.getName().getString(), Math.round(bot.distanceTo(target)));
    }

    @Override
    public void tick() {
        timer++;
        switch (phase) {
            case TAKEOFF -> tickTakeoff();
            case FLY -> tickFly();
            case LAND -> tickLand();
            default -> {
            }
        }
    }

    private void tickTakeoff() {
        if (!bot.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
            phase = -1;
            return;
        }
        if (!bot.onGround()) {
            if (bot.getDeltaMovement().y < 0.05D) {
                bot.startGliding();
                if (bot.isFallFlying()) {
                    boost();
                    phase = FLY;
                    timer = 0;
                    return;
                }
            }
        } else if (timer % 8 == 0) {
            bot.getJumpControl().jump();
        }
        if (timer > 60) {
            phase = LAND; // never got airborne
        }
    }

    private void tickFly() {
        if (!bot.isFallFlying()) {
            phase = LAND;
            timer = 0;
            return;
        }
        if (target == null || !target.isAlive() || timer > 600) {
            bot.stopGliding();
            phase = LAND;
            timer = 0;
            return;
        }
        double dx = target.getX() - bot.getX();
        double dz = target.getZ() - bot.getZ();
        double dy = target.getEyeY() - bot.getEyeY();
        double flat = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        float pitch;
        if (flat > 10.0D) {
            // keep a little altitude while far; rockets provide the speed
            pitch = (float) -(Mth.atan2(dy + 3.0D, flat) * (180.0D / Math.PI));
            pitch = Mth.clamp(pitch, -35.0F, 40.0F);
        } else {
            pitch = (float) -(Mth.atan2(dy + 0.5D, flat) * (180.0D / Math.PI));
            pitch = Mth.clamp(pitch, -60.0F, 70.0F);
        }
        bot.setFlightAim(yaw, pitch);

        double speed = bot.getDeltaMovement().length();
        if (--boostTimer <= 0 && flat > 6.0D && (speed < 1.2D || dy > 4.0D)) {
            boost();
        }

        // dive hit on the way down
        if (bot.canReach(target) && bot.isAttackReady()) {
            bot.swing(InteractionHand.MAIN_HAND);
            bot.doHurtTarget(target);
            bot.resetAttackCooldown();
        }

        if (flat < 3.0D && dy > -4.0D) {
            PlayerInvasion.LOGGER.debug("{} drops on {} after {} ticks of flight", bot.getBotName(), target.getName().getString(), timer);
            bot.stopGliding();
            phase = LAND;
            timer = 0;
        }
    }

    private void tickLand() {
        if (bot.isFallFlying()) {
            bot.stopGliding();
        }
        if (bot.onGround() || bot.isInWater() || timer > 200) {
            phase = -1;
        }
    }

    private void boost() {
        ItemStack rockets = bot.getInventory().find(s -> s.is(Items.FIREWORK_ROCKET));
        if (rockets.isEmpty()) {
            return;
        }
        FireworkRocketEntity rocket = new FireworkRocketEntity(bot.level(), rockets, bot);
        bot.level().addFreshEntity(rocket);
        rockets.shrink(1);
        bot.playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0F, 1.0F);
        bot.swing(InteractionHand.OFF_HAND);
        boostTimer = 25 + bot.getRandom().nextInt(15);
    }

    private void wearElytra() {
        if (bot.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
            return;
        }
        ItemStack elytra = bot.getInventory().takeFirst(s -> s.is(Items.ELYTRA));
        if (elytra.isEmpty()) {
            return;
        }
        ItemStack chest = bot.getItemBySlot(EquipmentSlot.CHEST);
        bot.setItemSlot(EquipmentSlot.CHEST, elytra);
        bot.getInventory().stash(chest);
    }

    private void wearChestplate() {
        ItemStack chest = bot.getItemBySlot(EquipmentSlot.CHEST);
        if (!chest.is(Items.ELYTRA)) {
            return;
        }
        ItemStack armor = bot.getInventory().takeFirst(s -> s.getItem() instanceof ArmorItem a && a.getEquipmentSlot() == EquipmentSlot.CHEST);
        bot.setItemSlot(EquipmentSlot.CHEST, armor);
        bot.getInventory().stash(chest);
    }

    @Override
    public void stop() {
        if (bot.isFallFlying()) {
            bot.stopGliding();
        }
        if (bot.onGround() || bot.isInWater() || bot.isDeadOrDying()) {
            wearChestplate();
        }
        phase = -1;
        target = null;
        cooldown = 200 + bot.getRandom().nextInt(200);
        bot.equipBestWeapon();
    }

    /** Called from the entity tick so a bot that landed after the goal ended still gets its armor back. */
    public static void restoreChestplateIfLanded(InvaderEntity bot) {
        if (bot.onGround() && !bot.isFallFlying() && bot.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
            ItemStack armor = bot.getInventory().takeFirst(s -> s.getItem() instanceof ArmorItem a && a.getEquipmentSlot() == EquipmentSlot.CHEST);
            if (!armor.isEmpty()) {
                ItemStack elytra = bot.getItemBySlot(EquipmentSlot.CHEST);
                bot.setItemSlot(EquipmentSlot.CHEST, armor);
                bot.getInventory().stash(elytra);
            }
        }
    }
}
