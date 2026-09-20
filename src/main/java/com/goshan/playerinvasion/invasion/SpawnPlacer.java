package com.goshan.playerinvasion.invasion;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Finds a spot where a "player" could plausibly have walked in from: on the
 * ground, out of sight, preferably behind the person it was spawned for.
 */
public final class SpawnPlacer {

    @Nullable
    public static BlockPos find(ServerLevel level, Player near, int minDist, int maxDist, RandomSource random) {
        BlockPos best = null;
        for (int attempt = 0; attempt < 48; attempt++) {
            boolean behind = attempt < 32;
            float yaw = near.getYRot() + (behind ? 180.0F + (random.nextFloat() - 0.5F) * 140.0F : random.nextFloat() * 360.0F);
            double dist = minDist + random.nextDouble() * Math.max(1, maxDist - minDist);
            double rad = Math.toRadians(yaw);
            int x = Mth.floor(near.getX() - Math.sin(rad) * dist);
            int z = Mth.floor(near.getZ() + Math.cos(rad) * dist);
            BlockPos pos = ground(level, x, z, Mth.floor(near.getY()));
            if (pos == null) {
                continue;
            }
            boolean hidden = !canSee(level, near, pos);
            if (hidden || attempt >= 40) {
                return pos;
            }
            if (best == null) {
                best = pos;
            }
        }
        return best;
    }

    @Nullable
    private static BlockPos ground(ServerLevel level, int x, int z, int nearY) {
        if (!level.hasChunkAt(new BlockPos(x, nearY, z))) {
            return null;
        }
        if (!level.dimensionType().hasCeiling()) {
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (Math.abs(y - nearY) <= 48 && standable(level, pos)) {
                return pos;
            }
        }
        // ceiling dimensions (and fallback): scan around the player's height
        for (int dy = 0; dy <= 12; dy++) {
            for (int sign = -1; sign <= 1; sign += 2) {
                BlockPos pos = new BlockPos(x, nearY + dy * sign, z);
                if (standable(level, pos)) {
                    return pos;
                }
                if (dy == 0) {
                    break;
                }
            }
        }
        return null;
    }

    public static boolean standable(ServerLevel level, BlockPos feet) {
        if (feet.getY() <= level.getMinBuildHeight() + 1 || feet.getY() >= level.getMaxBuildHeight() - 2) {
            return false;
        }
        BlockState below = level.getBlockState(feet.below());
        if (!below.isSolid() || !below.getFluidState().isEmpty() || !below.isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)) {
            return false;
        }
        BlockState at = level.getBlockState(feet);
        BlockState head = level.getBlockState(feet.above());
        if (!at.getCollisionShape(level, feet).isEmpty() || !head.getCollisionShape(level, feet.above()).isEmpty()) {
            return false;
        }
        return at.getFluidState().isEmpty() && head.getFluidState().isEmpty()
                && !at.is(net.minecraft.world.level.block.Blocks.FIRE) && !at.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE);
    }

    private static boolean canSee(ServerLevel level, Player player, BlockPos pos) {
        Vec3 eye = player.getEyePosition();
        Vec3 target = new Vec3(pos.getX() + 0.5D, pos.getY() + 1.6D, pos.getZ() + 0.5D);
        HitResult hit = level.clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS;
    }

    private SpawnPlacer() {
    }
}
