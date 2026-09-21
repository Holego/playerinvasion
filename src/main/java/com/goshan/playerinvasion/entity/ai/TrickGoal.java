package com.goshan.playerinvasion.entity.ai;

import com.goshan.playerinvasion.entity.InvaderEntity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Base for the short scripted combat tricks (lava, cobweb, crystal, anchor,
 * pearl, splash potion). This is a plain object, not a {@code Goal} - it is
 * driven by {@link CombatTricksGoal}, which owns the bot's one "ranged trick"
 * turn and picks fairly among whichever tricks are off cooldown and
 * geometrically possible right now. Six separate same-priority {@code Goal}s
 * would let whichever one happens to be checked first (and recharges fastest,
 * like the crystal) win every single time and starve the rest - that was the
 * original bug: lava, cobwebs, pearls and potions almost never fired.
 */
public abstract class TrickGoal {

    protected final InvaderEntity bot;
    protected int phase = -1;
    protected int timer;
    protected int cooldown;
    protected LivingEntity target;

    protected TrickGoal(InvaderEntity bot) {
        this.bot = bot;
    }

    /** Whether this trick could be attempted right now (resources, range, geometry). Cooldown is handled by the caller. */
    protected abstract boolean ready(LivingEntity target);

    /** Called once per tick while the trick runs; return false when it is done or gives up. */
    protected abstract boolean step();

    protected abstract int cooldownTicks();

    /** Ticks the cooldown down; returns true once it has reached zero (i.e. this trick may be polled this tick). */
    boolean tickCooldown() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        return true;
    }

    boolean isActive() {
        return phase >= 0;
    }

    void begin(LivingEntity currentTarget) {
        this.target = currentTarget;
        this.phase = 0;
        this.timer = 0;
    }

    void runTick() {
        if (!step()) {
            phase = -1;
        }
        timer++;
    }

    void stop() {
        phase = -1;
        cooldown = cooldownTicks();
        target = null;
    }
}
