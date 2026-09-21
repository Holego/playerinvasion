package com.goshan.playerinvasion.entity.ai;

import com.goshan.playerinvasion.PIConfig;
import com.goshan.playerinvasion.PlayerInvasion;
import com.goshan.playerinvasion.entity.InvaderEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;

/**
 * Iron-tier ranged trick: throws a splash potion. Which one is picked the same
 * way a witch picks one - Slowness to stop you closing the distance, Poison
 * against a healthy target, Weakness up close, Harming otherwise - and the aim
 * and arc match the witch's throw exactly. Diamond and up trade this for lava
 * and cobwebs, which hit harder but need the bot to be much closer.
 */
public class PotionThrowGoal extends TrickGoal {

    private static final double MIN_RANGE = 4.0D;
    private static final double MAX_RANGE = 16.0D;

    private Potion chosenPotion = Potions.HARMING;

    public PotionThrowGoal(InvaderEntity bot) {
        super(bot);
    }

    private static boolean isSplash(ItemStack stack) {
        return stack.is(Items.SPLASH_POTION);
    }

    @Override
    protected boolean ready(LivingEntity target) {
        if (!PIConfig.loaded() || !PIConfig.USE_POTIONS.get() || bot.isDrawingBow() || bot.getHealth() < 9.0F) {
            return false;
        }
        double dist = bot.distanceTo(target);
        if (dist < MIN_RANGE || dist > MAX_RANGE) {
            return false;
        }
        return !bot.getInventory().find(PotionThrowGoal::isSplash).isEmpty();
    }

    @Override
    protected boolean step() {
        if (phase == 0) {
            double flat = flatDistance();
            chosenPotion = pickPotion(flat);
            var matchesChosen = (java.util.function.Predicate<ItemStack>)
                    (s -> isSplash(s) && PotionUtils.getPotion(s) == chosenPotion);
            if (!bot.holdFromInventory(matchesChosen) && !bot.holdFromInventory(PotionThrowGoal::isSplash)) {
                return false;
            }
            bot.getLookControl().setLookAt(target, 60.0F, 60.0F);
            phase = 1;
            return true;
        }
        if (phase == 1 && timer >= 1) {
            if (!isSplash(bot.getMainHandItem())) {
                return false;
            }
            throwPotion();
            phase = 2;
            return true;
        }
        return phase == 2 && timer < 8;
    }

    private double flatDistance() {
        Vec3 vel = target.getDeltaMovement();
        double dx = target.getX() + vel.x - bot.getX();
        double dz = target.getZ() + vel.z - bot.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Same picking logic as {@code Witch.performRangedAttack}, minus the raider-healing branch. */
    private Potion pickPotion(double flat) {
        if (flat >= 8.0D && !target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
            return Potions.SLOWNESS;
        }
        if (target.getHealth() >= 8.0F && !target.hasEffect(MobEffects.POISON)) {
            return Potions.POISON;
        }
        if (flat <= 3.0D && !target.hasEffect(MobEffects.WEAKNESS) && bot.getRandom().nextFloat() < 0.25F) {
            return Potions.WEAKNESS;
        }
        return Potions.HARMING;
    }

    private void throwPotion() {
        ItemStack held = bot.getMainHandItem();
        Vec3 vel = target.getDeltaMovement();
        double dx = target.getX() + vel.x - bot.getX();
        double dy = target.getEyeY() - 1.1D - bot.getY();
        double dz = target.getZ() + vel.z - bot.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);

        ItemStack thrownStack = held.copy();
        thrownStack.setCount(1);
        ThrownPotion thrown = new ThrownPotion(bot.level(), bot);
        thrown.setItem(thrownStack);
        thrown.setXRot(thrown.getXRot() + 20.0F);
        thrown.shoot(dx, dy + flat * 0.2D, dz, 0.75F, 8.0F);
        bot.level().playSound(null, bot.getX(), bot.getY(), bot.getZ(), SoundEvents.WITCH_THROW,
                bot.getSoundSource(), 0.8F, 0.8F + bot.getRandom().nextFloat() * 0.4F);
        bot.level().addFreshEntity(thrown);
        held.shrink(1);
        bot.swing(InteractionHand.MAIN_HAND);
        PlayerInvasion.LOGGER.debug("{} throws a splash potion of {} at {} ({} blocks)", bot.getBotName(),
                thrownStack.getHoverName().getString(), target.getName().getString(), Math.round(flat));
    }

    @Override
    protected int cooldownTicks() {
        return 50 + bot.getRandom().nextInt(70);
    }
}
