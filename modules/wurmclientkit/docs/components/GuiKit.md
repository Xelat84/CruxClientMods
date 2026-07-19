# `GuiKit`

`com.wurmonline.client.renderer.gui.GuiKit` — static factory helpers for the game's package-private
GUI widgets. Lives in the vanilla gui package so it can construct widgets whose constructors and
fields are package-private (see [the constraint](../concepts/package-private-constraint.md)). `final`,
all-static, private constructor.

Each helper wraps one recurring, easy-to-get-wrong idiom discovered building AuctionHouse.

## Methods

### `input(String name, InputFieldListener listener) → WurmInputField`

A single-line input field with the **default `"] "` prompt cleared**. Vanilla `WurmInputField`
defaults `prompt` to `"] "`, so every input box shows a stray bracket unless you blank it. This helper
constructs the field and sets `prompt = ""` for you.

```java
WurmInputField search = GuiKit.input("search", myListener);
```

The listener fires on Enter (`handleInput`) and per-change (`handleInputChanged`).

### `icon(int imageNumber) → Texture`

An item icon by **image number** (not template id), or `null` on a non-positive number or any load
failure. Wraps `IconLoader.getIcon((short) imageNumber)` in a try/catch so a bad icon never throws
into your render path.

```java
Texture tex = GuiKit.icon(item.imageNumber);   // may be null — callers handle null
```

> **Image number, not template id.** `IconLoader` indexes by the template's *image number*
> (`sheet = id/240, sub = id%240`), which the **server must send** — the client has no template→image
> registry for arbitrary templates. Passing a template id gives the wrong icon or none. This is the
> single most common icon bug. Read the value with `PacketReader.getUnsignedShort()`
> ([wire format](../concepts/wire-format.md)).

### `centered(String name, FlexComponent content) → WurmDecorator`

Wraps `content` in a `WurmDecorator` with `align = ALIGN_CENTER`, so the content **centers** rather
than stretches. Only meaningful when the decorator is then placed in a **border-panel slot**, which
stretches to give the decorator room to center within:

```java
borderPanel.setComponent(GuiKit.centered("okWrap", okButton), WurmBorderPanel.SOUTH);
```

Centering inside a `WurmArrayPanel` does nothing — array panels shrink-wrap their children, so there's
no slack to center in. This is the vanilla `ConfirmWindow` button-centering trick, packaged. See the
[layout gotcha](../gotchas.md).

### `hbox(String name)` / `vbox(String name) → WurmArrayPanel<FlexComponent>`

Shrink-wrap panels: `hbox` is `DIR_HORIZONTAL`, `vbox` is `DIR_VERTICAL`. Convenience constructors for
the two most common array-panel layouts.

```java
WurmArrayPanel<FlexComponent> row = GuiKit.hbox("buttons");
row.addComponent(saveButton);
row.addComponent(cancelButton);
```

### `row(String name, int gap) → WurmArrayPanel<FlexComponent>`

Like `hbox`, but sets `componentWidthOffset = gap` so children are spaced `gap` pixels apart — the only
inter-item spacing knob `WurmArrayPanel` has.

```java
WurmArrayPanel<FlexComponent> buttons = GuiKit.row("buttons", 6);   // 6px between each
```

### `spacer(int width, int height) → WurmPanel`

A fixed-size **transparent** spacer. Because `WurmArrayPanel` has *no* vertical inter-item spacing,
a `spacer(1, h)` is how you insert a vertical gap between rows in a `vbox`.

```java
column.addComponent(headerRow);
column.addComponent(GuiKit.spacer(1, 6));   // 6px vertical gap
column.addComponent(bodyRow);
```

## When to reach for it

Use `GuiKit` for the small stuff — an input, an icon lookup, a centered row, a quick box. For a whole
window use [`KitTabbedWindow`](KitTabbedWindow.md); for tree rows use [`KitTreeItem`](KitTreeItem.md).
For layout beyond these, compose `WurmBorderPanel`/`WurmArrayPanel` directly (the SDK deliberately
doesn't wrap all of layout — see the workspace `ClientToolkitReference.md` §3 for the full layout
rules).

## Source

`src/main/java/com/wurmonline/client/renderer/gui/GuiKit.java`

## See also

- [Package-private constraint](../concepts/package-private-constraint.md) — why this is in the gui package.
- [Gotchas](../gotchas.md) — icons, prompts, and centering as a checklist.
