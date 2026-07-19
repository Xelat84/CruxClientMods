package com.wurmonline.client.renderer.gui;

import java.util.function.Consumer;

/**
 * Self-managing wrapper around the game's {@link WurmInputField}. Solves the two things every mod
 * hand-rolls for a form field:
 *
 * <ol>
 *   <li><b>The stray {@code "] "} prompt</b> — cleared on construction.</li>
 *   <li><b>Enter wipes the text</b> — vanilla single-line fields are chat-style: pressing Enter
 *       archives the value to history and blanks the field, so mods keep a shadow {@code String}
 *       mirror to survive it. This wrapper sets {@code simpleInput = true}, which makes the field keep
 *       its text on Enter (real form-field behaviour), and additionally mirrors the committed value so
 *       {@link #getValue()} is always correct regardless of field mode.</li>
 * </ol>
 *
 * <p>{@link WurmInputField} is {@code final}, so this is a wrapper (compose + implement the listener),
 * not a subclass — see {@code docs/specs/private-class-techniques.md} technique B. Add the wrapped
 * field to a panel with {@link #field()}. Lives in {@code com.wurmonline.client.renderer.gui} for
 * package-private access to {@link WurmInputField} and {@link InputFieldListener}.
 *
 * <p>Typical use:
 * <pre>{@code
 * KitInputField price = new KitInputField("price").onSubmit(v -> requestQuote());
 * row.addComponent(price.field());
 * long iron = price.asLong(0);       // parse with a default
 * Long optional = price.asLongOrNull(); // null when blank/invalid
 * }</pre>
 */
public final class KitInputField implements InputFieldListener {

	private final WurmInputField field;
	private String committed = "";

	private Consumer<String> onSubmit;
	private Consumer<String> onChange;
	private Runnable onEscape;

	public KitInputField(String name) {
		field = new WurmInputField(name, this);
		field.prompt = "";
		field.simpleInput = true;
	}

	/**
	 * Optional hardening for the Down-arrow edge case. {@code simpleInput} stops Enter from clearing
	 * the field, but the vanilla Down-arrow branch clears it unconditionally. Call
	 * {@link com.wurmonline.clientkit.GuiPatches#installInputFieldFix()} once in {@code preInit()} to
	 * gate that clear on {@code simpleInput}; without it, Down-arrow in a focused field still wipes it.
	 */
	public static void installKeyFix() {
		com.wurmonline.clientkit.GuiPatches.installInputFieldFix();
	}

	/**
	 * The wrapped widget — add this to a {@code WurmArrayPanel} / {@code WurmBorderPanel} slot.
	 * Mutate the text via {@link #setValue(String)}, not the returned field, or {@link #getValue()}
	 * desyncs from what is displayed.
	 */
	public WurmInputField field() {
		return field;
	}

	/**
	 * Enable/disable the field. Disabled greys the text (and dims the texture) AND, with
	 * {@code GuiPatches.installDisabledInputFocusFix()} installed, makes it non-focusable (no caret) — so a
	 * disabled field visibly reads as disabled instead of looking identical to an editable one.
	 */
	public KitInputField setEnabled(boolean enabled) {
		field.enabled = enabled;
		// setColor tints the field's panel-texture background (drawn at FULL alpha in WurmInputField.renderComponent),
		// so a disabled field's whole box is a darker shade regardless of content; setPenColor dims any typed text.
		// r/g/b persist and are never reset elsewhere, so this state is stable across re-layout and re-enable.
		float shade = enabled ? 1.0f : 0.35f;
		field.setColor(shade, shade, shade);
		field.setPenColor(shade, shade, shade);
		return this;
	}

	/** Cap the number of characters the field accepts. */
	public KitInputField maxChars(int max) {
		field.setMaxInput(max);
		return this;
	}

	/** Fired on Enter, with the field's current text. */
	public KitInputField onSubmit(Consumer<String> callback) {
		onSubmit = callback;
		return this;
	}

	/** Fired on every edit, with the new text. */
	public KitInputField onChange(Consumer<String> callback) {
		onChange = callback;
		return this;
	}

	/** Fired on Esc. */
	public KitInputField onEscape(Runnable callback) {
		onEscape = callback;
		return this;
	}

	/** The current committed value (never null). */
	public String getValue() {
		return committed;
	}

	/** True when the value is empty or whitespace-only. */
	public boolean isBlank() {
		return committed.trim().isEmpty();
	}

	public KitInputField setValue(String value) {
		committed = value == null ? "" : value;
		field.setTextMoveToEnd(committed);
		return this;
	}

	public void clear() {
		setValue("");
	}

	public int asInt(int fallback) {
		try {
			return Integer.parseInt(committed.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	public long asLong(long fallback) {
		try {
			return Long.parseLong(committed.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	public double asDouble(double fallback) {
		try {
			return Double.parseDouble(committed.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	/** Parsed integer, or {@code null} when blank or unparseable (for optional fields). */
	public Integer asIntOrNull() {
		if (isBlank()) {
			return null;
		}
		try {
			return Integer.valueOf(committed.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public Long asLongOrNull() {
		if (isBlank()) {
			return null;
		}
		try {
			return Long.valueOf(committed.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public Double asDoubleOrNull() {
		if (isBlank()) {
			return null;
		}
		try {
			return Double.valueOf(committed.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	@Override
	public void handleInput(String text) {
		committed = text == null ? "" : text;
		if (onSubmit != null) {
			onSubmit.accept(committed);
		}
	}

	@Override
	public void handleInputChanged(WurmInputField source, String text) {
		committed = text == null ? "" : text;
		if (onChange != null) {
			onChange.accept(committed);
		}
	}

	@Override
	public void handleEscape(WurmInputField source) {
		if (onEscape != null) {
			onEscape.run();
		}
	}
}
