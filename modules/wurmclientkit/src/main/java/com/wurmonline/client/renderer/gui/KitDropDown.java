package com.wurmonline.client.renderer.gui;

import java.util.function.IntConsumer;

/**
 * A {@link WurmDropDown} that fires a callback when its value changes. Vanilla {@code WurmDropDown} has
 * <b>no change listener</b> — the dropdown popup commits a selection by calling the package-private
 * {@code setValue(int)}, and mods are left to poll {@code getValue()} every {@code gameTick}. This
 * subclass overrides {@code setValue} to notify an {@link IntConsumer}, so you react to selection
 * immediately instead of polling.
 *
 * <p>{@code WurmDropDown} is non-final, so this <b>is</b> the widget (add it straight to a panel) —
 * unlike {@link KitInputField}, which must wrap a {@code final} field. Lives in
 * {@code com.wurmonline.client.renderer.gui} to subclass the package-private class and override its
 * package-private {@code setValue}.
 *
 * <pre>{@code
 * KitDropDown material = new KitDropDown("material", new String[]{"iron", "steel", "bronze"})
 *         .onChange(i -> rebuildForMaterial(i));
 * row.addComponent(material);
 * int chosen = material.selected();
 * material.selectSilently(0);   // reset without firing the callback
 * }</pre>
 */
public class KitDropDown extends WurmDropDown {

	private final String[] options;
	private IntConsumer onChange;
	private boolean suppress;

	public KitDropDown(String name, String[] options) {
		this(name, 0, options);
	}

	public KitDropDown(String name, int value, String[] options) {
		super(name, value, options);
		// Fail fast: vanilla WurmDropDown.renderComponent dereferences options.length with no null check,
		// so a null here would NPE later on the render thread rather than at construction.
		this.options = java.util.Objects.requireNonNull(options, "options");
		super.setValue(clamp(value));
	}

	/** Fired when the selected index changes (via the popup or {@link #select(int)}). */
	public KitDropDown onChange(IntConsumer callback) {
		onChange = callback;
		return this;
	}

	/** The current selected index. */
	public int selected() {
		return getValue();
	}

	/** Number of options. */
	public int optionCount() {
		return options == null ? 0 : options.length;
	}

	/** The currently selected option text, or {@code ""} if there are no options. */
	public String selectedText() {
		int i = getValue();
		return options != null && i >= 0 && i < options.length ? options[i] : "";
	}

	/** Set the selection (clamped to a valid index) and fire {@link #onChange} if it changed. */
	public KitDropDown select(int index) {
		setValue(index);
		return this;
	}

	/** Set the selection without firing {@link #onChange} (e.g. programmatic reset). */
	public KitDropDown selectSilently(int index) {
		suppress = true;
		try {
			setValue(index);
		} finally {
			suppress = false;
		}
		return this;
	}

	private int clamp(int index) {
		if (options == null || options.length == 0) {
			return 0;
		}
		return index < 0 ? 0 : (index >= options.length ? options.length - 1 : index);
	}

	@Override
	void setValue(int value) {
		int clamped = clamp(value);
		boolean changed = clamped != getValue();
		super.setValue(clamped);
		if (changed && !suppress && onChange != null) {
			onChange.accept(clamped);
		}
	}
}
