package com.wurmonline.client.renderer.gui;

import java.util.Objects;

/**
 * Fluent builder for a {@link WurmPopup} context menu. Vanilla popups are awkward to extend: the
 * {@code addButton}/{@code addSeparator} methods and the button base are package-private, and a live
 * button is an inner class you must instantiate as {@code popup.new WPopupLiveButton(label){ ... }} and
 * give an abstract {@code handleLeftClick}. This wraps all of that so a mod adds a labelled action with
 * a {@link Runnable}. Generalises RecipeExamine's popup-button injection.
 *
 * <p>Lives in {@code com.wurmonline.client.renderer.gui} for the package-private popup API.
 *
 * <pre>{@code
 * WPopupBuilder.create("mymenu", "Options", x, y)
 *     .button("Examine", () -> examine(id))
 *     .separator()
 *     .label("(read-only)")
 *     .show();
 * }</pre>
 *
 * <p><b>Note</b> on {@code button(label, Runnable)}: the builder itself is lambda-free, but if you call
 * it from a class that is referenced by javassist-injected code (e.g. a helper a hook calls, like
 * RecipeExamine's), pass an anonymous {@code Runnable} rather than a lambda — a lambda would make that
 * helper carry {@code invokedynamic}, which the client's javassist can't parse (see
 * {@code docs/specs/private-class-techniques.md}).
 */
public final class WPopupBuilder {

	private final WurmPopup popup;

	public WPopupBuilder(WurmPopup popup) {
		this.popup = popup;
	}

	/** Create a fresh popup at a deterministic position (no random jitter). */
	public static WPopupBuilder create(String id, String title, int x, int y) {
		return new WPopupBuilder(new WurmPopup(id, title, x, y, false));
	}

	/** A clickable action row. {@code onClick} runs on the client thread (the popup auto-closes first). */
	public WPopupBuilder button(String label, Runnable onClick) {
		Objects.requireNonNull(onClick, "onClick");
		popup.addButton(popup.new WPopupLiveButton(label) {
			@Override
			protected void handleLeftClick() {
				onClick.run();
			}
		});
		return this;
	}

	/** An inert (non-clickable) label row. */
	public WPopupBuilder label(String text) {
		popup.addButton(popup.new WPopupDeadButton(text, null));
		return this;
	}

	public WPopupBuilder separator() {
		popup.addSeparator();
		return this;
	}

	/** The wrapped popup (e.g. to pass as a submenu, or for further vanilla calls). */
	public WurmPopup popup() {
		return popup;
	}

	/** Show the popup on the HUD. */
	public void show() {
		WurmComponent.hud.showPopupComponent(popup);
	}
}
