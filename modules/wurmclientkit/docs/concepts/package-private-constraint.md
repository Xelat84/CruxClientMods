# Concept: the package-private constraint

**The single most important rule in client GUI modding.** Get it wrong and your window won't compile
(or worse, compiles but throws `IllegalAccessError` at runtime).

## The rule

Almost every GUI class in `com.wurmonline.client.renderer.gui` is **package-private** — no `public`
modifier on the class, its constructors, or many of its fields and methods:

`WWindow`, `WurmBorderPanel`, `WurmArrayPanel`, `WurmDecorator`, `WButton`, `WurmInputField`,
`WurmTreeList`, `TreeListItem`, `WurmDropDown`, `WCheckBox`, `WurmLabel`, `WurmPopup`, …

To subclass one of these or touch its package-private members, **your class must be declared in
package `com.wurmonline.client.renderer.gui`.** In this repo that means the file lives under:

```
src/main/java/com/wurmonline/client/renderer/gui/YourWindow.java
```

with `package com.wurmonline.client.renderer.gui;` at the top.

The publicly usable exceptions — safe to reference from any package — are `WurmComponent`,
`FlexComponent`, `ContainerComponent`, `BmlWindowComponent`, and `WurmTabPanel`.

## Why the SDK is split into two packages

- `com.wurmonline.clientkit` — `ServerCommandMod`, `PacketWriter`, `PacketReader`, `Format`. None of
  these touch a package-private widget, so they live in the SDK's own clean namespace.
- `com.wurmonline.client.renderer.gui` — `KitTabbedWindow`, `KitTreeItem`, `GuiKit`. All three touch
  package-private widgets, so they are declared in the vanilla gui package.

When you build a mod on the SDK, the same split applies to *your* code: protocol/logic in your own
package, window/widget classes in `com.wurmonline.client.renderer.gui`.

## Compile-time access is not enough — runtime needs the same classloader

Declaring the package correctly satisfies the *compiler*. But Java also enforces package access at
runtime **per classloader**: two classes are only "in the same package" if they share the same
package name **and** the same defining classloader. Vanilla `WWindow` is defined by the client's
loader; your `YourWindow` is defined by the mod's private loader. Without help, they are *different*
runtime packages and the package-private access throws `IllegalAccessError`.

The fix is the **`ClassClassPath` append** performed in `ServerCommandMod.preInit()`:

```java
pool.appendClassPath(new ClassClassPath(getClass()));
```

This puts your subclass's jar bytecode on the HookManager Loader's class pool, so your gui-package
classes load on the **same loader as `WWindow`** — which is exactly what makes the package-private
access legal at runtime. See [the hooking model](hooking-model.md) for the full mechanism (it's the
same append that fixes the `NoClassDefFoundError` on the patched vanilla method).

> If your mod does **not** extend `ServerCommandMod` (pure-GUI mod, no custom protocol), you must do
> the `ClassClassPath` append yourself in `preInit()`, or your gui-package classes will fault at
> runtime. `ServerCommandMod` does it for free; a standalone GUI mod does not get it for free.

## Symptom → cause quick table

| Symptom | Cause |
|---------|-------|
| `error: WWindow is not public in ...; cannot be accessed from outside package` | Your class isn't in `com.wurmonline.client.renderer.gui`. |
| `IllegalAccessError` at runtime touching a widget | Right package, wrong loader — missing `ClassClassPath` append. |
| `NoClassDefFoundError` in a patched vanilla method | Same root cause — see [hooking model](hooking-model.md). |

## See also

- [Hooking model](hooking-model.md) — the `ClassClassPath` append in full.
- [Gotchas](../gotchas.md) — the checklist form of this rule.
- Workspace `ClientToolkitReference.md` §0 — the exhaustive list of package-private classes.
