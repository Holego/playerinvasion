package com.goshan.playerinvasion.entity.ai;

import com.goshan.playerinvasion.entity.InvaderEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** Small shared helpers for goals that put blocks into the world. */
public final class Placing {

    /** Survival block reach, measured from the eyes to the block centre. */
    public static final double REACH = 5.0D;

    public static boolean isFree(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || (state.canBeReplaced() && state.getFluidState().isEmpty());
    }

    public static boolean isFreeOrFluid(Level level, BlockPos pos) {
        return level.getBlockState(pos).canBeReplaced();
    }

    public static boolean isSolidGround(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isFaceSturdy(level, pos, Direction.UP);
    }

    public static boolean noEntityInside(Level level, BlockPos pos, @Nullable Entity except) {
        return level.getEntitiesOfClass(Entity.class, new AABB(pos), e -> e != except && e.isAlive() && !e.isSpectator()).isEmpty();
    }

    public static boolean inReach(InvaderEntity bot, BlockPos pos) {
        return bot.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= REACH * REACH;
    }

    public static double distanceFrom(Entity entity, BlockPos pos) {
        return Math.sqrt(entity.position().distanceToSqr(Vec3.atCenterOf(pos)));
    }

    public static boolean place(InvaderEntity bot, BlockPos pos, BlockState state, Item consume, @Nullable SoundEvent sound) {
        Level level = bot.level();
        if (!level.setBlock(pos, state, 3)) {
            return false;
        }
        if (consume != null) {
            if (bot.getMainHandItem().is(consume)) {
                bot.getMainHandItem().shrink(1);
            } else {
                bot.getInventory().take(consume, 1);
            }
        }
        Vec3 c = Vec3.atCenterOf(pos);
        bot.getLookControl().setLookAt(c.x, c.y, c.z, 60.0F, 60.0F);
        bot.swingAndPlaySound(sound != null ? sound : state.getSoundType(level, pos, bot).getPlaceSound());
        return true;
    }

    /** A block the bot may build with (never its consumables or explosives). */
    public static ItemStack buildingBlock(InvaderEntity bot) {
        return bot.getInventory().find(s -> s.getItem() instanceof BlockItem
                && !s.is(Items.COBWEB) && !s.is(Items.RESPAWN_ANCHOR) && !s.is(Items.OBSIDIAN) && !s.is(Items.GLOWSTONE)
                && !(s.getItem() instanceof BedItem));
    }

    /**
     * Bridges one block towards {@code goal} when the bot stands at the edge of a gap.
     * Returns true if a block was placed (the navigation can then walk on).
     */
    public static boolean bridgeTowards(InvaderEntity bot, Vec3 goal) {
        if (!bot.onGround()) {
            return false;
        }
        ItemStack blocks = buildingBlock(bot);
        if (blocks.isEmpty()) {
            return false;
        }
        Level level = bot.level();
        BlockPos feet = bot.blockPosition();
        double dx = goal.x - bot.getX();
        double dz = goal.z - bot.getZ();
        if (dx * dx + dz * dz < 4.0D) {
            return false;
        }
        Direction dir = Math.abs(dx) >= Math.abs(dz)
                ? (dx > 0 ? Direction.EAST : Direction.WEST)
                : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
        BlockPos front = feet.relative(dir);
        BlockPos frontBelow = front.below();
        boolean gap = isFree(level, front) && isFree(level, front.above()) && isFree(level, frontBelow)
                && isFree(level, frontBelow.below());
        if (!gap || !noEntityInside(level, frontBelow, bot)) {
            return false;
        }
        BlockItem item = (BlockItem) blocks.getItem();
        boolean placed = place(bot, frontBelow, item.getBlock().defaultBlockState(), item, null);
        if (placed) {
            bot.setWantSneak(false);
        }
        return placed;
    }

    /** All 8 horizontal neighbours of a block, nearest to {@code from} first. */
    public static BlockPos[] ring(BlockPos center, Vec3 from, boolean farthestFirst) {
        BlockPos[] ring = new BlockPos[8];
        int i = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    ring[i++] = center.offset(dx, 0, dz);
                }
            }
        }
        for (int a = 0; a < ring.length; a++) {
            for (int b = a + 1; b < ring.length; b++) {
                double da = from.distanceToSqr(Vec3.atCenterOf(ring[a]));
                double db = from.distanceToSqr(Vec3.atCenterOf(ring[b]));
                if (farthestFirst ? db > da : db < da) {
                    BlockPos t = ring[a];
                    ring[a] = ring[b];
                    ring[b] = t;
                }
            }
        }
        return ring;
    }

    private Placing() {
    }
}
