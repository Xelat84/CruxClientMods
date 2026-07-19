# `PacketWriter` / `PacketReader`

`com.wurmonline.clientkit.PacketWriter` and `PacketReader` — the matched big-endian codec for custom
packets. Both mirror the vanilla `ByteBuffer` wire format; see [wire format](../concepts/wire-format.md)
for the format rules and why they must stay symmetric.

Both classes are `final`.

## `PacketWriter` (encode side)

A growable big-endian byte builder. Starts at a 32-byte buffer and doubles as needed. **Fluent** —
every `put*` returns `this`.

### Construction

```java
new PacketWriter()                    // empty
new PacketWriter(cmd)                  // seed with leading bytes (varargs)...
new PacketWriter(cmd, sub)             // ...e.g. command + sub-command
```

The varargs constructor just `put`s each leading byte, so `new PacketWriter(CMD)` is the idiomatic
start for a packet whose first byte is the command.

### Methods

| Method | Writes |
|--------|--------|
| `put(byte)` | 1 byte |
| `putShort(int v)` | 2 bytes, big-endian (truncates `v` to 16 bits) |
| `putInt(int v)` | 4 bytes, big-endian |
| `putLong(long v)` | 8 bytes, big-endian |
| `putFloat(float v)` | 4 bytes (`Float.floatToIntBits`) |
| `putString(String s)` | 2-byte unsigned length + UTF-8 bytes (`null` → empty) |
| `bytes()` | returns a right-sized `byte[]` copy of what's been written |

`bytes()` returns a **copy** trimmed to the written length — safe to hand straight to
`ServerCommandMod.send`. The internal buffer is not exposed.

### Example

```java
byte[] payload = new PacketWriter(CMD)      // command byte
        .put((byte) 2)                      // sub-command
        .putLong(itemId)
        .putShort(imageNumber)              // read back with getUnsignedShort()
        .putString(name)
        .putFloat(quality)
        .bytes();
send(conn, payload);
```

## `PacketReader` (decode side)

A thin wrapper over a vanilla `ByteBuffer`. Reads **advance the underlying buffer's position** —
construct one per received packet. In a `ServerCommandMod.handle`, the buffer you're given is already
positioned past the command byte, so `new PacketReader(bb)` starts on your first field.

### Construction

```java
new PacketReader(byteBuffer)
```

### Methods

| Method | Reads |
|--------|-------|
| `get()` | 1 byte |
| `getShort()` | 2 bytes → signed short |
| `getUnsignedShort()` | 2 bytes → `int` in 0..65535 |
| `getInt()` | 4 bytes |
| `getLong()` | 8 bytes |
| `getFloat()` | 4 bytes |
| `readString()` | 2-byte unsigned length + UTF-8 bytes |
| `remaining()` | bytes left in the buffer |
| `buffer()` | the underlying `ByteBuffer` (escape hatch) |

### Example

```java
PacketReader r = new PacketReader(bb);
byte sub      = r.get();
long id       = r.getLong();
int  image    = r.getUnsignedShort();   // NOT getShort — image numbers exceed 32767
String name   = r.readString();
float ql      = r.getFloat();
```

## Practical bounds

- **Strings are capped at 65535 UTF-8 bytes** — the length prefix is a 16-bit unsigned short. Writer and
  reader are symmetric, so normal strings are fine; a payload beyond 64 KB would silently corrupt the
  frame. Don't send huge strings in one field (chunk them).
- **A malformed inbound length** (claims more bytes than remain) throws `BufferUnderflowException` from
  `readString`. That's caught and logged by `ServerCommandMod`'s dispatch, so it won't crash the
  client — but validate lengths if you parse untrusted framing yourself.

## The one bug everyone hits

**Reading an image number (or any large id/count) with `getShort()` instead of `getUnsignedShort()`.**
Values above 32767 sign-extend to negative, and downstream code (`IconLoader.getIcon`) chokes. If an
icon is missing or an id looks negative, check this first. See [wire format](../concepts/wire-format.md)
for the full desync-debugging checklist.

## Source

`src/main/java/com/wurmonline/clientkit/PacketWriter.java`,
`src/main/java/com/wurmonline/clientkit/PacketReader.java`

## See also

- [Wire format](../concepts/wire-format.md) — format rules, sub-commands, debugging desyncs.
- [`ServerCommandMod`](ServerCommandMod.md) — where the buffer comes from and how `bytes()` gets sent.
