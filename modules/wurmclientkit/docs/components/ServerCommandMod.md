# `ServerCommandMod`

`com.wurmonline.clientkit.ServerCommandMod` — abstract base for a client mod that speaks **one**
custom server→client command byte and can send raw payloads back. Subclass it and you have a complete
working protocol mod without touching javassist.

Implements `WurmClientMod` + `PreInitable`.

## What you implement

```java
protected abstract byte commandId();                                 // your unique opcode
protected abstract void handle(Object connection, ByteBuffer payload); // inbound handler
```

- **`commandId()`** — the custom command byte this mod owns (e.g. `−67`). Must be unique across all
  installed mods and unused by the vanilla switch. Keyed internally as `commandId() & 0xFF` (0..255).
- **`handle(connection, payload)`** — called for each inbound packet on your opcode. `payload` is a
  `ByteBuffer` positioned **just after** the command byte (on your first sub-command / field). Runs
  on the **client (game) thread** — you may touch GUI state directly, but must not block. Exceptions
  thrown here are caught and logged by the dispatcher, not propagated.

Capture the `connection` argument if you want to reply later:

```java
private static Object conn;
@Override protected void handle(Object connection, ByteBuffer bb) {
    conn = connection;
    // ...decode with PacketReader...
}
```

## What you call

```java
protected void send(Object connection, byte[] payload);
```

Sends a raw payload on the connection via the injected `wckSend` (invoked reflectively). The payload
**must already carry the command byte** — build it with `PacketWriter`:

```java
send(conn, new PacketWriter(commandId()).put(sub).putString(text).bytes());
```

If `connection` is `null`, `send` logs a warning and no-ops (you called it before capturing a
connection). The reflected `wckSend` `Method` is cached after first use.

## What the base does for you (in `preInit`)

`preInit()` is inherited — do **not** override it without calling `super.preInit()`. It:

1. Registers `(commandId → this)` in the shared handler map (refusing duplicates with a warning).
2. Appends `new ClassClassPath(getClass())` to the HookManager pool — fixes both the
   `NoClassDefFoundError` on the patched vanilla method and the package-private GUI loader problem.
3. Installs the shared vanilla edits (`reallyHandle` peek/dispatch + `wckSend`) exactly once.

The full mechanism is in [the hooking model](../concepts/hooking-model.md); the once-vs-per-subclass
split is in [multi-mod coexistence](../concepts/multi-mod-coexistence.md).

## Static dispatch API (called by the patched vanilla method)

You never call these directly, but they explain the log lines:

- `static boolean owns(byte cmd)` — is any mod registered for this byte? (the peek check)
- `static void dispatch(byte cmd, Object connection, ByteBuffer bb)` — route to the owning mod's
  `handle`, catching exceptions.

## Complete minimal mod

```java
package com.example.mymod;

import com.wurmonline.clientkit.PacketReader;
import com.wurmonline.clientkit.PacketWriter;
import com.wurmonline.clientkit.ServerCommandMod;

public class MyMod extends ServerCommandMod {
    private static final byte CMD = -70;
    private static Object conn;

    @Override protected byte commandId() { return CMD; }

    @Override protected void handle(Object connection, java.nio.ByteBuffer bb) {
        conn = connection;
        PacketReader r = new PacketReader(bb);
        switch (r.get()) {                     // sub-command
            case 1: onText(r.readString()); break;
            case 2: onCount(r.getInt());   break;
        }
    }

    private void onText(String s) { /* update GUI */ }
    private void onCount(int n)   { /* update GUI */ }

    public void request(long id) {
        send(conn, new PacketWriter(CMD).put((byte) 9).putLong(id).bytes());
    }
}
```

## Gotchas

- **Don't call `send` with a `null` connection.** Capture the one `handle` gives you first. There is
  no ambient "current connection" to fall back on.
- **`handle` runs on the game thread and must not block.** Long work stalls all packet processing.
- **The payload you build must lead with the command byte** — `new PacketWriter(commandId())…`.
  Forgetting it means the server (or the dispatcher) can't route your reply.
- **Overriding `preInit` without `super`** skips the patch install and your mod silently receives
  nothing.
- A **pure-GUI mod that does not extend this class** does not get the `ClassClassPath` append — it
  must do it itself (see [package-private constraint](../concepts/package-private-constraint.md)).

## Source

`src/main/java/com/wurmonline/clientkit/ServerCommandMod.java`

## See also

- [Hooking model](../concepts/hooking-model.md) · [Multi-mod coexistence](../concepts/multi-mod-coexistence.md)
- [Wire format](../concepts/wire-format.md) · [`PacketCodec`](PacketCodec.md)
