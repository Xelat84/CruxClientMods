package com.wurmonline.client.renderer.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * A reusable tab container: a strip of real tab headers ({@link KitTab}) in the NORTH slot and a
 * swapped content panel in the CENTER. It's a {@link WurmBorderPanel} (a {@code FlexComponent}), so it
 * drops into <b>any slot of any window</b> — unlike {@link KitTabbedWindow}, which makes the whole
 * window tabbed. Exactly one tab is active; clicking a header swaps the content and fires
 * {@link #onTabChange}.
 *
 * <p>Lives in {@code com.wurmonline.client.renderer.gui} for the package-private widgets.
 *
 * <pre>{@code
 * KitTabPanel tabs = new KitTabPanel("browserTabs")
 *         .onTabChange(i -> requestTabData(i));
 * tabs.addTab("Browse",   browsePanel);
 * tabs.addTab("Settings", settingsPanel);
 * someWindow.setComponent(tabs, WurmBorderPanel.CENTER);   // drop it into a slot
 * }</pre>
 */
public class KitTabPanel extends WurmBorderPanel implements ButtonListener {

	private final WurmArrayPanel<FlexComponent> tabBar;
	private final WurmBorderPanel content;
	private final List<KitTab> tabs = new ArrayList<>();
	private final List<FlexComponent> panels = new ArrayList<>();
	private int selected = -1;
	private IntConsumer onTabChange;

	public KitTabPanel(String name) {
		super(name);
		tabBar = new WurmArrayPanel<>(name + "Bar", WurmArrayPanel.DIR_HORIZONTAL);
		content = new WurmBorderPanel(name + "Content");
		setComponent(tabBar, NORTH);
		setComponent(content, CENTER);
	}

	/** Append a tab. The first tab added becomes active (without firing {@link #onTabChange}). */
	public KitTabPanel addTab(String label, FlexComponent panel) {
		KitTab tab = new KitTab(label, this);
		tabs.add(tab);
		panels.add(panel);
		tabBar.addComponent(tab);
		if (selected < 0) {
			apply(tabs.size() - 1, false);
		}
		return this;
	}

	/** Fired with the new tab index when the active tab changes (by click or {@link #focusTab}). */
	public KitTabPanel onTabChange(IntConsumer callback) {
		onTabChange = callback;
		return this;
	}

	/** The active tab index, or -1 if there are no tabs. */
	public int selected() {
		return selected;
	}

	/** Activate a tab programmatically and fire {@link #onTabChange} if it changed. */
	public KitTabPanel focusTab(int index) {
		if (index != selected) {
			apply(index, true);
		}
		return this;
	}

	private void apply(int index, boolean fire) {
		if (index < 0 || index >= tabs.size()) {
			return;
		}
		selected = index;
		content.setComponent(panels.get(index), CENTER);
		for (int i = 0; i < tabs.size(); i++) {
			tabs.get(i).setSelected(i == index);
		}
		if (fire && onTabChange != null) {
			onTabChange.accept(index);
		}
	}

	@Override
	public void buttonPressed(WButton button) {
	}

	@Override
	public void buttonClicked(WButton button) {
		int index = tabs.indexOf(button);
		if (index >= 0) {
			focusTab(index);
		}
	}
}
