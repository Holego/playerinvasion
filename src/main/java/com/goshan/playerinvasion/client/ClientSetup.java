package com.goshan.playerinvasion.client;

import com.goshan.playerinvasion.PIEntities;
import com.goshan.playerinvasion.PlayerInvasion;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PlayerInvasion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PIEntities.INVADER.get(), InvaderRenderer::new);
    }

    private ClientSetup() {
    }
}
