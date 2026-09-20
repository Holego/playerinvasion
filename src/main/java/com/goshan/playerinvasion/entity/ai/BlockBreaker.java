package com.goshan.playerinvasion.entity.ai;

import com.goshan.playerinvasion.PIConfig;
import com.goshan.playerinvasion.PlayerInvasion;
import com.goshan.playerinvasion.entity.InvaderEntity;
import com.goshan.playerinvasion.entity.InvaderInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Lets a fake player mine its way through whatever separates it from its target,
 * using vanilla's own break-time formula with the best tool it carries. A wooden
 * pickaxe will not get through obsidian in any reasonable time, so it gives up on
 * such blocks - exactly like a real player would.
 */
public final class BlockBreaker {

    /** Blocks that take longer than this (ticks) are not worth the effort. */
    private static final int MAX_BREAK_TICKS = 400;

    private final InvaderEntity bot;
    @Nullable
    private BlockPos target;
    private int progress;
    private int total;
    private int lastStage = -1;
    private ItemStack tool = ItemStack.EMPTY;

    public BlockBreaker(InvaderEntity bot) {
        this.bot = bot;
    }

    public boolean isBusy() {
        return target != null;
    }

    public void reset() {
        if (target != null && bot.level() instanceof ServerLevel level) {
            level.destroyBlockProgress(bot.getId(), target, -1);
        }
        boolean hadTool = target != null;
        target = null;
        progress = 0;
        total = 0;
        lastStage = -1;
        tool = ItemStack.EMPTY;
        if (hadTool) {
            bot.equipBestWeapon();
        }
    }

    /**
     * Clears the way towards {@code goal}. Returns true while a block is being mined,
     * false if there is nothing (breakable) in the way.
     */
    public boolean tickTowards(Vec3 goal) {
        if (!(bot.level() instanceof ServerLevel level)) {
            return false;
        }
        if (target == null) {
            target = pickBlock(level, goal);
            if (target == null) {
                return false;
            }
            BlockState state = level.getBlockState(target);
            tool = bestTool(state);
            total = breakTicks(level, target, state, tool);
            if (total < 0) {
                target = null;
                return false;
            }
            progress = 0;
            lastStage = -1;
            if (!tool.isEmpty()) {
                ItemStack chosen = tool;
                bot.holdFromInventory(s -> s == chosen);
            }
        }

        BlockState state = level.getBlockState(target);
        if (state.isAir()) {
            level.destroyBlockProgress(bot.getId(), target, -1);
            target = null;
            return false;
        }

        Vec3 center = Vec3.atCenterOf(target);
        bot.getLookControl().setLookAt(center.x, center.y, center.z, 30.0F, 30.0F);
        if (progress % 4 == 0) {
            bot.swing(InteractionHand.MAIN_HAND);
        }
        progress++;
        int stage = Math.min(9, progress * 10 / Math.max(1, total));
        if (stage != lastStage) {
            lastStage = stage;
            level.destroyBlockProgress(bot.getId(), target, stage);
        }
        if (progress >= total) {
            breakBlock(level, target, state);
            level.destroyBlockProgress(bot.getId(), target, -1);
            target = null;
        }
        return true;
    }

    private void breakBlock(ServerLevel level, BlockPos pos, BlockState state) {
        PlayerInvasion.LOGGER.debug("{} broke {} at {}", bot.getBotName(), state.getBlock().getName().getString(), pos.toShortString());
        level.levelEvent(2001, pos, Block.getId(state));
        BlockEntity blockEntity = level.getBlockEntity(pos);
        ItemStack held = bot.getMainHandItem();
        Block.dropResources(state, level, pos, blockEntity, bot, held);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        if (!held.isEmpty() && held.isDamageableItem()) {
            held.hurtAndBreak(1, bot, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        }
    }

    @Nullable
    private BlockPos pickBlock(ServerLevel level, Vec3 goal) {
        BlockPos feet = bot.blockPosition();
        double dx = goal.x - bot.getX();
        double dz = goal.z - bot.getZ();
        double dy = goal.y - bot.getY();
        Direction dir = Math.abs(dx) >= Math.abs(dz)
                ? (dx > 0 ? Direction.EAST : Direction.WEST)
                : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
        Direction side = Math.abs(dx) >= Math.abs(dz)
                ? (dz > 0 ? Direction.SOUTH : Direction.NORTH)
                : (dx > 0 ? Direction.EAST : Direction.WEST);

        BlockPos[] candidates;
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (dy < -2.5D && flat < 2.5D) {
            // target is right below: dig straight down, but never into a drop or lava
            BlockPos under = feet.below();
            BlockState twoUnder = level.getBlockState(under.below());
            if (twoUnder.isSolid() && twoUnder.getFluidState().isEmpty()) {
                candidates = new BlockPos[]{under};
            } else {
                candidates = new BlockPos[]{feet.relative(dir), feet.relative(dir).above(), feet.relative(dir).below()};
            }
        } else if (dy > 1.5D) {
            // target above: open the ceiling first, then the wall
            candidates = new BlockPos[]{feet.above(2), feet.relative(dir).above(), feet.relative(dir), feet.relative(dir).above(2)};
        } else if (dy < -1.5D) {
            // target below and off to the side: cut a staircase - open the way forward, then the step
            BlockPos front = feet.relative(dir);
            BlockPos step = front.below();
            BlockState underStep = level.getBlockState(step.below());
            boolean stepSafe = underStep.isSolid() && underStep.getFluidState().isEmpty();
            candidates = stepSafe
                    ? new BlockPos[]{front, front.above(), step}
                    : new BlockPos[]{front, front.above()};
        } else {
            candidates = new BlockPos[]{feet.relative(dir).above(), feet.relative(dir), feet.relative(side).above(), feet.relative(side)};
        }
        for (BlockPos pos : candidates) {
            if (isWorthBreaking(level, pos)) {
                return pos;
            }
        }
        return null;
    }

    private boolean isWorthBreaking(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.canBeReplaced()) {
            return false;
        }
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }
        if (level.getBlockEntity(pos) != null) {
            return false;
        }
        return breakTicks(level, pos, state, bestTool(state)) >= 0;
    }

    private ItemStack bestTool(BlockState state) {
        ItemStack best = ItemStack.EMPTY;
        float bestSpeed = 1.0F;
        ItemStack hand = bot.getMainHandItem();
        if (hand.getItem() instanceof DiggerItem && hand.getDestroySpeed(state) > bestSpeed) {
            best = hand;
            bestSpeed = hand.getDestroySpeed(state);
        }
        InvaderInventory inv = bot.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.get(i);
            if (stack.getItem() instanceof DiggerItem && stack.getDestroySpeed(state) > bestSpeed) {
                best = stack;
                bestSpeed = stack.getDestroySpeed(state);
            }
        }
        return best;
    }

    /** Vanilla break time in ticks for this bot with this tool, or -1 when hopeless. */
    private int breakTicks(ServerLevel level, BlockPos pos, BlockState state, ItemStack tool) {
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0.0F) {
            return -1;
        }
        if (hardness == 0.0F) {
            return 1;
        }
        float speed = tool.isEmpty() ? 1.0F : tool.getDestroySpeed(state);
        if (speed > 1.0F && !tool.isEmpty()) {
            int efficiency = EnchantmentHelper.getBlockEfficiency(bot);
            if (efficiency > 0) {
                speed += efficiency * efficiency + 1;
            }
        }
        boolean correct = !state.requiresCorrectToolForDrops() || (!tool.isEmpty() && tool.isCorrectToolForDrops(state));
        float perTick = speed / hardness / (correct ? 30.0F : 100.0F);
        int ticks = (int) Math.ceil(1.0F / perTick);
        return ticks > MAX_BREAK_TICKS ? -1 : ticks;
    }

    public static boolean enabled() {
        return PIConfig.loaded() && PIConfig.DIG_THROUGH_BLOCKS.get();
    }
}
