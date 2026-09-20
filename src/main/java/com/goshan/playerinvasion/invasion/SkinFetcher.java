package com.goshan.playerinvasion.invasion;

import com.goshan.playerinvasion.PlayerInvasion;
import com.mojang.authlib.Agent;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.properties.Property;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Asks Mojang whether the nickname belongs to a real account and, if so, fetches
 * its skin. If it does not (or the account has no skin), a random "donor" account
 * from the config lends its skin, so bots do not all look like Steve. Runs off the
 * server thread; any failure just means Steve/Alex.
 */
public final class SkinFetcher {

    /**
     * Resolves to a profile named {@code name} carrying a textures property, with the
     * real UUID of that nickname when it exists, or to null when nothing could be found.
     */
    public static CompletableFuture<GameProfile> fetch(MinecraftServer server, String name, List<? extends String> donors) {
        List<String> donorPool = new ArrayList<>(donors);
        return CompletableFuture.supplyAsync(() -> resolve(server, name, donorPool), Util.backgroundExecutor())
                .orTimeout(25, TimeUnit.SECONDS)
                .exceptionally(t -> {
                    PlayerInvasion.LOGGER.debug("Skin lookup for {} failed: {}", name, t.toString());
                    return null;
                });
    }

    @Nullable
    private static GameProfile resolve(MinecraftServer server, String name, List<String> donors) {
        GameProfile own = lookup(server, name);
        if (own != null && hasTextures(own)) {
            return own;
        }
        Collections.shuffle(donors);
        int attempts = 0;
        for (String donor : donors) {
            if (attempts++ >= 2) {
                break;
            }
            GameProfile donated = lookup(server, donor);
            if (donated != null && hasTextures(donated)) {
                GameProfile result = new GameProfile(own != null ? own.getId() : null, name);
                for (Property property : donated.getProperties().get("textures")) {
                    result.getProperties().put("textures", property);
                }
                return result;
            }
        }
        return own;
    }

    private static boolean hasTextures(GameProfile profile) {
        return !profile.getProperties().get("textures").isEmpty();
    }

    @Nullable
    private static GameProfile lookup(MinecraftServer server, String name) {
        AtomicReference<GameProfile> found = new AtomicReference<>();
        try {
            server.getProfileRepository().findProfilesByNames(new String[]{name}, Agent.MINECRAFT, new ProfileLookupCallback() {
                @Override
                public void onProfileLookupSucceeded(GameProfile profile) {
                    found.set(profile);
                }

                @Override
                public void onProfileLookupFailed(GameProfile profile, Exception exception) {
                    found.set(null);
                }
            });
        } catch (Exception e) {
            PlayerInvasion.LOGGER.debug("Profile lookup for {} failed: {}", name, e.toString());
            return null;
        }
        GameProfile profile = found.get();
        if (profile == null || profile.getId() == null) {
            return null;
        }
        try {
            GameProfile filled = server.getSessionService().fillProfileProperties(profile, true);
            return filled != null ? filled : profile;
        } catch (Exception e) {
            PlayerInvasion.LOGGER.debug("Texture lookup for {} failed: {}", name, e.toString());
            return profile;
        }
    }

    private SkinFetcher() {
    }
}
