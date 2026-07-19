# Getting started

## Prerequisites

- **JDK 8** (`jdk1.8.0_202`). Wurm Unlimited's client runs on Java 8; the mods target `1.8`.
- **Maven** (bundled with IntelliJ is fine).
- Nothing else — the build is self-contained. The `client-modlauncher`, `client`, and
  `common-client` artifacts come from the in-project Maven repository (`repo/`) committed in the
  CruxClientMods reactor (the WU client API ships there as signature-only stubs), so there are no
  external hosts to reach.

## Building the SDK

The kit is a module of the reactor and is shaded into `crux-clientmod.jar`; build the whole
reactor from the repo root with a JDK 8 toolchain:

```bash
mvn clean install -DskipTests
```

Output: `modules/wurmclientkit/target/wurmclientkit-<version>.jar` (also folded into
`crux-clientmod.jar`).

The `pom.xml` declares:

- `client-modlauncher:0.15` — the modloader API (`WurmClientMod`, `PreInitable`, `HookManager`, …).
- `client:3721782` (**provided**) — the decompiled/repackaged client classes (`WWindow`, widgets,
  `SimpleServerConnectionClass`, `IconLoader`, …). `provided` because the real client supplies them
  at runtime; the SDK jar must **not** bundle them.
- `common-client:3721782` (**provided**) — shared client/common classes.

`provided` scope matters: it keeps the vanilla client classes off the shaded/shipped jar so your mod
doesn't collide with the real ones the launcher loads.

## The two consumption models

The SDK has **no `classname` entry point** — it is a plain library jar. A dependent mod uses it one
of two ways:

### 1. Ship alongside (recommended for separate jars)

Ship `wurmclientkit.jar` next to your mod jar and list it on the `classpath=` line of your mod's
descriptor:

```properties
# mods/mymod/mymod.properties
classname=com.example.mymod.MyMod
classpath=mymod.jar:wurmclientkit.jar
sharedClassLoader=true
```

Both jars load on the same private classloader. The [`ClassClassPath` append](concepts/hooking-model.md)
that `ServerCommandMod.preInit()` performs puts your subclass's bytecode on the HookManager pool, and
because the SDK's classes are on the same loader they resolve too.

### 2. Shade into your jar

Add the SDK as a Maven dependency and shade it into your mod jar with `maven-shade-plugin`. Then your
mod ships a single fat jar and the `classpath=` line only lists that jar. Use this when you want one
self-contained artifact (e.g. the consolidated Crux distribution).

Either way works — the difference is packaging, not behavior. Shading avoids version-skew between the
SDK jar and the mod; shipping alongside lets several mods share one SDK jar on disk.

## Writing your first mod

A minimal protocol mod is just a `ServerCommandMod` subclass:

```java
package com.example.mymod;

import com.wurmonline.clientkit.PacketReader;
import com.wurmonline.clientkit.PacketWriter;
import com.wurmonline.clientkit.ServerCommandMod;

public class MyMod extends ServerCommandMod {
    private static final byte CMD = -70;            // pick an unused opcode, unique across all mods
    private static Object conn;

    @Override protected byte commandId() { return CMD; }

    @Override protected void handle(Object connection, java.nio.ByteBuffer bb) {
        conn = connection;
        PacketReader r = new PacketReader(bb);      // positioned just past the CMD byte
        byte sub = r.get();
        if (sub == 1) {
            String text = r.readString();
            // update mod state / GUI here — you are on the client (game) thread
        }
    }

    public void ping(String name) {
        send(conn, new PacketWriter(CMD).put((byte) 1).putString(name).bytes());
    }
}
```

That is a complete, working mod. `preInit()` (inherited) installs the shared hook and registers your
command id; `handle` receives inbound packets for your opcode; `send` writes outbound ones. You did
not write a single line of javassist.

Adding a GUI is the next step — see [`KitTabbedWindow`](components/KitTabbedWindow.md) and
[`KitTreeItem`](components/KitTreeItem.md), and remember your window class must live in package
`com.wurmonline.client.renderer.gui` ([why](concepts/package-private-constraint.md)).

## Choosing a command byte

The opcode is a signed `byte` (−128..127) matched against the vanilla `reallyHandle` switch. Pick one
the vanilla switch does **not** use and that no other installed mod uses. AuctionHouse uses `−67`.
Negative values are convenient because most vanilla opcodes are positive. Duplicate ids are detected
at registration and the second one is refused with a warning — see
[multi-mod coexistence](concepts/multi-mod-coexistence.md).

## Next steps

- [Overview](overview.md) — how the pieces fit together.
- [Messaging deep-dive](components/ServerCommandMod.md) — lifecycle, dispatch, sending.
- [Wire format](concepts/wire-format.md) — keep both ends in lockstep.
- [Gotchas](gotchas.md) — read before you spend an afternoon on a `NoClassDefFoundError`.
