package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.resources.textures.IconLoader;
import com.wurmonline.client.resources.textures.Texture;

/**
 * Factory helpers for the game's package-private GUI widgets. Lives in this package so it can
 * construct widgets whose constructors and fields are package-private.
 */
public final class GuiKit {

	private GuiKit() {
	}

	/** Single-line input field with the default "] " prompt cleared. */
	public static WurmInputField input(String name, InputFieldListener listener) {
		WurmInputField field = new WurmInputField(name, listener);
		field.prompt = "";
		return field;
	}

	/** Item icon by image number, or null (also on any load failure). */
	public static Texture icon(int imageNumber) {
		try {
			return imageNumber > 0 ? IconLoader.getIcon((short) imageNumber) : null;
		} catch (Exception e) {
			return null;
		}
	}

	/** Wrap content so it centers (rather than stretches) inside a stretching border-panel slot. */
	public static WurmDecorator centered(String name, FlexComponent content) {
		WurmDecorator decorator = new WurmDecorator(name, content);
		decorator.align = WurmDecorator.ALIGN_CENTER;
		return decorator;
	}

	/** Horizontal shrink-wrap panel. */
	public static WurmArrayPanel<FlexComponent> hbox(String name) {
		return new WurmArrayPanel<>(name, WurmArrayPanel.DIR_HORIZONTAL);
	}

	/** Vertical shrink-wrap panel. */
	public static WurmArrayPanel<FlexComponent> vbox(String name) {
		return new WurmArrayPanel<>(name, WurmArrayPanel.DIR_VERTICAL);
	}

	/** Horizontal shrink-wrap panel with {@code gap} pixels of spacing between children. */
	public static WurmArrayPanel<FlexComponent> row(String name, int gap) {
		WurmArrayPanel<FlexComponent> panel = new WurmArrayPanel<>(name, WurmArrayPanel.DIR_HORIZONTAL);
		panel.componentWidthOffset = gap;
		return panel;
	}

	/**
	 * A fixed-size transparent spacer. Use it as a gap between components — {@code WurmArrayPanel} has
	 * no vertical inter-item spacing, so a {@code spacer(1, h)} is how you insert a vertical gap.
	 */
	public static WurmPanel spacer(int width, int height) {
		return new WurmPanel(width, height, false);
	}
}
