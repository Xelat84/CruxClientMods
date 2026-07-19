# Spec: notifying the user (chat/event tabs, popups, sound)

How a client mod tells the player something — a chat/event line, a center-screen popup, a sound, a
window flash — and how to read *incoming* chat. Entry points + visibility. Verified against
`../../../Decompiled/Client_vf/`; names stable, line numbers drift.

## The `hud` handle

Everything here hangs off `HeadsUpDisplay`. Reach it via:
- **`ModClient.getHeadsUpDisplay()`** (launcher reflection — from any package), or
- **`WurmComponent.hud`** — a package-private static, so gui-package code reads it directly (what
  AuctionHouse does), or `hud.getWorld()` etc. See [game-state-access](game-state-access.md) §1.

## 1. A chat / event-tab line — the workhorse

```java
hud.textMessage(String tab, float r, float g, float b, String message);          // single colour
hud.textMessage(String tab, List<MulticolorLineSegment> segments);               // multi-colour
```

Both **public**. `r,g,b` are **0.0–1.0** floats (AuctionHouse: red error `1,0.3,0.3`; amber warn
`1,0.75,0.2`). The **`tab` string is the routing key**:

- `":Friends"` / `":Support"` → **rejected** (throws) — never post there.
- A name in the event-window set (`":Combat", ":Event", ":Help", ":Skills", ":System", ":Logs", …`) →
  the **event window** (with timestamp + `LiveLog` echo).
- Any other name → the **chat window** (`:Local`, or a novel name).

**`:Event` is the recommended channel for mod feedback** — it's what AuctionHouse routes `eventLog` /
`warnSell` / `showProtocolError` to (all thin wrappers over one `hud.textMessage(":Event", r,g,b, msg)`),
deliberately avoiding center-screen popups for routine feedback. Use [`Format`](../components/Format.md)
for any coin/weight/QL in the message.

## 2. Tabs

You **create a tab implicitly by posting to a novel name** — `ChatPanelComponent.getTab` auto-creates
an unknown tab (a novel name lands on the **chat** window, muteable, default orange). There's no public
"createTab" to call directly, and you still can't post to `:Friends`/`:Support`. A custom `MyModTab`
just works via `hud.textMessage("MyModTab", …)`.

## 3. Reading INCOMING chat — no listener; hook the receive path

There is **no registerable chat listener** in the client (unlike `FriendsListener`/`MissionListener`,
which do exist). Incoming server text arrives at
`comm.ServerConnectionListenerClass.textMessage(String title, float r,g,b, String message, byte onScreenType)`
(**public**) which forwards to `hud.textMessage`. To observe/intercept incoming chat, **bytecode-hook
that method** (the public String overload is the clean target; the segment overload is package-private).
See [private-class-techniques](private-class-techniques.md).

## 4. Sound / alerts

```java
world.getSoundEngine().play(String resourceName, SoundSource src,
                            float volume, float priority, float rate, boolean isPersonal, boolean looping);
world.getSoundEngine().playMusic(String resourceName, SoundSource src, float volume, float priority, float rate);
```

`World.getSoundEngine()` is **public**. **Sound names are resource keys** (dot-path, like model/texture
names) — the canonical set is `com.wurmonline.shared.constants.SoundNames`. There is no "play a raw
.wav" path; custom sounds are registered under a `sound.*` key the ServerPacks/resource way.

- **Category-mute gotcha:** `play` silently drops keys under `sound.combat`/`sound.arrow`/`sound.emote`/
  `sound.work`/`sound.liquid`/`sound.door` when the matching user Option is off. For an **always-audible
  alert**, use a key *not* under those prefixes (e.g. an `sound.effect.*` key). `sound.achievement`
  (played via `playMusic`) is a ready-made "ping."
- **`SoundSource`:** `FixedSoundSource(x,y,h)` / `MovableSoundSource` / `OffsetSoundSource`; for a
  non-positional alert pass `isPersonal=true` (or a fixed source at the player).
- **Thread-safe:** `play`/`playMusic` queue work to the sound thread — **safe to call off the game
  thread** (unlike the GUI calls below).

## 5. Other feedback channels (all public on `HeadsUpDisplay`)

- **Center popup / toast:** `addOnscreenMessage(String msg, float r, float g, float b, byte onScreenType)`.
  `onScreenType`: `1` = always shown; `2/3/4` = info/fail/hostile (each user-suppressible); `0` = not
  shown. Use `1` for a guaranteed alert.
- **Window flash:** `addBlinkingWindow(short windowId, int times)` — toggles a window's visibility on a
  500ms cadence to draw attention.
- **Status line:** `setStatusString(String)` — bottom status text, low-visibility.
- **Achievement banner:** `addNewAchievement(String name, byte type)` — themed popup (semantically
  "achievement"; normally paired with the achievement chime).

## 6. Threading

- **GUI/chat/popup calls (`textMessage`, `addOnscreenMessage`, `addNewAchievement`, `addBlinkingWindow`,
  `setStatusString`) must run on the game thread** — they mutate unsynchronized GUI state. From a UI or
  background thread, marshal with `ModClient.runTask(Runnable)` (see [game-state-access](game-state-access.md) §7).
- **Sound is the exception** — `getSoundEngine().play(...)` is safe from any thread.
- **Tab-visibility:** a line lands in its tab even if the user isn't viewing it. For critical feedback,
  prefer `:Event` **and** (if urgent) an `addOnscreenMessage(…, (byte)1)` popup and/or a sound.

## See also

- [game-state-access](game-state-access.md) — getting the `hud`/`World` handle and the game-thread rule.
- [`Format`](../components/Format.md) — coin/weight/QL strings for message bodies.
- [private-class-techniques](private-class-techniques.md) — hooking `ServerConnectionListenerClass.textMessage` to read incoming chat.
