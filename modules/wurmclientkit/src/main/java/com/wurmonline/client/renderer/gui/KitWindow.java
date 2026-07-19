package com.wurmonline.client.renderer.gui;

/**
 * A titled {@link WWindow} with a <b>mixable content area</b>: unlike vanilla item windows (Inventory,
 * containers) which are items-only, a {@code KitWindow} lets you place an item-container tree
 * ({@link KitItemList}) in one region and arbitrary widgets (buttons, inputs, tabs, labels) in the
 * others. Content is laid out in an inner 5-region border panel (NORTH/EAST/SOUTH/WEST/CENTER), so the
 * window's own title-bar / close-box chrome is untouched.
 *
 * <p>This SDK only makes such a window <i>showable</i>; opening it in response to a server packet, and
 * feeding a container's items, is the mod's job (see {@link KitItemList#container(long)}).
 *
 * <p>Lives in the gui package for package-private access to {@link WWindow}/{@link WurmBorderPanel}.
 *
 * <pre>{@code
 * KitWindow w = new KitWindow("Vault");
 * w.set(new KitItemList(KitItemList.container(windowId)).component(), KitWindow.CENTER);
 * w.set(sidebar, KitWindow.WEST);
 * w.set(buttonRow, KitWindow.SOUTH);
 * w.show();
 * }</pre>
 */
public class KitWindow extends WWindow {

	public static final int NORTH = WurmBorderPanel.NORTH;
	public static final int EAST = WurmBorderPanel.EAST;
	public static final int SOUTH = WurmBorderPanel.SOUTH;
	public static final int WEST = WurmBorderPanel.WEST;
	public static final int CENTER = WurmBorderPanel.CENTER;

	private final WurmBorderPanel content;

	public KitWindow(String title) {
		super(title);
		setTitle(title);
		closeable = true;
		content = new WurmBorderPanel(title + "Content");
		setComponent(content);
	}

	/** Place a component in one of the five regions ({@link #CENTER}, {@link #NORTH}, …). */
	public KitWindow set(FlexComponent component, int region) {
		content.setComponent(component, region);
		return this;
	}

	public void show() {
		if (!hud.isComponentEnabled(this)) {
			hud.toggleComponent(this);
		}
	}

	@Override
	protected void closePressed() {
		if (hud.isComponentEnabled(this)) {
			hud.toggleComponent(this);
		}
	}
}
