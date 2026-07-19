# UI component backlog (live tracker)

> **Status (as of iter 17): SDK complete & at steady state.** 15 components, all audited; specs
> (GUI internals, custom rendering, private-class techniques), a verified worked example
> (`examples/`), a committed patch-verification tool (`examples/verify/PatchVerify.java`), and a
> passed consistency sweep. Two exploration passes found no new component that clears the promotion
> bar; held items (#16–18) await a real 2nd consumer. **Decision pending (user away):** adopt
> components into the actual mods (edits shipping source — needs go-ahead) · stop the loop
> (`CronDelete 68ccdb50`) · keep idling with regression-only checks. No speculative work will be
> manufactured to fill cycles.

The working queue for the "grow the SDK" effort. Ranked by **recurrence × pain × infrastructure-value**
(business-logic-free wins). Each item ties to real call sites so nothing is speculative. Update the
**Status** as work moves; when a component ships, also update
[component-catalog.md](component-catalog.md), the top-level README table, and add a
[components/](components/) doc.

**Status legend:** `pending` · `in-progress` · `done` · `blocked` · `hold` (below the promotion bar
until a second consumer appears) · `dropped`.

## Post-loop (user-directed)

- **Mixed-content item window (`KitItemList` + `KitWindow`).** User wanted the SDK able to *show* a
  window that mixes a real item-container tree with other widgets (server sends the open packet — out of
  scope here). Exploration of `Client_vf` found the load-bearing fact: **the item list is not a window** —
  `InventoryListComponent` is a public, embeddable `FlexComponent` (a `WurmDecorator`) with a public ctor,
  and it already carries icons/columns/folded-stacks/live-updates/native-drag/right-click-actions. Vanilla
  item windows are items-only only by layout choice, not constraint. So:
  - **`KitItemList`** (gui) — a thin package-safe front over `new InventoryListComponent(view,…)` + the two
    view lookups (`playerInventory()`, guarded `container(long)`); exposes `component()` + `destroy()`.
    Zero behavior reimplemented (Option A from the exploration).
  - **`KitWindow`** (gui) — a `WWindow` whose content is an inner 5-region border panel, so an item list
    goes in one region and other widgets in the rest without disturbing the title-bar chrome.
  - Demo gained a **Live items** tab embedding a `KitItemList` over the player inventory beside a widget.
  - Compile-verified against real client+modloader jars (SDK + demo build, EXIT 0). No new javassist
    patches. Runtime pixels/drag/right-click need the play-test.

- **Two fixes from the first in-client run of `wckdemo`:**
  1. **Column mapping bug (demo + doc).** `WurmTreeList`'s `colWidths`/`colNames` are the **extra columns
     only, 0-based**; the name column is a hardcoded "Name" filling CENTER (verified `WurmTreeList.java:121-133`).
     The demo passed a 4-name array treating `[0]` as the name column → every header shifted right. Fixed
     the demo (`{"Price","QL",""}`) and corrected `KitTreeItem.md` (an earlier audit note wrongly said the
     arrays included column 0).
  2. **Cell buttons now render as real buttons.** They were accent-coloured *text*. Extended
     `installTreeCellButtons` with an `insertAfter` on `TreeListPanel.renderComponent` that draws the
     vanilla `WButton` 3-slice skin (`panelTexture` caps + `panelTextureTilingH` middle) behind each
     cell-button label. Offline-verified against the real client (TREECELL_OK); demo jar rebuilt. Pixels
     still need the play-test.


- **Built-in test suite (`demo/`)** — *removed during the monorepo consolidation.* It was a
  throwaway console-driven mod (`wckdemo` command) that opened a window exercising every component
  with local data, used to verify the SDK live in the client before migrating Crux onto it.


- **Fail-proofing audit (3 parallel passes, all vs real `Client_vf` source + offline patch harness).**
  Every vanilla symbol the SDK touches (ctors, fields, methods, constants, tab-skin UVs, the
  `connection`/`getBuffer`/`reallySend` send path, the tree `this$0`/`columnWidths`/`commonWidth`/
  `hasImpColumn` + render geometry, every override) traced to real `file:line` and **VERIFIED** — no
  hallucinated/mismatched/inaccessible symbols; the two highest-risk assumptions (send path, tree
  geometry) hold; both javassist patches provably compile against the real client jar.
  - **MEDIUM (fixed):** the input-field Down-arrow patch was too broad — a `FieldAccess` editor blocked
    *every* empty write to `input` in `keyPressed`, so Backspace/Delete-to-empty stuck at 1 char on any
    `simpleInput` field; and the "vanilla never sets `simpleInput`" premise was false
    (`BmlWindowComponent:200` sets it). Rewrote to a narrow `insertBefore`:
    `if ($1 == 208 && $0.simpleInput && $0.maxLines == 1) return;` — no-ops Down only, touches nothing
    else, correct for vanilla BML fields too. Re-verified offline (INPUTFIELD_OK).
  - **LOW (fixed):** `KitDropDown` null `options` → deferred render NPE → added `requireNonNull`.
  - **Docs corrected:** GuiPatches/KitInputField (narrow fix + BML-sets-simpleInput), private-class spec
    (constant rename), KitDropDown non-null, PacketCodec 64KB-string/malformed-length bounds.
  - **Confirmed non-issues:** NaN-sort reverses under descending (valid total order, correct reversal);
    ValueLineBox needs a width-bearing ancestor (documented); Format locale decimal (documented);
    `compareTo`==0 for mixed non-Kit rows (not intended usage); KitTab height-arg ignored (vanilla-faithful).


- **Tabs framework** — user asked for proper **tabs in a window that keeps its title bar** (not the
  gold-text buttons `KitTabbedWindow` had). Built **`KitTab`** (a `WButton` rendering the real vanilla
  tab skin — replicated `TabButton`'s 3-slice `panelTexture` draw, no `AbstractTab` chat coupling),
  **`KitTabPanel`** (reusable tab container — strip NORTH + swapped content CENTER — droppable into any
  slot; exactly-one-active like `SegmentedButtons`), and refactored **`KitTabbedWindow`** to keep the
  `WWindow` title bar and delegate to a `KitTabPanel` (layout: [title]/[tab strip]/[content]; API
  preserved). Build 0 errors; example integration-compiles. Adversarial review: rendering faithful to
  vanilla, state machine sound; fixed its one finding — tab text now selected=white/unselected=black
  (matching vanilla `setNormalColor`) instead of always-white (legibility on the light skin). Rendering
  itself is compile-verified only; needs a play-test to eyeball pixels.

## Queue

| # | Component | Status | Package | Recurrence (proof) | Notes |
|---|-----------|--------|---------|--------------------|-------|
| 1 | **`KitInputField`** — self-listening input wrapper: clears prompt, `simpleInput=true` (no Enter-clear), committed-value mirror, typed accessors (`asLong/asInt/asDouble` + `*OrNull`), `onSubmit/onChange/onEscape`, `setEnabled/maxChars`. + **`GuiPatches.installInputFieldFix()`** for the Down-arrow wipe | **done** | gui + clientkit | AuctionWindow (~9 shadow fields), BuyOrderWindow (4) | Eliminates the single most-copied hack. `WurmInputField` is `final` → wrapper, not subclass. Adversarial review caught the Down-arrow clear → fixed via bytecode patch. |
| 2 | **`KitDropDown`** — `WurmDropDown` subclass overriding `setValue` to fire an `onChange` callback (+ `select`/`selectSilently`/`selected`) | **done** | gui | AuctionWindow (`VaultDropDown` subclass) vs BuyOrderWindow (polls in gameTick) | Roadmap "WurmDropDown change-listener". `WurmDropDown` non-final → subclass OK. Fires only on actual change. |
| 3 | **`ValueLineBox`** — optional-header vertical stack; renders only non-blank lines, collapses when empty; pooled labels; `set(...)` / `set(List)` | **done** | gui | `AuctionPricingBox` shared by Sell tab + BuyOrderWindow | Generalized `AuctionPricingBox` (fixed 3 lines + fixed header → any count + optional header). |
| 4 | **`KitTreeItem` extension** — container/expandable rows (`isContainer`) + numeric-column sort (`sortKey`/`sortKeys`) + **fixed** `compareTo` column-index off-by-one | **done** | gui | AuctionRow (numeric sort), AuctionCategoryRow (`isContainer`) | Extended existing component. Also fixed a latent bug: `col <= 0` treated first-extra column as name-sort. |
| 5 | **Tree cell button** — interactable button in a `WurmTreeList` cell (the user's headline example): `KitTreeItem.isCellButton`/`cellButtonClicked` + accent color + `GuiPatches.installTreeCellButtons()` | **done** | gui + clientkit | none yet (net-new capability) | Bytecode patch on `TreeListPanel.leftPressed` mirroring the checkbox branch; geometry matches render base; verified offline (javassist harness → PATCH_OK). |
| 6 | **`SegmentedButtons`** — exactly-one-active button group, pressed-look active, `onSelect`/`select`/`selectSilently`/`selected` | **done** | gui | AuctionWindow (Per Unit/Total, `setDown`+dim) + BuyOrderWindow (2 pairs, `"> "` labels) | Unifies two divergent hand-rolled idioms via `WButton.setDown`. |
| 7 | **`PagerRow`** — centered `|< < Page X of Y > >|` bound to (page, totalPages); `onPage`/`setState`; buttons disable at ends | **done** | gui | AuctionWindow `buildPagerRow` + `gotoPage`/`updatePageLabel` | Unidirectional: click→onPage→request; response→setState (no re-fire). Width-reserved label. |
| 8 | **`GuiKit` spacing helpers** — `row(name, gap)` (sets `componentWidthOffset`) + `spacer(w, h)` transparent gap | **done** | gui | AuctionWindow `row()`, `spacer()` | Trivial factories added to `GuiKit`. |
| 9 | **`Collapsible`** — position-stable show/hide wrapper (slot stays in parent, child toggles inside) | **done** | gui | AuctionWindow `setPartialVisible`, dropdown rebuilds | Chose a wrapper over a naive `GuiKit.setVisible`: `WurmArrayPanel` has no insert-at-index, so remove/re-add-to-parent scrambles order. Wrapper preserves position. |
| 10 | **`KitTreeList` selection helper** — programmatic single-select over `lines` + change callback | pending | gui | AuctionWindow `selectCategoryRow`/`pollCategorySelection`, BuyOrderWindow `pollPickerSelection` | Overlaps gameTick-polling roadmap; the programmatic-select part is new. |
| 11 | **`WPopupBuilder`** — fluent `WurmPopup` builder: `button(label, Runnable)`/`label`/`separator`/`show`; hides the pkg-private inner-class boilerplate | **done** | gui | RecipeExamineHelper | Submenu deferred (unverified vanilla behavior). Builder is lambda-free (anonymous button). |
| 12 | **`bmlPrompt(title, bml, onSubmit)`** — wrap `BmlWindowComponent` + listener + dynamic-component lifecycle + `buildOutMap` | hold | gui | AuctionWindow `promptSellAmount` (1 site) | Below bar (single site); roadmap flags possible over-engineering. Promote on 2nd consumer. |
| 13 | **`:Event` message helper** — `event/warn/error` over `hud.textMessage(":Event", …)` | hold | gui | AuctionWindow `eventLog/warnSell/showProtocolError` (1 mod) | Trivial; promote on 2nd consumer. |
| 14 | **Drop-target resolver** — `resolveDroppedItemIds(DraggableComponent)` across the 3 drag payload types + group expansion | hold | gui | AuctionWindow `itemDropped`/`droppedItemIds` (1 mod) | Roadmap covers drag *source*; this is the *target* side. Promote on 2nd consumer. |

## New candidates (iter-9 exploration pass)

Ranked by the exploration agent, cross-checked against what's built. Tier-1 cleared the bar and is done.

| # | Component | Status | Package | Recurrence (proof) | Notes |
|---|-----------|--------|---------|--------------------|-------|
| 15 | **`Hooks`** — HookManager/javassist boilerplate (`get`/`method`/`appendClassPath`/`edit`/`replaceCall`) | **done** | clientkit | 7 sites incl. SDK (`ServerCommandMod`, `GuiPatches`) | Top new win — pure infra. Surfaced the invokedynamic constraint (referenced classes must be lambda-free). |
| 16 | **`KitConfirm`** — yes/no modal over `ConfirmWindow` (pkg-private ctor, self-registers, manual `close()`) | hold | gui | hostile API but 0 mod consumers | Below recurrence bar; promote on 1st real consumer. `WButton` w/ confirm does NOT auto-show a dialog (unlike `WCheckBox`). |
| 17 | **`PollWatch`** — value-supplier + `onChange`, ticked from `gameTick` (subsumes checkbox-change + tree-selection #10) | hold | gui | `WCheckBox` is `final` (no subclass); AuctionWindow/BuyOrderWindow poll checkboxes in gameTick | Generic listener-less-widget poller. Better than a checkbox-specific class. Promote when a 2nd poll site needs it. |
| 18 | **Config read helper** — `readInt(source, key, default, min, max)` w/ warn + clamp | hold | clientkit | WideSkillColumn, MaxActions, DirectConnect (3×, divergent sources) | Recurs 3× but each reads a different source; shared core tiny → marginal. Low priority. |
| 19 | **`KeybindMod`** — patch `WurmConsole.handleInput2` for a custom console verb + `bind(key, Runnable)` helper | hold | clientkit | none (no mod binds keys) | Mechanism fully documented in [specs/keybindings-and-input.md](specs/keybindings-and-input.md). Build when a mod first needs a key→own-code binding. |

Tier-3 vanilla widgets (`WurmProgressBar`, `WurmGridPanel`, `WurmScrollPanel`, `WurmRadioButton`,
`WTextureButton`, `WurmItemPlate`, `WurmHeader`, `WurmImage`) have **0 consumers** → captured as spec
nuances in [specs/client-gui-internals.md](specs/client-gui-internals.md), not built. `WurmRadioButton`
is superseded by `SegmentedButtons`; `WurmItemPlate` is buggy vanilla code to avoid.

## Non-UI infra assessment (iter-12 exploration) — nothing clears the bar

Assessed against the strict bar (2+ *user-mod* consumers OR real vanilla hostility). All **don't-build**:

| Candidate | Verdict | Why |
|-----------|---------|-----|
| ModComm channel base | don't-build | Only consumer is the `serverpacks` vanilla fork; every user mod uses raw CMD via the shipped `ServerCommandMod`. Zero user-mod consumers. |
| Reflection-hook (`registerHook`) helper | don't-build | Both consumers (`custommap`, `serverpacks`) are vanilla forks; user mods all use direct bytecode (served by `Hooks`). And `registerHook` isn't a hostile API. |
| Console-command registration | don't-build | Only `serverpacks` (fork) registers one. Crux's `ConsoleListener` impl is dead code (never calls `addConsoleListener`); no delegate implements it. |
| `sendAction` helper | don't-build | Zero occurrences of `sendAction`/`PlayerAction` anywhere in `ClientMods/`. |
| `Configurable` config-helper (#18) | hold (unchanged) | One real `.properties` consumer (MaxActions); the other two read JVM `System.getProperty` (different source). Marginal. |

**Conclusion: the SDK is at a stable, well-covered state.** Custom-command plumbing (`ServerCommandMod`),
codec (`Packet*`), formatting (`Format`), javassist/HookManager boilerplate (`Hooks`), opt-in widget
patches (`GuiPatches`), and the full GUI widget set cover every idiom that actually recurs across the
user's mods. Held items (#12–18) stay held pending a real second consumer — building any would be
speculative.

**Loop pivot:** with the component queue exhausted and a second pass confirming no justified new
component, future iterations shift from *building components* to the other two explicit asks —
**deepening the specs** (client-source nuances, rendering pipeline, protocol/packet reference,
private-class recipes) and any **maintenance/hardening** — done only where genuinely useful, not as
padding. New component work resumes if a mod grows a need that clears the bar.

## Cleanups (not new components, but on the list)

| Component | Status | Action |
|-----------|--------|--------|
| `AuctionFormat` / `AuctionRow.formatQl` duplicate `Format` | pending | Replace with `com.wurmonline.clientkit.Format`; delete dups. |
| AuctionWindow hand-rolled tab strip vs `KitTabbedWindow` | pending | Adopt `KitTabbedWindow` (needs themable active-tab style — see roadmap). |
| AuctionRow/CategoryRow extend `TreeListItem` not `KitTreeItem` | pending | Migrate after #4 (container + numeric sort) lands. |

> These cleanups touch a **shipping mod's source** (AuctionHouse/Crux). Do them only as deliberate,
> separately-audited changes — not drive-by edits during SDK work.

## How this feeds the loop

Each loop iteration: gather (subagents) → update this tracker + [specs/](specs/) → rank → implement
the top `pending` item → validate/audit → mark `done`. New findings append rows here; re-rank when the
proof base changes.

## Changelog

- **Iter 1** — created tracker + [specs/client-gui-internals.md](specs/client-gui-internals.md) +
  [specs/private-class-techniques.md](specs/private-class-techniques.md). Implemented **#1
  `KitInputField`** + **`GuiPatches`** (Down-arrow bytecode fix). Findings from 2 exploration agents;
  adversarial review agent caught the Down-arrow text-wipe (`simpleInput` doesn't guard `keyPressed`
  case 208) → fixed with a `simpleInput`-gated `ExprEditor` patch; also added `setEnabled`/`maxChars`.
  Build: 0 errors.
- **Iter 2** — implemented **#2 `KitDropDown`** (subclass of `WurmDropDown`, overrides package-private
  `setValue` to fire `onChange`; `select`/`selectSilently`/`selected`; fires only on actual change).
  Verified against source that the popup commits via `setValue` (WurmDropdownPopup:77). Adversarial
  review caught that `select()` widened the path to an out-of-range index → per-frame render crash;
  fixed by clamping every value at the `setValue` choke point + added `optionCount()`/`selectedText()`.
  Build: 0 errors.
- **Iter 3** — implemented **#3 `ValueLineBox`** (generalized `AuctionPricingBox`: optional header, any
  number of lines, pooled/reused labels, collapse-when-empty, `set(...)`/`set(List)`). Read the proven
  source to preserve behavior; `autoWidth` array to avoid `WurmLabel` clip. Adversarial review confirmed
  faithful behavior + correct pooling; fixed an overstated `autoWidth` comment/doc (it stretches short
  lines up but doesn't grow the box for a long line → needs an ancestor-imposed width) and guarded
  `set(List)` against a null list. Build: 0 errors.
- **Iter 4** — extended **#4 `KitTreeItem`**: `container` flag (`isContainer`), numeric `sortKey(int)` +
  `sortKeys[]` ctor, and **fixed** a real column-index bug (`col <= 0` treated the first extra column
  as name-sort; the tree uses `-2`/`-3` for name/imp and 0-based for extras). Made the mixed numeric/
  NaN order total to protect `List.sort`'s comparator contract. Verified index scheme against
  `WurmTreeList` TreeListButton (-2 name, 0.. extras). Build: 0 errors. Review confirmed the index fix,
  `isContainer`, ctor chaining, access all correct; the transitivity fix I applied preemptively matches
  the reviewer's exact recommendation (total order); tightened ctor/`sortKey` JavaDocs to match.
- **Iter 5** — implemented **#5 tree-cell interactable button** (the headline example): `KitTreeItem`
  `isCellButton(col)`/`cellButtonClicked(col)` + gold accent via `getSecondaryR/G/B`, routed by a new
  `GuiPatches.installTreeCellButtons()` bytecode patch on `WurmTreeList$TreeListPanel.leftPressed`
  (mirrors the checkbox branch; consumes the click; geometry matches the render base incl. imp column).
  **Verified the runtime-compiled javassist offline** with a javassist harness against the real client
  jar (`this.this$0.columnWidths` resolves → PATCH_OK) — new reusable technique documented in
  specs/private-class-techniques.md. Build: 0 errors. Review confirmed geometry/consumption/safety/index
  all correct (patch dodged vanilla's own checkbox-offset bug); caught a Medium **double-fire on
  double-click** → fixed by gating dispatch on `clickCount < 2` while still consuming both presses, plus
  catch-and-log around the handler. Re-verified the amended patch offline (harness string byte-matches
  shipped, PATCH_OK).
- **Iter 6** — implemented **#6 `SegmentedButtons`** (exactly-one-active toggle via `WButton.setDown`;
  `onSelect`/`select`/`selectSilently`/`selected`; first segment auto-active; fires only on change).
  Verified `setDown` gives a persistent pressed look and `WButton` has no `equals` override (identity
  `indexOf`). Build: 0 errors. Review confirmed the invariant/reentrancy are sound; added `fireSelected()`
  for the "default selection never fires onSelect" trap and documented the disable-active and `panel()`
  caveats.
- **Iter 7** — implemented **#7 `PagerRow`** (centered `|< < Page X of Y > >|` bound to (page,
  totalPages); `onPage`/`setState`; buttons disable at ends; change-guarded; width-reserved label so
  buttons don't shift). Unidirectional flow (click→onPage→request; response→setState, no re-fire).
  Verified `WurmPanel`/`WurmDecorator`/`WurmLabel.setLabel` signatures. Build: 0 errors. Review found it
  solid (an improvement over the auction — end-clicks are change-guarded, no redundant request); folded
  in the "call setState on every response" optimistic-advance contract and corrected the >9999
  overdraw wording.
- **Iter 8** — added **#8 `GuiKit` spacing helpers**: `row(name, gap)` (sets `componentWidthOffset`) and
  `spacer(w, h)` (transparent `WurmPanel` for vertical gaps, which `WurmArrayPanel` lacks). Trivial
  stateless factories — inline audit only (no dedicated review; risk-scaled). Build: 0 errors.
- **Iter 9** — implemented **#9 as `Collapsible`** (not the naive `GuiKit.setVisible`): a position-stable
  wrapper — a slot panel stays in the parent while the child toggles in/out, because `WurmArrayPanel`
  has no insert-at-index so remove/re-add-to-parent would scramble order. Verified add/removeAll both
  relayout. Build: 0 errors; review running. **Also kicked off a fresh exploration pass** (background)
  to replenish the backlog — the original survey's top items are now exhausted. Next: rank the new
  findings, then continue (**#10 tree-selection helper**, **#11 WPopupBuilder**, or higher-ranked new
  items). Collapsible review: production-solid (collapse/re-expand chain traced against vanilla, proven
  pattern); added a constructor null-guard (fail-fast) for the one Low finding.
- **Iter 10** — ranked the fresh exploration findings (new candidates #15–18 above; Tier-3 widgets →
  spec nuances). Implemented **#15 `Hooks`** (top new win: HookManager/javassist boilerplate, 7-site
  idiom). Attempted to dogfood it in `GuiPatches` via `Hooks.edit(() -> ...)` — **the offline harness
  caught that this broke the cell-button patch** (`invalid constant type: 18`): Java-8 lambdas compile
  to invokedynamic, and the client's javassist 3.12.1 can't parse a referenced class containing it.
  Reverted `GuiPatches` to lambda-free (both patches re-verified INPUTFIELD_OK + TREECELL_OK) and
  documented the **invokedynamic constraint** as a top gotcha in specs/private-class-techniques.md.
  #10/#11 (tree-selection / WPopupBuilder) now fold into #17 `PollWatch` (hold) / remain pending. Next:
  **#11 `WPopupBuilder`** (real 1-mod need, RecipeExamine) or add remaining spec nuances.
- **Iter 11** — implemented **#11 `WPopupBuilder`** (fluent `WurmPopup` builder: `button(label,
  Runnable)` via anonymous `WPopupLiveButton`, `label` via `WPopupDeadButton(text, null)`, `separator`,
  `show`). Deferred submenu (unverified vanilla open behavior — won't ship unverified). Lambda-free
  (anonymous button, javassist-safe) + documented the consumer-lambda caveat. Build: 0 errors; review
  running. **Ranked queue now largely cleared** — remaining are holds (#16–18) pending a 2nd consumer.
  Review verdict: "ship it" (all 7 checks confirmed vs source; skipped vanilla-show steps are inert);
  added a fail-fast null-guard on `button`'s Runnable. Next: another exploration pass, or adopt SDK
  components into the mods (cleanup list).
- **Iter 12** — UI ranked-queue is dry (all pending done; #16–18 on hold by design). Rather than force a
  marginal build, kicked off a focused **non-UI-infra exploration** (ModComm channel base, reflection-
  hook/`registerHook` helper, console commands, `sendAction`, `Configurable`) to assess whether any
  clear the strict promotion bar (2+ mod consumers OR real vanilla hostility) — most may be single-use.
  Awaiting ranked verdict; will build only what genuinely clears the bar, else report the SDK as
  stable/well-covered. **Verdict: nothing clears the bar** (see "Non-UI infra assessment" above) — the
  2-consumer candidates are vanilla forks, user-mod candidates are single-use. Built nothing (held the
  line). SDK declared stable/well-covered; loop pivots to spec-deepening + maintenance. No build,
  no new bugs — this cycle was a decision, correctly made.
- **Iter 13** — spec-deepening (loop pivot). Wrote [specs/custom-rendering.md](specs/custom-rendering.md):
  the draw helpers (`fillRect`/`fillInvertRect`/`drawTexture`/`drawTexTiling*`, UV /256 & /64 divisors),
  text (`moveTo`/`paint`, baseline), the raw-`Primitive` geometry path (`reservePrimitive` → set state →
  `queue.queue(prim, matrix)`), scissor clipping, and patching a vanilla `renderComponent`. Verified all
  signatures against `WurmComponent`/`WCheckBox` source. No code change. Next: continue spec-deepening
  (candidate: a protocol/packet reference tying `ServerCommandMod` to the vanilla `reallyHandle` dispatch
  + item opcodes) or await user direction.
- **Iter 14** — built a **verified worked example** under `examples/` (not shipped in the jar):
  `ExampleMod` (ServerCommandMod CMD −80 + GuiPatches + PacketReader/Writer) + `ExampleWindow`
  (KitTabbedWindow with a KitTreeItem tree — numeric sortKeys + "Buy" cell button — + PagerRow, and a
  filters tab with KitInputField/KitDropDown/SegmentedButtons/ValueLineBox/Collapsible/GuiKit/Format).
  **Compiled against the shipped jar + client jars → JAVAC_EXIT 0** — a full-surface integration test;
  every component's public API composes cleanly, no mismatch. Wrote [example-mod.md](example-mod.md);
  skipped a protocol spec (would duplicate wire-format/hooking-model/multi-mod concept docs — padding).
- **Iter 15** — operationalized offline patch verification. Extracted the two `GuiPatches` javassist
  strings to `public static final` constants and committed a **drift-proof** harness
  `examples/verify/PatchVerify.java` (references those constants → verifies the ACTUAL shipped patches
  compile under the client's javassist). **Found & fixed a real bug:** `Hooks.replaceCall` used a lambda
  (`edit(() -> ...)`), giving `Hooks.class` invokedynamic — contradicting its own documented lambda-free
  guarantee and unsafe if referenced from injected code; rewrote it lambda-free. Also mapped 3 verifier
  pitfalls (pin javassist 3.12.1; exclude modlauncher; run standalone) — documented in
  specs/private-class-techniques.md. **Dropped an automated `verify.sh`** as flaky: javassist 3.12.1
  false-fails in batch context where the identical standalone run passes — shipping a crying-wolf script
  is worse than a documented manual recipe. Patches confirmed correct (ALL_PATCHES_OK, pinned 3.12.1).
- **Iter 16** — holistic SDK consistency/QA audit (cross-cutting, vs the per-component audits): doc↔code
  coverage, API-signature accuracy, catalog/backlog accuracy, broken cross-links, convention consistency
  (callback naming, fluent returns, accessor names, null-guards, package placement), Java import hygiene,
  stale claims. **Result:** coverage/imports/cross-links/API-signatures clean; 12 drift items found &
  fixed. #1 (only copy-paste-breaking one) `KitTreeItem.md` documented `addItem` → `addTreeListItem`.
  Stale `gotchas.md` (WurmDropDown→KitDropDown; header-sort→`sortKey`); `roadmap.md` built items marked
  ✅shipped/⤫assessed; catalog coverage-snapshot + `Last synced` reconciled; `Format.md` inverted locale
  claim; `PatchVerify` javadoc `verify.sh`→standalone; GuiKit one-liners +`row`/`spacer` (3 files);
  `overview.md` layout completed. Code: `Objects.requireNonNull` on `KitTreeItem` name (real deferred
  NPE in `compareTo`); removed dead `KitTabbedWindow.activeButton`. Build 0 errors. SDK confirmed
  internally consistent — drift was prose lag, now closed.
- **Iter 24** — added **[specs/README.md](specs/README.md)** — a task-oriented index/navigation map for
  the now-7-spec reference suite ("I want to X → read spec Y"). Organization of existing content, not new
  deep-dives. Spec suite is complete; no non-overlapping topics remain, so further cycles stay brief
  unless there's real change or a user steer.
- **Iter 23** — wrote **[specs/bml-dialogs.md](specs/bml-dialogs.md)** (declarative server-style forms —
  a distinct UI path from the hand-built components; AuctionHouse's `promptSellAmount` pattern):
  package-private ctor (gui-package), `BmlWindowListener.submit(w, buttonId)`/`cancel`, `buildOutMap()`
  to read fields, the BML grammar subset, add/removeDynamicComponent lifecycle, and BML-vs-hand-built
  guidance. Done via a targeted source read (no exploration agent — cheaper). Also corrected a
  client-gui-internals inaccuracy (BmlWindowComponent ctors are package-private, not freely public).
  **Foundational spec suite now feels complete** (GUI internals, custom rendering, private-class,
  keybindings, game-state, notifications, BML); remaining topics overlap existing docs → will be
  selective/brief going forward.
- **Iter 22** — spec-deepening: exploring **notifying the user** — chat/event-tab messages (`textMessage`
  targets/colors, the `:Event` log, AuctionHouse's `eventLog`/`warn` usage), reading incoming chat, and
  the sound/alert API. Wrote **[specs/notifications-and-sound.md](specs/notifications-and-sound.md)**:
  `hud.textMessage(tab, r,g,b, msg)` routing (`:Event` recommended, `:Friends`/`:Support` rejected,
  novel-name tabs auto-created), no chat listener (hook `ServerConnectionListenerClass.textMessage`),
  `getSoundEngine().play(resourceKey, …)` with the category-mute gotcha, center popups/flash/status,
  and the game-thread rule (sound excepted). Ties to Format + game-state-access.
- **Iter 21** — spec-deepening continues: exploring **client game-state access** (how a mod obtains a
  `World` reference; the World/PlayerObj/InventoryManager getter graph + visibility; hovered/selected
  target; the inventory-change listener; threading). The most foundational undocumented gap — every mod
  needs it. Wrote **[specs/game-state-access.md](specs/game-state-access.md)**: the bootstrap
  (`ModClient.getWorld()` canonical; hook-and-capture; in-package `hud.getWorld()`), the World/PlayerObj
  getter graph + visibility, hovered (`getCurrentHoveredObject`) vs selected (`SelectBar` protected),
  the inventory `InventoryMetaListener`/`addWindowListener` chain to feed a `KitTreeItem` tree, the send
  path, and the single-game-thread rule (+ `ModClient.runTask` to marshal). Source-cited, ties directly
  to the components; item-field details cross-ref the workspace doc (not duplicated).
- **Iter 20** — with component work stable, pivoted to the co-equal "store useful client-source info in
  specs" goal (still-first-class, not padding). Explored + wrote **[specs/keybindings-and-input.md](specs/keybindings-and-input.md)**
  — the key-dispatch path, the WurmConsole binding registry, and concrete mod recipes (bind→existing
  command; patch `handleInput2` for a custom verb; `say /cmd` to reach a server-side command; a
  `WurmEventListener` to observe keys) + hard limits (no public new-verb API; closed `ActionClass` enum;
  pkg-private `addListener`; no key consumption). Fills the gap the workspace reference punted on. A
  `KeybindMod` component is a candidate but HELD (no mod binds keys yet). (Iters 18–19: no change, awaited
  direction; user away.)
- **Iter 17** — regression check on iter-16's code edits (KitTreeItem null-guard, KitTabbedWindow field
  removal): standalone patch verify `ALL_PATCHES_OK` + example integration compile `EXIT 0`. No
  regression. No new work manufactured — the SDK is at steady state; further autonomous cycles have
  nothing bar-clearing to add. Next real step needs user steer (adopt components into the mods) or a
  mod growing a new need.
