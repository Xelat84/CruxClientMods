package org.gotti.wurmonline.clientmods.wideweightcolumn;

import java.util.Properties;
import java.util.logging.Logger;

import org.gotti.wurmunlimited.modloader.classhooks.HookException;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmClientMod;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

/**
 * Widens the "Weight"/"Volume" column of every item window (player inventory and any container
 * window) so large masses no longer overlap the neighbouring columns.
 *
 * <p>The vanilla {@code InventoryListComponent} constructor sizes that column from
 * {@code text.getWidth("2000.00")}, while the QL/DMG columns use {@code "100.00"} and the price
 * column uses {@code "1000.0000"}. This mod re-sizes only the weight/volume column by feeding a
 * longer measuring string into that single {@code getWidth} call, leaving the other columns
 * untouched. The template defaults to {@code "200000.00"} and is set via the
 * {@code weightColumnTemplate} key in {@code crux-clientmod.properties}.
 */
public class WideWeightColumn implements WurmClientMod, Configurable, PreInitable {

	private static final Logger LOGGER = Logger.getLogger(WideWeightColumn.class.getName());
	private static final String DEFAULT_TEMPLATE = "200000.00";

	private String template = DEFAULT_TEMPLATE;

	@Override
	public void configure(Properties properties) {
		String raw = properties.getProperty("weightColumnTemplate", DEFAULT_TEMPLATE);
		this.template = raw == null || raw.trim().isEmpty() ? DEFAULT_TEMPLATE : raw.trim();
	}

	@Override
	public void preInit() {
		try {
			ClassPool classPool = HookManager.getInstance().getClassPool();
			CtClass component = classPool.get("com.wurmonline.client.renderer.gui.InventoryListComponent");
			for (CtConstructor constructor : component.getDeclaredConstructors()) {
				constructor.instrument(new ExprEditor() {
					@Override
					public void edit(MethodCall call) throws CannotCompileException {
						if ("getWidth".equals(call.getMethodName())) {
							call.replace("$_ = \"2000.00\".equals($1) ? $proceed(\"" + template + "\") : $proceed($$);");
						}
					}
				});
			}

			LOGGER.info("WideWeightColumn: weight/volume column sized for \"" + template + "\"");
		} catch (NotFoundException | CannotCompileException e) {
			throw new HookException(e);
		}
	}
}
