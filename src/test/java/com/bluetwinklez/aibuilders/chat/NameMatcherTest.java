package com.bluetwinklez.aibuilders.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class NameMatcherTest {
	private static final List<String> NAMES = List.of("Ali", "Ayse", "ev_buyuk", "kule", "Kale");

	@Test
	void exactIgnoresCaseAndTurkishLetters() {
		assertEquals("Ayse", NameMatcher.match("Ayşe", NAMES).value());
		assertEquals("Ali", NameMatcher.match("ALI", NAMES).value());
		assertEquals("ev_buyuk", NameMatcher.match("ev büyük", NAMES).value());
	}

	@Test
	void uniquePrefixAndTypos() {
		assertEquals("ev_buyuk", NameMatcher.match("ev", NAMES).value());
		assertEquals("kule", NameMatcher.match("kulle", NAMES).value());
	}

	@Test
	void fileNameOfSchematicInFolder() {
		assertEquals("houses/castle", NameMatcher.match("Castle", List.of("houses/castle", "kule")).value());
		assertEquals(NameMatcher.Kind.AMBIGUOUS, NameMatcher.match("castle", List.of("a/castle", "b/castle")).kind());
	}

	@Test
	void ambiguousAndMissing() {
		assertEquals(NameMatcher.Kind.AMBIGUOUS, NameMatcher.match("a", NAMES).kind());
		assertEquals(NameMatcher.Kind.NONE, NameMatcher.match("zeplin", NAMES).kind());
		assertEquals(NameMatcher.Kind.NONE, NameMatcher.match("", NAMES).kind());
	}
}
