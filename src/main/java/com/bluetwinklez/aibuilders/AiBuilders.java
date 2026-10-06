package com.bluetwinklez.aibuilders;

import com.bluetwinklez.aibuilders.build.BuildTask;
import com.bluetwinklez.aibuilders.config.AiBuildersConfig;
import com.bluetwinklez.aibuilders.entity.ModEntities;
import com.bluetwinklez.aibuilders.chat.ChatHandler;
import com.bluetwinklez.aibuilders.command.BuilderCommands;
import com.bluetwinklez.aibuilders.command.PreviewManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AiBuilders implements ModInitializer {
	public static final String MOD_ID = "aibuilders";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		AiBuildersConfig.load();
		ModEntities.init();
		BuildTask.register();
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> BuilderCommands.register(dispatcher));
		ServerMessageEvents.CHAT_MESSAGE.register(ChatHandler::onChat);
		ServerTickEvents.END_SERVER_TICK.register(PreviewManager::tick);
		LOGGER.info("AI Builders loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
