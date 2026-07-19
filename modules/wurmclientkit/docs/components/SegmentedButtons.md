# `SegmentedButtons`

`com.wurmonline.client.renderer.gui.SegmentedButtons` — a horizontal row of buttons where **exactly one
is active**, the active one shown pressed (`WButton.setDown(true)`). A mutually-exclusive mode toggle:
"Per unit / Total", "Buy / Sell", "By name / By price". Unifies the two hand-rolled idioms in the
auction UI (setDown + dim the inactive field; a `"> "` label prefix) into one consistent visual.

`final`; lives in the gui package to construct the package-private `WButton`/`WurmArrayPanel` and
implement `ButtonListener`.

## API

```java
new SegmentedButtons(String name)
new SegmentedButtons(String name, String... labels)   // first label becomes active

WurmArrayPanel<FlexComponent> panel()   // add to a parent slot
SegmentedButtons add(String label)      // append a segment (first added auto-activates, silently)
SegmentedButtons onSelect(IntConsumer)  // fired when the active segment changes
int  selected()                         // active index, or -1 if empty
SegmentedButtons select(int index)      // activate + fire onSelect if changed
SegmentedButtons selectSilently(int)    // activate without firing
SegmentedButtons fireSelected()         // fire onSelect for the current selection (init default state)
```

The `on*`/`select*`/`add` methods are fluent.

## Usage

```java
package com.wurmonline.client.renderer.gui;

SegmentedButtons mode = new SegmentedButtons("priceMode", "Per unit", "Total")
        .onSelect(i -> applyPriceMode(i));
row.addComponent(mode.panel());

int m = mode.selected();       // 0 or 1
mode.selectSilently(0);        // reset to "Per unit" without triggering applyPriceMode
```

## Notes & gotchas

- **Exactly one active, always.** The first segment added is activated silently, so there's never a
  zero-active state. Selecting is atomic — every other segment is un-pressed in the same call.
- **Fires only on change.** Clicking the already-active segment is a no-op (no `onSelect`). `select(i)`
  with the current index also does nothing.
- **The default selection does NOT fire `onSelect`** — the initial active segment is applied silently.
  To initialize downstream state for the starting mode, either read `selected()` and apply it yourself,
  or call `fireSelected()` once after wiring `onSelect`.
- **Don't add your own widgets via `panel()`** — it returns the live bar for embedding into a parent;
  components you add there aren't tracked as segments (only the wired `WButton`s toggle).
- **Active look needs the button enabled.** `WButton` only renders the pressed state while `enabled`
  (vanilla gates the down-look on `enabled`). Don't disable the active segment or it won't look active.
- **Add to a parent via `panel()`** — `SegmentedButtons` isn't itself a `FlexComponent`; the wrapped
  horizontal array is.
- **Out-of-range `select` is a silent no-op** — it leaves the current selection unchanged.
- **Single-threaded:** clicks and `onSelect` run on the client thread.

## Source

`src/main/java/com/wurmonline/client/renderer/gui/SegmentedButtons.java`

## See also

- [`KitDropDown`](KitDropDown.md) — when there are many options (a dropdown), not a few (a segmented row).
- [Spec: client GUI internals §4](../specs/client-gui-internals.md) — `WButton.setDown` semantics.
