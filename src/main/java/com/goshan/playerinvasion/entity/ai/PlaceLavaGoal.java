package com.goshan.playerinvasion.entity.ai;

import com.goshan.playerinvasion.PIConfig;
import com.goshan.playerinvasion.PlayerInvasion;
import com.goshan.playerinvasion.entity.InvaderEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** Dumps a lava bucket under the target's feet, then backs off. */
public class PlaceLavaGoal extends TrickGoal {

    @Nullable
    private BlockPos spot;

    public PlaceLavaGoal(InvaderEntity bot) {
        super(bot);
    }

    @Override
    protected boolean ready(LivingEntity target) {
        if (!PIConfig.loaded() || !PIConfig.USE_LAVA.get() || !bot.getInventory().has(Items.LAVA_BUCKET)) {
            return false;
        }
        // low health: back off and eat instead of finishing a trap - and don't let the eat goal
        // (which outranks this one) catch the bot mid-pour with an empty bucket in hand
        if (!bot.onGround() || bot.getHealth() < 9.0F || target.isInWater() || bot.isInWater()) {
            return false;
        }
        double dist = bot.distanceTo(target);
        if (dist < 2.5D || dist > 6.0D) {
            return false;
        }
        spot = findSpot(target);
        return spot != null;
    }

    @Nullable
    private BlockPos findSpot(LivingEntity target) {
        Level level = bot.level();
        BlockPos feet = target.blockPosition();
        BlockPos[] candidates = new BlockPos[9];
        candidates[0] = feet;
        System.arraycopy(Placing.ring(feet, bot.position(), false), 0, candidates, 1, 8);
        for (BlockPos pos : candidates) {
            if (!Placing.isFree(level, pos) || !Placing.isSolidGround(level, pos.below())) {
                continue;
            }
            if (Placing.distanceFrom(bot, pos) < 2.2D || !Placing.inReach(bot, pos)) {
                continue;
            }
            return pos;
        }
        return null;
    }

    @Override
    protected boolean step() {
        if (spot == null) {
            return false;
        }
        if (phase == 0) {
            if (!bot.holdFromInventory(s -> s.is(Items.LAVA_BUCKET))) {
                PlayerInvasion.LOGGER.debug("{} lava: could not hold a lava bucket (has={})", bot.getBotName(), bot.getInventory().has(Items.LAVA_BUCKET));
                return false;
            }
            Vec3 c = Vec3.atCenterOf(spot);
            bot.getLookControl().setLookAt(c.x, c.y, c.z, 60.0F, 60.0F);
            phase = 1;
            return true;
        }
        if (phase == 1 && timer >= 1) {
            if (!Placing.isFree(bot.level(), spot) || !bot.getMainHandItem().is(Items.LAVA_BUCKET)) {
                PlayerInvasion.LOGGER.debug("{} lava: spot {} free={} holding={}", bot.getBotName(), spot.toShortString(),
                        Placing.isFree(bot.level(), spot), bot.getMainHandItem());
                return false;
            }
            bot.level().setBlock(spot, Blocks.LAVA.defaultBlockState(), 11);
            bot.swingAndPlaySound(SoundEvents.BUCKET_EMPTY_LAVA);
            PlayerInvasion.LOGGER.debug("{} pours lava at {}", bot.getBotName(), spot.toShortString());
            bot.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
            phase = 2;
            return true;
        }
        return phase == 2 && timer < 8;
    }

    @Override
    protected int cooldownTicks() {
        return 240 + bot.getRandom().nextInt(200);
    }
}
