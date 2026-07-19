# Spec: reading game state from a mod

How a client mod **obtains a `World` reference** (the bootstrapping problem — there's no `World` handed
to mods) and reads live state: the player, hovered/selected target, inventory, server/connection. Focus
is **access entry points + visibility**; item *field* details live in
`ClientToolkitReference.md` §4 (not duplicated). Verified against
`../../../Decompiled/Client_vf/`; method/field names are stable, line numbers drift.

## 1. Bootstrapping — getting `World`

There is **no public static `World`**. `WurmClientBase.clientObject` (the singleton) and its `world`
field are both `private`. Three ways to reach it, best first:

1. **`ModClient.getWorld()` — canonical.** The modloader ships
   `org.gotti.wurmunlimited.modsupport.ModClient`, which reflects out the private
   `WurmClientBase.clientObject`/`world`/`hud` fields once and exposes:
   `ModClient.getWorld()`, `getClientInstance()`, `getHeadsUpDisplay()`. **Use this** rather than
   re-implementing the reflection. Valid only after client launch (any game-tick-time hook is safe).
2. **Hook-and-capture** (when you can't depend on `ModClient`): register a hook on a `World` *instance*
   method — inside the `InvocationHandler`, `proxy` **is** the `World`. `World.setServerInformation`
   (public, fires on login) is a good one-shot capture point (this is how `custommap` does it). Stash
   the reference.
3. **In-package bridge** (gui-package code only): `WurmComponent.hud` is a package-private static
   `HeadsUpDisplay`; `HeadsUpDisplay.getWorld()` is **public**. So a class compiled into
   `com.wurmonline.client.renderer.gui` can do `WurmComponent.hud.getWorld()` — what AuctionHouse's
   window does. Not usable from a non-gui-package class (see [private-class-techniques](private-class-techniques.md)).

## 2. The `World` graph (all public) — `World`

| Getter | Returns |
|--------|---------|
| `getPlayer()` | `PlayerObj` |
| `getHud()` | `HeadsUpDisplay` |
| `getClient()` | `WurmClientBase` (back to the singleton) |
| `getServerConnection()` | `SimpleServerConnectionClass` (the send path) |
| `getInventoryManager()` | `InventoryMetaWindowManager` |
| `getCurrentHoveredObject()` | `PickableUnit` (§4) |
| `getWorldRenderer()` | `WorldRender` |
| `getServerName()` / `isServerEpic()` / `getWorldSize()` | server info |
| `getUsername()` | player name (via connection) |
| `getPlayerPosX/Y/H`, `getPlayerRotX/Y`, `getPlayerLayer`, `getPlayerCurrentTileX/Y` | interpolated player position |

## 3. `PlayerObj` (public unless noted)

- Position: `getPos()` → `PlayerPosition`; alpha-interpolated `getX/Y/H(float alpha)`,
  `getXRot/getYRot(float)`, deltas, `getLayer()`.
- **Modifiers: `isShiftDown()` / `isAltDown()` / `isControlDown()`** — public (the canonical way to read
  modifier state, since raw modifier keys are swallowed — see [keybindings-and-input](keybindings-and-input.md)).
- Identity: `getPlayerName`, `getKingdom`, `getReligion`. Vitals: `getStamina/getDamage/getThirst/
  getHunger/…`. Sets: `getSkillSet()`, `getSpelleffectSet()`, `getMissions()`. Flags: `isDead`,
  `isFlying`, `isCommandingBoat`.
- **No clean accessor:** current fight stance / target — `private`, no getter (reflection/in-package
  only). Hovered/selected/carried items are **not** on `PlayerObj` (see §4/§5).

## 4. Hovered / selected / targeted object

- **Hovered:** `World.getCurrentHoveredObject()` → `PickableUnit` (public interface: `getId()`,
  `getHoverName()`, `targetMatches(int mask)`). This is what actions target by default.
- **How action-targeting resolves** (`World.sendHoveredAction`): if the action's target mask has the
  multi-target bit, it uses `hud.getCommandTargetsFrom(x,y)` (public, `long[]`); else falls back to the
  hovered object's id if `targetMatches`. Send via `hud.sendAction(PlayerAction, long[]|long)` (public).
- **Selected (select-bar):** `SelectBar.selectedUnit` is `protected`, **no public getter** —
  `hud.getSelectBar()` gets the bar but not the selection; reflection/in-package to read it.

## 5. Inventory — reading items & reacting to changes

The entry chain a mod uses to populate a [`KitTreeItem`](../components/KitTreeItem.md) tree from real
server items (all public):

```
World.getInventoryManager()                 → InventoryMetaWindowManager
  .getPlayerInventory() / getWindow(id)     → InventoryMetaWindowView   (getRootItem/getItem/contains)
  .addWindowListener(InventoryMetaWindowListener)   // window add/remove
  view.addItemListener(InventoryMetaListener)       // per-item observer ← register this
```

- **`InventoryMetaListener`** is the observer to react to items: `addInventoryItem`,
  `removeInventoryItem`, `updateInventoryItem`, `addFakeInventoryItem`, `removeFakeInventoryItem`. Items
  are `InventoryMetaItem` — field details in the workspace reference §4 (image number → icon, bulk count
  in description, etc.). Reference impl: `SelectBar` registers itself as an `InventoryMetaWindowListener`.

## 6. Connection & sending

Reach it via `World.getServerConnection()` (or `WurmClientBase.getServerConnection()`) →
`SimpleServerConnectionClass` (public). State: `getName()`, `isConnecting()`, `isLoggedIn()`. Every
`send*` is public (`sendAction`, `sendMoveSomeItems`, `sendBmlResponse`, `sendOpenInventory`, …).
**Custom raw-CMD sending is already wrapped by [`ServerCommandMod`](../components/ServerCommandMod.md)** —
this section is just how to reach the connection for vanilla sends.

## 7. Threading (important)

Everything runs on the **single Wurm main/game thread** — rendering, `World.tick()` →
`player.gametick()` / `hud.gameTick()`, **and network packet processing** (`serverConnection.update()`
is called on the game thread inside the game loop; packet-decode callbacks mutate `World`/`PlayerObj`/
inventory there). So all live-state mutation is single-threaded relative to your tick hooks / packet
handlers.

**Rule:** read or mutate game state only from a game-thread context (a `gameTick`, a packet handler, a
hooked game-thread method). If your code runs on another thread (Swing/JavaFX UI, a background worker),
**marshal onto the game thread with `ModClient.runTask(Runnable)`** — the launcher drains it at the top
of the game loop. This is the reference-approved cross-thread entry.

## Visibility cheat-sheet (→ reflection/in-package where noted)

| State | Accessor | Visibility |
|-------|----------|-----------|
| `World` singleton | `ModClient.getWorld()` | private fields → use ModClient (reflection) or hook |
| player/hud/inventory/connection/hovered/renderer/serverName | `World.getX()` | public |
| `HeadsUpDisplay.getWorld()` | — | public |
| `WurmComponent.hud`, `HeadsUpDisplay.world` | — | package-private (gui-package only) |
| player modifiers, vitals, pos, sets | `PlayerObj.getX()` | public |
| fight stance / current action / combat target | — | private, no getter |
| selected unit | `SelectBar.selectedUnit` | protected, no getter |
| inventory windows/items + listeners | `getInventoryManager()` / `addWindowListener` / `addItemListener` | public |

## See also

- [private-class-techniques](private-class-techniques.md) — reflection/in-package/hook toolbox for the private bits.
- [keybindings-and-input](keybindings-and-input.md) — modifier state, the input path.
- [`ServerCommandMod`](../components/ServerCommandMod.md) — custom-CMD send/receive (don't hand-roll it).
- Workspace `ClientToolkitReference.md` §4 — `InventoryMetaItem` field details.
