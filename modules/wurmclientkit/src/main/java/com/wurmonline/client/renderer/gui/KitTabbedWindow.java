package com.wurmonline.client.renderer.gui;

import java.util.function.IntConsumer;

/**
 * A window that keeps its normal {@link WWindow} <b>title bar</b> and shows a strip of <b>real tabs</b>
 * (the vanilla tab skin, via {@link KitTab}) beneath it — layout is <i>[title bar] / [tab strip] /
 * [content]</i>. Vanilla {@code WurmTabbedWindow}/{@code WurmTabPanel} are package-private and
 * chat-coupled, so this composes a {@link KitTabPanel} as the window's content instead. For tabs inside
 * part of a window (not the whole window), use {@link KitTabPanel} directly in a slot.
 *
 * <p>Lives in the gui package for package-private access to {@link WWindow}.
 *
 * <pre>{@code
 * KitTabbedWindow w = new KitTabbedWindow("Auction");
 * w.addTab("Browse", browsePanel);
 * w.addTab("Sell",   sellPanel);
 * w.setInitialSize(600, 400, false, 0.5F, 0.5F);
 * w.show();
 * }</pre>
 */
public class KitTabbedWindow extends WWindow {

	private final KitTabPanel tabs;

	public KitTabbedWindow(String title) {
		super(title);
		setTitle(title);
		closeable = true;
		tabs = new KitTabPanel(title + "Tabs");
		setComponent(tabs);
	}

	/** Append a tab; the first added is shown by default. */
	public KitTabbedWindow addTab(String label, FlexComponent panel) {
		tabs.addTab(label, panel);
		return this;
	}

	/** Switch to a tab programmatically (e.g. a server "open on tab N" message). */
	public void focusTab(int index) {
		tabs.focusTab(index);
	}

	/** Fired with the new tab index when the active tab changes. */
	public KitTabbedWindow onTabChange(IntConsumer callback) {
		tabs.onTabChange(callback);
		return this;
	}

	/** The active tab index, or -1 if there are no tabs. */
	public int selected() {
		return tabs.selected();
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
