package com.wurmonline.client.renderer.gui;

import java.util.ArrayList;
import java.util.List;

import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.clientmods.auctionhouse.AuctionCategory;
import com.wurmonline.clientmods.auctionhouse.AuctionClient;
import com.wurmonline.clientmods.auctionhouse.AuctionProtocol;

/**
 * The Auction House window: four tabs (Browse / Sell / My Orders / Vault) over the global order
 * book. Lives in the game GUI package because {@code WWindow} and the widget classes
 * ({@code WurmTreeList}, {@code WButton}, {@code WurmInputField}, {@code WurmDropDown},
 * {@code WCheckBox}, {@code WurmArrayPanel}, {@code WurmBorderPanel}) are package-private.
 *
 * <p>Real tabs via {@link KitTabbedWindow} (WurmClientKit): the window keeps its title bar and shows a
 * genuine tab strip; the active tab is raised, others recessed. All data comes from {@link AuctionClient}'s
 * state; buttons encode and send via {@code AuctionClient}.
 */
public class AuctionWindow extends KitTabbedWindow implements ButtonListener {

	private static AuctionWindow instance;

	public static AuctionWindow getOrCreate() {
		if (instance == null) {
			instance = new AuctionWindow();
		}
		return instance;
	}

	private static final int NORTH = WurmBorderPanel.NORTH;
	private static final int SOUTH = WurmBorderPanel.SOUTH;
	private static final int CENTER = WurmBorderPanel.CENTER;
	private static final int WEST = WurmBorderPanel.WEST;
	private static final int H = WurmArrayPanel.DIR_HORIZONTAL;
	private static final int V = WurmArrayPanel.DIR_VERTICAL;

	// Tab indices == AuctionProtocol.TAB_* (Browse=0, Sell=1, Orders=2, Vault=3), in addTab order.

	// ---- Browse tab ----
	private WurmBorderPanel browsePanel;
	private WurmTreeList<AuctionRow> browseTree;
	private KitInputField searchField;
	private KitInputField qlMinField;
	private KitInputField qlMaxField;
	private KitInputField priceMinField;
	private KitInputField priceMaxField;
	// Amount to buy: a Qty/kg tab pair, each with its own input (the kg tab is disabled unless the selected listing
	// is partial-buyable). Above it, a running "Total: ..." for the entered amount clamped to what's available.
	private TabbedDualInput browseQty;
	private WurmLabel browseTotalLabel;
	private long browseTotalRowId = -1L;
	private WButton refreshButton;
	private WButton clearFiltersButton;
	// A sub-tab strip (Sell orders / Buy orders) below the main tabs; switches only the browse data source.
	private WButton sellOrdersSubTab;
	private WButton buyOrdersSubTab;
	private boolean browseBuyMode;
	private WButton buySelectedButton;
	private WButton placeBuyButton;
	private WurmArrayPanel<FlexComponent> browseActionsRow;
	private PagerRow pager;
	private long lastRefreshMs;

	// Category sidebar: a foldable name-only WurmTreeList mirroring the server taxonomy. Selecting a
	// row (row highlight, not a checkbox) filters the Browse list locally by SnapshotRow.categoryId.
	// A gameTick poll detects the newly-selected row and rebuilds the browse list.
	private WurmTreeList<AuctionCategoryRow> categoryTree;
	private AuctionCategoryRow categoryAllRow;
	private final List<AuctionCategoryRow> categoryRows = new ArrayList<>();
	private int selectedCategoryId = AuctionCategory.ALL;

	// ---- Sell tab ----
	private WurmBorderPanel sellPanel;
	private WurmTreeList<AuctionRow> sellTree;
	// Price is a 2x2 grid: Per unit / Total buttons (tabs) each over their own input field. Both fields stay
	// visible and keep their own value; only the selected mode's field is editable (the other is disabled).
	private WButton perUnitButton;
	private WButton totalButton;
	private KitInputField perUnitField;
	private KitInputField totalField;
	private boolean sellTotalMode;
	private WCheckBox sellAllowPartial;
	private Collapsible partialBox; // wraps the "allow partial" row; hidden for non-combinable listings
	// Red hint shown right under the partial checkbox when non-full (sub-unit) items are staged and partial is not
	// yet allowed; wrapped in a Collapsible so it takes no space when hidden. Place Sell Order is blocked meanwhile.
	private KitColorLabel sellPartialWarning;
	private Collapsible sellPartialWarningBox;
	private boolean lastSellPartialChecked;
	private KitDropDown sellDuration;
	private WurmLabel sellPreviewLabel;
	private ValueLineBox sellPricing;
	private WButton placeSellButton;
	// The client holds ONLY the raw dragged input: itemId -> amount (0 = whole item). Everything shown in the
	// Sell window (aggregate identity, averaged QL, piece counts) is computed by the server and returned as a
	// preview; the client stores that last preview solely to render it and to gate "Create order".
	private final java.util.LinkedHashMap<Long, Long> stagedInputs = new java.util.LinkedHashMap<>();
	// Items added by the most recent drop, still awaiting the server's merge verdict. If the preview comes back
	// !ok, these are the ones that broke the single-listing rule and get rolled back (see onStagePreview).
	private final java.util.List<Long> pendingStageAdd = new java.util.ArrayList<>();
	private AuctionClient.StagePreview stagePreview;
	private boolean sellPriceEdited;

	// ---- My Orders tab ----
	private WurmBorderPanel ordersPanel;
	private WurmTreeList<AuctionRow> sellOrdersTree;
	private WurmTreeList<AuctionRow> buyOrdersTree;

	// ---- Vault tab ----
	private WurmBorderPanel vaultPanel;
	private WurmTreeList<AuctionRow> vaultTree;
	private WButton vaultOpenButton;
	private WButton vaultExtendButton;
	private WButton vaultManageButton;
	private KitDropDown vaultDropDown;
	private WurmArrayPanel<FlexComponent> vaultTopRow;
	private long selectedVaultOwner;
	private final List<Long> vaultOwnerIds = new ArrayList<>();

	private AuctionWindow() {
		super("Auction House");

		buildBrowse();
		buildSell();
		buildOrders();
		buildVault();

		addTab("Browse", browsePanel);
		addTab("Sell", sellPanel);
		addTab("My Orders", ordersPanel);
		addTab("Vault", vaultPanel);
		onTabChange(this::queryForTab);   // lazily (re)load a tab's data when the user switches to it

		setInitialSize(720, 560, false, 0.5F, 0.5F);
	}

	// Status/feedback goes to the :Event chat log (KitTabbedWindow has no spare footer slot, and the player
	// reads feedback there anyway - see feedback routing on the Sell tab).
	private void status(String msg) {
		eventLog(msg, 1.0f, 0.85f, 0.4f);
	}

	// Issue a tab's data query. Fired by onTabChange (user click) and openOnTab (server "open on tab N").
	private void queryForTab(int tab) {
		switch (tab) {
			case AuctionProtocol.TAB_ORDERS:
				AuctionClient.sendQueryMyOrders();
				break;
			case AuctionProtocol.TAB_VAULT:
				AuctionClient.sendVaultList(selectedVaultOwner);
				break;
			case AuctionProtocol.TAB_BROWSE:
				requestBrowse(AuctionClient.getSnapshotPage());
				break;
			default:
				break; // Sell has no server query
		}
	}

	@Override
	protected void closePressed() {
		clearStaging();
		super.closePressed();
	}

	/** Staging is an ephemeral fake container; wipe it whenever the window hides so it never persists. */
	private void clearStaging() {
		stagedInputs.clear();
		pendingStageAdd.clear();
		stagePreview = null;
		sellPriceEdited = false;
		if (perUnitField != null) {
			perUnitField.setValue("0");
		}
		if (totalField != null) {
			totalField.setValue("0");
		}
		setSellPriceMode(false); // back to Per unit
		rebuildSellStage();
		updateSellPreview();
	}

	/**
	 * Switch to the tab the server asked for (NPC "Auction" menu choice) and issue that tab's data query so it
	 * populates. If already on that tab, {@code focusTab} wouldn't fire {@code onTabChange}, so query directly.
	 */
	public void openOnTab(int tab) {
		if (selected() == tab) {
			queryForTab(tab);
		} else {
			focusTab(tab);   // fires onTabChange -> queryForTab
		}
	}

	// ---------------- Browse ----------------

	private void buildBrowse() {
		browsePanel = new WurmBorderPanel("browsePanel");

		WurmArrayPanel<FlexComponent> filters = new WurmArrayPanel<>("browseFilters", V);

		// A second layer of tabs under the main strip: Sell orders / Buy orders. It only switches the browse data
		// source; the rest of the tab is identical. Tabs are distinguished by text colour (gold active / white
		// inactive), never setDown (whose pressed art reads as disabled).
		sellOrdersSubTab = new WButton("Sell orders", this);
		buyOrdersSubTab = new WButton("Buy orders", this);
		WurmArrayPanel<FlexComponent> subTabRow = row("browseSubTabs");
		subTabRow.addComponent(sellOrdersSubTab);
		subTabRow.addComponent(buyOrdersSubTab);
		filters.addComponent(subTabRow);
		applyBrowseSubTabLook();

		searchField = new KitInputField("auctionSearch").onSubmit(v -> requestBrowse(0));
		WurmArrayPanel<FlexComponent> searchRow = row("searchRow");
		searchRow.addComponent(new WurmLabel("Search:"));
		searchRow.addComponent(searchField.field());
		refreshButton = new WButton("Refresh", this);
		searchRow.addComponent(refreshButton);
		filters.addComponent(searchRow);

		qlMinField = new KitInputField("qlMin").onSubmit(v -> requestBrowse(0));
		qlMaxField = new KitInputField("qlMax").onSubmit(v -> requestBrowse(0));
		priceMinField = new KitInputField("priceMin").onSubmit(v -> requestBrowse(0));
		priceMaxField = new KitInputField("priceMax").onSubmit(v -> requestBrowse(0));
		WurmArrayPanel<FlexComponent> rangeRow = row("rangeRow");
		rangeRow.addComponent(new WurmLabel("QL"));
		rangeRow.addComponent(qlMinField.field());
		rangeRow.addComponent(new WurmLabel("-"));
		rangeRow.addComponent(qlMaxField.field());
		rangeRow.addComponent(new WurmLabel("  Price"));
		rangeRow.addComponent(priceMinField.field());
		rangeRow.addComponent(new WurmLabel("-"));
		rangeRow.addComponent(priceMaxField.field());
		clearFiltersButton = new WButton("Clear", this);
		rangeRow.addComponent(clearFiltersButton);
		filters.addComponent(rangeRow);

		browsePanel.setComponent(filters, NORTH);

		buildCategoryTree();
		selectCategoryRow(categoryAllRow); // highlight "All categories" by default so a category is always visibly active
		// WEST sizes a child by its own width and stretches its height to fill the slot, so set width only.
		// Width = deepest indent (depth*16) + icon gap + longest label + the scroll panel's gutter.
		int labelWidth = text.getWidth("All categories");
		categoryTree.setSize(2 * 16 + 16 + labelWidth + 28, categoryTree.height);
		browsePanel.setComponent(categoryTree, WEST);

		// First column is the implicit name+icon column; these are the EXTRA columns only.
		browseTree = new WurmTreeList<>("auctionBook",
			new int[] { 55, 85, 110, 55 },
			new String[] { "QL", "Weight", "Price/Unit", "Partial" });
		browseTree.setKeepAutoSorted(true);
		browsePanel.setComponent(browseTree, CENTER);

		// A border panel gives its NORTH slot the full bottom width, so the pager decorator inside it
		// can actually center (a vertical array would shrink-wrap to the pager's own width).
		WurmBorderPanel bottom = new WurmBorderPanel("browseBottom");
		bottom.setComponent(buildPagerRow(), NORTH);

		WurmArrayPanel<FlexComponent> bottomStack = new WurmArrayPanel<>("browseBottomStack", V);

		// Total the player would pay for the entered amount (clamped to what the selected listing actually has).
		// Sits in the empty space above the amount row; blank until a listing is selected and an amount typed.
		browseTotalLabel = new WurmLabel(" ");
		bottomStack.addComponent(browseTotalLabel);

		browseActionsRow = row("browseActions");
		browseQty = new TabbedDualInput("buyQty", TabbedDualInput.LINE, "Qty", "kg")
			.sizes(36, 58)
			.onChange((mode, value) -> updateBrowseTotal());
		browseQty.setValue(0, "1");
		buySelectedButton = new WButton("Buy selected", this);
		placeBuyButton = new WButton("Create Buy Order", this);
		updateBrowseActions();
		bottomStack.addComponent(browseActionsRow);

		bottom.setComponent(bottomStack, SOUTH);
		browsePanel.setComponent(bottom, SOUTH);
	}

	/** The |&lt; &lt; Page X of Y &gt; &gt;| pager (WurmClientKit); its component centers when placed in a border slot. */
	private FlexComponent buildPagerRow() {
		pager = new PagerRow("browsePager").onPage(this::gotoPage);
		return pager.component();
	}

	private void buildCategoryTree() {
		// Name-only tree (no extra columns) so it renders just the label + expand arrow, no checkbox cells.
		categoryTree = new WurmTreeList<>("browseCategories",
			new int[0], new String[0]);
		categoryRows.clear();

		categoryAllRow = new AuctionCategoryRow(AuctionCategory.ALL, "All categories", false);
		categoryTree.addTreeListItem(categoryAllRow, null);
		categoryRows.add(categoryAllRow);

		for (AuctionCategory cat : AuctionCategory.roots()) {
			boolean hasChildren = AuctionCategory.hasChildren(cat.id);
			AuctionCategoryRow parent = new AuctionCategoryRow(cat.id, cat.displayName, hasChildren);
			categoryTree.addTreeListItem(parent, null);
			categoryRows.add(parent);
			if (hasChildren) {
				for (AuctionCategory leaf : AuctionCategory.childrenOf(cat.id)) {
					AuctionCategoryRow child = new AuctionCategoryRow(leaf.id, leaf.displayName, false);
					categoryTree.addTreeListItem(child, parent);
					categoryRows.add(child);
				}
			}
		}
		categoryTree.recalcLines();
	}

	/** Select exactly one category row (WurmTreeList exposes no public selector; drive its node list directly). */
	private void selectCategoryRow(AuctionCategoryRow target) {
		if (categoryTree == null) {
			return;
		}
		for (WTreeListNode<AuctionCategoryRow> node : categoryTree.lines) {
			node.setSelected(node.item == target);
		}
	}

	/** Poll for a category row selection (WurmTreeList has no change listener). */
	private void pollCategorySelection() {
		if (categoryTree == null) {
			return;
		}
		List<AuctionCategoryRow> sel = categoryTree.getSelections();
		if (sel == null || sel.isEmpty()) {
			return;
		}
		AuctionCategoryRow row = sel.get(0);
		if (row.categoryId == selectedCategoryId) {
			return;
		}
		selectedCategoryId = row.categoryId;
		requestBrowse(0);
	}

	// ---------------- Sell ----------------

	private void buildSell() {
		sellPanel = new WurmBorderPanel("sellPanel");

		// First column is the implicit name+icon column; QL / Weight / Qty are extra columns; the 4th extra column
		// is a per-row "Remove" cell button (KitTreeItem + GuiPatches.installTreeCellButtons) that clears staging.
		// Weight is the true amount staged; Qty is that rounded up to whole items, so it alone under-represents.
		sellTree = new WurmTreeList<>("sellStage",
			new int[] { 55, 75, 55, 70 },
			new String[] { "QL", "Weight", "Qty", "" });
		sellPanel.setComponent(sellTree, CENTER);

		// autoWidth so every row/label is stretched to the panel width (WurmLabel.setLabel never resizes, so a
		// shrink-wrap column would fix widths to the initial text and clip later, longer strings).
		WurmArrayPanel<FlexComponent> config = new WurmArrayPanel<>("sellConfig", V, true);
		config.addComponent(spacer());

		// Price as a 2x2 grid: two mode buttons (Per unit / Total) as tabs on the top row, each with its OWN input
		// field directly beneath it on the bottom row. Only the selected mode's field is editable; the other is
		// disabled. Built as two vertical columns in a horizontal row so each field aligns under its button (a
		// vertical array stretches its two children to the shared column width).
		perUnitButton = new WButton("Per unit", this);
		totalButton = new WButton("Total", this);
		perUnitField = new KitInputField("sellPricePerUnit").onChange(v -> onPriceEdited(v));
		perUnitField.setValue("0");
		totalField = new KitInputField("sellPriceTotal").onChange(v -> onPriceEdited(v));
		totalField.setValue("0");

		WurmArrayPanel<FlexComponent> perUnitCol = new WurmArrayPanel<>("sellPerUnitCol", V, true);
		perUnitCol.addComponent(perUnitButton);
		perUnitCol.addComponent(perUnitField.field());
		WurmArrayPanel<FlexComponent> totalCol = new WurmArrayPanel<>("sellTotalCol", V, true);
		totalCol.addComponent(totalButton);
		totalCol.addComponent(totalField.field());

		WurmArrayPanel<FlexComponent> priceGrid = row("sellPriceGrid");
		priceGrid.addComponent(perUnitCol);
		priceGrid.addComponent(totalCol);
		config.addComponent(priceGrid);

		setSellPriceMode(false); // default: Per unit editable, Total disabled

		config.addComponent(spacer());

		// Only meaningful for combinable/bulk goods: lets a buyer take less than one full item's worth. Wrapped in a
		// Collapsible so it can be hidden (position-stable) for non-combinable listings (see updateSellPreview).
		sellAllowPartial = new WCheckBox("Allow buying less than one full item");
		WurmArrayPanel<FlexComponent> partialRow = row("sellPartialRow");
		partialRow.addComponent(sellAllowPartial);
		partialBox = new Collapsible("sellPartialBox", partialRow);
		config.addComponent(partialBox.component());

		sellPartialWarning = new KitColorLabel("- required because non-full items are staged");
		sellPartialWarning.setColor(1.0f, 0.3f, 0.3f);
		WurmArrayPanel<FlexComponent> partialWarnRow = row("sellPartialWarnRow");
		partialWarnRow.addComponent(sellPartialWarning);
		sellPartialWarningBox = new Collapsible("sellPartialWarnBox", partialWarnRow);
		sellPartialWarningBox.setVisible(false);
		config.addComponent(sellPartialWarningBox.component());

		config.addComponent(spacer());

		sellDuration = new KitDropDown("sellDuration",
			new String[] { "1 day", "2 days", "3 days", "5 days", "7 days", "14 days", "30 days" })
			.onChange(i -> updateSellPreview()); // refresh the holding-fee line when the duration changes
		sellDuration.selectSilently(4); // default 7 days
		WurmArrayPanel<FlexComponent> durRow = row("durRow");
		durRow.addComponent(new WurmLabel("Listing duration:"));
		durRow.addComponent(sellDuration);
		config.addComponent(durRow);

		config.addComponent(spacer());

		sellPreviewLabel = new WurmLabel(" ");
		config.addComponent(sellPreviewLabel);

		// Dedicated pricing box: each market fact on its own line (trader offer / other sellers / buyers). NOT a
		// duplicate of what the player typed. Hidden entirely when there's nothing to show.
		sellPricing = new ValueLineBox("sellPricingBox", "- Pricing -");
		sellPricing.appendTo(config);

		config.addComponent(spacer());

		// Only the primary action at the bottom; Clear lives up by the item table (above). No "Remove selected" -
		// one staged listing is one row, nothing to select.
		WurmArrayPanel<FlexComponent> sellButtons = row("sellButtons");
		placeSellButton = new WButton("Place Sell Order", this);
		sellButtons.addComponent(placeSellButton);
		config.addComponent(sellButtons);

		sellPanel.setComponent(config, SOUTH);
	}

	// A horizontal row with a gap between its components (WurmArrayPanel has no padding; componentWidthOffset is the
	// only spacing knob) so buttons/fields never stick together into one blob.
	private static WurmArrayPanel<FlexComponent> row(String name) {
		WurmArrayPanel<FlexComponent> p = new WurmArrayPanel<>(name, H);
		p.componentWidthOffset = 6;
		return p;
	}

	// A thin fixed-height blank to separate stacked sections vertically (a vertical array has no gap concept).
	private static FlexComponent spacer() {
		return new WurmArrayPanel<>("sp", H, 1, 6);
	}

	// Selected tab = bright gold text; inactive-but-usable = white (a dimmed grey read as "disabled"). WButton
	// renders the pressed (down) state with dark art, which reads as disabled, so tabs are distinguished by colour only.
	private static void applyTabLook(WButton button, boolean active) {
		if (button == null) {
			return;
		}
		if (active) {
			button.setTextColor(1.0f, 0.82f, 0.4f);
		} else {
			button.setTextColor(1.0f, 1.0f, 1.0f);
		}
	}

	/** Colour the browse Sell/Buy sub-tabs to reflect the current source (gold = active). */
	private void applyBrowseSubTabLook() {
		applyTabLook(sellOrdersSubTab, !browseBuyMode);
		applyTabLook(buyOrdersSubTab, browseBuyMode);
	}

	private boolean isPerUnit() {
		return !sellTotalMode;
	}

	private KitInputField currentPriceField() {
		return isPerUnit() ? perUnitField : totalField;
	}

	// Select a price mode: press its tab button, enable its field and disable the other. Each field keeps its
	// own value across switches (nothing is cleared).
	private void setSellPriceMode(boolean total) {
		sellTotalMode = total;
		// Selected tab = bright gold text; inactive tab = white. Do NOT use setDown() - its pressed art is
		// dark and reads as "disabled", the opposite of "selected".
		applyTabLook(perUnitButton, !total);
		applyTabLook(totalButton, total);
		if (perUnitField != null) {
			perUnitField.setEnabled(!total);
		}
		if (totalField != null) {
			totalField.setEnabled(total);
		}
		KitInputField field = currentPriceField();
		sellPriceEdited = field != null && field.asLong(0L) > 0L;
		updatePlaceEnabled();
		updateSellPreview();
	}

	private void onPriceEdited(String value) {
		String p = value == null ? "" : value.trim();
		sellPriceEdited = !p.isEmpty() && !"0".equals(p);
		updatePlaceEnabled();
		updateSellPreview();
	}

	// Place Sell Order is only actionable with at least one staged item AND a price above zero AND, when non-full
	// (sub-unit) items are staged, the seller having allowed partial buying; grey it out otherwise so the player
	// sees up front that something is missing (the reason is still logged on a click).
	private void updatePlaceEnabled() {
		if (placeSellButton != null) {
			boolean ready = !stagedInputs.isEmpty() && sellPerUnitPriceIron() > 0L
				&& !(partialRequired() && !partialAllowed())
				&& !sellWholePieceViolation();
			placeSellButton.setEnabled(ready);
		}
	}

	/** True when the staged combinable total isn't a whole multiple of the item's unit weight (a sub-unit remainder). */
	private boolean stagedHasSubUnitRemainder() {
		if (stagePreview == null || !stagePreview.ok || stagePreview.templateWeightGrams <= 0f) {
			return false;
		}
		long total = Math.round(stagePreview.weightGrams);
		long unit = Math.round(stagePreview.templateWeightGrams);
		return unit > 0 && (total % unit) != 0;
	}

	// A sub-unit remainder can only ever be sold if the seller allows partial buying, so when splittable goods are
	// staged with a non-whole total the "allow partial" tick is required (or the staged amount must be made whole).
	private boolean partialRequired() {
		return stagePreview != null && stagePreview.ok && stagePreview.splittable && stagedHasSubUnitRemainder();
	}

	private boolean partialAllowed() {
		return sellAllowPartial != null && sellAllowPartial.checked;
	}

	// A non-combinable (bulk/discrete) staged listing whose total isn't a whole multiple of the unit weight can't be
	// sold - such goods trade only in whole pieces and there's no "allow partial" escape (unlike combinable goods).
	private boolean sellWholePieceViolation() {
		return stagePreview != null && stagePreview.ok && !stagePreview.splittable && stagedHasSubUnitRemainder();
	}

	// ---------------- My Orders ----------------

	private void buildOrders() {
		ordersPanel = new WurmBorderPanel("ordersPanel");

		// First column is the implicit name+icon column; the rest are the EXTRA columns. The trailing "" column is a
		// per-row "Cancel" cell button (KitTreeItem + GuiPatches.installTreeCellButtons) - one Cancel per order.
		sellOrdersTree = new WurmTreeList<>("mySellOrders",
			new int[] { 60, 100, 100, 90, 100, 70 },
			new String[] { "QL", "REMAINING", "PRICE/UNIT", "TIME LEFT", "STORAGE HELD", "" });
		buyOrdersTree = new WurmTreeList<>("myBuyOrders",
			new int[] { 70, 120, 100, 100, 70 },
			new String[] { "MIN QL", "REMAINING", "MAX PRICE", "COIN HELD", "" });
		sellOrdersTree.setKeepAutoSorted(true);
		buyOrdersTree.setKeepAutoSorted(true);

		// Layout per Docs/client-gui-layout.md #6: a WurmTreeList in a border CENTER is a bounded, scrollable
		// region. Sell section in the outer NORTH with a FIXED height; buy section in the outer CENTER (takes the
		// rest). Cancel is now a per-row cell button, so each section is just a label (NORTH) + list (CENTER).
		WurmBorderPanel sellSection = new WurmBorderPanel("sellOrdersSection");
		sellSection.setComponent(new WurmLabel("SELL ORDERS"), NORTH);
		sellSection.setComponent(sellOrdersTree, CENTER);

		WurmBorderPanel buySection = new WurmBorderPanel("buyOrdersSection");
		buySection.setComponent(new WurmLabel("BUY ORDERS"), NORTH);
		buySection.setComponent(buyOrdersTree, CENTER);

		// Fixed height for the top (sell) half; NORTH preserves it, CENTER (buy) fills the rest.
		sellSection.setSize(680, 250);
		WurmBorderPanel split = new WurmBorderPanel("ordersSplit");
		split.setComponent(sellSection, NORTH);
		split.setComponent(buySection, CENTER);
		ordersPanel.setComponent(split, CENTER);
	}

	// ---------------- Vault ----------------

	private void buildVault() {
		vaultPanel = new WurmBorderPanel("vaultPanel");

		vaultTopRow = new WurmArrayPanel<>("vaultTop", H);
		vaultTopRow.addComponent(new WurmLabel("Vault of:"));
		vaultOwnerIds.clear();
		vaultOwnerIds.add(0L);
		vaultDropDown = new KitDropDown("vaultPicker", new String[] { ownVaultLabel() }).onChange(this::onVaultPicked);
		vaultTopRow.addComponent(vaultDropDown);
		vaultPanel.setComponent(vaultTopRow, NORTH);

		// First column is the implicit name+icon column; the rest are the EXTRA columns.
		vaultTree = new WurmTreeList<>("auctionVault",
			new int[] { 60, 100, 90, 100 },
			new String[] { "QL", "WEIGHT", "AMOUNT", "TIME LEFT" });
		vaultTree.setKeepAutoSorted(true);
		vaultPanel.setComponent(vaultTree, CENTER);

		WurmArrayPanel<FlexComponent> vbtns = row("vaultButtons");
		vaultOpenButton = new WButton("Open vault container", this);
		vaultOpenButton.setHoverString("Opens the draggable vault container (you must be at an auctioneer in town).");
		vaultExtendButton = new WButton("Extend storage", this);
		vaultManageButton = new WButton("Manage access", this);
		vbtns.addComponent(vaultOpenButton);
		vbtns.addComponent(vaultExtendButton);
		vbtns.addComponent(vaultManageButton);
		// A spacer above lifts the buttons off the bottom window edge; row() gives them gaps (they were jammed
		// together and flush against the border otherwise).
		WurmArrayPanel<FlexComponent> vbottom = new WurmArrayPanel<>("vaultBottom", V);
		vbottom.addComponent(spacer());
		vbottom.addComponent(vbtns);
		vaultPanel.setComponent(vbottom, SOUTH);
	}

	private String ownVaultLabel() {
		try {
			String name = hud.world.getPlayer().getPlayerName();
			if (name != null && !name.isEmpty()) {
				return name + "'s vault";
			}
		} catch (Exception ignored) {
		}
		return "My vault";
	}

	private void onVaultPicked(int index) {
		if (index < 0 || index >= vaultOwnerIds.size()) {
			return;
		}
		selectedVaultOwner = vaultOwnerIds.get(index);
		AuctionClient.sendVaultList(selectedVaultOwner);
	}

	// ---------------- state -> view ----------------

	public void refreshFromState() {
		rebuildBrowse();
		rebuildSellStage();
		rebuildOrders();
		rebuildVault();
		updatePageLabel();
	}

	private void rebuildOrders() {
		if (sellOrdersTree == null || buyOrdersTree == null) {
			return;
		}
		sellOrdersTree.clear();
		for (AuctionClient.MySellRow s : AuctionClient.getMySellOrders()) {
			AuctionRow r = new AuctionRow(AuctionRow.Kind.MY_SELL_ORDER, s.templateId, s.name);
			r.imageNumber = s.imageNumber;
			r.orderId = s.orderId;
			r.ql = s.ql;
			r.rarity = s.rarity;
			r.perUnitIron = s.perTemplateIron;
			r.remaining = s.remainingPieces;
			r.timeLeftMs = s.timeLeftMs;
			r.heldIron = s.reserveHeldIron;
			r.onCancel = () -> AuctionClient.sendCancel(r.orderId);
			sellOrdersTree.addTreeListItem(r, null);
		}
		sellOrdersTree.recalcLines();

		buyOrdersTree.clear();
		for (AuctionClient.MyBuyRow b : AuctionClient.getMyBuyOrders()) {
			AuctionRow r = new AuctionRow(AuctionRow.Kind.MY_BUY, b.templateId, b.name);
			r.imageNumber = b.imageNumber;
			r.orderId = b.orderId;
			r.minQl = b.minQl;
			r.maxPerIron = b.maxPerTemplateIron;
			r.remaining = b.remainingPieces;
			r.heldIron = b.coinHeldIron;
			r.onCancel = () -> AuctionClient.sendCancel(r.orderId);
			buyOrdersTree.addTreeListItem(r, null);
		}
		buyOrdersTree.recalcLines();
	}

	// Render-only: the SERVER filters and paginates the book (see requestBrowse), so the client just draws the
	// page it was handed. No client-side filtering - a search or category never hides matches on other pages.
	private void rebuildBrowse() {
		if (browseTree == null) {
			return;
		}
		browseTree.clear();
		int shown = 0;
		for (AuctionClient.SnapshotRow s : AuctionClient.getSnapshot()) {
			AuctionRow row = new AuctionRow(AuctionRow.Kind.LISTING, s.templateId, s.name);
			row.imageNumber = s.imageNumber;
			row.orderId = s.orderId;
			row.ql = s.ql;
			row.rarity = s.rarity;
			row.enchanted = s.enchanted;
			row.allowPartial = s.allowPartial;
			row.combinable = s.combinable;
			row.perUnitIron = s.perTemplateIron;
			row.remaining = s.remainingPieces;
			row.weight = s.remainingWeight; // the listing's real total weight, not per-unit template weight
			row.templateWeight = s.templateWeight;
			row.bulkable = s.bulkable;
			browseTree.addTreeListItem(row, null);
			shown++;
		}
		browseTree.recalcLines();
		// The redraw drops any prior row selection, so clear/recompute the total now - otherwise a stale
		// "Total: ..." from a previously-selected listing lingers after switching tabs and back.
		browseTotalRowId = -1L;
		updateBrowseTotal();
		// An empty result is self-evident (empty list) - no status line needed (and it would spam the :Event log).
	}

	private boolean hasBrowseFilter() {
		return selectedCategoryId != AuctionCategory.ALL
			|| (searchField != null && !searchField.isBlank())
			|| qlMinField.asDoubleOrNull() != null || qlMaxField.asDoubleOrNull() != null
			|| !priceMinField.isBlank() || !priceMaxField.isBlank();
	}

	// Build the current filter from the browse controls and ask the server for the given page. The server owns
	// filtering + pagination; unset fields are sent as -1 (numeric) / "" (search) meaning "no constraint".
	private void requestBrowse(int page) {
		String search = searchField == null ? "" : searchField.getValue().trim();
		Double qlMin = qlMinField.asDoubleOrNull();
		Double qlMax = qlMaxField.asDoubleOrNull();
		// Price filters accept coin notation ("1s 2c") like every other price field, not just bare iron.
		Long priceMin = coinFilter(priceMinField);
		Long priceMax = coinFilter(priceMaxField);
		AuctionClient.sendQueryBook(0, page, AuctionClient.getSnapshotPageSize(),
			search, selectedCategoryId,
			qlMin != null ? qlMin.floatValue() : -1f,
			qlMax != null ? qlMax.floatValue() : -1f,
			priceMin != null ? priceMin.longValue() : -1L,
			priceMax != null ? priceMax.longValue() : -1L,
			browseBuyMode);
	}

	/** A price-filter field as iron (coin notation or bare number), or null when blank/unparseable = no constraint. */
	private static Long coinFilter(KitInputField field) {
		if (field == null || field.isBlank()) {
			return null;
		}
		long v = AuctionFormat.parseCoin(field.getValue(), -1L);
		return v < 0L ? null : v;
	}

	private void rebuildSellStage() {
		if (sellTree == null) {
			return;
		}
		sellTree.clear();
		// The staged set always folds into ONE listing (incompatible items are rejected on drop), so render that
		// single merged row - averaged QL and total pieces for bulk goods - rather than the individual sources.
		if (stagePreview != null && stagePreview.ok) {
			AuctionRow r = new AuctionRow(AuctionRow.Kind.MY_SELL, stagePreview.templateId, stagePreview.name);
			r.imageNumber = stagePreview.imageNumber;
			r.ql = stagePreview.ql;
			r.weight = stagePreview.weightGrams;   // true staged amount; Qty is this rounded up to whole items
			r.remaining = stagePreview.pieces;
			r.onRemove = this::clearStaging;   // the one merged row's Remove button clears the staging
			sellTree.addTreeListItem(r, null);
		}
		sellTree.recalcLines();
		updateSellPreview();
	}

	private void rebuildVault() {
		if (vaultTree == null) {
			return;
		}
		rebuildVaultDropDown();
		vaultTree.clear();
		for (AuctionClient.VaultRow v : AuctionClient.getVault()) {
			AuctionRow r = new AuctionRow(AuctionRow.Kind.VAULT, v.templateId, v.name);
			r.imageNumber = v.imageNumber;
			r.itemId = v.itemId;
			r.ql = v.ql;
			r.rarity = v.rarity;
			r.weight = v.weightGrams;
			r.timeLeftMs = v.remainingMs;
			r.remaining = v.amount;
			vaultTree.addTreeListItem(r, null);
		}
		vaultTree.recalcLines();
	}

	/**
	 * Rebuild the vault dropdown options from the accessible-vaults list, but only when the set of
	 * owner ids actually changed - otherwise the rebuild would reset the user's current selection on
	 * every refresh. Falls back to a single "My vault" entry (owner 0) before any vault packet arrives.
	 */
	private void rebuildVaultDropDown() {
		if (vaultDropDown == null || vaultTopRow == null) {
			return;
		}
		List<AuctionClient.VaultOwner> owners = AuctionClient.getAccessibleVaults();
		List<Long> newIds = new ArrayList<>();
		List<String> newLabels = new ArrayList<>();
		if (owners.isEmpty()) {
			newIds.add(0L);
			newLabels.add(ownVaultLabel());
		} else {
			for (AuctionClient.VaultOwner owner : owners) {
				newIds.add(owner.ownerId);
				newLabels.add(owner.name + "'s vault");
			}
		}
		if (newIds.equals(vaultOwnerIds)) {
			return;
		}
		vaultOwnerIds.clear();
		vaultOwnerIds.addAll(newIds);

		vaultTopRow.removeAllComponents();
		vaultTopRow.addComponent(new WurmLabel("Vault of:"));
		vaultDropDown = new KitDropDown("vaultPicker", newLabels.toArray(new String[0])).onChange(this::onVaultPicked);
		vaultTopRow.addComponent(vaultDropDown);

		// The rebuilt dropdown resets to index 0; keep selectedVaultOwner in sync with what's now shown so queries
		// target the displayed vault (otherwise the list shows owner[0] while data still targets the old owner).
		long shown = newIds.get(0);
		if (selectedVaultOwner != shown) {
			selectedVaultOwner = shown;
			AuctionClient.sendVaultList(shown);
		}
	}

	private void updatePageLabel() {
		if (pager != null) {
			pager.setState(AuctionClient.getSnapshotPage(), AuctionClient.getTotalPages());
		}
	}

	/**
	 * The staged items and their QL/qty are shown in the sell tree; the duration is in the dropdown; the price is
	 * in the field. So this only fills what those DON'T show: a one-line total confirmation, and the dedicated
	 * pricing box (trader offer + player-market spread). No duplication of the tree/dropdown/field.
	 */
	private void updateSellPreview() {
		updatePlaceEnabled();
		if (sellPreviewLabel == null) {
			return;
		}
		if (sellPricing != null) {
			sellPricing.clear();
		}
		// Partial (sub-unit weight) buying only applies to truly combinable goods (dirt-like), NOT bulk (shaft/
		// plank/log) or discrete items - bulk always sells whole pieces. Hide the option unless splittable.
		setPartialVisible(stagePreview == null || !stagePreview.ok || stagePreview.splittable);
		// Sub-unit remainder handling (Place Sell Order is blocked by updatePlaceEnabled in both cases):
		//  - combinable goods: partial is allowed, so just require the "allow partial" tick (or a whole amount);
		//  - non-combinable (bulk/discrete) goods: they sell ONLY in whole pieces, so a non-whole total is a hard
		//    error with no opt-in - the player must stage an exact multiple of the unit weight.
		if (sellPartialWarningBox != null) {
			if (partialRequired() && !partialAllowed()) {
				sellPartialWarning.setLabel("- required because non-full items are staged");
				sellPartialWarningBox.setVisible(true);
			} else if (sellWholePieceViolation()) {
				sellPartialWarning.setLabel("- this item sells in whole pieces only; stage an exact multiple of "
					+ AuctionFormat.weight(stagePreview.templateWeightGrams) + " kg");
				sellPartialWarningBox.setVisible(true);
			} else {
				sellPartialWarningBox.setVisible(false);
			}
		}
		if (stagedInputs.isEmpty() || stagePreview == null) {
			sellPreviewLabel.setLabel("Drag item stacks here to stage them, then set a price.");
			return;
		}
		if (!stagePreview.ok) {
			sellPreviewLabel.setLabel(stagePreview.message);
			return;
		}
		// No echo of the entered price and no "enter a price" nag - the field is right there, and a missing price is
		// reported (to the :Event log) only when the player actually clicks Place Sell Order.
		sellPreviewLabel.setLabel(" ");
		// Only facts that exist - never a "nothing here" line. Pure information, no instructions on what to do.
		sellPricing.set(
			stagePreview.traderTotalIron > 0
				? "Trader would pay " + AuctionFormat.coin(stagePreview.traderTotalIron) + " for the whole lot ("
					+ stagePreview.pieces + (stagePreview.pieces == 1 ? " item)." : " items).")
				: null,
			stagePreview.marketBestAsk > 0
				? "Other sellers ask from " + AuctionFormat.coin(stagePreview.marketBestAsk) + " each."
				: null,
			stagePreview.marketBestBid > 0
				? "Buyers bid up to " + AuctionFormat.coin(stagePreview.marketBestBid) + " each."
				: null,
			sellHoldingFeeLine());
	}

	// The coin reserved up front to hold the resting stock for the selected duration (demurrage). Computed live from
	// the entered price + duration and the server-supplied rate; null until a price is entered (the fee scales with it).
	// The server is authoritative at placement - this is a preview estimate mirroring Demurrage.DailyTaxIron/ReserveIron.
	private String sellHoldingFeeLine() {
		if (stagePreview == null || !stagePreview.ok || stagePreview.demurrageRatePerDay <= 0f) {
			return null;
		}
		long perUnit = sellPerUnitPriceIron();
		long pieces = stagePreview.pieces;
		if (perUnit <= 0L || pieces <= 0L) {
			return null;
		}
		int days = selectedDurationDays();
		long dailyTax = Math.max(1L, (long) Math.ceil((double) stagePreview.demurrageRatePerDay * perUnit * pieces));
		long reserve = dailyTax * days;
		return "Holding fee: " + AuctionFormat.coin(reserve) + " reserved for " + days + (days == 1 ? " day" : " days")
			+ " (" + AuctionFormat.coin(dailyTax) + " / day).";
	}

	/** Show/hide the "allow partial" row (position-stable via Collapsible); non-combinable listings can't be split. */
	private void setPartialVisible(boolean visible) {
		if (partialBox == null || sellAllowPartial == null) {
			return;
		}
		if (!visible) {
			sellAllowPartial.checked = false;
		}
		partialBox.setVisible(visible);
	}

	/**
	 * A malformed / unparseable auction packet means the SERVER has a bug - never swallow it silently. Surface
	 * it loudly (red on-screen message + status line) so it gets noticed and fixed, and log it.
	 */
	public void showProtocolError(String detail) {
		eventLog("Auction: bad data from server (" + detail + ") - this is a server bug.", 1.0f, 0.3f, 0.3f);
	}

	/** Route auction feedback to the :Event chat tab (never a center-screen popup - the player reads it there). */
	private static void eventLog(String msg, float r, float g, float b) {
		if (WurmComponent.hud != null) {
			WurmComponent.hud.textMessage(":Event", r, g, b, msg);
		}
	}

	public void onResult(boolean ok, long orderId, String message) {
		// The server pushes the (coloured) result line to the event log itself - the client only reacts to the
		// outcome by refreshing the active tab. It must NOT also print `message`, or the line shows twice.
		if (!ok) {
			return;
		}
		// Refresh whatever tab is active so its view reflects the action's result (Buy selected, Extend
		// storage, etc. previously left stale lists). A successful Sell also wipes the staging list.
		switch (selected()) {
			case AuctionProtocol.TAB_SELL:
				clearStaging();
				break;
			case AuctionProtocol.TAB_ORDERS:
				AuctionClient.sendQueryMyOrders();
				break;
			case AuctionProtocol.TAB_BROWSE:
				requestBrowse(AuctionClient.getSnapshotPage());
				break;
			case AuctionProtocol.TAB_VAULT:
				AuctionClient.sendVaultList(selectedVaultOwner);
				break;
			default:
				break;
		}
	}

	// ---------------- input ----------------


	private int selectedDurationDays() {
		int[] days = { 1, 2, 3, 5, 7, 14, 30 };
		int idx = sellDuration == null ? 4 : sellDuration.selected();
		return days[Math.max(0, Math.min(days.length - 1, idx))];
	}

	private AuctionRow firstChecked(WurmTreeList<AuctionRow> tree) {
		if (tree == null) {
			return null;
		}
		List<AuctionRow> sel = tree.getSelections();
		if (sel != null && !sel.isEmpty()) {
			return sel.get(0);
		}
		return null;
	}

	@Override
	public void gameTick() {
		super.gameTick();
		// The disabled-field tint (r/g/b) is reset to default by re-layout (which a tab switch triggers), so a
		// one-shot re-assert loses it again. Re-assert every frame - idempotent, and gameTick runs before render, so
		// the greyed inactive field is correct on every drawn frame. Field enable state only (no preview side effects).
		if (browseQty != null) {
			browseQty.reapplyEnabled();
		}
		if (perUnitButton != null && totalButton != null) {
			applyTabLook(perUnitButton, !sellTotalMode);
			applyTabLook(totalButton, sellTotalMode);
		}
		if (sellOrdersSubTab != null && buyOrdersSubTab != null) {
			applyBrowseSubTabLook();
		}
		if (perUnitField != null) {
			perUnitField.setEnabled(!sellTotalMode);
		}
		if (totalField != null) {
			totalField.setEnabled(sellTotalMode);
		}
		pollCategorySelection();
		// WCheckBox has no change listener; poll the "allow partial" tick so ticking it live-clears the red
		// non-full-items warning and re-enables Place Sell Order.
		if (sellAllowPartial != null && sellAllowPartial.checked != lastSellPartialChecked) {
			lastSellPartialChecked = sellAllowPartial.checked;
			updateSellPreview();
		}
		// Arbitrary-weight (kg) buying is only possible on a listing the seller opened for partial (combinable);
		// everything else sells in whole pieces, so grey out the kg tab unless the selected listing is partial.
		if (browseQty != null && browseTree != null) {
			AuctionRow sel = firstChecked(browseTree);
			boolean partial = sel != null && sel.combinable && sel.allowPartial;
			browseQty.setTabEnabled(1, partial);
			// "Buy selected" only makes sense with a listing selected - grey it out otherwise (it can't act anyway).
			if (buySelectedButton != null) {
				boolean canBuy = sel != null && sel.kind == AuctionRow.Kind.LISTING && sel.orderId != 0L;
				buySelectedButton.setEnabled(canBuy);
			}
			// Re-run the total when the selected listing changes (selection has no change listener).
			long selId = sel != null ? sel.orderId : -1L;
			if (selId != browseTotalRowId) {
				browseTotalRowId = selId;
				// Pre-fill the amount with the WHOLE selected lot so "Buy selected" buys all of it by default; the
				// buyer only edits the field to take less. Pick the tab that matches the lot: kg (full weight) for a
				// seller-opened-partial combinable, else Units (full piece count).
				if (!browseBuyMode && sel != null && sel.kind == AuctionRow.Kind.LISTING) {
					autoFillBrowseAmount(sel, partial);
				}
				updateBrowseTotal();
			}
		}
	}

	// Fill the Qty/kg field with the entire selected lot (whole-lot default for "Buy selected").
	private void autoFillBrowseAmount(AuctionRow lot, boolean partial) {
		if (browseQty == null) {
			return;
		}
		if (partial) {
			browseQty.selectSilently(1); // kg tab (enabled just above for a partial lot)
			browseQty.setValue(1, trimKg(lot.weight));
		} else {
			browseQty.selectSilently(0); // Units tab
			browseQty.setValue(0, Long.toString(lot.remaining));
		}
	}

	// grams -> compact kg string ("5", "5.5"), no trailing ".0" (mirrors the Buy-order dialog's formatter).
	private static String trimKg(float grams) {
		double kg = grams / 1000.0;
		return kg == Math.floor(kg)
			? Long.toString((long) kg)
			: String.format("%.2f", kg).replaceAll("0+$", "").replaceAll("\\.$", "");
	}

	// "Total: <coin>" for the amount typed into the Qty/kg field, clamped to what the selected listing actually has.
	// Fired on every amount/tab change and when the selected listing changes. Blank when nothing sensible to show.
	private void updateBrowseTotal() {
		if (browseTotalLabel == null || browseQty == null) {
			return;
		}
		AuctionRow row = firstChecked(browseTree);
		if (browseBuyMode || row == null || row.kind != AuctionRow.Kind.LISTING || row.perUnitIron <= 0L) {
			browseTotalLabel.setLabel(" ");
			return;
		}
		long total;
		if (browseQty.selected() == 1) {
			Double kg = browseQty.asDoubleOrNull();
			double grams = (kg != null ? kg : 0.0) * 1000.0;
			if (grams <= 0.0 || row.templateWeight <= 0f) {
				browseTotalLabel.setLabel(" ");
				return;
			}
			double clamped = row.weight >= 0f ? Math.min(grams, row.weight) : grams;
			total = Math.max(1L, (long) Math.ceil(row.perUnitIron * (clamped / row.templateWeight)));
		} else {
			long pieces = browseQty.asLong(0L);
			if (pieces <= 0L) {
				browseTotalLabel.setLabel(" ");
				return;
			}
			long clamped = row.remaining > 0 ? Math.min(pieces, row.remaining) : pieces;
			total = row.perUnitIron * clamped;
		}
		browseTotalLabel.setLabel("Total: " + AuctionFormat.coin(total));
	}

	@Override
	public void buttonPressed(WButton button) {
	}

	@Override
	public void buttonClicked(WButton button) {
		if (button == refreshButton) {
			doRefresh();
		} else if (button == clearFiltersButton) {
			doClearFilters();
		} else if (button == sellOrdersSubTab) {
			setBrowseBuyMode(false);
		} else if (button == buyOrdersSubTab) {
			setBrowseBuyMode(true);
		} else if (button == buySelectedButton) {
			doBuySelected();
		} else if (button == placeBuyButton) {
			doPlaceBuy();
		} else if (button == placeSellButton) {
			doSell();
		} else if (button == perUnitButton) {
			setSellPriceMode(false);
		} else if (button == totalButton) {
			setSellPriceMode(true);
		} else if (button == vaultOpenButton) {
			AuctionClient.sendVaultOpen(selectedVaultOwner);
		} else if (button == vaultExtendButton) {
			doVaultExtend();
		} else if (button == vaultManageButton) {
			doVaultManage();
		}
	}

	// Switch the browse data source (Sell orders / Buy orders sub-tab) and re-query; the total is meaningless in
	// buy-order view, so it's cleared by updateBrowseTotal (browseBuyMode gate).
	private void setBrowseBuyMode(boolean buy) {
		if (browseBuyMode == buy) {
			return;
		}
		browseBuyMode = buy;
		applyBrowseSubTabLook();
		updateBrowseActions();
		updateBrowseTotal();
		requestBrowse(0);
	}

	// Buying/quantity controls only make sense when viewing SELL listings. In the Buy-orders view they're hidden
	// (you can't "buy" someone's demand), leaving just "Create Buy Order".
	private void updateBrowseActions() {
		if (browseActionsRow == null) {
			return;
		}
		browseActionsRow.removeAllComponents();
		if (!browseBuyMode) {
			browseActionsRow.addComponent(browseQty.panel());
			browseActionsRow.addComponent(buySelectedButton);
		}
		browseActionsRow.addComponent(placeBuyButton);
	}

	private void doClearFilters() {
		if (searchField != null) searchField.clear();
		if (qlMinField != null) qlMinField.clear();
		if (qlMaxField != null) qlMaxField.clear();
		if (priceMinField != null) priceMinField.clear();
		if (priceMaxField != null) priceMaxField.clear();
		selectedCategoryId = AuctionCategory.ALL;
		selectCategoryRow(categoryAllRow);
		requestBrowse(0);
	}

	private void doRefresh() {
		long now = System.currentTimeMillis();
		if (now - lastRefreshMs < 5000L) {
			status("Refresh on cooldown (5s).");
			return;
		}
		lastRefreshMs = now;
		requestBrowse(AuctionClient.getSnapshotPage());
	}

	private void gotoPage(int page) {
		int total = AuctionClient.getTotalPages();
		if (page < 0) {
			page = 0;
		}
		if (page > total - 1) {
			page = total - 1;
		}
		requestBrowse(page);
	}

	private void doBuySelected() {
		if (browseBuyMode) {
			status("Those are buy orders - to sell to one, stage matching items on the Sell tab.");
			return;
		}
		AuctionRow row = firstChecked(browseTree);
		if (row == null || row.kind != AuctionRow.Kind.LISTING || row.orderId == 0L) {
			return;
		}
		boolean weightUnit = browseQty != null && browseQty.selected() == 1;
		// kg is a decimal amount (e.g. 0.1 kg = 100 g); parse it as a double, not a long (which dropped the decimal).
		Double kg = weightUnit ? browseQty.asDoubleOrNull() : null;
		long pieces = weightUnit ? 0L : browseQty.asLong(1L);
		float weight = weightUnit ? (float) ((kg != null ? kg : 1.0) * 1000.0) : 0f;
		AuctionClient.sendBuySelected(row.orderId, pieces, weight);
	}

	/**
	 * Open the Place Buy Order dialog. A buy order needs no existing listing - the dialog's "Choose item..."
	 * picker searches the item catalog - but if a Browse listing is selected we pre-fill its item.
	 */
	private void doPlaceBuy() {
		AuctionRow row = firstChecked(browseTree);
		boolean fromListing = row != null && row.kind == AuctionRow.Kind.LISTING;
		int templateId = fromListing ? row.templateId : -1;
		String name = row != null ? row.displayName : "";
		int unitWeightGrams = fromListing && row.templateWeight > 0f ? (int) row.templateWeight : 0;
		int imageNumber = fromListing ? row.imageNumber : 0;
		BuyOrderWindow.getOrCreate().open(templateId, name, unitWeightGrams, imageNumber);
	}

	/**
	 * Place a sell order. There is no separate "Sell now": the server satisfies the order immediately from
	 * existing buy orders and the trader (only at or above the entered price), rests the remainder, and reports
	 * how much sold now vs rested. Deciding order-vs-immediate is the server's job, not the player's.
	 */
	private void doSell() {
		if (stagedInputs.isEmpty()) {
			warnSell("Stage at least one item to sell.");
			return;
		}
		if (stagePreview == null || !stagePreview.ok) {
			warnSell(stagePreview != null && stagePreview.message != null && !stagePreview.message.isEmpty()
				? stagePreview.message
				: "These items can't be sold as one listing.");
			return;
		}
		long perUnit = sellPerUnitPriceIron();
		if (perUnit <= 0) {
			warnSell(!isPerUnit()
				? "That total divided across " + stagePreview.pieces + " units rounds to zero per unit - enter a higher total or switch to Per unit."
				: "Enter a price above zero.");
			return;
		}
		boolean partial = sellAllowPartial != null && sellAllowPartial.checked;
		int days = selectedDurationDays();
		// Send the whole dragged set (itemId, amount). The server re-validates every item is still present and
		// recomputes the single listing (bulk goods averaged); it commits atomically or aborts and warns.
		long[] itemIds = new long[stagedInputs.size()];
		long[] quantities = new long[stagedInputs.size()];
		int i = 0;
		for (java.util.Map.Entry<Long, Long> e : stagedInputs.entrySet()) {
			itemIds[i] = e.getKey();
			quantities[i] = e.getValue() == null ? 0L : e.getValue();
			i++;
		}
		AuctionClient.sendPostSell(0, 0, (byte) 0, partial, perUnit, days, itemIds, quantities);
	}

	/**
	 * The per-unit iron price to list at. The server rests a per-unit price; in total-price mode the entered
	 * total is divided across the server-computed piece count (always exact - no client guessing).
	 */
	private long sellPerUnitPriceIron() {
		if (isPerUnit()) {
			return perUnitField == null ? 0L : AuctionFormat.parseCoin(perUnitField.getValue(), 0L);
		}
		long total = totalField == null ? 0L : AuctionFormat.parseCoin(totalField.getValue(), 0L);
		long units = stagePreview != null && stagePreview.ok ? stagePreview.pieces : 0L;
		// Round the per-unit price UP so the listed total is never LESS than the seller's entered total (a floor
		// would silently drop up to units-1 iron of their asking).
		return units > 0 ? (total + units - 1) / units : total;
	}

	private void doVaultExtend() {
		AuctionRow row = firstChecked(vaultTree);
		if (row == null || row.kind != AuctionRow.Kind.VAULT) {
			status("Select a vault item to extend.");
			return;
		}
		AuctionClient.sendVaultExtend(row.itemId, 7);
	}

	/**
	 * Requests the manage-permissions window for the viewed vault. No id prompt - the request carries the
	 * vault owner id; the server opens the vanilla permissions management window for that vault.
	 */
	private void doVaultManage() {
		AuctionClient.sendVaultPerms(selectedVaultOwner, 0);
	}

	/**
	 * Drop handler: items dragged onto the window while the Sell tab is active are added to the raw input set
	 * (whole item by default) and the server is asked to recompute the whole Sell-window display. The client
	 * never decides identity, quality or piece counts - the server does, in {@link #onStagePreview}.
	 */
	@Override
	void itemDropped(int xMouse, int yMouse, DraggableComponent draggedItem) {
		if (selected() != AuctionProtocol.TAB_SELL) {
			super.itemDropped(xMouse, yMouse, draggedItem);
			return;
		}
		// A stack dragged from a bulk container (BSB): ask how many to sell, exactly like taking from the BSB.
		// The server clamps the entered amount to what's actually there.
		if (draggedItem instanceof InventoryContainerWindow.InventoryContainerItem) {
			InventoryMetaItem item = ((InventoryContainerWindow.InventoryContainerItem) draggedItem).getItem();
			if (item != null) {
				focusTab(AuctionProtocol.TAB_SELL);
				promptSellAmount(item.getId());
				return;
			}
		}
		long[] droppedIds = droppedItemIds(draggedItem);
		if (droppedIds.length == 0) {
			super.itemDropped(xMouse, yMouse, draggedItem);
			return;
		}
		for (long id : droppedIds) {
			// putIfAbsent returns null when the id was newly added - track those so a bad mix can be rolled back.
			if (stagedInputs.putIfAbsent(id, 0L) == null) {
				pendingStageAdd.add(id);
			}
		}
		focusTab(AuctionProtocol.TAB_SELL);
		refreshStagePreview();
	}

	/** BML "how many to sell?" prompt for a bulk-container stack; blank/0 = all. The server clamps the amount. */
	private void promptSellAmount(final long itemId) {
		String bml = "<varray rescale='true'>"
			+ "<text type='bold' text='How many to sell? (blank = all)' />"
			+ "<input id='answer' maxchars='9' text='' />"
			+ "<harray><button text='Stage' id='ok' /></harray></varray>";
		BmlWindowComponent prompt = new BmlWindowComponent("Sell quantity", bml, new BmlWindowListener() {
			@Override
			public void cancel(BmlWindowComponent window) {
				WurmComponent.hud.removeDynamicComponent(window);
			}

			@Override
			public void submit(BmlWindowComponent window, String buttonName) {
				WurmComponent.hud.removeDynamicComponent(window);
				if (!"ok".equals(buttonName)) {
					return;
				}
				long amount = 0L; // 0 = whole stack; the server resolves/clamps the real piece count
				String answer = window.buildOutMap().get("answer");
				if (answer != null && answer.trim().length() > 0) {
					try {
						amount = Long.parseLong(answer.trim());
					} catch (NumberFormatException ignored) {
						amount = 0L;
					}
					if (amount < 0L) {
						amount = 0L;
					}
				}
				boolean isNew = !stagedInputs.containsKey(itemId);
				stagedInputs.put(itemId, amount);
				if (isNew) {
					pendingStageAdd.add(itemId);
				}
				refreshStagePreview();
			}
		});
		prompt.setInitialSize(220, 110, true);
		WurmComponent.hud.addDynamicComponent(prompt);
	}

	/** Send the current dragged set to the server and let it compute the display. */
	private void refreshStagePreview() {
		if (stagedInputs.isEmpty()) {
			stagePreview = null;
			rebuildSellStage();
			return;
		}
		long[] ids = new long[stagedInputs.size()];
		long[] amounts = new long[stagedInputs.size()];
		int i = 0;
		for (java.util.Map.Entry<Long, Long> e : stagedInputs.entrySet()) {
			ids[i] = e.getKey();
			amounts[i] = e.getValue() == null ? 0L : e.getValue();
			i++;
		}
		AuctionClient.sendQueryStagePreview(ids, amounts);
	}

	/** Server-computed Sell-window display for the current dragged set. The client just renders it. */
	public void onStagePreview(AuctionClient.StagePreview preview) {
		// A Sell is ONE listing (same item, material and rarity). If the item(s) just added break that, roll them
		// back and warn - the player can't create a bad mix in the first place; the previous good staging stays.
		if (preview != null && !preview.ok && !pendingStageAdd.isEmpty()) {
			for (Long id : pendingStageAdd) {
				stagedInputs.remove(id);
			}
			pendingStageAdd.clear();
			warnSell(preview.message != null && !preview.message.isEmpty()
				? preview.message
				: "That item can't be combined with your listing - same item, material and rarity only.");
			refreshStagePreview(); // re-query to restore the previous good display
			return;
		}

		stagePreview = preview;
		// Store the server-CLAMPED amount per source, so subsequent updates/orders carry the real count
		// (e.g. a "999" typed for a 50-stack becomes 50). Clear pending rollback markers SELECTIVELY - only for
		// the ids this ok reply actually confirmed - not a blanket clear: with two rapid drops in flight, an
		// intermediate ok reply must not wipe the still-pending marker of a later incompatible drop, or its !ok
		// reply would find pending empty and leave the bad item stuck (a blanket clear did exactly that).
		if (preview != null && preview.ok) {
			for (AuctionClient.PreviewSource src : preview.sources) {
				pendingStageAdd.remove(Long.valueOf(src.itemId)); // by value, not list index
				if (stagedInputs.containsKey(src.itemId)) {
					stagedInputs.put(src.itemId, src.pieces);
				}
			}
		}
		rebuildSellStage();
		updateSellPreview();
	}

	/** Amber warning for a rejected drop (a normal user error) - to the :Event log, not a center-screen popup. */
	private void warnSell(String msg) {
		eventLog(msg, 1.0f, 0.75f, 0.2f);
	}

	/**
	 * Resolve a drag to the actual item ids to stage, using the vanilla command-target resolution so a folded
	 * group ("shaft (11x)") expands to ALL its members and a multi-selection stages every selected item - the
	 * group's members live on GroupTreeListItem, not InventoryMetaItem.getChildren(), so we can't recurse them.
	 */
	private long[] droppedItemIds(DraggableComponent draggedItem) {
		if (draggedItem instanceof InventoryListComponent.InventoryTreeListItem) {
			InventoryListComponent.InventoryTreeListItem tli = (InventoryListComponent.InventoryTreeListItem) draggedItem;
			List<InventoryListComponent.InventoryTreeListItem> sel = tli.getOwner().itemList.getSelections();
			if (sel != null && !sel.isEmpty() && sel.contains(tli)) {
				return tli.getOwner().getSelectedCommandTargets();
			}
			return tli.getCommandTargetIds();
		} else if (draggedItem instanceof InventoryContainerWindow.InventoryContainerItem) {
			return ((InventoryContainerWindow.InventoryContainerItem) draggedItem).getSelectedCommandTargets();
		} else if (draggedItem instanceof PaperDollItem) {
			InventoryMetaItem item = ((PaperDollItem) draggedItem).getItem();
			return item != null ? new long[] { item.getId() } : new long[0];
		}
		return new long[0];
	}

}
