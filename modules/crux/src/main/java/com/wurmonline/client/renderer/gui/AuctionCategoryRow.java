package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.resources.textures.Texture;

/**
 * One row of the Browse-tab category filter tree. Parent rows ("Resources", and the "All
 * categories" master) are expandable containers; leaf rows are individual categories. The category
 * filter is chosen by row highlight (WurmTreeList.getSelections), not a per-row checkbox, so the
 * tree is a plain name-only expandable list. The implicit name column shows the category label.
 */
public class AuctionCategoryRow extends TreeListItem {

	public final int categoryId;
	private final String label;
	private final boolean container;

	public AuctionCategoryRow(int categoryId, String label, boolean container) {
		this.categoryId = categoryId;
		this.label = label;
		this.container = container;
	}

	@Override
	String getName() {
		return label;
	}

	@Override
	String getParameter(int col) {
		return "";
	}

	@Override
	public Texture getIcon() {
		return null;
	}

	@Override
	boolean isContainer() {
		return container;
	}

	@Override
	public boolean hasCheckbox(int col) {
		return false;
	}

	@Override
	int compareTo(TreeListItem other, int sortCol) {
		return 0;
	}
}
