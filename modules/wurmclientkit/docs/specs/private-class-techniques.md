# Spec: dealing with private & package-private client classes

The client's GUI (and much of its internals) is a wall of restricted access: package-private classes,
package-private members of public classes, `final` classes you can't subclass, `private` methods you
can't call. This spec is the **decision tree and technique catalog** for getting past each barrier —
the foundation every GUI component in this SDK relies on.

## Access-barrier decision tree

Identify what you're up against, then pick the technique:

| Barrier | Can I subclass? | Technique |
|---------|-----------------|-----------|
| **package-private class**, non-final (e.g. `WWindow`, `WurmDropDown`, `WButton`) | Yes, if your subclass is `package com.wurmonline.client.renderer.gui` | **A. In-package subclass** |
| **package-private class**, `final` (e.g. `WurmInputField`, `WCheckBox`, `WurmPopup`) | No | **B. In-package wrapper** (compose an instance, implement its listener) |
| **package-private member** of a public/accessible class (field/ctor/method) | n/a | **A/B** (in-package code sees it) or **E. reflection** |
| **`private` member** (e.g. `HeadsUpDisplay.addComponent`) | n/a | **C. bytecode edit** or **E. reflection**; or find a public alternative (D) |
| **behavior inside a vanilla method** you can't call | n/a | **C. bytecode instrumentation** (`insertBefore/After/setBody/ExprEditor`) |
| **method-entry/exit interception** across a call | n/a | **F. `HookManager.registerHook`** |

Preference order for maintainability: **A/B (compile-checked, in-package) > D (public alternative) >
C (bytecode) > F (registered hook) > E (reflection)**. Reflection is last because it's unchecked and
slow; bytecode is powerful but brittle across client versions.

---

## A. In-package subclass (the primary trick)

Declare your class in package `com.wurmonline.client.renderer.gui` (physically under
`src/main/java/com/wurmonline/client/renderer/gui/` in your mod jar). It then shares the runtime
package with the vanilla widgets and can:

- call package-private constructors (`new WurmTreeList<>(...)`, `new WButton(...)`),
- read/write package-private fields (`field.prompt`, `checkbox.checked`, `popup.title`),
- call package-private methods (`popup.addButton(...)`, `field.getText()`),
- **subclass** non-final package-private classes (`extends WWindow`, `extends WurmDropDown`,
  `extends TreeListItem`).

**Runtime requirement:** the class must load on the **same classloader** as the vanilla gui classes,
or the JVM treats it as a different runtime package and package access throws `IllegalAccessError`.
Achieve this with the `ClassClassPath` append:

```java
pool.appendClassPath(new ClassClassPath(getClass()));   // in preInit
```

`ServerCommandMod.preInit()` does this for you. A pure-GUI mod must do it itself. (Full explanation:
[../concepts/package-private-constraint.md](../concepts/package-private-constraint.md).)

Used by: every SDK gui-package class (`KitTabbedWindow`, `KitTreeItem`, `GuiKit`); RecipeExamine's
`RecipeExamineHelper`; AuctionHouse's `VaultDropDown extends WurmDropDown`.

## B. In-package wrapper (for `final` classes)

`final` package-private classes (`WurmInputField`, `WCheckBox`, `WurmPopup`) can't be subclassed. Wrap
an instance instead: construct it (in-package), implement its listener interface, expose a clean API,
and forward. Example — `KitInputField` wraps `WurmInputField`, implements `InputFieldListener`, sets
`prompt=""` + `simpleInput=true`, and mirrors the committed value. See
[../components/KitInputField.md](../components/KitInputField.md).

For a `final` class with a **public** extension point, use that instead of wrapping: `WurmPopup` is
final but its `WPopupLiveButton` nested class is public and abstract — subclass the button, not the popup.

## C. Bytecode instrumentation (javassist)

In `preInit()` (`implements WurmClientMod, PreInitable`), get the pool and edit methods:

```java
ClassPool pool = HookManager.getInstance().getClassPool();
CtClass c = pool.get("com.wurmonline.client.renderer.gui.SomeClass");
try {
    c.getDeclaredMethod("someMethod").insertBefore("{ ...java source string... }");
    c.getDeclaredMethod("other").insertAfter("com.example.Helper.after($0, $1);");
    c.getMethod("full", "(I)V").setBody("{ return; }");
    for (CtConstructor ctor : c.getDeclaredConstructors())
        ctor.instrument(new ExprEditor() {
            public void edit(MethodCall mc) throws CannotCompileException {
                if ("getWidth".equals(mc.getMethodName()))
                    mc.replace("$_ = $proceed($$) * 3 / 2;");   // rewrite one call in place
            }
        });
} catch (NotFoundException | CannotCompileException e) {
    throw new HookException(e);
}
```

Javassist source-string variables: `$0`=this, `$1..$n`=args, `$$`=all args, `$_`=call result,
`$proceed($$)`=invoke the original call (inside `ExprEditor`), `$r`=return type. The injected string
is compiled against the target class's context, so it can reference **that class's** package-private
members and call **your** helper by fully-qualified name (append its `ClassClassPath` first).

Real uses: WideSkillColumn (`ExprEditor` scales a `getWidth` result), StackPlacement (`ExprEditor`
forces `isActive()`→false, `setBody` on `canPlaceOn`, `insertAfter` on `updateParent`), RecipeExamine
(`insertAfter` on `reconnected`, `MethodCall.replace` wrapping `showPopupComponent` to inject a button),
MaxActions (`setBody`), and the SDK's `GuiPatches` (`insertBefore` on `WurmInputField.keyPressed` and on
`WurmTreeList$TreeListPanel.leftPressed`).

**Accessing the enclosing instance from an inner class.** An inner (non-static) class holds a synthetic
field referencing its outer instance, named **`this$0`** by javac. javassist source can read it directly
— `this.this$0.someOuterField` — and since the injected code runs in the inner class's package it can
touch the outer's package-private fields. **Verify the field name** with `javap -p` on the compiled
class before relying on it (it's `this$0` for standard javac, but confirm). The SDK's
`installTreeCellButtons` uses exactly this to read the outer `WurmTreeList`'s `columnWidths`/`commonWidth`
from inside `TreeListPanel.leftPressed`.

**⚠️ Classes referenced from injected code MUST be lambda-free (javassist 3.12.1 + invokedynamic).**
The client bundles **javassist 3.12.1**, which cannot read the `invokedynamic` constant-pool entries
that Java-8 lambdas and method-references compile to — it throws `java.io.IOException: invalid constant
type: 18`. When an injected string (`insertBefore`/`setBody`/`replace`) names a helper method like
`com.example.MyMod.onEvent(...)`, javassist parses that helper's whole class to resolve the call; if the
class contains a lambda **anywhere**, the patch fails to install at runtime (a `HookException` that
`mvn`/`dotnet build` never sees). Rules:
- Keep any class named by injected bytecode lambda-free — use anonymous inner classes, not lambdas, and
  no method references. (Anonymous classes compile to separate `.class` files, not `invokedynamic`.)
- The SDK's `GuiPatches` and `ServerCommandMod` are referenced by their own patches, so they use explicit
  `try/catch` rather than `Hooks.edit(() -> ...)` — the lambda would poison them. `Hooks` itself is
  lambda-free for the same reason.
- Java-8 string concatenation is safe (compiles to `StringBuilder`, not invokedynamic — that only became
  invokedynamic in Java 9+). It's specifically lambdas/method-refs that bite.
- This was found the hard way: refactoring `GuiPatches` to use `Hooks.edit(() -> ...)` compiled fine with
  `mvn` but broke the cell-button patch (`invalid constant type: 18`) because the injected string
  references `GuiPatches.onCellButtonError`. The offline harness (below) caught it.

**Verify a runtime-compiled javassist string OFFLINE before shipping.** `insertBefore`/`setBody`/`replace`
strings are compiled by javassist **at patch time**, not by `mvn` — so `dotnet build`/`mvn package`
success does NOT prove the patch compiles. A malformed reference surfaces only when the mod runs. To
catch it without launching the game, run a tiny harness against the real client jar:

```java
ClassPool pool = new ClassPool(true);
pool.appendClassPath(clientJar); pool.appendClassPath(commonJar); pool.appendClassPath(kitClasses);
CtClass c = pool.get("com.wurmonline...$Inner");
c.getDeclaredMethod("m").insertBefore("<the exact string>");
c.toBytecode();                     // throws CannotCompileException if the string is bad
System.out.println("PATCH_OK");
```

Run with the javassist jar + client/common jars + your compiled classes on the classpath (Windows: use
`cygpath -w` for paths and `;` as the classpath separator). `PATCH_OK` means the patch will compile at
runtime — worth doing for every non-trivial injected string.

**The SDK ships this as a committed, drift-proof tool: `examples/verify/PatchVerify.java`.** It applies
the *actual* shipped source constants (`GuiPatches.INPUT_FIELD_DOWN_FIX_SRC` / `TREE_CELL_BUTTON_SRC`) to
the real client classes and calls `toBytecode()`, so it can't drift from what installs. Run it standalone
after building the jar:

```bash
JAVASSIST="$HOME/.m2/repository/javassist/javassist/3.12.1.GA/javassist-3.12.1.GA.jar"   # PIN 3.12.1
CJAR=$(find ~/.m2 -name 'client-3721782.jar'|head -1); CC=$(find ~/.m2 -name 'common-client-3721782.jar'|head -1)
KIT=target/wurmclientkit-0.1.jar; OUT=$(mktemp -d)
CP="$(cygpath -w "$JAVASSIST");$(cygpath -w "$CJAR");$(cygpath -w "$CC");$(cygpath -w "$KIT")"
javac -cp "$CP" -d "$(cygpath -w "$OUT")" examples/verify/PatchVerify.java
java  -cp "$(cygpath -w "$OUT");$CP" PatchVerify "$(cygpath -w "$JAVASSIST")" "$(cygpath -w "$CJAR")" "$(cygpath -w "$CC")" "$(cygpath -w "$KIT")"
# expect: INPUTFIELD_OK / TREECELL_OK / ALL_PATCHES_OK
```

Three hard-won pitfalls (all cost real debugging time):
1. **Pin javassist 3.12.1** — the exact version the client bundles. `~/.m2` may hold newer copies
   (3.20/3.23) that *do* parse invokedynamic and would **falsely pass** a patch the client's 3.12.1
   rejects. A bare `javassist-*.jar` glob + `head -1` is nondeterministic — pin the path.
2. **Exclude the modlauncher jar** from the harness classpath — it bundles lambda-bearing classes, and
   `ClassPool(true)` makes 3.12.1 choke (`invalid constant type: 18`) reading one during type-checking,
   even though the patch strings never reference it.
3. **Run it standalone**, not chained after `mvn` in one script — in batch context 3.12.1 has been
   observed to false-fail (lazily scanning the client's own lambda classes) where the identical
   standalone invocation passes. This flakiness is a javassist-3.12.1 artifact, not a patch defect —
   which is why the SDK ships the tool but not an automated `verify.sh` wrapping it.

**Adding a method / renaming to wrap** (the DirectConnect pattern): copy the original into a renamed
method, then `addMethod`/`setBody` a replacement that delegates — how `ServerCommandMod` injects
`wckSend`. See [../concepts/hooking-model.md](../concepts/hooking-model.md).

## D. Find the public alternative first

Before reflecting or patching a `private` member, check for a public path to the same effect:

- `hud.addComponent` is private → use `hud.mainMenu.registerComponent(name, comp)` /
  `hud.hudSettings.registerComponent(name, comp)` (public).
- Want a context-menu button? Don't patch the menu — subclass the public `WPopupLiveButton`.
- Want tab behavior? Don't touch the pkg-private `WurmTabbedWindow` — use `KitTabbedWindow`.

Public alternatives survive client updates far better than bytecode edits.

## E. Reflection (last resort for members)

For a one-off field read/write on a package-private/`private` member where injecting an in-package
class is overkill:

```java
Field f = target.getClass().getDeclaredField("input");
f.setAccessible(true);
String v = (String) f.get(target);
```

Unchecked and slow; the shipped mods prefer A/B over reflection because those are compile-verified.
`ServerCommandMod.send` uses reflection for exactly one thing: calling the runtime-injected `wckSend`,
which by definition isn't on the compile classpath.

## F. `HookManager.registerHook`

`HookManager.getInstance().registerHook(className, methodName, signature, invocationHandlerFactory)`
renames the target and routes it through your `InvocationHandler` (run before/after, call original via
`method.invoke(proxy, args)`). Best for **observing/augmenting** a method without rewriting its body
(custommap hooks `World.setServerInformation` this way). For surgical in-body changes, prefer C.

Caveats (both C and F): native methods can't be hooked; specify the descriptor for overloaded methods;
the class must be resolvable in the pool at registration time; do all of it in `preInit`, before the
target class is linked.

## Checklist when adding an SDK component that touches restricted code

1. Classify each barrier with the decision tree above.
2. Prefer A/B; the file goes in `com.wurmonline.client.renderer.gui`.
3. If it needs the same-loader guarantee, confirm the consumer runs the `ClassClassPath` append
   (`ServerCommandMod` gives it free; document the requirement for pure-GUI mods).
4. If bytecode/hook is unavoidable, isolate it and document the exact target method + signature in the
   component doc, so a client-version bump has one place to re-verify.

## See also

- [keybindings-and-input](keybindings-and-input.md) — a worked application of these techniques (patching `WurmConsole.handleInput2`, in-package `WurmEventHandler.addListener`).
- [client-gui-internals](client-gui-internals.md) — which members are pkg-private vs private vs public.
- [../concepts/package-private-constraint.md](../concepts/package-private-constraint.md) · [../concepts/hooking-model.md](../concepts/hooking-model.md)
