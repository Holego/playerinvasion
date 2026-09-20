package com.goshan.playerinvasion.invasion;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** World-level state: advancement counter and every person that ever joined. */
public class InvasionSavedData extends SavedData {

    private static final String NAME = "playerinvasion";

    public int advancements;
    public final Map<UUID, Person> persons = new LinkedHashMap<>();

    public static InvasionSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(InvasionSavedData::load, InvasionSavedData::new, NAME);
    }

    public static InvasionSavedData load(CompoundTag tag) {
        InvasionSavedData data = new InvasionSavedData();
        data.advancements = tag.getInt("Advancements");
        ListTag list = tag.getList("Persons", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Person p = Person.load(list.getCompound(i));
            data.persons.put(p.id, p);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("Advancements", advancements);
        ListTag list = new ListTag();
        for (Person p : persons.values()) {
            list.add(p.save());
        }
        tag.put("Persons", list);
        return tag;
    }
}
