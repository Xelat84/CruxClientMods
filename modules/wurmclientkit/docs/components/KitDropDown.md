# `KitDropDown`

`com.wurmonline.client.renderer.gui.KitDropDown` — a `WurmDropDown` that fires a callback on
selection change. Vanilla `WurmDropDown` has **no change listener**: the popup commits a choice by
calling the package-private `setValue(int)`, leaving mods to poll `getValue()` every `gameTick`. This
subclass overrides `setValue` to notify an `IntConsumer` the moment the value changes.

`WurmDropDown` is non-final, so `KitDropDown` **is** the widget — add it straight to a panel (unlike
[`KitInputField`](KitInputField.md), which wraps a `final` field). Lives in the gui package to subclass
the package-private class and override its package-private `setValue`.

## API

```java
new KitDropDown(String name, String[] options)              // starts at index 0
new KitDropDown(String name, int value, String[] options)

KitDropDown onChange(IntConsumer callback)   // fired when the selected index changes
int         selected()                       // current index (== getValue())
int         optionCount()                    // number of options
String      selectedText()                   // current option label, or "" if none
KitDropDown select(int index)                // set (clamped) + fire onChange if changed
KitDropDown selectSilently(int index)        // set without firing (programmatic reset)
```

The `on*`/`select*` methods are fluent.

## Usage

```java
package com.wurmonline.client.renderer.gui;

KitDropDown material = new KitDropDown("material", new String[]{"iron", "steel", "bronze"})
        .onChange(i -> rebuildForMaterial(i));
row.addComponent(material);                 // it IS a FlexComponent

int chosen = material.selected();
material.selectSilently(0);                 // reset to iron without triggering rebuild
```

## Notes & gotchas

- **Fires only on actual change.** Re-selecting the current value (popup or `select`) does not fire —
  the override compares against `getValue()` before applying. Use this to avoid redundant work.
- **`selectSilently` suppresses only the callback**, not the value change — `super.setValue` still runs.
- **The option list is fixed at construction.** `WurmDropDown.options` is `private final`; to change the
  choices you must build a new `KitDropDown` and swap it into the panel (the AuctionHouse
  "rebuildVaultDropDown" pattern). There is no `setOptions`. `KitDropDown` keeps its own reference to
  the array to power `optionCount()`/`selectedText()`.
- **`options` must be non-null** (may be empty) — the ctor fail-fasts on null; vanilla would otherwise
  NPE later on the render thread.
- **Indices are clamped, not trusted.** `WurmDropDown.renderComponent` indexes `options[value]` with no
  range check, so a bad index crashes the render thread every frame. `KitDropDown` clamps every value
  (ctor, `select`, `selectSilently`, and popup) into `[0, optionCount)` — so a stale index after a
  rebuild lands on the nearest valid option instead of crashing. Empty-option dropdowns stay at 0.
- **Single-threaded:** selection, `gameTick`, and the callback all run on the client thread.
- Prefer this over polling `getValue()` in `gameTick` — that pattern (BuyOrderWindow) is exactly what
  this removes.

## Source

`src/main/java/com/wurmonline/client/renderer/gui/KitDropDown.java`

## See also

- [`KitInputField`](KitInputField.md) — the wrap-vs-subclass contrast (final vs non-final).
- [Spec: client GUI internals §4](../specs/client-gui-internals.md) — `WurmDropDown` semantics.
