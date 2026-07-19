package com.wurmonline.clientmods.auctionhouse;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.wurmonline.client.renderer.gui.AuctionWindow;
import com.wurmonline.clientkit.StaticCommandHook;

/**
 * Decodes the five server -> client auction sub-commands into row models, encodes the nine
 * client -> server sub-commands, and owns the current book / order / vault state shown by
 * {@link AuctionWindow}.
 *
 * <p>Receiving: {@link #handle(Object, ByteBuffer)} is invoked by WurmClientKit's
 * {@code ServerCommandMod} dispatch (via {@link AuctionHouseMod}) with the live connection object (so
 * it can be remembered for sending) and a {@link ByteBuffer} positioned just after the CMD byte, on
 * the sub-command byte.
 *
 * <p>Sending: {@link AuctionHouseMod} wires {@link #setTransport} to {@code ServerCommandMod.send},
 * which writes the raw payload (already led by the CMD byte) onto the connection and flushes it. The
 * auction window only ever opens in response to a received packet, so the connection is always set
 * before the first send.
 */
public final class AuctionClient {

	private static final Logger LOGGER = Logger.getLogger(AuctionClient.class.getName());

	private static Object connection;

	private static AuctionWindow window;

	// Current state, mutated only on the client (game) thread inside handle()/send().
	private static int snapshotPage;
	private static int snapshotPageSize = AuctionProtocol.DEFAULT_PAGE_SIZE;
	private static int snapshotTotalRows;
	private static final List<SnapshotRow> snapshot = new ArrayList<>();
	private static final List<ListingRow> listings = new ArrayList<>();
	private static int listingsTemplateId;
	private static int listingsHeaderImageNumber;
	private static String listingsName = "";
	private static long vaultOwnerId;
	private static final List<VaultRow> vault = new ArrayList<>();
	private static final List<VaultOwner> accessibleVaults = new ArrayList<>();
	private static final List<MySellRow> mySellOrders = new ArrayList<>();
	private static final List<MyBuyRow> myBuyOrders = new ArrayList<>();
	private static final List<ItemMatch> itemMatches = new ArrayList<>();
	private static Runnable itemMatchesListener;
	private static final List<MaterialOption> itemMaterials = new ArrayList<>();
	private static int itemMaterialsTemplateId;
	private static Runnable itemMaterialsListener;
	private static Quote lastQuote;
	private static Runnable quoteListener;

	private AuctionClient() {
	}

	// ---- row models -------------------------------------------------------

	public static final class VaultOwner {
		public final long ownerId;
		public final String name;

		VaultOwner(long ownerId, String name) {
			this.ownerId = ownerId;
			this.name = name;
		}
	}

	/** One resting sell listing - the Browse table is a flat, sortable list of individual listings. */
	public static final class SnapshotRow {
		public final long orderId;
		public final int templateId;
		public final int imageNumber;
		public final String name;
		public final int categoryId;
		public final float ql;
		public final byte rarity;
		public final boolean enchanted;
		public final boolean allowPartial;
		public final boolean combinable;
		public final long perTemplateIron;
		public final long remainingPieces;
		public final float remainingWeight;
		public final float templateWeight;
		public final boolean bulkable;

		SnapshotRow(long orderId, int templateId, int imageNumber, String name, int categoryId, float ql, byte rarity,
				boolean enchanted, boolean allowPartial, boolean combinable, long perTemplateIron, long remainingPieces,
				float remainingWeight, float templateWeight, boolean bulkable) {
			this.orderId = orderId;
			this.templateId = templateId;
			this.imageNumber = imageNumber;
			this.name = name;
			this.categoryId = categoryId;
			this.ql = ql;
			this.rarity = rarity;
			this.enchanted = enchanted;
			this.allowPartial = allowPartial;
			this.combinable = combinable;
			this.perTemplateIron = perTemplateIron;
			this.remainingPieces = remainingPieces;
			this.remainingWeight = remainingWeight;
			this.templateWeight = templateWeight;
			this.bulkable = bulkable;
		}
	}

	public static final class ListingRow {
		public final long orderId;
		public final int templateId;
		public final float ql;
		public final byte rarity;
		public final boolean enchanted;
		public final boolean allowPartial;
		public final long perTemplateIron;
		public final long remainingPieces;
		public final float templateWeight;

		ListingRow(long orderId, int templateId, float ql, byte rarity, boolean enchanted, boolean allowPartial,
				long perTemplateIron, long remainingPieces, float templateWeight) {
			this.orderId = orderId;
			this.templateId = templateId;
			this.ql = ql;
			this.rarity = rarity;
			this.enchanted = enchanted;
			this.allowPartial = allowPartial;
			this.perTemplateIron = perTemplateIron;
			this.remainingPieces = remainingPieces;
			this.templateWeight = templateWeight;
		}
	}

	public static final class VaultRow {
		public final long itemId;
		public final int templateId;
		public final int imageNumber;
		public final String name;
		public final float ql;
		public final byte rarity;
		public final float weightGrams; // per-unit weight
		public final int amount;        // unit count (1 for a discrete item, N for a bulk stack)
		public final long remainingMs;  // time left until expiry, server-relative

		VaultRow(long itemId, int templateId, int imageNumber, String name, float ql, byte rarity, float weightGrams, int amount, long remainingMs) {
			this.itemId = itemId;
			this.templateId = templateId;
			this.imageNumber = imageNumber;
			this.name = name;
			this.ql = ql;
			this.rarity = rarity;
			this.weightGrams = weightGrams;
			this.amount = amount;
			this.remainingMs = remainingMs;
		}
	}

	public static final class MySellRow {
		public final long orderId;
		public final int templateId;
		public final int imageNumber;
		public final String name;
		public final float ql;
		public final byte rarity;
		public final long perTemplateIron;
		public final long remainingPieces;
		public final long timeLeftMs;
		public final long reserveHeldIron;

		MySellRow(long orderId, int templateId, int imageNumber, String name, float ql, byte rarity, long perTemplateIron,
				long remainingPieces, long timeLeftMs, long reserveHeldIron) {
			this.orderId = orderId;
			this.templateId = templateId;
			this.imageNumber = imageNumber;
			this.name = name;
			this.ql = ql;
			this.rarity = rarity;
			this.perTemplateIron = perTemplateIron;
			this.remainingPieces = remainingPieces;
			this.timeLeftMs = timeLeftMs;
			this.reserveHeldIron = reserveHeldIron;
		}
	}

	/** One item-catalog match for the Place Buy Order "Choose item..." picker. */
	public static final class ItemMatch {
		public final int templateId;
		public final int imageNumber;
		public final String name;
		public final int categoryId;
		public final int unitWeightGrams;

		ItemMatch(int templateId, int imageNumber, String name, int categoryId, int unitWeightGrams) {
			this.templateId = templateId;
			this.imageNumber = imageNumber;
			this.name = name;
			this.categoryId = categoryId;
			this.unitWeightGrams = unitWeightGrams;
		}
	}

	/** One material a buyer may target for the chosen item (server-provided). */
	public static final class MaterialOption {
		public final byte material;
		public final String name;

		MaterialOption(byte material, String name) {
			this.material = material;
			this.name = name;
		}
	}

	/** Buy-window pricing context for a selected item+material: trader ask + stock, and the player-market spread. */
	public static final class Quote {
		public final int templateId;
		public final byte material;
		public final long traderAsk;
		public final int traderStock;
		public final long marketBestAsk;
		public final long marketBestBid;
		public final boolean bulkable;
		public final boolean splittable; // truly combinable - the buyer may opt in to receiving partial amounts

		Quote(int templateId, byte material, long traderAsk, int traderStock, long marketBestAsk, long marketBestBid,
				boolean bulkable, boolean splittable) {
			this.templateId = templateId;
			this.material = material;
			this.traderAsk = traderAsk;
			this.traderStock = traderStock;
			this.marketBestAsk = marketBestAsk;
			this.marketBestBid = marketBestBid;
			this.bulkable = bulkable;
			this.splittable = splittable;
		}
	}

	/** One source item shown in the Sell window (server-provided, for the removable source list). */
	public static final class PreviewSource {
		public final long itemId;
		public final int imageNumber;
		public final String name;
		public final float ql;
		public final long pieces;

		PreviewSource(long itemId, int imageNumber, String name, float ql, long pieces) {
			this.itemId = itemId;
			this.imageNumber = imageNumber;
			this.name = name;
			this.ql = ql;
			this.pieces = pieces;
		}
	}

	/** The whole Sell-window display, computed server-side for the current dragged set. */
	public static final class StagePreview {
		public final boolean ok;
		public final String message;
		public final int templateId;
		public final int imageNumber;
		public final String name;
		public final float ql;
		public final byte rarity;
		public final boolean combinable;
		public final boolean splittable; // truly combinable - can be sold in sub-unit (partial) weight
		public final long pieces;
		public final float weightGrams; // true total weight staged (pieces is this rounded up to whole items)
		public final float templateWeightGrams; // per-unit template weight; lets the window detect a sub-unit remainder
		// Pricing context (only meaningful when ok): the total iron the trader would pay for the whole staged lot,
		// and the current player-market spread (lowest ask / highest bid) at >= this QL. 0 = trader won't buy / none.
		public final long traderTotalIron;
		public final long marketBestAsk;
		public final long marketBestBid;
		public final float demurrageRatePerDay; // holding-fee rate/day (config); lets the window show the fee live
		public final java.util.List<PreviewSource> sources;

		StagePreview(boolean ok, String message, int templateId, int imageNumber, String name, float ql, byte rarity,
				boolean combinable, boolean splittable, long pieces, float weightGrams, float templateWeightGrams, long traderTotalIron, long marketBestAsk, long marketBestBid,
				float demurrageRatePerDay, java.util.List<PreviewSource> sources) {
			this.ok = ok;
			this.message = message;
			this.templateId = templateId;
			this.imageNumber = imageNumber;
			this.name = name;
			this.ql = ql;
			this.rarity = rarity;
			this.combinable = combinable;
			this.splittable = splittable;
			this.pieces = pieces;
			this.weightGrams = weightGrams;
			this.templateWeightGrams = templateWeightGrams;
			this.traderTotalIron = traderTotalIron;
			this.marketBestAsk = marketBestAsk;
			this.marketBestBid = marketBestBid;
			this.demurrageRatePerDay = demurrageRatePerDay;
			this.sources = sources;
		}
	}

	public static final class MyBuyRow {
		public final long orderId;
		public final int templateId;
		public final int imageNumber;
		public final String name;
		public final int minQl;
		public final long maxPerTemplateIron;
		public final long remainingPieces;
		public final long coinHeldIron;

		MyBuyRow(long orderId, int templateId, int imageNumber, String name, int minQl, long maxPerTemplateIron, long remainingPieces,
				long coinHeldIron) {
			this.orderId = orderId;
			this.templateId = templateId;
			this.imageNumber = imageNumber;
			this.name = name;
			this.minQl = minQl;
			this.maxPerTemplateIron = maxPerTemplateIron;
			this.remainingPieces = remainingPieces;
			this.coinHeldIron = coinHeldIron;
		}
	}

	// ---- state accessors for the window -----------------------------------

	public static int getSnapshotPage() {
		return snapshotPage;
	}

	public static int getSnapshotPageSize() {
		return snapshotPageSize;
	}

	public static int getSnapshotTotalRows() {
		return snapshotTotalRows;
	}

	public static int getTotalPages() {
		int size = snapshotPageSize <= 0 ? AuctionProtocol.DEFAULT_PAGE_SIZE : snapshotPageSize;
		if (snapshotTotalRows <= 0) {
			return 1;
		}
		return (snapshotTotalRows + size - 1) / size;
	}

	public static List<SnapshotRow> getSnapshot() {
		return snapshot;
	}

	public static int getListingsTemplateId() {
		return listingsTemplateId;
	}

	public static int getListingsHeaderImageNumber() {
		return listingsHeaderImageNumber;
	}

	public static String getListingsName() {
		return listingsName;
	}

	public static List<ListingRow> getListings() {
		return listings;
	}

	public static long getVaultOwnerId() {
		return vaultOwnerId;
	}

	public static List<VaultRow> getVault() {
		return vault;
	}

	public static List<VaultOwner> getAccessibleVaults() {
		return accessibleVaults;
	}

	public static List<MySellRow> getMySellOrders() {
		return mySellOrders;
	}

	public static List<MyBuyRow> getMyBuyOrders() {
		return myBuyOrders;
	}

	public static List<ItemMatch> getItemMatches() {
		return itemMatches;
	}

	/** The buy-order picker sets this to be notified (on the game thread) when ITEM_MATCHES arrives. */
	public static void setItemMatchesListener(Runnable r) {
		itemMatchesListener = r;
	}

	public static List<MaterialOption> getItemMaterials() {
		return itemMaterials;
	}

	public static int getItemMaterialsTemplateId() {
		return itemMaterialsTemplateId;
	}

	/** The buy-order window sets this to be notified when ITEM_MATERIALS arrives for the chosen item. */
	public static void setItemMaterialsListener(Runnable r) {
		itemMaterialsListener = r;
	}

	public static Quote getLastQuote() {
		return lastQuote;
	}

	/** The buy-order window sets this to be notified when QUOTE (pricing context) arrives. */
	public static void setQuoteListener(Runnable r) {
		quoteListener = r;
	}

	// ---- incoming ---------------------------------------------------------

	private static boolean modelsForced;

	/**
	 * Force every row/model inner class onto {@code AuctionClient}'s own classloader on the FIRST packet. Under the
	 * modlauncher's static-dispatch loader, an inner class first touched on a LATER packet (e.g. {@code MySellRow}
	 * in {@code readMyOrders}, {@code VaultRow} in {@code readVault}) fails to resolve - a {@code NoClassDefFoundError}
	 * that crashes the client when My Orders / Vault is opened. Loading them all now (while browse's SnapshotRow load
	 * proves the loader works) sidesteps the lazy-load window. Best-effort: a failure is logged, never fatal.
	 */
	private static void forceLoadModels() {
		if (modelsForced) {
			return;
		}
		modelsForced = true;
		ClassLoader cl = AuctionClient.class.getClassLoader();
		String[] models = { "SnapshotRow", "ListingRow", "VaultRow", "VaultOwner", "MySellRow", "MyBuyRow",
			"ItemMatch", "MaterialOption", "Quote", "PreviewSource", "StagePreview" };
		for (String m : models) {
			try {
				Class.forName("com.wurmonline.clientmods.auctionhouse.AuctionClient$" + m, false, cl);
			} catch (Throwable t) {
				LOGGER.log(Level.WARNING, "AuctionHouse: could not preload model class " + m, t);
			}
		}
	}

	/** Called from the patched {@code reallyHandle}; {@code bb} is positioned on the sub-command byte. */
	public static void handle(Object conn, ByteBuffer bb) {
		connection = conn;
		forceLoadModels();
		try {
			byte sub = bb.get();
			switch (sub) {
				case AuctionProtocol.SNAPSHOT:
					readSnapshot(bb);
					break;
				case AuctionProtocol.ORDER_UPDATE:
					readOrderUpdate(bb);
					break;
				case AuctionProtocol.VAULT:
					readVault(bb);
					break;
				case AuctionProtocol.MY_ORDERS:
					readMyOrders(bb);
					break;
				case AuctionProtocol.STAGE_PREVIEW:
					readStagePreview(bb);
					break;
				case AuctionProtocol.ITEM_MATCHES:
					readItemMatches(bb);
					break;
				case AuctionProtocol.ITEM_MATERIALS:
					readItemMaterials(bb);
					break;
				case AuctionProtocol.QUOTE:
					readQuote(bb);
					break;
				case AuctionProtocol.RESULT:
					readResult(bb);
					break;
				case AuctionProtocol.FOCUS_TAB: {
					byte tab = bb.get();
					ensureWindow();
					if (window != null) {
						window.openOnTab(tab);
					}
					return;
				}
				default:
					LOGGER.severe("AuctionHouse: unknown server sub-command " + sub + " - server/client protocol mismatch");
					ensureWindow();
					if (window != null) {
						window.showProtocolError("unknown message " + sub);
					}
					return;
			}
			ensureWindow();
			if (window != null) {
				window.refreshFromState();
			}
		} catch (Exception e) {
			// A malformed packet is a server bug, not a normal condition - fail loud, never swallow it silently.
			LOGGER.log(Level.SEVERE, "AuctionHouse: failed to parse auction packet (server bug)", e);
			ensureWindow();
			if (window != null) {
				window.showProtocolError("unreadable message");
			}
		}
	}

	private static void readSnapshot(ByteBuffer bb) {
		snapshotPage = bb.getInt();
		snapshotPageSize = bb.getInt();
		snapshotTotalRows = bb.getInt();
		int count = bb.getInt();
		snapshot.clear();
		for (int i = 0; i < count; i++) {
			long orderId = bb.getLong();
			int templateId = bb.getInt();
			int imageNumber = bb.getShort();
			String name = readString(bb);
			int categoryId = bb.getInt();
			float ql = bb.getFloat();
			byte rarity = bb.get();
			boolean enchanted = bb.get() != 0;
			boolean allowPartial = bb.get() != 0;
			boolean combinable = bb.get() != 0;
			long perTemplateIron = bb.getLong();
			long remainingPieces = bb.getLong();
			float remainingWeight = bb.getFloat();
			float templateWeight = bb.getFloat();
			boolean bulkable = bb.get() != 0;
			snapshot.add(new SnapshotRow(orderId, templateId, imageNumber, name, categoryId, ql, rarity,
					enchanted, allowPartial, combinable, perTemplateIron, remainingPieces, remainingWeight, templateWeight, bulkable));
		}
	}

	private static void readOrderUpdate(ByteBuffer bb) {
		listingsTemplateId = bb.getInt();
		listingsHeaderImageNumber = bb.getShort();
		listingsName = readString(bb);
		int count = bb.getInt();
		listings.clear();
		for (int i = 0; i < count; i++) {
			long orderId = bb.getLong();
			float ql = bb.getFloat();
			byte rarity = bb.get();
			boolean enchanted = bb.get() != 0;
			boolean allowPartial = bb.get() != 0;
			long perTemplateIron = bb.getLong();
			long remainingPieces = bb.getLong();
			float templateWeight = bb.getFloat();
			listings.add(new ListingRow(orderId, listingsTemplateId, ql, rarity, enchanted, allowPartial,
					perTemplateIron, remainingPieces, templateWeight));
		}
	}

	private static void readVault(ByteBuffer bb) {
		vaultOwnerId = bb.getLong();
		int count = bb.getInt();
		vault.clear();
		for (int i = 0; i < count; i++) {
			long itemId = bb.getLong();
			int templateId = bb.getInt();
			int imageNumber = bb.getShort();
			String name = readString(bb);
			float ql = bb.getFloat();
			byte rarity = bb.get();
			float weightGrams = bb.getFloat();
			int amount = bb.getInt();
			long remainingMs = bb.getLong();
			vault.add(new VaultRow(itemId, templateId, imageNumber, name, ql, rarity, weightGrams, amount, remainingMs));
		}
		int accessibleCount = bb.getInt();
		accessibleVaults.clear();
		for (int i = 0; i < accessibleCount; i++) {
			long ownerId = bb.getLong();
			String ownerName = readString(bb);
			accessibleVaults.add(new VaultOwner(ownerId, ownerName));
		}
	}

	private static void readMyOrders(ByteBuffer bb) {
		int sellCount = bb.getInt();
		mySellOrders.clear();
		for (int i = 0; i < sellCount; i++) {
			long orderId = bb.getLong();
			int templateId = bb.getInt();
			int imageNumber = bb.getShort();
			String name = readString(bb);
			float ql = bb.getFloat();
			byte rarity = bb.get();
			long perTemplateIron = bb.getLong();
			long remainingPieces = bb.getLong();
			long timeLeftMs = bb.getLong();
			long reserveHeldIron = bb.getLong();
			mySellOrders.add(new MySellRow(orderId, templateId, imageNumber, name, ql, rarity, perTemplateIron, remainingPieces, timeLeftMs, reserveHeldIron));
		}
		int buyCount = bb.getInt();
		myBuyOrders.clear();
		for (int i = 0; i < buyCount; i++) {
			long orderId = bb.getLong();
			int templateId = bb.getInt();
			int imageNumber = bb.getShort();
			String name = readString(bb);
			int minQl = bb.getInt();
			long maxPerTemplateIron = bb.getLong();
			long remainingPieces = bb.getLong();
			long coinHeldIron = bb.getLong();
			myBuyOrders.add(new MyBuyRow(orderId, templateId, imageNumber, name, minQl, maxPerTemplateIron, remainingPieces, coinHeldIron));
		}
	}

	private static void readStagePreview(ByteBuffer bb) {
		boolean ok = bb.get() != 0;
		String message = readString(bb);
		int templateId = 0;
		int imageNumber = 0;
		String name = "";
		float ql = 0f;
		byte rarity = 0;
		boolean combinable = false;
		boolean splittable = false;
		long pieces = 0L;
		float weightGrams = 0f;
		float templateWeightGrams = 0f;
		long traderTotalIron = 0L;
		long marketBestAsk = 0L;
		long marketBestBid = 0L;
		float demurrageRatePerDay = 0f;
		java.util.List<PreviewSource> sources = new ArrayList<>();
		int sourceCount = bb.getInt();
		for (int i = 0; i < sourceCount; i++) {
			long itemId = bb.getLong();
			int srcImage = bb.getShort();
			String srcName = readString(bb);
			float srcQl = bb.getFloat();
			long srcPieces = bb.getLong();
			sources.add(new PreviewSource(itemId, srcImage, srcName, srcQl, srcPieces));
		}
		if (ok) {
			templateId = bb.getInt();
			imageNumber = bb.getShort();
			name = readString(bb);
			ql = bb.getFloat();
			rarity = bb.get();
			combinable = bb.get() != 0;
			splittable = bb.get() != 0;
			pieces = bb.getLong();
			weightGrams = bb.getFloat();
			traderTotalIron = bb.getLong();
			marketBestAsk = bb.getLong();
			marketBestBid = bb.getLong();
			templateWeightGrams = bb.getFloat();
			demurrageRatePerDay = bb.getFloat();
		}
		ensureWindow();
		if (window != null) {
			window.onStagePreview(new StagePreview(ok, message, templateId, imageNumber, name, ql, rarity, combinable,
				splittable, pieces, weightGrams, templateWeightGrams, traderTotalIron, marketBestAsk, marketBestBid,
				demurrageRatePerDay, sources));
		}
	}

	private static void readItemMatches(ByteBuffer bb) {
		int count = bb.getInt();
		itemMatches.clear();
		for (int i = 0; i < count; i++) {
			int templateId = bb.getInt();
			int imageNumber = bb.getShort();
			String name = readString(bb);
			int categoryId = bb.getInt();
			int unitWeightGrams = bb.getInt();
			itemMatches.add(new ItemMatch(templateId, imageNumber, name, categoryId, unitWeightGrams));
		}
		if (itemMatchesListener != null) {
			itemMatchesListener.run();
		}
	}

	private static void readItemMaterials(ByteBuffer bb) {
		itemMaterialsTemplateId = bb.getInt();
		int count = bb.getInt();
		itemMaterials.clear();
		for (int i = 0; i < count; i++) {
			byte material = bb.get();
			String name = readString(bb);
			itemMaterials.add(new MaterialOption(material, name));
		}
		if (itemMaterialsListener != null) {
			itemMaterialsListener.run();
		}
	}

	private static void readQuote(ByteBuffer bb) {
		int templateId = bb.getInt();
		byte material = bb.get();
		long traderAsk = bb.getLong();
		int traderStock = bb.getInt();
		long marketBestAsk = bb.getLong();
		long marketBestBid = bb.getLong();
		boolean bulkable = bb.get() != 0;
		boolean splittable = bb.get() != 0;
		lastQuote = new Quote(templateId, material, traderAsk, traderStock, marketBestAsk, marketBestBid, bulkable, splittable);
		if (quoteListener != null) {
			quoteListener.run();
		}
	}

	private static String readString(ByteBuffer bb) {
		int len = bb.getShort() & 0xFFFF;
		byte[] bytes = new byte[len];
		bb.get(bytes);
		try {
			return new String(bytes, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			return "";
		}
	}

	private static void readResult(ByteBuffer bb) {
		boolean ok = bb.get() != 0;
		long orderId = bb.getLong();
		String msg = readString(bb);
		ensureWindow(); // every other sub-command creates the window; a result must never be silently dropped
		window.onResult(ok, orderId, msg);
	}

	private static void ensureWindow() {
		if (window == null) {
			window = AuctionWindow.getOrCreate();
		}
		window.show();
	}

	// ---- outgoing ---------------------------------------------------------

	// Send on the connection this class received the packet on (stored in handle). Self-contained by design:
	// AuctionClient is loaded twice (modloader copy that preInit touches; HookManager-loader copy the static
	// dispatch calls at runtime), so nothing may be wired in from preInit - it would land on the wrong copy.
	// StaticCommandHook.send does the reflective wckSend; see its javadoc for the classloader rationale.
	private static void send(byte[] payload) {
		StaticCommandHook.send(connection, payload);
	}

	private static Encoder begin(byte sub) {
		return new Encoder(sub);
	}

	public static void sendQueryBook(int templateId, int page, int pageSize, String search, int categoryId,
			float minQl, float maxQl, long minPrice, long maxPrice, boolean buyMode) {
		send(begin(AuctionProtocol.QUERY_BOOK)
			.putInt(templateId).putInt(page).putInt(pageSize)
			.putUTF(search == null ? "" : search)
			.putInt(categoryId).putFloat(minQl).putFloat(maxQl).putLong(minPrice).putLong(maxPrice)
			.put((byte) (buyMode ? 1 : 0))
			.bytes());
	}

	public static void sendPostSell(int templateId, int ql, byte rarity, boolean allowPartial, long perTemplateIron,
			int durationDays, long[] itemIds, long[] quantities) {
		Encoder e = begin(AuctionProtocol.POST_SELL)
			.putInt(templateId).putInt(ql).put(rarity)
			.put((byte) (allowPartial ? 1 : 0)).putLong(perTemplateIron).putInt(durationDays)
			.putInt(itemIds.length);
		for (int i = 0; i < itemIds.length; i++) {
			e.putLong(itemIds[i]).putLong(quantities[i]);
		}
		send(e.bytes());
	}

	public static void sendQueryQuote(int templateId, byte material, int minQl) {
		send(begin(AuctionProtocol.QUERY_QUOTE).putInt(templateId).put(material).putInt(minQl).bytes());
	}

	public static void sendPostBuy(int templateId, long maxPerTemplateIron, int minQl, byte minRarity, float minWeight,
			byte unit, long amount, boolean excludeEnchanted, byte material, boolean allowPartial) {
		send(begin(AuctionProtocol.POST_BUY)
			.putInt(templateId).putLong(maxPerTemplateIron).putInt(minQl).put(minRarity)
			.putFloat(minWeight).put(unit).putLong(amount).put((byte) (excludeEnchanted ? 1 : 0)).put(material)
			.put((byte) (allowPartial ? 1 : 0))
			.bytes());
	}

	public static void sendQueryItems(String search) {
		send(begin(AuctionProtocol.QUERY_ITEMS).putUTF(search).bytes());
	}

	public static void sendQueryItemMaterials(int templateId) {
		send(begin(AuctionProtocol.QUERY_ITEM_MATERIALS).putInt(templateId).bytes());
	}

	public static void sendQueryStagePreview(long[] itemIds, long[] amounts) {
		Encoder e = begin(AuctionProtocol.QUERY_STAGE_PREVIEW).putInt(itemIds.length);
		for (int i = 0; i < itemIds.length; i++) {
			e.putLong(itemIds[i]).putLong(amounts[i]);
		}
		send(e.bytes());
	}

	public static void sendBuySelected(long sellListingId, long pieces, float weight) {
		send(begin(AuctionProtocol.BUY_SELECTED).putLong(sellListingId).putLong(pieces).putFloat(weight).bytes());
	}

	public static void sendCancel(long orderId) {
		send(begin(AuctionProtocol.CANCEL).putLong(orderId).bytes());
	}

	public static void sendQueryMyOrders() {
		send(begin(AuctionProtocol.QUERY_MY_ORDERS).bytes());
	}

	public static void sendVaultList(long ownerId) {
		send(begin(AuctionProtocol.VAULT_LIST).putLong(ownerId).bytes());
	}

	public static void sendVaultOpen(long ownerId) {
		send(begin(AuctionProtocol.VAULT_OPEN).putLong(ownerId).bytes());
	}

	public static void sendVaultExtend(long itemId, int days) {
		send(begin(AuctionProtocol.VAULT_EXTEND).putLong(itemId).putInt(days).bytes());
	}

	public static void sendVaultPerms(long granted, int bits) {
		send(begin(AuctionProtocol.VAULT_PERMS).putLong(granted).putInt(bits).bytes());
	}

	/** Minimal big-endian byte builder. The first two bytes are always CMD + sub. */
	private static final class Encoder {
		private byte[] buf = new byte[32];
		private int len;

		Encoder(byte sub) {
			put(AuctionProtocol.CMD);
			put(sub);
		}

		private void ensure(int extra) {
			if (len + extra > buf.length) {
				int n = buf.length * 2;
				while (n < len + extra) {
					n *= 2;
				}
				byte[] nb = new byte[n];
				System.arraycopy(buf, 0, nb, 0, len);
				buf = nb;
			}
		}

		Encoder put(byte b) {
			ensure(1);
			buf[len++] = b;
			return this;
		}


		Encoder putInt(int v) {
			ensure(4);
			buf[len++] = (byte) (v >> 24);
			buf[len++] = (byte) (v >> 16);
			buf[len++] = (byte) (v >> 8);
			buf[len++] = (byte) v;
			return this;
		}

		Encoder putLong(long v) {
			ensure(8);
			for (int s = 56; s >= 0; s -= 8) {
				buf[len++] = (byte) (v >> s);
			}
			return this;
		}

		Encoder putFloat(float v) {
			return putInt(Float.floatToIntBits(v));
		}

		Encoder putShort(int v) {
			ensure(2);
			buf[len++] = (byte) (v >> 8);
			buf[len++] = (byte) v;
			return this;
		}

		Encoder putUTF(String s) {
			byte[] b;
			try {
				b = (s == null ? "" : s).getBytes("UTF-8");
			} catch (java.io.UnsupportedEncodingException e) {
				b = new byte[0];
			}
			putShort(b.length);
			ensure(b.length);
			System.arraycopy(b, 0, buf, len, b.length);
			len += b.length;
			return this;
		}

		byte[] bytes() {
			byte[] out = new byte[len];
			System.arraycopy(buf, 0, out, 0, len);
			return out;
		}
	}
}
