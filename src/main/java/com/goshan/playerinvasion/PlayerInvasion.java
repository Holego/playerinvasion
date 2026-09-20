package com.goshan.playerinvasion;

import com.goshan.playerinvasion.entity.InvaderEntity;
import com.mojang.logging.LogUtils;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(PlayerInvasion.MODID)
public class PlayerInvasion {

    public static final String MODID = "playerinvasion";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PlayerInvasion() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        PIEntities.ENTITIES.register(modBus);
        modBus.addListener(this::onAttributes);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, PIConfig.SPEC, "playerinvasion-common.toml");

        LOGGER.info("PlayerInvasion loaded - someone is about to join your server.");
    }

    private void onAttributes(EntityAttributeCreationEvent event) {
        event.put(PIEntities.INVADER.get(), InvaderEntity.createAttributes().build());
    }
}
