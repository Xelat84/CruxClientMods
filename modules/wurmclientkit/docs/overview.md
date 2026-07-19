# Overview

WurmClientKit is a **library of reusable building blocks for Wurm Unlimited client mods**. Every mod
that wants to talk to the server over a custom protocol, or draw its own window with the game's GUI
widgets, has to re-derive the same handful of non-obvious things: the javassist hook that intercepts
inbound packets, the classloader trick that lets a patched vanilla method reference mod code, the
`com.wurmonline.client.renderer.gui` package requirement, the big-endian wire encoding. This SDK
solves each of those *once* and exposes a clean API on top.

It was extracted from the **AuctionHouse** mod — the first mod complex enough to need a full custom
protocol plus a multi-tab window with tree lists, icons, and drag targets. The plumbing that made
AuctionHouse work is what lives here, generalized so a second and third mod can reuse it.

## Design philosophy

1. **Distill proven code, don't speculate.** A component earns its place in the SDK only after a real
   mod has needed it. AuctionHouse produced the first wave; every future component should trace back
   to a concrete use in a shipping mod. (The [roadmap](roadmap.md) lists candidates, but candidates
   are not components until something uses them.)
2. **Hide the plumbing, not the widgets.** The SDK owns the *infrastructure* (hooks, classloader,
   wire format, tab machinery). It does **not** wrap every vanilla widget or invent a new GUI
   framework — mods still compose `WWindow`/`WurmBorderPanel`/`WurmArrayPanel` directly. The SDK adds
   a helper only where the vanilla API is missing, hostile, or repeatedly gets a detail wrong.
3. **Multi-mod safe by construction.** More than one mod using the SDK must coexist in the same
   client. The shared patches install exactly once; per-mod state is keyed by command id. See
   [multi-mod coexistence](concepts/multi-mod-coexistence.md).
4. **Fail loud, degrade never (for required plumbing).** A missing hook or duplicate command id is a
   logged warning or a thrown `HookException`, not a silent no-op that leaves the mod half-wired.
5. **Match vanilla names and semantics.** Wire encoding mirrors the vanilla `ByteBuffer` format
   (big-endian, short-length-prefixed strings) so both ends stay in lockstep with the server.

## Package layout

```
com.wurmonline.clientkit                 (logic — lives in the mod's own namespace)
  ServerCommandMod       custom command routing + send
  PacketWriter           big-endian encode
  PacketReader           big-endian decode
  Format                 display-string helpers (no client deps)
  Hooks                  HookManager/javassist boilerplate helpers
  GuiPatches             opt-in javassist widget-hardening patches

com.wurmonline.client.renderer.gui       (GUI helpers — MUST live in the vanilla gui package)
  KitTabbedWindow        WWindow with real tabs
  KitTreeItem            TreeListItem row base (containers, numeric sort, cell buttons)
  KitInputField          form input wrapper
  KitDropDown            dropdown with onChange
  SegmentedButtons       exactly-one-active toggle
  PagerRow               |< < Page X of Y > >| pager
  ValueLineBox           collapsing info panel
  Collapsible            position-stable show/hide wrapper
  WPopupBuilder          WurmPopup context-menu builder
  GuiKit                 widget factories (input/icon/centered/hbox/vbox/row/spacer)
```

Why the split? The GUI classes touch **package-private** vanilla widgets, so they can only compile
and run inside `com.wurmonline.client.renderer.gui`. The logic classes have no such constraint and
sit in the SDK's own `clientkit` namespace. See
[the package-private constraint](concepts/package-private-constraint.md) for the full explanation.

## How the pieces fit together

A typical SDK-based mod looks like this:

```
MyMod extends ServerCommandMod          ← lifecycle + protocol entry point
  ├─ commandId() = -70                  ← your unique opcode
  ├─ handle(conn, buf)                  ← inbound: decode with PacketReader, update GUI
  └─ sendX(...)                         ← outbound: build with PacketWriter, send(conn, bytes)

MyWindow extends KitTabbedWindow        ← your GUI (in the gui package)
  ├─ addTab("Browse", browsePanel)
  └─ rows extend KitTreeItem            ← WurmTreeList content
       └─ GuiKit.icon(imageNumber)      ← icons, inputs, centering
Format.coin/weight/ql(...)              ← render numbers as the game does
```

The mod class handles the network + lifecycle; the window class handles presentation; `Format` and
`GuiKit` are leaf utilities used by both. Nothing in the SDK reaches back into your mod — you pull
from it, it never pushes into you.

## What the SDK is *not*

- **Not a runnable mod.** No entry point; it's a dependency. See [getting started](getting-started.md).
- **Not a BML renderer.** The client already renders BML (`BmlWindowComponent`); the SDK doesn't
  duplicate that. Use BML directly for quick server-style forms.
- **Not a general javassist tutorial.** The [hooking model](concepts/hooking-model.md) doc explains
  what the SDK does; for open-ended hooking, see the workspace `ClientToolkitReference.md`.
- **Not per-mod business logic.** Category trees, price math, auction state — those stay in the mod.

## Relationship to the workspace reference

`ClientToolkitReference.md` is the *encyclopedic* client-modding reference —
it documents the whole client surface (every widget, the item model, resources, console, actions,
hooks). This SDK is the *code* for the reusable subset of that knowledge. When the reference says
"WurmClientKit ships this as X," this docs tree is where X is documented in depth.
