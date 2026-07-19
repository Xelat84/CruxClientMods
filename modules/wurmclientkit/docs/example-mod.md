# Worked example: a mod using the whole SDK

`examples/` holds a small, **compilable** reference mod that composes most of WurmClientKit end to end.
It is deliberately **not** under `src/main/java`, so it never ships in the library jar — it's reference
code and a full-surface integration check (building it against the jar confirms the components compose).

## The two files

- **`examples/src/com/example/examplemod/ExampleMod.java`** — the mod logic, extending
  [`ServerCommandMod`](components/ServerCommandMod.md) on command byte `−80`. Shows:
  - `preInit()` calling `super.preInit()` then [`GuiPatches`](components/GuiPatches.md)
    `installInputFieldFix()` + `installTreeCellButtons()`.
  - inbound `handle` decoding a sub-command with [`PacketReader`](components/PacketCodec.md) (note
    `getUnsignedShort()` for image numbers), and driving the window.
  - outbound replies built with `PacketWriter` and sent via `send(connection, …)`.
- **`examples/src/com/wurmonline/client/renderer/gui/ExampleWindow.java`** — the GUI (in the gui package,
  as required). A [`KitTabbedWindow`](components/KitTabbedWindow.md) with:
  - an **Items** tab — a `WurmTreeList` of [`KitTreeItem`](components/KitTreeItem.md) rows with a numeric
    `sortKeys` price/QL and a clickable **"Buy"** cell button, plus a [`PagerRow`](components/PagerRow.md)
    in the SOUTH slot.
  - a **Filters** tab — a [`KitInputField`](components/KitInputField.md) search, a
    [`KitDropDown`](components/KitDropDown.md) material picker, a
    [`SegmentedButtons`](components/SegmentedButtons.md) mode toggle, and a
    [`ValueLineBox`](components/ValueLineBox.md) inside a [`Collapsible`](components/Collapsible.md),
    laid out with [`GuiKit`](components/GuiKit.md) `vbox`/`row`/`spacer`, and
    [`Format`](components/Format.md) for the coin/QL strings.

## Compiling it (integration check)

Not built by `mvn` (it's outside `src/main/java`). To verify it composes, compile against the built jar
plus the client jars:

```bash
javac -cp "target/wurmclientkit-0.1.jar;<client>;<common-client>;<client-modlauncher>" \
      -d /tmp/out \
      examples/src/com/wurmonline/client/renderer/gui/ExampleWindow.java \
      examples/src/com/example/examplemod/ExampleMod.java
```

(On Git Bash, convert the jar paths with `cygpath -w` and use `;` as the classpath separator.) A clean
compile means every component's public API still lines up — a cheap regression guard when the SDK
changes.

## Note on lambdas

`ExampleMod`/`ExampleWindow` use lambdas freely (e.g. `onPage`, `onChange`, `this::sendBuy`) — that's
safe because **neither class is referenced by name from injected bytecode**. `ServerCommandMod` dispatches
to `handle` through a virtual call, not a javassist string. Only classes named inside an injected code
string must stay lambda-free (see [private-class-techniques](specs/private-class-techniques.md)).

## See also

- [Getting started](getting-started.md) — the minimal first mod.
- [Component reference](components/) · [Component catalog](component-catalog.md).
