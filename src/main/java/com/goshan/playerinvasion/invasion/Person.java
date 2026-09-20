package com.goshan.playerinvasion.invasion;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * One "person" behind a nickname. Lives in the world's saved data, so a bot that
 * logged off to go mining comes back later with its tier intact - and a bot that
 * died stays dead across restarts.
 */
public final class Person {

    public enum State {
        FRESH, ONLINE, OFFLINE, DEAD
    }

    public final UUID id;
    public final String name;
    public UUID profileId;
    @Nullable
    public String texturesValue;
    @Nullable
    public String texturesSignature;
    public boolean skinResolved;
    public int tier;
    public State state = State.FRESH;
    public long progressTicks;
    public long returnAtTick;
    public int kills;
    public int deaths;
    public int joins;

    public Person(UUID id, String name, int tier) {
        this.id = id;
        this.name = name;
        this.tier = tier;
        this.profileId = id;
    }

    public GameProfile toProfile() {
        GameProfile profile = new GameProfile(profileId, name);
        if (texturesValue != null) {
            profile.getProperties().put("textures", texturesSignature != null
                    ? new Property("textures", texturesValue, texturesSignature)
                    : new Property("textures", texturesValue));
        }
        return profile;
    }

    /** Remembers whatever Mojang told us about this nickname so we never ask twice. */
    public void rememberProfile(@Nullable GameProfile fetched) {
        skinResolved = true;
        if (fetched == null) {
            return;
        }
        if (fetched.getId() != null) {
            profileId = fetched.getId();
        }
        for (Property property : fetched.getProperties().get("textures")) {
            texturesValue = property.getValue();
            texturesSignature = property.getSignature();
            break;
        }
    }

    public boolean isAlive() {
        return state != State.DEAD;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        tag.putUUID("ProfileId", profileId);
        if (texturesValue != null) {
            tag.putString("TexturesValue", texturesValue);
        }
        if (texturesSignature != null) {
            tag.putString("TexturesSignature", texturesSignature);
        }
        tag.putBoolean("SkinResolved", skinResolved);
        tag.putInt("Tier", tier);
        tag.putString("State", state.name());
        tag.putLong("Progress", progressTicks);
        tag.putLong("ReturnAt", returnAtTick);
        tag.putInt("Kills", kills);
        tag.putInt("Deaths", deaths);
        tag.putInt("Joins", joins);
        return tag;
    }

    public static Person load(CompoundTag tag) {
        Person p = new Person(tag.getUUID("Id"), tag.getString("Name"), tag.getInt("Tier"));
        if (tag.hasUUID("ProfileId")) {
            p.profileId = tag.getUUID("ProfileId");
        }
        if (tag.contains("TexturesValue")) {
            p.texturesValue = tag.getString("TexturesValue");
        }
        if (tag.contains("TexturesSignature")) {
            p.texturesSignature = tag.getString("TexturesSignature");
        }
        p.skinResolved = tag.getBoolean("SkinResolved");
        try {
            p.state = State.valueOf(tag.getString("State"));
        } catch (IllegalArgumentException e) {
            p.state = State.OFFLINE;
        }
        if (p.state == State.ONLINE) {
            // the server went down while this one was online
            p.state = State.OFFLINE;
        }
        p.progressTicks = tag.getLong("Progress");
        p.returnAtTick = tag.getLong("ReturnAt");
        p.kills = tag.getInt("Kills");
        p.deaths = tag.getInt("Deaths");
        p.joins = tag.getInt("Joins");
        return p;
    }
}
