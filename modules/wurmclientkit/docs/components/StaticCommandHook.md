# `StaticCommandHook`

`com.wurmonline.clientkit.StaticCommandHook` — installs a **static-dispatch** custom server→client
command hook. Use this (not [`ServerCommandMod`](ServerCommandMod.md)) whenever your command handler
**instantiates a gui-package window** (anything extending the package-private `WWindow`).

## When to use which

| Your `handle` … | Use | Why |
|-----------------|-----|-----|
| opens / touches a window (gui-package class) | **`StaticCommandHook`** | dispatch is a static `Handler.handle(...)` call by name → handler + its gui classes load on the **HookManager loader**, same as `WWindow` → package-private access is legal |
| is pure logic (no gui-package instantiation) | `ServerCommandMod` | instance dispatch is fine; multi-mod registry, less boilerplate |

## The classloader trap this avoids

A custom-command hook loads your handler class **twice**: once on the modloader's loader (when
`preInit` references it) and once on the HookManager loader (the copy the patched vanilla
`reallyHandle` actually calls). `ServerCommandMod` dispatches through the mod *instance* — created on
the modloader loader — so a `handle` that `new`s up a window resolves that window on the **wrong**
loader and throws `IllegalAccessError`; the window silently never opens. `StaticCommandHook` embeds
`YourHandler.handle(...)` **by name** in the patched method, so dispatch resolves on the HookManager
loader and the whole handler subtree (window classes included) loads there too.

## API

```java
static void install(byte commandId, Class<?> handlerClass)   // call from preInit()
static void send(Object connection, byte[] payload)          // call from the handler
```

## Handler contract

```java
public static void handle(Object connection, java.nio.ByteBuffer payload)
```

- `payload` is positioned **just after** the command byte (on your sub-command / first field).
- Runs on the client (game) thread — touch GUI directly, don't block.
- **Be self-contained.** Store the `connection` you receive and send with
  `StaticCommandHook.send(connection, payload)`. Do **NOT** wire senders/state into the handler from
  `preInit`: that sets fields on the *modloader* copy of the class, a different object from the
  HookManager-loader copy the patch calls — it reads back null at runtime ("no transport wired yet").

## Complete minimal mod

```java
// preInit
StaticCommandHook.install((byte) -67, AuctionClient.class);

// AuctionClient (handler)
private static Object connection;
public static void handle(Object conn, ByteBuffer bb) {
    connection = conn;
    switch (bb.get()) { /* sub-commands */ }
}
private static void send(byte[] payload) {           // led by the command byte
    StaticCommandHook.send(connection, payload);
}
```

## Symptom → cause quick table

| Symptom | Cause | Fix |
|---------|-------|-----|
| window silently never opens, no error you can see | GUI handler dispatched through a mod instance (`ServerCommandMod`) → `IllegalAccessError` swallowed | use `StaticCommandHook` |
| `no transport wired yet` / null field at runtime | state wired into the handler from `preInit` (modloader copy) but read on the HookManager copy | make the handler self-contained; wire nothing from `preInit` |
| `NoClassDefFoundError` on the patched method | handler jar not on the pool | `install` does `Hooks.appendClassPath` for you — don't skip it |

## Coexistence

Safe to use alongside `ServerCommandMod` and other `StaticCommandHook` installs: the shared `wckSend`
send method is injected only if absent, and each command's peek is an independent `insertBefore` guard.

## Source

`src/main/java/com/wurmonline/clientkit/StaticCommandHook.java`

## See also

- [`ServerCommandMod`](ServerCommandMod.md) — instance-dispatch base for pure-logic handlers
- [Package-private constraint](../concepts/package-private-constraint.md) · [Hooking model](../concepts/hooking-model.md)
