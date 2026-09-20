package com.goshan.playerinvasion.entity.ai;

import com.goshan.playerinvasion.PIConfig;
import com.goshan.playerinvasion.entity.InvaderEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * No visible target, but somebody is around: walk to where they are, sneak up
 * when they are not looking, and tower / bridge / dig when walking does not get
 * there. This is what turns "the bot wanders in the woods" into "the bot is in
 * your base" - or in your mine shaft.
 */
public class HuntGoal extends Goal {

    private final InvaderEntity bot;
    private Player prey;
    private int repathTimer;

    public HuntGoal(InvaderEntity bot) {
        this.bot = bot;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    private double radius() {
        return PIConfig.loaded() ? PIConfig.HUNT_RADIUS.get() : 0.0D;
    }

    @Override
    public boolean canUse() {
        if (bot.getTarget() != null || radius() <= 0.0D || bot.isEating() || bot.isFallFlying()) {
            return false;
        }
        prey = bot.nearestRealPlayer(radius());
        return prey != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (bot.getTarget() != null || prey == null || !prey.isAlive() || prey.isSpectator() || prey.isCreative() || bot.isFallFlying()) {
            return false;
        }
        return bot.distanceToSqr(prey) < radius() * radius() * 1.5D;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        repathTimer = 0;
    }

    @Override
    public void stop() {
        prey = null;
        bot.setWantSneak(false);
        bot.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (prey == null) {
            return;
        }
        double dist = bot.distanceTo(prey);

        // creep up when close and unnoticed
        Vec3 toMe = bot.position().subtract(prey.position()).normalize();
        Vec3 look = prey.getViewVector(1.0F);
        boolean preyLooking = new Vec3(look.x, 0.0D, look.z).normalize().dot(new Vec3(toMe.x, 0.0D, toMe.z).normalize()) > 0.3D;
        bot.setWantSneak(dist < 20.0D && !preyLooking && bot.onGround() && !bot.getObstacleSolver().isBusy());

        if (bot.getObstacleSolver().tick(prey.position(), dist < 2.0D)) {
            return;
        }

        if (--repathTimer <= 0) {
            repathTimer = 20 + bot.getRandom().nextInt(20);
            bot.getNavigation().moveTo(prey, bot.navSpeed());
        }
    }
}
