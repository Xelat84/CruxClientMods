package org.gotti.wurmonline.clientmods.wideskillcolumn;

import java.util.logging.Logger;

import org.gotti.wurmunlimited.modloader.classhooks.HookException;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
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
 * Widens the "Level" column of the skill window so values longer than "100.00" (e.g. the
 * skill-points pool) stay readable.
 *
 * <p>The vanilla {@code SkillWindowComponent} constructor sizes the column as
 * {@code text.getWidth("100.00") + 4}; this mod multiplies the {@code getWidth} result.
 * The factor defaults to 2.5 and can be overridden with {@code -Dwurm.skillcolumn.factor}
 * or the {@code WURM_SKILLCOLUMN_FACTOR} environment variable (accepted range 1.0 - 10.0).
 */
public class WideSkillColumn implements WurmClientMod, PreInitable {

	private static final Logger LOGGER = Logger.getLogger(WideSkillColumn.class.getName());

	@Override
	public void preInit() {
		final int percent = factorPercent();
		try {
			ClassPool classPool = HookManager.getInstance().getClassPool();
			CtClass component = classPool.get("com.wurmonline.client.renderer.gui.SkillWindowComponent");
			for (CtConstructor constructor : component.getDeclaredConstructors()) {
				constructor.instrument(new ExprEditor() {
					@Override
					public void edit(MethodCall call) throws CannotCompileException {
						if ("getWidth".equals(call.getMethodName())) {
							call.replace("$_ = $proceed($$) * " + percent + " / 100;");
						}
					}
				});
			}

			LOGGER.info("WideSkillColumn: level column width scaled to " + percent + "%");
		} catch (NotFoundException | CannotCompileException e) {
			throw new HookException(e);
		}
	}

	private static int factorPercent() {
		String raw = System.getProperty("wurm.skillcolumn.factor");
		if (raw == null || raw.isEmpty()) {
			raw = System.getenv("WURM_SKILLCOLUMN_FACTOR");
		}

		double factor = 2.5;
		if (raw != null && !raw.isEmpty()) {
			try {
				factor = Double.parseDouble(raw.trim());
			} catch (NumberFormatException e) {
				LOGGER.warning("WideSkillColumn: invalid factor '" + raw + "', using 2.5");
			}
		}

		factor = Math.max(1.0, Math.min(10.0, factor));
		return (int) Math.round(factor * 100.0);
	}
}
