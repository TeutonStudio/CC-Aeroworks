# The four displays

[[Deutsch|Programmierbare-Displays]] · [[English|Displays-en]]

![All four mounted display variants: normal large, normal small, advanced small and advanced large.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-four-displays.png)


CC-Aeroworks provides **four display modules**: small and large, each in normal and advanced tiers. Every variant supports text, numbers and pixel output. Size and tier are independent: a large display gains advanced functions only when it is the advanced variant.

## Compare the variants

| Display | Item ID | Compatible sockets | Colour | Touch / draw | Raster at 256 PPB |
|---|---|---|---|---|---|
| Small Display | `cc_aeroworks:small_display` | `left`, `right`, `big` | fixed signal colour | no | 112 × 112 |
| Large Display | `cc_aeroworks:large_display` | `big` only | fixed signal colour | no | 160 × 112 |
| Advanced Small Display | `cc_aeroworks:advanced_small_display` | `left`, `right`, `big` | configurable, `0xRRGGBB` | yes, with an advanced slot device | 112 × 112 |
| Advanced Large Display | `cc_aeroworks:advanced_large_display` | `big` only | configurable, `0xRRGGBB` | yes, with an advanced slot device | 160 × 112 |

Normal displays have a grey frame; advanced displays have a yellow frame and a persistent 24-bit display colour. Touch and draw also require an **advanced CC:Tweaked computer** or an **advanced CC: Terminals terminal** in the active desk assembly. A normal computer is sufficient for output.

## 1. Small Display

The Small Display is an output module for compact instruments. It fits either small socket or the large socket. Its usable surface measures **7/16 × 7/16 of a block**.

**Recipe:** 1 normal CC:Tweaked monitor → Mechanical Press → 1 Small Display.

![Recipe: a normal monitor under the Mechanical Press produces a Small Display.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/recipes/en-gb/small_display.png)

## 2. Large Display

The Large Display provides a wider surface measuring **10/16 × 7/16 of a block**. It fits `big` only and remains an output module without touch input.

**Recipe:** 1 Small Display + 1 Iron Sheet (`create:iron_sheet`) → Deployer → 1 Large Display.

![Recipe: a Small Display and an Iron Sheet in the Deployer produce a Large Display.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/recipes/en-gb/large_display.png)

## 3. Advanced Small Display

This variant combines the small surface with configurable colour and touch/draw input. It fits `left`, `right` and `big`.

**Recipe:** 1 advanced CC:Tweaked monitor → Mechanical Press → 1 Advanced Small Display.

![Recipe: an advanced monitor under the Mechanical Press produces an Advanced Small Display.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/recipes/en-gb/advanced_small_display.png)

## 4. Advanced Large Display

This variant combines the wider surface with configurable colour and touch/draw input. It fits `big` only.

**Recipe:** 1 Advanced Small Display + 1 Iron Sheet (`create:iron_sheet`) → Deployer → 1 Advanced Large Display.

![Recipe: an Advanced Small Display and an Iron Sheet in the Deployer produce an Advanced Large Display.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/recipes/en-gb/advanced_large_display.png)

Each recipe produces one display. Enlarging a display preserves its tier. Power the press and deployer using Create's usual rotational power; the deployer applies the Iron Sheet. These images are rendered from the mod models and textures and follow the recipe JSON files in version 1.5.3.

## Mounting and interaction

1. Mount the display in a compatible socket on an Aeroworks desk.
2. Sneak and right-click to open the module overview, then insert the required slot device.
3. Use a Create wrench on a horizontal side of the desk to open module settings.
4. On an advanced display, **Display Control** selects a compatible Lua script or, with Create: Radars installed, a radar source. Only one source owns the display output at a time.
5. Right-click to enter desk control for touch input. Left-click produces `tap`; holding the right mouse button produces a `draw` gesture. The configured input axes move the virtual pointer.

The embedded computer runs bound handlers automatically. An advanced terminal can enable touch for an external computer, whose program must process the events itself. A terminal does not run local Lua files.

## Resolution

The raster follows the **pixel density in parts per block (PPB)** and the physical surface area. The default is 256 PPB, independent of tier. Use `getDisplaySize(socket)` to read the effective raster dimensions.

When the server disables `display.useGlobalResolution`, programs can set individual socket resolutions. Changing the raster size clears an incompatible pixel image, so the program must redraw it. See the [configuration reference](https://github.com/TeutonStudio/CC-Aeroworks/blob/master/docs/configuration.md) for the server settings.

## Programming

The [[Lua API|API-en]] explains desk discovery, text and number output, pixel rasters, colour and touch handlers. [Executable examples](https://github.com/TeutonStudio/CC-Aeroworks/tree/master/examples/cc) and the [[German troubleshooting guide|Fehlerbehebung]] provide further help.
