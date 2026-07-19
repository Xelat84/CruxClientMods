package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.resources.textures.IconLoader;
import com.wurmonline.client.resources.textures.Texture;

/**
 * One {@code WurmTreeList} row for the auction window. Lives in the game GUI package because
 * {@code TreeListItem} and its {@code getName()/getParameter(int)/compareTo} hooks are
 * package-private. A row is one of several {@link Kind}s - a grouped book header, an individual
 * listing under it, a "my order" line, or a vault entry - each filling the column cells
 * differently.
 *
 * <p>{@code WurmTreeList}'s first column is the implicit name+icon column rendered from
 * {@link #getName()} and {@link #getIcon()}; the constructor's column arrays are the EXTRA columns
 * only. So {@code getParameter(0)} is the first extra column, and {@link #hasCheckbox(int)} /
 * {@link #getChecked(int)} are likewise indexed into the extra columns. The icon draws the item
 * icon via {@code IconLoader.getIcon((short)templateId)}.
 */
public class AuctionRow extends KitTreeItem {

	private static final String[] NO_COLUMNS = new String[0];

	public enum Kind {
		LISTING, MY_SELL, MY_SELL_ORDER, MY_BUY, VAULT, ITEM
	}

	public final Kind kind;
	public final int templateId;
	public final String displayName;
	public int imageNumber;
	// Action for a per-row cell button (the MY_SELL "Remove" column). Null = no button on this row.
	public Runnable onRemove;
	// Per-row "Cancel" cell button for the My Orders trees (MY_SELL_ORDER / MY_BUY). Null = no button.
	public Runnable onCancel;

	// Listing / order / vault payload (only the relevant ones are set per kind).
	public long orderId;
	public long itemId;
	public double ql = -1;
	public byte rarity;
	public boolean enchanted;
	public boolean allowPartial;
	public boolean combinable;
	public long perUnitIron;
	public long remaining;
	public long heldIron = -1;
	public long maxPerIron = -1;
	public int minQl = -1;
	public float weight = -1f;
	public float templateWeight = -1f;
	public long timeLeftMs = -1L;
	public boolean bulkable;
	public String aux = "";
	public Object tag; // backing identity for a row (e.g. the source item id a MY_SELL stage row represents)

	private boolean checked;

	public AuctionRow(Kind kind, int templateId, String displayName) {
		super(displayName == null ? ("#" + templateId) : displayName, 0, NO_COLUMNS);
		this.kind = kind;
		this.templateId = templateId;
		this.displayName = displayName == null ? ("#" + templateId) : displayName;
	}

	@Override
	public Texture getIcon() {
		try {
			return imageNumber > 0 ? IconLoader.getIcon((short) imageNumber) : null;
		} catch (Exception e) {
			return null;
		}
	}

	String getName() {
		StringBuilder sb = new StringBuilder(displayName);
		if ((kind == Kind.LISTING || kind == Kind.VAULT) && remaining > 1) {
			sb.append("  (x").append(remaining).append(')');
		}
		if (rarity > 0) {
			sb.append(rarity == 1 ? "  (rare)" : rarity == 2 ? "  (supreme)" : "  (fantastic)");
		}
		return sb.toString();
	}

	String getParameter(int col) {
		switch (kind) {
			case LISTING:
				if (col == 0) {
					return ql >= 0 ? formatQl(ql) : "";
				}
				if (col == 1) {
					// The listing's total remaining weight (what it actually represents), not per-unit template weight.
					return weight >= 0 ? AuctionFormat.weight(weight) : "";
				}
				if (col == 2) {
					return AuctionFormat.coin(perUnitIron);
				}
				if (col == 3) {
					// Partial-buy state: Yes/No for combinable goods, blank for non-combinable (can't be split).
					return combinable ? (allowPartial ? "Yes" : "No") : "";
				}
				return "";
			case MY_SELL:
				// name(icon) | QL(0) | WEIGHT(1) | QTY(2) | REMOVE(3, cell button)
				if (col == 0) {
					return ql >= 0 ? formatQl(ql) : "";
				}
				if (col == 1) {
					return weight >= 0 ? AuctionFormat.weight(weight) : "";
				}
				if (col == 2) {
					return Long.toString(remaining);
				}
				if (col == 3) {
					return onRemove != null ? "Remove" : "";
				}
				return "";
			case MY_SELL_ORDER:
				// name(icon) | QL(0) | REMAINING(1) | PRICE/UNIT(2) | TIME LEFT(3) | STORAGE HELD(4) | CANCEL(5, cell button)
				if (col == 0) {
					return ql >= 0 ? formatQl(ql) : "";
				}
				if (col == 1) {
					return Long.toString(remaining);
				}
				if (col == 2) {
					return AuctionFormat.coin(perUnitIron);
				}
				if (col == 3) {
					return timeLeftMs >= 0 ? AuctionFormat.duration(timeLeftMs) : "";
				}
				if (col == 4) {
					return heldIron > 0 ? AuctionFormat.coin(heldIron) : "-";
				}
				if (col == 5) {
					return onCancel != null ? "Cancel" : "";
				}
				return "";
			case MY_BUY:
				// name(icon) | MIN QL(0) | REMAINING(1) | MAX PRICE(2) | COIN HELD(3) | CANCEL(4, cell button)
				if (col == 0) {
					return minQl >= 0 ? Integer.toString(minQl) : "";
				}
				if (col == 1) {
					return Long.toString(remaining);
				}
				if (col == 2) {
					return AuctionFormat.coin(maxPerIron);
				}
				if (col == 3) {
					return heldIron >= 0 ? AuctionFormat.coin(heldIron) : "";
				}
				if (col == 4) {
					return onCancel != null ? "Cancel" : "";
				}
				return "";
			case ITEM:
				// name(icon) | CATEGORY(0)
				return col == 0 ? aux : "";
			case VAULT:
				// name(icon) | QL(0) | WEIGHT(1) | AMOUNT(2) | TIME LEFT(3)
				if (col == 0) {
					return ql >= 0 ? formatQl(ql) : "";
				}
				if (col == 1) {
					return weight >= 0 ? AuctionFormat.weight(weight) : "";
				}
				if (col == 2) {
					return Long.toString(remaining);
				}
				if (col == 3) {
					return timeLeftMs >= 0 ? AuctionFormat.duration(timeLeftMs) : "";
				}
				return "";
			default:
				return "";
		}
	}

	/** QL always with two decimals ("99.00"), matching the client's inventory windows - never a bare integer. */
	private static String formatQl(double ql) {
		return String.format("%.2f", ql);
	}

	int compareTo(TreeListItem other, int sortCol) {
		if (!(other instanceof AuctionRow)) {
			return 0;
		}
		AuctionRow o = (AuctionRow) other;
		int byName = displayName.compareToIgnoreCase(o.displayName);
		switch (kind) {
			case ITEM: // CATEGORY(0)
				return sortCol == 0 ? aux.compareToIgnoreCase(o.aux) : byName;
			case LISTING: // QL(0) | WEIGHT(1) | PRICE/UNIT(2) | PARTIAL(3)
				if (sortCol == 0) return Double.compare(ql, o.ql);
				if (sortCol == 1) return Float.compare(weight, o.weight);
				if (sortCol == 2) return Long.compare(perUnitIron, o.perUnitIron);
				if (sortCol == 3) return Boolean.compare(combinable && allowPartial, o.combinable && o.allowPartial);
				return byName;
			case VAULT: // QL(0) | WEIGHT(1) | AMOUNT(2) | TIME LEFT(3)
				if (sortCol == 0) return Double.compare(ql, o.ql);
				if (sortCol == 1) return Float.compare(weight, o.weight);
				if (sortCol == 2) return Long.compare(remaining, o.remaining);
				if (sortCol == 3) return Long.compare(timeLeftMs, o.timeLeftMs);
				return byName;
			case MY_SELL_ORDER: // QL(0) | REMAINING(1) | PRICE/UNIT(2) | TIME LEFT(3) | STORAGE HELD(4)
				if (sortCol == 0) return Double.compare(ql, o.ql);
				if (sortCol == 1) return Long.compare(remaining, o.remaining);
				if (sortCol == 2) return Long.compare(perUnitIron, o.perUnitIron);
				if (sortCol == 3) return Long.compare(timeLeftMs, o.timeLeftMs);
				if (sortCol == 4) return Long.compare(heldIron, o.heldIron);
				return byName;
			case MY_BUY: // MIN QL(0) | REMAINING(1) | MAX PRICE(2) | COIN HELD(3)
				if (sortCol == 0) return Integer.compare(minQl, o.minQl);
				if (sortCol == 1) return Long.compare(remaining, o.remaining);
				if (sortCol == 2) return Long.compare(maxPerIron, o.maxPerIron);
				if (sortCol == 3) return Long.compare(heldIron, o.heldIron);
				return byName;
			case MY_SELL: // QL(0) | WEIGHT(1) | QTY(2)
				if (sortCol == 0) return Double.compare(ql, o.ql);
				if (sortCol == 1) return Float.compare(weight, o.weight);
				if (sortCol == 2) return Long.compare(remaining, o.remaining);
				return byName;
			default:
				return byName;
		}
	}

	// Real per-row cell buttons (KitTreeItem + GuiPatches.installTreeCellButtons): the MY_SELL "Remove" column
	// (extra col 3) clears the staged listing; the My Orders "Cancel" column (MY_SELL_ORDER col 5, MY_BUY col 4)
	// cancels that order. All other columns are plain text / row-select.
	@Override
	protected boolean isCellButton(int col) {
		return (kind == Kind.MY_SELL && col == 3 && onRemove != null)
			|| (kind == Kind.MY_SELL_ORDER && col == 5 && onCancel != null)
			|| (kind == Kind.MY_BUY && col == 4 && onCancel != null);
	}

	@Override
	protected void cellButtonClicked(int col) {
		if (kind == Kind.MY_SELL && col == 3 && onRemove != null) {
			onRemove.run();
		} else if ((kind == Kind.MY_SELL_ORDER && col == 5 || kind == Kind.MY_BUY && col == 4) && onCancel != null) {
			onCancel.run();
		}
	}

	// Selection is by row highlight (WurmTreeList.getSelections), as in InventoryListComponent - these
	// data columns must render their parameter text, so no per-column checkbox here.
	@Override
	public boolean hasCheckbox(int col) {
		return false;
	}

	@Override
	public boolean getChecked(int col) {
		return checked;
	}

	@Override
	public void setChecked(int col, boolean value) {
		checked = value;
	}
}
