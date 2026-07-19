package com.wurmonline.clientkit;

/** Display formatting helpers. Pure logic, no client dependencies. Coin base unit is iron. */
public final class Format {

	private Format() {
	}

	/** iron -> short coin string, e.g. 602 -> "6c 2i", 18000 -> "1s 80c", 0 -> "0i". Largest two units. */
	public static String coin(long iron) {
		if (iron <= 0) {
			return "0i";
		}
		long gold = iron / 1000000L;
		long silver = iron / 10000L % 100L;
		long copper = iron / 100L % 100L;
		long rest = iron % 100L;
		String[] parts = new String[4];
		int n = 0;
		if (gold > 0) {
			parts[n++] = gold + "g";
		}
		if (silver > 0) {
			parts[n++] = silver + "s";
		}
		if (copper > 0) {
			parts[n++] = copper + "c";
		}
		if (rest > 0) {
			parts[n++] = rest + "i";
		}
		if (n == 0) {
			return "0i";
		}
		StringBuilder sb = new StringBuilder(parts[0]);
		if (n > 1) {
			sb.append(' ').append(parts[1]);
		}
		return sb.toString();
	}

	/** grams -> "1.00 kg". */
	public static String weight(float grams) {
		return String.format("%.2f kg", grams / 1000.0f);
	}

	/** A relative remaining duration in ms -> "2d 04h" / "5h 12m" / "30m" / "expired". */
	public static String duration(long remainMs) {
		if (remainMs <= 0) {
			return "expired";
		}
		long mins = remainMs / 60000L;
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

	/** An absolute epoch-ms expiry -> remaining duration string. */
	public static String timeLeft(long expiryEpochMs) {
		return duration(expiryEpochMs - System.currentTimeMillis());
	}

	/** QL: whole numbers show no decimals, else 2 decimals (92 -> "92", 92.34 -> "92.34"). */
	public static String ql(double q) {
		return q == Math.floor(q) ? String.valueOf((long) q) : String.format("%.2f", q);
	}
}
