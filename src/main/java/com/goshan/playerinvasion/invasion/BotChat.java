package com.goshan.playerinvasion.invasion;

import com.goshan.playerinvasion.PIConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Picks and broadcasts chat lines so they look exactly like a real player typed them. */
public final class BotChat {

    public enum Occasion {
        JOIN, RETURN, SPOT, LOW_HEALTH, KILL, DEATH, LEAVE, TIER_UP, IDLE, REPLY, GREETING_REPLY
    }

    private static final String[] GREETINGS = {
            "привет", "прив", "ку", "хай", "здарова", "здорово", "йоу", "hi", "hello", "hey", "салют", "дратути"
    };

    public static List<? extends String> phrases(Occasion occasion) {
        if (!PIConfig.loaded()) {
            return List.of();
        }
        return switch (occasion) {
            case JOIN -> PIConfig.PHRASES_JOIN.get();
            case RETURN -> PIConfig.PHRASES_RETURN.get();
            case SPOT -> PIConfig.PHRASES_SPOT.get();
            case LOW_HEALTH -> PIConfig.PHRASES_LOW_HEALTH.get();
            case KILL -> PIConfig.PHRASES_KILL.get();
            case DEATH -> PIConfig.PHRASES_DEATH.get();
            case LEAVE -> PIConfig.PHRASES_LEAVE.get();
            case TIER_UP -> PIConfig.PHRASES_TIER_UP.get();
            case IDLE -> PIConfig.PHRASES_IDLE.get();
            case REPLY -> PIConfig.PHRASES_REPLY.get();
            case GREETING_REPLY -> PIConfig.PHRASES_GREETING_REPLY.get();
        };
    }

    /** A random line for the occasion with {player} filled in, or null if nothing fits. */
    @Nullable
    public static String pick(Occasion occasion, RandomSource random, @Nullable String playerName) {
        List<? extends String> all = phrases(occasion);
        if (all.isEmpty()) {
            return null;
        }
        List<String> usable = new ArrayList<>(all.size());
        for (String s : all) {
            if (playerName != null || !s.contains("{player}")) {
                usable.add(s);
            }
        }
        if (usable.isEmpty()) {
            return null;
        }
        String line = usable.get(random.nextInt(usable.size()));
        return playerName != null ? line.replace("{player}", playerName) : line;
    }

    public static boolean isGreeting(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        for (String g : GREETINGS) {
            if (lower.equals(g) || lower.startsWith(g + " ") || lower.startsWith(g + ",") || lower.endsWith(" " + g)) {
                return true;
            }
        }
        return false;
    }

    public static void broadcast(MinecraftServer server, Component botName, String text) {
        Component line = Component.translatable("chat.type.text", botName, Component.literal(text));
        server.getPlayerList().broadcastSystemMessage(line, false);
    }

    private BotChat() {
    }
}
