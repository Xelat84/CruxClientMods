package com.wurmonline.clientkit;

import org.gotti.wurmunlimited.modloader.classhooks.HookException;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;

import javassist.CannotCompileException;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

/**
 * Boilerplate helpers for javassist class-hooking in {@code preInit()}. Every client mod repeats the
 * same preamble — grab the {@link HookManager} class pool, {@code get} a class (turning the checked
 * {@link NotFoundException} into a {@link HookException}), append its {@link ClassClassPath}, and wrap
 * edits in the {@code catch (NotFoundException | CannotCompileException) -> HookException} idiom. This
 * collapses all of that. Pure plumbing, no client dependency.
 *
 * <pre>{@code
 * CtClass c = Hooks.get("com.wurmonline.client.renderer.gui.SomeClass");
 * CtMethod m = Hooks.method(c, "doThing");
 * Hooks.edit(() -> m.insertBefore("{ ... }"));
 * Hooks.replaceCall(m, "getWidth", "$_ = $proceed($$) * 3 / 2;");
 * }</pre>
 */
public final class Hooks {

	private Hooks() {
	}

	/** A javassist edit that may throw the two checked exceptions {@link #edit} converts. */
	@FunctionalInterface
	public interface HookAction {
		void run() throws NotFoundException, CannotCompileException;
	}

	/** The shared HookManager class pool. */
	public static ClassPool pool() {
		return HookManager.getInstance().getClassPool();
	}

	/** {@code pool().get(name)}, converting {@link NotFoundException} to {@link HookException}. */
	public static CtClass get(String className) {
		try {
			return pool().get(className);
		} catch (NotFoundException e) {
			throw new HookException(e);
		}
	}

	/** {@code ctClass.getDeclaredMethod(name)}, converting {@link NotFoundException} to {@link HookException}. */
	public static CtMethod method(CtClass ctClass, String name) {
		try {
			return ctClass.getDeclaredMethod(name);
		} catch (NotFoundException e) {
			throw new HookException(e);
		}
	}

	/**
	 * Put {@code anchor}'s jar bytecode on the pool so the HookManager loader can define it. Required in
	 * every mod's {@code preInit} that patches a vanilla method referencing mod code (fixes
	 * {@code NoClassDefFoundError}), and it also loads gui-package classes on the same loader as
	 * {@code WWindow} (see {@code docs/concepts/package-private-constraint.md}).
	 */
	public static void appendClassPath(Class<?> anchor) {
		pool().appendClassPath(new ClassClassPath(anchor));
	}

	/** Run javassist edits, converting checked {@link NotFoundException}/{@link CannotCompileException} to {@link HookException}. */
	public static void edit(HookAction action) {
		try {
			action.run();
		} catch (NotFoundException | CannotCompileException e) {
			throw new HookException(e);
		}
	}

	/**
	 * Rewrite every call to {@code targetMethod} inside {@code method} with the javassist source
	 * {@code replacement} (which may use {@code $_}, {@code $proceed($$)}, {@code $0}, {@code $1}…).
	 */
	public static void replaceCall(CtBehavior method, String targetMethod, String replacement) {
		// No lambda here on purpose: Hooks may be referenced from injected code, and the client's
		// javassist 3.12.1 cannot parse the invokedynamic that a lambda compiles to. Anonymous class + a
		// direct try/catch keeps Hooks.class lambda-free (see docs/specs/private-class-techniques.md).
		try {
			method.instrument(new ExprEditor() {
				@Override
				public void edit(MethodCall call) throws CannotCompileException {
					if (call.getMethodName().equals(targetMethod)) {
						call.replace(replacement);
					}
				}
			});
		} catch (CannotCompileException e) {
			throw new HookException(e);
		}
	}
}
