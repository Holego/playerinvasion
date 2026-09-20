package com.goshan.playerinvasion.entity.ai;

import com.goshan.playerinvasion.PIConfig;
import com.goshan.playerinvasion.PlayerInvasion;
import com.goshan.playerinvasion.entity.InvaderEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.pathfinder.Path;

/**
 * Ender pearl to close the gap: thrown at a target that is far away, up on a
 * pillar, across a ravine, or simply unreachable by walking. The teleport itself
 * is vanilla - the pearl lands, the thrower appears there.
 */
public class PearlGoal extends TrickGoal {

    public PearlGoal(InvaderEntity bot) {
        super(bot);
    }

    @Override
    protected boolean ready(LivingEntity target) {
        if (!PIConfig.loaded() || !PIConfig.USE_PEARLS.get() || !bot.getInventory().has(Items.ENDER_PEARL)) {
            return false;
        }
        if (!bot.onGround() && !bot.isInWater()) {
            return false;
        }
        double dist = bot.distanceTo(target);
        double dy = target.getY() - bot.getY();
        if (dist > 48.0D) {
            return false;
        }
        if (dist >= 10.0D) {
            return true;
        }
        if (dy > 3.0D && dist > 3.0D) {
            return true;
        }
        Path path = bot.getNavigation().getPath();
        boolean unreachable = dist > 6.0D && (path == null || !path.canReach()) && bot.getNavigation().isDone();
        return unreachable;
    }

    @Override
    protected boolean step() {
        if (phase == 0) {
            if (!bot.holdFromInventory(s -> s.is(Items.ENDER_PEARL))) {
                return false;
            }
            bot.getLookControl().setLookAt(target, 60.0F, 60.0F);
            phase = 1;
            return true;
        }
        if (phase == 1 && timer >= 3) {
            ItemStack held = bot.getMainHandItem();
            if (!held.is(Items.ENDER_PEARL)) {
                return false;
            }
            ThrownEnderpearl pearl = new ThrownEnderpearl(bot.level(), bot);
            pearl.setItem(new ItemStack(Items.ENDER_PEARL));
            double dx = target.getX() - bot.getX();
            double dy = target.getY(0.5D) - pearl.getY();
            double dz = target.getZ() - bot.getZ();
            double flat = Math.sqrt(dx * dx + dz * dz);
            // pearls fly at 1.5 blocks/tick with 0.03 gravity: lift the aim to land on the target
            pearl.shoot(dx, dy + flat * flat * 0.0067D + 0.5D, dz, 1.5F, 1.0F);
            bot.level().addFreshEntity(pearl);
            PlayerInvasion.LOGGER.debug("{} throws an ender pearl at {} ({} blocks)", bot.getBotName(), target.getName().getString(), Math.round(flat));
            bot.playSound(SoundEvents.ENDER_PEARL_THROW, 0.5F, 0.4F / (bot.getRandom().nextFloat() * 0.4F + 0.8F));
            held.shrink(1);
            bot.swingAndPlaySound(null);
            bot.getNavigation().stop();
            phase = 2;
            return true;
        }
        return phase == 2 && timer < 6;
    }

    @Override
    protected int cooldownTicks() {
        return 100 + bot.getRandom().nextInt(120);
    }
}
