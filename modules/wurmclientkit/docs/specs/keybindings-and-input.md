# Spec: keybindings & keyboard input

How the client turns a keypress into an action, and the concrete ways a mod can **bind a key**,
**intercept a key**, or **run its own code from a key**. The workspace reference punts on this ("no
public API to register bindings"); this documents the actual mechanism. Verified against
`../../../Decompiled/Client_vf/client/com/wurmonline/client/` — class+method cited; line numbers rot,
re-verify before relying on one.

> **The one insight that makes an entry point exist:** a "keybind" is literally a stored **console
> `bind` command**. The keybinding registry *is* the console (`WurmConsole`). So the keybind path and
> the console-command path are the same path — which is where a mod hooks in.

## The two cooperating systems

1. **Raw input pump + GUI focus** — `WurmEventHandler` polls LWJGL every frame and **broadcasts** each
   event to a list of `WurmEventListener`s. `HeadsUpDisplay` is inserted at **index 0**, so it sees keys
   first and decides "is a text field focused (typing) or is this a gameplay key?".
2. **Keybinding registry** — `com.wurmonline.client.console.WurmConsole` owns
   `Map<Integer, KeyBinding> keyBinds` (meta-code → binding), loaded from `keybindings.txt`. A binding
   holds either an `ActionClass` (→ network action) or a raw console command string.

## Dispatch path (key → action)

```
LWJGL Keyboard.poll()  (WurmEventHandler.handleKeyEvents, key-repeat ENABLED)
  → keyPressed(code,chr)
      → handleMetaKeys()         // Shift/Ctrl/Alt SWALLOWED here → player.setShiftDown/… (never delivered)
      → for each listener: listener.keyPressed(...)   // broadcast; NO consumption (returns void)
          → HeadsUpDisplay.keyPressed:
                if (isTyping && !console.playerOverride(code))  kbFocusComponent.keyPressed(...)  // text field eats it
                else                                            console.toggleKey(code, true)     // GAMEPLAY
                     → getCurrentBinding(code)  // meta-code = code + modifier bits
                        ├─ binding.action → player.toggleKey(ActionClass) → sendHoveredAction(PlayerAction)  // network
                        └─ binding.strCommand → console.handleInput(cmd)   // re-enters the console parser
```

Key facts that shape what a mod can do:
- **No key consumption.** `WurmEventListener.keyPressed/keyTyped/keyReleased` all return `void` — every
  listener gets every key. (Only `mousePressed` returns a boolean.) You can *observe* but not *block* via
  the listener list.
- **Modifiers never arrive as keys** — they're swallowed into player state (`player.isShiftDown()` etc.)
  and re-encoded as bits in the binding meta-code (`SHIFT_BIT=65536`, `ALT_BIT=131072`,
  `CONTROL_BIT=262144`, mouse `BUTTON_CODE_OFFSET=4096`).
- **Key repeat is ON** (`Keyboard.enableRepeatEvents(true)`) — a held key fires repeated `keyPressed`.
  Debounce if you want edge-only behavior.
- **`WurmConsole.toggleKey(int, boolean)` is the single choke point** where a keycode becomes an
  action/command — the best place to intercept resolution.

## Accessors (both public)

```java
WurmConsole console = world.getClient().getConsole();          // World.getClient() → WurmClientBase.getConsole()
WurmEventHandler ev  = world.getClient().getEventHandler();    // getEventHandler() is public
```

## How a mod hooks it

### Bind a key to an existing command — no bytecode
`WurmConsole.handleInput(String, boolean)` is **public**. A binding to a quoted command string does not
require an `ActionClass`:

```java
console.handleInput("bind F5 \"toggle map\"", false);   // works today
console.handleInput("bind Mouse3 \"...\"", false);       // mouse buttons bind too (code+4096)
```
Limitation: the command must be one the console's `handleInput2` already understands (`toggle …`,
`say …`, `exec`, `screenshot`, …). There is **no public API to register a new console verb** — new verbs
need a bytecode patch (below).

### Run your OWN code from a key — patch `handleInput2`
Instrument `WurmConsole.handleInput2(String, boolean)` (private; hardcoded if/else ending in
`"Unknown command: …"`) to recognize a custom verb before the vanilla chain, then bind a key to it:

```java
// preInit, via Hooks:
Hooks.edit(() -> Hooks.method(Hooks.get("com.wurmonline.client.console.WurmConsole"), "handleInput2")
    .insertBefore("if ($1 != null && $1.startsWith(\"mymod\")) { com.example.MyMod.run($1); return; }"));
// then: console.handleInput("bind K \"mymod dostuff\"", false);
```
Now `K` → `toggleKey` → `handleInput("mymod dostuff")` → your code. (Keep the referenced class
lambda-free — see [private-class-techniques](private-class-techniques.md) invokedynamic rule.)

### Reach a SERVER-side mod command from a key — no client patch
Bind the key to a `say` of a slash-command; the server mod handles it:
```java
console.handleInput("bind K \"say /mymodcmd\"", false);
```
This is the standard escape hatch when your logic lives server-side.

### Observe every keypress — a `WurmEventListener`
Implement `com.wurmonline.client.WurmEventListener` and register it:
```java
world.getClient().getEventHandler().addListener(0, myListener);  // index 0 = before the HUD
```
Because events are broadcast with no consumption, your listener reliably sees every key. **Gotcha:**
`WurmEventHandler.addListener(...)` is **package-private** — call it from an in-package shim
(package `com.wurmonline.client`) or via reflection; `getEventHandler()` itself is public. You **cannot
block** vanilla handling this way — to suppress a key, hook `HeadsUpDisplay.keyPressed(int,char)` or
`WurmConsole.toggleKey(int,boolean)` instead.

## Gotchas

- **A focused text field steals keys.** While `HeadsUpDisplay.isTyping` (any `WurmInputField` has kb
  focus — chat, console, a form), the gameplay path is skipped and the field gets the key. Exceptions
  come through `console.playerOverride(code)` (hardcoded F1–F10 + tab/window nav).
- **Persist + refresh after a programmatic `bind`.** A non-silent vanilla `bind` also calls
  `WurmSettingsFX.saveAllKeybinds()` + `hud.updateBinds(false)`. If you mutate binds yourself, refresh
  similarly or the settings UI/popups go stale. Binds live in `keybindings.txt` (`Options.keybindingsSource`
  picks config-dir vs player-dir).
- **Free-look mode ignores modifier bits** in binding resolution.

## Hard limits (document, don't fight)

- **No public API for new console verbs** — patch `WurmConsole.handleInput2` (the exact method).
- **`ActionClass` and `PlayerKeybind` are closed enums** — new *actions* need bytecode; new *key→command*
  bindings do not.
- **`WurmEventHandler.addListener` is package-private** — in-package shim or reflection.
- **Key events have no consumption semantics** — observe freely; to block, hook the HUD/console choke points.
- Vanilla `ConsoleListenerClass` is **output-only** (console text mirror), not a command-interception hook.

## Would this become an SDK component?

Only if a real mod needs it (the promotion bar). A `KeybindMod` base — instrument `handleInput2` for a
custom verb + a `bind(key, Runnable)` helper — is a clean candidate, but **no current mod binds keys**,
so it stays a documented recipe here until a second-consumer need appears. See
[ui-component-backlog.md](../ui-component-backlog.md).

## See also

- [private-class-techniques](private-class-techniques.md) — the bytecode/in-package/reflection toolbox this uses.
- [client-gui-internals](client-gui-internals.md) — the HUD input path and `WurmInputField` focus.
