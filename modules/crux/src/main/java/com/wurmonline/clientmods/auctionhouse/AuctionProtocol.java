package com.wurmonline.clientmods.auctionhouse;

/**
 * Wire constants for the auction command, mirroring the server's {@code AuctionProtocol}.
 * Every packet is {@code byte CMD=-67}, {@code byte subCmd}, then a big-endian payload
 * (Java {@code ByteBuffer} is big-endian by default).
 */
public final class AuctionProtocol {

	private AuctionProtocol() {
	}

	public static final byte CMD = -67;

	public static final int DEFAULT_PAGE_SIZE = 50;

	// C -> S
	// QUERY_BOOK payload: int templateId, int page, int pageSize, string search, int categoryId,
	// float minQl, float maxQl, long minPrice, long maxPrice, byte mode (0=sell listings, 1=buy orders). The
	// server filters + paginates the browse book (templateId == 0); filters sent always. -1 / "" == no constraint.
	public static final byte QUERY_BOOK = 1;
	// POST_SELL payload: int templateId, int ql, byte rarity, byte allowPartial,
	// long perTemplateIron, int durationDays, int count, count*(long itemId, long quantity).
	// quantity is the number of units (pieces) to sell from itemId; <=0 means "whole item".
	public static final byte POST_SELL = 2;
	// POST_BUY payload: int templateId, long maxPerTemplateIron, int minQl, byte minRarity, float minWeight,
	// byte unit, long amount, byte excludeEnchanted, byte material, byte allowPartial. allowPartial = the buyer opts
	// in to receiving a combinable stack's sub-unit tail (mirrors the seller's POST_SELL allowPartial).
	public static final byte POST_BUY = 3;
	// 4 retired (was MARKET_NOW / "Sell now"); placing a sell now satisfies immediately server-side. Reserved.
	public static final byte BUY_SELECTED = 5;
	public static final byte CANCEL = 6;
	public static final byte VAULT_LIST = 7;
	public static final byte VAULT_EXTEND = 8;
	public static final byte VAULT_PERMS = 9;
	// VAULT_OPEN payload: long ownerId (0 = self). Opens the native draggable vault container (town-gated).
	public static final byte VAULT_OPEN = 12;
	// 13 retired (was POST_BUY_BY_NAME - superseded by the item picker resolving a real templateId). Reserved.
	public static final byte QUERY_MY_ORDERS = 10;
	// QUERY_ITEM_MATERIALS payload: int templateId. Reply is ITEM_MATERIALS - the materials a buyer may target.
	public static final byte QUERY_ITEM_MATERIALS = 11;
	// QUERY_ITEMS payload: string search. Reply is ITEM_MATCHES - the buy-order "Choose item..." picker.
	public static final byte QUERY_ITEMS = 14;
	// QUERY_STAGE_PREVIEW payload: int count, count*(long itemId, long amount). Reply is STAGE_PREVIEW - the
	// server computes the whole Sell-window display for the current dragged set; the client stores nothing else.
	public static final byte QUERY_STAGE_PREVIEW = 15;
	// QUERY_QUOTE payload: int templateId, byte material, int minQl. Reply is QUOTE - Buy-window pricing context.
	public static final byte QUERY_QUOTE = 16;

	// S -> C
	// SNAPSHOT row: long orderId, int templateId, short image, string name, int category, float ql, byte rarity,
	// byte enchanted, byte allowPartial, byte combinable, long perTemplateIron, long remainingPieces,
	// float remainingWeight, float templateWeight, byte bulkable. remainingWeight = the listing's real total grams;
	// combinable drives the "Partial" column (Yes/No, blank if not combinable).
	public static final byte SNAPSHOT = 101;
	public static final byte ORDER_UPDATE = 102;
	public static final byte VAULT = 103;
	// QUOTE payload: int templateId, byte material, long traderAsk, int traderStock, long marketBestAsk,
	// long marketBestBid, byte bulkable, byte splittable. Buy-window pricing context; bulkable gates the kg amount
	// tab, splittable gates the "allow partial" opt-in.
	public static final byte QUOTE = 104;
	public static final byte RESULT = 105;
	public static final byte MY_ORDERS = 106;
	// ITEM_MATERIALS payload: int templateId, int count, count*(byte material, string name).
	public static final byte ITEM_MATERIALS = 107;
	// FOCUS_TAB payload: byte tab (see TAB_* below). Tells the window which tab to show on open.
	public static final byte FOCUS_TAB = (byte) 108;
	// ITEM_MATCHES payload: int count, count*(int templateId, short image, string name, int categoryId, int unitWeightGrams).
	public static final byte ITEM_MATCHES = (byte) 109;
	// STAGE_PREVIEW payload: byte ok, string message, int sourceCount, sourceCount*(long itemId, short image,
	// string name, float ql, long pieces) [always], then when ok: int templateId, short image, string name,
	// float ql, byte rarity, byte combinable, byte splittable, long pieces, float weightGrams, long traderTotalIron,
	// long marketBestAsk, long marketBestBid, float templateWeightGrams. traderTotalIron = what the trader pays for
	// the whole lot; combinable = QL-averages like a BSB; splittable = sub-unit partial sell allowed;
	// templateWeightGrams = per-unit weight (client detects a sub-unit remainder = non-full items staged).
	public static final byte STAGE_PREVIEW = (byte) 110;

	// tabs
	public static final byte TAB_BROWSE = 0;
	public static final byte TAB_SELL = 1;
	public static final byte TAB_ORDERS = 2;
	public static final byte TAB_VAULT = 3;

	// AmountUnit
	public static final byte UNIT_PIECES = 0;
	public static final byte UNIT_WEIGHT = 1;
}
