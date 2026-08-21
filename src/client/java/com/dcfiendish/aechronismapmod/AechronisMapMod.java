package com.dcfiendish.aechronismapmod;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AechronisMapMod implements ClientModInitializer {

	private static final Logger LOGGER = LoggerFactory.getLogger("Aechronis");

	public static AechronisMapData mapData;
	private static AechronisDataFetcher fetcher;
	private static AechronisRenderer renderer;
	private static boolean rendererRegistered = false;
	// True only while actually connected to Aechronis (mirrors the JOIN/DISCONNECT gating
	// below). Exists so features that don't need a renderer at all — e.g. AechronisAutoTSpawn —
	// can gate on server activation without duplicating the address check.
	public static volatile boolean active = false;

	@Override
	public void onInitializeClient() {
		LOGGER.info("Initializing...");

		// Register config
		AutoConfig.register(AechronisConfig.class, GsonConfigSerializer::new);

		// Register text-label toggle keybinds (unbound by default, see AechronisKeyBinds)
		AechronisKeyBinds.init();

		// Auto /t spawn on respawn (off by default, see AechronisConfig.autoTSpawn)
		AechronisAutoTSpawn.init();

		// Create data objects
		mapData = new AechronisMapData();
		fetcher = new AechronisDataFetcher();
		fetcher.mapData = mapData;

		// Register chat listener
		new AechronisChatListener(mapData).register();

		// Re-run on EVERY join, including proxy transfers (e.g. lobby -> main server),
		// since XaeroPlus may treat a backend transfer as a new map-world and drop
		// previously-registered draw features. We re-enable (not just enable-once)
		// so the draw features get freshly re-registered every time.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {

			// Only activate on the actual Aechronis server — skip singleplayer and any other server entirely.
			// IMPORTANT: every early-return path below must explicitly disable an already-enabled
			// renderer, not just skip re-enabling it. Otherwise leaving Aechronis (e.g. quitting to
			// singleplayer, or to any other server) leaves the PREVIOUS session's renderer running —
			// its draw features stay registered in AechronisRenderer.ourFeatures and the mixin keeps
			// rendering them unconditionally every frame regardless of what world you're actually in.
			// (This was the actual cause of the nation overlay showing up in singleplayer.)
			var serverData = client.getCurrentServer();
			String serverAddress = serverData != null ? serverData.ip : null;
			if (serverAddress == null || !serverAddress.toLowerCase().contains("aechronis.net")) {
				LOGGER.info("Not connected to Aechronis (address={}), mod inactive.", serverAddress);
				active = false;
				if (rendererRegistered) {
					renderer.disable();
					LOGGER.info("Renderer disabled (left Aechronis).");
				}
				fetcher.onLeaveAechronis();
				return;
			}

			active = true;

			if (!rendererRegistered) {
				// First time ever this session: create and add the module once.
				renderer = new AechronisRenderer(mapData);
				xaeroplus.module.ModuleManager.addModule(renderer);
				renderer.enable();
				rendererRegistered = true;
				LOGGER.info("Renderer created and enabled.");
			} else {
				// Subsequent joins (proxy transfers etc.) — force a fresh re-registration
				// of draw features by disabling then re-enabling the same module instance.
				renderer.disable();
				renderer.enable();
				LOGGER.info("Renderer re-enabled (fresh registration).");
			}
			fetcher.onJoinAechronis();
		});

		// Disconnecting (quit to title, kicked, connection lost) does NOT fire another
		// JOIN event. We deliberately do NOT call renderer.disable() here, even though
		// that means AechronisRenderer.ourFeatures (and therefore the Aechronis-only
		// fairplay bypass in AechronisDrawManagerMixin) can stay "live" for a few extra
		// frames until the next JOIN corrects it — this used to call renderer.disable()
		// immediately on disconnect, but that closes each DrawFeature (releasing
		// XaeroPlus's own GL-backed resources) at a moment that isn't guaranteed to be
		// safe relative to the old world/GL context's own teardown, which produced a
		// reproducible native crash (Windows exit 0xC0000409 / STATUS_STACK_BUFFER_OVERRUN,
		// no Java exception) specifically when disconnecting from Crusalis on the mod this
		// was forked from. The JOIN handler's existing "not connected to Aechronis" branch
		// already calls renderer.disable() safely — by that point a full new connection has
		// been established, well past the old GL context's teardown window. A brief stale
		// overlay/bypass is a far smaller cost than a client crash.
		//
		// Safe to call here regardless: onLeaveAechronis() is pure Java scheduler state
		// (cancels a ScheduledFuture), no GL/native interaction at all.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			active = false;
			fetcher.onLeaveAechronis();
		});

		LOGGER.info("Initialized!");
	}
}
