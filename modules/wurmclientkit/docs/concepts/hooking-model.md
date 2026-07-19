# Concept: the hooking model

How `ServerCommandMod` bends the vanilla client to route a custom command byte. You do not have to
write any of this — it's all inside the base `preInit()` — but understanding it explains every
failure mode you'll hit.

## The vanilla packet path

All inbound server packets funnel through one method:

```
com.wurmonline.client.comm.SimpleServerConnectionClass.reallyHandle(int, ByteBuffer)
```

It's a ~200-case switch keyed on the first payload byte (`cmd = bb.get()`). Sending is symmetric and
private: `connection.getBuffer()` → write bytes → `reallySend()`.

To inject a custom command we need two edits to that class, plus one classloader fix.

## Edit 1 — the `reallyHandle` peek/dispatch (installed once)

`ServerCommandMod` inserts this at the **top** of `reallyHandle` via javassist `insertBefore`:

```java
if ($2 != null && $2.remaining() >= 1
        && com.wurmonline.clientkit.ServerCommandMod.owns($2.get($2.position()))) {
    byte __wckCmd = $2.get();                                  // consume the byte
    com.wurmonline.clientkit.ServerCommandMod.dispatch(__wckCmd, $0, $2);
    return;                                                     // skip the vanilla switch
}
```

`$2` is the `ByteBuffer`, `$0` is `this` (the connection). The key detail: it **peeks** with an
absolute `$2.get($2.position())` (non-consuming) and only consumes + dispatches if some registered
mod `owns` that byte. For every other command the buffer is untouched and the vanilla switch runs its
own `cmd = bb.get()` exactly as before. So the patch is transparent to all non-mod traffic.

`owns` / `dispatch` are static methods on `ServerCommandMod`. They consult a shared
`ConcurrentHashMap<Integer, ServerCommandMod>` (command id → owning mod) and route to the right
handler, catching and logging any exception the handler throws.

## Edit 2 — the `wckSend` injection (installed once)

A public method is *added* to the connection class:

```java
public void wckSend(byte[] payload) {
    java.nio.ByteBuffer buf = this.connection.getBuffer();
    buf.put(payload);
    this.reallySend();
}
```

This wraps the private send path so mod code can push bytes out. `ServerCommandMod.send(conn, bytes)`
calls it **reflectively** (`conn.getClass().getMethod("wckSend", byte[].class)`), because `wckSend`
doesn't exist on the compile classpath — it's grafted onto a vanilla class at runtime. The reflected
`Method` is cached after the first lookup.

Both edits are guarded by a static `patched` flag inside a `synchronized installShared(pool)`, so no
matter how many mods extend the base, they install **exactly once**. See
[multi-mod coexistence](multi-mod-coexistence.md).

## Edit 3 — the `ClassClassPath` append (per subclass)

This is the one step done by **every** subclass, not just the first:

```java
pool.appendClassPath(new ClassClassPath(getClass()));
```

Why per-subclass and why at all? The patched `reallyHandle` now references
`com.wurmonline.clientkit.ServerCommandMod`. The HookManager Loader must be able to **define** that
class (and, transitively, your subclass and your gui-package classes) when it links the patched
vanilla method. But your mod runs on a *private* classloader whose jar is not on the Loader's class
pool. Without the append, linking the patched method throws `NoClassDefFoundError` — a runtime fault,
not a compile error.

The append puts the concrete subclass's jar bytecode on the pool, so the Loader can define your
classes. Each subclass jar is a **separate classpath entry**, so each subclass must append itself —
that's why this step is *not* guarded by the `patched` flag.

As a bonus, this same append is what makes the [package-private GUI access](package-private-constraint.md)
work at runtime: your gui-package classes end up on the same loader as `WWindow`.

## Timing — do it in `preInit`

Bytecode edits belong in `preInit()` (the `PreInitable` lifecycle stage), which runs before the
client's connection class is loaded/linked. Registering runtime reflection hooks or ModComm channels
belongs later, in `init()` (`Initable`). `ServerCommandMod` implements `PreInitable` and does all its
work in `preInit`.

## Failure modes

| Failure | Meaning | Fix |
|---------|---------|-----|
| `NoClassDefFoundError` in `reallyHandle` | `ClassClassPath` append didn't run / wrong class | Ensure your mod extends `ServerCommandMod` (or append yourself). |
| `HookException` at preInit | `pool.get(CONN_CLASS)` or method edit failed | Client version mismatch — verify `SimpleServerConnectionClass.reallyHandle` still exists with that signature. |
| Inbound packet never arrives | command id not registered / duplicate | Check the preInit log line; see [multi-mod coexistence](multi-mod-coexistence.md). |
| `send` logs "no connection to send on yet" | you called `send(null, …)` | Capture the `connection` from `handle` first, or hold the connection reference another way. |

## See also

- [Package-private constraint](package-private-constraint.md) — the other half of the `ClassClassPath` story.
- [Multi-mod coexistence](multi-mod-coexistence.md) — the once-vs-per-subclass split.
- [`ServerCommandMod` reference](../components/ServerCommandMod.md).
