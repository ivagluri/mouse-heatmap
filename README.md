# Mouse Heatmap
Draws a heatmap of where your mouse spends its time over the game view.

## Settings
- **Show heatmap** - Draw the heatmap. Mouse movement is still recorded while hidden
- **Toggle hotkey** - Hotkey to show or hide the heatmap
- **Include interfaces** - Map the whole client, including the inventory, chat and other interfaces, and draw over them
- **Separate tab heatmaps** - Keep a separate heatmap for each side panel tab, such as inventory, prayer and spellbook. Needs Include interfaces
- **Cold colour** - Colour of the least visited areas
- **Warm colour** - Colour of moderately visited areas
- **Hot colour** - Colour of the most visited areas
- **Opacity** - Opacity of the hottest areas
- **Remember heatmap** - Save the heatmap and restore it next time the client starts

### Advanced
- **Cell size** - Size of each heatmap cell. Changing this clears the heatmap
- **Spread radius** - How far around the pointer heat is added
- **Sample interval** - How often the mouse position is recorded
- **Update interval** - How often the drawn heatmap is refreshed
- **Fade half-life** - Time for heat to fade to half. 0 never fades
- **Time to hot** - How long the mouse must spend over a spot before it shows the hot colour. Until a spot gets there, the map builds up gradually; after that, colours are relative to the hottest spot. 0 always shows the hottest spot as hot
- **Ignore idle mouse** - Only record while the mouse is moving or clicking, so a resting pointer doesn't build up heat
- **Smooth** - Blend cells together instead of drawing hard-edged squares
- **Reset hotkey** - Hotkey to clear the heatmap
