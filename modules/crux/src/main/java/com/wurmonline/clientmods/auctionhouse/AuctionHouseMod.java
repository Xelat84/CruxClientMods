package com.wurmonline.clientmods.auctionhouse;

import java.util.logging.Logger;

import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmClientMod;

import com.wurmonline.clientkit.GuiPatches;
import com.wurmonline.clientkit.StaticCommandHook;

/**
 * Client side of the player auction exchange. The server speaks one first-class command
 * ({@code CMD == -67}) carrying a sub-command byte; {@link StaticCommandHook} routes that command to
 * {@link AuctionClient#handle} (which opens an {@link AuctionWindow}).
 *
 * <p>The auction handler INSTANTIATES gui-package windows, so it uses {@link StaticCommandHook} (static
 * dispatch on the HookManager loader) rather than {@code ServerCommandMod} (instance dispatch on the
 * modloader loader, which would throw {@code IllegalAccessError} on the window classes). All the
 * classloader-sensitive plumbing lives in the SDK; this mod just names its command and handler and
 * installs the two GUI hardening patches.
 */
public class AuctionHouseMod implements WurmClientMod, PreInitable {

	private static final Logger LOGGER = Logger.getLogger(AuctionHouseMod.class.getName());

	@Override
	public void preInit() {
		GuiPatches.installTreeCellButtons();
		GuiPatches.installInputFieldFix();
		GuiPatches.installDisabledInputFocusFix(); // disabled Sell-price field can't take a caret
		StaticCommandHook.install(AuctionProtocol.CMD, AuctionClient.class);
		LOGGER.info("AuctionHouse: static command hook installed for CMD " + AuctionProtocol.CMD + ".");
	}
}
