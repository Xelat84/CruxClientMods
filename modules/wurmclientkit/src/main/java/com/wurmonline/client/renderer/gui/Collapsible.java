package com.wurmonline.client.renderer.gui;

import java.util.Objects;

/**
 * A position-stable show/hide wrapper for a single component. Wurm's widgets have no "visible" flag,
 * and the naive add/remove-from-parent trick breaks layout order because {@link WurmArrayPanel} has no
 * insert-at-index — re-showing would append the child at the end. {@code Collapsible} instead keeps a
 * slot panel permanently in the parent and toggles the child <i>inside the slot</i>, so the widget's
 * position is preserved and the surrounding layout collapses/expands around it (the same mechanism
 * {@link ValueLineBox} uses to hide itself).
 *
 * <p>Lives in {@code com.wurmonline.client.renderer.gui} for the package-private {@link WurmArrayPanel}.
 *
 * <pre>{@code
 * Collapsible advanced = new Collapsible("advanced", buildAdvancedPanel());
 * column.addComponent(advanced.component());   // slot holds its place in the column
 * advanced.hide();                             // child removed; the column collapses the gap
 * advanced.setVisible(showAdvanced);           // back in its original position
 * }</pre>
 */
public final class Collapsible {

	private final WurmArrayPanel<FlexComponent> slot;
	private final FlexComponent child;
	private boolean visible = true;

	public Collapsible(String name, FlexComponent child) {
		this.child = Objects.requireNonNull(child, "child");
		slot = new WurmArrayPanel<>(name, WurmArrayPanel.DIR_VERTICAL);
		slot.addComponent(child);
	}

	/** The slot to add to a parent — it stays put while the child toggles. */
	public FlexComponent component() {
		return slot;
	}

	public boolean isVisible() {
		return visible;
	}

	public Collapsible setVisible(boolean visible) {
		if (visible == this.visible) {
			return this;
		}
		this.visible = visible;
		slot.removeAllComponents();
		if (visible) {
			slot.addComponent(child);
		}
		return this;
	}

	public Collapsible show() {
		return setVisible(true);
	}

	public Collapsible hide() {
		return setVisible(false);
	}
}
