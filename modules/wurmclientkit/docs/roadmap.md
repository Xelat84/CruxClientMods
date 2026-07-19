# Roadmap

Candidate components the SDK could grow, and the principle for adding them. This is the working list
the autonomous "grow the SDK" task draws from — but a candidate becomes a component only when a real
mod needs it (see [overview](overview.md) design philosophy #1).

## The bar for promotion

Promote a helper into the SDK when **all** of these hold:

1. **It recurs.** Two or more mods (existing or clearly planned) hand-roll the same idiom.
2. **It's infrastructure, not business logic.** Plumbing, wire format, widget wiring — not
   auction-specific or recipe-specific behavior.
3. **The vanilla API is missing, hostile, or repeatedly misused.** If vanilla already does it cleanly,
   don't wrap it.
4. **It's multi-mod safe.** Any shared static state is keyed so N mods coexist.

If a helper only ever serves one mod, it stays in that mod.

## Candidates (grounded in existing mods)

Each is annotated with where the recurring pattern already appears, so the need is verifiable, not
speculative.

### Hooking helpers

> **Status note:** several items below have since shipped or been assessed — see
> [component-catalog.md](component-catalog.md) and the [backlog](ui-component-backlog.md) for the
> authoritative state. Kept here with their rationale and marked inline.

- **Reflection-hook base / helper** — a thin wrapper over
  `HookManager.registerHook(class, method, sig, factory)`. **⤫ Assessed below-bar (iter-12):** the only
  `registerHook` consumers are the vanilla forks (custommap, serverpacks); the user's mods use direct
  bytecode, already served by [`Hooks`](components/Hooks.md).
- **Bytecode-edit helper** — **✅ Shipped as [`Hooks`](components/Hooks.md)** (`get`/`method`/`edit`/
  `replaceCall` over the `getClassPool().get(...).instrument(ExprEditor)` / `insertBefore` boilerplate).

### Messaging

- **ModComm channel base** — the negotiated-channel counterpart to `ServerCommandMod`. **⤫ Assessed
  below-bar (iter-12):** only the serverpacks vanilla fork uses ModComm; every user mod uses raw CMD via
  `ServerCommandMod`. Revisit if a user mod needs a negotiated channel.

### GUI

- **`WurmPopup` builder** — **✅ Shipped as [`WPopupBuilder`](components/WPopupBuilder.md)** (button/label/
  separator/show without the package-private inner-class boilerplate).
- **`WurmDropDown` change-listener** — **✅ Shipped as [`KitDropDown`](components/KitDropDown.md)** (subclass
  overriding `setValue` to fire `onChange`).
- **BML dialog helper** — a small wrapper over `BmlWindowComponent` for quick server-style forms with
  a typed result map. *Vanilla renders BML already* — only worth it if the boilerplate recurs.
- **Drag/drop source wiring** — a `DraggableComponent` helper for tree rows
  (`getIcon`/`getIconSize`/`getHoverDescription` + `hud.startDrag`). *Seen in:* AuctionHouse rows.
  Remember: server-only items can't drop on real containers (model as a real container instead).
- **Themable tab colors** — `KitTabbedWindow` hardcodes the active-tab gold; expose colors if a second
  mod wants a different look.

### Lifecycle & polling

- **`gameTick` polling helper** — a registration point for per-tick callbacks (poll dropdowns, refresh
  timers) without each widget overriding `gameTick`. *Recurs wherever* a value has no change event.
- **`Configurable` helper** — typed reads over the `.properties` descriptor
  (`configure(Properties)`), with defaults. *Interface available:*
  `org.gotti.wurmunlimited.modloader.interfaces.Configurable`; sample in ClientModExample.
- **Window persistence** — remember open/closed state and position across sessions (`WWindow` has a
  `rememberOpen` ctor flag; wrap the bookkeeping).

### Console & actions

- **Console-command registration** — a base over `ModConsole.addConsoleListener(ConsoleListener)` with
  a command-prefix router (return `true` to consume). *Available in:*
  `org.gotti.wurmunlimited.modsupport.console.*`. Handy for debug/config commands (DirectConnect-style
  entry points).
- **`sendAction` helper** — wrap `SimpleServerConnectionClass.sendAction(source, targets[], PlayerAction)`
  (opcode 97). Note action ids are **server-defined** — a mod can only trigger ids the server knows.

## Non-goals (explicitly out of scope)

- **BML string authoring DSL** — the client renders BML; a Java DSL to build the string is over-engineering.
- **A new GUI framework** — mods compose vanilla `WurmBorderPanel`/`WurmArrayPanel` directly; the SDK
  adds helpers, not an abstraction layer over layout.
- **General javassist tutorials** — belongs in the workspace `ClientToolkitReference.md`, not in code.
- **Per-mod business logic** — category trees, price math, recipe parsing stay in their mods.

## When adding a component

Follow the checklist in [`components/README.md`](components/README.md): write the reference doc from
the skeleton, add a catalog row, add a table row to the top-level README, and cross-link the relevant
concept/gotcha. Keep the [component catalog](component-catalog.md) "Coverage snapshot" honest.
