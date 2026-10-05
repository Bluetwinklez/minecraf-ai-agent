package com.bluetwinklez.aibuilders.entity;

import com.bluetwinklez.aibuilders.AiBuilders;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	public static final ResourceKey<EntityType<?>> BUILDER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, AiBuilders.id("builder"));
	public static final EntityType<AgentNpc> BUILDER = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		BUILDER_KEY,
		EntityType.Builder.<AgentNpc>of(AgentNpc::new, MobCategory.MISC)
			.sized(0.6F, 1.8F)
			.eyeHeight(1.62F)
			.clientTrackingRange(10)
			.build(BUILDER_KEY)
	);

	private ModEntities() {
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(BUILDER, AgentNpc.createAttributes());
	}
}
