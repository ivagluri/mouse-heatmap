package com.plugheatmap;

import com.google.inject.Provides;
import com.plugheatmap.HeatmapStorage.Saved;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Point;
import net.runelite.api.events.ClientTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.HotkeyListener;

@Slf4j
@PluginDescriptor(
	name = "Mouse Heatmap",
	internalName = "mouseheatmap",
	description = "Draws a heatmap of where your mouse spends its time over the game view",
	tags = {"mouse", "heatmap", "overlay"}
)
public class HeatmapPlugin extends Plugin
{
	// the side panel tab content area for fixed, resizable classic and resizable modern layouts
	private static final int[] SIDE_PANELS = {
		InterfaceID.Toplevel.SIDE_PANELS,
		InterfaceID.ToplevelOsrsStretch.SIDE_PANELS,
		InterfaceID.ToplevelPreEoc.SIDE_PANELS,
	};

	// a click counts the mouse as active for one game tick, even if it hasn't moved
	private static final long CLICK_ACTIVE_MILLIS = 600;
	private static final long SAVE_INTERVAL_MILLIS = 60_000;
	private static final String SAVE_FILE = "heatmap.dat";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private HeatmapConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private HeatmapOverlay overlay;

	@Inject
	private ScheduledExecutorService executor;

	@Getter
	private final Heatmap heatmap = new Heatmap();

	// keyed by open side panel tab
	private final Map<Integer, Heatmap> tabHeatmaps = new HashMap<>();

	/**
	 * The heatmap for the open side panel tab, or null when tabs aren't split or no panel is open.
	 */
	@Getter
	private Heatmap tabHeatmap;

	private long lastSampleMillis;
	private long lastRenderMillis;
	private long lastSaveMillis;
	// false until any saved heatmap has been loaded, so an empty one can't overwrite it
	private volatile boolean loaded;
	private Point lastMouse;
	// written on the AWT thread, read on the client thread
	private volatile long lastClickMillis;

	private final HotkeyListener toggleListener = new HotkeyListener(() -> config.toggleKeybind())
	{
		@Override
		public void hotkeyPressed()
		{
			configManager.setConfiguration(HeatmapConfig.GROUP, HeatmapConfig.SHOW_HEATMAP, !config.showHeatmap());
		}
	};

	private final HotkeyListener resetListener = new HotkeyListener(() -> config.resetKeybind())
	{
		@Override
		public void hotkeyPressed()
		{
			clientThread.invoke(HeatmapPlugin.this::clear);
		}
	};

	private final MouseAdapter clickListener = new MouseAdapter()
	{
		@Override
		public MouseEvent mousePressed(MouseEvent event)
		{
			lastClickMillis = System.currentTimeMillis();
			return event;
		}
	};

	@Override
	protected void startUp() throws Exception
	{
		clientThread.invoke(this::updateColors);
		overlayManager.add(overlay);
		keyManager.registerKeyListener(toggleListener);
		keyManager.registerKeyListener(resetListener);
		mouseManager.registerMouseListener(clickListener);

		loaded = false;
		if (config.rememberHeatmap())
		{
			executor.execute(this::load);
		}
		else
		{
			loaded = true;
		}
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(overlay);
		keyManager.unregisterKeyListener(toggleListener);
		keyManager.unregisterKeyListener(resetListener);
		mouseManager.unregisterMouseListener(clickListener);
		clientThread.invoke(() ->
		{
			if (config.rememberHeatmap() && loaded)
			{
				Saved saved = snapshot();
				executor.execute(() -> write(saved));
			}
			clear();
		});
	}

	@Subscribe
	public void onClientShutdown(ClientShutdown event)
	{
		if (!config.rememberHeatmap() || !loaded)
		{
			return;
		}

		CompletableFuture<Saved> saved = new CompletableFuture<>();
		clientThread.invoke(() -> saved.complete(snapshot()));
		event.waitFor(saved.thenAcceptAsync(this::write, executor));
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		if (config.includeInterfaces())
		{
			heatmap.resize(0, 0, client.getCanvasWidth(), client.getCanvasHeight(), config.cellSize());
		}
		else
		{
			heatmap.resize(client.getViewportXOffset(), client.getViewportYOffset(),
				client.getViewportWidth(), client.getViewportHeight(), config.cellSize());
		}
		updateTabHeatmap();

		long now = System.currentTimeMillis();
		if (now - lastSampleMillis >= config.sampleInterval())
		{
			lastSampleMillis = now;
			sample(now);
		}

		long sinceRender = now - lastRenderMillis;
		if (sinceRender >= config.renderInterval())
		{
			lastRenderMillis = now;
			// cap the elapsed time so the first render after logging in doesn't fade everything away
			sinceRender = Math.min(sinceRender, config.renderInterval());
			float decay = config.fadeMinutes() > 0
				? (float) Math.pow(0.5, sinceRender / (config.fadeMinutes() * 60_000.0))
				: 1;
			render(heatmap, decay);
			for (Heatmap tab : tabHeatmaps.values())
			{
				render(tab, decay);
			}
		}

		if (config.rememberHeatmap() && loaded && now - lastSaveMillis >= SAVE_INTERVAL_MILLIS)
		{
			lastSaveMillis = now;
			Saved saved = snapshot();
			executor.execute(() -> write(saved));
		}
	}

	private void render(Heatmap map, float decay)
	{
		if (decay < 1)
		{
			map.decay(decay);
		}
		map.render(config.logScale(), config.smooth());
	}

	private void updateTabHeatmap()
	{
		Rectangle panel = config.includeInterfaces() && config.splitTabs() ? findSidePanel() : null;
		if (panel == null)
		{
			tabHeatmap = null;
			return;
		}

		int tab = client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL);
		tabHeatmap = tabHeatmaps.computeIfAbsent(tab, t -> newHeatmap());
		tabHeatmap.resize(panel.x, panel.y, panel.width, panel.height, config.cellSize());
	}

	private Heatmap newHeatmap()
	{
		Heatmap map = new Heatmap();
		setColors(map);
		return map;
	}

	private Rectangle findSidePanel()
	{
		for (int id : SIDE_PANELS)
		{
			Widget widget = client.getWidget(id);
			if (widget != null && !widget.isHidden())
			{
				return widget.getBounds();
			}
		}
		return null;
	}

	private void sample(long now)
	{
		Point mouse = client.getMouseCanvasPosition();
		boolean idle = mouse.equals(lastMouse) && now - lastClickMillis > CLICK_ACTIVE_MILLIS;
		lastMouse = mouse;
		if (client.isMenuOpen() || (idle && config.ignoreIdle()))
		{
			return;
		}

		if (tabHeatmap == null || !add(tabHeatmap, mouse))
		{
			add(heatmap, mouse);
		}
	}

	/**
	 * Adds heat at a canvas point if it's inside the heatmap's area.
	 */
	private boolean add(Heatmap map, Point mouse)
	{
		int x = mouse.getX() - map.getX();
		int y = mouse.getY() - map.getY();
		if (x < 0 || y < 0 || x >= map.getWidth() || y >= map.getHeight())
		{
			return false;
		}
		map.add(x, y, config.radius());
		return true;
	}

	/**
	 * Copies every heatmap for saving. Must run on the client thread.
	 */
	private Saved snapshot()
	{
		Map<Integer, Heatmap.Snapshot> tabs = new HashMap<>();
		tabHeatmaps.forEach((tab, map) -> tabs.put(tab, map.snapshot()));
		return new Saved(heatmap.snapshot(), tabs);
	}

	private void restore(Saved saved)
	{
		heatmap.restore(saved.getHeatmap());
		tabHeatmaps.clear();
		saved.getTabHeatmaps().forEach((tab, snapshot) ->
		{
			Heatmap map = newHeatmap();
			map.restore(snapshot);
			tabHeatmaps.put(tab, map);
		});
		tabHeatmap = null;
	}

	private Filepath saveFile() throws IOException
	{
		return getPluginDirectory().joinSegment(SAVE_FILE);
	}

	private void load()
	{
		try
		{
			Filepath file = saveFile();
			if (file.exists())
			{
				Saved saved = HeatmapStorage.read(file);
				clientThread.invoke(() ->
				{
					restore(saved);
					loaded = true;
				});
				return;
			}
		}
		catch (IOException e)
		{
			log.warn("Failed to load saved heatmap", e);
		}
		loaded = true;
	}

	private void write(Saved saved)
	{
		try
		{
			HeatmapStorage.write(saveFile(), saved);
		}
		catch (IOException e)
		{
			log.warn("Failed to save heatmap", e);
		}
	}

	private void clear()
	{
		heatmap.clear();
		tabHeatmaps.clear();
		tabHeatmap = null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!HeatmapConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		if (HeatmapConfig.REMEMBER_HEATMAP.equals(event.getKey()) && config.rememberHeatmap())
		{
			// keep the current session's heatmap and save it from now on
			loaded = true;
		}

		if (HeatmapConfig.INCLUDE_INTERFACES.equals(event.getKey()))
		{
			overlayManager.remove(overlay);
			overlay.updateLayer();
			overlayManager.add(overlay);
		}

		// also marks the heatmaps dirty, so scale and smoothing changes redraw too
		clientThread.invoke(this::updateColors);
	}

	private void updateColors()
	{
		setColors(heatmap);
		tabHeatmaps.values().forEach(this::setColors);
	}

	private void setColors(Heatmap map)
	{
		map.setColors(config.coldColor(), config.warmColor(), config.hotColor(), config.opacity());
	}

	@Provides
	HeatmapConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HeatmapConfig.class);
	}
}
