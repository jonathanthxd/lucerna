<img src="src/main/resources/assets/lucerna/icon.png" width="96" align="right" alt="Lucerna icon">

# Lucerna

Shader pack loader for Minecraft 1.8.9, built for [OneClient](https://modrinth.com/modpack/oneclient-modpack). Runs OptiFine format shader packs on Ornithe, on top of [Argentum](https://github.com/QuicksilverMC/argentum).

For DLSS and FSR upscaling, add [Amplio](https://modrinth.com/mod/amplio).

Based on [Iris](https://github.com/IrisShaders/Iris) and Celeritas.

## TFL experimental branch

`0.1.5-tfl.2` fixes the DH/composite texture-unit-0 regression in `tfl.1`,
preserves shader menu stability fixes and keeps the DH pipeline opt-in.
See [sampler regression evidence](docs/SAMPLER_REGRESSION_TFL_2.md).
The TFL Core renderer adapter and in-game validation remain pending.
See [integration contract](docs/TFL_FAR_TERRAIN.md) and [validation](docs/VALIDATION_TFL.md).
Do not enable the experimental JVM property with an unmodified TFL Core renderer.
