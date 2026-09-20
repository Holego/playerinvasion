package com.goshan.playerinvasion.entity.ai;

import com.goshan.playerinvasion.PIConfig;
import com.goshan.playerinvasion.entity.InvaderEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Crystal PvP: obsidian next to the target, crystal on top, hit it. The bot keeps
 * a real player's safety distance and takes the splash like anyone else would.
 */
public class CrystalAttackGoal extends TrickGoal {

    private static final double MIN_SELF_DISTANCE = 4.0D;

    @Nullable
    private BlockPos obsidian;
    @Nullable
    private EndCrystal crystal;

    public CrystalAttackGoal(InvaderEntity bot) {
        super(bot);
    }

    @Override
    protected boolean ready(LivingEntity target) {
        if (!PIConfig.loaded() || !PIConfig.USE_CRYSTALS.get()) {
            return false;
        }
        if (!bot.getInventory().has(Items.END_CRYSTAL) || !bot.getInventory().has(Items.OBSIDIAN)) {
            return false;
        }
        if (!bot.onGround() || bot.getHealth() < 9.0F) {
            return false;
        }
        double dist = bot.distanceTo(target);
        if (dist < 2.5D || dist > 6.0D) {
            return false;
        }
        obsidian = findSpot(target);
        return obsidian != null;
    }

    @Nullable
    private BlockPos findSpot(LivingEntity target) {
        Level level = bot.level();
        BlockPos feet = target.blockPosition();
        for (BlockPos pos : Placing.ring(feet, bot.position(), true)) {
            if (Placing.distanceFrom(bot, pos) < MIN_SELF_DISTANCE || !Placing.inReach(bot, pos)) {
                continue;
            }
            if (!Placing.isFree(level, pos) && !level.getBlockState(pos).is(Blocks.OBSIDIAN)) {
                continue;
            }
            if (!level.isEmptyBlock(pos.above()) || !level.isEmptyBlock(pos.above(2))) {
                continue;
            }
            if (!Placing.noEntityInside(level, pos, null) || !Placing.noEntityInside(level, pos.above(), null)) {
                continue;
            }
            return pos;
        }
        return null;
    }

    @Override
    protected boolean step() {
        if (obsidian == null) {
            return false;
        }
        Level level = bot.level();
        switch (phase) {
            case 0 -> {
                if (!level.getBlockState(obsidian).is(Blocks.OBSIDIAN)) {
                    if (!bot.holdFromInventory(s -> s.is(Items.OBSIDIAN)) || !Placing.isFree(level, obsidian)) {
                        return false;
                    }
                    Placing.place(bot, obsidian, Blocks.OBSIDIAN.defaultBlockState(), Items.OBSIDIAN, null);
                }
                phase = 1;
                return true;
            }
            case 1 -> {
                if (timer < 2) {
                    return true;
                }
                if (!bot.holdFromInventory(s -> s.is(Items.END_CRYSTAL))) {
                    return false;
                }
                BlockPos top = obsidian.above();
                if (!level.isEmptyBlock(top) || !Placing.noEntityInside(level, top, null)) {
                    return false;
                }
                EndCrystal c = new EndCrystal(level, top.getX() + 0.5D, top.getY(), top.getZ() + 0.5D);
                c.setShowBottom(false);
                level.addFreshEntity(c);
                crystal = c;
                bot.getMainHandItem().shrink(1);
                Vec3 look = Vec3.atCenterOf(top);
                bot.getLookControl().setLookAt(look.x, look.y, look.z, 60.0F, 60.0F);
                bot.swingAndPlaySound(SoundEvents.STONE_PLACE);
                phase = 2;
                return true;
            }
            case 2 -> {
                if (crystal == null || crystal.isRemoved()) {
                    return false;
                }
                bot.equipBestWeapon();
                boolean targetClose = target.distanceToSqr(crystal) < 9.0D;
                boolean selfSafe = bot.distanceToSqr(crystal) >= MIN_SELF_DISTANCE * MIN_SELF_DISTANCE * 0.8D;
                if ((targetClose && selfSafe) || timer > 24) {
                    detonate(level);
                    return false;
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void detonate(Level level) {
        if (crystal == null || crystal.isRemoved()) {
            return;
        }
        bot.swing(InteractionHand.MAIN_HAND);
        Vec3 pos = crystal.position();
        crystal.remove(Entity.RemovalReason.KILLED);
        Level.ExplosionInteraction interaction = PIConfig.EXPLOSIONS_BREAK_BLOCKS.get()
                ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE;
        level.explode(crystal, bot.damageSources().explosion(crystal, bot), null,
                pos.x, pos.y, pos.z, 6.0F, false, interaction);
        crystal = null;
    }

    @Override
    public void stop() {
        super.stop();
        crystal = null;
        obsidian = null;
    }

    @Override
    protected int cooldownTicks() {
        return 20 + bot.getRandom().nextInt(30);
    }
}
