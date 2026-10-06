package com.bluetwinklez.aibuilders.client;

import com.bluetwinklez.aibuilders.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class AiBuildersClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.BUILDER, AgentNpcRenderer::new);
	}
}
