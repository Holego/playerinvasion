# Player Invasion

A mod for **Minecraft 1.20.1 / Forge 47.x** that makes "other players" join your server and hunt you.

They are not players. They are mobs that look, move, fight, talk and die like players: they appear in the tab list with a real skin, get the yellow `X joined the game` line, start with a wooden axe, gear up the longer they live — iron with a shield, lava and cobwebs; diamond with golden apples and a bow; netherite with totems, elytra, end crystals, respawn anchors and ender pearls — dig into your base, tower up after you, write in chat, and when you finally kill one, it drops everything it carried, says `gg` and **leaves for good**.

Works in singleplayer and on dedicated servers. The mod must be installed on **both** the server and every client (it registers an entity and its renderer).

License: All Rights Reserved.

---

## How it works

### Joining

Every `spawnCheckSeconds` (default 60) the mod rolls the dice. The chance is

```
min(maxChance, baseChance + chancePerAdvancement × advancements)
```

where `advancements` is the number of advancements (the ones with an icon, not recipe unlocks) earned by anyone in this world. With defaults that is 2% + 1% per advancement, capped at 60%. Earning an advancement also triggers an extra `instantChanceOnAdvancement` roll — someone noticed you.

A successful roll brings one person online (up to `maxBots` at once, default 3). It picks a nickname from the pool, asks Mojang for that account's skin (or borrows one from the `skinDonors` list if the nickname has no account), sends a tab-list entry and the vanilla join message to everyone, and places the entity 40–80 blocks from a random survival player — on the ground, out of their line of sight, preferably behind them. A few seconds later it says hello.

Nothing joins in Peaceful difficulty, while no survival/adventure player is online, or during the first `firstJoinDelaySeconds` after the first player logs in.

### Progression

Every person is a record in the world's saved data (`data/playerinvasion.dat`). It keeps its **tier**, and the tier only goes up: `tierUpMinutes` (default 12) of existence — online or offline — earns the next tier, up to `maxTier`.

| Tier | Kit |
|---|---|
| 0 wood | wooden axe, wooden pickaxe, 16 dirt |
| 1 iron | iron sword + axe + pickaxe, full iron armor, **shield**, **8 splash potions** (Harming/Poison/Slowness/Weakness), 64 cobblestone |
| 2 diamond | diamond sword (Sharpness II) + axe + pickaxe (Efficiency II), full diamond armor (Protection II), shield, **bow** (Power II) + 32 arrows, **8 golden apples**, **2 lava buckets + 16 cobwebs**, cobblestone |
| 3 netherite | netherite sword (Sharpness IV) + axe + pickaxe (Efficiency IV), full netherite armor (Protection IV), **totem of undying** in the off hand + 2 spare, **elytra (Unbreaking III) + 32 firework rockets**, bow (Power IV) + 64 arrows, 16 golden + 2 enchanted golden apples, **16 end crystals + 32 obsidian**, **4 respawn anchors + 16 glowstone**, 4 beds, **16 ender pearls**, lava, cobwebs, cobblestone |

Enchantments can be turned off with `enchantedGear = false`.

A bot that has not seen anyone for `giveUpMinutes` says something like "going mining", logs off (`X left the game`), keeps developing while offline and comes back `returnMinMinutes`–`returnMaxMinutes` later, stronger, with "im back". A bot whose chunk unloads simply logs off and is eligible to return on the next roll.

### Fighting

The bot is a pathfinding mob with a player's numbers: 20 hp, 3-block reach, walking and sprinting speed of a player, attack cooldown taken from the weapon's attack speed, full-cooldown hits only.

Crystals, anchors/beds, lava, cobwebs, pearls and potions are all "tricks" a bot can only do one of at a time. Whichever of them are actually possible right now (right gear, right range, right geometry, health above 45%) go into a pool and one is picked at random each time — so a netherite bot that could equally throw a crystal or drop lava does not always reach for the same one, and a bot below 45% health backs off to eat instead of starting a trick it might not finish.

* **Melee** — sprints in, strafes around you, hits on cooldown, jumps for the occasional crit (sprint off, on the way down, ×1.5, crit particles), W-taps for knockback. Swaps sword → axe when you raise a shield, so the shield gets disabled; swaps back afterwards.
* **Shield** — raised when you face it and its own attack is cooling down, and against incoming arrows. An axe hit disables it for 5 seconds like a player's. The shield takes durability and can break.
* **Splash potions** (iron) — Harming, Poison, Slowness or Weakness, picked the same way a witch picks one: Slowness at range to stop you closing in, Poison against a healthy target, Weakness up close, Harming otherwise.
* **Lava** (diamond+) — a bucket dumped under your feet from 2.5–6 blocks, then it keeps its distance.
* **Cobwebs** (diamond+) — placed on your feet so you cannot run.
* **Bow** — from 12+ blocks, drawn for a full second, shot with a bit of lead; strafes like a skeleton; switches back to the sword under 5 blocks.
* **Golden apples** — under 45% health it backs off and eats; the enchanted one when it is really bad.
* **End crystals** — obsidian next to you, crystal on top, hit. Keeps ≥ 4 blocks away from its own crystal and eats the splash like anyone else. Death message: `was blown up by X`.
* **Respawn anchors** (Overworld, End) and **beds** (Nether, End) — placed, charged, "used": `was killed by [Intentional Game Design]`.
* **Ender pearls** — thrown at a target 10+ blocks away, up on something, or unreachable by walking (also out of water); the vanilla pearl teleport does the rest.
* **Elytra** — when the target is more than 20 blocks away or 6+ blocks higher, the netherite bot swaps its chestplate for the elytra, jumps, spreads the wings, boosts with rockets, steers at you and drops on you (a hit on the way down is a crit), then puts the chestplate back on. 30 blocks take about a second.
* **Totem of undying** — netherite tier carries three instead of a shield; a popped one is replaced from the bag.
* **Digging** — when the path to you fails, it mines through the wall, opens the ceiling when you are above it, digs straight down when you are right below, and cuts a staircase down when you are below and off to the side - with the best tool it has, vanilla break times and the crack animation. A wooden pickaxe does not get through obsidian; a netherite one does in 9 seconds. Blocks with block entities (chests) are left alone.
* **Building** — towers up under you when you are on a platform or a cliff (jump, block underneath, repeat), digs through the platform if it is in the way, bridges across gaps. Towering, bridging and digging live in one `ObstacleSolver` per bot, so switching between chasing and hunting does not restart a half-built tower.
* **Swimming** — swims at player speed (mobs normally crawl through water), sprint-swims after a target in the swimming pose, and pearls out of the water.
* **Hunting** — without a visible target it walks towards the nearest survival player within `huntRadius`, sneaks up when you are not looking (name tag hidden past 32 blocks, like a sneaking player), digs into your hideout.
* **Loot** — picks up items lying around, including what you drop when it kills you, and wears armor that is better than its own.

Zombies, skeletons, creepers and other monsters treat bots as players and attack them (`monstersAttackBots`). Bots do not fight each other.

### Dying

The bot takes armor durability loss, fall damage, fire, drowning, everything. When it dies:

* the vanilla death message is broadcast (`X was slain by You`);
* **everything drops**, undamaged — armor, weapons, the whole bag (that netherite kit is the reward);
* XP drops;
* a second later it says something (`gg`, `hacker`, `lag`...), then `X left the game` and the tab-list entry disappears;
* with `permanentDeath = true` (default) that nickname is retired for this world forever. With `false` it comes back later at the starting tier, like a respawned player.

### Chat

Bots talk on occasions: joining, returning, spotting you (`{player} 1v1?`), low health, killing you (`ez`, `skill issue`), dying, leaving, gearing up, and randomly while idle. The defaults are mostly English gamer chat with a few Russian lines mixed in. They answer when you greet them or mention their nickname, and sometimes butt into other conversations. Every list is in the config; `{player}` is replaced with the nearest real player's name. Lines are broadcast as `<Name> text`, exactly like player chat.

---

## Commands

All require permission level 2.

| Command | Effect |
|---|---|
| `/invasion spawn [name] [tier]` | bring someone online now (a returning person, a fresh one, or the given name at the given tier 0–3) |
| `/invasion list` | who is online, with tier, health and position; how many are offline and how many are dead for good |
| `/invasion kick <name>` | make that bot say goodbye and log off |
| `/invasion tier <name> <0-3>` | set a person's tier (applies immediately if online) |
| `/invasion chance` | current per-roll chance, advancement counter, online count |
| `/invasion advancements <n>` | set the advancement counter |
| `/invasion reset` | kick everyone and wipe the roster and counters |

A bot created with `/summon playerinvasion:invader` is adopted: it gets a name, a kit and a tab-list entry like the others.

---

## Configuration

`config/playerinvasion-common.toml`, sections `spawning`, `progression`, `behaviour`, `presentation`, `phrases`. Every trick has its own switch (`useSplashPotions`, `useLava`, `useCobwebs`, `useGoldenApples`, `useBow`, `useEndCrystals`, `useRespawnAnchors`, `useEnderPearls`, `useElytra`, `digThroughBlocks`, `placeBlocks`, `pickUpLoot`, `explosionsBreakBlocks`, `monstersAttackBots`). `onlineSkins = false` makes every bot a Steve/Alex without touching the network. `names`, `skinDonors` and all `phrases.*` lists are plain string arrays.

---

## Building

Needs **JDK 17** (Gradle fetches it itself via the toolchain, given network access).

```bash
./gradlew build
```

Built jar: `build/libs/playerinvasion-1.20.1-1.0.0.jar`.

Run a client for testing: `./gradlew runClient`. For a quick look, set `firstJoinDelaySeconds = 0`, `baseChance = 1.0`, `spawnCheckSeconds = 5` in the run directory's config.

---

## Notes

* Skins are fetched on the server from Mojang's public profile API when a bot first joins, then cached in the saved data. On a server without internet access bots are Steve/Alex. Clients download the skin texture themselves, the same way they do for real players.
* The tab-list entry is a hand-written player-info packet — the vanilla client cannot tell it from a real player. It is sent to players who log in later, and removed when the bot leaves or dies.
* Bots are never written into chunks. If a bot's chunk unloads, the bot "logs off" instead of freezing in place, and returns near a player later. A bot that ends up more than `relocateDistance` blocks from everyone for `relocateAfterSeconds` quietly moves next to someone.
* Explosions from crystals and anchors break terrain by default, exactly like a real player's would. `explosionsBreakBlocks = false` keeps the damage but spares the blocks.

---

## What's verified on a real run

Built and run as a dev client (integrated server) and as a dedicated Forge 47.4.10 / MC 1.20.1 server:

* the dedicated server starts clean (`Done (13.7s)`), the config with Cyrillic phrases is written and read back as UTF-8;
* with an aggressive test config three netherite-tier bots joined within 30 s, the log shows every kill type against the test player: `was slain by` (melee), `was shot by` (bow), `was blown up by` (crystal), `was killed by [Intentional Game Design]` (anchor);
* chat lines fire on join, spot, kill, low health and idle; bots answer a greeting typed by the player;
* a person from a previous session came back as `(tier 3, returning)` with its tier intact — the saved data round-trips;
* elytra: `takes off on elytra towards Dev (37 blocks away)` -> `drops on Dev after 23 ticks of flight` -> `Dev was blown up`; towering: `towers up (12 blocks to climb)`; digging down to a player underground: `starts digging towards the target (dy -6)` -> `broke Grass Block`, `broke Dirt`; all at DEBUG level in the dev log;
* the fair-turn trick picker was confirmed live: `MishaGamer picks PlaceCobwebGoal out of 2 ready tricks` — before this, crystals/anchors (short cooldown) always won the shared slot and lava/cobwebs/pearls/potions never got a turn; every trick's own health >= 9 gate and 1-tick wind-up (down from 2-3) were added after a live session showed a bot picking up a lava bucket/cobweb and then never using it - the eat-golden-apple goal (higher priority) was interrupting it mid-attempt;
* no exceptions from the mod in any run;
* **not independently re-confirmed after the health-gate/wind-up fix**: a full lava-poured/cobweb-placed log line, because the local dev harness's background game processes stopped producing output mid-session (unrelated to the mod - confirmed via `Get-Process` that the JVMs were still consuming CPU) across every launch method tried (integrated client, dedicated server, isolated working directory). The fix is mechanically direct (same pattern already proven for crystals/anchors, which have always had this health gate) and the code compiles and starts cleanly; treat the completion of low-tier tricks as reasoned-through rather than freshly log-verified, and report back if `useSplashPotions`/`useLava`/`useCobwebs` still misbehave.
