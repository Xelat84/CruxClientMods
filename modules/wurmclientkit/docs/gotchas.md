# Gotchas

The hard-won list. Every item here cost someone (usually while building AuctionHouse) an afternoon.
Skim it before you start; return to it when something behaves impossibly.

## Infrastructure

### `ClassClassPath` / `NoClassDefFoundError`

The patched vanilla method `reallyHandle` references `com.wurmonline.clientkit.ServerCommandMod`. Your
mod runs on a private classloader whose jar isn't on the HookManager Loader's class pool, so without
help the patched vanilla class fails to **define** at runtime — a `NoClassDefFoundError`, not a
compile error. `ServerCommandMod.preInit()` fixes this with
`pool.appendClassPath(new ClassClassPath(getClass()))`. **Done for you in every `ServerCommandMod`
subclass.** A pure-GUI mod that doesn't extend it must do the append itself. Full story:
[hooking model](concepts/hooking-model.md).

### Package-private widgets → your GUI classes MUST live in `com.wurmonline.client.renderer.gui`

`WurmTreeList`, `WurmInputField`, `WurmDecorator`, `WWindow`, `WurmBorderPanel`, `WurmArrayPanel`, … are
package-private. A window using them only compiles **and** runs if declared in package
`com.wurmonline.client.renderer.gui`. The `ClassClassPath` append also puts those classes on the same
loader as `WWindow`, which is what makes the package-private access legal at runtime. Full story:
[the package-private constraint](concepts/package-private-constraint.md).

### Don't override `preInit` without `super.preInit()`

`ServerCommandMod.preInit()` installs the shared patch and registers your command id. Skip it and your
mod silently receives nothing.

### Command ids must be unique across all installed mods

Duplicate ids are refused at registration with a warning; the loser is dead on that opcode. Coordinate
opcodes. AuctionHouse owns `−67`. See [multi-mod coexistence](concepts/multi-mod-coexistence.md).

## Wire format

### Read large ids/counts with `getUnsignedShort()`, not `getShort()`

Image numbers, counts, and ids above 32767 sign-extend to negative under `getShort()`. This is the
single most common icon/desync bug. See [wire format](concepts/wire-format.md).

### Keep writer and reader field order identical

There's no per-field type tag — the reader must read exactly what the writer wrote, in order. A shifted
read is silent garbage, not an error. Debugging checklist in [wire format](concepts/wire-format.md).

### `send` payloads must lead with the command byte

Build them `new PacketWriter(commandId())…`. A payload without the command byte can't be routed.

## GUI idioms

### `WurmTreeList` column 0 is implicit

The `String[]` you pass a row's `columns` (via `KitTreeItem`) lists the **EXTRA** columns only —
`getParameter(0)` is the first extra. Don't add "Name" to the row's extras. (The tree *constructor's*
width/name arrays do count column 0 — keep the two models straight; see
[`KitTreeItem`](components/KitTreeItem.md).)

### `WurmInputField.prompt` defaults to `"] "`

Set `field.prompt = ""` after constructing, or every input box shows a stray bracket. Or just use
[`GuiKit.input`](components/GuiKit.md), which clears it.

### Centering a row

Wrap the component in a `WurmDecorator` with `align = ALIGN_CENTER` and place it in a **border-panel
slot** (which stretches). Array panels shrink-wrap their children, so centering inside one does
nothing. This is the vanilla `ConfirmWindow` button trick — packaged as
[`GuiKit.centered`](components/GuiKit.md).

### Item icons need the IMAGE NUMBER, not the template id

`IconLoader.getIcon((short) imageNumber)` where `imageNumber` is the template's image number, which the
**server must send** — the client has no template→image registry for arbitrary templates. Passing the
template id gives the wrong or a missing icon. [`GuiKit.icon`](components/GuiKit.md) wraps it safely.

### `KitTreeItem` columns sort lexically unless you give them a numeric key

Without a sort key, a column sorts by its formatted string, so numeric-looking columns sort textually
(`"100" < "9"`). Supply a numeric `sortKeys` array (or override `sortKey(int)`) on your row so that
column sorts by its raw value — see [`KitTreeItem`](components/KitTreeItem.md#sorting).

### Window X does nothing

Override `protected void closePressed()`. [`KitTabbedWindow`](components/KitTabbedWindow.md) already
wires it to hide the window.

### Tabs aren't reusable from vanilla

`WurmTabbedWindow`/`WurmTabPanel` are package-private and chat-coupled. Use
[`KitTabbedWindow`](components/KitTabbedWindow.md).

### `WurmDropDown` has no change listener

Vanilla `WurmDropDown` fires nothing on selection — you'd have to poll `getValue()` in `gameTick`. Use
[`KitDropDown`](components/KitDropDown.md) instead: it subclasses `WurmDropDown` and adds an `onChange`
callback (this gotcha is already solved for you).

### Bulk-item counts live in the description field

Not a dedicated count field. Read the count out of the description string when rendering bulk rows.

### Server-only items aren't natively draggable onto real containers

The vanilla drop handlers only accept real `InventoryMetaItem`s. To make custom items draggable to real
containers, model them as a real openable container (the bank/vault pattern), not a custom tree.

## See also

- [Concepts](concepts/) — the "why" behind each infrastructure gotcha.
- Workspace `ClientToolkitReference.md` — the exhaustive client-side gotcha list.
