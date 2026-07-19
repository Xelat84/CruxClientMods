# `Hooks`

`com.wurmonline.clientkit.Hooks` — boilerplate helpers for javassist class-hooking in `preInit()`.
Every client mod repeats the same preamble: grab the `HookManager` class pool, `get` a class (turning
the checked `NotFoundException` into a `HookException`), append its `ClassClassPath`, and wrap edits in
the `catch (NotFoundException | CannotCompileException) → HookException` idiom. `Hooks` collapses all of
that. `final`, all-static, pure plumbing (no client dependency).

The idiom recurs in 7+ sites across the mods (RecipeExamine, StackPlacement, WideSkillColumn, MaxActions,
AuctionHouse, DirectConnect) and the SDK itself (`ServerCommandMod`) — this is the distilled form.

## API

```java
ClassPool pool()                                  // the shared HookManager pool
CtClass   get(String className)                   // pool().get, NotFoundException → HookException
CtMethod  method(CtClass c, String name)          // getDeclaredMethod, NotFoundException → HookException
void      appendClassPath(Class<?> anchor)        // put a class's jar on the pool (loader fix)
void      edit(HookAction action)                 // run edits; checked exceptions → HookException
void      replaceCall(CtBehavior m, String target, String replacement)  // ExprEditor over one call name
```

`HookAction` is a functional interface (`void run() throws NotFoundException, CannotCompileException`).

## Usage

```java
@Override public void preInit() {
    CtClass c = Hooks.get("com.wurmonline.client.renderer.gui.SkillWindowComponent");
    Hooks.appendClassPath(getClass());                       // if your patch references mod code
    Hooks.replaceCall(Hooks.method(c, "layout"), "getWidth", // rewrite one call in place
            "$_ = $proceed($$) * 3 / 2;");
    Hooks.edit(() -> Hooks.method(c, "reconnected")          // arbitrary edit, exceptions wrapped
            .insertAfter("com.example.MyMod.onReconnect();"));
}
```

## ⚠️ The invokedynamic constraint (critical)

**Any class referenced from a javassist-injected code string must be lambda-free.** The client's
javassist is **3.12.1**, which cannot parse the `invokedynamic` constant-pool entries that Java-8
lambdas and method-references compile to — it throws `java.io.IOException: invalid constant type: 18`
when it reads such a class. When your `insertBefore`/`setBody`/`replace` string names a helper method
(e.g. `com.example.MyMod.onEvent(...)`), javassist must parse that helper's class at patch-compile time;
if that class contains a lambda **anywhere**, the patch fails to install at runtime.

Consequences:
- `Hooks` itself is lambda-free (it uses an anonymous `ExprEditor`, not a lambda), so it's safe to
  reference from injected code.
- **`Hooks.edit(() -> ...)` puts a lambda in *your* class.** That's fine — *unless your class is also
  referenced by injected code.** The SDK's own `GuiPatches` and `ServerCommandMod` are referenced by
  their patches, so they deliberately use explicit `try/catch` (not `Hooks.edit`) to stay lambda-free.
- Verify any non-trivial patch offline against the real client jar before shipping (see
  [private-class-techniques](../specs/private-class-techniques.md) — the offline harness catches exactly
  this class of failure, which `mvn` build does not).

## Source

`src/main/java/com/wurmonline/clientkit/Hooks.java`

## See also

- [Spec: private-class techniques](../specs/private-class-techniques.md) — the full technique catalog + the invokedynamic gotcha + offline verification.
- [Hooking model](../concepts/hooking-model.md) — what `ServerCommandMod` does with these primitives.
- [`GuiPatches`](GuiPatches.md) — worked patches (deliberately lambda-free).
