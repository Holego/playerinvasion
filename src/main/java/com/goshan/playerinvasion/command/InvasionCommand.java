package com.goshan.playerinvasion.command;

import com.goshan.playerinvasion.entity.InvaderEntity;
import com.goshan.playerinvasion.entity.Loadouts;
import com.goshan.playerinvasion.invasion.InvasionManager;
import com.goshan.playerinvasion.invasion.Person;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * /invasion spawn [name] [tier] | list | kick <name> | tier <name> <0-3> | chance | advancements <n> | reset
 */
public final class InvasionCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("invasion")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawn")
                        .executes(ctx -> spawn(ctx, null, -1))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "name"), -1))
                                .then(Commands.argument("tier", IntegerArgumentType.integer(0, 3))
                                        .executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "name"),
                                                IntegerArgumentType.getInteger(ctx, "tier"))))))
                .then(Commands.literal("list").executes(InvasionCommand::list))
                .then(Commands.literal("kick")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> kick(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("tier")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("tier", IntegerArgumentType.integer(0, 3))
                                        .executes(ctx -> tier(ctx, StringArgumentType.getString(ctx, "name"),
                                                IntegerArgumentType.getInteger(ctx, "tier"))))))
                .then(Commands.literal("chance").executes(InvasionCommand::chance))
                .then(Commands.literal("advancements")
                        .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                .executes(ctx -> advancements(ctx, IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("reset").executes(InvasionCommand::reset)));
    }

    @Nullable
    private static InvasionManager manager(CommandContext<CommandSourceStack> ctx) {
        InvasionManager manager = InvasionManager.current();
        if (manager == null) {
            ctx.getSource().sendFailure(Component.translatable("command.playerinvasion.not_running"));
        }
        return manager;
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, @Nullable String name, int tier) {
        InvasionManager manager = manager(ctx);
        if (manager == null) {
            return 0;
        }
        if (manager.spawnOne(name, tier)) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.playerinvasion.spawned"), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.translatable("command.playerinvasion.spawn_failed"));
        return 0;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        InvasionManager manager = manager(ctx);
        if (manager == null) {
            return 0;
        }
        int online = 0;
        for (InvaderEntity bot : manager.onlineBots()) {
            online++;
            String pos = String.format("%d %d %d", bot.getBlockX(), bot.getBlockY(), bot.getBlockZ());
            ctx.getSource().sendSuccess(() -> Component.literal("  " + bot.getBotName() + " - " + Loadouts.tierName(bot.getTier())
                    + ", " + Math.round(bot.getHealth()) + " hp, " + pos), false);
        }
        int finalOnline = online;
        ctx.getSource().sendSuccess(() -> Component.translatable("command.playerinvasion.online", finalOnline), false);
        int offline = 0;
        int dead = 0;
        for (Person p : manager.data().persons.values()) {
            if (p.state == Person.State.DEAD) {
                dead++;
            } else if (p.state == Person.State.OFFLINE) {
                offline++;
            }
        }
        int finalOffline = offline;
        int finalDead = dead;
        ctx.getSource().sendSuccess(() -> Component.translatable("command.playerinvasion.roster", finalOffline, finalDead), false);
        return online;
    }

    private static int kick(CommandContext<CommandSourceStack> ctx, String name) {
        InvasionManager manager = manager(ctx);
        if (manager == null) {
            return 0;
        }
        if (manager.kick(name)) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.playerinvasion.kicked", name), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.translatable("command.playerinvasion.no_such_bot", name));
        return 0;
    }

    private static int tier(CommandContext<CommandSourceStack> ctx, String name, int tier) {
        InvasionManager manager = manager(ctx);
        if (manager == null) {
            return 0;
        }
        if (manager.setTier(name, tier)) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.playerinvasion.tier_set", name, Loadouts.tierName(tier)), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.translatable("command.playerinvasion.no_such_bot", name));
        return 0;
    }

    private static int chance(CommandContext<CommandSourceStack> ctx) {
        InvasionManager manager = manager(ctx);
        if (manager == null) {
            return 0;
        }
        String percent = String.format("%.1f", manager.currentChance() * 100.0D);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.playerinvasion.chance",
                percent, manager.data().advancements, manager.onlineCount()), false);
        return 1;
    }

    private static int advancements(CommandContext<CommandSourceStack> ctx, int count) {
        InvasionManager manager = manager(ctx);
        if (manager == null) {
            return 0;
        }
        manager.data().advancements = count;
        manager.data().setDirty();
        ctx.getSource().sendSuccess(() -> Component.translatable("command.playerinvasion.advancements_set", count), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        InvasionManager manager = manager(ctx);
        if (manager == null) {
            return 0;
        }
        manager.reset();
        ctx.getSource().sendSuccess(() -> Component.translatable("command.playerinvasion.reset"), true);
        return 1;
    }

    private InvasionCommand() {
    }
}
