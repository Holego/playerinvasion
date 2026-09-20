package com.goshan.playerinvasion.entity;

import com.goshan.playerinvasion.PIConfig;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * The four "stages of development" of a fake player. Tier 0 is a fresh spawn with
 * a wooden axe; tier 3 is a full netherite crystal-PvP kit. Everything a bot owns
 * comes from here (or from what it picked up), so what drops on death is exactly
 * what a real player at that stage would have carried.
 */
public final class Loadouts {

    public static final int WOOD = 0;
    public static final int IRON = 1;
    public static final int DIAMOND = 2;
    public static final int NETHERITE = 3;

    public static String tierName(int tier) {
        return switch (tier) {
            case IRON -> "iron";
            case DIAMOND -> "diamond";
            case NETHERITE -> "netherite";
            default -> "wood";
        };
    }

    public static int clamp(int tier) {
        return Math.max(WOOD, Math.min(NETHERITE, tier));
    }

    /** Replaces every piece of equipment and the whole inventory with the kit of the given tier. */
    public static void apply(InvaderEntity bot, int tier) {
        tier = clamp(tier);
        boolean ench = !PIConfig.loaded() || PIConfig.ENCHANTED_GEAR.get();
        InvaderInventory inv = bot.getInventory();
        inv.clear();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            bot.setItemSlot(slot, ItemStack.EMPTY);
        }

        switch (tier) {
            case WOOD -> {
                bot.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_AXE));
                inv.add(new ItemStack(Items.WOODEN_PICKAXE));
                inv.add(new ItemStack(Items.DIRT, 16));
            }
            case IRON -> {
                bot.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                bot.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                armor(bot, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS, null, 0);
                inv.add(new ItemStack(Items.IRON_AXE));
                inv.add(new ItemStack(Items.IRON_PICKAXE));
                inv.add(new ItemStack(Items.LAVA_BUCKET));
                inv.add(new ItemStack(Items.LAVA_BUCKET));
                inv.add(new ItemStack(Items.COBWEB, 8));
                inv.add(new ItemStack(Items.COBBLESTONE, 64));
            }
            case DIAMOND -> {
                bot.setItemSlot(EquipmentSlot.MAINHAND, enchanted(new ItemStack(Items.DIAMOND_SWORD), ench, Enchantments.SHARPNESS, 2));
                bot.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                armor(bot, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
                        ench ? Enchantments.ALL_DAMAGE_PROTECTION : null, 2);
                inv.add(new ItemStack(Items.DIAMOND_AXE));
                inv.add(enchanted(new ItemStack(Items.DIAMOND_PICKAXE), ench, Enchantments.BLOCK_EFFICIENCY, 2));
                inv.add(enchanted(new ItemStack(Items.BOW), ench, Enchantments.POWER_ARROWS, 2));
                inv.add(new ItemStack(Items.ARROW, 32));
                inv.add(new ItemStack(Items.GOLDEN_APPLE, 8));
                inv.add(new ItemStack(Items.LAVA_BUCKET));
                inv.add(new ItemStack(Items.LAVA_BUCKET));
                inv.add(new ItemStack(Items.COBWEB, 16));
                inv.add(new ItemStack(Items.COBBLESTONE, 64));
            }
            default -> {
                bot.setItemSlot(EquipmentSlot.MAINHAND, enchanted(new ItemStack(Items.NETHERITE_SWORD), ench, Enchantments.SHARPNESS, 4));
                // end-game kit: a totem in the off hand instead of a shield, spare totems, pearls, elytra + rockets
                bot.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
                inv.add(new ItemStack(Items.TOTEM_OF_UNDYING));
                inv.add(new ItemStack(Items.TOTEM_OF_UNDYING));
                inv.add(new ItemStack(Items.ENDER_PEARL, 16));
                inv.add(enchanted(new ItemStack(Items.ELYTRA), ench, Enchantments.UNBREAKING, 3));
                inv.add(rockets(32));
                armor(bot, Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS,
                        ench ? Enchantments.ALL_DAMAGE_PROTECTION : null, 4);
                inv.add(new ItemStack(Items.NETHERITE_AXE));
                inv.add(enchanted(new ItemStack(Items.NETHERITE_PICKAXE), ench, Enchantments.BLOCK_EFFICIENCY, 4));
                inv.add(enchanted(new ItemStack(Items.BOW), ench, Enchantments.POWER_ARROWS, 4));
                inv.add(new ItemStack(Items.ARROW, 64));
                inv.add(new ItemStack(Items.GOLDEN_APPLE, 16));
                inv.add(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 2));
                inv.add(new ItemStack(Items.END_CRYSTAL, 16));
                inv.add(new ItemStack(Items.OBSIDIAN, 32));
                inv.add(new ItemStack(Items.RESPAWN_ANCHOR, 4));
                inv.add(new ItemStack(Items.GLOWSTONE, 16));
                inv.add(new ItemStack(Items.RED_BED, 4));
                inv.add(new ItemStack(Items.LAVA_BUCKET));
                inv.add(new ItemStack(Items.LAVA_BUCKET));
                inv.add(new ItemStack(Items.COBWEB, 16));
                inv.add(new ItemStack(Items.COBBLESTONE, 64));
            }
        }
    }

    /** Firework rockets with flight duration 3 - what an elytra player actually carries. */
    private static ItemStack rockets(int count) {
        ItemStack stack = new ItemStack(Items.FIREWORK_ROCKET, count);
        FireworkRocketItem.setDuration(stack, (byte) 3);
        return stack;
    }

    private static void armor(InvaderEntity bot, Item head, Item chest, Item legs, Item feet, Enchantment enchant, int level) {
        bot.setItemSlot(EquipmentSlot.HEAD, enchanted(new ItemStack(head), enchant != null, enchant, level));
        bot.setItemSlot(EquipmentSlot.CHEST, enchanted(new ItemStack(chest), enchant != null, enchant, level));
        bot.setItemSlot(EquipmentSlot.LEGS, enchanted(new ItemStack(legs), enchant != null, enchant, level));
        bot.setItemSlot(EquipmentSlot.FEET, enchanted(new ItemStack(feet), enchant != null, enchant, level));
    }

    private static ItemStack enchanted(ItemStack stack, boolean enabled, Enchantment enchantment, int level) {
        if (enabled && enchantment != null && level > 0) {
            stack.enchant(enchantment, level);
        }
        return stack;
    }

    private Loadouts() {
    }
}
