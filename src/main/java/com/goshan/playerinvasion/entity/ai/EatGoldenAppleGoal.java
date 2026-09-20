package com.goshan.playerinvasion.entity.ai;

import com.goshan.playerinvasion.PIConfig;
import com.goshan.playerinvasion.entity.InvaderEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** Low on hearts: back off and eat a golden apple (the enchanted one when it is really bad). */
public class EatGoldenAppleGoal extends Goal {

    private final InvaderEntity bot;
    private int cooldown;
    private int retreatTimer;

    public EatGoldenAppleGoal(InvaderEntity bot) {
        this.bot = bot;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        if (!PIConfig.loaded() || !PIConfig.USE_GOLDEN_APPLES.get() || bot.isEating()) {
            return false;
        }
        if (bot.getHealth() > bot.getMaxHealth() * 0.45F || bot.isInWater() || bot.isDrawingBow() || bot.isFallFlying()) {
            return false;
        }
        return !pickApple().isEmpty();
    }

    private ItemStack pickApple() {
        if (bot.getHealth() <= 5.0F) {
            ItemStack notch = bot.getInventory().find(s -> s.is(Items.ENCHANTED_GOLDEN_APPLE));
            if (!notch.isEmpty()) {
                return notch;
            }
        }
        ItemStack apple = bot.getInventory().find(s -> s.is(Items.GOLDEN_APPLE));
        if (apple.isEmpty()) {
            apple = bot.getInventory().find(s -> s.is(Items.ENCHANTED_GOLDEN_APPLE));
        }
        return apple;
    }

    @Override
    public boolean canContinueToUse() {
        return bot.isEating();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        if (bot.isUsingItem()) {
            bot.stopUsingItem();
        }
        bot.setWantSprint(false);
        if (!bot.startEating(pickApple())) {
            cooldown = 40;
        }
        retreatTimer = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = bot.getTarget();
        if (target == null) {
            return;
        }
        bot.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (--retreatTimer > 0) {
            return;
        }
        retreatTimer = 10;
        if (bot.distanceToSqr(target) < 36.0D) {
            Vec3 away = DefaultRandomPos.getPosAway(bot, 8, 4, target.position());
            if (away != null) {
                bot.getNavigation().moveTo(away.x, away.y, away.z, 1.0D);
            }
        } else {
            bot.getNavigation().stop();
        }
    }

    @Override
    public void stop() {
        cooldown = 80;
        bot.getNavigation().stop();
    }
}
