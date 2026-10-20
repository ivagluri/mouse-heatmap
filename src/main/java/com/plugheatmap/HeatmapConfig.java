package com.plugheatmap;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(HeatmapConfig.GROUP)
public interface HeatmapConfig extends Config
{
	String GROUP = "mouseheatmap";
	String SHOW_HEATMAP = "showHeatmap";
	String INCLUDE_INTERFACES = "includeInterfaces";
	String REMEMBER_HEATMAP = "rememberHeatmap";

	@ConfigSection(
		name = "Advanced",
		description = "Grid, timing and rendering tweaks",
		position = 10,
		closedByDefault = true
	)
	String advancedSection = "advanced";

	@ConfigItem(
		keyName = SHOW_HEATMAP,
		name = "Show heatmap",
		description = "Draw the heatmap over the game view. Mouse movement is still recorded while hidden",
		position = 0
	)
	default boolean showHeatmap()
	{
		return true;
	}

	@ConfigItem(
		keyName = "toggleKeybind",
		name = "Toggle hotkey",
		description = "Hotkey to show or hide the heatmap",
		position = 1
	)
	default Keybind toggleKeybind()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = INCLUDE_INTERFACES,
		name = "Include interfaces",
		description = "Map the whole client, including the inventory, chat and other interfaces, and draw over them."
			+ " Each side panel tab can have its own heatmap",
		position = 2
	)
	default boolean includeInterfaces()
	{
		return true;
	}

	@ConfigItem(
		keyName = "splitTabs",
		name = "Separate tab heatmaps",
		description = "Keep a separate heatmap for each side panel tab, such as inventory, prayer and spellbook."
			+ " Needs Include interfaces",
		position = 3
	)
	default boolean splitTabs()
	{
		return true;
	}

	@ConfigItem(
		keyName = "coldColor",
		name = "Cold colour",
		description = "Colour of the least visited areas",
		position = 4
	)
	default Color coldColor()
	{
		return Color.GREEN;
	}

	@ConfigItem(
		keyName = "warmColor",
		name = "Warm colour",
		description = "Colour of moderately visited areas",
		position = 5
	)
	default Color warmColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(
		keyName = "hotColor",
		name = "Hot colour",
		description = "Colour of the most visited areas",
		position = 6
	)
	default Color hotColor()
	{
		return Color.RED;
	}

	@Range(max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "opacity",
		name = "Opacity",
		description = "Opacity of the hottest areas",
		position = 7
	)
	default int opacity()
	{
		return 50;
	}

	@ConfigItem(
		keyName = REMEMBER_HEATMAP,
		name = "Remember heatmap",
		description = "Save the heatmap and restore it next time the client starts",
		position = 8
	)
	default boolean rememberHeatmap()
	{
		return true;
	}

	@Range(min = 2, max = 50)
	@Units(Units.PIXELS)
	@ConfigItem(
		keyName = "cellSize",
		name = "Cell size",
		description = "Size of each heatmap cell. Changing this clears the heatmap",
		position = 0,
		section = advancedSection
	)
	default int cellSize()
	{
		return 5;
	}

	@Range(max = 100)
	@Units(Units.PIXELS)
	@ConfigItem(
		keyName = "radius",
		name = "Spread radius",
		description = "How far around the pointer heat is added",
		position = 1,
		section = advancedSection
	)
	default int radius()
	{
		return 5;
	}

	@Range(min = 20, max = 1000)
	@Units(Units.MILLISECONDS)
	@ConfigItem(
		keyName = "sampleInterval",
		name = "Sample interval",
		description = "How often the mouse position is recorded",
		position = 2,
		section = advancedSection
	)
	default int sampleInterval()
	{
		return 20;
	}

	@Range(min = 20, max = 5000)
	@Units(Units.MILLISECONDS)
	@ConfigItem(
		keyName = "renderInterval",
		name = "Update interval",
		description = "How often the drawn heatmap is refreshed",
		position = 3,
		section = advancedSection
	)
	default int renderInterval()
	{
		return 200;
	}

	@Units(Units.MINUTES)
	@ConfigItem(
		keyName = "fadeMinutes",
		name = "Fade half-life",
		description = "Time for heat to fade to half. 0 never fades",
		position = 4,
		section = advancedSection
	)
	default int fadeMinutes()
	{
		return 0;
	}

	@Range(max = 3600)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "timeToHot",
		name = "Time to hot",
		description = "How long the mouse must spend over a spot before it shows the hot colour. Until a spot gets"
			+ " there, the map builds up gradually; after that, colours are relative to the hottest spot."
			+ " 0 always shows the hottest spot as hot",
		position = 5,
		section = advancedSection
	)
	default int timeToHot()
	{
		return 90;
	}

	@ConfigItem(
		keyName = "ignoreIdle",
		name = "Ignore idle mouse",
		description = "Only record while the mouse is moving or clicking, so a resting pointer doesn't build up heat",
		position = 6,
		section = advancedSection
	)
	default boolean ignoreIdle()
	{
		return true;
	}

	@ConfigItem(
		keyName = "smooth",
		name = "Smooth",
		description = "Blend cells together instead of drawing hard-edged squares",
		position = 7,
		section = advancedSection
	)
	default boolean smooth()
	{
		return true;
	}

	@ConfigItem(
		keyName = "resetKeybind",
		name = "Reset hotkey",
		description = "Hotkey to clear the heatmap",
		position = 8,
		section = advancedSection
	)
	default Keybind resetKeybind()
	{
		return Keybind.NOT_SET;
	}
}
