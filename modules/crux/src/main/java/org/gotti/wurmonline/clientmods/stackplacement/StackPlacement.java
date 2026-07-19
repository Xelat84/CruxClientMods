package org.gotti.wurmonline.clientmods.stackplacement;

import java.util.logging.Logger;

import org.gotti.wurmunlimited.modloader.classhooks.HookException;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmClientMod;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.Descriptor;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

/**
 * Lets the item placer target items that are already placed on a surface, so items can be
 * stacked (e.g. an ingot on top of an ingot), not just dropped on the base surface.
 *
 * <p>Vanilla {@code GroundItemCellRenderable.pick} renders picking geometry only when
 * {@code !itemPlacer.isActive() || getParent() == null} - i.e. child items (anything placed
 * on top of another item) are excluded from the pick buffer while the placer is active, so the
 * cursor can only ever resolve to the bottom/base item. This forces the {@code isActive()} call
 * inside {@code pick} to read {@code false}, which makes the gate always true and renders picking
 * geometry for placed children too. The real placer state is untouched everywhere else.
 *
 * <p>That alone is not enough: an item already placed on a surface is sent with placeable == 2
 * (a child), but {@code canPlaceOn()} only returns true for placeable == 1, so the placer treats
 * a stacked item as "place on the ground beside it" and never as a surface. So this also widens
 * {@code canPlaceOn()} to accept any placeable item (1 or 2). The server still validates the
 * parentId it receives (only real placement surfaces accept children), so widening the client
 * check can't create invalid placements.
 *
 * <p>The server already trusts whatever parentId the client sends, so no server change is needed.
 */
public class StackPlacement implements WurmClientMod, PreInitable {

	private static final Logger LOGGER = Logger.getLogger(StackPlacement.class.getName());

	@Override
	public void preInit() {
		try {
			ClassPool classPool = HookManager.getInstance().getClassPool();
			CtClass renderable = classPool.get("com.wurmonline.client.renderer.cell.GroundItemCellRenderable");

			int patched = 0;
			for (CtMethod method : renderable.getDeclaredMethods()) {
				if (!"pick".equals(method.getName())) {
					continue;
				}

				method.instrument(new ExprEditor() {
					@Override
					public void edit(MethodCall call) throws CannotCompileException {
						if ("isActive".equals(call.getMethodName())) {
							call.replace("$_ = false;");
						}
					}
				});
				patched++;
			}

			CtMethod canPlaceOn = renderable.getMethod("canPlaceOn", Descriptor.ofMethod(CtClass.booleanType, new CtClass[0]));
			canPlaceOn.setBody("{ return this.item.getPlaceable() != 0; }");

			// updateModelMatrix() runs in initialize() BEFORE updateParent() links the parent, and
			// updateParent() never re-runs it - so a freshly received placed child keeps its
			// parentless (relative-coords-minus-render-origin = offscreen) matrix. Recompute it after
			// the parent link so nested children (stacks deeper than one) render in the right place.
			CtMethod updateParent = renderable.getMethod("updateParent", "()V");
			updateParent.insertAfter("this.updateModelMatrix();");

			LOGGER.info("StackPlacement: placed items are now targetable and stackable (patched "
				+ patched + " pick method(s) + canPlaceOn + updateParent).");
		} catch (NotFoundException | CannotCompileException e) {
			throw new HookException(e);
		}
	}
}
