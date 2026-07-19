package com.wurmonline.client.renderer.gui;

import java.util.List;

/**
 * A vertical stack of plain text lines with an optional header, where <b>only non-blank lines are
 * shown</b> and the whole box (header included) collapses to nothing when every line is blank. This is
 * the general form of AuctionHouse's "- Pricing -" box: an absent fact is simply omitted, never
 * printed as an empty or negative line.
 *
 * <p>Labels are pooled and reused across {@link #set} calls, so refreshing the box every tick doesn't
 * churn widgets. Lives in {@code com.wurmonline.client.renderer.gui} for package-private access to
 * {@link WurmArrayPanel} and {@link WurmLabel}.
 *
 * <pre>{@code
 * ValueLineBox pricing = new ValueLineBox("pricing", "- Pricing -").appendTo(sidebar);
 * // later, on refresh:
 * pricing.set(traderLine, otherSellersLine, otherBuyersLine);  // blanks omitted; all blank -> hidden
 * }</pre>
 */
public final class ValueLineBox {

	private final WurmArrayPanel<FlexComponent> box;
	private final WurmLabel header;

	public ValueLineBox(String name) {
		this(name, null);
	}

	/** @param headerText a fixed header shown above the lines when any line is present, or null for none. */
	public ValueLineBox(String name, String headerText) {
		// PLAIN vertical (NOT autoWidth): the box shrink-wraps its width to its widest line, and each line is a
		// fresh WurmLabel sized to its text (see apply). Width therefore flows UP from the content, so lines never
		// clip. autoWidth does the opposite - it forces every line DOWN to the parent's width, which clips long
		// lines whenever the parent column is narrower than the text (the "- Pricing -" cut-off bug).
		box = new WurmArrayPanel<>(name, WurmArrayPanel.DIR_VERTICAL);
		header = headerText != null ? new WurmLabel(headerText) : null;
	}

	/** The panel to add to a parent slot (or use {@link #appendTo}). */
	public WurmArrayPanel<FlexComponent> panel() {
		return box;
	}

	/** Convenience: append this box's panel to {@code parent} and return this. */
	public ValueLineBox appendTo(WurmArrayPanel<FlexComponent> parent) {
		parent.addComponent(box);
		return this;
	}

	/** Set the lines. Null/blank entries are omitted; if none remain, the box (header included) is empty. */
	public void set(String... lines) {
		apply(lines);
	}

	/** {@link #set(String...)} for a list (a null list clears the box). */
	public void set(List<String> lines) {
		apply(lines == null ? new String[0] : lines.toArray(new String[0]));
	}

	public void clear() {
		box.removeAllComponents();
	}

	private void apply(String[] lines) {
		box.removeAllComponents();
		int shown = 0;
		for (String line : lines) {
			if (present(line)) {
				shown++;
			}
		}
		if (shown == 0) {
			return;
		}
		if (header != null) {
			box.addComponent(header);
		}
		// A fresh WurmLabel(text) sizes its width to the text; setLabel() on a reused label never resizes, so a
		// pooled label created with " " would clip any longer line. Rebuild the labels each set() - a pricing box
		// refreshes only when its data changes, not per frame, so the churn is negligible and clipping is gone.
		for (String line : lines) {
			if (present(line)) {
				box.addComponent(new WurmLabel(line));
			}
		}
	}

	private static boolean present(String s) {
		return s != null && !s.trim().isEmpty();
	}
}
