# `KitTabPanel`

`com.wurmonline.client.renderer.gui.KitTabPanel` — a **reusable tab container**: a strip of real tab
headers ([`KitTab`](#kittab-the-tab-header)) in the NORTH slot and a swapped content panel in the
CENTER. It's a `WurmBorderPanel` (a `FlexComponent`), so it drops into **any slot of any window** —
unlike [`KitTabbedWindow`](KitTabbedWindow.md), which makes the whole window tabbed. Exactly one tab is
active; clicking a header swaps the content and fires `onTabChange`.

Real tabs, not a button row: the headers render the vanilla tab skin (raised when active/hovered,
recessed otherwise). Lives in the gui package for the package-private widgets.

## API

```java
new KitTabPanel(String name)

KitTabPanel addTab(String label, FlexComponent panel)   // first added becomes active (silently)
KitTabPanel onTabChange(IntConsumer callback)            // fired with the new index on change
KitTabPanel focusTab(int index)                          // activate programmatically (+fire if changed)
int         selected()                                    // active index, or -1 if empty
```

`addTab`/`onTabChange`/`focusTab` are fluent.

## Usage

```java
package com.wurmonline.client.renderer.gui;

KitTabPanel tabs = new KitTabPanel("browserTabs")
        .onTabChange(i -> requestTabData(i));       // e.g. lazy-load a tab's data
tabs.addTab("Browse",   browsePanel);               // shown by default
tabs.addTab("Settings", settingsPanel);

someWindow.setComponent(tabs, WurmBorderPanel.CENTER);   // drop it into any slot
```

## Notes & gotchas

- **Drop `KitTabPanel` into a slot; use `KitTabbedWindow` for a whole tabbed window.** The panel is just
  a `FlexComponent` — put it in a border slot (it stretches to fill), or nest it beside a sidebar, etc.
- **Exactly one tab active, always** — the first `addTab` activates silently; selecting is atomic (all
  other headers are de-selected in the same call). Same model as [`SegmentedButtons`](SegmentedButtons.md).
- **Fires only on change** — clicking the active tab or `focusTab(current)` is a no-op (no `onTabChange`).
  Use `onTabChange` to lazily populate a tab or to sync a server "open on tab N".
- **Content panels fill the CENTER** — give each tab's panel real content (e.g. a tree in a
  `WurmBorderPanel.CENTER`); a bare shrink-wrap panel looks cramped.
- **Single-threaded** — build and switch on the client thread.

## `KitTab` (the tab header)

`KitTab` is the `WButton` subclass that renders the genuine Wurm tab shape — the same 3-slice
`panelTexture` skin vanilla's chat `TabButton` uses (raised/recessed by state) — **without** the chat
coupling (`TabButton`'s only blocker is its `TabButton(AbstractTab, label)` ctor). You don't normally
construct it directly; `KitTabPanel.addTab` makes them. It exists as a public class so a mod can reuse a
real tab header standalone if needed (`new KitTab(label, listener)` + `setSelected(boolean)`).

## Source

`src/main/java/com/wurmonline/client/renderer/gui/KitTabPanel.java`,
`src/main/java/com/wurmonline/client/renderer/gui/KitTab.java`

## See also

- [`KitTabbedWindow`](KitTabbedWindow.md) — a whole window (with title bar) built on `KitTabPanel`.
- [Spec: client GUI internals §3](../specs/client-gui-internals.md) — why vanilla tabs aren't reusable.
