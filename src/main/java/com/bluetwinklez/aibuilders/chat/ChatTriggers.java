package com.bluetwinklez.aibuilders.chat;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Pure text matching for chat triggers; kept free of Minecraft classes so it is unit-testable. */
public final class ChatTriggers {
	public enum Greeting { SELAMUN_ALEYKUM, SELAM }

	private static final Set<String> SA = Set.of("sa", "sea", "selamun aleykum", "selamunaleykum", "selamın aleyküm", "slm aleykum");
	private static final Set<String> HELLO = Set.of("selam", "slm", "merhaba", "mrb");

	private ChatTriggers() {
	}

	/** Lowercases, folds Turkish diacritics, drops punctuation and squeezes spaces. */
	public static String normalize(String text) {
		String lower = text.toLowerCase(Locale.ROOT)
			.replace('ı', 'i').replace('ş', 's').replace('ğ', 'g').replace('ç', 'c').replace('ö', 'o').replace('ü', 'u');
		String folded = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return folded.replaceAll("[^a-z0-9 ]", "").replaceAll("\\s+", " ").trim();
	}

	public static Optional<Greeting> greeting(String message) {
		String n = normalize(message);
		String compact = n.replace(" ", "");
		if (compact.equals("sa") || compact.equals("selamunaleykum") || compact.equals("selaminaleykum") || SA.contains(n)) {
			return Optional.of(Greeting.SELAMUN_ALEYKUM);
		}
		if (HELLO.contains(n)) {
			return Optional.of(Greeting.SELAM);
		}
		return Optional.empty();
	}

	/**
	 * If {@code message} starts with the trigger word as a whole word (e.g. "Claude test",
	 * "claude: hi", "Claude, ..."), returns the rest of the message. "Claudeee x" or
	 * "hi Claude" do not match.
	 */
	public static Optional<String> triggered(String triggerWord, String message) {
		String trimmed = message.strip();
		if (triggerWord.isEmpty() || trimmed.length() < triggerWord.length()
			|| !trimmed.regionMatches(true, 0, triggerWord, 0, triggerWord.length())) {
			return Optional.empty();
		}
		String rest = trimmed.substring(triggerWord.length());
		if (!rest.isEmpty() && Character.isLetterOrDigit(rest.charAt(0))) {
			return Optional.empty();
		}
		rest = rest.replaceFirst("^[\\s,:;!.-]+", "").strip();
		return Optional.of(rest);
	}
}
