package com.wurmonline.client.renderer.gui;

import java.util.Objects;

import com.wurmonline.client.game.inventory.InventoryMetaWindowView;

/**
 * An embeddable, fully-functional <b>item-container tree</b> — icons, QL/damage/weight columns, folded
 * stacks, live add/remove/update, native drag-to-move, and the real right-click item action menu — that
 * you drop into any window slot next to other widgets. It wraps the vanilla {@code InventoryListComponent}
 * (which is itself an embeddable {@code FlexComponent}, not a window), so all of that behaviour comes for
 * free; the SDK just gives it a clean, package-safe front and the view lookups.
 *
 * <p>Point it at an {@link InventoryMetaWindowView}: the player inventory ({@link #playerInventory()}),
 * or a container window the <b>server has opened</b> ({@link #container(long)}). This SDK does not open
 * containers or handle packets — the mod/server does that; the component only renders whatever view it's
 * given and stays live via the view's item listener.
 *
 * <p>Lives in {@code com.wurmonline.client.renderer.gui} for the package-private widget plumbing.
 *
 * <pre>{@code
 * KitItemList items = new KitItemList(KitItemList.playerInventory());
 * window.set(items.component(), KitWindow.CENTER);   // mix it with other regions
 * // on window close:  items.destroy();              // unregister the item listener
 * }</pre>
 */
public final class KitItemList {

	private final InventoryListComponent component;

	/**
	 * Item list over {@code view} matching a real item window (Inventory / container): QL/damage/weight
	 * columns, no price, imp column available (the "Toggle imp column" right-click option is present), no
	 * server preload. For the trade-window look (with price) or lazy player-inventory loading, use the
	 * full constructor.
	 */
	public KitItemList(InventoryMetaWindowView view) {
		this(view, false, true, false);
	}

	/**
	 * @param showPrice  add a price column (as the trade window does)
	 * @param canShowImp make the improvement (imp) column available — adds the "Toggle imp column"
	 *                   right-click option; initial visibility follows the client's imp-column option
	 * @param preload    lazily request the inventory from the server on first render (the vanilla
	 *                   "Loading.." behaviour) — leave false for a view the server already populates
	 */
	public KitItemList(InventoryMetaWindowView view, boolean showPrice, boolean canShowImp, boolean preload) {
		component = new InventoryListComponent(Objects.requireNonNull(view, "view"), showPrice, canShowImp, preload);
	}

	/** The component to add to a window slot (it IS a {@code FlexComponent}). */
	public FlexComponent component() {
		return component;
	}

	/** Unregister the item listener. Call when the hosting window is closed/discarded. */
	public void destroy() {
		component.destroy();
	}

	/** The player's own inventory view (window id -1) — always available once in-world. */
	public static InventoryMetaWindowView playerInventory() {
		return WurmComponent.hud.getWorld().getInventoryManager().getPlayerInventory();
	}

	/**
	 * The view for a container window the server has opened, or {@code null} if no such window exists.
	 * A window id the server has not opened resolves to a non-view placeholder, so this returns null
	 * rather than risking a bad cast — always null-check the result.
	 */
	public static InventoryMetaWindowView container(long windowId) {
		Object window = WurmComponent.hud.getWorld().getInventoryManager().getWindow(windowId);
		return window instanceof InventoryMetaWindowView ? (InventoryMetaWindowView) window : null;
	}
}
