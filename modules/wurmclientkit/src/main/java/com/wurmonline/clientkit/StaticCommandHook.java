package com.wurmonline.clientkit;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.NotFoundException;

/**
 * Installs a <b>static-dispatch</b> custom server&#8594;client command hook. This is the correct pattern
 * for a command handler that <b>instantiates gui-package classes</b> (windows extending the
 * package-private {@code WWindow}) — use it instead of {@link ServerCommandMod} whenever {@code handle}
 * opens or touches a window.
 *
 * <h2>Why not {@link ServerCommandMod} for GUI handlers</h2>
 * A custom-command hook loads the handler class on <b>two different classloaders</b>: the modloader's
 * loader (when your {@code preInit} references it) and the HookManager loader (the copy the patched
 * vanilla {@code reallyHandle} actually calls). {@code ServerCommandMod} dispatches inbound packets
 * through the mod <i>instance</i>, which the modloader created on its own loader; a {@code handle} that
 * {@code new}s up a gui-package window then resolves that window on the wrong loader and throws
 * {@code IllegalAccessError} (package-private access is enforced per-classloader) — the window silently
 * never opens.
 *
 * <p>This helper instead patches {@code reallyHandle} to call {@code <HandlerClass>.handle(conn, buf)}
 * <b>by name</b> — a static reference embedded in the patched method, resolved on the HookManager
 * loader. That pulls the handler class and every gui-package class it touches onto the same loader as
 * {@code WWindow}, so package-private access is legal. Two rules make it robust:
 * <ol>
 *   <li>The handler entry point is {@code public static void handle(Object connection, java.nio.ByteBuffer payload)}
 *       ({@code payload} positioned just after the command byte).</li>
 *   <li>The handler is <b>self-contained</b>: it sends via {@link #send(Object, byte[])} on the
 *       connection it received, and stores nothing wired from {@code preInit}. Wiring state from
 *       {@code preInit} sets it on the modloader copy of the handler class — a different class object
 *       from the HookManager-loader copy the patch calls, so it reads back null at runtime.</li>
 * </ol>
 *
 * <p>Coexists with {@link ServerCommandMod} and with other {@code StaticCommandHook} installs in the
 * same client: the shared {@code wckSend} injection is added only if absent, and each command's peek is
 * an independent {@code insertBefore} guard.
 *
 * <pre>{@code
 * // preInit:
 * StaticCommandHook.install((byte) -67, AuctionClient.class);
 * // handler class:
 * public static void handle(Object conn, ByteBuffer bb) { ... }         // entry point
 * private static void send(byte[] payload) { StaticCommandHook.send(conn, payload); }
 * }</pre>
 */
public final class StaticCommandHook {

	private static final Logger LOGGER = Logger.getLogger(StaticCommandHook.class.getName());

	private static final String CONN_CLASS = "com.wurmonline.client.comm.SimpleServerConnectionClass";

	private static Method sendMethod;

	private StaticCommandHook() {
	}

	/**
	 * Patch {@code SimpleServerConnectionClass.reallyHandle} so an inbound {@code commandId} byte is
	 * consumed and dispatched to {@code handlerClass.handle(connection, buffer)} statically, and inject
	 * the shared {@code wckSend} raw-send method (once). Call from {@code preInit()}.
	 *
	 * @param commandId    the custom command byte this handler owns (must be unused by the vanilla switch)
	 * @param handlerClass a class with {@code public static void handle(Object, java.nio.ByteBuffer)}
	 */
	public static void install(byte commandId, Class<?> handlerClass) {
		Hooks.appendClassPath(handlerClass);
		CtClass conn = Hooks.get(CONN_CLASS);
		String handler = handlerClass.getName();
		Hooks.edit(() -> {
			CtMethod reallyHandle = conn.getDeclaredMethod("reallyHandle");
			reallyHandle.insertBefore(
				"{ if ($2 != null && $2.remaining() >= 1 && $2.get($2.position()) == (byte)" + commandId + ") {"
				+ "     $2.get();"
				+ "     " + handler + ".handle($0, $2);"
				+ "     return;"
				+ "} }");
			injectSendIfAbsent(conn);
		});
		LOGGER.info("StaticCommandHook: CMD " + commandId + " -> " + handler + ".handle (static dispatch).");
	}

	/**
	 * Push a raw payload (already led by the command byte) onto the connection via the injected
	 * {@code wckSend}. Call this from the handler class, on the connection {@code handle} received —
	 * never a connection wired in from {@code preInit}.
	 */
	public static void send(Object connection, byte[] payload) {
		if (connection == null) {
			LOGGER.warning("StaticCommandHook: no connection to send on yet");
			return;
		}
		try {
			if (sendMethod == null) {
				sendMethod = connection.getClass().getMethod("wckSend", byte[].class);
			}
			sendMethod.invoke(connection, (Object) payload);
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "StaticCommandHook: failed to send packet", e);
		}
	}

	private static void injectSendIfAbsent(CtClass conn) throws javassist.CannotCompileException {
		try {
			conn.getDeclaredMethod("wckSend");
			return; // already injected (by ServerCommandMod or a previous StaticCommandHook install)
		} catch (NotFoundException absent) {
			// fall through and add it
		}
		conn.addMethod(CtNewMethod.make(
			"public void wckSend(byte[] payload) {"
			+ "   java.nio.ByteBuffer buf = this.connection.getBuffer();"
			+ "   buf.put(payload);"
			+ "   this.reallySend();"
			+ "}", conn));
	}
}
