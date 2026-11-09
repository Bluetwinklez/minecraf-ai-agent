package com.bluetwinklez.aibuilders.client;

import com.bluetwinklez.aibuilders.entity.AgentNpc;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Fills an {@link AvatarRenderState} for the NPC. Because the state is an avatar state, the
 * vanilla dispatcher draws it with the player renderer matching the skin model (wide/slim),
 * so NPCs look exactly like players wearing the chosen skin.
 */
public class AgentNpcRenderer extends HumanoidMobRenderer<AgentNpc, AvatarRenderState, PlayerModel> {
	private final PlayerSkinRenderCache skinCache;

	public AgentNpcRenderer(final EntityRendererProvider.Context context) {
		super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
		this.skinCache = context.getPlayerSkinRenderCache();
	}

	@Override
	public AvatarRenderState createRenderState() {
		return new AvatarRenderState();
	}

	@Override
	public void extractRenderState(final AgentNpc entity, final AvatarRenderState state, final float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.skin = skinCache.getOrDefault(entity.getProfile()).playerSkin();
		state.id = entity.getId();
		state.showCape = false;
	}

	@Override
	protected HumanoidModel.ArmPose getArmPose(final AgentNpc mob, final HumanoidArm arm) {
		return mob.getItemHeldByArm(arm).isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
	}

	@Override
	public Identifier getTextureLocation(final AvatarRenderState state) {
		return state.skin.body().texturePath();
	}
}
