package com.bluetwinklez.aibuilders.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bluetwinklez.aibuilders.chat.IntentParser.Action;
import com.bluetwinklez.aibuilders.chat.IntentParser.Intent;
import java.util.List;
import org.junit.jupiter.api.Test;

class IntentParserTest {
	private static final List<String> NPCS = List.of("Ali", "Veli");
	private static final List<String> SCHEMATICS = List.of("ev", "kule", "houses/castle");

	private static Intent parse(String text) {
		return IntentParser.parse(text, NPCS, SCHEMATICS).orElseThrow(() -> new AssertionError("no intent for: " + text));
	}

	@Test
	void buildInTurkish() {
		Intent i = parse("Ali ev şemasını buraya kursun");
		assertEquals(Action.BUILD, i.action());
		assertEquals("Ali", i.npc());
		assertEquals("ev", i.schematic());
		assertTrue(i.here());
	}

	@Test
	void buildWithSuffixesAndNoNpc() {
		Intent i = parse("kuleyi inşa et");
		assertEquals(Action.BUILD, i.action());
		assertNull(i.npc());
		assertEquals("kule", i.schematic());
		assertFalse(i.here());
	}

	@Test
	void buildInEnglish() {
		Intent i = parse("Veli build the castle here");
		assertEquals(Action.BUILD, i.action());
		assertEquals("houses/castle", i.schematic());
		assertTrue(i.here());
	}

	@Test
	void controlVerbs() {
		assertEquals(Action.STOP, parse("Ali'yi durdur").action());
		assertEquals("Ali", parse("Ali'yi durdur").npc());
		assertEquals(Action.PAUSE, parse("Veli biraz bekle").action());
		assertEquals(Action.RESUME, parse("Ali devam et").action());
		assertEquals(Action.STATUS, parse("Ali ne yapıyor").action());
		assertEquals(Action.STATUS, parse("Veli durum").action());
		assertEquals(Action.MATERIALS, parse("ev için ne lazım").action());
	}

	@Test
	void noFalseMatches() {
		assertTrue(IntentParser.parse("evet tabi", NPCS, SCHEMATICS).isEmpty());
		assertTrue(IntentParser.parse("merhaba nasılsın", NPCS, SCHEMATICS).isEmpty());
		// A build without a known schematic is not an intent.
		assertTrue(IntentParser.parse("Ali bir şey yap", NPCS, SCHEMATICS).isEmpty());
	}
}
