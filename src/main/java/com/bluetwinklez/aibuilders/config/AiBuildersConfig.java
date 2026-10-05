package com.bluetwinklez.aibuilders.config;

import com.bluetwinklez.aibuilders.AiBuilders;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Server config, stored as JSON in {@code config/aibuilders.json}. */
public class AiBuildersConfig {
	public enum BuildMode {
		SURVIVAL, CREATIVE;

		public static final com.mojang.serialization.Codec<BuildMode> CODEC = com.mojang.serialization.Codec.STRING.xmap(
			s -> "CREATIVE".equalsIgnoreCase(s) ? CREATIVE : SURVIVAL, Enum::name
		);
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static AiBuildersConfig instance = new AiBuildersConfig();

	// Building
	public BuildMode buildMode = BuildMode.SURVIVAL;
	public double blocksPerSecond = 4.0;
	public double reach = 4.5;
	public int chestSearchRadius = 16;
	public boolean clearObstructions = true;
	public boolean useScaffolding = true;
	public int itemPickupRadius = 6;
	public boolean allowTeleportWhenStuck = false;
	public int maxNpcsPerPlayer = 2;
	public int maxSchematicBlocks = 500_000;
	public int materialRecheckSeconds = 5;
	public boolean showBossBar = true;

	// Chat
	public int chatRadius = 32;
	public boolean greetings = true;
	public boolean chatEnabled = true;
	public String chatTriggerWord = "Claude";
	/** {@code ollama}, {@code claude} or {@code none}. */
	public String provider = "ollama";
	public String ollamaUrl = "http://127.0.0.1:11434";
	public String ollamaModel = "qwen2.5:3b";
	public String claudeModel = "claude-sonnet-5-5";
	/** Empty means: read the {@code ANTHROPIC_API_KEY} environment variable. */
	public String claudeApiKey = "";
	public int maxTokens = 200;
	public int chatTimeoutSeconds = 30;
	public int chatCooldownSeconds = 5;

	public static AiBuildersConfig get() {
		return instance;
	}

	public static Path configDir() {
		return FabricLoader.getInstance().getConfigDir().resolve(AiBuilders.MOD_ID);
	}

	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve(AiBuilders.MOD_ID + ".json");
		try {
			if (Files.exists(file)) {
				try (Reader reader = Files.newBufferedReader(file)) {
					AiBuildersConfig loaded = GSON.fromJson(reader, AiBuildersConfig.class);
					if (loaded != null) {
						instance = loaded;
					}
				}
			}
			// Rewrite so newly added options appear in the file.
			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(instance, writer);
			}
			Files.createDirectories(configDir().resolve("schematics"));
		} catch (IOException | RuntimeException e) {
			AiBuilders.LOGGER.error("Could not load {}, using defaults", file, e);
		}
	}

	public String resolvedClaudeApiKey() {
		if (claudeApiKey != null && !claudeApiKey.isBlank()) {
			return claudeApiKey;
		}
		String env = System.getenv("ANTHROPIC_API_KEY");
		return env == null ? "" : env;
	}
}
