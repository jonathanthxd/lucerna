# TFL Client / Lucerna: experimental Ornithe compatibility plan

Upstream: https://github.com/Z1xus/lucerna
TFL Client: https://github.com/TFLivesStudio/TFLClientMOD

## Baseline (recorded from TFL Client `tools/ornithe/dependencies.json`)
- Minecraft 1.8.9, Ornithe Gen2, Feather mappings build 2, Fabric Loader 0.19.5
- Java 25, Ornithe Standard Libraries 0.22.2
- Legacy LWJGL3 1.4.10; Argentum 1.0.2
- TFL Core, including custom menus, HUD, minimap, world map, camera controls, and color-grade PostChain

## Scope and CI evidence
`tfl-ornithe-ci.yml` validates source metadata and builds Lucerna with Java 25, then checks the JAR structure.
A successful run is NOT proof that shaders render, nor of TFL Core / Argentum / Legacy LWJGL3 runtime compatibility.
Do not tag a stable release or auto-merge based on this workflow alone.

## High-risk compatibility surfaces
1. TFL Core's `WorldDetailsMixin` redirects `ClientWorld.getStarBrightness` during `WorldRenderer.renderSky`. Lucerna injects around the same call in `MixinWorldRenderer`. Audit mixin application order and change TFL to non-destructive modification when necessary.
2. Lucerna intercepts `GameRenderer`, `WorldRenderer`, hand, clouds, weather, block entities, and framebuffers. Verify all mixins with exact Argentum 1.0.2.
3. TFL Core's `OrnitheColors` uses an independent post-processing chain against the main framebuffer. Start with TFL color grading disabled while shaders are enabled, until ownership and GL state restoration are proven.
4. Verify zoom, FOV, freelook, panorama, Paper Doll, resize, resource reload and world/dimension transitions.
5. Lucerna temporarily disables some Argentum graphics features during shaderpack use; verify original values are restored, including on failure and after editing settings.

## Runtime acceptance (must be performed on a physical GPU)
- [ ] Start clean Ornithe Gen2 + OSL + Legacy LWJGL3 + Argentum + Lucerna (no TFL Core)
- [ ] Start with shaders OFF, enter world, change dimension, resize window and restart
- [ ] Enable a simple shaderpack, confirm terrain/shadows/entities/hand/particles
- [ ] Disable shaders, confirm GL state / renderer / Argentum settings restored
- [ ] Add TFL Core and repeat with HUD, custom title/pause, minimap, world map, camera/FOV
- [ ] Compare frame-time p50/p95/p99 with the same scene and settings
- [ ] Add Cera, Argentum Extras, Sarcio, SoundFix individually after core passes
- [ ] Document GPU model, OS, drivers, Java, hashes, shaderpack and crash logs

## Distribution constraints
Retain upstream copyright and LGPL-3.0-only notices, relevant third-party notices and corresponding modified source when required. Argentum binary redistribution is NOT implied by Lucerna's source license. Keep releases prerelease/experimental until native acceptance evidence exists.
