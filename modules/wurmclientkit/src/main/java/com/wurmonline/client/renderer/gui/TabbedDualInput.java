package com.wurmonline.client.renderer.gui;

import java.util.function.BiConsumer;

/**
 * Two mutually-exclusive tab buttons, each paired with its own input field; only the selected tab's field is
 * editable (the other is greyed/disabled). The selected tab is drawn in gold, an inactive-but-usable tab in white,
 * and a disabled tab in dim grey (so "inactive" never reads as "unavailable"). Distinguishing tabs by text colour —
 * never {@code setDown()}, whose pressed art is dark and reads as disabled.
 *
 * <p>Two arrangements:
 * <ul>
 *   <li>{@link #LINE}: a single horizontal row {@code [tabA][fieldA] [tabB][fieldB]} (compact; give tabs/fields a
 *       fixed width via {@link #sizes(int, int)}).</li>
 *   <li>{@link #GRID}: two columns, each tab button OVER its field, separated by a gap (like the Sell price tabs).</li>
 * </ul>
 *
 * <p>Lives in {@code com.wurmonline.client.renderer.gui} to construct the package-private {@link WButton} /
 * {@link WurmArrayPanel} and implement {@link ButtonListener}.
 */
public final class TabbedDualInput implements ButtonListener {

	public static final int LINE = 0;
	public static final int GRID = 1;

	private static final float[] ACTIVE = { 1.0f, 0.82f, 0.4f };   // gold
	private static final float[] INACTIVE = { 1.0f, 1.0f, 1.0f };  // white
	private static final float[] DISABLED = { 0.45f, 0.45f, 0.45f }; // dim grey

	private final WurmArrayPanel<FlexComponent> bar;
	private final WButton tabA;
	private final WButton tabB;
	private final KitInputField fieldA;
	private final KitInputField fieldB;
	private final boolean[] tabEnabled = { true, true };
	private int selected;
	private BiConsumer<Integer, String> onChange;

	public TabbedDualInput(String name, int arrangement, String labelA, String labelB) {
		this(name, arrangement, null, labelA, labelB);
	}

	public TabbedDualInput(String name, int arrangement, String leadingLabel, String labelA, String labelB) {
		tabA = new WButton(labelA, this);
		tabB = new WButton(labelB, this);
		fieldA = new KitInputField(name + "A").onChange(v -> fire());
		fieldB = new KitInputField(name + "B").onChange(v -> fire());

		bar = new WurmArrayPanel<>(name, WurmArrayPanel.DIR_HORIZONTAL);
		bar.componentWidthOffset = 6;
		if (leadingLabel != null) {
			bar.addComponent(new WurmLabel(leadingLabel));
		}
		if (arrangement == LINE) {
			bar.addComponent(tabA);
			bar.addComponent(fieldA.field());
			bar.addComponent(tabB);
			bar.addComponent(fieldB.field());
		} else {
			bar.addComponent(column(name + "ColA", tabA, fieldA));
			bar.addComponent(column(name + "ColB", tabB, fieldB));
		}
		selectSilently(0);
	}

	private static WurmArrayPanel<FlexComponent> column(String name, WButton tab, KitInputField field) {
		WurmArrayPanel<FlexComponent> col = new WurmArrayPanel<>(name, WurmArrayPanel.DIR_VERTICAL, true);
		col.addComponent(tab);
		col.addComponent(field.field());
		return col;
	}

	/** The panel to add to a parent slot. */
	public WurmArrayPanel<FlexComponent> panel() {
		return bar;
	}

	/** Fixed widths for the tab buttons and input fields (LINE arrangement; the field width also applies in GRID). */
	public TabbedDualInput sizes(int tabWidth, int fieldWidth) {
		tabA.setSize(tabWidth, tabA.height);
		tabB.setSize(tabWidth, tabB.height);
		fieldA.field().setSize(fieldWidth, fieldA.field().height);
		fieldB.field().setSize(fieldWidth, fieldB.field().height);
		return this;
	}

	/** Fired whenever the active value changes — by a field edit or a tab switch. Args: (selectedIndex, value). */
	public TabbedDualInput onChange(BiConsumer<Integer, String> callback) {
		onChange = callback;
		return this;
	}

	public int selected() {
		return selected;
	}

	public String value() {
		return (selected == 0 ? fieldA : fieldB).getValue();
	}

	public long asLong(long fallback) {
		return (selected == 0 ? fieldA : fieldB).asLong(fallback);
	}

	public Double asDoubleOrNull() {
		return (selected == 0 ? fieldA : fieldB).asDoubleOrNull();
	}

	public TabbedDualInput setValue(int index, String value) {
		(index == 0 ? fieldA : fieldB).setValue(value);
		return this;
	}

	public void select(int index) {
		doSelect(index, true);
	}

	public void selectSilently(int index) {
		doSelect(index, false);
	}

	/** Enable/disable a tab. Disabling the active tab falls back to the other (if enabled) so a usable tab is selected. */
	public TabbedDualInput setTabEnabled(int index, boolean enabled) {
		if (index < 0 || index > 1 || tabEnabled[index] == enabled) {
			return this;
		}
		tabEnabled[index] = enabled;
		if (!enabled && selected == index && tabEnabled[1 - index]) {
			doSelect(1 - index, false);
		} else {
			applyLook();
		}
		return this;
	}

	public boolean isTabEnabled(int index) {
		return index >= 0 && index <= 1 && tabEnabled[index];
	}

	private void doSelect(int index, boolean fire) {
		if (index < 0 || index > 1 || !tabEnabled[index]) {
			return;
		}
		selected = index;
		applyLook();
		fieldA.setEnabled(index == 0);
		fieldB.setEnabled(index == 1);
		if (fire) {
			fire();
		}
	}

	/**
	 * Re-assert the full look: the active/inactive tab text colours AND the greyed state of the inactive field. The
	 * field tint (r/g/b) and, potentially, the tab text colour are reset to default by re-layout, so a one-shot
	 * re-apply is lost when a later layout runs. Windows should call this every {@code gameTick} (idempotent, cheap)
	 * so the styling is correct on every rendered frame, not only right after a tab switch.
	 */
	public void reapplyEnabled() {
		applyLook();
		fieldA.setEnabled(selected == 0);
		fieldB.setEnabled(selected == 1);
	}

	private void applyLook() {
		style(tabA, 0);
		style(tabB, 1);
	}

	private void style(WButton button, int index) {
		float[] c = !tabEnabled[index] ? DISABLED : (index == selected ? ACTIVE : INACTIVE);
		button.setTextColor(c[0], c[1], c[2]);
	}

	private void fire() {
		if (onChange != null) {
			onChange.accept(selected, value());
		}
	}

	@Override
	public void buttonPressed(WButton button) {
	}

	@Override
	public void buttonClicked(WButton button) {
		if (button == tabA) {
			select(0);
		} else if (button == tabB) {
			select(1);
		}
	}
}
