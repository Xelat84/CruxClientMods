# Concept: the wire format

`PacketWriter` and `PacketReader` are a matched pair that speak the **vanilla Wurm `ByteBuffer`
format**. If you keep them symmetric they Just Work; if you drift, you get silent garbage (a shifted
read is not an error — it's wrong data).

## The rules

1. **Big-endian.** Every multi-byte value is written most-significant-byte first, matching Java's
   `ByteBuffer` default and the vanilla server's encoding. `PacketWriter` does this by hand;
   `PacketReader` delegates to `ByteBuffer` (also big-endian by default).
2. **Strings are short-length-prefixed UTF-8.** A 16-bit **unsigned** length (0..65535) followed by
   that many UTF-8 bytes. `putString` / `readString` are the matched pair. This mirrors the vanilla
   server's string helper — do not use a null terminator or a 4-byte length.
3. **Fixed field order, no framing.** There is no self-describing type tag per field. The reader must
   read exactly the fields the writer wrote, in the same order, with the same types. The command byte
   is the only routing token; everything after it is a positional payload you define.

## Type reference

| Writer | Bytes | Reader | Notes |
|--------|-------|--------|-------|
| `put(byte)` | 1 | `get()` | |
| `putShort(int)` | 2 | `getShort()` / `getUnsignedShort()` | writer takes an `int`, truncates to 16 bits |
| `putInt(int)` | 4 | `getInt()` | |
| `putLong(long)` | 8 | `getLong()` | |
| `putFloat(float)` | 4 | `getFloat()` | via `Float.floatToIntBits` |
| `putString(String)` | 2 + n | `readString()` | `null` writes as empty string |

`getUnsignedShort()` exists because a raw `getShort()` sign-extends; use the unsigned read when the
value is a count or id in 0..65535 (e.g. an item image number).

## A protocol is a contract between two files

The producer and consumer live in **different codebases** — the C# server (`WurmSharp`) and the Java
client mod. There is no shared schema; the "schema" is the two pieces of code agreeing field-for-field.

Server side (C#), the matching encoder writes big-endian and short-length-prefixed strings too — this
is why the SDK deliberately mirrors that format rather than inventing its own. When you add a field on
one end, add it in the same position on the other, or every field after it shifts.

**Sub-commands.** A common pattern is `commandByte, subByte, <payload for that sub>`. The command byte
routes to your mod; the sub byte routes inside your `handle`. Build with
`new PacketWriter(CMD).put(sub)...` and read with `r.get()` for the sub, then branch. Keeping a small
enum of sub-commands documented next to the mod saves you from magic-number drift.

## Worked example

Server sends `CMD, sub=1, itemId(long), imageNumber(ushort), name(string), ql(float)`:

```java
// client handle()
PacketReader r = new PacketReader(bb);          // positioned past CMD
byte sub = r.get();
if (sub == 1) {
    long id      = r.getLong();
    int  image   = r.getUnsignedShort();
    String name  = r.readString();
    float ql     = r.getFloat();
}
```

Client replies `CMD, sub=2, itemId(long)`:

```java
send(conn, new PacketWriter(CMD).put((byte) 2).putLong(id).bytes());
```

Note `imageNumber` is read with `getUnsignedShort()` — image numbers exceed 32767, so a signed read
would go negative and `IconLoader.getIcon` would fail. This is the single most common wire bug.

## Debugging a desync

If reads come out wrong:

1. Count bytes. Add up field sizes on both ends; a mismatch pinpoints the drifted field.
2. Check signedness on shorts (`getShort` vs `getUnsignedShort`).
3. Check string encoding — a length that reads as a huge number means you read a string where the
   writer didn't write one (or vice versa), so the 2-byte length is really other data.
4. Confirm the reader started at the right position — `PacketReader` reads from the buffer's *current*
   position, which `handle` gives you already advanced past the command byte.

## See also

- [`PacketWriter` / `PacketReader` reference](../components/PacketCodec.md).
- [`ServerCommandMod` reference](../components/ServerCommandMod.md) — where the buffer comes from.
