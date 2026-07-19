# `Collapsible`

`com.wurmonline.client.renderer.gui.Collapsible` — a **position-stable show/hide wrapper** for a single
component. Wurm's widgets have no "visible" flag, and the naive "remove from parent / re-add" trick
scrambles layout order because `WurmArrayPanel` has no insert-at-index (re-showing appends the child at
the end). `Collapsible` keeps a **slot panel permanently in the parent** and toggles the child *inside
the slot*, so the widget's position is preserved and the surrounding layout collapses/expands around it
(the same mechanism [`ValueLineBox`](ValueLineBox.md) uses to hide itself).

`final`; lives in the gui package for the package-private `WurmArrayPanel`.

## API

```java
new Collapsible(String name, FlexComponent child)   // starts visible

FlexComponent component()          // the slot — add THIS to the parent (it holds the position)
boolean isVisible()
Collapsible setVisible(boolean)    // toggle (no-op if unchanged)
Collapsible show()                 // = setVisible(true)
Collapsible hide()                 // = setVisible(false)
```

The setters are fluent.

## Usage

```java
package com.wurmonline.client.renderer.gui;

Collapsible advanced = new Collapsible("advanced", buildAdvancedPanel());
column.addComponent(advanced.component());   // slot holds its place in the column

advanced.hide();                 // child removed; the column collapses the gap
advanced.setVisible(showFlag);   // reappears in its ORIGINAL position, not at the end
```

## Notes & gotchas

- **Add `component()` (the slot) to the parent, not the child.** The slot is what stays in place; the
  child moves in and out of it.
- **This is the position-stable answer to hide/show.** Prefer it over removing a widget from its parent
  directly, which loses ordering when you add it back.
- **Hiding collapses the gap.** An empty slot shrink-wraps to nothing, so the parent closes the space —
  it's a true collapse, not just an invisible hole. Showing re-expands and reflows the parent.
- **The slot is a vertical array** holding one child; fine inside vertical or horizontal parents.
- **Single-threaded:** toggle on the client thread.

## Source

`src/main/java/com/wurmonline/client/renderer/gui/Collapsible.java`

## See also

- [`ValueLineBox`](ValueLineBox.md) — a fixed collapsing info panel (same underlying mechanism, specialized).
- [`GuiKit`](GuiKit.md) — `vbox`/`row`/`spacer` for the panels around it.
