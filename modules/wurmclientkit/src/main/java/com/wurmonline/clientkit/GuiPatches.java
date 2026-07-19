package com.wurmonline.clientkit;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.gotti.wurmunlimited.modloader.classhooks.HookException;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;

import javassist.CannotCompileException;
import javassist.ClassClassPath;
import javassist.CtClass;
import javassist.NotFoundException;

/**
 * Optional javassist patches that harden vanilla GUI widgets for SDK use. Each installer is idempotent
 * and must be called from a mod's {@code preInit()} (before the target class is loaded). These are
 * opt-in: the SDK components work without them, but installing hardens edge cases the components can't
 * reach by composition (their target classes are {@code final} with {@code protected} internals).
 *
 * <p><b>Why this class deliberately does NOT use {@link Hooks#edit} / lambdas:</b> the cell-button
 * patch's injected bytecode references {@link #onCellButtonError}, which forces the client's javassist
 * (3.12.1) to parse this class at patch-compile time. javassist 3.12.1 cannot read the
 * {@code invokedynamic} constant-pool entries that Java-8 lambdas compile to ("invalid constant type:
 * 18"), so any class referenced from injected code must stay lambda-free. Hence the explicit
 * {@code try/catch} here instead of {@code Hooks.edit(() -> ...)}.
 *
 * @see com.wurmonline.client.renderer.gui.KitInputField
 */
public final class GuiPatches {

	private static final Logger LOGGER = Logger.getLogger(GuiPatches.class.getName());

	private static volatile boolean inputFieldFixInstalled;
	private static volatile boolean treeCellButtonsInstalled;
	private static volatile boolean disabledInputFocusFixInstalled;

	/**
	 * The javassist {@code insertBefore} body for {@code HeadsUpDisplay.startTyping}. A vanilla
	 * {@code WurmInputField} with {@code enabled == false} blocks typing but is still focusable — a click
	 * places a blinking caret in it, which reads as "editable" even though keystrokes do nothing. This
	 * returns early when the keyboard-focus target is a disabled input field, so it never receives focus
	 * (no caret). Focus assignment already ran {@code stopTyping} on the previously-focused field, so the
	 * net effect matches clicking a non-input: the disabled field stays inert and uncaretted.
	 */
	public static final String DISABLED_INPUT_FOCUS_FIX_SRC =
		"{ if (this.kbFocusComponent instanceof com.wurmonline.client.renderer.gui.WurmInputField"
		+ "     && !((com.wurmonline.client.renderer.gui.WurmInputField) this.kbFocusComponent).enabled) return; }";

	/**
	 * The javassist {@code insertBefore} body for {@code WurmInputField.keyPressed}. Neutralises ONLY the
	 * Down-arrow (keyCode 208) branch on a single-line {@code simpleInput} field — where vanilla clears
	 * the text unconditionally — by returning early. Deliberately narrow: it does not touch Backspace
	 * (case 14), Delete (case 211), typing, or multi-line cursor movement, all of which also assign
	 * {@code input} (an over-broad "block empty writes" edit would break last-character deletion). Exposed
	 * so an offline harness can verify it compiles against the real client (see {@code examples/verify/}).
	 */
	public static final String INPUT_FIELD_DOWN_FIX_SRC =
		"{ if ($1 == 208 && $0.simpleInput && $0.maxLines == 1) return; }";

	/** The javassist {@code insertBefore} body for {@code WurmTreeList$TreeListPanel.leftPressed}. */
	public static final String TREE_CELL_BUTTON_SRC =
		"{ com.wurmonline.client.renderer.gui.WTreeListNode __ln = this.getNodeAt($1, $2);"
		+ "  if (__ln != null && __ln.item instanceof com.wurmonline.client.renderer.gui.KitTreeItem) {"
		+ "    com.wurmonline.client.renderer.gui.KitTreeItem __kit ="
		+ "        (com.wurmonline.client.renderer.gui.KitTreeItem) __ln.item;"
		+ "    int[] __cw = this.this$0.columnWidths;"
		+ "    int __fixed = this.this$0.commonWidth + (this.this$0.hasImpColumn ? 16 : 0);"
		+ "    int __base = this.x + this.width - 4 - __fixed;"
		+ "    for (int __i = 0; __i < __cw.length; __i++) {"
		+ "      if (__kit.isCellButton(__i) && $1 >= __base && $1 < __base + __cw[__i]) {"
		+ "        if ($3 < 2) {"
		+ "          try { __kit.cellButtonClicked(__i); }"
		+ "          catch (java.lang.Throwable __t) {"
		+ "            com.wurmonline.clientkit.GuiPatches.onCellButtonError(__t); }"
		+ "        }"
		+ "        return;"
		+ "      }"
		+ "      __base += __cw[__i];"
		+ "    }"
		+ "  } }";

	/**
	 * The javassist {@code insertAfter} body for {@code WurmTreeList$TreeListPanel.renderComponent}: draws
	 * a real button skin (the vanilla {@code WButton} 3-slice: 8px {@code panelTexture} caps +
	 * {@code panelTextureTilingH} middle) behind each cell-button column's label, so a
	 * {@code KitTreeItem.isCellButton(col)} cell renders as a button rather than plain accent text. Runs
	 * after vanilla has drawn the row, over its own component scissor clip. {@code lineHeight} is
	 * {@code private} on the outer class, so it's recomputed as {@code text.getHeight() + 1}; geometry
	 * uses the same render base as the click patch.
	 */
	public static final String TREE_CELL_BUTTON_RENDER_SRC =
		"{ int __lh = this.text.getHeight() + 1;"
		+ "  int[] __cw = this.this$0.columnWidths;"
		+ "  int __fixed = this.this$0.commonWidth + (this.this$0.hasImpColumn ? 16 : 0);"
		+ "  int __padX = 3;"                              // horizontal margin inside the cell
		+ "  int __bh = __lh - 4; if (__bh < 8) __bh = __lh - 2;"   // button height, ~2px vertical margin each side
		+ "  java.util.List __rows = this.this$0.lines;"
		+ "  for (int __yy = this.start; __yy < this.end && __yy < __rows.size(); __yy++) {"
		+ "    com.wurmonline.client.renderer.gui.WTreeListNode __n ="
		+ "        (com.wurmonline.client.renderer.gui.WTreeListNode) __rows.get(__yy);"
		+ "    if (__n.item instanceof com.wurmonline.client.renderer.gui.KitTreeItem) {"
		+ "      com.wurmonline.client.renderer.gui.KitTreeItem __kit ="
		+ "          (com.wurmonline.client.renderer.gui.KitTreeItem) __n.item;"
		+ "      int __colX = this.x + this.width - 4 - __fixed;"
		+ "      int __by = this.y + __yy * __lh + (__lh - __bh) / 2;"
		+ "      for (int __i = 0; __i < __cw.length; __i++) {"
		+ "        int __w = __cw[__i];"
		+ "        if (__kit.isCellButton(__i) && __w > 2 * __padX + 16) {"
		+ "          int __bx = __colX + __padX;"
		+ "          int __bw = __w - 2 * __padX;"
		+ "          this.drawTexture($1, panelTexture, 1.0F, 1.0F, 1.0F, 1.0F, __bx, __by, 8, __bh, 64, 16, 8, 16);"
		+ "          this.drawTexture($1, panelTexture, 1.0F, 1.0F, 1.0F, 1.0F, __bx + __bw - 8, __by, 8, __bh, 88, 16, 8, 16);"
		+ "          this.drawTexTilingH($1, panelTextureTilingH, 1.0F, 1.0F, 1.0F, 1.0F, __bx + 8, __by, __bw - 16, __bh, 61, 16);"
		+ "          String __lbl = __kit.getParameter(__i);"
		+ "          this.text.moveTo(__bx + __bw / 2 - this.text.getWidth(__lbl) / 2, __by + __bh / 2 + this.text.getHeight() / 2);"
		+ "          this.text.paint($1, __lbl, 1.0F, 1.0F, 1.0F, 1.0F);"
		+ "        }"
		+ "        __colX += __w;"
		+ "      }"
		+ "    }"
		+ "  } }";

	private GuiPatches() {
	}

	/**
	 * Neutralise {@code WurmInputField}'s Down-arrow text-wipe on single-line {@code simpleInput} fields.
	 *
	 * <p>On a single-line field, Down (keyCode 208) navigates history, and with no history — always the
	 * case for a {@code simpleInput} form field, which never records history — it clears the text
	 * unconditionally, silently wiping a form value on one keystroke. This {@code insertBefore} returns
	 * early for exactly that case ({@code keyCode == 208 && simpleInput && maxLines == 1}), so Down
	 * becomes a no-op there and every other key (Backspace, Delete, typing, multi-line cursor movement)
	 * is untouched.
	 *
	 * <p>Note: {@code simpleInput} is NOT exclusive to {@code KitInputField} — vanilla
	 * {@code BmlWindowComponent} also sets it on server-driven BML form inputs. Making Down a no-op on
	 * those single-line form fields is equally correct (Down clearing a form field is never wanted), so
	 * the patch is safe for vanilla BML fields too; ordinary chat/console fields ({@code simpleInput ==
	 * false}) are entirely unaffected.
	 *
	 * <p>Idempotent; call once in {@code preInit()}.
	 */
	public static synchronized void installInputFieldFix() {
		if (inputFieldFixInstalled) {
			return;
		}
		try {
			CtClass field = HookManager.getInstance().getClassPool()
				.get("com.wurmonline.client.renderer.gui.WurmInputField");
			field.getDeclaredMethod("keyPressed").insertBefore(INPUT_FIELD_DOWN_FIX_SRC);
			inputFieldFixInstalled = true;
			LOGGER.info("WurmClientKit: installed WurmInputField Down-arrow no-op for single-line simpleInput fields.");
		} catch (NotFoundException | CannotCompileException e) {
			throw new HookException(e);
		}
	}

	/**
	 * Enable clickable buttons inside {@code WurmTreeList} cells for
	 * {@link com.wurmonline.client.renderer.gui.KitTreeItem} rows.
	 *
	 * <p>Vanilla has no per-cell button hook. This prepends a check to
	 * {@code WurmTreeList$TreeListPanel.leftPressed} that mirrors the built-in checkbox branch: it finds
	 * the clicked row, and for each extra column whose item reports {@code isCellButton(col)} and whose
	 * column slot contains the click x, it calls {@code cellButtonClicked(col)} and consumes the click
	 * (so the row is not selected). The geometry is computed with the vanilla render formula
	 * ({@code x + width - 4 - (commonWidth + imp16)}, then per-column {@code columnWidths}), so the click
	 * rect aligns with the drawn label. Non-{@code KitTreeItem} rows and non-button columns are
	 * untouched — vanilla behaviour is fully preserved. Fires once per click (gated on {@code clickCount})
	 * and catches handler exceptions via {@link #onCellButtonError}.
	 *
	 * <p>Idempotent; call once in {@code preInit()}.
	 */
	public static synchronized void installTreeCellButtons() {
		if (treeCellButtonsInstalled) {
			return;
		}
		try {
			// The injected patch references KitTreeItem and GuiPatches.onCellButtonError (both in this SDK jar).
			// javassist must find them on the pool to COMPILE the insertBefore, so put this jar on the pool first.
			// Anchor on GuiPatches.class (already loaded, same jar) - NOT KitTreeItem.class: a class literal for a
			// gui-package class forces it to be DEFINED now on the mod loader, but it extends the package-private
			// vanilla TreeListItem, so it must be defined by the HookManager loader (which owns the vanilla gui
			// classes) - eager definition here throws IllegalAccessError. Anchoring on the plain clientkit class
			// exposes the same jar's bytecode to javassist without loading any gui-package class.
			HookManager.getInstance().getClassPool()
				.appendClassPath(new ClassClassPath(GuiPatches.class));
			CtClass panel = HookManager.getInstance().getClassPool()
				.get("com.wurmonline.client.renderer.gui.WurmTreeList$TreeListPanel");
			panel.getDeclaredMethod("leftPressed").insertBefore(TREE_CELL_BUTTON_SRC);
			panel.getDeclaredMethod("renderComponent").insertAfter(TREE_CELL_BUTTON_RENDER_SRC);
			treeCellButtonsInstalled = true;
			LOGGER.info("WurmClientKit: installed WurmTreeList cell-button dispatch for KitTreeItem rows.");
		} catch (NotFoundException | CannotCompileException e) {
			throw new HookException(e);
		}
	}

	/**
	 * Make a disabled ({@code enabled == false}) {@code WurmInputField} non-focusable, so clicking it does
	 * not place a caret and it visibly reads as disabled (pair with a greyed look, e.g.
	 * {@link com.wurmonline.client.renderer.gui.KitInputField#setEnabled}). Patches
	 * {@code HeadsUpDisplay.startTyping} — see {@link #DISABLED_INPUT_FOCUS_FIX_SRC}.
	 *
	 * <p>Idempotent; call once in {@code preInit()}.
	 */
	public static synchronized void installDisabledInputFocusFix() {
		if (disabledInputFocusFixInstalled) {
			return;
		}
		try {
			CtClass hud = HookManager.getInstance().getClassPool()
				.get("com.wurmonline.client.renderer.gui.HeadsUpDisplay");
			hud.getDeclaredMethod("startTyping").insertBefore(DISABLED_INPUT_FOCUS_FIX_SRC);
			disabledInputFocusFixInstalled = true;
			LOGGER.info("WurmClientKit: installed disabled-input focus block (disabled WurmInputField cannot take a caret).");
		} catch (NotFoundException | CannotCompileException e) {
			throw new HookException(e);
		}
	}

	/** Called by the cell-button patch when a {@code cellButtonClicked} handler throws. */
	public static void onCellButtonError(Throwable t) {
		LOGGER.log(Level.WARNING, "WurmClientKit: a cell-button handler threw", t);
	}
}
