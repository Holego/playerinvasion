package com.goshan.playerinvasion.entity.ai;

import com.goshan.playerinvasion.PlayerInvasion;
import com.goshan.playerinvasion.entity.InvaderEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Arbitrates the bot's ranged/trap tricks - end crystals, respawn anchors or
 * beds, lava, cobwebs, ender pearls, splash potions - so they take fair turns
 * instead of one always winning. Each tick this is free to act, every trick
 * that is off its own cooldown and reports {@code ready()} goes into a pool,
 * and one is picked at random; a trick with a short cooldown (the crystal)
 * naturally gets more turns over time because it comes off cooldown sooner,
 * but it no longer permanently locks the others out the way six competing
 * same-priority {@code Goal}s did.
 */
public class CombatTricksGoal extends Goal {

    private final InvaderEntity bot;
    private final List<TrickGoal> tricks;
    private TrickGoal active;

    public CombatTricksGoal(InvaderEntity bot) {
        this.bot = bot;
        this.tricks = List.of(
                new CrystalAttackGoal(bot),
                new AnchorAttackGoal(bot),
                new PlaceLavaGoal(bot),
                new PlaceCobwebGoal(bot),
                new PearlGoal(bot),
                new PotionThrowGoal(bot)
        );
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    private static boolean validTarget(LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return false;
        }
        return !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    private int debugTick;

    @Override
    public boolean canUse() {
        LivingEntity target = bot.getTarget();
        boolean eligible = validTarget(target) && !bot.isEating() && !bot.isFallFlying()
                && bot.getSensing().hasLineOfSight(target);

        List<TrickGoal> candidates = null;
        StringBuilder debug = PlayerInvasion.LOGGER.isDebugEnabled() && ++debugTick % 15 == 0 ? new StringBuilder() : null;
        for (TrickGoal trick : tricks) {
            // cooldowns tick down in real time regardless of whether we currently have a target
            boolean offCooldown = trick.tickCooldown();
            boolean isReady = offCooldown && eligible && trick.ready(target);
            if (debug != null) {
                debug.append(trick.getClass().getSimpleName()).append('=')
                        .append(offCooldown ? (isReady ? "ready" : "notready") : "cd(" + trick.cooldown + ")").append(' ');
            }
            if (isReady) {
                if (candidates == null) {
                    candidates = new ArrayList<>(tricks.size());
                }
                candidates.add(trick);
            }
        }
        if (debug != null) {
            PlayerInvasion.LOGGER.debug("{} trick poll: eligible={} dist={} :: {}", bot.getBotName(), eligible,
                    target != null ? Math.round(bot.distanceTo(target) * 10) / 10.0 : "-", debug);
        }
        if (candidates == null) {
            return false;
        }
        active = candidates.get(bot.getRandom().nextInt(candidates.size()));
        active.begin(target);
        if (PlayerInvasion.LOGGER.isDebugEnabled() && candidates.size() > 1) {
            PlayerInvasion.LOGGER.debug("{} picks {} out of {} ready tricks", bot.getBotName(),
                    active.getClass().getSimpleName(), candidates.size());
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (active == null) {
            return false;
        }
        boolean ok = active.isActive() && validTarget(active.target);
        if (!ok && PlayerInvasion.LOGGER.isDebugEnabled()) {
            PlayerInvasion.LOGGER.debug("{} trick {} ends: active={} validTarget={}", bot.getBotName(),
                    active.getClass().getSimpleName(), active.isActive(), validTarget(active.target));
        }
        return ok;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (active != null) {
            active.runTick();
        }
    }

    @Override
    public void stop() {
        if (active != null) {
            active.stop();
            active = null;
        }
        if (!bot.isEating()) {
            bot.equipBestWeapon();
        }
    }
}
