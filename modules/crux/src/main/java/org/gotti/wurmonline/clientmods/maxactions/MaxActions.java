package org.gotti.wurmonline.clientmods.maxactions;

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
import javassist.CtMethod;
import javassist.NotFoundException;

/**
 * Pins the crafting-window action-queue size (the bullet count and the {@code n / max} text) to a
 * fixed value regardless of the player's Mind Logic skill, matching the server, which always allows
 * 10 queued actions (see Player.maxNumActions). {@code CreationWindow}'s bullet array is fixed at
 * 10, so values above 10 have no effect and the configured value is clamped to [2, 10].
 *
 * <p>{@code MindLogicCalculator.calculateMaxNumberOfActions()} is the single upstream source feeding
 * the field, the open-time read, and the skill-change refresh callback, so patching that one method
 * keeps every display path consistent even when Mind Logic changes mid-session.
 */
public class MaxActions implements WurmClientMod, Configurable, PreInitable {

	private static final Logger LOGGER = Logger.getLogger(MaxActions.class.getName());
	private static final int CLIENT_BULLET_CAP = 10;

	private int maxActions = CLIENT_BULLET_CAP;

	@Override
	public void configure(Properties properties) {
		String raw = properties.getProperty("maxActions", String.valueOf(CLIENT_BULLET_CAP));
		int configured = CLIENT_BULLET_CAP;
		try {
			configured = Integer.parseInt(raw.trim());
		} catch (NumberFormatException e) {
			LOGGER.warning("MaxActions: invalid maxActions '" + raw + "', using " + CLIENT_BULLET_CAP);
		}
		this.maxActions = Math.max(2, Math.min(CLIENT_BULLET_CAP, configured));
	}

	@Override
	public void preInit() {
		try {
			ClassPool pool = HookManager.getInstance().getClassPool();
			CtClass calc = pool.get("com.wurmonline.client.renderer.gui.MindLogicCalculator");
			CtMethod method = calc.getDeclaredMethod("calculateMaxNumberOfActions");
			method.setBody("{ return " + this.maxActions + "; }");
			LOGGER.info("MaxActions: crafting queue pinned to " + this.maxActions + " bullets");
		} catch (NotFoundException | CannotCompileException e) {
			throw new HookException(e);
		}
	}
}
