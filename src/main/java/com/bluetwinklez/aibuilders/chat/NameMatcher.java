package com.bluetwinklez.aibuilders.chat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Resolves a loosely typed name (from chat or a model) against known names: exact
 * (case and Turkish diacritics ignored), then a unique prefix, then a unique close typo.
 */
public final class NameMatcher {
	public enum Kind { FOUND, AMBIGUOUS, NONE }

	public record Match(Kind kind, String value, List<String> candidates) {
		public boolean found() {
			return kind == Kind.FOUND;
		}
	}

	private static final int MAX_TYPOS = 2;

	private NameMatcher() {
	}

	public static Match match(String query, Collection<String> names) {
		String q = key(query);
		if (q.isEmpty()) {
			return new Match(Kind.NONE, null, List.copyOf(names));
		}
		for (String name : names) {
			if (key(name).equals(q)) {
				return new Match(Kind.FOUND, name, List.of(name));
			}
		}
		// Schematics in sub folders ("houses/castle") can be called by their file name.
		List<String> byFileName = new ArrayList<>();
		for (String name : names) {
			if (name.contains("/") && key(name.substring(name.lastIndexOf('/') + 1)).equals(q)) {
				byFileName.add(name);
			}
		}
		if (!byFileName.isEmpty()) {
			return byFileName.size() == 1 ? new Match(Kind.FOUND, byFileName.getFirst(), byFileName) : new Match(Kind.AMBIGUOUS, null, byFileName);
		}
		List<String> prefixed = new ArrayList<>();
		for (String name : names) {
			String k = key(name);
			if (k.startsWith(q) || q.startsWith(k) && q.length() - k.length() <= 4) {
				prefixed.add(name);
			}
		}
		if (prefixed.size() == 1) {
			return new Match(Kind.FOUND, prefixed.getFirst(), prefixed);
		}
		if (prefixed.size() > 1) {
			return new Match(Kind.AMBIGUOUS, null, prefixed);
		}
		int best = Integer.MAX_VALUE;
		List<String> closest = new ArrayList<>();
		for (String name : names) {
			int d = distance(key(name), q);
			if (d < best) {
				best = d;
				closest.clear();
				closest.add(name);
			} else if (d == best) {
				closest.add(name);
			}
		}
		if (best <= MAX_TYPOS && best < q.length()) {
			return closest.size() == 1 ? new Match(Kind.FOUND, closest.getFirst(), closest) : new Match(Kind.AMBIGUOUS, null, closest);
		}
		return new Match(Kind.NONE, null, List.copyOf(names));
	}

	/** Comparison key: normalized text without spaces, separators or apostrophes. */
	static String key(String s) {
		return ChatTriggers.normalize(s.replace('_', ' ').replace('-', ' ').replace('/', ' ')).replace(" ", "");
	}

	static int distance(String a, String b) {
		int[] prev = new int[b.length() + 1];
		int[] cur = new int[b.length() + 1];
		for (int j = 0; j <= b.length(); j++) {
			prev[j] = j;
		}
		for (int i = 1; i <= a.length(); i++) {
			cur[0] = i;
			for (int j = 1; j <= b.length(); j++) {
				int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
			}
			int[] t = prev;
			prev = cur;
			cur = t;
		}
		return prev[b.length()];
	}
}
