package com.goshan.playerinvasion.invasion;

import com.goshan.playerinvasion.PIConfig;
import com.goshan.playerinvasion.PIEntities;
import com.goshan.playerinvasion.PlayerInvasion;
import com.goshan.playerinvasion.entity.InvaderEntity;
import com.goshan.playerinvasion.entity.Loadouts;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side coordinator: rolls for new joins, keeps the roster of who is online,
 * lets bots log off and come back stronger, moves them next to players when they
 * end up alone, and speaks for them in chat.
 */
public final class InvasionManager {

    @Nullable
    private static InvasionManager instance;

    private final MinecraftServer server;
    private final InvasionSavedData data;
    private final RandomSource random = RandomSource.create();
    private final Map<UUID, InvaderEntity> online = new LinkedHashMap<>();
    private final Set<UUID> pending = new HashSet<>();
    private final Map<UUID, Integer> relocateTimers = new HashMap<>();
    private final Map<UUID, Long> lastSpotSay = new HashMap<>();
    private final List<DelayedTask> tasks = new ArrayList<>();
    private int rollTimer;
    private long sessionStartTick = -1L;

    private record DelayedTask(long tick, Runnable action) {
    }

    private InvasionManager(MinecraftServer server) {
        this.server = server;
        this.data = InvasionSavedData.get(server);
    }

    public static void start(MinecraftServer server) {
        instance = new InvasionManager(server);
    }

    public static void shutdown() {
        if (instance != null) {
            instance.onServerStopping();
            instance = null;
        }
    }

    @Nullable
    public static InvasionManager current() {
        return instance;
    }

    public MinecraftServer server() {
        return server;
    }

    public InvasionSavedData data() {
        return data;
    }

    public long now() {
        return server.overworld().getGameTime();
    }

    private boolean enabled() {
        return PIConfig.loaded() && PIConfig.ENABLED.get();
    }

    // ------------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------------

    public void tick() {
        runDueTasks();
        validateOnline();

        if (!enabled()) {
            return;
        }
        boolean anyPlayers = !server.getPlayerList().getPlayers().isEmpty();
        if (anyPlayers && sessionStartTick < 0) {
            sessionStartTick = now();
        }

        if (server.getTickCount() % 20 == 0) {
            tickProgress();
            tickRelocation();
        }

        if (!anyPlayers) {
            return;
        }
        rollTimer++;
        int interval = PIConfig.SPAWN_CHECK_SECONDS.get() * 20;
        if (rollTimer >= interval) {
            rollTimer = 0;
            roll();
        }
    }

    private void runDueTasks() {
        if (tasks.isEmpty()) {
            return;
        }
        long now = now();
        Iterator<DelayedTask> it = tasks.iterator();
        List<Runnable> due = new ArrayList<>();
        while (it.hasNext()) {
            DelayedTask task = it.next();
            if (task.tick() <= now) {
                due.add(task.action());
                it.remove();
            }
        }
        for (Runnable r : due) {
            try {
                r.run();
            } catch (Exception e) {
                PlayerInvasion.LOGGER.error("Delayed invasion task failed", e);
            }
        }
    }

    private void later(int ticks, Runnable action) {
        tasks.add(new DelayedTask(now() + ticks, action));
    }

    /** Drops entities that vanished (chunk unload, /kill, dimension change) from the roster. */
    private void validateOnline() {
        if (online.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, InvaderEntity>> it = online.entrySet().iterator();
        List<Runnable> followUps = new ArrayList<>();
        while (it.hasNext()) {
            Map.Entry<UUID, InvaderEntity> entry = it.next();
            InvaderEntity bot = entry.getValue();
            if (!bot.isRemoved()) {
                continue;
            }
            Person person = data.persons.get(entry.getKey());
            Entity.RemovalReason reason = bot.getRemovalReason();
            if (reason == Entity.RemovalReason.CHANGED_DIMENSION) {
                InvaderEntity copy = findByPerson(entry.getKey());
                if (copy != null) {
                    entry.setValue(copy);
                    continue;
                }
            }
            if (reason == Entity.RemovalReason.KILLED || (person != null && person.state == Person.State.DEAD)) {
                it.remove();
                continue;
            }
            it.remove();
            if (person != null && person.state == Person.State.ONLINE) {
                followUps.add(() -> logoutNow(person, bot.getProfile(), false));
            }
        }
        followUps.forEach(Runnable::run);
    }

    @Nullable
    private InvaderEntity findByPerson(UUID personId) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof InvaderEntity bot && !bot.isRemoved() && personId.equals(bot.getPersonId())) {
                    return bot;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------
    // Progression: develop while idle or offline, give up and come back stronger
    // ------------------------------------------------------------------------

    private void tickProgress() {
        long tierUpTicks = PIConfig.TIER_UP_MINUTES.get() * 1200L;
        long giveUpTicks = PIConfig.GIVE_UP_MINUTES.get() * 1200L;
        int maxTier = PIConfig.MAX_TIER.get();
        long now = now();

        List<Runnable> followUps = new ArrayList<>();
        for (Person person : data.persons.values()) {
            if (person.state == Person.State.ONLINE) {
                InvaderEntity bot = online.get(person.id);
                if (bot == null) {
                    continue;
                }
                // the longer they live, the stronger they get
                person.progressTicks += 20;
                if (person.progressTicks >= tierUpTicks && person.tier < maxTier) {
                    person.progressTicks = 0;
                    person.tier++;
                    bot.setTier(person.tier);
                    data.setDirty();
                    botSay(bot, BotChat.Occasion.TIER_UP);
                }
                if (bot.ticksSinceSawPlayer() > giveUpTicks && !bot.isEating()) {
                    followUps.add(() -> leaveVoluntarily(person, bot));
                }
            } else if (person.state == Person.State.OFFLINE) {
                person.progressTicks += 20;
                if (person.progressTicks >= tierUpTicks && person.tier < maxTier) {
                    person.progressTicks = 0;
                    person.tier++;
                    data.setDirty();
                }
            }
        }
        followUps.forEach(Runnable::run);
        if (now % 1200 == 0) {
            data.setDirty();
        }
    }

    private void tickRelocation() {
        int distance = PIConfig.RELOCATE_DISTANCE.get();
        int after = PIConfig.RELOCATE_AFTER_SECONDS.get() * 20;
        List<Runnable> followUps = new ArrayList<>();
        for (Map.Entry<UUID, InvaderEntity> entry : online.entrySet()) {
            InvaderEntity bot = entry.getValue();
            if (bot.isRemoved()) {
                continue;
            }
            ServerPlayer nearest = nearestPlayer(bot.level(), bot.position());
            if (nearest != null && nearest.distanceToSqr(bot) < (double) distance * distance) {
                relocateTimers.remove(entry.getKey());
                continue;
            }
            int t = relocateTimers.merge(entry.getKey(), 20, Integer::sum);
            if (t < after) {
                continue;
            }
            relocateTimers.remove(entry.getKey());
            Person person = data.persons.get(entry.getKey());
            if (person == null) {
                continue;
            }
            ServerPlayer anyone = pickTargetPlayer((ServerLevel) bot.level());
            if (anyone != null && anyone.level() == bot.level()) {
                BlockPos pos = SpawnPlacer.find((ServerLevel) bot.level(), anyone,
                        PIConfig.MIN_SPAWN_DISTANCE.get(), PIConfig.MAX_SPAWN_DISTANCE.get(), random);
                if (pos != null) {
                    bot.getNavigation().stop();
                    bot.setTarget(null);
                    bot.teleportTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
                    continue;
                }
            }
            followUps.add(() -> leaveVoluntarily(person, bot));
        }
        followUps.forEach(Runnable::run);
    }

    @Nullable
    private ServerPlayer nearestPlayer(net.minecraft.world.level.Level level, net.minecraft.world.phys.Vec3 pos) {
        ServerPlayer best = null;
        double bestDist = Double.MAX_VALUE;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() != level || player.isSpectator()) {
                continue;
            }
            double d = player.position().distanceToSqr(pos);
            if (d < bestDist) {
                bestDist = d;
                best = player;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------------
    // Rolling and joining
    // ------------------------------------------------------------------------

    public double currentChance() {
        if (!PIConfig.loaded()) {
            return 0.0D;
        }
        double chance = PIConfig.BASE_CHANCE.get() + PIConfig.CHANCE_PER_ADVANCEMENT.get() * data.advancements;
        return Math.min(PIConfig.MAX_CHANCE.get(), chance);
    }

    public int onlineCount() {
        return online.size() + pending.size();
    }

    private boolean canSpawnMore() {
        if (!enabled() || server.getWorldData().getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }
        if (sessionStartTick >= 0 && now() - sessionStartTick < PIConfig.FIRST_JOIN_DELAY_SECONDS.get() * 20L) {
            return false;
        }
        return onlineCount() < PIConfig.MAX_BOTS.get();
    }

    private void roll() {
        if (!canSpawnMore() || pickTargetPlayer(null) == null) {
            return;
        }
        if (random.nextDouble() < currentChance()) {
            spawnOne(null, -1);
        }
    }

    /** Survival/adventure players are fair game; creative and spectators are ignored. */
    @Nullable
    public ServerPlayer pickTargetPlayer(@Nullable ServerLevel preferredLevel) {
        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || player.isCreative() || !player.isAlive()) {
                continue;
            }
            if (preferredLevel != null && player.level() != preferredLevel) {
                continue;
            }
            candidates.add(player);
        }
        if (candidates.isEmpty() && preferredLevel != null) {
            return pickTargetPlayer(null);
        }
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    /**
     * Brings one more person online. {@code forcedName} / {@code forcedTier} are for the
     * command; the natural flow passes null / -1 and prefers someone who is due to return.
     */
    public boolean spawnOne(@Nullable String forcedName, int forcedTier) {
        ServerPlayer near = pickTargetPlayer(null);
        if (near == null) {
            return false;
        }
        Person person = null;
        if (forcedName != null) {
            if (forcedName.isBlank() || forcedName.length() > 16) {
                return false;
            }
            person = findPersonByName(forcedName);
            if (person != null && (person.state == Person.State.ONLINE || pending.contains(person.id))) {
                return false;
            }
            if (person != null && person.state == Person.State.DEAD) {
                if (PIConfig.PERMANENT_DEATH.get()) {
                    return false;
                }
                person.state = Person.State.OFFLINE;
            }
            if (person == null) {
                person = newPerson(forcedName);
            }
        } else {
            List<Person> returning = new ArrayList<>();
            long now = now();
            for (Person p : data.persons.values()) {
                if (p.state == Person.State.OFFLINE && p.returnAtTick <= now && !pending.contains(p.id)) {
                    returning.add(p);
                }
            }
            if (!returning.isEmpty() && random.nextInt(10) < 7) {
                person = returning.get(random.nextInt(returning.size()));
            } else {
                String name = freeName();
                if (name == null) {
                    if (returning.isEmpty()) {
                        return false;
                    }
                    person = returning.get(random.nextInt(returning.size()));
                } else {
                    person = newPerson(name);
                }
            }
        }
        if (forcedTier >= 0) {
            person.tier = Loadouts.clamp(forcedTier);
        }
        join(person, near);
        return true;
    }

    private Person newPerson(String name) {
        Person person = new Person(UUID.randomUUID(), name, PIConfig.START_TIER.get());
        data.persons.put(person.id, person);
        data.setDirty();
        return person;
    }

    @Nullable
    public Person findPersonByName(String name) {
        for (Person p : data.persons.values()) {
            if (p.name.equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    @Nullable
    private String freeName() {
        List<? extends String> pool = PIConfig.NAMES.get();
        if (pool.isEmpty()) {
            return null;
        }
        Set<String> taken = new HashSet<>();
        for (Person p : data.persons.values()) {
            taken.add(p.name.toLowerCase(Locale.ROOT));
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            taken.add(player.getGameProfile().getName().toLowerCase(Locale.ROOT));
        }
        List<String> free = new ArrayList<>();
        for (String name : pool) {
            if (!taken.contains(name.toLowerCase(Locale.ROOT))) {
                free.add(name);
            }
        }
        if (!free.isEmpty()) {
            return free.get(random.nextInt(free.size()));
        }
        // pool exhausted: derive a new one
        for (int attempt = 0; attempt < 50; attempt++) {
            String base = pool.get(random.nextInt(pool.size()));
            String candidate = base.substring(0, Math.min(base.length(), 12)) + (10 + random.nextInt(90));
            if (!taken.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
        return null;
    }

    private void join(Person person, ServerPlayer near) {
        pending.add(person.id);
        UUID nearId = near.getUUID();
        if (person.skinResolved || !PIConfig.ONLINE_SKINS.get()) {
            if (!person.skinResolved) {
                person.rememberProfile(null);
            }
            finishJoin(person, nearId);
            return;
        }
        SkinFetcher.fetch(server, person.name, PIConfig.SKIN_DONORS.get()).thenAccept(profile -> server.execute(() -> {
            if (instance != this) {
                return;
            }
            person.rememberProfile(profile);
            data.setDirty();
            finishJoin(person, nearId);
        }));
    }

    private void finishJoin(Person person, UUID nearId) {
        pending.remove(person.id);
        if (!enabled() || person.state == Person.State.ONLINE || online.size() >= PIConfig.MAX_BOTS.get()) {
            return;
        }
        ServerPlayer near = server.getPlayerList().getPlayer(nearId);
        if (near == null || near.isSpectator() || near.isCreative()) {
            near = pickTargetPlayer(null);
        }
        if (near == null) {
            return;
        }
        ServerLevel level = near.serverLevel();
        BlockPos pos = SpawnPlacer.find(level, near, PIConfig.MIN_SPAWN_DISTANCE.get(), PIConfig.MAX_SPAWN_DISTANCE.get(), random);
        if (pos == null) {
            PlayerInvasion.LOGGER.debug("No spawn spot for {} near {}", person.name, near.getGameProfile().getName());
            return;
        }

        GameProfile profile = person.toProfile();
        InvaderEntity bot = new InvaderEntity(PIEntities.INVADER.get(), level);
        bot.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
        bot.setIdentity(person.id, profile);
        bot.setTier(person.tier);
        if (!level.addFreshEntity(bot)) {
            return;
        }

        boolean returning = person.joins > 0;
        person.state = Person.State.ONLINE;
        person.joins++;
        data.setDirty();
        online.put(person.id, bot);

        if (PIConfig.ANNOUNCE_JOIN_LEAVE.get()) {
            broadcastPacket(TabList.add(profile, 30 + random.nextInt(120)));
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("multiplayer.player.joined", bot.getDisplayName()).withStyle(ChatFormatting.YELLOW), false);
        }
        PlayerInvasion.LOGGER.info("{} joined the game (tier {}, {})", person.name, person.tier, returning ? "returning" : "new");

        later(40 + random.nextInt(80), () -> {
            if (!bot.isRemoved()) {
                botSay(bot, returning ? BotChat.Occasion.RETURN : BotChat.Occasion.JOIN);
            }
        });
    }

    private void broadcastPacket(Packet<?> packet) {
        server.getPlayerList().broadcastAll(packet);
    }

    /** Gives an identity to a bot that was not created by us (/summon, another mod). */
    public void adopt(InvaderEntity bot) {
        String name = freeName();
        if (name == null) {
            name = "Steve" + (10 + random.nextInt(90));
        }
        Person person = newPerson(name);
        person.rememberProfile(null);
        GameProfile profile = person.toProfile();
        bot.setIdentity(person.id, profile);
        bot.setTier(person.tier);
        person.state = Person.State.ONLINE;
        person.joins++;
        data.setDirty();
        online.put(person.id, bot);
        if (PIConfig.loaded() && PIConfig.ANNOUNCE_JOIN_LEAVE.get()) {
            broadcastPacket(TabList.add(profile, 30 + random.nextInt(120)));
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("multiplayer.player.joined", bot.getDisplayName()).withStyle(ChatFormatting.YELLOW), false);
        }
        later(40 + random.nextInt(80), () -> {
            if (!bot.isRemoved()) {
                botSay(bot, BotChat.Occasion.JOIN);
            }
        });
    }

    // ------------------------------------------------------------------------
    // Leaving and dying
    // ------------------------------------------------------------------------

    /** Says goodbye, then logs off a moment later. */
    public void leaveVoluntarily(Person person, InvaderEntity bot) {
        if (person.state != Person.State.ONLINE) {
            return;
        }
        botSay(bot, BotChat.Occasion.LEAVE);
        person.state = Person.State.OFFLINE; // blocks a second call while we wait
        later(20 + random.nextInt(40), () -> {
            if (person.state == Person.State.DEAD) {
                return; // killed while saying goodbye
            }
            GameProfile profile = bot.getProfile();
            if (!bot.isRemoved()) {
                bot.discard();
            }
            online.remove(person.id);
            person.state = Person.State.ONLINE;
            logoutNow(person, profile, true);
        });
    }

    /** Immediate logout: tab entry gone, "left the game", may come back later. */
    private void logoutNow(Person person, @Nullable GameProfile profile, boolean scheduleReturn) {
        online.remove(person.id);
        relocateTimers.remove(person.id);
        person.state = Person.State.OFFLINE;
        int min = PIConfig.loaded() ? PIConfig.RETURN_MIN_MINUTES.get() : 5;
        int max = PIConfig.loaded() ? Math.max(min, PIConfig.RETURN_MAX_MINUTES.get()) : 20;
        person.returnAtTick = now() + (scheduleReturn ? (min + random.nextInt(max - min + 1)) * 1200L : 0L);
        data.setDirty();
        announceLeft(person, profile);
        PlayerInvasion.LOGGER.info("{} left the game", person.name);
    }

    private void announceLeft(Person person, @Nullable GameProfile profile) {
        if (!PIConfig.loaded() || !PIConfig.ANNOUNCE_JOIN_LEAVE.get()) {
            return;
        }
        UUID profileId = profile != null && profile.getId() != null ? profile.getId() : person.profileId;
        broadcastPacket(TabList.remove(profileId));
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("multiplayer.player.left", Component.literal(person.name)).withStyle(ChatFormatting.YELLOW), false);
    }

    public void onBotDied(InvaderEntity bot, DamageSource cause, @Nullable Component deathMessage) {
        UUID id = bot.getPersonId();
        Person person = id != null ? data.persons.get(id) : null;
        online.remove(id);
        relocateTimers.remove(id);

        if (deathMessage != null && server.getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES)) {
            server.getPlayerList().broadcastSystemMessage(deathMessage, false);
        }
        if (person == null) {
            return;
        }
        person.deaths++;
        boolean permanent = PIConfig.loaded() && PIConfig.PERMANENT_DEATH.get();
        person.state = Person.State.DEAD;
        data.setDirty();
        PlayerInvasion.LOGGER.info("{} died ({}){}", person.name, cause.getMsgId(), permanent ? " - permanently" : "");

        GameProfile profile = bot.getProfile();
        String killerName = cause.getEntity() instanceof Player p ? p.getGameProfile().getName() : null;
        later(20 + random.nextInt(40), () -> sayAs(person, BotChat.Occasion.DEATH, killerName));
        later(80 + random.nextInt(60), () -> {
            announceLeft(person, profile);
            if (!permanent) {
                person.state = Person.State.OFFLINE;
                person.tier = PIConfig.START_TIER.get();
                person.progressTicks = 0;
                int min = PIConfig.RETURN_MIN_MINUTES.get();
                int max = Math.max(min, PIConfig.RETURN_MAX_MINUTES.get());
                person.returnAtTick = now() + (min + random.nextInt(max - min + 1)) * 1200L;
                data.setDirty();
            }
            PlayerInvasion.LOGGER.info("{} left the game", person.name);
        });
    }

    public void onBotKilledPlayer(InvaderEntity bot, ServerPlayer victim) {
        UUID id = bot.getPersonId();
        Person person = id != null ? data.persons.get(id) : null;
        if (person != null) {
            person.kills++;
            data.setDirty();
        }
        String victimName = victim.getGameProfile().getName();
        later(20 + random.nextInt(40), () -> {
            if (!bot.isRemoved()) {
                botSay(bot, BotChat.Occasion.KILL, victimName);
            }
        });
    }

    public void onBotSpotted(InvaderEntity bot, Player target) {
        UUID id = bot.getPersonId();
        if (id == null) {
            return;
        }
        long now = now();
        Long last = lastSpotSay.get(id);
        if (last != null && now - last < 1200L) {
            return;
        }
        lastSpotSay.put(id, now);
        String name = target.getGameProfile().getName();
        later(5 + random.nextInt(20), () -> {
            if (!bot.isRemoved()) {
                botSay(bot, BotChat.Occasion.SPOT, name);
            }
        });
    }

    // ------------------------------------------------------------------------
    // Chat
    // ------------------------------------------------------------------------

    public void botSay(InvaderEntity bot, BotChat.Occasion occasion) {
        Player nearest = bot.nearestRealPlayer(64.0D);
        botSay(bot, occasion, nearest != null ? nearest.getGameProfile().getName() : null);
    }

    public void botSay(InvaderEntity bot, BotChat.Occasion occasion, @Nullable String playerName) {
        if (!PIConfig.loaded() || !PIConfig.CHAT_ENABLED.get() || random.nextDouble() >= PIConfig.CHAT_CHANCE.get()) {
            return;
        }
        String line = BotChat.pick(occasion, random, playerName);
        if (line != null) {
            BotChat.broadcast(server, bot.getDisplayName(), line);
        }
    }

    private void sayAs(Person person, BotChat.Occasion occasion, @Nullable String playerName) {
        if (!PIConfig.loaded() || !PIConfig.CHAT_ENABLED.get() || random.nextDouble() >= PIConfig.CHAT_CHANCE.get()) {
            return;
        }
        String line = BotChat.pick(occasion, random, playerName);
        if (line != null) {
            BotChat.broadcast(server, Component.literal(person.name), line);
        }
    }

    /** A real player said something: maybe one of ours answers. */
    public void onPlayerChat(ServerPlayer player, String message) {
        if (online.isEmpty() || !PIConfig.loaded() || !PIConfig.CHAT_ENABLED.get()) {
            return;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        InvaderEntity addressed = null;
        for (InvaderEntity bot : online.values()) {
            if (!bot.isRemoved() && !bot.getBotName().isEmpty() && lower.contains(bot.getBotName().toLowerCase(Locale.ROOT))) {
                addressed = bot;
                break;
            }
        }
        boolean greeting = BotChat.isGreeting(message);
        if (addressed == null && !greeting && random.nextInt(6) != 0) {
            return;
        }
        InvaderEntity speaker = addressed;
        if (speaker == null) {
            List<InvaderEntity> alive = new ArrayList<>();
            for (InvaderEntity bot : online.values()) {
                if (!bot.isRemoved()) {
                    alive.add(bot);
                }
            }
            if (alive.isEmpty()) {
                return;
            }
            speaker = alive.get(random.nextInt(alive.size()));
        }
        InvaderEntity bot = speaker;
        String name = player.getGameProfile().getName();
        later(20 + random.nextInt(60), () -> {
            if (!bot.isRemoved()) {
                String line = BotChat.pick(greeting ? BotChat.Occasion.GREETING_REPLY : BotChat.Occasion.REPLY, random, name);
                if (line != null) {
                    BotChat.broadcast(server, bot.getDisplayName(), line);
                }
            }
        });
    }

    // ------------------------------------------------------------------------
    // Hooks
    // ------------------------------------------------------------------------

    public void onAdvancementEarned(ServerPlayer player) {
        data.advancements++;
        data.setDirty();
        if (!PIConfig.loaded() || !canSpawnMore()) {
            return;
        }
        if (random.nextDouble() < PIConfig.INSTANT_CHANCE_ON_ADVANCEMENT.get()) {
            spawnOne(null, -1);
        }
    }

    public void onPlayerLoggedIn(ServerPlayer player) {
        if (sessionStartTick < 0) {
            sessionStartTick = now();
        }
        if (!PIConfig.loaded() || !PIConfig.ANNOUNCE_JOIN_LEAVE.get()) {
            return;
        }
        for (InvaderEntity bot : online.values()) {
            GameProfile profile = bot.getProfile();
            if (profile != null && !bot.isRemoved()) {
                player.connection.send(TabList.add(profile, 30 + random.nextInt(120)));
            }
        }
    }

    private void onServerStopping() {
        for (Map.Entry<UUID, InvaderEntity> entry : online.entrySet()) {
            Person person = data.persons.get(entry.getKey());
            if (person != null && person.state == Person.State.ONLINE) {
                person.state = Person.State.OFFLINE;
                person.returnAtTick = now();
            }
        }
        online.clear();
        pending.clear();
        tasks.clear();
        data.setDirty();
    }

    // ------------------------------------------------------------------------
    // Command helpers
    // ------------------------------------------------------------------------

    public List<InvaderEntity> onlineBots() {
        List<InvaderEntity> list = new ArrayList<>();
        for (InvaderEntity bot : online.values()) {
            if (!bot.isRemoved()) {
                list.add(bot);
            }
        }
        return list;
    }

    @Nullable
    public InvaderEntity onlineBot(String name) {
        for (InvaderEntity bot : online.values()) {
            if (!bot.isRemoved() && bot.getBotName().equalsIgnoreCase(name)) {
                return bot;
            }
        }
        return null;
    }

    public boolean kick(String name) {
        InvaderEntity bot = onlineBot(name);
        if (bot == null || bot.getPersonId() == null) {
            return false;
        }
        Person person = data.persons.get(bot.getPersonId());
        if (person == null) {
            return false;
        }
        leaveVoluntarily(person, bot);
        return true;
    }

    public boolean setTier(String name, int tier) {
        Person person = findPersonByName(name);
        if (person == null) {
            return false;
        }
        person.tier = Loadouts.clamp(tier);
        person.progressTicks = 0;
        data.setDirty();
        InvaderEntity bot = online.get(person.id);
        if (bot != null && !bot.isRemoved()) {
            bot.setTier(person.tier);
        }
        return true;
    }

    public void reset() {
        for (InvaderEntity bot : onlineBots()) {
            GameProfile profile = bot.getProfile();
            bot.discard();
            if (profile != null) {
                broadcastPacket(TabList.remove(profile.getId()));
            }
        }
        online.clear();
        pending.clear();
        tasks.clear();
        relocateTimers.clear();
        data.persons.clear();
        data.advancements = 0;
        data.setDirty();
    }
}
