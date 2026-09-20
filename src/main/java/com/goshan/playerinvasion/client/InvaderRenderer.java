package com.goshan.playerinvasion.client;

import com.goshan.playerinvasion.entity.InvaderEntity;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ArrowLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;

/**
 * Renders the bot exactly like a player: player model (wide or slim to match the
 * skin), armor, held items, stuck arrows, name tag visible through walls.
 */
public class InvaderRenderer extends LivingEntityRenderer<InvaderEntity, PlayerModel<InvaderEntity>> {

    private final PlayerModel<InvaderEntity> wideModel;
    private final PlayerModel<InvaderEntity> slimModel;

    public InvaderRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wideModel = this.model;
        this.slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        this.addLayer(new ArrowLayer<>(context, this));
        this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
        this.addLayer(new ElytraLayer<>(this, context.getModelSet()));
    }

    /** Same body tilt and roll as PlayerRenderer gives a gliding player. */
    @Override
    protected void setupRotations(InvaderEntity entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks) {
        super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks);
        if (!entity.isFallFlying() && entity.getSwimAmount(partialTicks) <= 0.0F) {
            return;
        }
        float swim = entity.getSwimAmount(partialTicks);
        if (!entity.isFallFlying() && swim > 0.0F) {
            float target = entity.isInWater() ? -90.0F - entity.getXRot() : -90.0F;
            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(swim, 0.0F, target)));
            if (entity.isVisuallySwimming()) {
                poseStack.translate(0.0F, -1.0F, 0.3F);
            }
            return;
        }
        float ticks = (float) entity.getFallFlyingTicks() + partialTicks;
        float lean = Mth.clamp(ticks * ticks / 100.0F, 0.0F, 1.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(lean * (-90.0F - entity.getXRot())));
        Vec3 look = entity.getViewVector(partialTicks);
        Vec3 motion = entity.getDeltaMovement();
        double motionFlat = motion.horizontalDistanceSqr();
        double lookFlat = look.horizontalDistanceSqr();
        if (motionFlat > 0.0D && lookFlat > 0.0D) {
            double cos = (motion.x * look.x + motion.z * look.z) / Math.sqrt(motionFlat * lookFlat);
            double cross = motion.x * look.z - motion.z * look.x;
            poseStack.mulPose(Axis.YP.rotation((float) (Math.signum(cross) * Math.acos(Mth.clamp(cos, -1.0D, 1.0D)))));
        }
    }

    @Override
    public void render(InvaderEntity entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int light) {
        resolveSkin(entity);
        this.model = entity.clientSlim ? slimModel : wideModel;
        setModelProperties(entity);
        super.render(entity, yaw, partialTicks, poseStack, buffer, light);
    }

    private void setModelProperties(InvaderEntity entity) {
        PlayerModel<InvaderEntity> model = getModel();
        model.setAllVisible(true);
        model.crouching = entity.isCrouching();
        HumanoidModel.ArmPose mainPose = armPose(entity, InteractionHand.MAIN_HAND);
        HumanoidModel.ArmPose offPose = armPose(entity, InteractionHand.OFF_HAND);
        if (mainPose.isTwoHanded()) {
            offPose = entity.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        }
        if (entity.getMainArm() == HumanoidArm.RIGHT) {
            model.rightArmPose = mainPose;
            model.leftArmPose = offPose;
        } else {
            model.rightArmPose = offPose;
            model.leftArmPose = mainPose;
        }
    }

    private static HumanoidModel.ArmPose armPose(InvaderEntity entity, InteractionHand hand) {
        ItemStack stack = entity.getItemInHand(hand);
        if (stack.isEmpty()) {
            return HumanoidModel.ArmPose.EMPTY;
        }
        if (entity.getUsedItemHand() == hand && entity.getUseItemRemainingTicks() > 0) {
            UseAnim anim = stack.getUseAnimation();
            if (anim == UseAnim.BLOCK) {
                return HumanoidModel.ArmPose.BLOCK;
            }
            if (anim == UseAnim.BOW) {
                return HumanoidModel.ArmPose.BOW_AND_ARROW;
            }
            if (anim == UseAnim.SPEAR) {
                return HumanoidModel.ArmPose.THROW_SPEAR;
            }
            if (anim == UseAnim.CROSSBOW) {
                return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
            }
            if (anim == UseAnim.SPYGLASS) {
                return HumanoidModel.ArmPose.SPYGLASS;
            }
            if (anim == UseAnim.TOOT_HORN) {
                return HumanoidModel.ArmPose.TOOT_HORN;
            }
            if (anim == UseAnim.BRUSH) {
                return HumanoidModel.ArmPose.BRUSH;
            }
        } else if (!entity.swinging && stack.is(Items.CROSSBOW) && CrossbowItem.isCharged(stack)) {
            return HumanoidModel.ArmPose.CROSSBOW_HOLD;
        }
        return HumanoidModel.ArmPose.ITEM;
    }

    /** Decodes the synced profile once and asks the skin manager for the texture (downloaded in the background). */
    private static void resolveSkin(InvaderEntity entity) {
        if (!entity.clientProfileDirty) {
            return;
        }
        entity.clientProfileDirty = false;
        GameProfile profile = entity.getProfile();
        if (profile == null) {
            entity.clientSkin = null;
            entity.clientSlim = false;
            return;
        }
        SkinManager skins = Minecraft.getInstance().getSkinManager();
        MinecraftProfileTexture skin = null;
        try {
            Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures = skins.getInsecureSkinInformation(profile);
            skin = textures.get(MinecraftProfileTexture.Type.SKIN);
        } catch (Exception ignored) {
            // malformed textures property: fall through to the default skin
        }
        if (skin != null) {
            entity.clientSkin = skins.registerTexture(skin, MinecraftProfileTexture.Type.SKIN);
            entity.clientSlim = "slim".equals(skin.getMetadata("model"));
        } else {
            UUID id = profile.getId() != null ? profile.getId() : UUIDUtil.createOfflinePlayerUUID(profile.getName());
            entity.clientSkin = DefaultPlayerSkin.getDefaultSkin(id);
            entity.clientSlim = "slim".equals(DefaultPlayerSkin.getSkinModelName(id));
        }
    }

    @Override
    public ResourceLocation getTextureLocation(InvaderEntity entity) {
        return entity.clientSkin instanceof ResourceLocation location ? location : DefaultPlayerSkin.getDefaultSkin();
    }

    @Override
    protected void scale(InvaderEntity entity, PoseStack poseStack, float partialTicks) {
        poseStack.scale(0.9375F, 0.9375F, 0.9375F);
    }
}
