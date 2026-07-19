# `GuiPatches`

`com.wurmonline.clientkit.GuiPatches` — optional javassist patches that harden vanilla GUI widgets for
SDK use. Each installer is **idempotent** and must be called from a mod's `preInit()` (before the
target class loads). These are **opt-in**: the SDK components work without them, but installing hardens
edge cases the components can't reach by composition — their targets are `final` classes with
`protected` internals, so bytecode is the only route.

`final`, all-static, private constructor.

This class is also the SDK's worked example of the "bytecode instrumentation" technique from
[private-class-techniques](../specs/private-class-techniques.md) §C — a real `ExprEditor` rewriting one
field write in a `final` vanilla class.

## Installers

### `installInputFieldFix()`

Fixes the Down-arrow text-wipe described in [`KitInputField`](KitInputField.md#the-down-arrow-edge-case-and-the-optional-fix).

Vanilla `WurmInputField.keyPressed` case 208 (Down) navigates history on a single-line field, and with
no history it clears the field unconditionally — which always happens for a `simpleInput` form field
(they never build history). This installer prepends a check that makes Down a **no-op for exactly that
case** and nothing else:

```java
// insertBefore on WurmInputField.keyPressed ($1 = keyCode):
"{ if ($1 == 208 && $0.simpleInput && $0.maxLines == 1) return; }"
```

It's deliberately **narrow**. An earlier version rewrote *every* empty-string write to `input` in
`keyPressed`, but Backspace-to-empty and Delete-to-empty also assign `""` there — that over-broad edit
broke last-character deletion. Targeting only keyCode 208 leaves Backspace/Delete/typing/multi-line
cursor movement untouched. Note `simpleInput` is **not** exclusive to `KitInputField` — vanilla
`BmlWindowComponent` sets it on BML form inputs too; making Down a no-op on those single-line form
fields is equally correct, and ordinary chat/console fields (`simpleInput == false`) are unaffected.

Call once in `preInit`:

```java
@Override public void preInit() {
    super.preInit();                       // if extending ServerCommandMod
    GuiPatches.installInputFieldFix();     // == KitInputField.installKeyFix()
}
```

Throws `HookException` if the target method can't be found (client-version mismatch — the one place to
re-verify is `WurmInputField.keyPressed`).

### `installTreeCellButtons()`

Enables clickable buttons inside `WurmTreeList` cells for [`KitTreeItem`](KitTreeItem.md) rows (see its
[cell buttons](KitTreeItem.md#cell-buttons) section).

Vanilla has no per-cell button hook. This installs **two** patches on `WurmTreeList$TreeListPanel`:
an `insertBefore` on `leftPressed` for the **click**, and an `insertAfter` on `renderComponent` for the
**look** (drawing the vanilla `WButton` 3-slice skin — 8px `panelTexture` caps + `panelTextureTilingH`
middle — behind each cell-button label, over the row's own scissor clip). The click patch finds the
clicked row, and for each extra column whose item reports `isCellButton(col)` and whose column slot
contains the click x, calls `cellButtonClicked(col)` and **consumes** the click (returns before the row
is selected/dragged):

```java
// insertBefore on TreeListPanel.leftPressed — accesses the outer WurmTreeList via the synthetic this$0:
WTreeListNode __ln = this.getNodeAt($1, $2);
if (__ln != null && __ln.item instanceof KitTreeItem) {
    KitTreeItem __kit = (KitTreeItem) __ln.item;
    int[] __cw = this.this$0.columnWidths;
    int __fixed = this.this$0.commonWidth + (this.this$0.hasImpColumn ? 16 : 0);
    int __base  = this.x + this.width - 4 - __fixed;      // matches the RENDER base, so click == drawn slot
    for (int __i = 0; __i < __cw.length; __i++) {
        if (__kit.isCellButton(__i) && $1 >= __base && $1 < __base + __cw[__i]) {
            __kit.cellButtonClicked(__i); return;
        }
        __base += __cw[__i];
    }
}
```

Key points:

- **Geometry matches the render layout** (`commonWidth + imp16` base), not vanilla's checkbox hit-test
  base — so the click rect aligns with where the label is actually drawn, even with an imp column.
- **Global but inert by default:** it runs for every `WurmTreeList`, but the `instanceof KitTreeItem`
  guard and `isCellButton` returning false leave all vanilla rows and non-button columns untouched.
- **Fires once per click, on press.** `leftPressed` fires on every press with an incrementing
  `clickCount`, so a double-click would call it twice — the handler is gated on `clickCount < 2` to fire
  exactly once, while the click is **consumed on both presses** (so a double-click never selects the
  row). This matters because a button action ("Buy", "Cancel") must not double-execute.
- **Handler exceptions are caught and logged** (`onCellButtonError`), so a throwing consumer handler
  can't abort the client's input dispatch — same policy as `ServerCommandMod`'s inbound dispatch.
- **Outer-instance access** uses the verified synthetic field name `this$0` — a good template for any
  patch that must reach an enclosing class from an inner one.

Throws `HookException` if `WurmTreeList$TreeListPanel.leftPressed` can't be found. This is the most
client-version-sensitive patch in the SDK (it depends on the inner class name and the field names
`columnWidths`/`commonWidth`/`hasImpColumn`/`this$0`); re-verify there on a client bump.

## Notes

- **Timing:** must run in `preInit`, before `WurmInputField` is loaded/linked. Calling it later is a
  no-op-or-too-late (the class may already be defined).
- **Idempotent:** a `volatile` guard means repeat calls (e.g. from several mods) install once.
- **Runtime-validated source string:** the javassist `replace` string is compiled when the installer
  runs, not at build time — so a client-version change that renames `simpleInput`/`input` surfaces as a
  runtime `HookException`, not a compile error. Keep the target names in sync with the client.

## Source

`src/main/java/com/wurmonline/clientkit/GuiPatches.java`

## See also

- [`KitInputField`](KitInputField.md) — the component this hardens.
- [Spec: private-class techniques §C](../specs/private-class-techniques.md) — the bytecode technique used here.
