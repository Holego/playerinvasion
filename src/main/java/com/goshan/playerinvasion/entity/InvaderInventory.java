package com.goshan.playerinvasion.entity;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * The bag a fake player carries besides its equipment: consumables (lava, cobwebs,
 * apples, crystals...), building blocks, spare tools and whatever it picked up.
 * Same size as a real inventory (36) so death drops look like a real player's.
 */
public final class InvaderInventory {

    private final NonNullList<ItemStack> slots;

    public InvaderInventory(int size) {
        this.slots = NonNullList.withSize(size, ItemStack.EMPTY);
    }

    public int size() {
        return slots.size();
    }

    public ItemStack get(int slot) {
        return slots.get(slot);
    }

    public void clear() {
        for (int i = 0; i < slots.size(); i++) {
            slots.set(i, ItemStack.EMPTY);
        }
    }

    public boolean isEmpty() {
        for (ItemStack stack : slots) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Inserts as much of the stack as fits; the argument is shrunk accordingly. Returns true if anything was taken. */
    public boolean add(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        int before = stack.getCount();
        for (int i = 0; i < slots.size() && !stack.isEmpty(); i++) {
            ItemStack existing = slots.get(i);
            if (!existing.isEmpty() && ItemStack.isSameItemSameTags(existing, stack) && existing.getCount() < existing.getMaxStackSize()) {
                int move = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
                existing.grow(move);
                stack.shrink(move);
            }
        }
        for (int i = 0; i < slots.size() && !stack.isEmpty(); i++) {
            if (slots.get(i).isEmpty()) {
                slots.set(i, stack.copy());
                stack.setCount(0);
            }
        }
        return stack.getCount() < before;
    }

    public boolean canAdd(ItemStack stack) {
        for (ItemStack existing : slots) {
            if (existing.isEmpty()) {
                return true;
            }
            if (ItemStack.isSameItemSameTags(existing, stack) && existing.getCount() < existing.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    public int count(Item item) {
        int n = 0;
        for (ItemStack stack : slots) {
            if (stack.is(item)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    public boolean has(Item item) {
        return count(item) > 0;
    }

    /** Removes up to {@code amount} of the item. Returns how many were actually removed. */
    public int take(Item item, int amount) {
        int taken = 0;
        for (int i = 0; i < slots.size() && taken < amount; i++) {
            ItemStack stack = slots.get(i);
            if (stack.is(item)) {
                int move = Math.min(amount - taken, stack.getCount());
                stack.shrink(move);
                taken += move;
                if (stack.isEmpty()) {
                    slots.set(i, ItemStack.EMPTY);
                }
            }
        }
        return taken;
    }

    /** Removes and returns the first stack matching the predicate, or EMPTY. */
    public ItemStack takeFirst(Predicate<ItemStack> predicate) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = slots.get(i);
            if (!stack.isEmpty() && predicate.test(stack)) {
                slots.set(i, ItemStack.EMPTY);
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Returns (without removing) the first stack matching the predicate, or EMPTY. */
    public ItemStack find(Predicate<ItemStack> predicate) {
        for (ItemStack stack : slots) {
            if (!stack.isEmpty() && predicate.test(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Swaps the given stack into the first empty slot (used to stash the weapon while eating or digging). */
    public void stash(ItemStack stack) {
        if (!stack.isEmpty()) {
            add(stack);
        }
    }

    public void dropAll(LivingEntity owner) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = slots.get(i);
            if (!stack.isEmpty()) {
                owner.spawnAtLocation(stack);
                slots.set(i, ItemStack.EMPTY);
            }
        }
    }

    public ListTag save() {
        ListTag list = new ListTag();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = slots.get(i);
            if (!stack.isEmpty()) {
                CompoundTag tag = new CompoundTag();
                tag.putByte("Slot", (byte) i);
                stack.save(tag);
                list.add(tag);
            }
        }
        return list;
    }

    public void load(ListTag list) {
        clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            int slot = tag.getByte("Slot") & 255;
            if (slot < slots.size()) {
                slots.set(slot, ItemStack.of(tag));
            }
        }
    }

    public static boolean isListTag(Tag tag) {
        return tag instanceof ListTag;
    }
}
