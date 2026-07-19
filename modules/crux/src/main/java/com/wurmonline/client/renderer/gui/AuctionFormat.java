package com.wurmonline.client.renderer.gui;

/** Coin / weight / time-left formatting for the auction window. Coin base unit is iron. */
public final class AuctionFormat {

	private AuctionFormat() {
	}

	/**
	 * Parse a coin amount into iron. Accepts the game's native notation in any combination/spacing -
	 * "1g 2s 3c 4i", "11c2i", "45c", "1s2c" - or a bare number, which is read as raw iron ("4500" = 4500i).
	 * Returns {@code fallback} for null/blank/unparseable input.
	 */
	public static long parseCoin(String s, long fallback) {
		if (s == null) {
			return fallback;
		}
		String t = s.trim().toLowerCase();
		if (t.isEmpty()) {
			return fallback;
		}
		// A bare number (no denomination letters) means raw iron.
		try {
			return Long.parseLong(t);
		} catch (NumberFormatException notBare) {
			// fall through to denomination parsing
		}
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*([gsci])").matcher(t);
		long total = 0L;
		boolean any = false;
		while (m.find()) {
			long n = Long.parseLong(m.group(1));
			switch (m.group(2).charAt(0)) {
				case 'g': total += n * 1000000L; break;
				case 's': total += n * 10000L; break;
				case 'c': total += n * 100L; break;
				case 'i': total += n; break;
				default: break;
			}
			any = true;
		}
		return any ? total : fallback;
	}

	/**
	 * iron -> full coin string, e.g. 602 -> "6c 2i", 1050030 -> "1g 5s 30i". ALL non-zero denominations are shown:
	 * these figures feed escrow/price lines that must match the player's bank movement to the iron - truncating to
	 * the two largest units would make the shown total short by up to two denominations.
	 */
	public static String coin(long iron) {
		if (iron <= 0) {
			return "0i";
		}
		long gold = iron / 1000000L;
		long silver = iron / 10000L % 100L;
		long copper = iron / 100L % 100L;
		long rest = iron % 100L;
		StringBuilder sb = new StringBuilder();
		if (gold > 0) {
			sb.append(gold).append('g');
		}
		if (silver > 0) {
			if (sb.length() > 0) sb.append(' ');
			sb.append(silver).append('s');
		}
		if (copper > 0) {
			if (sb.length() > 0) sb.append(' ');
			sb.append(copper).append('c');
		}
		if (rest > 0) {
			if (sb.length() > 0) sb.append(' ');
			sb.append(rest).append('i');
		}
		return sb.length() == 0 ? "0i" : sb.toString();
	}

	/** grams -> plain kg number for a table column, e.g. "1.00" (no unit suffix, like the inventory weight column). */
	public static String weight(float grams) {
		return String.format("%.2f", grams / 1000.0f);
	}

	/** Quality like the inventory: whole numbers show no decimals, otherwise up to two. */
	public static String ql(double ql) {
		// Always two decimals, matching the client's inventory windows (e.g. "99.00", not "99").
		return String.format("%.2f", ql);
	}

	/** A relative remaining duration in ms -> "2d 04h" / "5h 12m" / "30m" / "expired". */
	public static String duration(long remain) {
		if (remain <= 0) {
			return "expired";
		}
		long mins = remain / 60000L;
		long days = mins / 1440L;
		long hours = mins / 60L % 24L;
		long m = mins % 60L;
		if (days > 0) {
			return days + "d " + String.format("%02dh", hours);
		}
		if (hours > 0) {
			return hours + "h " + String.format("%02dm", m);
		}
		return m + "m";
	}
}
