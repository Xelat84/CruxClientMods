# Concept: multi-mod coexistence

Several mods can extend `ServerCommandMod` and run in the same client at once. This is a first-class
design goal, not an accident. Here is exactly how N mods share the machinery.

## What's shared vs. per-mod

| Thing | Scope | Guard |
|-------|-------|-------|
| `reallyHandle` peek/dispatch patch | **once** | static `patched` flag in `synchronized installShared` |
| `wckSend` injection | **once** | same `patched` flag |
| Command id → mod map (`HANDLERS`) | **shared, one entry per mod** | `ConcurrentHashMap` |
| `ClassClassPath` append | **per subclass** | not guarded (each jar is a distinct pool entry) |

The rule of thumb: **anything that edits the vanilla class is installed once; anything that registers
this specific mod happens per subclass.**

## The registration flow

Each subclass's `preInit()` runs (modloader calls it once per mod):

1. `HANDLERS.putIfAbsent(commandId & 0xFF, this)` — claim this command id.
2. `pool.appendClassPath(new ClassClassPath(getClass()))` — put *this* jar on the pool
   ([why](hooking-model.md)).
3. `installShared(pool)` — install the two vanilla edits **iff** not already done.

Whichever mod `preInit`s first installs the shared patch; every mod registers its own handler. The
generic dispatcher (`ServerCommandMod.dispatch`) reads the map, so a mod on `−70` and a mod on `−71`
both work under the single patch: inbound bytes route to whichever mod claimed that id.

## Command id is a 0..255 key

The map is keyed on `commandId() & 0xFF` — the byte is normalized to an unsigned int so `−67` and its
unsigned form agree on both the register side and the `owns`/`dispatch` lookup side. Don't reason
about the raw signed byte when thinking about collisions; think 0..255.

## Duplicate command ids

If two mods claim the same id, `putIfAbsent` returns the incumbent and the second registration is
**refused with a warning**:

```
<mod>: command id -70 already registered by <other mod>; ignoring duplicate.
```

The second mod's `handle` will simply never be called — the first mod owns that byte. There is no
crash and no silent corruption, but the losing mod is dead on that opcode. **Coordinate command bytes
across all installed mods.** Keep a registry (a comment in each mod, or a shared doc) of which
opcodes are taken. AuctionHouse owns `−67`.

## Thread model

`HANDLERS` is a `ConcurrentHashMap` and `patched` is `volatile` behind a `synchronized` installer, so
registration from multiple mods' `preInit` is safe even if the modloader parallelizes it (it does
not today, but the SDK doesn't rely on that). Dispatch itself — `handle(conn, buf)` — runs on the
**client (game) thread**, single-threaded, the same thread that would have run the vanilla switch
case. Your handler can therefore touch GUI state directly without cross-thread marshalling, but it
must not block (it stalls packet processing).

## Adding a mod to an existing install — checklist

- Pick a command byte no vanilla opcode and no other installed mod uses.
- Ship or shade `wurmclientkit.jar` (all SDK-based mods can share one copy on disk if shipped
  alongside — see [getting started](../getting-started.md)).
- Confirm each mod's `preInit` log line shows its registration and, once, the shared-patch line:
  `WurmClientKit: patched ...reallyHandle and injected wckSend (once).`

## See also

- [Hooking model](hooking-model.md) — what the shared patch actually does.
- [`ServerCommandMod` reference](../components/ServerCommandMod.md).
