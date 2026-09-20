package com.goshan.playerinvasion.entity.ai;

import com.goshan.playerinvasion.entity.InvaderEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Sword-and-shield PvP the way a decent player does it: sprint in, strafe around
 * the target, hit on full cooldown, jump for the occasional crit, swap to the axe
 * when the target hides behind a shield, and when the target is unreachable -
 * tower up, bridge over or mine through (see {@link ObstacleSolver}).
 */
public class InvaderMeleeGoal extends Goal {

    private static final double CLOSE_RANGE = 3.2D;

    private final InvaderEntity bot;
    private int pathTimer;
    private int strafeTimer;
    private float strafeDir = 1.0F;
    private int jumpCritTimer;
    private boolean strafing;

    public InvaderMeleeGoal(InvaderEntity bot) {
        this.bot = bot;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean validTarget(LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return false;
        }
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        return true;
    }

    @Override
    public boolean canUse() {
        return validTarget(bot.getTarget()) && !bot.isEating() && !bot.isFallFlying();
    }

    @Override
    public boolean canContinueToUse() {
        return validTarget(bot.getTarget()) && !bot.isEating() && !bot.isFallFlying();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        bot.setWantSprint(true);
        bot.setWantSneak(false);
        pathTimer = 0;
        strafing = false;
    }

    @Override
    public void stop() {
        bot.setWantSprint(false);
        bot.getNavigation().stop();
        bot.getMoveControl().strafe(0.0F, 0.0F);
        if (bot.isUsingItem() && bot.getUsedItemHand() == InteractionHand.MAIN_HAND && !bot.isEating()) {
            bot.stopUsingItem();
        }
    }

    @Override
    public void tick() {
        LivingEntity target = bot.getTarget();
        if (target == null) {
            return;
        }
        bot.getLookControl().setLookAt(target, 30.0F, 30.0F);

        double dist = bot.distanceTo(target);
        boolean canSee = bot.getSensing().hasLineOfSight(target);
        boolean close = dist <= CLOSE_RANGE && canSee;

        chooseWeapon(target);

        if (bot.getObstacleSolver().tick(target.position(), close || bot.canReach(target))) {
            strafing = false;
            tryAttack(target);
            return;
        }

        if (close) {
            strafe(target, dist);
        } else {
            approach(target);
        }
        tryAttack(target);
    }

    private void chooseWeapon(LivingEntity target) {
        if (bot.isUsingItem() && bot.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            return;
        }
        boolean holdingSword = bot.getMainHandItem().getItem() instanceof SwordItem;
        boolean holdingAxe = bot.getMainHandItem().getItem() instanceof AxeItem;
        if (target.isBlocking() && bot.hasAxe()) {
            if (!holdingAxe) {
                bot.holdFromInventory(s -> s.getItem() instanceof AxeItem);
            }
        } else if (!holdingSword && !bot.getObstacleSolver().isBusy()) {
            if (!bot.holdFromInventory(s -> s.getItem() instanceof SwordItem) && !InvaderEntity.isWeapon(bot.getMainHandItem())) {
                bot.equipBestWeapon();
            }
        }
    }

    private void approach(LivingEntity target) {
        if (strafing) {
            strafing = false;
            bot.getMoveControl().strafe(0.0F, 0.0F);
        }
        if (--pathTimer <= 0) {
            pathTimer = 4 + bot.getRandom().nextInt(4);
            bot.getNavigation().moveTo(target, bot.navSpeed());
        }
    }

    private void strafe(LivingEntity target, double dist) {
        if (!strafing) {
            strafing = true;
            bot.getNavigation().stop();
        }
        if (--strafeTimer <= 0) {
            strafeTimer = 8 + bot.getRandom().nextInt(12);
            strafeDir = bot.getRandom().nextBoolean() ? 1.0F : -1.0F;
            if (bot.getRandom().nextInt(4) == 0) {
                strafeDir = 0.0F;
            }
        }
        float forward;
        if (dist > 2.4D) {
            forward = 1.0F;
        } else if (dist < 1.3D) {
            forward = -0.4F;
        } else {
            forward = 0.2F;
        }
        bot.getMoveControl().strafe(forward, strafeDir * 0.7F);
        // keep the body pointed at the target so the strafe actually circles them
        Vec3 toTarget = target.position().subtract(bot.position());
        float yaw = (float) (Math.atan2(toTarget.z, toTarget.x) * (180.0D / Math.PI)) - 90.0F;
        bot.setYRot(yaw);
        bot.yBodyRot = yaw;
    }

    private void tryAttack(LivingEntity target) {
        if (jumpCritTimer > 0) {
            jumpCritTimer--;
        }
        if (!bot.canReach(target) || !bot.isAttackReady()) {
            return;
        }
        if (bot.isUsingItem()) {
            if (bot.getUsedItemHand() == InteractionHand.OFF_HAND) {
                bot.stopUsingItem();
            } else {
                return;
            }
        }
        boolean midAir = !bot.onGround() && bot.fallDistance > 0.0F;
        if (jumpCritTimer > 0 && !midAir) {
            return; // jumped, waiting for the way down
        }
        if (jumpCritTimer == 0 && bot.onGround() && !bot.getObstacleSolver().isBusy() && bot.getRandom().nextFloat() < 0.35F) {
            bot.setWantSprint(false); // crits need the sprint off
            bot.getJumpControl().jump();
            jumpCritTimer = 10;
            return;
        }
        bot.swing(InteractionHand.MAIN_HAND);
        bot.doHurtTarget(target);
        bot.resetAttackCooldown();
        bot.setWantSprint(true);
        jumpCritTimer = 0;
    }
}
