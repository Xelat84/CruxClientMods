# Spec: client GUI internals (reference)

Deep reference on the vanilla client GUI system, distilled from the Vineflower decompile at
`../../../Decompiled/Client_vf/client/com/wurmonline/client/renderer/gui/`. This is working knowledge
for building SDK components — **re-verify against source before relying on a line number; they rot.**
Everything here is package `com.wurmonline.client.renderer.gui` unless noted.

> How to read this: the "access" column/notes tell you whether a member is reachable from an
> in-package helper (package-private → yes if your class is in this package) vs. truly `private`
> (needs bytecode/reflection — see [private-class-techniques](private-class-techniques.md)).

## 1. The rendering model

### `WurmComponent` (public abstract) — base of everything

- Package-private fields: `int x, y, width, height` (**absolute screen pixels**), `float r,g,b`,
  `WurmComponent parent`, `TextFont text` / `textBold`, `static HeadsUpDisplay hud`,
  `static int SCREEN_WIDTH, SCREEN_HEIGHT`, shared skin textures (`panelTexture`, `backgroundTexture`, …).
- **`public final void render(Queue, float alpha)`** wraps `hud.scissor.pushClip(x,y,width,height)`
  and, if not fully clipped, calls **`protected void renderComponent(Queue, float alpha)`** (default
  empty). **Override `renderComponent`, never `render`.** Must `popClip()` (render() handles it).
- **`public void gameTick()`** — once per logic tick; containers propagate to children. Use for
  animation and for **polling widgets that have no change event** (dropdowns, tree selection).
- Input handlers (`leftPressed`, `rightPressed`, `leftReleased`, `mouseDragged`, `mouseMoved`,
  `mouseWheeled`, `itemDropped`, `pick`, …) all **default to bubbling to `parent`**. Override to consume.
- `contains(xp,yp)` is **exclusive** on left/top, inclusive on right/bottom (`xp > x && … <= x+width`)
  — a click exactly on the left/top edge is NOT inside. Watch this in custom hit-testing.

### Drawing primitives (protected/package on `WurmComponent`)

| Call | Draws |
|------|-------|
| `fillRect(queue, r,g,b,a, x,y,w,h)` | solid alpha-blended quad |
| `fillInvertRect(...)` | `DSTINVERT` quad (text-selection highlight) |
| `drawTexture(queue, tex, r,g,b,a, x,y,w,h, u,v,uw,vh)` | textured quad; **UVs are /256** |
| `drawTexTilingH/V(...)` | tiling variants |
| text | `text.moveTo(x, yBaseline)` then `text.paint(queue, s, r,g,b,a)` — baseline is the glyph bottom, so draw at `y + text.getHeight()` |
| raw geometry | `queue.reservePrimitive()` → set `type` (`LINES`/`TRIANGLESTRIP`), `vertex` (a `VertexBuffer`), `num`, colors, `clipRect = hud.scissor.getCurrent()` → `queue.queue(prim, matrix)` |

Canonical raw-geometry examples: `WCheckBox.renderComponent`, `WurmDropDown.renderComponent`, and the
tree expand-box builder in `WurmTreeList$TreeListPanel`. VBOs via
`VertexBuffer.create(Usage.GUI, ...)` filled between `.lock()/.unlock()`.

### Coordinate space & clipping

One space: absolute screen pixels. Clipping is a scissor-rect stack (`hud.scissor`); `pushClip`
returns false when fully clipped (skip drawing).

## 2. Mouse input flow (`HeadsUpDisplay`)

Root dispatch: `public boolean mousePressed(int x, int y, int button, int clickCount)`.

1. `getTopComponentAt(x,y)` iterates the component list **back-to-front** (last added = topmost),
   returns first that `contains(x,y) && isAvailable()`. **Z-order = list order.**
2. `mouseFocusComponent = top.getComponentAt(x,y)` — recursive descent into children (each container
   implements `getComponentAt`; a leaf `FlexComponent` returns `this` if it contains the point).
3. If focus is a `WurmInputField` → keyboard focus + `startTyping`, else `stopTyping`.
4. Dispatch `leftPressed`/`rightPressed` to the focus component.

Release → `mouseReleased` → `leftReleased`/`rightReleased`, or `itemDropped` if a drag is active.
Drag → `mouseDragged`. Hover/tooltip path → `pick(PickData, x, y)`.

## 3. `WurmTreeList` / `TreeListItem` — the important one

Files: `WurmTreeList.java`, `TreeListItem.java`, `WTreeListNode.java`.

- `public class WurmTreeList<T extends TreeListItem> extends WurmBorderPanel implements ButtonListener`
  — **class is public but every constructor is package-private.** You cannot `new` one outside the
  gui package. Row model: `List<WTreeListNode<T>> lines` (flattened visible rows, rebuilt by
  `recalcLines()`); each node has `depth/isOpen/isSelected/isHovered` + `final T item`.
- Rendering is one monolithic method: **`TreeListPanel.renderComponent(Queue, float)`** (package-
  private non-static inner class — cannot be usefully subclassed). Sequence: compute visible window
  from `hud.scissor` ÷ `lineHeight` (`= text.getHeight()+1`); row backgrounds via `fillRect`
  (selected = blue `0,0,1,.2`; odd rows `0,0,0,.1`; hover = white); **name column** (expand boxes as
  `LINES` VBO + `text.paint(getName())` + 16×16 `drawTexture(getIcon())`); **secondary columns**
  (checkbox from static VBO when `hasCheckbox(i)`, else `getParameter(i)` text, right-aligned if it
  matches `\d*\.?\d*`).

### Hit-testing (in `TreeListPanel`)

- `getNodeAt(x,y)`: **Y-only** — `(y - this.y) / lineHeight`. Row pick ignores X.
- `leftPressed(x,y,clickCount)`: expand-box check (`depth*16 + x - 16` .. +16) → checkbox loop
  (`startX = x + width - 4 - commonWidth; curX = startX + 8`, tests `hasCheckbox(i)` +
  x∈[curX,curX+16]) → selection (shift-range/ctrl-multi) → `item.leftClick(x,y)`.
- `rightPressed` → select → `item.rightClick(x,y)`.
- **Rendering vs hit-test geometry are not perfectly aligned** (draw uses `curX+12`, hit uses
  `curX+8`). A button-in-cell must compute and own its own rect, not trust these constants.

### `TreeListItem` overridables (package-private/protected; default no-op unless noted)

`getName()` (abstract), `getParameter(int)` (abstract), `compareTo(TreeListItem,int)` (abstract),
`getIcon()`, `getImpIcon()`, `getTemperatureIndex()`, `isContainer()` (shows expand box),
`getDraggable()` (enables drag), `leftClick(x,y)`, `rightClick(x,y)`, `doubleClick`, `orderChanged`,
`getHoverDescription(PickData)`, checkbox hooks `hasCheckbox(int)/getChecked(int)/setChecked(int,b)`,
color hooks `getR/G/B`, `getSecondaryR/G/B(int)`, setters `setCustomColor`, `setColumnColor(int,…)`.

### Interactable button in a cell — the recipe

No built-in hook. Two viable layers:
- **Behavior (no bytecode patch):** override `TreeListItem.leftClick(x,y)` / `rightClick(x,y)` in your
  `T` subclass — it already gets absolute mouse coords for any non-checkbox click on the row. Compute
  your button's column rect (mirror the `leftPressed` column loop) and act if the click lands in it.
  To suppress row-selection on that click, you must patch `leftPressed` to `return` early (mirror the
  checkbox branch).
- **Appearance:** cheapest = return a glyph string from `getParameter(i)` or a button-like texture
  from `getIcon()`/`getImpIcon()` (static look, zero patching). Pixel-accurate custom drawing =
  javassist `insertAfter` on `TreeListPanel.renderComponent` appending `drawTexture`/`fillRect`/text
  over `WurmTreeList.this.lines[start..end]`.

## 4. Widget nuances (the ones that bite)

### `WurmInputField` (**final**, pkg-private)

- Ctors: `(String name, InputFieldListener)` (maxLines=1, maxChars=−1); `(name, listener, maxLines,
  maxChars)`. `maxLines==1` single-line + history; `<0` auto-grow multi-line; `>1` fixed multi-line.
- **`String prompt = "] "`** (pkg field) — prepended to rendered text; set `prompt = ""` for a plain
  box or you get a stray bracket.
- **`boolean simpleInput = false`** (pkg field) — when **`false`** (default), Enter archives to
  history and **clears the text** (`this.input = ""`); `handleInput(originalInput)` still fires with
  the pre-clear value. When **`true`**, Enter keeps the text (real form-field behavior) and skips
  history. **Setting `simpleInput = true` is the clean fix for the "field loses its value on Enter"
  problem** — no shadow-string mirror needed; `getText()` always returns the current value.
- Listener `InputFieldListener` (pkg-private interface, 3 methods): `handleInput(String)` on Enter
  (value BEFORE any clear), `handleInputChanged(WurmInputField, String)` on every edit,
  `handleEscape(WurmInputField)` on Esc. All null-guarded.
- Members (pkg-private): `getText()`, `setText(s)`, `setTextMoveToEnd(s)`, `setTextAndSelect(s)`,
  `setMaxInput`, `setPenColor(r,g,b)`, `setBackgroundColor(r,g,b)`, `setBackgroundTexture(theme)`,
  `enabled`. Clipboard Ctrl+C/X/V and Ctrl+A built in.
- **Cannot subclass (final).** Reusable helpers must **wrap** an instance and implement the listener.

### `WButton` (pkg-private)

- Ctors: `(label)`, `(label, h, v)`, `(label, ButtonListener)`, `(label, ButtonListener, h, v)`,
  `(label, ButtonListener, confirmQuestion)`, `(label, ButtonListener, confirmMessage, confirmQuestion)`.
- **Listener fires twice:** `buttonPressed` on press, `buttonClicked` on release **only if released
  over the button**. Put the action in `buttonClicked`.
- `setLabel` auto-resizes width. `hoverMode` draws texture only while hovered (popup items).
  `setEnabled(false)` = dimmed + unclickable. `setDown(bool)` = pressed look (used for active
  segmented toggle). `setTextColor`, `setHoverString`.

### `WurmDropDown` (pkg-private, **public ctor** `(String name, int value, String[] options)`)

- **No change listener.** Selection commits via `setValue(int)`; there is no observer. Either poll
  `getValue()` in `gameTick`, or (since it's non-final) subclass and override `setValue` to fire a
  callback — the `VaultDropDown` pattern in AuctionWindow. Auto-sizes to widest option; `<empty>` if 0.

### `WCheckBox` (**final**, pkg-private)

- Ctor `(String label)`. **No listener** — read the `checked` (pkg) field or poll. Toggles only on a
  click within the 16px box (not the label). Optional confirm dialogs via `setConfirm(...)`.
  `setCustomColor`, `setHoverString`, `enabled`.

### `WurmLabel` (pkg-private)

- Ctors `(label)`, `(label, tooltip)`, `(label, tooltip, bg)`, `(label, tooltip, bg, parent)`.
- **`filledBg` is dead** — the background is never drawn regardless of the flag; only text is painted,
  **always white** (hardcoded). `setLabel` does NOT resize (width frozen at ctor). Use a `WurmPanel`
  or custom draw if you need a colored label background.

### `WurmDecorator` (pkg-private, extends `ContainerComponent`)

- Wraps one `FlexComponent` with `align`: `FILL=0, LEFT=1, RIGHT=2, CENTER=3`. `pack()` sizes to
  child. **Centering only works inside a stretching border slot** (array panels shrink-wrap → no
  slack). `renderComponent` calls child's `renderComponent` directly (skips the child's own clip).

### `WurmBorderPanel` (pkg-private) — `NORTH=0, EAST=1, SOUTH=2, WEST=3, CENTER=4`

- N/S stretch full width (height kept); E/W keep width (height stretched); CENTER fills. `shrinkWrap`
  sizes to contents. `setComponent(FlexComponent, dir)`. `setColor` cascades. `setComponent(comp,
  SOUTH)` needs a SouthBar or it warns + no-ops.

### `WurmArrayPanel<T extends FlexComponent>` (pkg-private)

- `DIR_VERTICAL=0, DIR_HORIZONTAL=1, DIR_VERTICAL_INV=2`. Ctors incl. `(dir)`, `(name, dir)`,
  `(name, dir, autoWidth)`, `(dir, w, h)` (fixed box). **Shrink-wraps to children.** `autoWidth`
  (vertical) forces children to panel width — **but if the parent is a `WurmScrollPanel` with a
  grandparent, width is forced to `grandparent.width − 24`** (scrollbar allowance) — surprising when
  nested elsewhere. `componentWidthOffset` = inter-item spacing (the only "padding" knob).

### `WurmPopup` (**final**, pkg-private) — context menus

- Ctors `(id)`, `(id, title, x, y)`, `(id, title, x, y, boolean offset)`. **`offset=true` jitters
  position −16..0px** — pass `false` for deterministic placement. `title` field + `addButton` /
  `addSeparator` / `addHelpButton` / `addTimer` are pkg-private.
- **Nested button types are PUBLIC** (the extension point): `public abstract WPopupLiveButton` (override
  `protected abstract void handleLeftClick()`, ctors `(String)` / `(String, WurmPopup submenu)`),
  `public final WPopupActionButton` (wraps a `PlayerAction`), `WPopupDeadButton` (inert). Create via
  `popup.new WPopupLiveButton("X"){ … }` from in-package code (addButton is pkg-private). Show with
  `hud.showPopupComponent(popup)`.

### `BmlWindowComponent` (public final class, **package-private ctors**, extends `WWindow`)

- The declarative whole-window path: describe UI in a BML string, parse to widgets keyed by id. Ctors
  `(title, String bml, BmlWindowListener)` / `(title, org.w3c.dom.Document, listener)` are
  **package-private** → construct from gui-package code. Errors render an inline error panel rather than
  throwing. Read results via `buildOutMap()`. Manage lifecycle with `hud.addDynamicComponent` /
  `removeDynamicComponent`. **Full treatment: [bml-dialogs](bml-dialogs.md).**

## 5. HUD, windows, fonts, drag, layout

- **HUD registration:** `hud.addComponent(WurmComponent)` is **private**; mods register via
  `hud.mainMenu.registerComponent(name, comp)` / `hud.hudSettings.registerComponent(name, comp)` (both
  public — the latter also persists window position). `hud.getComponents()` = live list;
  `setActiveWindow(comp)` raises z-order. Popups: `hud.showPopupComponent`,
  `hud.showDropdownPopupComponent`, `hud.clearAllPopups`. Messages: `hud.textMessage(":Event", …)`.
- **`WWindow`** (pkg-private) — base draggable/resizable/closeable window. Constants `TOP_BAR_HEIGHT=21`,
  `SOUTH_BAR_HEIGHT=16`, `SIDE_BAR_WIDTH=3`, `MINIMIZED_HEIGHT=25`. `setComponent(FlexComponent)` builds
  chrome; `setTitle/getTitle`, `resizable/closeable/minimized/rememberOpenStatus`, `toggleSize()`.
  Override `closePressed()` for the X. Owns a `DragController`.
- **Window persistence:** `interface WindowSerializer { restorePositionHints(WindowPosition);
  WindowPosition createPositionHints(); }` (public); `WWindow` implements it. `HudSettings.registerComponent`
  persists geometry across sessions.
- **Fonts (`renderer/gui/text/TextFont`, public):** `getText/getBoldText/getMonospaced/getHeaderText/…`;
  measure `getWidth(String)`, `getHeight()`, `getAscent/Descent/Leading`; render `moveTo(x,yBaseline)` +
  `paint(queue, s[, r,g,b,a])`. `WurmComponent.text` = default, `.textBold` = bold.
- **Icons/textures:** `IconLoader.getIcon((short) imageNumber)` — the **image number**, not template
  id, sent by the server. Skin atlas UVs are /256. `ResourceTextureLoader.getNearestTexture(key)` for
  custom skins.
- **Drag & drop:** `interface DraggableComponent { getHoverDescription(PickData); Texture getIcon();
  int getIconSize(); }` (pkg-private); `TreeListItem` implements it. Row drag: override `getDraggable()`
  → `TreeListPanel.mouseDragged` calls `hud.startDrag` past 5px. Drop arrives at `itemDropped(x,y,
  DraggableComponent)` on the component under the cursor (default bubbles — override to accept).
  Concrete drag payloads seen: `InventoryTreeListItem`, `InventoryContainerItem`, `PaperDollItem`;
  expand folded groups with `getSelectedCommandTargets()`.
- **Layout:** `FlexComponent.sizeFlags` `FIXED_WIDTH=1`, `FIXED_HEIGHT=2` (set → incoming size ignored).
  Override `performLayout()`, not `layout()` (which guards reentrancy via `volatile inLayout`).
  `childResized(FlexComponent)` propagates up. `setInitialSize(...)` does font-scaled + screen-relative
  first-open placement.

## 6. More widgets & patterns (from the iter-9 exploration pass)

These are documented as **reference knowledge**, not wrapped — most have zero current mod consumers, so
an SDK wrapper wouldn't clear the promotion bar. Reach for them directly when needed.

- **`ConfirmWindow` (public class, pkg-private ctors) + `ConfirmListener` (public iface,
  `confirmed()`/`cancelled()`)** — the vanilla yes/no modal. Ctor `ConfirmWindow(ConfirmListener, msg,
  question[, x, y])` **self-registers** via `hud.addDynamicComponent(this)`; the caller must call
  `close()` to remove it. `closeable=false` (the X is unreachable); `closePressed()` maps to
  `cancelled()`. **`WButton` with a confirm question does NOT auto-show the dialog** (it just fires
  `buttonClicked` — the owner must build it), whereas **`WCheckBox` DOES** pop one. A `KitConfirm`
  wrapper is a backlog candidate (hold — no consumer yet).
- **Tooltips are uniform: override `pick(PickData, x, y)` and call `pickData.addText(String)`.** The
  convenience setter `setHoverString(String)` exists on `WButton`, `WCheckBox`, `WurmRadioButton`,
  `WurmImage`. A custom component adds a tooltip by overriding `pick`. (`TextureButton.pick` calls
  `pickData.reset()` first.)
- **`WurmScrollPanel` (pkg-private, NOT final)** — the general scrolling container. Ctor
  `(name, FlexComponent content, boolean hHScroll, boolean hVScroll)`; wheel handled (8px steps);
  `stickyBottom`, `scrollDownToBottom()`, `scrollDownTo(int)`. Ties into the §4 `autoWidth` note: an
  `autoWidth` vertical array **inside** a scroll panel is forced to `grandparent.width − 24` (scrollbar
  allowance).
- **`WurmGridPanel` (pkg-private final)** — the only 2-D layout: `(name, w, h)` + `addComponent(comp, x,
  y)`, cells sized by proportional integer division. `WurmArrayPanel` is 1-D only.
- **`WurmHeader` (pkg-private final)** — the answer to `WurmLabel`'s "always white" limitation: uses
  `TextFont.getHeaderText()` and supports **colored** text via `(label, r, g, b)`, `isCenterable()`. But
  like `WurmLabel`, `setLabel` does NOT resize (width frozen at ctor).
- **`WTextureButton` (PUBLIC class + PUBLIC ctor) / `TextureButton` (fully PUBLIC)** — rare public escape
  hatches usable from **any** package (no in-package/bytecode dance). `WTextureButton(label, hover,
  ResourceTexture, ButtonListener)` extends `WButton`. `TextureButton` is standalone (not a
  `WurmComponent`): you drive `render`/`pick`/`gameTick(windowX, windowY)` yourself with window-relative
  coords; supports sprite-sheet UV offset.
- **`WurmImage` (pkg-private)** — image-by-resource-name with async HTTP loading (`http://` names load
  via `HttpTextureBuilder` + `TextureLoadListener`); optional 1px border (adds 2×3 px — watch layout),
  optional overlay, tooltip via `pick`.
- **`WurmProgressBar` (pkg-private final)** — `setProgress(float 0–1, String centeredText, boolean
  colorGreen)` (clamps), `setCustomHeight`, `setRenderbackground`. Trivial craft/download progress.
- **`RadioButtonGroup` footgun** — `WurmRadioButton`'s ctor does NOT register with its group; you must
  call `group.add(button)` or `getCheckedRadioButton()` never sees it (and falls back to
  `radioButtons.get(0)`, throwing if empty). No change listener (poll). **Superseded by
  `SegmentedButtons`** for single-select.
- **`WurmItemPlate` — avoid.** Half-finished vanilla code (hardcoded placeholder strings;
  `removeFakeInventoryItem` compares `item.getId() == item.getId()`, always true).
- **`gameTick` polling is the sanctioned pattern for listener-less widgets.** `WurmDropDown` (→
  `KitDropDown`), `WCheckBox` (final — can't subclass), `WurmTreeList` selection, and quote-on-idle all
  poll their state in `gameTick` because there's no change event. The `PollWatch` backlog candidate
  would generalise this.

## See also

- [custom-rendering](custom-rendering.md) — the draw helpers (`fillRect`/`drawTexture`/text) and raw-`Primitive` path in depth.
- [private-class-techniques](private-class-techniques.md) — how to reach the pkg-private/private members above.
- [ui-component-backlog](../ui-component-backlog.md) — what we're building from this knowledge.
