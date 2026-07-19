# Spec: custom rendering in a `WurmComponent`

How to draw your own graphics — filled rects, textures, lines, text — inside a component's
`renderComponent`. Most custom widgets never need more than `fillRect` + `drawTexture` + text; the
raw-`Primitive` path is only for custom geometry (the checkbox/tree expand-box style). Verified against
`Decompiled/Client_vf/client/com/wurmonline/client/renderer/gui/WurmComponent.java` and `WCheckBox.java`.

> **Where you draw:** override `protected void renderComponent(Queue queue, float alpha)` (never the
> `final render`). It runs inside the component's pushed scissor clip. All coordinates are **absolute
> screen pixels** (§1/§2 of [client-gui-internals](client-gui-internals.md)).

## The draw helpers (protected/package on `WurmComponent`)

These are the everyday API — inherited, so a gui-package subclass calls them directly.

### Filled quad

```java
fillRect(Queue queue, float r, float g, float b, float a, int x, int y, int w, int h);
```
Solid alpha-blended rectangle. Internally: reserves a `Primitive`, copies `Renderer.stateAlphaBlend`,
uses the shared unit-square `Primitive.staticVertexSquare2D` scaled by a model matrix
(`fromTranslationAndNonUniformScale(x, y, 0, w, h, 0)`), `TRIANGLESTRIP`, clipped to the current scissor.

`fillInvertRect(...)` — same signature, `BlendMode.DSTINVERT` (used for text-selection highlight).

### Textured quad

```java
drawTexture(Queue queue, Texture tex, float r, float g, float b, float a,
            int x, int y, int w, int h, int u, int v, int uw, int vh);
```
Delegates to `Renderer.texturedQuadAlphaBlend(...)`. **UVs are divided by 256** — the GUI skin atlas
convention (`u/256f, v/256f, uw/256f, vh/256f`). For a whole texture use `0,0,256,256`. Get a `Texture`
from `IconLoader.getIcon((short) imageNumber)` (item icons — see [`GuiKit.icon`](../components/GuiKit.md))
or `ResourceTextureLoader.getNearestTexture(name)` (skins).

- `drawTexTilingH(... int v, int vh)` — horizontal tiling; U runs `0..w/256`.
- `drawTexTilingV(... int u, int uw)` — vertical tiling; the V axis uses a **/64** divisor, not /256.

### Text

```java
this.text.moveTo(int x, int yBaseline);              // baseline = glyph bottom
this.text.paint(queue, String s, float r, float g, float b, float a);
this.text.paint(queue, String s);                    // white
```
`this.text` (default font) and `this.textBold` are inherited `TextFont`s. Baseline sits at the glyph
bottom, so to top-align at `y` draw at `y + text.getHeight()`. Measure with `text.getWidth(s)` /
`getHeight()` / `getAscent()` / `getDescent()`. Other fonts: `TextFont.getHeaderText()`,
`getMonospaced()`, etc. (§6 of client-gui-internals). Note `WurmLabel` hardcodes white; draw text
yourself (or use `WurmHeader`) if you need colour.

## Raw geometry (custom `Primitive`) — only when the helpers don't fit

For lines or a custom mesh (e.g. the checkbox tick, the tree expand box), reserve a `Primitive` and set
its state by hand. Canonical pattern from `WCheckBox.renderComponent`:

```java
Primitive prim = queue.reservePrimitive();
prim.type       = Primitive.Type.LINES;          // or TRIANGLESTRIP
prim.num        = this.checked ? 6 : 4;           // primitive count (line segments / strip verts)
prim.r = colR; prim.g = colG; prim.b = colB; prim.a = 1.0F;   // or prim.setColor(r,g,b,a)
prim.texture[0] = prim.texture[1] = null;         // untextured
prim.texenv[0]  = Primitive.TexEnv.MODULATE;
prim.vertex     = vbo;                             // a prebuilt VertexBuffer (see below)
prim.index      = null;
prim.clipRect   = HeadsUpDisplay.scissor.getCurrent();
this.modelMatrix.setTranslation(this.x, this.y + dy, 0.0F);   // position the geometry
queue.queue(prim, this.modelMatrix);
```

Key types (packages matter):
- `com.wurmonline.client.renderer.backend.Primitive` — `Type.{LINES, TRIANGLESTRIP, …}`,
  `TexEnv.MODULATE`, `BlendMode.DSTINVERT`, `copyStateFrom(Renderer.stateAlphaBlend)`, `setColor`.
- `com.wurmonline.client.renderer.backend.Queue` — `reservePrimitive()` then `queue(prim, matrix)`.
- `com.wurmonline.client.renderer.Matrix` — `setTranslation(x,y,z)` and
  `fromTranslationAndNonUniformScale(x,y,z, sx,sy,sz)`. `WurmComponent` keeps a shared static one for
  the helpers; widgets that draw custom geometry use their own `modelMatrix` instance.

**Building the `VertexBuffer`** is done once in a `static { … }` initializer via `VertexBuffer.create(…)`,
filled between `.lock()` / `.unlock()`. The exact `create(...)` argument list is verbose and rarely
needed — copy it from the canonical static blocks in `WCheckBox` (the tick VBO) or `WurmTreeList` (the
`vboCheckbox` static block) rather than hand-writing it. If you only need rectangles and text, you never
touch `VertexBuffer` at all.

## Scissor clipping

`HeadsUpDisplay.scissor` is a clip-rect stack. `render()` already pushes the component's own bounds
before calling `renderComponent`, so your drawing is clipped to the component. To sub-clip (e.g. a
scrolling region), `pushClip(x, y, w, h)` — it returns `false` when fully clipped (skip drawing) — and
always `popClip()` in a `finally`. Every custom `Primitive` must set `clipRect =
HeadsUpDisplay.scissor.getCurrent()` or it won't be clipped.

## Patching vanilla rendering

There's no per-cell/per-row render hook in the vanilla widgets (e.g. `WurmTreeList$TreeListPanel`'s
`renderComponent` is one monolithic package-private method). To inject custom drawing into a vanilla
component, bytecode-patch its `renderComponent` with `insertAfter` and call these same helpers/`Queue`
from the injected code — see [private-class-techniques](private-class-techniques.md) (and the
cell-button precedent, which patched input rather than render — appearance there is driven through the
existing `getParameter`/`getSecondaryR/G/B` hooks instead, which is cheaper than a render patch when it
suffices).

## See also

- [client-gui-internals](client-gui-internals.md) §1–2 — the render/input model and coordinate space.
- [private-class-techniques](private-class-techniques.md) — patching a vanilla `renderComponent`.
