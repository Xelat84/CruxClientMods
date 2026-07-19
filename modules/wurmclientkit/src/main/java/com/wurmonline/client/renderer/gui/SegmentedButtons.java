package com.wurmonline.client.renderer.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * A horizontal row of buttons where <b>exactly one is active</b>, the active one shown pressed
 * ({@code WButton.setDown(true)}). A mutually-exclusive mode toggle — "Per unit / Total", "Buy / Sell",
 * etc. Unifies the two hand-rolled idioms in AuctionHouse (setDown + dim the inactive field) and
 * BuyOrderWindow (a {@code "> "} label prefix) into one consistent visual.
 *
 * <p>Lives in {@code com.wurmonline.client.renderer.gui} to construct the package-private
 * {@link WButton} / {@link WurmArrayPanel} and implement {@link ButtonListener}.
 *
 * <pre>{@code
 * SegmentedButtons mode = new SegmentedButtons("priceMode", "Per unit", "Total")
 *         .onSelect(i -> applyPriceMode(i));   // fired on user change
 * row.addComponent(mode.panel());
 * int m = mode.selected();
 * mode.selectSilently(0);                      // reset without firing
 * }</pre>
 */
public final class SegmentedButtons implements ButtonListener {

	private final WurmArrayPanel<FlexComponent> bar;
	private final List<WButton> buttons = new ArrayList<>();
	private int selected = -1;
	private IntConsumer onSelect;

	public SegmentedButtons(String name) {
		bar = new WurmArrayPanel<>(name, WurmArrayPanel.DIR_HORIZONTAL);
	}

	public SegmentedButtons(String name, String... labels) {
		this(name);
		for (String label : labels) {
			add(label);
		}
	}

	/** The panel to add to a parent slot. */
	public WurmArrayPanel<FlexComponent> panel() {
		return bar;
	}

	/** Append a segment. The first segment added becomes active (without firing {@link #onSelect}). */
	public SegmentedButtons add(String label) {
		WButton button = new WButton(label, this);
		buttons.add(button);
		bar.addComponent(button);
		if (selected < 0) {
			apply(buttons.size() - 1, false);
		}
		return this;
	}

	/** Fired when the active segment changes (by click or {@link #select(int)}). */
	public SegmentedButtons onSelect(IntConsumer callback) {
		onSelect = callback;
		return this;
	}

	/** The active segment index, or -1 if there are no segments. */
	public int selected() {
		return selected;
	}

	/** Activate a segment and fire {@link #onSelect} if it changed. */
	public SegmentedButtons select(int index) {
		if (index != selected) {
			apply(index, true);
		}
		return this;
	}

	/** Activate a segment without firing {@link #onSelect}. */
	public SegmentedButtons selectSilently(int index) {
		apply(index, false);
		return this;
	}

	/**
	 * Fire {@link #onSelect} for the current selection. The default/initial selection is applied
	 * silently, so downstream state for the starting mode is never initialised through the callback —
	 * call this once after wiring {@link #onSelect} to drive that initial state.
	 */
	public SegmentedButtons fireSelected() {
		if (selected >= 0 && onSelect != null) {
			onSelect.accept(selected);
		}
		return this;
	}

	private void apply(int index, boolean fire) {
		if (index < 0 || index >= buttons.size()) {
			return;
		}
		selected = index;
		for (int i = 0; i < buttons.size(); i++) {
			buttons.get(i).setDown(i == index);
		}
		if (fire && onSelect != null) {
			onSelect.accept(index);
		}
	}

	@Override
	public void buttonPressed(WButton button) {
	}

	@Override
	public void buttonClicked(WButton button) {
		int index = buttons.indexOf(button);
		if (index >= 0) {
			select(index);
		}
	}
}
