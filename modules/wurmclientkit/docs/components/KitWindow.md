# `KitWindow`

`com.wurmonline.client.renderer.gui.KitWindow` — a titled `WWindow` with a **mixable content area**.
Vanilla item windows (Inventory, containers) are items-only; a `KitWindow` lets you place an
item-container tree ([`KitItemList`](KitItemList.md)) in one region and arbitrary widgets — buttons,
inputs, dropdowns, tabs, labels — in the others.

`class` extending `WWindow`, gui package.

## Layout

Content is laid out in an inner 5-region border panel, exposed as constants on the class:

```
        ┌──────────────── title bar (WWindow chrome) ────────────────┐
        │                        NORTH                               │
        │  WEST   ┌──────────────  CENTER  ──────────────┐    EAST    │
        │         └───────────────────────────────────────┘          │
        │                        SOUTH                               │
        └────────────────────────────────────────────────────────────┘
```

The inner panel is set as the window's single content, so the window's own title-bar / close-box chrome
is never disturbed (you don't fight the `WWindow` N/S/E/W chrome slots).

## Usage

```java
KitWindow w = new KitWindow("Vault");
w.set(new KitItemList(KitItemList.container(windowId)).component(), KitWindow.CENTER);
w.set(sidebar,  KitWindow.WEST);
w.set(buttonRow, KitWindow.SOUTH);
w.setInitialSize(600, 400, false, 0.5F, 0.5F);   // inherited from WWindow
w.show();
```

`set(component, region)` returns `this`, so calls chain. `show()` / `closePressed()` are wired to
`hud.toggleComponent(this)` (same pattern as [`KitTabbedWindow`](KitTabbedWindow.md)).

This SDK only makes such a window **showable**. The server packet that opens it, and feeding a
container's items, is the mod's job — see [`KitItemList.container(long)`](KitItemList.md#getting-a-view).

## API

| Member | Purpose |
|--------|---------|
| `KitWindow(String title)` | Create a closeable, titled window with an empty content area. |
| `KitWindow set(FlexComponent, int region)` | Place a component in a region; chainable. |
| `NORTH` / `EAST` / `SOUTH` / `WEST` / `CENTER` | Region constants (mirror `WurmBorderPanel`). |
| `void show()` | Show if not already shown. |
| `protected void closePressed()` | Hide on the title-bar close box (overridden from `WWindow`). |

Plus everything inherited from `WWindow` (`setInitialSize`, `setTitle`, drag, remember-open, …).

## When to use which

- **`KitWindow`** — a window whose whole content is a fixed multi-region layout (item list + side/bottom
  widgets). The mixable-content case this was built for.
- **[`KitTabbedWindow`](KitTabbedWindow.md)** — a window whose content is switched between tabs.
- **[`KitTabPanel`](KitTabPanel.md)** — tabs inside *part* of a window; drop it into a `KitWindow` region
  if you want both.

## Verification

Compile-verified against the real client + modloader jars via the SDK/demo build. Vanilla surface
confirmed in `Client_vf`: `WWindow(String)`, package-private `setComponent(FlexComponent)` /
`setComponent(FlexComponent, int)`, `closeable`, `closePressed()`; `WurmBorderPanel(String)` +
region constants `NORTH..CENTER`.

## Source

`src/main/java/com/wurmonline/client/renderer/gui/KitWindow.java`

## See also

- [`KitItemList`](KitItemList.md) — the item tree to place in a region.
- [`KitTabbedWindow`](KitTabbedWindow.md) / [`KitTabPanel`](KitTabPanel.md) — tabbed alternatives.
