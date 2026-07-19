# `KitItemList`

`com.wurmonline.client.renderer.gui.KitItemList` — an **embeddable, fully-functional item-container
tree**: icons, QL/damage/weight columns, folded stacks, live add/remove/update, native drag-to-move, and
the real right-click item action menu (Examine / Take / …). You drop it into any window region next to
other widgets.

`final` class, gui package.

## Why this exists (and why it's small)

The key discovery: **the vanilla item list is not a window.** `InventoryListComponent` is a public,
embeddable `FlexComponent` (a `WurmDecorator`) with a public constructor — the same node type as a button
or a panel. Vanilla's Inventory / container windows are "items-only" purely because *that's all they put
in the layout*, not because the item list is special. So there is nothing to reimplement: `KitItemList`
is a thin, package-safe front over `new InventoryListComponent(view, …)` plus the two view lookups you
need. All the real behavior — icons, columns, folded stacks, drag-move (`sendMoveSomeItems`), the
right-click action menu, and live `InventoryMetaListener` updates — comes from the vanilla component and
its rows, none of which depend on being inside the vanilla inventory window.

> Contrast with [`KitTreeItem`](KitTreeItem.md): that is a **display-only** row for showing arbitrary
> tabular data (e.g. auction snapshots) in a `WurmTreeList` — no live item, no drag, no action menu.
> Use `KitItemList` when you want a region backed by **real inventory items**; use `KitTreeItem` when you
> want a custom data table.

## Getting a view

The tree is driven by an `InventoryMetaWindowView`. Two static helpers get one:

```java
KitItemList.playerInventory();     // the player's own inventory (window id -1) — always available in-world
KitItemList.container(windowId);   // a container window the SERVER has opened, or null if not open
```

`container(long)` guards the cast: an id the server hasn't opened resolves to a non-view placeholder, so
it returns `null` rather than risking a bad cast — **always null-check it**. Opening the container (the
server packet that registers the window) is *not* this SDK's job; `KitItemList` only renders whatever
view it's given and stays live through that view's item listener.

## Usage

```java
KitItemList items = new KitItemList(KitItemList.playerInventory());
window.set(items.component(), KitWindow.CENTER);   // mix with other regions
// … on window close:
items.destroy();                                    // unregister the item listener
```

Constructor options:

```java
new KitItemList(view);                                 // matches a real item window (see below)
new KitItemList(view, showPrice, canShowImp, preload); // full control
```

- `showPrice` — add a price column (as the vanilla **trade** window does; item/container windows don't).
- `canShowImp` — make the improvement (imp) column **available**. This does *not* force the column on;
  it adds the **"Toggle imp column"** right-click option, and the initial visibility follows the client's
  imp-column option (`Options.impColumn`). Vanilla passes `true` for every real item window
  (`InventoryWindow`, `EquipmentWindow`, server-opened `ItemListWindow`) — so the single-arg constructor
  defaults it to `true`, giving you the same right-click menu as the real inventory. Pass `false` and the
  tree's right-click menu has only "Expand all" / "Collapse all".
- `preload` — lazily request the inventory from the server on first render (the vanilla "Loading.."
  behaviour). Leave **false** for a view the server already populates (a server-opened container, or an
  already-loaded inventory). Vanilla's main inventory uses `true`; a server-opened container uses `false`.

**The single-arg default is `(showPrice=false, canShowImp=true, preload=false)`** — a server-opened
container that behaves like the real inventory's list (including the "Toggle imp column" menu entry).

## API

| Member | Purpose |
|--------|---------|
| `KitItemList(InventoryMetaWindowView)` | Default columns. |
| `KitItemList(view, boolean showPrice, boolean canShowImp, boolean preload)` | Full options. |
| `FlexComponent component()` | The node to add to a window region (it *is* a `FlexComponent`). |
| `void destroy()` | Unregister the item listener. Call when the hosting window closes/discards. |
| `static InventoryMetaWindowView playerInventory()` | The player inventory view. |
| `static InventoryMetaWindowView container(long windowId)` | A server-opened container view, or `null`. |

## Lifecycle

`destroy()` calls the vanilla component's `destroy()`, which removes it from the view's listener list.
Skipping it leaks one listener per created list (harmless for a single persistent window, but call it
if you build/discard item windows repeatedly).

## Limitations (from the vanilla component)

- **It roots at the view's root item.** There is no built-in way to point it at *one sub-container* of
  the player inventory — a carried bag's contents live under the single player-inventory view as
  descendant nodes. Scoping to one sub-container needs a custom `InventoryMetaWindowView` wrapper whose
  `getRootItem()` returns the container item (new code, test against live reparenting). For a **whole**
  inventory or a **server-opened container window** (which has its own view), no wrapper is needed.
- Multiple `KitItemList`s over the same view are fine (the view supports multiple listeners) — e.g. a
  second live copy of the player inventory in your own window alongside the vanilla one.

## Verification

Compile-verified against the real client + modloader jars via the `demo` build (the demo's **Live
items** tab embeds a `KitItemList` over the player inventory beside a widget). Vanilla surface confirmed
in `Client_vf`: `InventoryListComponent` public ctor `(InventoryMetaWindowView, boolean, boolean,
boolean)` + package-private `destroy()`; `InventoryMetaWindowManager.getPlayerInventory()` →
`InventoryMetaWindowView`, `getWindow(long)` → `InventoryMetaWindowControl` (guarded cast).

## Source

`src/main/java/com/wurmonline/client/renderer/gui/KitItemList.java`

## See also

- [`KitWindow`](KitWindow.md) — the mixable window to host it in.
- [`KitTreeItem`](KitTreeItem.md) — display-only tree rows for custom data (not live items).
