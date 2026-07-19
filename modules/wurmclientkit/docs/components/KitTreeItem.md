# `KitTreeItem`

`com.wurmonline.client.renderer.gui.KitTreeItem` — a ready base for `WurmTreeList` rows: an implicit
name+icon column plus arbitrary string columns, single-select by row highlight (no per-cell
checkbox). Generalizes AuctionHouse's `AuctionRow`. Lives in the gui package because `TreeListItem`'s
abstract hooks are package-private.

`abstract`, extends `TreeListItem`.

## The column-0 rule (read this first)

`WurmTreeList`'s **column 0 is the implicit name+icon column**, rendered from each row's `getName()`
and `getIcon()`. The `String[]` columns you pass are the **EXTRA** columns only — so `getParameter(0)`
is the *first extra* column, i.e. tree column 1.

Corollary for the tree's constructor: **do not** put a "Name" entry in the header/width arrays you
pass `WurmTreeList` — column 0 is implicit, and adding it makes you off-by-one on every subsequent
column.

## Constructors

```java
protected KitTreeItem(String name, int imageNumber, String[] columns)
protected KitTreeItem(String name, int imageNumber, String[] columns, boolean container)
protected KitTreeItem(String name, int imageNumber, String[] columns, boolean container, double[] sortKeys)
```

- `name` — the name column (tree column 0), also used for name-sort.
- `imageNumber` — item **image number** for the icon (not template id — see
  [`GuiKit.icon`](GuiKit.md)); `≤ 0` or a load failure yields no icon.
- `columns` — the extra column strings; `null` is treated as empty.
- `container` — `true` renders an expand box so the row can hold children (a category/group row). The
  tree still owns the child hierarchy (add children with `tree.addTreeListItem(child, thisItem)`); the
  flag alone is safe with no children.
- `sortKeys` — optional numeric sort values parallel to `columns` (extra-column index). A column with a
  non-NaN key sorts numerically; NaN/missing sorts lexically. For anything beyond a fixed array,
  override `sortKey(int)`.

Subclass it to add your own data/behavior:

```java
package com.wurmonline.client.renderer.gui;

public class ItemRow extends KitTreeItem {
    final long itemId;

    ItemRow(long itemId, String name, int imageNumber, long priceIron, float ql) {
        super(name, imageNumber, new String[] {
            Format.coin(priceIron),   // extra column 0 → tree column 1
            Format.ql(ql),            // extra column 1 → tree column 2
        });
        this.itemId = itemId;
    }
}
```

## What the base implements

| Override | Behavior |
|----------|----------|
| `getName()` | returns `name` |
| `getParameter(int col)` | `columns[col]` (bounds-safe → `""`) |
| `getIcon()` | `GuiKit.icon(imageNumber)` |
| `isContainer()` | the `container` flag (expand box) |
| `sortKey(int col)` | numeric key from `sortKeys[col]`, else `NaN` (override for raw-value sort) |
| `compareTo(other, col)` | see below |
| `hasCheckbox(int col)` | `false` — rows are row-select, not per-cell checkbox |

### Sorting

`compareTo` is what the tree calls when a column header is clicked. **Column-index convention** (from
`WurmTreeList`): the tree passes `-2` for the name column, `-3` for the imp column, and the **0-based
extra-column index** for the data columns — the same index space as `getParameter`. So:

- `col < 0` → sort by `name`.
- `col ≥ 0` → if both rows supply a numeric `sortKey(col)`, compare numerically (`Double.compare`);
  otherwise compare `getParameter(col)` case-insensitively.

**Numeric sort keys** exist because a formatted string sorts textually — `"100"` before `"9"`, or a
coin string `"1s 80c"` nonsensically. Supply the raw backing value (price in iron, quality, timestamp)
via the `sortKeys` ctor arg or by overriding `sortKey(int)` — exactly how `AuctionRow` sorts on `ql` /
`perUnitIron` rather than their display text.

The mixed case is a **total order**: numeric-keyed rows sort before non-keyed (NaN) rows, numerics by
value, the NaN group by lexical — so a column with only some keys can't violate `List.sort`'s
comparator contract.

> Fixed in this version: the previous `compareTo` used `col <= 0`, which wrongly treated the **first
> extra column** as a name-sort (off-by-one against the tree's `-2`/`0`-based index scheme). It now
> uses `col < 0` for the name/imp columns.

## Wiring into a tree

```java
// widths/names describe the EXTRA columns only, 0-based — NOT the name column.
int[]    widths = { 90, 60 };            // extra col 0 (Price), extra col 1 (QL)
String[] names  = { "Price", "QL" };     // headers for the extra columns
WurmTreeList<ItemRow> tree = new WurmTreeList<>("items", widths, names);

tree.addTreeListItem(new ItemRow(id, "Iron lump", image, 1800, 92f), null);  // null parent = root row
tree.recalcLines();                      // after mutating rows
```

> **The name column is separate and automatic** (verified in `WurmTreeList`'s constructor): it's a
> hardcoded **"Name"** header that fills the remaining width and renders each row's `getName()`/
> `getIcon()`. The `widths`/`names` arrays you pass are the **extra columns only** and use the same
> 0-based index as `getParameter`/`sortKey`/`isCellButton`. So a row with `columns = {a, b}` needs
> exactly two entries in each array. (Earlier docs wrongly said these arrays included column 0 — they
> do not.)

Place the tree in a border-panel CENTER so it fills:
`panel.setComponent(tree, WurmBorderPanel.CENTER)`.

## Cell buttons

A cell can act as a **clickable button**. Override two hooks and return the button's label from
`getParameter` for that column:

```java
public class ActionRow extends KitTreeItem {
    ActionRow(long id, String name, int image) {
        super(name, image, new String[] { "Buy" });   // the "Buy" column IS the button label
        this.id = id;
    }
    @Override protected boolean isCellButton(int col) { return col == 0; }   // extra column 0 = the button
    @Override protected void cellButtonClicked(int col) { sendBuy(id); }
}
```

The cell renders as a **real button** — the vanilla `WButton` 3-slice skin drawn behind the label —
courtesy of [`GuiPatches.installTreeCellButtons()`](GuiPatches.md#installtreecellbuttons), which you must
call **once in `preInit()`**. That installer wires both halves: the click (an `insertBefore` on
`leftPressed`) and the button appearance (an `insertAfter` on `renderComponent`). Clicking the column's
slot fires `cellButtonClicked(col)` and does **not** select/drag the row. Without the installer the
column falls back to plain text (still accent-coloured via the overridden `getSecondaryR/G/B`) and isn't
clickable.

- `col` is the 0-based extra-column index — the same index space as `getParameter`, `sortKey`, and
  `getSecondaryR/G/B`.
- **Fires once on press**, not on release; a double-click fires the action once (not twice) and never
  selects the row. A throwing `cellButtonClicked` is caught and logged, not propagated.
- The click rect is the **whole column slot**, so it covers the label wherever it's drawn within the
  column (right-aligned numerics, left-offset text).
- Buttons and checkboxes shouldn't share a column (both key off the same click) — pick one per column.

## Notes & gotchas

- **Subclass lives in the gui package** — it extends `TreeListItem`.
- **Icon = image number**, sent by the server; not the template id.
- **Numeric columns** — pass `sortKeys` (or override `sortKey(int)`) so the header sorts on the raw
  value, not the formatted string. Column-sort index is 0-based over extra columns; the name column is
  the negative sentinel handled for you.
- **Bulk item counts** ride in the item's description string, not a dedicated field — if you render
  bulk rows, parse the count out of the description (workspace `ClientToolkitReference.md` §3/§4).
- Call `tree.recalcLines()` after adding/removing/mutating rows or the display won't refresh.

## Source

`src/main/java/com/wurmonline/client/renderer/gui/KitTreeItem.java`

## See also

- [`GuiKit`](GuiKit.md) — the `icon` helper this uses.
- [`Format`](Format.md) — rendering the string columns.
- [`KitTabbedWindow`](KitTabbedWindow.md) — hosting the tree in a tab.
