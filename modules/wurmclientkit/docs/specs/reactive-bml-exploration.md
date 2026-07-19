# BML enhancements & reactive server-driven UI — parked exploration

> **STATUS: PARKED / EXPLORATORY. Nothing here is decided or scheduled. We may build none of it.**
> This is a faithful capture of a design discussion so it isn't lost. Do **not** implement from it
> without the user re-opening the topic and deciding scope.

## Why this exists

The goal is **interactive, server-driven UI** for modded windows/popups. The current server→client UI
path (BML) is declarative but effectively stateless: on every user action the server regenerates the
whole BML and the client tears down and rebuilds the window. The user's concrete objection (recorded so
it isn't softened later):

- Every user action **blinks** the window (close + rebuild from scratch).
- It adds **noticeable latency / response time** per interaction (full round-trip + re-parse + re-layout).
- It is **wasteful on network and server** (the server re-generates the same BML on each action).

So "the server can just generate BML" is **not** an adequate answer for interactive UI — that is exactly
the problem being solved. Any design here must support **updating an open window in place**, not
rebuilding it.

## Current BML reality (grounded in `Client_vf`)

- BML is **client-side parsed**: `com.wurmonline.client.bml.BParser`/`BNode` tokenize the markup into an
  element tree; `renderer/gui/BmlWindowComponent.java` maps that tree to real GUI widgets.
- Flow: server sends a BML string → client parses + lays out a window → on a button click the client
  returns the button id + a map of input-field values → window closes → server may send new BML.
- **Tag set today:** `text, header, label, input, passthrough, dropdown, button, radio, checkbox, table,
  border, scroll, varray, harray, left, right, center, image, tree, closebutton, update`.
- **`image`** exists but takes a resource **path** (`src="…"` → `new WurmImage(resourceName, …)`), not an
  item icon number.
- **`tree`** exists — a server-described `WurmTreeList` of `col`/`row` elements (`BMLTreeListItem`). It is
  *declarative rows*, not a live item container (no `InventoryMetaItem` binding, no drag/right-click).
- **`update` already exists** and is the closest thing to reactivity: an `<update>` block references
  existing elements **by id** and mutates the **already-open** window without a rebuild. But it is
  **barely implemented — only `<button>`** (label / enabled / confirm / hover). The plumbing is there
  though: `BmlWindowComponent` keeps id-keyed maps (`inputFields`, `buttons`, `treeLists`,
  `radioButtons`) and the parser already has a "mutate in place, return null" path.

## The asks (as stated) + rationale

### 1. Item icons — and specifically **inline in text, like an emoji**
Not just an icon element sitting in its own box, but an icon **embedded into a text run**, flowing inline
with words (emoji-style). Open question: **is inline-in-text even possible** with the client's text
renderer? Item icons come from `IconLoader.getIcon((short)num)` → `Texture`; text is drawn via
`TextFont`/`WurmLabel`. Inline icons would require the text layout to interleave glyph runs with texture
quads on the same baseline — needs investigation of `TextFont`/the text painting path to see whether it
can host inline image runs, or whether only a separate `<icon>` box element is feasible.
(A standalone `<icon num=… size=… text=…/>` element is the easy, already-understood part; **inline** is
the real question.)

### 2. A **reliable** layout system
Not a push toward HTML. The complaint: the **current BML layout is unreliable** — alignment, stretching,
and dynamic component sizes are tricky and often don't behave as expected. The requirement is a layout
system whose alignment/stretch/sizing is **predictable and reliable**. Whether that's a rework of the
existing varray/harray/table/border/scroll behavior or a new layout engine is open. HTML is explicitly
**not** the goal; reliability is.

### 3. **JavaScript in the client** (Nashorn / Rhino / successor) — for hot-fixing modded UI from the server
Rationale (recorded faithfully — this is a genuine motivation, not to be dismissed):
- JS is **already embedded deeply in the JVM** and gives **full freedom via reflection**.
- **The killer feature:** the user can **update the server and have it send updated JS to the client**, so
  a bug/change in the modded UI workflow is fixed **without forcing every player to download and update a
  client mod on their machine**. Server-push code updates instead of client redistribution.
- **Lua is not native to the JVM** — that's why JS is preferred over Lua for this.
- Engine note (fact, not objection): Nashorn is deprecated (JDK 11) and removed (JDK 15); the client is
  Java 8 so Nashorn exists but is a dead end long-term. Rhino would be a bundled library.

### The safety tradeoff (acknowledged, not settled)
- If the **server sends code directly** to the client, it is **not 100% safe** (a compromised/hostile
  server → arbitrary code execution on the client). The user acknowledges this is *maybe the only reason*
  to avoid JS.
- **Lua would be safer** in this respect (sandboxable), despite not being JVM-native.
- **Also worth thinking about: ways to get dynamic content behavior WITHOUT sending any code** from server
  to client at all. This is an open thread to explore, not yet designed.

## Corrections to the earlier discussion (so they aren't repeated)

- **Do not raise "server-side JS."** The user never proposed it and considers it nonsensical. JS here
  means **client-side**, for the server-push-hot-fix reason above.
- **Do not answer interactivity with "the server can generate BML."** That regeneration-per-action model
  is the blink/latency/load problem being solved (see "Why this exists").
- The prior reply under-weighted the JS motivation. The real driver is **avoiding forced client-mod
  redistribution** by pushing UI logic from the server — evaluate JS/alternatives against *that* goal.

## Option space (sketches only — nothing chosen)

These are unresolved directions, listed so the thinking isn't lost:

- **Finish `<update>` reactivity.** Extend `<update>` from buttons-only to all element types (text/label,
  input value+enabled, dropdown options+selection, checkbox/radio, tree rows, image/icon, visibility)
  using the existing id maps, plus a "soft" button action that posts field state but **keeps the window
  open** so the server can reply with an `<update>` patch. Reactive UI, no window blink, minimal payload.
- **Reliable-layout rework** of the varray/harray/table/border/scroll behavior (or a replacement engine)
  so alignment/stretch/sizing are predictable. Relates to the deferred "own flexible table/grid renderer"
  note (WurmTreeList alignment is hardcoded).
- **Client-side dynamic logic** to cut round-trips further — the JS/Lua/no-code-transfer question. Judge
  each option against: (a) can the server hot-fix UI behavior without redistributing client mods?
  (b) safety of executing server-provided content, (c) JVM-nativeness/maintenance.
- **Mod-hosted vs vanilla-patched** parser: host an extended dialect in WurmClientKit (isolated, reuses
  `KitWindow`/`KitItemList`, only for mod windows over a custom packet) vs. patch vanilla
  `BmlWindowComponent` (affects standard server BML too). Undecided.

## Investigation threads to open IF this is revived

- `TextFont` / text painting path: can it interleave inline icon textures with glyphs (for emoji-style
  inline icons)? Or is a separate box element the only option?
- Exact `<update>` delivery: how does an update-BML target an already-open window (window/question id
  routing)? Confirm the server→client path that reaches an existing `BmlWindowComponent` without opening a
  new one.
- Rhino embedding footprint on Java 8; reflection sandboxing options; a safe capability-limited API
  surface if any code-from-server path is ever pursued.
- No-code-transfer alternatives for dynamic content (data-driven state machines, pre-shipped behavior
  parameterized by server data, etc.).
