package com.bluetwinklez.aibuilders.chat;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Rule-based understanding of simple builder requests (Turkish and English), used when no
 * chat model is available. Example: "Ali ev şemasını buraya kursun" -> BUILD(Ali, ev, here).
 */
public final class IntentParser {
	public enum Action { BUILD, STOP, PAUSE, RESUME, STATUS, MATERIALS }

	/** {@code npc}/{@code schematic} are matched names or null; {@code here} means "at my position". */
	public record Intent(Action action, String npc, String schematic, boolean here) {
	}

	private static final List<String> BUILD = List.of("kur", "insa", "yap", "build", "construct", "olustur", "dik");
	private static final List<String> STOP = List.of("durdur", "dur", "birak", "iptal", "stop", "cancel");
	private static final List<String> PAUSE = List.of("duraklat", "bekle", "pause", "hold");
	private static final List<String> RESUME = List.of("devam", "resume", "continue", "surdur");
	private static final List<String> STATUS = List.of("durum", "status", "progress", "ilerleme");
	private static final List<String> MATERIALS = List.of("malzeme", "eksik", "lazim", "gerek", "material", "missing", "need");
	private static final Set<String> HERE = Set.of("buraya", "burada", "bura", "burda", "here", "yanima", "yanimda");

	private IntentParser() {
	}

	public static Optional<Intent> parse(String text, Collection<String> npcNames, Collection<String> schematicNames) {
		String[] words = ChatTriggers.normalize(text).split(" ");
		String npc = null;
		String schematic = null;
		boolean here = false;
		boolean[] used = new boolean[words.length];
		for (int i = 0; i < words.length; i++) {
			if (HERE.contains(words[i])) {
				here = true;
				used[i] = true;
			}
		}
		// Longer names first so "ev_buyuk" wins over "ev".
		for (int i = 0; i < words.length && npc == null; i++) {
			npc = wordMatch(words[i], npcNames);
			if (npc != null) {
				used[i] = true;
			}
		}
		for (int i = 0; i < words.length && schematic == null; i++) {
			if (!used[i]) {
				schematic = wordMatch(words[i], schematicNames);
				if (schematic != null) {
					used[i] = true;
				}
			}
		}
		String joined = String.join("", words);
		Action action = joined.contains("neyapiyor") || joined.contains("nasilgidiyor") || joined.contains("howisitgoing") ? Action.STATUS : null;
		for (int i = 0; i < words.length && action == null; i++) {
			if (!used[i]) {
				action = verb(words[i]);
			}
		}
		if (action == null) {
			return Optional.empty();
		}
		if (action == Action.BUILD && schematic == null) {
			return Optional.empty();
		}
		return Optional.of(new Intent(action, npc, schematic, here));
	}

	/**
	 * A word refers to a name if it equals the name's key or is the name plus a short Turkish
	 * suffix ("aliyi", "evi", "evin", "kuleyi").
	 */
	private static String wordMatch(String word, Collection<String> names) {
		String best = null;
		int bestLength = 0;
		for (String name : names) {
			// Schematics in sub folders ("houses/castle") can be called by their file name.
			String lastSegment = name.substring(name.lastIndexOf('/') + 1);
			for (String k : List.of(NameMatcher.key(name), NameMatcher.key(lastSegment))) {
				if (k.length() < 2) {
					continue;
				}
				boolean match = word.equals(k) || word.startsWith(k) && isSuffix(word.substring(k.length()));
				if (match && k.length() > bestLength) {
					best = name;
					bestLength = k.length();
				}
			}
		}
		return best;
	}

	/** Common Turkish case/possessive endings after a name ("ev" + "i", "kule" + "yi", "ali" + "nin"). */
	private static final Set<String> SUFFIXES = Set.of(
		"i", "u", "yi", "yu", "e", "a", "ye", "ya", "in", "un", "nin", "nun", "de", "da", "te", "ta",
		"den", "dan", "ten", "tan", "ni", "nu", "si", "su", "sini", "sunu", "ini", "unu", "ine", "una",
		"ina", "une", "na", "ne", "sina", "sine", "yla", "yle", "la", "le"
	);

	private static boolean isSuffix(String rest) {
		return SUFFIXES.contains(rest);
	}

	private static Action verb(String word) {
		// Order matters: "durdur" before "dur", "duraklat" before both.
		if (startsAny(word, PAUSE)) {
			return Action.PAUSE;
		}
		if (startsAny(word, STATUS)) {
			return Action.STATUS;
		}
		if (startsAny(word, STOP)) {
			return Action.STOP;
		}
		if (startsAny(word, RESUME)) {
			return Action.RESUME;
		}
		if (startsAny(word, MATERIALS)) {
			return Action.MATERIALS;
		}
		if (startsAny(word, BUILD)) {
			return Action.BUILD;
		}
		return null;
	}

	private static boolean startsAny(String word, List<String> stems) {
		for (String stem : stems) {
			if (word.startsWith(stem)) {
				return true;
			}
		}
		return false;
	}
}
