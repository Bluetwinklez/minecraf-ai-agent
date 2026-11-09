package com.bluetwinklez.aibuilders.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChatTriggersTest {
	@Test
	void recognisesSelamunAleykumVariants() {
		for (String msg : new String[] {"sa", "SA", "sa!", "s.a", "S.a.", "Selamün Aleyküm", "selamun aleykum", "sea"}) {
			assertEquals(Optional.of(ChatTriggers.Greeting.SELAMUN_ALEYKUM), ChatTriggers.greeting(msg), msg);
		}
	}

	@Test
	void recognisesHello() {
		assertEquals(Optional.of(ChatTriggers.Greeting.SELAM), ChatTriggers.greeting("Selam"));
		assertEquals(Optional.of(ChatTriggers.Greeting.SELAM), ChatTriggers.greeting("merhaba!"));
	}

	@Test
	void ignoresOtherMessages() {
		for (String msg : new String[] {"saat kaç", "saat", "sana ne", "as", "selam millet nasılsınız"}) {
			assertTrue(ChatTriggers.greeting(msg).isEmpty(), msg);
		}
	}

	@Test
	void triggerWordMustStartTheMessageAsAWord() {
		assertEquals(Optional.of("test test"), ChatTriggers.triggered("Claude", "Claude test test"));
		assertEquals(Optional.of("selam"), ChatTriggers.triggered("Claude", "claude: selam"));
		assertEquals(Optional.of("kaç kalas lazım?"), ChatTriggers.triggered("Claude", "  Claude, kaç kalas lazım?"));
		assertEquals(Optional.of(""), ChatTriggers.triggered("Claude", "Claude"));
		assertTrue(ChatTriggers.triggered("Claude", "Claudeee test").isEmpty());
		assertTrue(ChatTriggers.triggered("Claude", "merhaba Claude").isEmpty());
		assertTrue(ChatTriggers.triggered("Claude", "Clau").isEmpty());
	}
}
