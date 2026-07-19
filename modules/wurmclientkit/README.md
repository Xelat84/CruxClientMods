# WurmClientKit

Reusable client-mod SDK for Wurm Unlimited. It extracts the proven plumbing from real mods
(AuctionHouse first) so a new mod can define a custom server↔client command and its own GUI
**without** re-deriving the javassist hooks, the classloader gotcha, or the big-endian wire format.

> **This is a library, not a runnable mod.** It has no `classname` entry point. A dependent mod
> either shades this jar into its own or ships `wurmclientkit.jar` alongside and lists it on
> `classpath=` in its `.properties`.

## What's in the box

| Area | Class | One-liner |
|------|-------|-----------|
| Messaging | [`ServerCommandMod`](docs/components/ServerCommandMod.md) | Abstract base — route ONE custom command byte, send raw payloads back. Multi-mod safe. |
| Messaging | [`PacketWriter`](docs/components/PacketCodec.md) | Growable big-endian byte builder (encode side). |
| Messaging | [`PacketReader`](docs/components/PacketCodec.md) | `ByteBuffer` decode wrapper (decode side). |
| Formatting | [`Format`](docs/components/Format.md) | `coin` / `weight` / `duration` / `timeLeft` / `ql` display strings. Pure logic. |
| GUI | [`KitInputField`](docs/components/KitInputField.md) | Form input wrapper — no `"] "` prompt, no Enter-clear, committed value + typed reads + callbacks. |
| GUI | [`KitDropDown`](docs/components/KitDropDown.md) | `WurmDropDown` with an `onChange` callback (no polling). |
| GUI | [`ValueLineBox`](docs/components/ValueLineBox.md) | Collapsing info panel — optional header + non-blank lines, hidden when empty. |
| GUI | [`SegmentedButtons`](docs/components/SegmentedButtons.md) | Exactly-one-active mode toggle with an `onSelect` callback. |
| GUI | [`PagerRow`](docs/components/PagerRow.md) | Centered `\|< < Page X of Y > >\|` pager bound to a page model. |
| GUI | [`Collapsible`](docs/components/Collapsible.md) | Position-stable show/hide wrapper (child toggles, slot keeps its place). |
| GUI | [`WPopupBuilder`](docs/components/WPopupBuilder.md) | Fluent `WurmPopup` context-menu builder (button/label/separator/show). |
| Infra | [`GuiPatches`](docs/components/GuiPatches.md) | Opt-in javassist hardening for vanilla widgets (call in `preInit`). |
| Infra | [`Hooks`](docs/components/Hooks.md) | HookManager/javassist boilerplate helpers for `preInit` patching. |
| GUI | [`KitTabPanel`](docs/components/KitTabPanel.md) | Reusable **real-tab** container (`KitTab` headers) droppable into any window slot. |
| GUI | [`KitTabbedWindow`](docs/components/KitTabbedWindow.md) | A `WWindow` keeping its **title bar** + a real tab strip (delegates to `KitTabPanel`). |
| GUI | [`KitTreeItem`](docs/components/KitTreeItem.md) | A `TreeListItem` base for `WurmTreeList` rows (name + icon + string columns). |
| GUI | [`GuiKit`](docs/components/GuiKit.md) | Static factories: `input`, `icon`, `centered`, `hbox`/`vbox`, `row`, `spacer`. |

Two packages: `com.wurmonline.clientkit` (logic) and `com.wurmonline.client.renderer.gui`
(GUI helpers — they **must** live there for package-private widget access; see
[the package-private constraint](docs/concepts/package-private-constraint.md)).

## Documentation map

Start here, then drill in:

- **[Overview](docs/overview.md)** — what the SDK is, its philosophy, and how it's organized.
- **[Getting started](docs/getting-started.md)** — building the jar, wiring it into a mod, the two consumption models.
- **[Worked example](docs/example-mod.md)** — a compilable reference mod (`examples/`) composing the whole SDK surface.
- **Concepts** (read before writing anything non-trivial):
  - [The package-private constraint](docs/concepts/package-private-constraint.md) — why your GUI classes live in `com.wurmonline.client.renderer.gui`.
  - [The hooking model](docs/concepts/hooking-model.md) — how `ServerCommandMod` patches the vanilla connection class, and the `ClassClassPath` gotcha.
  - [Multi-mod coexistence](docs/concepts/multi-mod-coexistence.md) — how N mods share one patch and route by command id.
  - [Wire format](docs/concepts/wire-format.md) — big-endian, short-length-prefixed UTF-8, keep both ends in lockstep.
- **Component reference** — one file per public class under [`docs/components/`](docs/components/).
- **[Gotchas](docs/gotchas.md)** — the hard-won list. Do not skip.
- **Specs** ([index](docs/specs/README.md)) — deep client-source reference (source of truth for building components):
  - [Client GUI internals](docs/specs/client-gui-internals.md) — rendering model, `WurmTreeList`, widget nuances, HUD, drag/drop, fonts.
  - [Private-class techniques](docs/specs/private-class-techniques.md) — the decision tree for subclassing/wrapping/patching restricted client code.
  - [Custom rendering](docs/specs/custom-rendering.md) — drawing quads/textures/lines/text in a `WurmComponent` (`Queue`/`Primitive`/matrix APIs).
  - [Keybindings & input](docs/specs/keybindings-and-input.md) — the key-dispatch path and how a mod binds/intercepts keys or runs code from one.
  - [Game-state access](docs/specs/game-state-access.md) — how a mod gets a `World` reference and reads player/hovered/inventory/connection state (+ threading).
  - [Notifications & sound](docs/specs/notifications-and-sound.md) — chat/event-tab lines, center popups, window flash, and playing sounds.
  - [BML dialogs](docs/specs/bml-dialogs.md) — declarative server-style forms (`BmlWindowComponent`) for quick modal prompts.
- **[Component catalog](docs/component-catalog.md)** — the state tracker: what exists, what it depends on, what's stable.
- **[UI component backlog](docs/ui-component-backlog.md)** — the ranked queue of components being built.
- **[Roadmap](docs/roadmap.md)** — candidate components and gaps the SDK should grow into.

For the broader client-modding picture (windows, tabs, layout, items, ModComm, resources, console,
actions, hooks, and every gotcha), see the workspace reference
`ClientToolkitReference.md`. This SDK is the *distilled, reusable code*
behind that reference.

## 60-second quickstart

```java
package com.example.mymod;

import com.wurmonline.clientkit.PacketReader;
import com.wurmonline.clientkit.PacketWriter;
import com.wurmonline.clientkit.ServerCommandMod;

public class MyMod extends ServerCommandMod {
    private static final byte CMD = -70;                 // must be unique across all installed mods
    private static Object conn;

    @Override protected byte commandId() { return CMD; }

    @Override protected void handle(Object connection, java.nio.ByteBuffer bb) {
        conn = connection;                                // remember it so we can reply later
        PacketReader r = new PacketReader(bb);            // positioned just after the CMD byte
        byte sub = r.get();
        String text = r.readString();
        // ... update your window / state on the client (game) thread ...
    }

    public void sendHello(String name) {                 // build a reply; first byte is the CMD
        send(conn, new PacketWriter(CMD).put((byte) 1).putString(name).bytes());
    }
}
```

Register it like any client mod: a `mods/mymod.properties` with `classname=com.example.mymod.MyMod`
(plus `classpath=` listing your jar and, unless you shade it, `wurmclientkit.jar`). Full walkthrough
in [Getting started](docs/getting-started.md).

## Building

This kit is the `wurmclientkit` module of the CruxClientMod reactor and is shaded into
`crux-clientmod.jar`. Build the whole reactor from the repo root with a JDK 8 toolchain:

```
mvn clean install -DskipTests
```

The build is self-contained (no external hosts). Details in
[Getting started](docs/getting-started.md).
