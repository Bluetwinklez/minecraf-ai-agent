package com.bluetwinklez.aibuilders.build.schematic;

/** A schematic file that cannot be used; the message is shown to the player. */
public class SchematicException extends RuntimeException {
	public SchematicException(String message) {
		super(message);
	}
}
