package org.gotti.wurmonline.clientmods.recipeexamine;

import java.util.logging.Logger;

import org.gotti.wurmunlimited.modloader.classhooks.HookException;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmClientMod;

import com.wurmonline.client.renderer.gui.RecipeExamineHelper;

import javassist.CannotCompileException;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

/**
 * Adds an "Examine" entry to the right-click menu of items in the crafting recipe browser
 * ({@code CreationListWindow}). It opens the item's plonk recipe card unconditionally - unlike the
 * vanilla double-click, which only reaches the plonk viewer through a branch that is effectively
 * dead for any listed item.
 *
 * <p>Both popup builders ({@code addItemPopupWindow} and {@code addOnlyHelpButton}) end in
 * {@code hud.showPopupComponent(popup)}. We intercept that call and add the Examine button to the
 * popup just before it is shown. The actual button is created by {@link
 * com.wurmonline.client.renderer.gui.RecipeExamineHelper}, which lives in the game package because
 * {@code WurmPopup}, its {@code addButton} method and its {@code title} field are package-private.
 *
 * <p>Also resyncs the recipe browser on reconnect: {@code CreationListWindow} requests the full
 * recipe list once per session and caches it, so server-side recipe changes (or a server restart)
 * leave a stale matrix until the client process is restarted. {@code HeadsUpDisplay.reconnected()}
 * resets its sibling windows but not this one, so we re-arm its {@code openFirstTime} latch there;
 * the window then clears and re-requests the list on its next tick.
 */
public class RecipeExamine implements WurmClientMod, PreInitable {

	private static final Logger LOGGER = Logger.getLogger(RecipeExamine.class.getName());

	@Override
	public void preInit() {
		try {
			ClassPool pool = HookManager.getInstance().getClassPool();
			pool.appendClassPath(new ClassClassPath(RecipeExamineHelper.class));
			CtClass window = pool.get("com.wurmonline.client.renderer.gui.CreationListWindow");
			injectExamine(window.getDeclaredMethod("addItemPopupWindow"));
			injectExamine(window.getDeclaredMethod("addOnlyHelpButton"));
			injectReconnectResync(pool);
			LOGGER.info("RecipeExamine: added Examine option to the crafting recipe browser");
		} catch (NotFoundException | CannotCompileException e) {
			throw new HookException(e);
		}
	}

	private static void injectReconnectResync(ClassPool pool) throws NotFoundException, CannotCompileException {
		CtClass hud = pool.get("com.wurmonline.client.renderer.gui.HeadsUpDisplay");
		hud.getDeclaredMethod("reconnected")
			.insertAfter("com.wurmonline.client.renderer.gui.RecipeExamineHelper.rearmCreationList(this.creationListWindow);");
	}

	private static void injectExamine(CtMethod method) throws CannotCompileException {
		method.instrument(new ExprEditor() {
			@Override
			public void edit(MethodCall mc) throws CannotCompileException {
				if (mc.getMethodName().equals("showPopupComponent")) {
					mc.replace("{ com.wurmonline.client.renderer.gui.RecipeExamineHelper.addExamineButton($1); $proceed($$); }");
				}
			}
		});
	}
}
