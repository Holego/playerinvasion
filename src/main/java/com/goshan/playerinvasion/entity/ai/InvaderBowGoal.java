package com.goshan.playerinvasion.entity.ai;

import com.goshan.playerinvasion.PIConfig;
import com.goshan.playerinvasion.entity.InvaderEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumSet;

/**
 * Bow at range: draw, lead the shot a little, release, strafe. Switches back to
 * the sword as soon as the target gets close. Adapted from vanilla's
 * RangedBowAttackGoal without the Monster requirement.
 */
public class InvaderBowGoal extends Goal {

    private static final double START_RANGE = 12.0D;
    private static final double STOP_RANGE = 5.0D;

    private final InvaderEntity bot;
    private int seeTime;
    private int attackDelay;
    private boolean strafingClockwise;
    private boolean strafingBackwards;
    private int strafingTime = -1;

    public InvaderBowGoal(InvaderEntity bot) {
        this.bot = bot;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean hasBowAndArrows() {
        boolean bow = bot.getMainHandItem().is(Items.BOW) || bot.getInventory().has(Items.BOW);
        return bow && bot.getInventory().has(Items.ARROW);
    }

    private static boolean validTarget(LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return false;
        }
        return !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = bot.getTarget();
        if (!validTarget(target) || bot.isEating() || bot.isFallFlying() || !PIConfig.loaded() || !PIConfig.USE_BOW.get()) {
            return false;
        }
        if (!hasBowAndArrows() || !bot.getSensing().hasLineOfSight(target)) {
            return false;
        }
        return bot.distanceTo(target) > START_RANGE;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = bot.getTarget();
        if (!validTarget(target) || bot.isEating() || bot.isFallFlying() || !hasBowAndArrows()) {
            return false;
        }
        return bot.distanceTo(target) > STOP_RANGE && bot.ticksSinceSawPlayer() < 100;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        bot.setWantSprint(false);
        bot.holdFromInventory(s -> s.is(Items.BOW));
        seeTime = 0;
        attackDelay = 10;
        strafingTime = -1;
    }

    @Override
    public void stop() {
        if (bot.isUsingItem() && bot.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            bot.stopUsingItem();
        }
        bot.setDrawingBow(false);
        bot.getNavigation().stop();
        bot.getMoveControl().strafe(0.0F, 0.0F);
        if (!bot.isEating()) {
            bot.equipBestWeapon();
        }
    }

    @Override
    public void tick() {
        LivingEntity target = bot.getTarget();
        if (target == null) {
            return;
        }
        double distSqr = bot.distanceToSqr(target);
        boolean canSee = bot.getSensing().hasLineOfSight(target);
        if (canSee) {
            seeTime++;
        } else {
            seeTime = 0;
        }

        if (distSqr > 20.0D * 20.0D || seeTime < 5) {
            bot.getMoveControl().strafe(0.0F, 0.0F);
            bot.getNavigation().moveTo(target, 1.0D);
            strafingTime = -1;
        } else {
            bot.getNavigation().stop();
            strafingTime++;
        }

        if (strafingTime >= 20) {
            if (bot.getRandom().nextFloat() < 0.3F) {
                strafingClockwise = !strafingClockwise;
            }
            if (bot.getRandom().nextFloat() < 0.3F) {
                strafingBackwards = !strafingBackwards;
            }
            strafingTime = 0;
        }
        if (strafingTime > -1) {
            if (distSqr > 15.0D * 15.0D * 0.75D) {
                strafingBackwards = false;
            } else if (distSqr < 15.0D * 15.0D * 0.25D) {
                strafingBackwards = true;
            }
            bot.getMoveControl().strafe(strafingBackwards ? -0.5F : 0.5F, strafingClockwise ? 0.5F : -0.5F);
            bot.lookAt(target, 30.0F, 30.0F);
        } else {
            bot.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }

        if (!bot.getMainHandItem().is(Items.BOW)) {
            if (!bot.holdFromInventory(s -> s.is(Items.BOW))) {
                return;
            }
        }

        if (bot.isUsingItem() && bot.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            if (!canSee && seeTime < -60) {
                bot.stopUsingItem();
                bot.setDrawingBow(false);
            } else if (canSee) {
                int drawn = bot.getTicksUsingItem();
                if (drawn >= 20) {
                    bot.stopUsingItem();
                    bot.setDrawingBow(false);
                    shoot(target, BowItem.getPowerForTime(drawn));
                    attackDelay = 20 + bot.getRandom().nextInt(20);
                }
            }
        } else if (--attackDelay <= 0 && seeTime >= 5) {
            bot.startUsingItem(InteractionHand.MAIN_HAND);
            bot.setDrawingBow(true);
        }
    }

    private void shoot(LivingEntity target, float power) {
        ItemStack arrowStack = bot.getInventory().find(s -> s.is(Items.ARROW));
        if (arrowStack.isEmpty()) {
            return;
        }
        AbstractArrow arrow = ProjectileUtil.getMobArrow(bot, arrowStack, power);
        arrow.pickup = AbstractArrow.Pickup.ALLOWED;
        if (power >= 1.0F) {
            arrow.setCritArrow(true);
        }
        double dx = target.getX() - bot.getX();
        double dy = target.getY(0.3333333D) - arrow.getY();
        double dz = target.getZ() - bot.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + flat * 0.06D, dz, power * 3.0F, 2.0F);
        bot.playSound(SoundEvents.ARROW_SHOOT, 1.0F, 1.0F / (bot.getRandom().nextFloat() * 0.4F + 1.2F) + power * 0.5F);
        bot.level().addFreshEntity(arrow);
        arrowStack.shrink(1);
        ItemStack bow = bot.getMainHandItem();
        if (bow.is(Items.BOW)) {
            bow.hurtAndBreak(1, bot, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        }
    }
}
