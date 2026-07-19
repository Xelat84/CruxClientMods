# Component catalog

The at-a-glance state of the SDK. This is the **state tracker** — when a component is added, changed,
or promoted, update the row here (and its [component doc](components/)). It's the fastest way to see
what already exists before building something new.

_Last synced: post-loop — added the tabs framework (`KitTab`, `KitTabPanel`, `KitTabbedWindow` refactor). Full component set (see table). Later iterations added a worked example
(`examples/`), the `PatchVerify` tool, the custom-rendering spec, and a consistency pass. See
[ui-component-backlog.md](ui-component-backlog.md) for the queue/changelog._

## Legend

- **Package**: `clientkit` = `com.wurmonline.clientkit` (logic, no client deps at compile time except
  the widget-free ones) · `gui` = `com.wurmonline.client.renderer.gui`
  ([why](concepts/package-private-constraint.md)).
- **Stability**: `stable` = shipped in a real mod, API unlikely to change · `evolving` = in use but
  API may grow.
- **Origin**: the mod the component was distilled from.

## Components

| Component | Package | Kind | Purpose | Deps | Stability | Origin |
|-----------|---------|------|---------|------|-----------|--------|
| [`ServerCommandMod`](components/ServerCommandMod.md) | clientkit | abstract class | Custom command routing + send; multi-mod safe | modloader, javassist, `SimpleServerConnectionClass` | stable | AuctionHouse |
| [`PacketWriter`](components/PacketCodec.md) | clientkit | final class | Big-endian encode | none | stable | AuctionHouse |
| [`PacketReader`](components/PacketCodec.md) | clientkit | final class | Big-endian decode | `ByteBuffer` | stable | AuctionHouse |
| [`Format`](components/Format.md) | clientkit | final static | Coin/weight/duration/ql display strings | none | stable | AuctionHouse |
| [`GuiPatches`](components/GuiPatches.md) | clientkit | final static | Opt-in javassist patches: WurmInputField Down-arrow fix + WurmTreeList cell-button dispatch | modloader, javassist, `WurmInputField`, `WurmTreeList` | stable | KitInputField review / cell-button feature |
| [`Hooks`](components/Hooks.md) | clientkit | final static | HookManager/javassist boilerplate: `get`/`method`/`appendClassPath`/`edit`/`replaceCall` | modloader, javassist | stable | 7-site idiom (mods + SDK) |
| [`KitInputField`](components/KitInputField.md) | gui | final class | Self-managing form input: no prompt, no Enter-clear, committed value + typed accessors + callbacks | `WurmInputField`, `InputFieldListener` | stable | AuctionHouse/BuyOrder survey |
| [`KitDropDown`](components/KitDropDown.md) | gui | class (extends `WurmDropDown`) | Dropdown with an `onChange` callback (no more polling) | `WurmDropDown` | stable | AuctionHouse/BuyOrder survey |
| [`ValueLineBox`](components/ValueLineBox.md) | gui | final class | Collapsing info panel: optional header + non-blank lines only, hidden when empty | `WurmArrayPanel`, `WurmLabel` | stable | AuctionPricingBox generalization |
| [`SegmentedButtons`](components/SegmentedButtons.md) | gui | final class | Exactly-one-active mode toggle (pressed-look active) + `onSelect` | `WButton`, `WurmArrayPanel`, `ButtonListener` | stable | AuctionHouse/BuyOrder survey |
| [`PagerRow`](components/PagerRow.md) | gui | final class | Centered `\|< < Page X of Y > >\|` pager bound to (page, total) + `onPage` | `WButton`, `WurmLabel`, `WurmArrayPanel`, `WurmDecorator`, `WurmPanel` | stable | AuctionHouse pager |
| [`Collapsible`](components/Collapsible.md) | gui | final class | Position-stable show/hide wrapper (slot stays, child toggles) | `WurmArrayPanel` | stable | AuctionHouse setPartialVisible |
| [`WPopupBuilder`](components/WPopupBuilder.md) | gui | final class | Fluent `WurmPopup` context-menu builder (button/label/separator/show) | `WurmPopup`, `HeadsUpDisplay` | stable | RecipeExamine popup injection |
| [`KitTabPanel`](components/KitTabPanel.md) | gui | class (extends `WurmBorderPanel`) | Reusable tab container (real tab skin) droppable in any slot; tab strip + swapped content | `WurmBorderPanel`, `WurmArrayPanel`, `KitTab` | stable | tabs-in-a-window request |
| [`KitTab`](components/KitTabPanel.md) | gui | final class (extends `WButton`) | Real Wurm tab-shaped header (vanilla `panelTexture` skin, no chat coupling) | `WButton`, `panelTexture`, `TextFont` | stable | tabs-in-a-window request |
| [`KitTabbedWindow`](components/KitTabbedWindow.md) | gui | class (extends `WWindow`) | Window keeping its title bar + real tab strip; delegates to `KitTabPanel` | `WWindow`, `KitTabPanel` | stable | AuctionHouse |
| [`KitTreeItem`](components/KitTreeItem.md) | gui | abstract (extends `TreeListItem`) | `WurmTreeList` row base: name+icon+columns, container/expand rows, numeric-column sort | `TreeListItem`, `GuiKit` | stable | AuctionHouse |
| [`KitItemList`](components/KitItemList.md) | gui | final class | Embeddable **real item-container tree** (icons, columns, folded stacks, live updates, native drag + right-click actions) over an `InventoryMetaWindowView`; view lookups | `InventoryListComponent`, `InventoryMetaWindowView`, `InventoryMetaWindowManager` | stable | mixed item-window request |
| [`KitWindow`](components/KitWindow.md) | gui | class (extends `WWindow`) | Titled window with a **mixable** 5-region content area — item list in one region, other widgets in the rest (vanilla item windows are items-only) | `WWindow`, `WurmBorderPanel` | stable | mixed item-window request |
| [`GuiKit`](components/GuiKit.md) | gui | final static | Widget factories (input/icon/centered/hbox/vbox/row-gap/spacer) | `WurmInputField`, `IconLoader`, `WurmDecorator`, `WurmArrayPanel`, `WurmPanel` | stable | AuctionHouse |

## Shared runtime patches (installed by `ServerCommandMod`)

| Patch | Target | Scope | Notes |
|-------|--------|-------|-------|
| `reallyHandle` peek/dispatch | `SimpleServerConnectionClass.reallyHandle(int, ByteBuffer)` | once | non-consuming peek; routes owned opcodes before the vanilla switch |
| `wckSend(byte[])` injection | `SimpleServerConnectionClass` | once | wraps private `getBuffer()` + `reallySend()`; called reflectively |
| `ClassClassPath` append | HookManager class pool | per subclass | fixes `NoClassDefFoundError` + gui-package loader |

See [hooking model](concepts/hooking-model.md) and [multi-mod coexistence](concepts/multi-mod-coexistence.md).

## Build facts

| Fact | Value |
|------|-------|
| Artifact | `org.gotti.wurmunlimited:wurmclientkit:0.1` |
| Output jar | `target/wurmclientkit-0.1.jar` |
| Java target | 1.8 |
| Runtime deps | `client-modlauncher:0.15` |
| Provided deps | `client:3721782`, `common-client:3721782` |
| Entry point | none (library) |

## Coverage snapshot

What the SDK covers today vs. what mods still hand-roll:

- **Covered:** custom-command protocol plumbing, big-endian codec, display formatting, tabbed window,
  tree-list rows (incl. containers, numeric sort, cell buttons), form inputs, dropdown change events,
  collapsing info panels, segmented toggles, pager, show/hide, popup builder, hook boilerplate, opt-in
  widget-hardening patches, the common widget factories.
- **Not yet covered (hand-rolled per mod):**
  tree selection change-callback, BML dialog helper, drag/drop target resolution,
  console-command registration, config/properties reading, window persistence. See the
  [UI component backlog](ui-component-backlog.md) (ranked queue) and [roadmap](roadmap.md).
