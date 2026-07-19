# `WPopupBuilder`

`com.wurmonline.client.renderer.gui.WPopupBuilder` — a fluent builder for a `WurmPopup` context menu.
Vanilla popups are awkward to extend: `addButton`/`addSeparator` and the button base are package-private,
and a live button is an inner class you instantiate as `popup.new WPopupLiveButton(label){ … }` with an
abstract `handleLeftClick`. This wraps all of it so you add a labelled action with a `Runnable`.
Generalises RecipeExamine's popup-button injection.

`final`; lives in the gui package for the package-private popup API.

## API

```java
new WPopupBuilder(WurmPopup existing)
static WPopupBuilder create(String id, String title, int x, int y)   // fresh popup, deterministic pos

WPopupBuilder button(String label, Runnable onClick)   // clickable action row
WPopupBuilder label(String text)                       // inert (non-clickable) label row
WPopupBuilder separator()
WurmPopup     popup()                                  // the wrapped popup
void          show()                                   // hud.showPopupComponent(popup)
```

The builder methods are fluent.

## Usage

```java
package com.wurmonline.client.renderer.gui;

WPopupBuilder.create("mymenu", "Options", mouseX, mouseY)
    .button("Examine", () -> examine(itemId))
    .button("Copy name", () -> copy(name))
    .separator()
    .label("(read-only)")
    .show();
```

## Notes & gotchas

- **`button`'s `Runnable` runs after the popup closes.** Vanilla `WPopupLiveButton.leftPressed` clears
  the popups, then calls `handleLeftClick` — so your action runs on a dismissed menu, on the client thread.
- **`create` uses deterministic placement** (the `offset=false` ctor); the default `WurmPopup` ctor
  jitters position by −16..0px.
- **Lambda caveat (invokedynamic).** The builder itself is lambda-free (it uses an anonymous
  `WPopupLiveButton`), so it's safe. But if you call `button(label, lambda)` **from a class that is
  referenced by javassist-injected code** (e.g. a helper a hook invokes, like RecipeExamine's), pass an
  anonymous `Runnable` instead of a lambda — a lambda would make that helper carry `invokedynamic`, which
  the client's javassist 3.12.1 can't parse. See [private-class-techniques](../specs/private-class-techniques.md).
- **Same-loader requirement.** Like all gui-package classes, it must load on the `WWindow` loader — the
  `ClassClassPath` append (`ServerCommandMod` / `Hooks.appendClassPath`) covers this.
- **Submenus** aren't exposed yet (the vanilla submenu-open behavior wasn't verified) — use `popup()` and
  the vanilla API if you need one.
- **Single-threaded:** build and show on the client thread.

## Source

`src/main/java/com/wurmonline/client/renderer/gui/WPopupBuilder.java`

## See also

- [Spec: client GUI internals §4](../specs/client-gui-internals.md) — `WurmPopup` and its public button types.
- [`Hooks`](Hooks.md) — `appendClassPath` for the loader requirement; the invokedynamic constraint.
