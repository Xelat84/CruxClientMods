package com.wurmonline.clientkit;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.gotti.wurmunlimited.modloader.classhooks.HookException;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmClientMod;

import javassist.CannotCompileException;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.NotFoundException;

/**
 * Base for a client mod that speaks ONE custom server -&gt; client command byte and can send raw
 * payloads back. Subclass and supply {@link #commandId()} + {@link #handle(Object, java.nio.ByteBuffer)}.
 *
 * <p>The whole design is copied from the AuctionHouse mod's proven javassist hook, generalized so
 * multiple mods can coexist:
 *
 * <ol>
 *   <li><b>reallyHandle patch (once)</b> — {@code SimpleServerConnectionClass.reallyHandle(int,ByteBuffer)}
 *       is patched to peek the first payload byte with an absolute {@code get(int)} (leaving the
 *       vanilla {@code cmd = bb.get()} untouched for every other command). If the byte matches ANY
 *       registered command id, it consumes that byte and dispatches to that command's handler, then
 *       returns before the vanilla switch. The peek/dispatch is generic and installed exactly once.</li>
 *   <li><b>wckSend injection (once)</b> — a public {@code wckSend(byte[])} is added to
 *       {@code SimpleServerConnectionClass} (writes to the private inner {@code connection.getBuffer()}
 *       and calls the private {@code reallySend()}). Installed once regardless of how many mods extend
 *       this base.</li>
 *   <li><b>ClassClassPath append (per subclass)</b> — the patched vanilla method references this
 *       package's dispatcher, which the HookManager Loader must be able to <i>define</i> at runtime.
 *       Each concrete subclass jar runs on its own private classloader, so we append a
 *       {@link ClassClassPath} of the concrete subclass's class to the pool. Without it the patched
 *       vanilla class throws {@code NoClassDefFoundError} when it first references our code. As a
 *       bonus, GUI helper classes living in {@code com.wurmonline.client.renderer.gui} then load on
 *       the same loader as {@code WWindow}, which is what makes their package-private access work.</li>
 * </ol>
 *
 * <p>Multi-mod safety: the reallyHandle patch and wckSend injection are guarded by a static
 * {@code patched} flag so only the first subclass to {@code preInit} installs them; every subclass
 * registers its own {@code (commandId -> this)} entry in {@link #HANDLERS}, which the generic
 * dispatcher consults. The ClassClassPath append is done by every subclass (its jar is a different
 * classpath entry, so all of them need to be on the pool).
 */
public abstract class ServerCommandMod implements WurmClientMod, PreInitable {

	private static final Logger LOGGER = Logger.getLogger(ServerCommandMod.class.getName());

	private static final String CONN_CLASS = "com.wurmonline.client.comm.SimpleServerConnectionClass";

	/** commandId (as unsigned int 0..255) -> owning mod instance. */
	private static final ConcurrentHashMap<Integer, ServerCommandMod> HANDLERS = new ConcurrentHashMap<>();

	private static volatile boolean patched;

	private static Method wckSendMethod;

	/** The custom command byte this mod owns (e.g. {@code -67}). Must be unique across all mods. */
	protected abstract byte commandId();

	/**
	 * Handle one received packet. {@code payload} is positioned just after the command byte (on the
	 * mod's own sub-command / first field). Runs on the client (game) thread.
	 */
	protected abstract void handle(Object connection, java.nio.ByteBuffer payload);

	@Override
	public void preInit() {
		int key = commandId() & 0xFF;
		ServerCommandMod prev = HANDLERS.putIfAbsent(key, this);
		if (prev != null && prev != this) {
			LOGGER.warning(getClass().getName() + ": command id " + commandId()
				+ " already registered by " + prev.getClass().getName() + "; ignoring duplicate.");
		}

		try {
			ClassPool pool = HookManager.getInstance().getClassPool();
			pool.appendClassPath(new ClassClassPath(getClass()));
			installShared(pool);
			LOGGER.info(getClass().getName() + ": registered ServerCommandMod for CMD " + commandId() + ".");
		} catch (NotFoundException | CannotCompileException e) {
			throw new HookException(e);
		}
	}

	private static synchronized void installShared(ClassPool pool) throws NotFoundException, CannotCompileException {
		if (patched) {
			return;
		}

		CtClass conn = pool.get(CONN_CLASS);

		CtMethod reallyHandle = conn.getDeclaredMethod("reallyHandle");
		reallyHandle.insertBefore(
			"{ if ($2 != null && $2.remaining() >= 1"
			+ "     && com.wurmonline.clientkit.ServerCommandMod.owns($2.get($2.position()))) {"
			+ "     byte __wckCmd = $2.get();"
			+ "     com.wurmonline.clientkit.ServerCommandMod.dispatch(__wckCmd, $0, $2);"
			+ "     return;"
			+ "} }");

		if (!hasMethod(conn, "wckSend")) {
			conn.addMethod(CtNewMethod.make(
				"public void wckSend(byte[] payload) {"
				+ "   java.nio.ByteBuffer buf = this.connection.getBuffer();"
				+ "   buf.put(payload);"
				+ "   this.reallySend();"
				+ "}", conn));
		}

		patched = true;
		LOGGER.info("WurmClientKit: patched " + CONN_CLASS + ".reallyHandle and injected wckSend (once).");
	}

	private static boolean hasMethod(CtClass ctClass, String name) {
		try {
			ctClass.getDeclaredMethod(name);
			return true;
		} catch (NotFoundException absent) {
			return false;
		}
	}

	/** Called from the patched vanilla method: is any mod registered for this command byte? */
	public static boolean owns(byte cmd) {
		return HANDLERS.containsKey(cmd & 0xFF);
	}

	/** Called from the patched vanilla method: route to the owning mod. {@code bb} is past the cmd byte. */
	public static void dispatch(byte cmd, Object connection, java.nio.ByteBuffer bb) {
		ServerCommandMod mod = HANDLERS.get(cmd & 0xFF);
		if (mod == null) {
			LOGGER.warning("WurmClientKit: no handler for command " + cmd);
			return;
		}
		try {
			mod.handle(connection, bb);
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "WurmClientKit: handler for command " + cmd + " threw", e);
		}
	}

	/**
	 * Send a raw payload on the given connection via the injected {@code wckSend}. Invoked reflectively
	 * because {@code wckSend} is added to a vanilla class at runtime and isn't on the compile classpath.
	 * The payload should already carry the command byte (build it with {@link PacketWriter}).
	 */
	protected void send(Object connection, byte[] payload) {
		if (connection == null) {
			LOGGER.warning(getClass().getName() + ": no connection to send on yet");
			return;
		}
		try {
			if (wckSendMethod == null) {
				wckSendMethod = connection.getClass().getMethod("wckSend", byte[].class);
			}
			wckSendMethod.invoke(connection, (Object) payload);
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, getClass().getName() + ": failed to send packet", e);
		}
	}
}
