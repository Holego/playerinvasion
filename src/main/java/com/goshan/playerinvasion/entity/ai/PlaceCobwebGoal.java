package com.goshan.playerinvasion.entity.ai;

import com.goshan.playerinvasion.PIConfig;
import com.goshan.playerinvasion.PlayerInvasion;
import com.goshan.playerinvasion.entity.InvaderEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Drops a cobweb on the target's feet so it cannot run, then keeps hitting. */
public class PlaceCobwebGoal extends TrickGoal {

    private BlockPos spot;

    public PlaceCobwebGoal(InvaderEntity bot) {
        super(bot);
    }

    @Override
    protected boolean ready(LivingEntity target) {
        if (!PIConfig.loaded() || !PIConfig.USE_COBWEBS.get() || !bot.getInventory().has(Items.COBWEB)) {
            return false;
        }
        // same reasoning as the lava trick: don't start a trap that the (higher-priority) eat-apple
        // goal would then interrupt half-done, leaving the bot holding an unused cobweb
        if (bot.getHealth() < 9.0F) {
            return false;
        }
        double dist = bot.distanceTo(target);
        if (dist < 1.2D || dist > 4.5D || target.isInWater()) {
            return false;
        }
        BlockPos feet = target.blockPosition();
        if (!bot.level().getBlockState(feet).isAir() || !Placing.inReach(bot, feet)) {
            return false;
        }
        if (bot.level().getBlockState(feet.below()).is(Blocks.COBWEB)) {
            return false;
        }
        spot = feet;
        return true;
    }

    @Override
    protected boolean step() {
        if (phase == 0) {
            if (!bot.holdFromInventory(s -> s.is(Items.COBWEB))) {
                PlayerInvasion.LOGGER.debug("{} cobweb: could not hold a cobweb (has={})", bot.getBotName(), bot.getInventory().has(Items.COBWEB));
                return false;
            }
            Vec3 c = Vec3.atCenterOf(spot);
            bot.getLookControl().setLookAt(c.x, c.y, c.z, 60.0F, 60.0F);
            phase = 1;
            return true;
        }
        if (phase == 1 && timer >= 1) {
            if (!bot.level().getBlockState(spot).isAir()) {
                PlayerInvasion.LOGGER.debug("{} cobweb: spot {} no longer air ({})", bot.getBotName(), spot.toShortString(),
                        bot.level().getBlockState(spot).getBlock().getName().getString());
                return false;
            }
            Placing.place(bot, spot, Blocks.COBWEB.defaultBlockState(), Items.COBWEB, null);
            PlayerInvasion.LOGGER.debug("{} traps the target in a cobweb at {}", bot.getBotName(), spot.toShortString());
            phase = 2;
            return true;
        }
        return phase == 2 && timer < 5;
    }

    @Override
    protected int cooldownTicks() {
        return 100 + bot.getRandom().nextInt(100);
    }
}
