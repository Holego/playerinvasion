package com.goshan.playerinvasion.invasion;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.world.level.GameType;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Tab-list entries for people who are not ServerPlayers. Vanilla's packet only
 * takes ServerPlayer objects, so the entry is written straight into the wire
 * format (UUID, name + textures, game mode, listed flag, latency) and decoded
 * back into a packet - the client cannot tell the difference.
 */
public final class TabList {

    public static Packet<?> add(GameProfile profile, int latency) {
        EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions = EnumSet.of(
                ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeEnumSet(actions, ClientboundPlayerInfoUpdatePacket.Action.class);
            buf.writeVarInt(1);
            buf.writeUUID(profile.getId());
            buf.writeUtf(profile.getName(), 16);
            buf.writeGameProfileProperties(profile.getProperties());
            buf.writeVarInt(GameType.SURVIVAL.getId());
            buf.writeBoolean(true);
            buf.writeVarInt(latency);
            return new ClientboundPlayerInfoUpdatePacket(buf);
        } finally {
            buf.release();
        }
    }

    public static Packet<?> remove(UUID profileId) {
        return new ClientboundPlayerInfoRemovePacket(List.of(profileId));
    }

    private TabList() {
    }
}
