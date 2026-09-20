package com.goshan.playerinvasion;

import com.goshan.playerinvasion.entity.InvaderEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class PIEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, PlayerInvasion.MODID);

    /**
     * MISC category: never counted against mob caps, never spawned by the game itself.
     * Tracking range matches what a player gets so the bot is visible from as far away
     * as a real player would be.
     */
    public static final RegistryObject<EntityType<InvaderEntity>> INVADER = ENTITIES.register("invader",
            () -> EntityType.Builder.of(InvaderEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(32)
                    .updateInterval(2)
                    .build("invader"));

    private PIEntities() {
    }
}
