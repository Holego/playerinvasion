package com.goshan.playerinvasion.entity.ai;

import com.goshan.playerinvasion.entity.InvaderEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * Base for the short scripted combat tricks (lava, cobweb, crystal, anchor).
 * A trick is a handful of ticks long, runs alongside the movement goals (no MOVE
 * flag) and has its own cooldown so the bot does not spam it.
 */
public abstract class TrickGoal extends Goal {

    protected final InvaderEntity bot;
    protected int phase;
    protected int timer;
    protected int cooldown;
    protected LivingEntity target;

    protected TrickGoal(InvaderEntity bot) {
        this.bot = bot;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    protected static boolean validTarget(LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return false;
        }
        return !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /** Preconditions besides the generic ones (cooldown, target, not eating). */
    protected abstract boolean ready(LivingEntity target);

    /** Called once per tick while the trick runs; return false when done. */
    protected abstract boolean step();

    protected abstract int cooldownTicks();

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        LivingEntity t = bot.getTarget();
        if (!validTarget(t) || bot.isEating() || bot.isFallFlying()) {
            return false;
        }
        if (!bot.getSensing().hasLineOfSight(t)) {
            return false;
        }
        return ready(t);
    }

    @Override
    public boolean canContinueToUse() {
        return phase >= 0 && validTarget(target);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        target = bot.getTarget();
        phase = 0;
        timer = 0;
    }

    @Override
    public void tick() {
        if (phase < 0) {
            return;
        }
        if (!step()) {
            phase = -1;
        }
        timer++;
    }

    @Override
    public void stop() {
        phase = -1;
        cooldown = cooldownTicks();
        target = null;
        if (!bot.isEating()) {
            bot.equipBestWeapon();
        }
    }
}
