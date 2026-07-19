package org.gotti.wurmonline.clientmods.directconnect;

import java.util.logging.Logger;

import org.gotti.wurmunlimited.modloader.classhooks.HookException;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmClientMod;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.NotFoundException;

/**
 * Connect straight into a server without the JavaFX server-browser GUI.
 *
 * <p>Connection info is read from JVM system properties, falling back to environment variables:
 * <ul>
 *   <li>{@code -Dwurm.connect.ip}       / {@code WURM_CONNECT_IP}       (required)</li>
 *   <li>{@code -Dwurm.connect.port}     / {@code WURM_CONNECT_PORT}     (optional, default 3724)</li>
 *   <li>{@code -Dwurm.connect.user}     / {@code WURM_CONNECT_USER}     (required, the character/account name)</li>
 *   <li>{@code -Dwurm.connect.password} / {@code WURM_CONNECT_PASSWORD} (optional server password, default empty)</li>
 * </ul>
 *
 * <p>When IP + user are present the hook replicates {@code ServerBrowserFX.launchGame(...)}
 * headlessly and never constructs the splash screen or {@code ServerBrowserFX}. Otherwise it
 * delegates to the preserved original {@code prepareLaunch} and the normal server browser shows.
 *
 * <p>Steam must be running: the client uses the live SteamID as its auth credential, and
 * {@code WurmMain.start()} initialises Steam before {@code prepareLaunch} is reached.
 *
 * <h2>Why this never touches JavaFX through Javassist</h2>
 * In this client the JavaFX runtime is served from the module path / runtime image, so its
 * {@code .class} files are not resolvable by Javassist's ClassPool. {@code WurmMain} extends
 * {@code javafx.application.Application} and {@code prepareLaunch} uses JavaFX locals, so any
 * Javassist operation that walks the superclass, copies the method, or rebuilds its stack-map
 * frames fails with "cannot find javafx.*". To avoid that entirely:
 * <ol>
 *   <li>{@link CtMethod#setName} renames the original to {@code prepareLaunch$directconnect} - a
 *       pure name edit; its bytecode and stack map are kept verbatim (verified lazily by the real
 *       runtime class loader, exactly as in vanilla).</li>
 *   <li>A fresh {@code prepareLaunch} is added whose body references only fully-qualified
 *       {@code com.wurmonline.*}/{@code java.*} members (no JavaFX type, no superclass merge), so
 *       its stack-map rebuild needs no JavaFX.</li>
 *   <li>The fallback invokes the preserved original <em>reflectively</em>, so Javassist performs
 *       no method-hierarchy walk that would reach {@code Application}.</li>
 * </ol>
 */
public class DirectConnect implements WurmClientMod, PreInitable {

	private static final Logger LOGGER = Logger.getLogger(DirectConnect.class.getName());

	private static final String ORIGINAL_COPY = "prepareLaunch$directconnect";

	@Override
	public void preInit() {
		try {
			ClassPool classPool = HookManager.getInstance().getClassPool();
			CtClass wurmMain = classPool.get("com.wurmonline.client.launcherfx.WurmMain");

			// 1. Preserve the original JavaFX-heavy method under a new name (pure rename: no
			//    superclass walk, no stack-map rebuild, JavaFX never resolved).
			CtMethod original = wurmMain.getDeclaredMethod("prepareLaunch");
			original.setName(ORIGINAL_COPY);

			// 2. Add a fresh, JavaFX-free prepareLaunch(String) and give it our wrapper body.
			CtClass stringType = classPool.get("java.lang.String");
			CtMethod wrapper = new CtMethod(CtClass.voidType, "prepareLaunch", new CtClass[] { stringType }, wurmMain);
			wrapper.setModifiers(Modifier.PRIVATE);
			wurmMain.addMethod(wrapper);
			wrapper.setBody(WRAPPER_BODY);

			LOGGER.info("DirectConnect: installed wrapper on WurmMain.prepareLaunch");
		} catch (NotFoundException | CannotCompileException e) {
			throw new HookException(e);
		}
	}

	/**
	 * Body for the replacement {@code WurmMain.prepareLaunch(String)}. Compiled by Javassist, so it
	 * is kept pre-Java-5 (raw types, no generics/diamond/enhanced-for) and references only
	 * fully-qualified client classes, {@code java.*} and reflection - no JavaFX type appears.
	 * If connection info is absent it reflectively delegates to the preserved original
	 * ({@link #ORIGINAL_COPY}), which shows the normal server browser.
	 */
	private static final String WRAPPER_BODY =
		  "{"
		+ "  String __ip = System.getProperty(\"wurm.connect.ip\");"
		+ "  if (__ip == null) __ip = System.getenv(\"WURM_CONNECT_IP\");"
		+ "  String __user = System.getProperty(\"wurm.connect.user\");"
		+ "  if (__user == null) __user = System.getenv(\"WURM_CONNECT_USER\");"
		+ "  if (__ip != null && __ip.trim().length() > 0 && __user != null && __user.trim().length() > 0) {"
		+ "    __ip = __ip.trim();"
		+ "    __user = __user.trim();"
		+ "    String __portStr = System.getProperty(\"wurm.connect.port\");"
		+ "    if (__portStr == null) __portStr = System.getenv(\"WURM_CONNECT_PORT\");"
		+ "    int __port = 3724;"
		+ "    if (__portStr != null && __portStr.trim().length() > 0) {"
		+ "      try { __port = Integer.parseInt(__portStr.trim()); } catch (NumberFormatException __e) { __port = 3724; }"
		+ "    }"
		+ "    String __pass = System.getProperty(\"wurm.connect.password\");"
		+ "    if (__pass == null) __pass = System.getenv(\"WURM_CONNECT_PASSWORD\");"
		+ "    if (__pass == null) __pass = \"\";"
		+ "    System.out.println(\"DirectConnect: connecting to \" + __ip + \":\" + __port + \" as '\" + __user + \"' (skipping server browser)\");"
		// JavaFX isn't resolvable to Javassist, so call Platform.setImplicitExit(false) reflectively
		// to keep the FX runtime alive after we skip showing any Stage.
		+ "    try {"
		+ "      Class __pf = Class.forName(\"javafx.application.Platform\");"
		+ "      Class[] __sig = new Class[1]; __sig[0] = Boolean.TYPE;"
		+ "      Object[] __pa = new Object[1]; __pa[0] = Boolean.FALSE;"
		+ "      __pf.getMethod(\"setImplicitExit\", __sig).invoke(null, __pa);"
		+ "    } catch (Throwable __pfe) { __pfe.printStackTrace(); }"
		+ "    com.wurmonline.client.launcherfx.WurmMain.serverIp = __ip;"
		+ "    com.wurmonline.client.launcherfx.WurmMain.serverPort = __port;"
		+ "    com.wurmonline.client.settings.Profile __profile = com.wurmonline.client.settings.Profile.getProfile();"
		+ "    __profile.loadPlayer(__user);"
		+ "    __profile.associateConfig();"
		+ "    com.wurmonline.client.WurmClientBase.setServerPassword(__pass);"
		+ "    com.wurmonline.client.WurmClientBase.setPassword(com.wurmonline.client.WurmClientBase.steamHandler.getSteamIdAsString());"
		+ "    com.wurmonline.client.WurmClientBase.setUsername(__user);"
		+ "    com.wurmonline.client.WurmClientBase.setExtraTileData(com.wurmonline.client.options.Options.isExtraTileData.value());"
		+ "    java.util.List __packs = new java.util.ArrayList();"
		+ "    __packs.add(\"sound.jar\");"
		+ "    __packs.add(\"pmk.jar\");"
		+ "    __packs.add(\"graphics.jar\");"
		+ "    com.wurmonline.client.resources.Resources __res = new com.wurmonline.client.resources.Resources(com.wurmonline.client.settings.GlobalData.getPackDirectory(), __packs);"
		+ "    try {"
		+ "      java.awt.GraphicsDevice __dev = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();"
		+ "      com.wurmonline.client.LwjglClient.setStartupDevice(__dev);"
		+ "    } catch (Throwable __t) { __t.printStackTrace(); }"
		+ "    System.gc();"
		+ "    com.wurmonline.client.WurmClientBase.launch(__profile.launchProfile(), __res, false);"
		+ "    return;"
		+ "  }"
		// No connection info: delegate to the preserved original (reflectively, so Javassist does
		// not resolve the JavaFX-containing method hierarchy at compile time).
		// Resolve the Class via Class.forName (NOT $0.getClass(), which would make Javassist walk
		// WurmMain's JavaFX superclass). $0 is only ever passed as the invoke target argument.
		+ "  try {"
		+ "    Class[] __ps = new Class[1]; __ps[0] = Class.forName(\"java.lang.String\");"
		+ "    Class __wm = Class.forName(\"com.wurmonline.client.launcherfx.WurmMain\");"
		+ "    java.lang.reflect.Method __m = __wm.getDeclaredMethod(\"" + ORIGINAL_COPY + "\", __ps);"
		+ "    __m.setAccessible(true);"
		+ "    Object[] __ia = new Object[1]; __ia[0] = $1;"
		+ "    __m.invoke($0, __ia);"
		+ "  } catch (Throwable __dt) { throw new RuntimeException(__dt); }"
		+ "}";
}
