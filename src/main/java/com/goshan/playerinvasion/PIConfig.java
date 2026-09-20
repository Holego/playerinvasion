package com.goshan.playerinvasion;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Everything that tunes the invasion. Names and chat phrases live here too, so a
 * server owner can localise or censor them without touching code.
 */
public final class PIConfig {

    public static final ForgeConfigSpec SPEC;

    // --- spawning -----------------------------------------------------------
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.IntValue MAX_BOTS;
    public static final ForgeConfigSpec.IntValue SPAWN_CHECK_SECONDS;
    public static final ForgeConfigSpec.DoubleValue BASE_CHANCE;
    public static final ForgeConfigSpec.DoubleValue CHANCE_PER_ADVANCEMENT;
    public static final ForgeConfigSpec.DoubleValue MAX_CHANCE;
    public static final ForgeConfigSpec.DoubleValue INSTANT_CHANCE_ON_ADVANCEMENT;
    public static final ForgeConfigSpec.IntValue MIN_SPAWN_DISTANCE;
    public static final ForgeConfigSpec.IntValue MAX_SPAWN_DISTANCE;
    public static final ForgeConfigSpec.IntValue FIRST_JOIN_DELAY_SECONDS;

    // --- progression --------------------------------------------------------
    public static final ForgeConfigSpec.IntValue START_TIER;
    public static final ForgeConfigSpec.IntValue MAX_TIER;
    public static final ForgeConfigSpec.IntValue TIER_UP_MINUTES;
    public static final ForgeConfigSpec.IntValue GIVE_UP_MINUTES;
    public static final ForgeConfigSpec.IntValue RETURN_MIN_MINUTES;
    public static final ForgeConfigSpec.IntValue RETURN_MAX_MINUTES;
    public static final ForgeConfigSpec.BooleanValue ENCHANTED_GEAR;
    public static final ForgeConfigSpec.BooleanValue PERMANENT_DEATH;

    // --- behaviour ----------------------------------------------------------
    public static final ForgeConfigSpec.IntValue HUNT_RADIUS;
    public static final ForgeConfigSpec.IntValue RELOCATE_DISTANCE;
    public static final ForgeConfigSpec.IntValue RELOCATE_AFTER_SECONDS;
    public static final ForgeConfigSpec.BooleanValue USE_LAVA;
    public static final ForgeConfigSpec.BooleanValue USE_COBWEBS;
    public static final ForgeConfigSpec.BooleanValue USE_GOLDEN_APPLES;
    public static final ForgeConfigSpec.BooleanValue USE_BOW;
    public static final ForgeConfigSpec.BooleanValue USE_CRYSTALS;
    public static final ForgeConfigSpec.BooleanValue USE_ANCHORS;
    public static final ForgeConfigSpec.BooleanValue USE_PEARLS;
    public static final ForgeConfigSpec.BooleanValue USE_ELYTRA;
    public static final ForgeConfigSpec.BooleanValue DIG_THROUGH_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue PILLAR_UP;
    public static final ForgeConfigSpec.BooleanValue EXPLOSIONS_BREAK_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue PICK_UP_LOOT;
    public static final ForgeConfigSpec.BooleanValue MONSTERS_ATTACK_BOTS;

    // --- presentation -------------------------------------------------------
    public static final ForgeConfigSpec.BooleanValue ONLINE_SKINS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SKIN_DONORS;
    public static final ForgeConfigSpec.BooleanValue ANNOUNCE_JOIN_LEAVE;
    public static final ForgeConfigSpec.BooleanValue CHAT_ENABLED;
    public static final ForgeConfigSpec.DoubleValue CHAT_CHANCE;
    public static final ForgeConfigSpec.IntValue IDLE_CHAT_MINUTES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> NAMES;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PHRASES_JOIN;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PHRASES_RETURN;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PHRASES_SPOT;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PHRASES_LOW_HEALTH;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PHRASES_KILL;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PHRASES_DEATH;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PHRASES_LEAVE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PHRASES_TIER_UP;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PHRASES_IDLE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PHRASES_REPLY;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PHRASES_GREETING_REPLY;

    private static final List<String> DEFAULT_NAMES = List.of(
            // russian-style nicks, different fashions
            "Vlad_Pro", "MaxPlay", "Sanya228", "TimurGG", "Nikita_YT", "DanilKrut", "Kirill_Top",
            "Artem_Craft", "MishaGamer", "EgorPvP", "Andrey_Mine", "Ilya_2011", "Sasha_Nub",
            "Denis_Killer", "Lexa_Pro", "Roma_Bro", "Pasha_Lol", "Kolya_Ez", "Vitya_Ded",
            "Zhenya_Pop", "Sergey_Boss", "Alex_Fire", "Dima_Hunter", "Kostya_X", "Gleb_Gg",
            "Matvey_Mc", "Stepan_Pvp", "Yarik_Top", "Bogdan_Wolf", "Oleg_Shadow", "Tema_Craft",
            "Slavik_Ez", "Zahar_Gg", "Vanya_Mine", "Fedya_Pro", "Grisha_Yt", "Tolik_Xd",
            "Ruslan_Killer", "Marat_Pvp", "Rustam_Top", "Arsen_Mc", "Timofey_X", "Semen_Gg",
            "Lev_Hunter", "Mark_Craft", "Miron_Yt", "Platon_Pro", "Savva_Mc", "Tihon_X",
            "nikitos", "danya2012", "MAX228", "_Kirill_", "xX_Vlad_Xx", "SashaPlay", "Artem_TV",
            "Gleb_MC", "IvanIvan", "Andrey_2007", "DimaKrut", "misha_top", "egorka", "Lexa2015",
            "TIMUR_PRO", "Vovan", "Kolyan_", "Seryoga", "Zheka", "Tolyan", "Dimon2010", "Sanek_",
            "Vitalik", "Romych", "Kostyan", "Lyoha", "Pashka_", "Yura_Gg", "Slava_2009", "Vasya_",
            "Igoryan", "Stas_Pro", "Nikolay_", "Fedor_2013", "Ilyukha", "Zakhar", "Bogdan_",
            "Deniska", "Arseniy_", "Matvey_2012", "Yarik", "Timokha", "Lyova", "Semyon_",
            // english-style
            "DarkWolf_", "ShadowKill", "NoobSlayer", "ProGamer_", "Fire_Storm", "IceBreaker_",
            "Night_Hawk", "Silent_Kid", "Ghost_Rider", "Iron_Fist", "Storm_Bringer", "xXDarkXx",
            "KingOfPvP", "Herobrine_", "Steve_Real", "Alex_Real", "Dream_Fan", "Skeppy_Fan",
            "Techno_Fan", "Nub_Master", "Ez_Clap", "Kek_Lol", "Lagger_", "Cheater_Not",
            "Crafter_", "MinerBoy", "Diamond_Kid", "Enderman_", "Creeper_Aw", "Blaze_", "Wither_",
            "Redstone_", "Netherite_", "Elytra_", "Speedrun_", "Hardcore_", "Survivor_", "Villager_"
    );

    private static final List<String> DEFAULT_SKIN_DONORS = List.of(
            "Alex", "Max", "Nick", "Tom", "Dan", "Sam", "Ben", "Leo", "Jack", "Mike", "Mark", "Paul",
            "Adam", "Luke", "Ryan", "Eric", "Kyle", "Ivan", "Igor", "Oleg", "Vlad", "Dima", "Denis",
            "Artem", "Egor", "Kirill", "Nikita", "Roma", "Sasha", "Misha", "Pasha", "Lexa", "Timur",
            "Danil", "Gleb", "Matvey", "Stepan", "Ruslan", "Marat", "Arsen", "Semen", "Miron",
            "Platon", "Bogdan", "Anton", "Boris", "Fedor", "Gosha", "Kolya", "Vitya", "Zhenya",
            "Kate", "Anna", "Lena", "Masha", "Dasha", "Nastya", "Sonya", "Vika", "Lera", "Polina"
    );

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("PlayerInvasion - fake players that hunt you").push("spawning");
        ENABLED = b.comment("Master switch. When false nothing joins.").define("enabled", true);
        MAX_BOTS = b.comment("How many fake players may be online at the same time.")
                .defineInRange("maxBots", 3, 1, 20);
        SPAWN_CHECK_SECONDS = b.comment("How often (seconds) the mod rolls the dice for a new join.")
                .defineInRange("spawnCheckSeconds", 60, 5, 3600);
        BASE_CHANCE = b.comment("Chance per roll with zero advancements earned (0..1).")
                .defineInRange("baseChance", 0.02D, 0.0D, 1.0D);
        CHANCE_PER_ADVANCEMENT = b.comment("Added to the chance for every advancement any player has earned in this world.")
                .defineInRange("chancePerAdvancement", 0.01D, 0.0D, 1.0D);
        MAX_CHANCE = b.comment("Upper cap of the per-roll chance.")
                .defineInRange("maxChance", 0.6D, 0.0D, 1.0D);
        INSTANT_CHANCE_ON_ADVANCEMENT = b.comment("Extra roll made the moment an advancement is earned (someone noticed you).")
                .defineInRange("instantChanceOnAdvancement", 0.15D, 0.0D, 1.0D);
        MIN_SPAWN_DISTANCE = b.comment("Bots appear no closer than this to the player they were spawned for (blocks).")
                .defineInRange("minSpawnDistance", 40, 8, 256);
        MAX_SPAWN_DISTANCE = b.comment("...and no farther than this.")
                .defineInRange("maxSpawnDistance", 80, 16, 512);
        FIRST_JOIN_DELAY_SECONDS = b.comment("Grace period after the first real player joins before the first roll can happen.")
                .defineInRange("firstJoinDelaySeconds", 300, 0, 86400);
        b.pop();

        b.comment("How the fake players gear up over time").push("progression");
        START_TIER = b.comment("Gear tier a brand-new fake player starts with. 0 = wooden axe, 1 = iron + shield + lava + cobwebs,",
                        "2 = diamond + golden apples + bow, 3 = netherite + totems + elytra + end crystals + respawn anchors + ender pearls.")
                .defineInRange("startTier", 0, 0, 3);
        MAX_TIER = b.comment("Highest tier a fake player can reach.").defineInRange("maxTier", 3, 0, 3);
        TIER_UP_MINUTES = b.comment("Minutes of existence (online or offline) needed to reach the next tier. The longer a fake player lives, the stronger it gets.")
                .defineInRange("tierUpMinutes", 12, 1, 600);
        GIVE_UP_MINUTES = b.comment("If a fake player has not seen anyone for this many minutes it logs off to go mining and returns later, stronger.")
                .defineInRange("giveUpMinutes", 8, 1, 600);
        RETURN_MIN_MINUTES = b.comment("Minimum minutes a fake player stays offline before coming back.")
                .defineInRange("returnMinMinutes", 5, 0, 600);
        RETURN_MAX_MINUTES = b.comment("Maximum minutes a fake player stays offline before coming back.")
                .defineInRange("returnMaxMinutes", 20, 0, 600);
        ENCHANTED_GEAR = b.comment("Diamond and netherite kits come enchanted (Protection, Sharpness, Power...).")
                .define("enchantedGear", true);
        PERMANENT_DEATH = b.comment("A killed fake player never comes back under that name. When false it may return like a respawned player.")
                .define("permanentDeath", true);
        b.pop();

        b.comment("What the fake players are allowed to do").push("behaviour");
        HUNT_RADIUS = b.comment("Without a visible target, a fake player walks towards the nearest real player inside this radius (blocks). 0 = never.")
                .defineInRange("huntRadius", 48, 0, 256);
        RELOCATE_DISTANCE = b.comment("If no real player is within this distance (blocks) for relocateAfterSeconds, the fake player quietly moves next to someone.")
                .defineInRange("relocateDistance", 96, 32, 1024);
        RELOCATE_AFTER_SECONDS = b.defineInRange("relocateAfterSeconds", 45, 5, 3600);
        USE_LAVA = b.define("useLava", true);
        USE_COBWEBS = b.define("useCobwebs", true);
        USE_GOLDEN_APPLES = b.define("useGoldenApples", true);
        USE_BOW = b.define("useBow", true);
        USE_CRYSTALS = b.define("useEndCrystals", true);
        USE_ANCHORS = b.comment("Respawn anchors in the Overworld, beds in the Nether and the End.").define("useRespawnAnchors", true);
        USE_PEARLS = b.comment("Netherite-tier bots throw ender pearls to close the distance.").define("useEnderPearls", true);
        USE_ELYTRA = b.comment("Netherite-tier bots fly in on elytra with firework rockets when the target is far away or high up.")
                .define("useElytra", true);
        DIG_THROUGH_BLOCKS = b.comment("Mine through walls (and floors) that block the way to the target. Respects the tool tier: a wooden pickaxe will not cut obsidian.")
                .define("digThroughBlocks", true);
        PILLAR_UP = b.comment("Place blocks: tower up to a target standing higher, bridge across gaps.").define("placeBlocks", true);
        EXPLOSIONS_BREAK_BLOCKS = b.comment("Crystal and anchor explosions destroy terrain, exactly like a real player would.")
                .define("explosionsBreakBlocks", true);
        PICK_UP_LOOT = b.comment("Fake players pick up items lying around - including what you drop when they kill you. Everything drops back when they die.")
                .define("pickUpLoot", true);
        MONSTERS_ATTACK_BOTS = b.comment("Zombies, skeletons and friends treat fake players as players and attack them.")
                .define("monstersAttackBots", true);
        b.pop();

        b.comment("Names, skins and chat").push("presentation");
        ONLINE_SKINS = b.comment("Look up the nickname on Mojang servers and use that account skin. Needs internet on the server; falls back to Steve/Alex.")
                .define("onlineSkins", true);
        SKIN_DONORS = b.comment("When the nickname has no Mojang account (or no skin), borrow the skin of a random account from this list instead.")
                .defineListAllowEmpty("skinDonors", DEFAULT_SKIN_DONORS, o -> o instanceof String s && !s.isBlank() && s.length() <= 16);
        ANNOUNCE_JOIN_LEAVE = b.comment("Show the vanilla '<name> joined the game' / 'left the game' lines and a tab-list entry.")
                .define("announceJoinLeave", true);
        CHAT_ENABLED = b.define("chatEnabled", true);
        CHAT_CHANCE = b.comment("Probability that a fake player actually says something when an occasion arises (0..1).")
                .defineInRange("chatChance", 0.6D, 0.0D, 1.0D);
        IDLE_CHAT_MINUTES = b.comment("Average minutes between random idle chat lines per fake player. 0 = never.")
                .defineInRange("idleChatMinutes", 6, 0, 600);
        NAMES = b.comment("Nickname pool. A name whose owner died is never reused while permanentDeath is on; when the pool runs dry, numbers are appended.")
                .defineList("names", DEFAULT_NAMES, o -> o instanceof String s && !s.isBlank() && s.length() <= 16);
        b.pop();

        b.comment("Chat lines. {player} is replaced with the nearest real player name. Empty list = silent for that occasion.").push("phrases");
        PHRASES_JOIN = b.defineListAllowEmpty("join", List.of(
                "hi", "hello", "hey", "sup", "yo", "hi all", "anyone here?", "whats up", "who is on?", "hello everyone",
                "hey guys", "anyone alive?", "server dead?", "hi chat", "lets play", "who wants 1v1?", "gm", "o/",
                "привет", "ку", "всем прив", "есть кто?"), PIConfig::isString);
        PHRASES_RETURN = b.defineListAllowEmpty("return", List.of(
                "im back", "back", "hello again", "miss me?", "ready now", "round 2", "back with gear", "here we go again",
                "did you miss me", "im not done", "я вернулся", "снова тут"), PIConfig::isString);
        PHRASES_SPOT = b.defineListAllowEmpty("spot", List.of(
                "found you", "run", "{player} stop", "i see you", "come here {player}", "{player} 1v1?", "dont run",
                "you are dead", "ez target", "{player} wait", "there you are", "hey {player}", "gotcha", "{player} fight me",
                "stop running", "lets go {player}", "give me your loot", "no escape", "нашёл тебя", "беги", "{player} стой"), PIConfig::isString);
        PHRASES_LOW_HEALTH = b.defineListAllowEmpty("lowHealth", List.of(
                "wait", "oops", "lag", "sec", "hold on", "not fair", "ouch", "im lagging", "wtf", "stop", "wait wait",
                "щас", "блин", "лагает"), PIConfig::isString);
        PHRASES_KILL = b.defineListAllowEmpty("kill", List.of(
                "ez", "gg", "ez clap", "lol", "get good", "skill issue", "thanks for the loot", "noob", "{player} noob",
                "rip", "L", "gg ez", "too easy", "next", "{player} learn to play", "free loot", "lmao", "bye {player}",
                "{player} again?", "ez pz", "изи", "лол", "спасибо за лут"), PIConfig::isString);
        PHRASES_DEATH = b.defineListAllowEmpty("death", List.of(
                "gg", "lag", "hacker", "wtf", "lagging", "unfair", "nice one", "cheater?", "my ping", "im out", "bs",
                "{player} hacks", "lucky", "{player} lucky", "whatever", "not my fault", "trash server", "ok bye",
                "лаги", "читер", "нечестно", "повезло"), PIConfig::isString);
        PHRASES_LEAVE = b.defineListAllowEmpty("leave", List.of(
                "brb", "afk", "gotta go", "bye", "cya", "going mining", "back soon", "gtg", "need resources", "off to the nether",
                "later", "be back", "bored, leaving", "mom is calling", "пока", "я афк", "скоро вернусь"), PIConfig::isString);
        PHRASES_TIER_UP = b.defineListAllowEmpty("tierUp", List.of(
                "got diamonds", "full set now", "netherite time", "geared up", "im ready", "lets go", "now we talk",
                "found debris", "full diamond", "new gear", "ready for pvp", "фулл сет", "нашёл алмазы", "апнулся"), PIConfig::isString);
        PHRASES_IDLE = b.defineListAllowEmpty("idle", List.of(
                "anyone?", "bored", "where is everyone", "who wants 1v1", "{player} where are you", "{player} where is your base",
                "hello?", "so quiet", "any diamonds?", "lets fight", "{player} come out", "anyone want to trade?", "hmm",
                "im looking for you {player}", "show me your base", "who is the best here", "кто где?", "скучно", "где база?"), PIConfig::isString);
        PHRASES_REPLY = b.defineListAllowEmpty("reply", List.of(
                "what", "lol", "ok", "no", "?", "sure", "coming", "whatever", "yes", "idk", "lmao", "why", "and?",
                "nope", "k", "wait", "1v1 then", "че?", "лан", "иду"), PIConfig::isString);
        PHRASES_GREETING_REPLY = b.defineListAllowEmpty("greetingReply", List.of(
                "hi", "hey", "sup", "hello {player}", "yo", "hi {player}", "hey {player}", "o/", "прив", "ку {player}"), PIConfig::isString);
        b.pop();

        SPEC = b.build();
    }

    private static boolean isString(Object o) {
        return o instanceof String;
    }

    public static boolean loaded() {
        return SPEC.isLoaded();
    }

    private PIConfig() {
    }
}
