# Client-source specs

Deep, source-cited reference on the Wurm Unlimited **client**, distilled from the decompile for
client-mod work. These are the "how does the client actually work / how do I reach X" companions to the
[component reference](../components/) (which documents the SDK's own classes). Line numbers in these
specs drift across client versions — treat method/field **names** as stable, lines as approximate, and
re-verify against `../../../Decompiled/Client_vf/` before relying on one.

## The specs

| Spec | Covers |
|------|--------|
| [client-gui-internals](client-gui-internals.md) | The GUI system: render/input model, `WurmTreeList` cell rendering + hit-testing, every widget's biting nuances, HUD/fonts/drag/layout, and a catalog of more widgets (§6). The map of the whole `renderer.gui` package. |
| [custom-rendering](custom-rendering.md) | Drawing your own graphics in a `WurmComponent` — `fillRect`/`drawTexture`/text, the raw-`Primitive` path, scissor clipping, patching a vanilla `renderComponent`. |
| [private-class-techniques](private-class-techniques.md) | The decision tree for restricted client code: in-package subclass → wrapper → public alternative → bytecode → reflection. Plus the **invokedynamic landmine** and the **offline patch-verification** harness/recipe. |
| [keybindings-and-input](keybindings-and-input.md) | The key-dispatch path and how a mod binds a key, intercepts a keypress, or runs its own code from one (patch `WurmConsole.handleInput2`). |
| [game-state-access](game-state-access.md) | How a mod **gets a `World` reference** (`ModClient.getWorld()`), and reads player / hovered / inventory / connection state. The game-thread rule. |
| [notifications-and-sound](notifications-and-sound.md) | Telling the player: chat/event-tab lines (`hud.textMessage`), center popups, window flash, and playing sounds. Reading incoming chat (hook the receive path). |
| [bml-dialogs](bml-dialogs.md) | Declarative server-style forms (`BmlWindowComponent`) for quick modal prompts — the alternative to hand-composed widgets. |

## "I want to…" → spec

- **Draw a custom widget / custom pixels** → [custom-rendering](custom-rendering.md) + [client-gui-internals](client-gui-internals.md) §1–2.
- **Build a window** → [`KitTabbedWindow`](../components/KitTabbedWindow.md) (rich) or [bml-dialogs](bml-dialogs.md) (quick prompt); layout rules in [client-gui-internals](client-gui-internals.md) §3–4.
- **Show a list of server items** → [`KitTreeItem`](../components/KitTreeItem.md) fed via the inventory listener in [game-state-access](game-state-access.md) §5.
- **Subclass/patch a package-private or `final` client class** → [private-class-techniques](private-class-techniques.md) (and verify any javassist patch with its offline harness).
- **Bind a key / react to a key** → [keybindings-and-input](keybindings-and-input.md).
- **Read the player / world / hovered target / connection** → [game-state-access](game-state-access.md).
- **Notify the user (chat line, popup, sound)** → [notifications-and-sound](notifications-and-sound.md).
- **Talk to the server over a custom protocol** → [`ServerCommandMod`](../components/ServerCommandMod.md) + [wire-format](../concepts/wire-format.md) (don't hand-roll it).

## Scope note

These document the **client**; the SDK components wrap the reusable subset. For the broader project's
encyclopedic client reference (item model field-by-field, asset/model pipeline, ServerPacks) see the
project's separate client-modding reference — these specs deliberately cross-reference rather than
duplicate it.
