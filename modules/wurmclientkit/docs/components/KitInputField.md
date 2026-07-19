# `KitInputField`

`com.wurmonline.client.renderer.gui.KitInputField` — a self-managing wrapper around the game's
`WurmInputField` for **form fields**. It removes the two things every mod hand-rolls: the stray
`"] "` prompt and the "Enter wipes the text" chat behavior. `final`; lives in the gui package for
package-private access to `WurmInputField` + `InputFieldListener`.

## Why it exists

Vanilla single-line `WurmInputField`s are chat-style: pressing Enter archives the value to history and
**blanks the field**. Mods work around this by keeping a shadow `String` mirror updated on every edit
and restored on submit — AuctionHouse's window carries ~9 such mirrors, BuyOrderWindow 4. `KitInputField`
sets the field's `simpleInput = true` (which makes Enter *keep* the text — real form behavior) **and**
mirrors the committed value internally, so `getValue()` is always correct. No more shadow strings.

`WurmInputField` is `final`, so this is a **wrapper**, not a subclass (see
[private-class-techniques](../specs/private-class-techniques.md) technique B).

## API

```java
new KitInputField(String name)          // prompt cleared, simpleInput=true, self-registered as listener

WurmInputField field()                  // the widget — add THIS to a panel slot

KitInputField onSubmit(Consumer<String>)   // Enter
KitInputField onChange(Consumer<String>)   // every edit
KitInputField onEscape(Runnable)           // Esc

String  getValue()                      // committed value, never null
boolean isBlank()                       // empty or whitespace-only
KitInputField setValue(String)          // set text + mirror (moves caret to end)
void    clear()
KitInputField setEnabled(boolean)       // grey out / re-enable
KitInputField maxChars(int)             // cap accepted length

static void installKeyFix()             // optional preInit hardening (see below)

int     asInt(int fallback)             // parse, fallback on failure
long    asLong(long fallback)
double  asDouble(double fallback)
Integer asIntOrNull()                   // null when blank/unparseable (optional fields)
Long    asLongOrNull()
Double  asDoubleOrNull()
```

The `on*` setters are fluent (`return this`).

## Usage

```java
package com.wurmonline.client.renderer.gui;

// a required numeric field with a submit action
KitInputField price = new KitInputField("price").onSubmit(v -> requestQuote());
row.addComponent(price.field());
long iron = price.asLong(0);            // 0 if the user typed nothing/garbage

// an optional filter
KitInputField qlMin = new KitInputField("qlMin");
filterRow.addComponent(qlMin.field());
Double min = qlMin.asDoubleOrNull();    // null → "no minimum"
```

## The Down-arrow edge case (and the optional fix)

`simpleInput=true` stops **Enter** from clearing the field, but on a single-line field vanilla's
**Down-arrow** branch (`WurmInputField.keyPressed` case 208) clears the text *unconditionally* when
there's no history — and a `simpleInput` field never has history, so a single Down keystroke in a
focused field silently wipes it. The wrapper can't intercept this (the class is `final`, `keyPressed` is
`protected`).

The fix is a bytecode patch, exposed as [`GuiPatches.installInputFieldFix()`](GuiPatches.md) (also
reachable as `KitInputField.installKeyFix()`). Call it **once in your mod's `preInit()`**:

```java
@Override public void preInit() {
    super.preInit();                 // if extending ServerCommandMod
    KitInputField.installKeyFix();   // makes Down a no-op on single-line simpleInput fields
}
```

It's idempotent and narrow: it only makes Down (keyCode 208) a no-op on single-line `simpleInput`
fields — Backspace, Delete, typing, and multi-line cursor movement are untouched. Ordinary chat/console
fields (`simpleInput == false`) are unaffected; vanilla `BmlWindowComponent` form inputs also set
`simpleInput`, and Down doing nothing there is equally correct. Without the patch, `KitInputField` still
works for its main purpose (Enter no longer clears); only the Down-arrow foot-gun remains.

## Notes & gotchas

- **Add `field()` to panels, not the wrapper.** `KitInputField` is not a `FlexComponent`; the wrapped
  `WurmInputField` is. Mutate text via `setValue`, not `field().setText(...)`, or `getValue()` desyncs.
- **`simpleInput=true` disables up/down history recall** — that's the intended form-field trade-off. If
  you specifically want a chat/search box that clears and recalls history on Enter, use a raw
  `WurmInputField` (or `GuiKit.input`) instead.
- **Install `installKeyFix()` in `preInit`** if you want the Down-arrow hardening (see above).
- **Numeric parsing uses `Integer/Long/Double.parseX`** — locale-independent (always `.` decimal),
  trims surrounding whitespace, accepts a leading `+`/`-`. `asLong("12.5")` fails → returns the
  fallback (or null); use `asDouble` for decimals.
- **`*OrNull` vs `asX(fallback)`:** use `*OrNull` for genuinely optional inputs (blank means "unset");
  use the fallback form when a value is always required and you have a sensible default.
- **Single-threaded:** all access is on the client (game) thread; the callbacks fire there too.
- Prefer this over `GuiKit.input` for any field whose *value you read back*. `GuiKit.input` just clears
  the prompt; `KitInputField` also solves the Enter-clear and gives you typed reads.

## Source

`src/main/java/com/wurmonline/client/renderer/gui/KitInputField.java`

## See also

- [`GuiKit`](GuiKit.md) — lighter-weight `input(name, listener)` when you don't need value management.
- [Spec: client GUI internals §4](../specs/client-gui-internals.md) — the `WurmInputField` semantics this relies on.
- [Spec: private-class techniques](../specs/private-class-techniques.md) — why it's a wrapper.
