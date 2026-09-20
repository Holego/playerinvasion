package com.goshan.playerinvasion.entity.ai;

import com.goshan.playerinvasion.PIConfig;
import com.goshan.playerinvasion.entity.InvaderEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * The other explosive: a respawn anchor charged with glowstone where anchors do
 * not work (Overworld, End), or a bed where beds do not work (Nether, End). Both
 * blow up the moment you "use" them - the [Intentional Game Design] classic.
 */
public class AnchorAttackGoal extends TrickGoal {

    private static final double MIN_SELF_DISTANCE = 4.0D;

    @Nullable
    private BlockPos spot;
    private boolean useBed;
    private Direction bedFacing = Direction.NORTH;

    public AnchorAttackGoal(InvaderEntity bot) {
        super(bot);
    }

    @Override
    protected boolean ready(LivingEntity target) {
        if (!PIConfig.loaded() || !PIConfig.USE_ANCHORS.get()) {
            return false;
        }
        Level level = bot.level();
        boolean anchorExplodes = !RespawnAnchorBlock.canSetSpawn(level);
        boolean bedExplodes = !BedBlock.canSetSpawn(level);
        boolean hasAnchor = bot.getInventory().has(Items.RESPAWN_ANCHOR) && bot.getInventory().has(Items.GLOWSTONE);
        boolean hasBed = !bot.getInventory().find(s -> s.getItem() instanceof BedItem).isEmpty();
        if (anchorExplodes && hasAnchor) {
            useBed = false;
        } else if (bedExplodes && hasBed) {
            useBed = true;
        } else {
            return false;
        }
        if (!bot.onGround() || bot.getHealth() < 9.0F) {
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
        for (BlockPos pos : Placing.ring(feet, bot.position(), true)) {
            if (Placing.distanceFrom(bot, pos) < MIN_SELF_DISTANCE || !Placing.inReach(bot, pos)) {
                continue;
            }
            if (!Placing.isFree(level, pos) || !Placing.noEntityInside(level, pos, null)) {
                continue;
            }
            if (!Placing.isSolidGround(level, pos.below())) {
                continue;
            }
            if (useBed) {
                Direction facing = pickBedFacing(pos, target);
                if (facing == null) {
                    continue;
                }
                bedFacing = facing;
            }
            return pos;
        }
        return null;
    }

    @Nullable
    private Direction pickBedFacing(BlockPos foot, LivingEntity target) {
        Level level = bot.level();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos head = foot.relative(dir);
            if (Placing.isFree(level, head) && Placing.noEntityInside(level, head, null)
                    && Placing.isSolidGround(level, head.below()) && Placing.distanceFrom(bot, head) >= MIN_SELF_DISTANCE - 1.0D) {
                return dir;
            }
        }
        return null;
    }

    @Override
    protected boolean step() {
        if (spot == null) {
            return false;
        }
        Level level = bot.level();
        if (useBed) {
            return stepBed(level);
        }
        return stepAnchor(level);
    }

    private boolean stepAnchor(Level level) {
        switch (phase) {
            case 0 -> {
                if (!bot.holdFromInventory(s -> s.is(Items.RESPAWN_ANCHOR)) || !Placing.isFree(level, spot)) {
                    return false;
                }
                Placing.place(bot, spot, Blocks.RESPAWN_ANCHOR.defaultBlockState(), Items.RESPAWN_ANCHOR, null);
                phase = 1;
                return true;
            }
            case 1 -> {
                if (timer < 2) {
                    return true;
                }
                if (!level.getBlockState(spot).is(Blocks.RESPAWN_ANCHOR) || !bot.holdFromInventory(s -> s.is(Items.GLOWSTONE))) {
                    return false;
                }
                BlockState charged = level.getBlockState(spot).setValue(RespawnAnchorBlock.CHARGE, 1);
                level.setBlock(spot, charged, 3);
                bot.getMainHandItem().shrink(1);
                bot.swingAndPlaySound(SoundEvents.RESPAWN_ANCHOR_CHARGE);
                phase = 2;
                return true;
            }
            case 2 -> {
                if (timer < 4) {
                    return true;
                }
                bot.equipBestWeapon();
                if (level.getBlockState(spot).is(Blocks.RESPAWN_ANCHOR)) {
                    explode(level, spot, null);
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean stepBed(Level level) {
        BlockPos head = spot.relative(bedFacing);
        switch (phase) {
            case 0 -> {
                ItemStack bed = bot.getInventory().find(s -> s.getItem() instanceof BedItem);
                if (bed.isEmpty() || !Placing.isFree(level, spot) || !Placing.isFree(level, head)) {
                    return false;
                }
                ItemStack held = bed;
                if (!bot.holdFromInventory(s -> s == held)) {
                    return false;
                }
                BedBlock block = (BedBlock) ((BedItem) held.getItem()).getBlock();
                BlockState foot = block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, bedFacing).setValue(BedBlock.PART, BedPart.FOOT);
                BlockState headState = foot.setValue(BedBlock.PART, BedPart.HEAD);
                level.setBlock(spot, foot, 3);
                level.setBlock(head, headState, 3);
                bot.getMainHandItem().shrink(1);
                Vec3 c = Vec3.atCenterOf(spot);
                bot.getLookControl().setLookAt(c.x, c.y, c.z, 60.0F, 60.0F);
                bot.swingAndPlaySound(SoundEvents.WOOD_PLACE);
                phase = 1;
                return true;
            }
            case 1 -> {
                if (timer < 3) {
                    return true;
                }
                bot.equipBestWeapon();
                if (level.getBlockState(spot).getBlock() instanceof BedBlock) {
                    explode(level, spot, head);
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    private void explode(Level level, BlockPos pos, @Nullable BlockPos secondPart) {
        bot.swing(InteractionHand.MAIN_HAND);
        level.removeBlock(pos, false);
        if (secondPart != null) {
            level.removeBlock(secondPart, false);
        }
        Vec3 center = Vec3.atCenterOf(pos);
        Level.ExplosionInteraction interaction = PIConfig.EXPLOSIONS_BREAK_BLOCKS.get()
                ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE;
        level.explode(null, level.damageSources().badRespawnPointExplosion(center), null,
                center, 5.0F, true, interaction);
    }

    @Override
    public void stop() {
        super.stop();
        spot = null;
    }

    @Override
    protected int cooldownTicks() {
        return 60 + bot.getRandom().nextInt(60);
    }
}
