package com.bluetwinklez.aibuilders.build.schematic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Filesystem containment for untrusted schematic names, including symbolic links. */
final class SchematicPaths {
	private SchematicPaths() { }

	static Optional<Path> findWithin(Path root, String candidate) {
		try {
			Path realRoot = root.toRealPath();
			Path relative = Path.of(candidate);
			if (relative.isAbsolute()) return Optional.empty();
			Path realFile = realRoot.resolve(relative).toRealPath();
			return realFile.startsWith(realRoot) && Files.isRegularFile(realFile)
				? Optional.of(realFile) : Optional.empty();
		} catch (IOException | IllegalArgumentException | SecurityException e) {
			return Optional.empty();
		}
	}
}
