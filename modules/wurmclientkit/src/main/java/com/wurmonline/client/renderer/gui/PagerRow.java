package com.wurmonline.client.renderer.gui;

import java.util.function.IntConsumer;

/**
 * A centered pager: {@code |<  <  Page X of Y  >  >|}, bound to a (page, totalPages) model. Clicking a
 * button clamps to range and fires {@link #onPage} with the new 0-based page; the buttons disable at
 * the ends. Generalises AuctionHouse's hand-built pager row.
 *
 * <p>Unidirectional flow: a click fires {@link #onPage} (you request that page from the server); when
 * the data arrives you call {@link #setState(int, int)} to resync the label and buttons — which does
 * <b>not</b> re-fire {@link #onPage}, so there's no request loop.
 *
 * <p><b>Contract:</b> the widget advances its page <i>optimistically</i> on click (so rapid clicks
 * step correctly), then relies on {@link #setState} to confirm or correct it. Call {@code setState} on
 * <b>every</b> response — success, empty, or failure — or the widget can stay stuck on the optimistic
 * page, out of sync with the data actually shown. During an in-flight request {@link #page()} is the
 * requested page, which may differ from what's displayed until {@code setState} lands.
 *
 * <p>Lives in {@code com.wurmonline.client.renderer.gui} for the package-private widgets.
 *
 * <pre>{@code
 * PagerRow pager = new PagerRow("pager").onPage(p -> requestBrowse(p));
 * bottom.setComponent(pager.component(), WurmBorderPanel.NORTH);  // border slot → centers
 * // when a browse response arrives:
 * pager.setState(response.page, response.totalPages);
 * }</pre>
 */
public final class PagerRow implements ButtonListener {

	private final WurmDecorator root;
	private final WButton first;
	private final WButton prev;
	private final WButton next;
	private final WButton last;
	private final WurmLabel label;

	private int page;
	private int totalPages = 1;
	private IntConsumer onPage;

	public PagerRow(String name) {
		first = new WButton("|<", this);
		prev = new WButton("<", this);
		next = new WButton(">", this);
		last = new WButton(">|", this);
		// Reserve width for a 4-digit page count so the buttons don't shift as the label text changes
		// (WurmLabel.setLabel never resizes — width is frozen at construction).
		label = new WurmLabel("Page 0000 of 0000");

		WurmArrayPanel<FlexComponent> bar = new WurmArrayPanel<>(name, WurmArrayPanel.DIR_HORIZONTAL);
		bar.addComponent(new WurmPanel(8, first.height, false));
		bar.addComponent(first);
		bar.addComponent(prev);
		bar.addComponent(label);
		bar.addComponent(next);
		bar.addComponent(last);
		bar.addComponent(new WurmPanel(8, first.height, false));

		root = new WurmDecorator(bar);
		root.align = WurmDecorator.ALIGN_CENTER;
		root.pack();
		update();
	}

	/** The centered component — place it in a border-panel slot (which stretches so it can center). */
	public FlexComponent component() {
		return root;
	}

	/** Fired with the new 0-based page when a pager button changes the page. */
	public PagerRow onPage(IntConsumer callback) {
		onPage = callback;
		return this;
	}

	public int page() {
		return page;
	}

	public int totalPages() {
		return totalPages;
	}

	/**
	 * Sync the pager to a (page, totalPages) state: refresh the label and button enablement. Clamps the
	 * page into range. Does NOT fire {@link #onPage} — call it from your data-arrived handler.
	 */
	public PagerRow setState(int page, int totalPages) {
		this.totalPages = Math.max(1, totalPages);
		this.page = clamp(page);
		update();
		return this;
	}

	private int clamp(int p) {
		if (p < 0) {
			return 0;
		}
		return p > totalPages - 1 ? totalPages - 1 : p;
	}

	private void update() {
		label.setLabel("Page " + (page + 1) + " of " + totalPages);
		boolean canBack = page > 0;
		boolean canFwd = page < totalPages - 1;
		first.setEnabled(canBack);
		prev.setEnabled(canBack);
		next.setEnabled(canFwd);
		last.setEnabled(canFwd);
	}

	private void go(int target) {
		int clamped = clamp(target);
		if (clamped != page) {
			page = clamped;
			update();
			if (onPage != null) {
				onPage.accept(page);
			}
		}
	}

	@Override
	public void buttonPressed(WButton button) {
	}

	@Override
	public void buttonClicked(WButton button) {
		if (button == first) {
			go(0);
		} else if (button == prev) {
			go(page - 1);
		} else if (button == next) {
			go(page + 1);
		} else if (button == last) {
			go(totalPages - 1);
		}
	}
}
