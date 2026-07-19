# Spec: BML dialogs (declarative server-style forms)

The client can build a whole window from a **BML** string (the same markup the server uses for its
dialogs). This is a distinct alternative to hand-composing widgets ([`KitTabbedWindow`](../components/KitTabbedWindow.md)
et al.): you describe the form declaratively, show it, and read the field values back on submit. Best
for **quick modal prompts** (an amount, a confirm-with-options, a small form). Verified against
`../../../Decompiled/Client_vf/.../gui/BmlWindowComponent.java`; names stable, lines drift.

## The class

`BmlWindowComponent extends WWindow` (`public final`) — a full window rendered from a BML string.

- **Constructors are package-private:** `BmlWindowComponent(String title, String bml, BmlWindowListener
  owner)` and `(title, org.w3c.dom.Document, owner)`. So you construct it from **gui-package** code
  (in-package trick — see [private-class-techniques](private-class-techniques.md)).
- **`BmlWindowListener`** (package-private interface): `submit(BmlWindowComponent w, String buttonName)`
  (a button was clicked) and `cancel(BmlWindowComponent w)`.
- **`buildOutMap()`** → `Map<String,String>` of `id` → value: inputs give their text, checkboxes give
  `"true"`/`"false"`, dropdowns give their selection. This is how you read the form on submit.
- **`parseBMLUpdate(String bml)`** (public) — replace the form's content live.

## Lifecycle — you manage show/hide

`BmlWindowComponent` is a **dynamic component**: add it to show, remove it in your listener to close.

```java
package com.wurmonline.client.renderer.gui;   // gui package — ctor is package-private

String bml =
    "<varray rescale='true'>" +
    "  <text type='bold' text='How many to sell? (blank = all)' />" +
    "  <input id='answer' maxchars='9' text='' />" +
    "  <harray><button text='Stage' id='ok' /></harray>" +
    "</varray>";

BmlWindowComponent prompt = new BmlWindowComponent("Sell quantity", bml, new BmlWindowListener() {
    @Override public void cancel(BmlWindowComponent w) { WurmComponent.hud.removeDynamicComponent(w); }
    @Override public void submit(BmlWindowComponent w, String buttonName) {
        WurmComponent.hud.removeDynamicComponent(w);          // always close
        if (!"ok".equals(buttonName)) return;                 // which button?
        String answer = w.buildOutMap().get("answer");        // read the field by id
        long amount = parseOrDefault(answer, 0L);
        // ... act ...
    }
});
WurmComponent.hud.addDynamicComponent(prompt);                // SHOW it
```

This is exactly AuctionHouse's `promptSellAmount`. Note `submit` gives you the **clicked button's id**,
and you read every field from `buildOutMap()` — there are no per-field change callbacks (beyond a
field's own vanilla listener), so BML is a fill-then-submit model, not a reactive one.

## BML grammar (the subset the client parses)

Layout containers and widgets, XML-ish, attributes single-quoted:

- **Layout:** `<varray>` (vertical), `<harray>` (horizontal), `<border>`; `rescale='true'` to size to
  content.
- **Widgets:** `<text type='bold' text='…'/>`, `<label text='…'/>`, `<header text='…'/>`,
  `<input id='x' maxchars='9' text=''/>`, `<checkbox id='x' text='…'/>`, `<dropdown id='x' …/>`,
  `<radio …/>`, `<button text='…' id='ok'/>`.
- **Common attributes:** `id` (the key in `buildOutMap` / the button name in `submit`), `text`,
  `type` (e.g. `bold`), `maxchars`, `color`, `size`, `hover`.

Internally the parser fills id-keyed maps (`inputFields`, `checkBoxes`, `dropDownLists`, `radioButtons`,
`treeLists`, `buttons`) — all package-private, reachable from in-package code if you need a widget
directly, but `buildOutMap()` is the normal way.

## BML vs hand-built widgets — when to use which

- **BML** — a quick modal prompt/form you throw up and read once (amount entry, a small options dialog).
  Minimal code, no layout work, but string-typed values, no reactive callbacks, and the ctor forces
  gui-package placement.
- **Hand-built ([`KitTabbedWindow`](../components/KitTabbedWindow.md) + [`KitInputField`](../components/KitInputField.md)/
  [`KitDropDown`](../components/KitDropDown.md)/…)** — a persistent, interactive, multi-tab window with
  live callbacks, custom rendering, drag targets. More code, full control.

## Gotchas

- **Construct in the gui package** (package-private ctor + listener).
- **You own the lifecycle** — `hud.addDynamicComponent(w)` to show, `hud.removeDynamicComponent(w)` in
  *both* `submit` and `cancel`, or the window leaks on screen.
- **Values are strings** — parse them (`buildOutMap().get(id)`); checkboxes are `"true"`/`"false"`.
- **`submit` fires per button** — branch on `buttonName` (the button's `id`).
- **It's a `WWindow`** — it carries title-bar chrome; for a borderless overlay use a custom component.
- **Game thread only** — build/show/read on the client thread ([game-state-access](game-state-access.md) §7).

## See also

- [client-gui-internals](client-gui-internals.md) §3 — where `BmlWindowComponent` sits among the widgets.
- [`KitTabbedWindow`](../components/KitTabbedWindow.md) — the hand-built alternative for rich windows.
- [private-class-techniques](private-class-techniques.md) — the gui-package requirement.
