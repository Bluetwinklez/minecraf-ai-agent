package com.bluetwinklez.aibuilders.build.schematic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SchematicPathsTest {
	@TempDir Path temp;

	@Test void acceptsNestedFilesAndRejectsTraversal() throws Exception {
		Path root = Files.createDirectory(temp.resolve("schematics"));
		Path nested = Files.createDirectory(root.resolve("nested"));
		Path file = Files.writeString(nested.resolve("house.nbt"), "fixture");
		assertEquals(file.toRealPath(), SchematicPaths.findWithin(root, "nested/house.nbt").orElseThrow());
		Files.writeString(temp.resolve("outside.nbt"), "fixture");
		assertTrue(SchematicPaths.findWithin(root, "../outside.nbt").isEmpty());
		assertTrue(SchematicPaths.findWithin(root, file.toAbsolutePath().toString()).isEmpty());
		assertTrue(SchematicPaths.findWithin(root, "missing.nbt").isEmpty());
		assertTrue(SchematicPaths.findWithin(root, "invalid\0name").isEmpty());
	}

	@Test void refusesSymlinksOutsideRoot() throws Exception {
		Path root = Files.createDirectory(temp.resolve("schematics"));
		Path outside = Files.writeString(temp.resolve("outside.nbt"), "fixture");
		try { Files.createSymbolicLink(root.resolve("escape.nbt"), outside); }
		catch (IOException | UnsupportedOperationException | SecurityException e) {
			assumeTrue(false, "Platform does not permit creating test symlinks");
		}
		assertTrue(SchematicPaths.findWithin(root, "escape.nbt").isEmpty());
	}
}
