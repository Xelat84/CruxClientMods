package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.renderer.PickData;
import com.wurmonline.client.renderer.backend.Queue;

/**
 * A one-line text label rendered in a settable colour. The game's {@link WurmLabel} hard-codes white text, so this
 * exists for coloured status/warning lines (e.g. a red "required" hint). Lives in
 * {@code com.wurmonline.client.renderer.gui} for package-private access to {@link FlexComponent} and its text font.
 */
public final class KitColorLabel extends FlexComponent {

	private String label;
	private float tr = 1.0f, tg = 1.0f, tb = 1.0f;

	public KitColorLabel(String label) {
		super("ColorLabel " + label);
		this.label = label == null ? "" : label;
		setSize(text.getWidth(this.label) + 8, text.getHeight() + 1);
	}

	public void setLabel(String value) {
		label = value == null ? "" : value;
		// Resize to the new text - otherwise the width stays frozen at the construction string and a longer message
		// (e.g. the full "sells only in whole pieces..." warning) is clipped.
		setSize(text.getWidth(label) + 8, text.getHeight() + 1);
	}

	public void setColor(float r, float g, float b) {
		tr = r;
		tg = g;
		tb = b;
	}

	@Override
	protected void renderComponent(Queue queue, float alpha) {
		text.moveTo(x + 4, y + height);
		text.paint(queue, label, tr, tg, tb, 1.0F);
	}

	@Override
	public void pick(PickData pickData, int xMouse, int yMouse) {
	}
}
