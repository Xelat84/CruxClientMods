# `ValueLineBox`

`com.wurmonline.client.renderer.gui.ValueLineBox` — a vertical stack of text lines with an optional
header, where **only non-blank lines show** and the whole box (header included) **collapses to nothing
when every line is blank**. The general form of AuctionHouse's "- Pricing -" box: an absent fact is
omitted, never printed as an empty or negative line.

Labels are pooled and reused across `set()` calls, so refreshing every tick doesn't churn widgets.
Lives in the gui package for package-private access to `WurmArrayPanel` + `WurmLabel`.

## API

```java
new ValueLineBox(String name)                 // no header
new ValueLineBox(String name, String header)  // header shown only when ≥1 line present

WurmArrayPanel<FlexComponent> panel()          // add to a parent slot...
ValueLineBox appendTo(WurmArrayPanel<FlexComponent> parent)  // ...or append fluently

void set(String... lines)                      // blanks omitted; all blank → box hidden
void set(List<String> lines)
void clear()
```

## Usage

```java
package com.wurmonline.client.renderer.gui;

ValueLineBox pricing = new ValueLineBox("pricing", "- Pricing -").appendTo(sidebar);

// on refresh — any of these may be blank/null:
pricing.set(traderOfferLine, otherSellersLine, otherBuyersLine);
// → shows the header + only the non-blank lines; if all three are blank, the box vanishes entirely.
```

## Notes & gotchas

- **Collapse semantics:** if no line is present, nothing is added — not even the header. This is the
  point: don't render "no other sellers" as an empty row; just show nothing.
- **Lines are always white.** `WurmLabel` hardcodes white text and its `filledBg` is dead. If you need
  colored or highlighted lines, `ValueLineBox` is the wrong tool — compose your own widget.
- **Give the box an ancestor that imposes a width.** `autoWidth` stretches each *shorter* line **up** to
  the panel's current width — but it does **not** grow the panel for a line *longer* than that width
  (`WurmLabel.setLabel` never resizes). So in a pure shrink-wrap chain a long line clips. Put the box
  under something that sets a real width ≥ your longest line: a `WurmScrollPanel`, a fixed-size
  container, or a border slot. A bare, unsized parent yields clipped/zero-width lines.
- **Relayout is implicit.** Like the original `AuctionPricingBox`, changing the lines via `set()` (which
  add/removes children) relies on `WurmArrayPanel`'s own relayout — no explicit `layout()` call needed
  in the common shrink-wrap chain. If you nest it somewhere that doesn't propagate, call `layout()` on
  the ancestor yourself.
- **Single-threaded:** call `set()` on the client thread.

## Source

`src/main/java/com/wurmonline/client/renderer/gui/ValueLineBox.java`

## See also

- [`GuiKit`](GuiKit.md) — `vbox`/`hbox` and other panel helpers.
- [Spec: client GUI internals §4](../specs/client-gui-internals.md) — `WurmArrayPanel`/`WurmLabel` behavior.
