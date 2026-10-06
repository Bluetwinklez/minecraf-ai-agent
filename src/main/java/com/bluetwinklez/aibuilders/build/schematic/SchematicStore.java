package com.bluetwinklez.aibuilders.build.schematic;

import com.bluetwinklez.aibuilders.AiBuilders;
import com.bluetwinklez.aibuilders.config.AiBuildersConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;

/**
 * Finds schematic files in {@code config/aibuilders/schematics/} and {@code <gameDir>/schematics/}
 * (Litematica's default folder) and loads them off the server thread.
 */
public final class SchematicStore {
	private static final List<String> EXTENSIONS = List.of(".litematic", ".nbt");
	private static final long MAX_NBT_BYTES = 256L * 1024 * 1024;
	private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "AI Builders schematic loader");
		t.setDaemon(true);
		return t;
	});
	private static final Map<Path, Cached> CACHE = new LinkedHashMap<>(8, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Path, Cached> eldest) {
			return size() > 4;
		}
	};

	private record Cached(FileTime modified, Schematic schematic) {
	}

	private SchematicStore() {
	}

	public static List<Path> roots() {
		return List.of(
			AiBuildersConfig.configDir().resolve("schematics"),
			FabricLoader.getInstance().getGameDir().resolve("schematics")
		);
	}

	/** Names usable in commands: path relative to a root, with '/' separators, without extension. */
	public static List<String> list() {
		List<String> names = new ArrayList<>();
		for (Path root : roots()) {
			if (!Files.isDirectory(root)) {
				continue;
			}
			try (Stream<Path> files = Files.walk(root, 4)) {
				files.filter(Files::isRegularFile)
					.filter(p -> extensionOf(p).isPresent())
					.forEach(p -> {
						String rel = root.relativize(p).toString().replace('\\', '/');
						String name = rel.substring(0, rel.length() - extensionOf(p).get().length());
						if (!names.contains(name)) {
							names.add(name);
						}
					});
			} catch (IOException e) {
				AiBuilders.LOGGER.warn("Could not list {}", root, e);
			}
		}
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	/** Resolves a command name to a file, refusing anything outside the schematic folders. */
	public static Optional<Path> find(String name) {
		if (name.isBlank() || name.contains("..") || name.contains(":") || name.startsWith("/") || name.startsWith("\\")) {
			return Optional.empty();
		}
		for (Path root : roots()) {
			Path normalizedRoot = root.toAbsolutePath().normalize();
			for (String ext : candidatesFor(name)) {
				Path file = normalizedRoot.resolve(ext).normalize();
				if (file.startsWith(normalizedRoot) && Files.isRegularFile(file)) {
					return Optional.of(file);
				}
			}
		}
		return Optional.empty();
	}

	private static List<String> candidatesFor(String name) {
		if (extensionOf(Path.of(name)).isPresent()) {
			return List.of(name);
		}
		return EXTENSIONS.stream().map(ext -> name + ext).toList();
	}

	private static Optional<String> extensionOf(Path p) {
		String lower = p.getFileName().toString().toLowerCase(Locale.ROOT);
		return EXTENSIONS.stream().filter(lower::endsWith).findFirst();
	}

	/** Loads (or returns a cached copy of) a schematic. Completes on the loader thread. */
	public static CompletableFuture<Schematic> load(MinecraftServer server, String name) {
		Optional<Path> file = find(name);
		if (file.isEmpty()) {
			return CompletableFuture.failedFuture(new SchematicException("Schematic not found: " + name));
		}
		Path path = file.get();
		long maxBlocks = AiBuildersConfig.get().maxSchematicBlocks;
		var blocks = server.registryAccess().lookupOrThrow(Registries.BLOCK);
		var fixer = server.getFixerUpper();
		return CompletableFuture.supplyAsync(() -> {
			try {
				FileTime modified = Files.getLastModifiedTime(path);
				synchronized (CACHE) {
					Cached cached = CACHE.get(path);
					if (cached != null && cached.modified().equals(modified)) {
						return cached.schematic();
					}
				}
				CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.create(MAX_NBT_BYTES));
				Schematic schematic = extensionOf(path).orElse("").equals(".litematic")
					? LitematicReader.read(name, root, blocks, fixer, maxBlocks)
					: StructureNbtReader.read(name, root, blocks, fixer, maxBlocks);
				synchronized (CACHE) {
					CACHE.put(path, new Cached(modified, schematic));
				}
				return schematic;
			} catch (IOException e) {
				throw new SchematicException("Could not read " + path.getFileName() + ": " + e.getMessage());
			}
		}, LOADER);
	}

	/** Hash of the file content used to detect a schematic changing under a saved build. */
	public static long fingerprint(String name) {
		return find(name).map(p -> {
			try {
				return Files.size(p) * 31 + Files.getLastModifiedTime(p).toMillis();
			} catch (IOException e) {
				return -1L;
			}
		}).orElse(-1L);
	}
}
