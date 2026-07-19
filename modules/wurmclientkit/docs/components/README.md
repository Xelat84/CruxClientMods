# Component reference

One file per public class in the SDK. Read the [concepts](../concepts/) first if you're new — the
components assume you understand the [package-private constraint](../concepts/package-private-constraint.md)
and the [wire format](../concepts/wire-format.md).

## Messaging (`com.wurmonline.clientkit`)

- **[`ServerCommandMod`](ServerCommandMod.md)** — abstract mod base: route one custom command byte,
  send raw payloads. Multi-mod safe.
- **[`PacketWriter` / `PacketReader`](PacketCodec.md)** — the matched big-endian codec.

## Formatting (`com.wurmonline.clientkit`)

- **[`Format`](Format.md)** — `coin`, `weight`, `duration`, `timeLeft`, `ql`. Pure logic.
- **[`GuiPatches`](GuiPatches.md)** — opt-in javassist hardening for vanilla widgets (call in `preInit`).
- **[`Hooks`](Hooks.md)** — HookManager/javassist boilerplate helpers for `preInit` patching.

## GUI (`com.wurmonline.client.renderer.gui`)

- **[`KitInputField`](KitInputField.md)** — self-managing form input (no prompt, no Enter-clear, typed reads).
- **[`KitDropDown`](KitDropDown.md)** — `WurmDropDown` with an `onChange` callback.
- **[`ValueLineBox`](ValueLineBox.md)** — collapsing info panel (optional header + non-blank lines).
- **[`SegmentedButtons`](SegmentedButtons.md)** — exactly-one-active mode toggle.
- **[`PagerRow`](PagerRow.md)** — centered pager bound to a (page, totalPages) model.
- **[`Collapsible`](Collapsible.md)** — position-stable show/hide wrapper.
- **[`WPopupBuilder`](WPopupBuilder.md)** — fluent `WurmPopup` context-menu builder.
- **[`KitTabPanel`](KitTabPanel.md)** — reusable real-tab container (incl. `KitTab`) for any window slot.
- **[`KitTabbedWindow`](KitTabbedWindow.md)** — a `WWindow` (title bar kept) with a real tab strip.
- **[`KitTreeItem`](KitTreeItem.md)** — a `TreeListItem` row base for `WurmTreeList`.
- **[`KitItemList`](KitItemList.md)** — embeddable real item-container tree (live items, native drag + right-click).
- **[`KitWindow`](KitWindow.md)** — a titled window with a mixable content area (item list + other widgets).
- **[`GuiKit`](GuiKit.md)** — widget factories: `input`, `icon`, `centered`, `hbox`/`vbox`, `row`, `spacer`.

For the at-a-glance state of every component (dependencies, stability, origin mod) see the
[component catalog](../component-catalog.md).

---

## Adding a new component doc

When the SDK grows a component, add its reference here with this skeleton so the docs stay uniform:

```markdown
# `ClassName`

`fully.qualified.ClassName` — one-line purpose. Which package and why (if gui-package, say so).
Modifiers (final / abstract / interface).

## What it does / API
(table of methods or the abstract contract)

## Usage
(a minimal, real example)

## Notes & gotchas
(the non-obvious bits — bullet list)

## Source
`src/main/java/.../ClassName.java`

## See also
(links to related concepts/components)
```

Then: add a row to the [component catalog](../component-catalog.md), a table row to the top-level
[`README.md`](../../README.md), and a bullet here.
