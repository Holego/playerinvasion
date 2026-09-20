package com.goshan.playerinvasion.event;

import com.goshan.playerinvasion.PIConfig;
import com.goshan.playerinvasion.PlayerInvasion;
import com.goshan.playerinvasion.command.InvasionCommand;
import com.goshan.playerinvasion.entity.InvaderEntity;
import com.goshan.playerinvasion.invasion.InvasionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PlayerInvasion.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerEvents {

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        InvasionManager.start(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        InvasionManager.shutdown();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        InvasionManager manager = InvasionManager.current();
        if (manager != null) {
            manager.tick();
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        InvasionCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        InvasionManager manager = InvasionManager.current();
        if (manager != null && event.getEntity() instanceof ServerPlayer player) {
            manager.onPlayerLoggedIn(player);
        }
    }

    /** Every advancement with an icon (i.e. not a recipe unlock) makes the world a little more interesting to visit. */
    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getAdvancement().getDisplay() == null) {
            return;
        }
        InvasionManager manager = InvasionManager.current();
        if (manager != null && event.getEntity() instanceof ServerPlayer player) {
            manager.onAdvancementEarned(player);
        }
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        InvasionManager manager = InvasionManager.current();
        if (manager != null) {
            manager.onPlayerChat(event.getPlayer(), event.getRawText());
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        if (event.getSource().getEntity() instanceof InvaderEntity bot) {
            InvasionManager manager = InvasionManager.current();
            if (manager != null) {
                manager.onBotKilledPlayer(bot, victim);
            }
        }
    }

    /** Hostile mobs should not know the difference between a bot and a player. */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Monster monster)) {
            return;
        }
        if (!PIConfig.loaded() || !PIConfig.MONSTERS_ATTACK_BOTS.get()) {
            return;
        }
        monster.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(monster, InvaderEntity.class, true));
    }

    private ServerEvents() {
    }
}
