package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.text.TextFont;

/**
 * A single tab header that renders the <b>real</b> Wurm tab shape — the 3-slice
 * {@code panelTexture} skin vanilla uses for chat tabs (raised when active/hovered, recessed
 * otherwise) — without the chat coupling of the package-private {@code TabButton}
 * ({@code TabButton}'s only obstacle is its {@code TabButton(AbstractTab, label)} ctor). Extends
 * {@link WButton}, so it wires into a {@link ButtonListener} like any button; selection is a plain
 * flag, decoupled from the transient pressed state.
 *
 * <p>Used by {@link KitTabPanel}; not usually constructed directly. Lives in the gui package for
 * package-private access to {@code WButton}'s skin fields and {@code WurmComponent}'s draw helpers.
 */
public final class KitTab extends WButton {

	private boolean selected;

	public KitTab(String label, ButtonListener listener) {
		super(label, listener);
		text = TextFont.getFixedSizeText();
		sizeToLabel();
	}

	@Override
	void setLabel(String label) {
		this.label = label;
		sizeToLabel();
	}

	private void sizeToLabel() {
		setSize(text.getWidth(label) + 8 + 13, text.getHeight() < 16 ? 16 : text.getHeight() + 2);
	}

	void setSelected(boolean selected) {
		this.selected = selected;
	}

	boolean isSelected() {
		return selected;
	}

	@Override
	protected void renderComponent(Queue queue, float alpha) {
		// Raised (active) skin row when selected, hovered or pressed; recessed row otherwise. UVs mirror
		// the vanilla TabButton 3-slice (left cap 8px, stretched middle, right cap 16px) minus the
		// chat-only bottom-overflow connector.
		boolean raised = selected || isCloseHovered || isDown;
		int yo = (raised ? 0 : 16) + 48;
		drawTexture(queue, panelTexture, r, g, b, 1.0F, x, y, 8, height, 64, 16 + yo, 8, 16);
		if (width > 16) {
			drawTexture(queue, panelTexture, r, g, b, 1.0F, x + 8, y, width - 16 - 8, height, 72, 16 + yo, 8, 16);
		}
		drawTexture(queue, panelTexture, r, g, b, 1.0F, x + width - 16, y, 16, height, 88, 16 + yo, 16, 16);

		int textY = isDown ? 0 : 1;
		text.moveTo(x + 4, y + text.getHeight() + textY);
		// Vanilla TabButton.setNormalColor: selected/raised tab = white text, recessed = black (legible
		// on the light panel skin). Keyed on selection only, not hover — matching vanilla.
		float tc = selected ? 1.0F : 0.0F;
		text.paint(queue, label, tc, tc, tc, 1.0F);
	}
}
