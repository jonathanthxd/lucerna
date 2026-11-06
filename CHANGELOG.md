# Changelog

## 0.1.5-tfl.1 (experimental, not a stable release)

- Fix stale row focus and release handling when shader options rebuild during clicks.
- Bound malformed shader menu column counts and diagnose invalid texture declarations.
- Release shader/program GPU handles on compilation and link failures.
- Add an opt-in native dh_terrain/dh_water VBO path with dedicated depth, render targets and versioned Lucerna-owned API.
- TFL Core adapter, real LOD/GPU validation and Mellow 3.5 testing are still pending. See docs/TFL_FAR_TERRAIN.md and docs/VALIDATION_TFL.md.

## 0.1.4

- Skip shader passes that packs turn off with number options

## 0.1.3

- Fix GL memory leaks when reloading shaders
- Fix depth with a lower render quality
- Fix clouds after a resource reload
- Fix the shader pack version check on OpenGL 4.6
- Fix the option menu after applying a shader pack
- Keep shader pack options when they cannot be read
- Improve performance
- Move the config to lucerna.properties, keybinds reset once

## 0.1.2

- Add LabPBR resource pack support
- Apply shader packs on a worker thread
- Speed up shader reloads
- Improve performance
- Fix rendering issues in many shader packs
- Fix crashes in the shader menu

## 0.1.1

- Fix white sky when holding potions or enchanted items
- Fix water shadows changing with the held item
- Render the sky below 4 chunks

## 0.1.0

- Initial release
