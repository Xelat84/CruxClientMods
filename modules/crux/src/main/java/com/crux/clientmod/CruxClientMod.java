package com.crux.clientmod;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmClientMod;
import org.gotti.wurmunlimited.modsupport.console.ConsoleListener;

import com.wurmonline.clientmods.auctionhouse.AuctionHouseMod;
import org.gotti.wurmonline.clientmods.directconnect.DirectConnect;
import org.gotti.wurmonline.clientmods.maxactions.MaxActions;
import org.gotti.wurmonline.clientmods.recipeexamine.RecipeExamine;
import org.gotti.wurmonline.clientmods.stackplacement.StackPlacement;
import org.gotti.wurmonline.clientmods.wideskillcolumn.WideSkillColumn;
import org.gotti.wurmonline.clientmods.wideweightcolumn.WideWeightColumn;

public class CruxClientMod implements WurmClientMod, Configurable, PreInitable, Initable, ConsoleListener {

	private static final Logger LOGGER = Logger.getLogger(CruxClientMod.class.getName());

	private final List<Object> delegates = new ArrayList<>();

	// serverpacks, custommap and connectionfix are the launcher's own modules (they self-register and are
	// loaded by the launcher) - deliberately NOT composited here: they were built to load as standalone
	// launcher modules and break under the composite's classloader (IllegalAccessError on their inner classes).
	public CruxClientMod() {
		delegates.add(new AuctionHouseMod());
		delegates.add(new DirectConnect());
		delegates.add(new MaxActions());
		delegates.add(new RecipeExamine());
		delegates.add(new StackPlacement());
		delegates.add(new WideSkillColumn());
		delegates.add(new WideWeightColumn());
	}

	// Load-time phases are all-or-nothing: if any bundled mod fails, abort the whole load so the client
	// refuses to run rather than launching half-modded. The re-thrown cause names the offending mod.
	private static void loadStep(Object delegate, String phase, ThrowingRunnable step) {
		try {
			step.run();
		} catch (Throwable t) {
			LOGGER.log(Level.SEVERE, "CruxClientMod: " + delegate.getClass().getSimpleName() + " " + phase + " failed - aborting client load.", t);
			throw new RuntimeException("CruxClientMod: " + delegate.getClass().getSimpleName() + " " + phase + " failed.", t);
		}
	}

	@Override
	public void configure(Properties properties) {
		for (Object delegate : delegates) {
			if (delegate instanceof Configurable) {
				loadStep(delegate, "configure()", () -> ((Configurable) delegate).configure(properties));
			}
		}
	}

	@Override
	public void preInit() {
		for (Object delegate : delegates) {
			if (delegate instanceof PreInitable) {
				loadStep(delegate, "preInit()", () -> ((PreInitable) delegate).preInit());
			}
		}
	}

	@Override
	public void init() {
		for (Object delegate : delegates) {
			if (delegate instanceof Initable) {
				loadStep(delegate, "init()", () -> ((Initable) delegate).init());
			}
		}
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Throwable;
	}

	@Override
	public boolean handleInput(String input, Boolean silent) {
		for (Object delegate : delegates) {
			if (delegate instanceof ConsoleListener) {
				try {
					if (((ConsoleListener) delegate).handleInput(input, silent)) {
						return true;
					}
				} catch (Throwable t) {
					LOGGER.log(Level.SEVERE, "handleInput() failed for " + delegate.getClass().getName(), t);
				}
			}
		}
		return false;
	}
}
