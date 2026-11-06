# Donut Base Finder

Client-side Fabric mod for Minecraft **1.21.11** (Java 21, Fabric Loader 0.20.1, Fabric API 0.141.5+1.21.11).

- Hold **F** and press **U** to open or close the settings screen. The **U** key is a normal
  Fabric key mapping, so it can be rebound in Options > Controls. F is the fixed modifier.
- Draws colour-coded wireframe boxes through walls: chests **brown**, shulker boxes **purple**,
  spawners **red**.
- Settings are saved to `config/donutbasefinder.json`.

## Build

The Gradle wrapper jar and scripts are not included. Generate them once with any Gradle 9.x:

```
gradle wrapper --gradle-version 9.2.1
./gradlew build
```

The mod is `build/libs/donutbasefinder-1.0.0.jar` (not the `-sources` jar). Drop it into `mods/`
together with Fabric API.

To test in a dev client: `./gradlew runClient`.

## Notes

- Mappings are **Mojang official names**, because Fabric's 1.21.11 rendering documentation is
  written against them.
- Through-walls drawing uses a custom `RenderPipeline` with `DepthTestFunction.NO_DEPTH_TEST`.
- Pressing F also triggers vanilla "Swap Item With Offhand". Rebind that key in Controls if it
  gets in the way.
- Many servers prohibit client-side ESP. Check the rules of any server before using this.

## Layout

```
src/main/java/com/donutbasefinder/
  DonutBaseFinder.java          entrypoint
  config/DonutConfig.java       settings + JSON persistence
  input/KeyHandler.java         F + U chord, key mapping registration
  gui/DonutSettingsScreen.java  settings screen
  esp/EspTarget.java            block kinds, colours, box shapes
  esp/BlockScanner.java         periodic scan of loaded chunks
  esp/EspRenderer.java          WorldRenderEvents hook + through-walls pipeline
  mixin/GameRendererMixin.java  releases GPU buffers on shutdown
```
