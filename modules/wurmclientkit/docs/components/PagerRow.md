# `PagerRow`

`com.wurmonline.client.renderer.gui.PagerRow` — a centered pager `|<  <  Page X of Y  >  >|` bound to a
`(page, totalPages)` model. Clicking a button clamps to range and fires `onPage` with the new 0-based
page; the buttons disable at the ends. Generalises AuctionHouse's hand-built pager row.

`final`; lives in the gui package for the package-private widgets.

## Flow (unidirectional)

```
click a button  →  onPage(newPage)  →  you request that page from the server
server responds →  setState(page, totalPages)  →  label + buttons resync (does NOT re-fire onPage)
```

`setState` deliberately does not fire `onPage`, so calling it from your data-arrived handler can't cause
a request loop.

**Contract:** the widget advances its page *optimistically* on click (so rapid clicks step correctly),
then relies on `setState` to confirm or correct it. **Call `setState` on every response — success,
empty, or failure** — or the pager can stay stuck on the optimistic page, out of sync with the data
shown. While a request is in flight, `page()` is the *requested* page, which may differ from what's
displayed until `setState` lands. (Out-of-order responses to rapid clicks will visibly rewind the pager
to whatever the latest `setState` says — same as the auction's server-as-truth behavior.)

## API

```java
new PagerRow(String name)

FlexComponent component()                 // add to a border slot (stretches → centers)
PagerRow onPage(IntConsumer callback)     // fired with new 0-based page on a button change
int      page()                           // current 0-based page
int      totalPages()
PagerRow setState(int page, int totalPages)   // resync label+buttons; clamps; does NOT fire onPage
```

## Usage

```java
package com.wurmonline.client.renderer.gui;

PagerRow pager = new PagerRow("pager").onPage(p -> requestBrowse(p));
bottom.setComponent(pager.component(), WurmBorderPanel.NORTH);   // a border slot centers it

// when a browse response arrives:
pager.setState(response.page, response.totalPages);
```

## Notes & gotchas

- **Buttons disable at the ends.** `|<`/`<` are disabled on page 0; `>`/`>|` on the last page; all four
  when there's a single page — a clear affordance, and `go()` also change-guards so a stray click can't
  double-request.
- **`setState` never fires `onPage`.** It's for syncing the widget to server data; button clicks are the
  only thing that fires `onPage`.
- **0-based internally, 1-based in the label.** `page()` returns 0-based; the label shows `page + 1`.
- **Label width is reserved for 4-digit counts** (`"Page 0000 of 0000"`), so the buttons don't shift as
  the number changes (`WurmLabel` never resizes). Past 9999 pages the label text is wider than the
  reserve and *overdraws* the next/last buttons (`WurmLabel` has no scissor clip) — cosmetic only, and
  not a realistic page count.
- **Place `component()` in a border slot**, not an array panel — the decorator centers only when its
  parent stretches it (array panels shrink-wrap). See [`GuiKit.centered`](GuiKit.md).
- **Single-threaded:** clicks and `onPage` run on the client thread.

## Source

`src/main/java/com/wurmonline/client/renderer/gui/PagerRow.java`

## See also

- [`GuiKit`](GuiKit.md) — the centering idiom this uses.
- [`KitTreeItem`](KitTreeItem.md) — the paged content a pager usually drives.
