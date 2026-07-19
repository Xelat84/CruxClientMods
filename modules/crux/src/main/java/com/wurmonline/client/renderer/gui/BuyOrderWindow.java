package com.wurmonline.client.renderer.gui;

import java.util.List;

import com.wurmonline.clientmods.auctionhouse.AuctionCategory;
import com.wurmonline.clientmods.auctionhouse.AuctionClient;
import com.wurmonline.clientmods.auctionhouse.AuctionProtocol;

/**
 * The Create Buy Order dialog. A buy order escrows a bank coin-hold with no goods, so it can be placed for an item
 * that isn't currently listed. The window is a two-phase wizard:
 *
 * <ul>
 *   <li><b>Pick phase</b> - the only thing shown is the item catalogue (search + table). The client has no template
 *       registry, so the list comes from the server ({@code SendAuctionItemMatches}).</li>
 *   <li><b>Order phase</b> - once an item is picked the table disappears and the order fields appear, with the chosen
 *       item ([icon] [name]) and a "Change item" button at the top. "Change item" returns to the pick phase.</li>
 * </ul>
 *
 * Lives in the game GUI package because the widget classes are package-private.
 */
public class BuyOrderWindow extends WWindow implements ButtonListener {

	private static BuyOrderWindow instance;

	public static BuyOrderWindow getOrCreate() {
		if (instance == null) {
			instance = new BuyOrderWindow();
		}
		return instance;
	}

	private static final int SOUTH = WurmBorderPanel.SOUTH;
	private static final int CENTER = WurmBorderPanel.CENTER;
	private static final int H = WurmArrayPanel.DIR_HORIZONTAL;
	private static final int V = WurmArrayPanel.DIR_VERTICAL;

	private final WurmBorderPanel root;

	// Pick phase: search + results table.
	private KitInputField pickerSearch;
	private WButton pickerFindButton;
	private WurmTreeList<AuctionRow> pickerTree;
	private WurmArrayPanel<FlexComponent> pickerPanel;
	private boolean pickerVisible;

	// Order phase: the chosen-item header ([icon] [name] [Change item]) + all the order fields.
	private WurmArrayPanel<FlexComponent> formPanel;
	private WurmArrayPanel<FlexComponent> headerRow;
	private ItemIconComponent chosenIcon;
	private WButton changeItemButton;

	private TabbedDualInput amountTabs; // 0 = units, 1 = total kg
	private TabbedDualInput priceTabs;  // 0 = per unit, 1 = total budget

	private KitInputField minQlField;
	private KitDropDown minRarityDrop;
	private WCheckBox excludeEnchantedBox;
	// Buyer opt-in to receiving partial (sub-unit) amounts - shown only for combinable items (like the Sell tab).
	private WCheckBox allowPartialBox;
	private Collapsible allowPartialWrap;

	// Material selection (matters hugely for balance - oak vs olive log, iron vs steel lump). Rebuilt when the
	// server sends the chosen item's valid materials; index 0 is always "Any material".
	private WurmArrayPanel<FlexComponent> materialRow;
	private KitDropDown materialDrop;
	private final List<Byte> materialBytes = new java.util.ArrayList<>();

	private WurmLabel escrowLabel;
	private ValueLineBox buyPricing;
	private WButton placeButton;
	private WButton cancelButton;
	private WurmArrayPanel<FlexComponent> buttonsRow;

	// Last (template, material, minQl) we asked the server to quote - re-request only when it changes.
	private int quotedTemplate = -1;
	private byte quotedMaterial = -128;
	private int quotedMinQl = -1;

	private int chosenTemplateId = -1;
	private String chosenName = "";
	private int chosenImageNumber = 0;
	private int chosenUnitWeightGrams = 0; // per-unit weight of the chosen item; lets a by-weight order size exactly
	private boolean chosenSplittable; // truly combinable (can be bought in an arbitrary sub-unit weight)
	// Red hint shown when a kg amount for a non-combinable (whole-piece) item isn't a whole multiple of the unit
	// weight; names the nearest valid kg values. Buying is blocked while it shows.
	private KitColorLabel amountWarning;
	private Collapsible amountWarningWrap;

	private BuyOrderWindow() {
		super("BuyOrder", false);
		setTitle("Create Buy Order");
		closeable = true;
		root = new WurmBorderPanel("buyOrderRoot");
		build();
		setComponent(root);
		setInitialSize(560, 620, false, 0.5F, 0.5F);
		AuctionClient.setItemMatchesListener(this::refreshPicker);
		AuctionClient.setItemMaterialsListener(this::rebuildMaterials);
	}

	/** Open the dialog, optionally pre-filled with an item chosen in the Browse list (its per-unit weight too). */
	public void open(int templateId, String name, int unitWeightGrams, int imageNumber) {
		if (templateId > 0) {
			setChosen(templateId, name, unitWeightGrams, imageNumber);
			showForm();
		} else {
			// No item pre-filled - open straight in the pick phase (the whole catalogue, ready to search/click).
			showPicker();
		}
		if (!hud.isComponentEnabled(this)) {
			hud.toggleComponent(this);
		}
		recalc();
	}

	@Override
	protected void closePressed() {
		if (hud.isComponentEnabled(this)) {
			hud.toggleComponent(this);
		}
	}

	private void build() {
		// ---- chosen-item header (order phase) ----
		// 16px item icon; it draws the chosen name itself (one component, shared baseline) so icon and name always align.
		chosenIcon = new ItemIconComponent(16);
		changeItemButton = new WButton("Change item", this);
		headerRow = row("buyHeaderRow");

		// ---- order-phase form ----
		formPanel = new WurmArrayPanel<>("buyBody", V, true);
		formPanel.addComponent(headerRow);
		formPanel.addComponent(spacer());

		amountTabs = new TabbedDualInput("buyAmount", TabbedDualInput.GRID, "Amount:", "Units", "kg")
			.sizes(50, 70)
			.onChange((m, v) -> recalc());
		amountTabs.setValue(0, "1");
		formPanel.addComponent(amountTabs.panel());

		amountWarning = new KitColorLabel("");
		amountWarning.setColor(1.0f, 0.3f, 0.3f);
		WurmArrayPanel<FlexComponent> amountWarnRow = row("buyAmountWarnRow");
		amountWarnRow.addComponent(amountWarning);
		amountWarningWrap = new Collapsible("buyAmountWarnWrap", amountWarnRow);
		amountWarningWrap.setVisible(false);
		formPanel.addComponent(amountWarningWrap.component());

		formPanel.addComponent(spacer());

		priceTabs = new TabbedDualInput("buyPrice", TabbedDualInput.GRID, "Price:", "Per unit", "Total")
			.sizes(56, 72)
			.onChange((m, v) -> recalc());
		priceTabs.setValue(0, "0");
		formPanel.addComponent(priceTabs.panel());

		formPanel.addComponent(spacer());

		minQlField = new KitInputField("buyMinQl");
		minQlField.setValue("0");
		WurmArrayPanel<FlexComponent> qlRow = row("buyQlRow");
		qlRow.addComponent(new WurmLabel("Min quality (QL):"));
		qlRow.addComponent(minQlField.field());
		formPanel.addComponent(qlRow);

		minRarityDrop = new KitDropDown("buyMinRarity", 0, new String[] { "Any", "Rare", "Supreme", "Fantastic" });
		WurmArrayPanel<FlexComponent> rarityRow = row("buyRarityRow");
		rarityRow.addComponent(new WurmLabel("Min rarity:"));
		rarityRow.addComponent(minRarityDrop);
		formPanel.addComponent(rarityRow);

		materialRow = row("buyMaterialRow");
		materialRow.addComponent(new WurmLabel("Material:"));
		rebuildMaterialDropDown(new String[] { "Any material" });
		formPanel.addComponent(materialRow);

		excludeEnchantedBox = new WCheckBox("Do not accept enchanted / runed");
		formPanel.addComponent(excludeEnchantedBox);

		allowPartialBox = new WCheckBox("Allow buying less than one full item");
		WurmArrayPanel<FlexComponent> partialRow = row("buyPartialRow");
		partialRow.addComponent(allowPartialBox);
		allowPartialWrap = new Collapsible("buyPartialWrap", partialRow);
		allowPartialWrap.setVisible(false);
		formPanel.addComponent(allowPartialWrap.component());

		formPanel.addComponent(spacer());

		escrowLabel = new WurmLabel("Money reserved: -");
		formPanel.addComponent(escrowLabel);

		buyPricing = new ValueLineBox("buyPricingBox", "- Pricing -");
		buyPricing.appendTo(formPanel);
		AuctionClient.setQuoteListener(this::renderQuote);

		// ---- pick phase ----
		pickerSearch = new KitInputField("buyPickerSearch").onSubmit(v -> AuctionClient.sendQueryItems(v.trim()));
		pickerFindButton = new WButton("Find", this);
		WurmArrayPanel<FlexComponent> pickerSearchRow = row("buyPickerSearchRow");
		pickerSearchRow.addComponent(new WurmLabel("Item name:"));
		pickerSearchRow.addComponent(pickerSearch.field());
		pickerSearchRow.addComponent(pickerFindButton);
		pickerTree = new WurmTreeList<>("buyPicker", new int[] { 160 }, new String[] { "Category" });
		pickerTree.setKeepAutoSorted(true);
		pickerTree.setSize(520, 320);
		pickerPanel = new WurmArrayPanel<>("buyPickerPanel", V);
		pickerPanel.addComponent(pickerSearchRow);
		pickerPanel.addComponent(pickerTree);

		// ---- buttons (SOUTH) ----
		placeButton = new WButton("Place Buy Order", this);
		cancelButton = new WButton("Cancel", this);
		buttonsRow = row("buyButtons");
		root.setComponent(buttonsRow, SOUTH);

		recalc();
	}

	// A horizontal row with a small gap between components.
	private static WurmArrayPanel<FlexComponent> row(String name) {
		WurmArrayPanel<FlexComponent> p = new WurmArrayPanel<>(name, H);
		p.componentWidthOffset = 6;
		return p;
	}

	private static FlexComponent spacer() {
		return new WurmArrayPanel<>("sp", H, 1, 6);
	}

	private void setChosen(int templateId, String name, int unitWeightGrams, int imageNumber) {
		chosenTemplateId = templateId;
		chosenName = name == null ? ("#" + templateId) : name;
		chosenUnitWeightGrams = unitWeightGrams;
		chosenImageNumber = imageNumber;
		chosenSplittable = false; // assume whole-piece until the fresh quote says otherwise
		rebuildHeader();
		if (amountWarningWrap != null) {
			amountWarningWrap.setVisible(false);
		}
		setBuyPartialVisible(false); // hide until the fresh quote says whether the new item is combinable
		// Fresh item: restore consistent defaults so the incoming quote can auto-fill the per-unit price.
		if (amountTabs != null) {
			amountTabs.selectSilently(0);
			amountTabs.setValue(0, "1");
		}
		if (priceTabs != null) {
			priceTabs.selectSilently(0);
			priceTabs.setValue(0, "0");
		}
		quotedTemplate = -1; // force a fresh quote for the new selection
		clearPricingBox();
		rebuildMaterialDropDown(new String[] { "Any material" });
		if (templateId > 0) {
			AuctionClient.sendQueryItemMaterials(templateId);
		}
	}

	/** Server replied with the chosen item's materials - rebuild the dropdown ("Any material" + each material). */
	private void rebuildMaterials() {
		if (AuctionClient.getItemMaterialsTemplateId() != chosenTemplateId) {
			return; // a stale reply for a previously-selected item
		}
		List<AuctionClient.MaterialOption> mats = AuctionClient.getItemMaterials();
		String[] labels = new String[mats.size() + 1];
		labels[0] = "Any material";
		for (int i = 0; i < mats.size(); i++) {
			labels[i + 1] = mats.get(i).name;
		}
		rebuildMaterialDropDown(labels);
	}

	private void rebuildMaterialDropDown(String[] labels) {
		if (materialRow == null) {
			return;
		}
		if (materialDrop != null) {
			materialRow.removeComponent(materialDrop);
		}
		materialBytes.clear();
		materialBytes.add((byte) 0); // index 0 = Any
		List<AuctionClient.MaterialOption> mats = AuctionClient.getItemMaterials();
		if (labels.length > 1 && AuctionClient.getItemMaterialsTemplateId() == chosenTemplateId) {
			for (AuctionClient.MaterialOption m : mats) {
				materialBytes.add(m.material);
			}
		}
		materialDrop = new KitDropDown("buyMaterial", 0, labels);
		materialRow.addComponent(materialDrop);
	}

	/** The material byte the buyer selected (0 = any). */
	private byte selectedMaterial() {
		if (materialDrop == null) {
			return 0;
		}
		int idx = materialDrop.selected();
		return idx >= 0 && idx < materialBytes.size() ? materialBytes.get(idx) : 0;
	}

	/** Show/hide the "allow partial" opt-in (position-stable via Collapsible); cleared when hidden. */
	private void setBuyPartialVisible(boolean visible) {
		if (allowPartialWrap == null || allowPartialBox == null) {
			return;
		}
		if (!visible) {
			allowPartialBox.checked = false;
		}
		allowPartialWrap.setVisible(visible);
	}

	/** The order-phase header: [icon name] [Change item]. */
	private void rebuildHeader() {
		if (headerRow == null) {
			return;
		}
		headerRow.removeAllComponents();
		if (chosenTemplateId > 0) {
			// The icon draws the name itself (one component, one shared baseline) so they always align - a separate
			// icon + WurmLabel never lines up because each anchors its content differently.
			chosenIcon.setImageNumber(chosenImageNumber);
			chosenIcon.setHover(chosenName);
			chosenIcon.setLabel(chosenName);
			headerRow.addComponent(chosenIcon);
		}
		headerRow.addComponent(changeItemButton);
	}

	private boolean amountInKg() {
		return amountTabs != null && amountTabs.selected() == 1;
	}

	private boolean priceIsBudget() {
		return priceTabs != null && priceTabs.selected() == 1;
	}

	/** The entered amount as a decimal (kg can be fractional, e.g. "5.5"); &lt;= 0 becomes 1. */
	private double amountValue() {
		Double d = amountTabs == null ? null : amountTabs.asDoubleOrNull();
		return d != null && d > 0.0 ? d : 1.0;
	}

	/** The whole-piece count the order covers: the entered units, or kg converted via the item's unit weight. */
	private long pieces() {
		if (amountInKg() && chosenUnitWeightGrams > 0) {
			return Math.max(1L, Math.round(amountValue() * 1000.0 / chosenUnitWeightGrams));
		}
		return Math.max(1L, (long) amountValue());
	}

	private long perUnitIron() {
		long entered = priceTabs == null ? 0L : AuctionFormat.parseCoin(priceTabs.value(), 0L);
		if (entered <= 0L) {
			return 0L;
		}
		long units = pieces();
		return priceIsBudget() && units > 0 ? entered / units : entered;
	}

	private void recalc() {
		if (escrowLabel == null) {
			return;
		}
		updateAmountWarning();
		long per = perUnitIron();
		if (per <= 0L || (amountInKg() && chosenUnitWeightGrams <= 0) || wholePieceViolation() != null) {
			escrowLabel.setLabel("Money reserved: -");
			return;
		}
		escrowLabel.setLabel("Money reserved: " + AuctionFormat.coin(per * pieces()));
	}

	// A non-combinable (whole-piece) item can't be bought by an arbitrary weight, so a kg amount that isn't a whole
	// multiple of the unit weight is invalid. Returns a message naming the nearest valid kg values, or null if OK.
	private String wholePieceViolation() {
		if (!amountInKg() || chosenSplittable || chosenUnitWeightGrams <= 0) {
			return null;
		}
		long grams = Math.round(amountValue() * 1000.0);
		long unit = chosenUnitWeightGrams;
		if (grams <= 0L || grams % unit == 0L) {
			return null;
		}
		// You can't buy fewer than one whole piece, so both suggestions are clamped to >= 1 (never "0 kg"). For a
		// sub-one amount (0.1 kg) floor and ceil collapse to a single "1 kg" suggestion - no spurious upper value.
		long floorPieces = Math.max(1L, grams / unit);
		long ceilPieces = Math.max(1L, (grams + unit - 1L) / unit);
		String head = chosenName + " sells only in whole pieces - try ";
		if (floorPieces == ceilPieces) {
			return head + trimKg(floorPieces * unit) + " kg (" + floorPieces + ").";
		}
		return head + trimKg(floorPieces * unit) + " kg (" + floorPieces + ") or "
			+ trimKg(ceilPieces * unit) + " kg (" + ceilPieces + ").";
	}

	private void updateAmountWarning() {
		if (amountWarningWrap == null) {
			return;
		}
		String msg = wholePieceViolation();
		if (msg != null) {
			amountWarning.setLabel(msg);
		}
		amountWarningWrap.setVisible(msg != null);
	}

	/** grams -> compact kg string ("5", "5.5"), no trailing ".0". */
	private static String trimKg(long grams) {
		double kg = grams / 1000.0;
		return kg == Math.floor(kg) ? Long.toString((long) kg) : String.format("%.2f", kg).replaceAll("0+$", "").replaceAll("\\.$", "");
	}

	private void refreshPicker() {
		if (pickerTree == null) {
			return;
		}
		pickerTree.clearSelections();
		pickerTree.clear();
		for (AuctionClient.ItemMatch m : AuctionClient.getItemMatches()) {
			AuctionRow row = new AuctionRow(AuctionRow.Kind.ITEM, m.templateId, m.name);
			row.imageNumber = m.imageNumber;
			row.aux = AuctionCategory.pathOf(m.categoryId);
			row.weight = m.unitWeightGrams;
			pickerTree.addTreeListItem(row, null);
		}
		pickerTree.recalcLines();
	}

	/** Pick phase: the catalogue fills the window; only Cancel in the footer. */
	private void showPicker() {
		pickerVisible = true;
		root.setComponent(pickerPanel, CENTER);
		buttonsRow.removeAllComponents();
		buttonsRow.addComponent(cancelButton);
		AuctionClient.sendQueryItems(pickerSearch == null ? "" : pickerSearch.getValue().trim());
	}

	/** Order phase: the form fills the window; Place + Cancel in the footer. */
	private void showForm() {
		pickerVisible = false;
		if (pickerTree != null) {
			pickerTree.clearSelections();
		}
		root.setComponent(formPanel, CENTER);
		buttonsRow.removeAllComponents();
		buttonsRow.addComponent(placeButton);
		buttonsRow.addComponent(cancelButton);
		// Re-assert the disabled-field tint now the form is laid out (it doesn't render from construction alone).
		amountTabs.reapplyEnabled();
		priceTabs.reapplyEnabled();
	}

	// Accept-and-advance: picking a result row sets the item and switches to the order phase.
	private void pollPickerSelection() {
		if (pickerTree == null || !pickerVisible) {
			return;
		}
		List<AuctionRow> sel = pickerTree.getSelections();
		if (sel == null || sel.isEmpty()) {
			return;
		}
		AuctionRow row = sel.get(0);
		setChosen(row.templateId, row.displayName, (int) row.weight, row.imageNumber);
		recalc();
		showForm();
	}

	@Override
	public void gameTick() {
		super.gameTick();
		pollPickerSelection();
		maybeRequestQuote();
		// The field's disabled tint (r/g/b) is reset to default by re-layout (which a tab switch triggers via the
		// warning collapsible / escrow label), so a one-shot re-assert loses it again. Re-assert every frame - it is
		// idempotent and gameTick runs before render, so the greyed inactive field is correct on every drawn frame.
		if (!pickerVisible) {
			amountTabs.reapplyEnabled();
			priceTabs.reapplyEnabled();
		}
	}

	/** Ask the server for pricing context whenever the chosen item / material / min-QL changes. */
	private void maybeRequestQuote() {
		if (chosenTemplateId <= 0) {
			return;
		}
		byte material = selectedMaterial();
		int minQl = (int) (minQlField == null ? 0L : minQlField.asLong(0L));
		if (chosenTemplateId == quotedTemplate && material == quotedMaterial && minQl == quotedMinQl) {
			return;
		}
		quotedTemplate = chosenTemplateId;
		quotedMaterial = material;
		quotedMinQl = minQl;
		AuctionClient.sendQueryQuote(chosenTemplateId, material, minQl);
	}

	private void clearPricingBox() {
		if (buyPricing != null) {
			buyPricing.clear();
		}
	}

	/** Render the latest QUOTE for the chosen item: what the trader sells it for (if stocked) + market spread. */
	private void renderQuote() {
		if (buyPricing == null) {
			return;
		}
		AuctionClient.Quote q = AuctionClient.getLastQuote();
		if (q == null || q.templateId != chosenTemplateId) {
			return;
		}
		buyPricing.set(
			q.traderStock > 0
				? "Trader sells at " + AuctionFormat.coin(q.traderAsk) + " each (" + q.traderStock + " in stock)."
				: "",
			q.marketBestAsk > 0
				? "Other sellers ask from " + AuctionFormat.coin(q.marketBestAsk) + " each."
				: "",
			q.marketBestBid > 0
				? "Other buyers bid up to " + AuctionFormat.coin(q.marketBestBid) + " each."
				: "");

		// kg / by-weight only makes sense for bulk goods: grey out the kg tab for non-bulkable items.
		amountTabs.setTabEnabled(1, q.bulkable);
		if (!q.bulkable && amountInKg()) {
			amountTabs.selectSilently(0);
			recalc();
		}
		// "allow partial" opt-in only for combinable goods (mirrors the Sell tab).
		chosenSplittable = q.splittable;
		setBuyPartialVisible(q.splittable);
		updateAmountWarning();

		// Pre-fill the per-unit max with the lowest current ask (player sellers or trader) - the value most buyers
		// want. Only while still on the Per-unit tab with nothing typed, so it never clobbers a chosen tab or entry.
		long lowestAsk = 0L;
		if (q.marketBestAsk > 0L) {
			lowestAsk = q.marketBestAsk;
		}
		if (q.traderStock > 0 && q.traderAsk > 0L && (lowestAsk == 0L || q.traderAsk < lowestAsk)) {
			lowestAsk = q.traderAsk;
		}
		if (lowestAsk > 0L && priceTabs.selected() == 0 && isBlankOrZero(priceTabs.value())) {
			// Show it in readable coin notation ("44c"); the price parser round-trips it back to iron.
			priceTabs.setValue(0, AuctionFormat.coin(lowestAsk));
			recalc();
		}
	}

	private static boolean isBlankOrZero(String s) {
		if (s == null) {
			return true;
		}
		String t = s.trim();
		return t.isEmpty() || "0".equals(t);
	}

	@Override
	public void buttonPressed(WButton button) {
	}

	@Override
	public void buttonClicked(WButton button) {
		if (button == changeItemButton) {
			showPicker();
		} else if (button == pickerFindButton) {
			AuctionClient.sendQueryItems(pickerSearch.getValue().trim());
		} else if (button == placeButton) {
			doPlace();
		} else if (button == cancelButton) {
			closePressed();
		}
	}

	private void doPlace() {
		if (chosenTemplateId <= 0) {
			escrowLabel.setLabel("Choose an item first (Change item).");
			return;
		}
		long per = perUnitIron();
		if (per <= 0L) {
			escrowLabel.setLabel("Enter a max price.");
			return;
		}
		// The client knows the item's unit weight, so a "by weight" order is converted to an exact PIECE count here
		// and always sent as a pieces order (keeps the server piece-based; shown escrow == real hold).
		long units = pieces();
		if (amountInKg() && amountValue() * 1000.0 < chosenUnitWeightGrams) {
			escrowLabel.setLabel("That is less than one " + chosenName + " - increase the weight.");
			return;
		}
		// Non-combinable item entered as a fractional kg amount: block and show the nearest valid weights.
		String violation = wholePieceViolation();
		if (violation != null) {
			updateAmountWarning();
			return;
		}
		int minQl = (int) (minQlField == null ? 0L : minQlField.asLong(0L));
		byte minRarity = (byte) (minRarityDrop == null ? 0 : minRarityDrop.selected());
		boolean excludeEnch = excludeEnchantedBox != null && excludeEnchantedBox.checked;
		boolean allowPartial = allowPartialBox != null && allowPartialBox.checked;
		AuctionClient.sendPostBuy(chosenTemplateId, per, minQl, minRarity, 0f, AuctionProtocol.UNIT_PIECES, units, excludeEnch, selectedMaterial(), allowPartial);
		closePressed();
	}
}
