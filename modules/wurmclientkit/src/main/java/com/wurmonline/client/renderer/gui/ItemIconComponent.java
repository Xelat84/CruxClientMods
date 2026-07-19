package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.renderer.PickData;
import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.resources.textures.IconLoader;
import com.wurmonline.client.resources.textures.Texture;

/**
 * An inline item icon rendered from an item's {@code imageNumber} via {@link IconLoader}, with an optional name drawn
 * to its right. Lets a form row show {@code [icon] name} the way a tree cell shows an item icon, outside a
 * {@code WurmTreeList}.
 *
 * <p>The icon and name are drawn by THIS one component so their vertical positions are controlled together and share a
 * single centre line - composing them from a separate icon widget + {@code WurmLabel} + button never aligns, because
 * each of those anchors its content differently ({@code WurmLabel} bottom-anchors its text at its own small height,
 * {@code WButton} centre-anchors and adds padding, a fixed icon pins to the row top). The component sizes itself
 * exactly to {@code icon (+ gap + text)} and is fixed-size so a parent row can't stretch and re-anchor it.
 *
 * <p>Lives in {@code com.wurmonline.client.renderer.gui} for package-private access to {@link FlexComponent}, its
 * {@code drawTexture} hook and the {@code text} font.
 */
public final class ItemIconComponent extends FlexComponent {

	private static final int GAP = 5;

	private final int iconSize;
	private Texture texture;
	private String hover = "";
	private String label = "";

	public ItemIconComponent(int size) {
		super("ItemIcon");
		iconSize = size;
		sizeFlags = 3; // fixed w+h: we size ourselves exactly to icon(+name); a parent row must not stretch/re-anchor us
		resize();
	}

	// Write the fields directly (as the game's own WurmImage does) so the requested size always takes - the inherited
	// setLocation-based setSize is a no-op once FIXED_WIDTH/HEIGHT is set.
	@Override
	void setSize(int newWidth, int newHeight) {
		width = newWidth;
		height = newHeight;
		componentResized();
	}

	private void resize() {
		int w = iconSize;
		if (!label.isEmpty()) {
			w += GAP + text.getWidth(label);
		}
		setSize(w, Math.max(iconSize, text.getHeight()));
	}

	/** Set (or clear, with &lt;= 0) the icon shown. */
	public void setImageNumber(int imageNumber) {
		try {
			texture = imageNumber > 0 ? IconLoader.getIcon((short) imageNumber) : null;
		} catch (Exception e) {
			texture = null;
		}
	}

	public void setHover(String hoverText) {
		hover = hoverText == null ? "" : hoverText;
	}

	/** Optional name drawn to the right of the icon, vertically centred on the same line as the icon. */
	public void setLabel(String name) {
		label = name == null ? "" : name;
		resize();
	}

	@Override
	protected void renderComponent(Queue queue, float alpha) {
		if (texture != null) {
			// Match exactly how WurmTreeList draws an item icon (WurmTreeList.java:810): white tint, source rect
			// (0,0,256,256). The source rect is a FIXED 256-unit UV space meaning "the whole texture" regardless of
			// the icon's real pixel size - using the texture's real 32px dims samples only 1/8 of it (garbage).
			int iy = y + (height - iconSize) / 2;
			drawTexture(queue, texture, 1.0F, 1.0F, 1.0F, 1.0F, x, iy, iconSize, iconSize, 0, 0, 256, 256);
		}
		if (!label.isEmpty()) {
			// Baseline = vertical centre + half the text height (text.moveTo positions the baseline, as WurmLabel does).
			int baseline = y + (height + text.getHeight()) / 2;
			text.moveTo(x + iconSize + GAP, baseline);
			text.paint(queue, label, 1.0F, 1.0F, 1.0F, 1.0F);
		}
	}

	@Override
	public void pick(PickData pickData, int xMouse, int yMouse) {
		if (!hover.isEmpty()) {
			pickData.addText(hover);
		}
	}

	@Override
	boolean isCenterable() {
		return true;
	}
}
