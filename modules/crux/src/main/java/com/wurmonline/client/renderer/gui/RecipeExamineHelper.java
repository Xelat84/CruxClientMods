package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.WurmClientBase;

/**
 * Lives in the game package on purpose: {@link WurmPopup} (and its {@code addButton} method and
 * {@code title} field) are package-private, so the Examine button can only be built from inside
 * {@code com.wurmonline.client.renderer.gui}. The RecipeExamine mod's Javassist hook calls
 * {@link #addExamineButton(WurmPopup)} just before each recipe-browser popup is shown.
 */
public final class RecipeExamineHelper {

	private RecipeExamineHelper() {
	}

	/**
	 * Re-arms the crafting-recipe browser so it re-requests the full recipe list. The window pulls
	 * the list once per client session (its {@code openFirstTime} latch) and caches it, so a server
	 * reconnect leaves a stale matrix. {@code HeadsUpDisplay.reconnected()} resets its sibling
	 * windows but not this one; the RecipeExamine hook calls this from there. {@code openFirstTime}
	 * is package-private, so it can only be set from inside {@code com.wurmonline.client.renderer.gui}.
	 */
	public static void rearmCreationList(final CreationListWindow window) {
		if (window != null) {
			window.openFirstTime = true;
		}
	}

	public static void addExamineButton(final WurmPopup popup) {
		final String itemName = popup.title;
		if (itemName == null || itemName.isEmpty()) {
			return;
		}

		popup.addButton(popup.new WPopupLiveButton("Examine") {
			@Override
			protected void handleLeftClick() {
				showPlonk(itemName);
			}
		});
	}

	private static void showPlonk(String itemName) {
		String key = "plonk.recipes." + itemName.replaceAll(" ", "");
		String bml = WurmClientBase.getResourceManager().getResourceAsString(key);
		if (bml == null || bml.trim().isEmpty()) {
			bml = fallbackBml(itemName);
		}

		WurmComponent.hud.getPlonkViewer().showPlonk(itemName, bml, 400, 300);
	}

	private static String fallbackBml(String itemName) {
		String safe = itemName.replace("\"", "").replace("'", "");
		return "border { center { text{ text=\"" + safe + " - no recipe details available.\" } }; null; null; null; null;}";
	}
}
