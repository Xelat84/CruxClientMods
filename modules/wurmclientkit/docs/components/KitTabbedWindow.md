# `KitTabbedWindow`

`com.wurmonline.client.renderer.gui.KitTabbedWindow` — a `WWindow` that **keeps its title bar** and
shows a strip of **real tabs** beneath it (the vanilla tab skin, via [`KitTab`](KitTabPanel.md#kittab-the-tab-header)).
Vanilla's `WurmTabbedWindow`/`WurmTabPanel` are package-private *and* chat-coupled, so they aren't
reusable; this composes a [`KitTabPanel`](KitTabPanel.md) as the window's content. For tabs inside only
*part* of a window (beside a sidebar, etc.), use `KitTabPanel` directly in a slot.

Extends `WWindow`. Lives in the gui package for package-private access to `WWindow`.

## Structure

```
WWindow (this)
 ├─ title bar        (normal WWindow chrome — kept)
 └─ content = KitTabPanel
      ├─ NORTH:  tab strip of KitTab headers (real tab skin: raised active, recessed inactive)
      └─ CENTER: the active tab's panel, swapped on click
```

So the on-screen layout is **[title bar] / [tab strip] / [content]**.

## API

```java
public KitTabbedWindow(String title)
public KitTabbedWindow addTab(String label, FlexComponent panel)   // first added shown by default
public void            focusTab(int index)                          // switch programmatically
public KitTabbedWindow onTabChange(IntConsumer callback)            // fired with new index on change
public int             selected()                                   // active index, or -1
public void            show()
protected void         closePressed()                               // X hides the window (wired)
```

`addTab`/`onTabChange` are fluent.

## Usage

```java
package com.wurmonline.client.renderer.gui;   // gui package — required

public class MyWindow extends KitTabbedWindow {
    public MyWindow() {
        super("My Window");                          // title bar text
        addTab("Browse",   buildBrowsePanel());      // first tab, shown by default
        addTab("Settings", buildSettingsPanel());
        onTabChange(i -> { /* lazy-load / react */ });
        setInitialSize(600, 400, false, 0.5F, 0.5F);
    }

    private FlexComponent buildBrowsePanel() {
        WurmBorderPanel p = new WurmBorderPanel("browse");
        p.setComponent(myTreeList, WurmBorderPanel.CENTER);   // tree fills the tab
        return p;
    }
}
```

```java
myWindow.show();
myWindow.focusTab(1);     // open directly on the Settings tab
```

## Notes & gotchas

- **The title bar is kept** — it's the normal `WWindow` top bar; the tab strip renders below it. Set the
  title via the ctor (or `setTitle`).
- **Real tabs, not buttons** — headers use the vanilla `panelTexture` tab skin (`KitTab`): the active tab
  is raised, others recessed, hovering previews raised. No gold-text-button styling.
- **Your subclass must be in `com.wurmonline.client.renderer.gui`** — it extends `WWindow`. See
  [the constraint](../concepts/package-private-constraint.md).
- **Register with the HUD** as part of standard `WWindow` setup before `show()`.
- **Tab panels fill their slot** — put content in a `WurmBorderPanel.CENTER` so it stretches.
- **`onTabChange` fires only on an actual change** (not on the initial/first tab) — ideal for lazily
  populating a tab or reacting to a switch.

## Source

`src/main/java/com/wurmonline/client/renderer/gui/KitTabbedWindow.java` (delegates to `KitTabPanel`).

## See also

- [`KitTabPanel`](KitTabPanel.md) — the reusable tab container this wraps (use directly for a slot).
- [`KitTreeItem`](KitTreeItem.md) — rows for a tree inside a tab. · [`GuiKit`](GuiKit.md) — tab-content helpers.
