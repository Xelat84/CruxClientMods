package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.resources.textures.Texture;

/**
 * Reusable {@code WurmTreeList} row: an implicit name+icon column plus arbitrary string columns,
 * single-select by row highlight (no per-cell checkbox). Generalises AuctionRow. Lives in this
 * package because {@link TreeListItem}'s abstract hooks are package-private.
 *
 * <p>The tree list's first column is the implicit name+icon column ({@link #getName()} +
 * {@link #getIcon()}); the {@code columns} array holds the EXTRA columns only, so
 * {@code getParameter(0)} is the first extra column.
 */
public abstract class KitTreeItem extends TreeListItem {

	private static final float BUTTON_R = 1.0f;
	private static final float BUTTON_G = 0.85f;
	private static final float BUTTON_B = 0.4f;

	protected final String name;
	protected final int imageNumber;
	protected final String[] columns;
	protected final boolean container;
	protected final double[] sortKeys;

	protected KitTreeItem(String name, int imageNumber, String[] columns) {
		this(name, imageNumber, columns, false, null);
	}

	/** @param container true to render an expand box and hold child rows (a category/group row). */
	protected KitTreeItem(String name, int imageNumber, String[] columns, boolean container) {
		this(name, imageNumber, columns, container, null);
	}

	/**
	 * @param sortKeys optional numeric sort values parallel to {@code columns} (extra-column index) —
	 *     a column sorts numerically when both compared rows supply a key; a row whose key is NaN (or
	 *     absent) for that column sorts after keyed rows, and NaN-vs-NaN falls back to lexical.
	 */
	protected KitTreeItem(String name, int imageNumber, String[] columns, boolean container, double[] sortKeys) {
		this.name = java.util.Objects.requireNonNull(name, "name");
		this.imageNumber = imageNumber;
		this.columns = columns == null ? new String[0] : columns;
		this.container = container;
		this.sortKeys = sortKeys;
	}

	@Override
	String getName() {
		return name;
	}

	@Override
	String getParameter(int col) {
		return col >= 0 && col < columns.length ? columns[col] : "";
	}

	/**
	 * Numeric sort key for extra column {@code col} (0-based), or {@code NaN} for "no numeric key" (that
	 * row then sorts after keyed rows; two NaN rows fall back to lexical). Override to sort on a raw
	 * backing value (a price in iron, a quality, a timestamp) instead of its formatted display string —
	 * string sort would order {@code "100"} before {@code "9"}. The default reads the {@code sortKeys}
	 * constructor array, if any.
	 */
	protected double sortKey(int col) {
		return sortKeys != null && col >= 0 && col < sortKeys.length ? sortKeys[col] : Double.NaN;
	}

	@Override
	boolean isContainer() {
		return container;
	}

	/**
	 * True if extra column {@code col} (0-based) should behave as a clickable button. Requires
	 * {@link com.wurmonline.clientkit.GuiPatches#installTreeCellButtons()} in {@code preInit} to route
	 * the click; the button's label is whatever {@link #getParameter(int)} returns for that column, and
	 * it is drawn in an accent colour. Override together with {@link #cellButtonClicked(int)}.
	 */
	protected boolean isCellButton(int col) {
		return false;
	}

	/** Invoked when a {@link #isCellButton(int)} column is clicked. Runs on the client (game) thread. */
	protected void cellButtonClicked(int col) {
	}

	@Override
	float getSecondaryR(int col) {
		return isCellButton(col) ? BUTTON_R : super.getSecondaryR(col);
	}

	@Override
	float getSecondaryG(int col) {
		return isCellButton(col) ? BUTTON_G : super.getSecondaryG(col);
	}

	@Override
	float getSecondaryB(int col) {
		return isCellButton(col) ? BUTTON_B : super.getSecondaryB(col);
	}

	@Override
	int compareTo(TreeListItem other, int col) {
		if (!(other instanceof KitTreeItem)) {
			return 0;
		}
		KitTreeItem o = (KitTreeItem) other;
		if (col < 0) {
			return name.compareToIgnoreCase(o.name);
		}
		double a = sortKey(col);
		double b = o.sortKey(col);
		boolean aNaN = Double.isNaN(a);
		boolean bNaN = Double.isNaN(b);
		if (!aNaN && !bNaN) {
			return Double.compare(a, b);
		}
		if (aNaN && bNaN) {
			return getParameter(col).compareToIgnoreCase(o.getParameter(col));
		}
		return aNaN ? 1 : -1;
	}

	@Override
	public Texture getIcon() {
		return GuiKit.icon(imageNumber);
	}

	@Override
	public boolean hasCheckbox(int col) {
		return false;
	}
}
