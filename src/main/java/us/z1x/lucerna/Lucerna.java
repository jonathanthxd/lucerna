package us.z1x.lucerna;

import com.google.common.base.Throwables;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.minecraft.client.Minecraft;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Formatting;
import net.minecraft.text.Style;
import net.minecraft.text.TranslatableText;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;
import us.z1x.lucerna.compat.argentum.ArgentumCompat;
import us.z1x.lucerna.config.LucernaConfig;
import us.z1x.lucerna.gl.GLDebug;
import us.z1x.lucerna.gl.LucernaRenderSystem;
import us.z1x.lucerna.gl.program.ProgramPool;
import us.z1x.lucerna.gl.shader.StandardMacros;
import us.z1x.lucerna.gui.screen.ShaderPackScreen;
import us.z1x.lucerna.pipeline.DeferredWorldRenderingPipeline;
import us.z1x.lucerna.pipeline.FixedFunctionWorldRenderingPipeline;
import us.z1x.lucerna.pipeline.PipelineManager;
import us.z1x.lucerna.pipeline.PipelineSources;
import us.z1x.lucerna.pipeline.WorldRenderingPipeline;
import us.z1x.lucerna.pipeline.transform.PatchShaderType;
import us.z1x.lucerna.shaderpack.DimensionId;
import us.z1x.lucerna.shaderpack.ProgramSet;
import us.z1x.lucerna.shaderpack.ShaderPack;
import us.z1x.lucerna.shaderpack.StringPair;
import us.z1x.lucerna.shaderpack.discovery.ShaderpackDirectoryManager;
import us.z1x.lucerna.shaderpack.option.Profile;
import us.z1x.lucerna.shaderpack.option.values.MutableOptionValues;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import java.util.zip.ZipError;
import java.util.zip.ZipException;

public class Lucerna {
    public static final String MODID = "lucerna";

    public static final String MODNAME = "Lucerna";

    public static final Logger logger = LogManager.getLogger(MODNAME);

    private static CompletableFuture<PreparedPack> pendingReload;
    private static PreparedPack submittedPack;

    private static Path shaderpacksDirectory;
    private static ShaderpackDirectoryManager shaderpacksDirectoryManager;

    private static ShaderPack currentPack;
    private static String currentPackName;
    private static boolean initialized;

    private static PipelineManager pipelineManager;
    private static LucernaConfig lucernaConfig;
    private static FileSystem zipFileSystem;
    private static KeyBinding reloadKeybind;
    private static KeyBinding toggleShadersKeybind;
    private static KeyBinding shaderpackScreenKeybind;

    private static final Map<String, String> shaderPackOptionQueue = new HashMap<>();
    // forces default option values on the next reload, more reliable than queueing the defaults for repairing a broken config
    private static boolean resetShaderPackOptions = false;

    private static Version lucernaVersion;
    private static boolean fallback;

    // called right before the options load, so key bindings can be added here, opengl is not available yet
    public static KeyBinding[] onEarlyInitialize() {
        ModContainer mod = FabricLoader.getInstance().getModContainer(MODID)
                .orElseThrow(() -> new IllegalStateException("Couldn't find the mod container for Lucerna"));

        lucernaVersion = mod.getMetadata().getVersion();

        try {
            if (!Files.exists(getShaderpacksDirectory())) {
                Files.createDirectories(getShaderpacksDirectory());
            }
        } catch (IOException e) {
            logger.warn("Failed to create the shaderpacks directory!", e);
        }

        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path configPath = configDir.resolve("lucerna.properties");
        try {
            if (Files.exists(configDir.resolve("iris.properties")) && !Files.exists(configPath)) {
                Files.move(configDir.resolve("iris.properties"), configPath);
            }
        } catch (IOException e) {
            logger.warn("Failed to migrate iris.properties", e);
        }

        lucernaConfig = new LucernaConfig(configPath);

        try {
            lucernaConfig.initialize();
        } catch (IOException e) {
            logger.error("Failed to initialize Lucerna configuration, default values will be used instead", e);
        }

        reloadKeybind = new KeyBinding("lucerna.keybind.reload", Keyboard.KEY_R, "lucerna.keybinds");
        toggleShadersKeybind = new KeyBinding("lucerna.keybind.toggleShaders", Keyboard.KEY_K, "lucerna.keybinds");
        shaderpackScreenKeybind = new KeyBinding("lucerna.keybind.shaderPackSelection", Keyboard.KEY_O, "lucerna.keybinds");

        initialized = true;

        return new KeyBinding[] { reloadKeybind, toggleShadersKeybind, shaderpackScreenKeybind };
    }

    public static void onRenderSystemInit() {
        if (!initialized) {
            Lucerna.logger.warn("Lucerna::onRenderSystemInit was called, but Lucerna::onEarlyInitialize was not called. Trying to avoid a crash but this is an odd state.");
            return;
        }

        LucernaRenderSystem.initRenderer();
        ProgramPool.INSTANCE.init();
        GLDebug.initRenderer();

        setDebug(lucernaConfig.areDebugOptionsEnabled());

        // only load the shader pack once opengl is available
        loadShaderpack();
    }

    public static void onLoadingComplete() {
        if (!initialized) {
            Lucerna.logger.warn("Lucerna::onLoadingComplete was called, but Lucerna::onEarlyInitialize was not called. Trying to avoid a crash but this is an odd state.");
            return;
        }

        // prepare the pipeline here so world loading is not slower, guessing the overworld
        // https://github.com/IrisShaders/Iris/issues/323
        lastDimension = DimensionId.OVERWORLD;
        Lucerna.getPipelineManager().preparePipeline(DimensionId.OVERWORLD);
    }

    public static void handleKeybinds(Minecraft minecraft) {
        if (reloadKeybind.consumeClick()) {
            try {
                reloadInBackground();

                if (minecraft.player != null) {
                    minecraft.player.addMessage(new TranslatableText("lucerna.shaders.reloaded"));
                }

            } catch (Exception e) {
                logger.error("Error while reloading Shaders for Lucerna!", e);

                if (minecraft.player != null) {
                    minecraft.player.addMessage(new TranslatableText("lucerna.shaders.reloaded.failure", Throwables.getRootCause(e).getMessage()).setStyle(new Style().setColor(Formatting.RED)));
                }
            }
        } else if (toggleShadersKeybind.consumeClick()) {
            try {
                toggleShaders(minecraft, !lucernaConfig.areShadersEnabled());
            } catch (Exception e) {
                logger.error("Error while toggling shaders!", e);

                if (minecraft.player != null) {
                    minecraft.player.addMessage(new TranslatableText("lucerna.shaders.toggled.failure", Throwables.getRootCause(e).getMessage()).setStyle(new Style().setColor(Formatting.RED)));
                }
                setShadersDisabled();
                fallback = true;
            }
        } else if (shaderpackScreenKeybind.consumeClick()) {
            minecraft.openScreen(new ShaderPackScreen(null));
        }
    }

    public static void toggleShaders(Minecraft minecraft, boolean enabled) throws IOException {
        lucernaConfig.setShadersEnabled(enabled);
        lucernaConfig.save();

        reload();
        if (minecraft.player != null) {
            minecraft.player.addMessage(enabled ? new TranslatableText("lucerna.shaders.toggled", currentPackName) : new TranslatableText("lucerna.shaders.disabled"));
        }
    }

    public static void loadShaderpack() {
        if (lucernaConfig == null) {
            if (!initialized) {
                throw new IllegalStateException("Lucerna::loadShaderpack was called, but Lucerna::onInitializeClient wasn't" +
                        " called yet. How did this happen?");
            } else {
                throw new NullPointerException("Lucerna.lucernaConfig was null unexpectedly");
            }
        }

        if (!lucernaConfig.areShadersEnabled()) {
            logger.info("Shaders are disabled because enableShaders is set to false in lucerna.properties");

            setShadersDisabled();

            return;
        }

        Optional<String> externalName = lucernaConfig.getShaderPackName();

        if (!externalName.isPresent()) {
            logger.info("Shaders are disabled because no valid shaderpack is selected");

            setShadersDisabled();

            return;
        }

        Map<String, String> queued = new HashMap<>(shaderPackOptionQueue);
        boolean reset = resetShaderPackOptions;

        clearShaderPackOptionQueue();
        resetShaderPackOptions = false;

        LoadedPack loaded = readShaderpack(externalName.get(), queued, reset, StandardMacros.createStandardEnvironmentDefines());

        if (loaded != null) {
            installShaderpack(loaded);
        } else {
            logger.warn("Falling back to normal rendering without shaders because the shaderpack could not be loaded");
            setShadersDisabled();
            fallback = true;
        }
    }

    @Nullable
    private static LoadedPack readShaderpack(String name, Map<String, String> queuedOptions, boolean resetOptions, Iterable<StringPair> environmentDefines) {
        Path shaderPackRoot;
        Path shaderPackConfigTxt;

        try {
            shaderPackRoot = getShaderpacksDirectory().resolve(name);
            shaderPackConfigTxt = getShaderpacksDirectory().resolve(name + ".txt");
        } catch (InvalidPathException e) {
            logger.error("Failed to load the shaderpack \"{}\" because it contains invalid characters in its path", name);

            return null;
        }

        FileSystem zip = null;
        LoadedPack loaded = null;

        try {
            Path shaderPackPath;

            if (shaderPackRoot.toString().endsWith(".zip")) {
                Optional<Path> optionalPath;

                try {
                    zip = FileSystems.newFileSystem(shaderPackRoot, Lucerna.class.getClassLoader());
                    optionalPath = findShadersDirectory(zip);
                } catch (FileSystemNotFoundException | NoSuchFileException e) {
                    logger.error("Failed to load the shaderpack \"{}\" because it does not exist in your shaderpacks folder!", name);

                    return null;
                } catch (ZipException e) {
                    logger.error("The shaderpack \"{}\" appears to be corrupted, please try downloading it again!", name);

                    return null;
                } catch (IOException e) {
                    logger.error("Failed to load the shaderpack \"{}\"!", name, e);

                    return null;
                }

                if (optionalPath.isPresent()) {
                    shaderPackPath = optionalPath.get();
                } else {
                    logger.error("Could not load the shaderpack \"{}\" because it appears to lack a \"shaders\" directory", name);
                    return null;
                }
            } else {
                if (!Files.exists(shaderPackRoot)) {
                    logger.error("Failed to load the shaderpack \"{}\" because it does not exist!", name);
                    return null;
                }

                shaderPackPath = shaderPackRoot.resolve("shaders");
            }

            if (!Files.exists(shaderPackPath)) {
                logger.error("Could not load the shaderpack \"{}\" because it appears to lack a \"shaders\" directory", name);
                return null;
            }

            Optional<Properties> savedOptions = tryReadConfigProperties(shaderPackConfigTxt);

            if (savedOptions.isEmpty()) {
                return null;
            }

            Map<String, String> changedConfigs = (Map<String, String>) (Map) savedOptions.get();

            changedConfigs.putAll(queuedOptions);

            if (resetOptions) {
                changedConfigs.clear();
            }

            try {
                ShaderPack pack = new ShaderPack(shaderPackPath, changedConfigs, environmentDefines);

                MutableOptionValues changedConfigsValues = pack.getShaderPackOptions().getOptionValues().mutableCopy();

                Properties configsToSave = new Properties();
                changedConfigsValues.getBooleanValues().forEach((k, v) -> configsToSave.setProperty(k, Boolean.toString(v)));
                changedConfigsValues.getStringValues().forEach(configsToSave::setProperty);

                tryUpdateConfigPropertiesFile(shaderPackConfigTxt, configsToSave);

                loaded = new LoadedPack(name, pack, zip);
                return loaded;
            } catch (Exception e) {
                logger.error("Failed to load the shaderpack \"{}\"!", name, e);

                return null;
            }
        } finally {
            if (loaded == null && zip != null) {
                try {
                    zip.close();
                } catch (IOException e) {
                    logger.warn("Failed to close the shaderpack \"{}\"", name, e);
                }
            }
        }
    }

    private static void installShaderpack(LoadedPack loaded) {
        currentPack = loaded.pack();
        zipFileSystem = loaded.zip();
        fallback = false;
        currentPackName = loaded.name();
        ArgentumCompat.setShadersActive(true);

        logger.info("Using shaderpack: {}", loaded.name());
    }

    private static Optional<Path> findShadersDirectory(FileSystem zipSystem) throws IOException {
        Path root = zipSystem.getRootDirectories().iterator().next();

        Path potentialShaderDir = zipSystem.getPath("shaders");

        if (Files.exists(potentialShaderDir)) {
            return Optional.of(potentialShaderDir);
        }

        // some packs nest the shaders directory in another folder, for example Trippy-Shaderpack-master.zip/Trippy-Shaderpack-master/shaders
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(path -> path.endsWith("shaders"))
                    .findFirst();
        }
    }

    private static void setShadersDisabled() {
        currentPack = null;
        ArgentumCompat.setShadersActive(false);
        fallback = false;
        currentPackName = "(off)";

        logger.info("Shaders are disabled");
    }

    private static void setDebug(boolean enable) {
        int success = enable ? GLDebug.setupDebugMessageCallback() : 1;

        logger.info("Debug functionality is {}", (enable ? "enabled, logging will be more verbose!" : "disabled."));
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.addMessage(new TranslatableText(success != 0 ? (enable ? "lucerna.shaders.debug.enabled" : "lucerna.shaders.debug.disabled") : "lucerna.shaders.debug.failure"));
            if (success == 2) {
                Minecraft.getInstance().player.addMessage(new TranslatableText("lucerna.shaders.debug.restart"));
            }
        }

        try {
            lucernaConfig.setDebugEnabled(enable);
            lucernaConfig.save();
        } catch (IOException e) {
            logger.error("Failed to save the debug setting", e);
        }
    }

    private static Optional<Properties> tryReadConfigProperties(Path path) {
        Properties properties = new Properties();

        if (Files.exists(path)) {
            try (InputStream is = Files.newInputStream(path)) {
                // optifine specifies iso-8859-1 for config properties, so no utf-8 workaround is needed
                properties.load(is);
            } catch (IOException e) {
                logger.error("Failed to read the shaderpack options from {}", path, e);
                return Optional.empty();
            }
        }

        return Optional.of(properties);
    }

    private static void tryUpdateConfigPropertiesFile(Path path, Properties properties) {
        try {
            if (properties.isEmpty()) {
                if (Files.exists(path)) {
                    Files.delete(path);
                }

                return;
            }

            try (OutputStream out = Files.newOutputStream(path)) {
                properties.store(out, null);
            }
        } catch (IOException e) {
            logger.warn("Failed to save the shaderpack options to {}", path, e);
        }
    }

    public static boolean isValidShaderpack(Path pack) {
        if (Files.isDirectory(pack)) {
            // the shaderpacks directory itself contains folders with "shaders" folders
            if (pack.equals(getShaderpacksDirectory())) {
                return false;
            }
            try (Stream<Path> stream = Files.walk(pack)) {
                return stream
                        .filter(Files::isDirectory)
                        // a pack named "shaders" is not valid
                        .filter(path -> !path.equals(pack))
                        .anyMatch(path -> path.endsWith("shaders"));
            } catch (IOException ignored) {
            }
        }

        if (pack.toString().endsWith(".zip")) {
            try (FileSystem zipSystem = FileSystems.newFileSystem(pack, Lucerna.class.getClassLoader())) {
                Path root = zipSystem.getRootDirectories().iterator().next();
                try (Stream<Path> stream = Files.walk(root)) {
                    return stream
                            .filter(Files::isDirectory)
                            .anyMatch(path -> path.endsWith("shaders"));
                }
            } catch (ZipError zipError) {
                // java 8 throws ZipError instead of an IOException subclass
                Lucerna.logger.warn("The ZIP at {} is corrupt", pack);
            } catch (IOException ignored) {
            }
        }

        return false;
    }

    public static Map<String, String> getShaderPackOptionQueue() {
        return shaderPackOptionQueue;
    }

    public static void queueShaderPackOptionsFromProfile(Profile profile) {
        getShaderPackOptionQueue().putAll(profile.optionValues);
    }

    // unlike resetShaderPackOptions, this does not need a pack reload
    public static void clearShaderPackOptionQueue() {
        getShaderPackOptionQueue().clear();
    }

    public static void resetShaderPackOptionsOnNextReload() {
        resetShaderPackOptions = true;
    }

    public static boolean shouldResetShaderPackOptionsOnNextReload() {
        return resetShaderPackOptions;
    }

    // keeps the current pack on screen until the new one is linked, so applying a pack never freezes the game
    public static void reloadInBackground() throws IOException {
        Optional<String> name = lucernaConfig.getShaderPackName();

        if (Minecraft.getInstance().world == null || !lucernaConfig.areShadersEnabled() || name.isEmpty()) {
            reload();
            return;
        }

        cancelBackgroundReload();

        Map<String, String> queued = new HashMap<>(shaderPackOptionQueue);
        boolean reset = resetShaderPackOptions;
        Iterable<StringPair> defines = StandardMacros.createStandardEnvironmentDefines();
        DimensionId dimension = getCurrentDimension();

        clearShaderPackOptionQueue();
        resetShaderPackOptions = false;

        pendingReload = CompletableFuture.supplyAsync(() -> {
            LoadedPack loaded = readShaderpack(name.get(), queued, reset, defines);

            return loaded == null ? null : new PreparedPack(loaded, PipelineSources.collect(loaded.pack().getProgramSet(dimension)));
        });
    }

    public static void tickBackgroundReload() {
        if (pendingReload == null || !pendingReload.isDone()) {
            return;
        }

        PreparedPack prepared;

        try {
            prepared = pendingReload.join();
        } catch (CompletionException e) {
            logger.error("Failed to prepare the shader pack in the background", e);
            prepared = null;
        }

        try {
            if (prepared == null) {
                pendingReload = null;
                reload();
            } else if (prepared != submittedPack) {
                submittedPack = prepared;
                ProgramPool.INSTANCE.submit(prepared.sources());
            } else if (ProgramPool.INSTANCE.tick()) {
                swapShaderpack(prepared.pack());
            }
        } catch (IOException e) {
            logger.error("Failed to reload the shader pack", e);
        }
    }

    private static void swapShaderpack(LoadedPack loaded) throws IOException {
        pendingReload = null;
        submittedPack = null;

        boolean hadPack = isShaderPackActive();

        destroyEverything();
        installShaderpack(loaded);
        finishReload(hadPack);
        ProgramPool.INSTANCE.clear();

        if (Minecraft.getInstance().screen instanceof ShaderPackScreen screen) {
            screen.refreshForChangedPack();
        }
    }

    private static void cancelBackgroundReload() {
        if (pendingReload != null) {
            pendingReload.thenAccept(prepared -> {
                if (prepared != null && prepared.pack().zip() != null) {
                    try {
                        prepared.pack().zip().close();
                    } catch (IOException e) {
                        logger.error("Failed to close zip file system?", e);
                    }
                }
            });
            pendingReload = null;
            submittedPack = null;
            ProgramPool.INSTANCE.clear();
        }
    }

    public static void reload() throws IOException {
        cancelBackgroundReload();
        lucernaConfig.initialize();

        boolean hadPack = isShaderPackActive();

        destroyEverything();

        loadShaderpack();
        finishReload(hadPack);
    }

    private static void finishReload(boolean hadPack) {
        // argentum's section manager only gets a shadow pass while a shader pack is loaded
        if (hadPack != isShaderPackActive() && Minecraft.getInstance().worldRenderer != null) {
            Minecraft.getInstance().worldRenderer.reload();
        }

        // the pipeline must be created again right away, see https://github.com/IrisShaders/Iris/issues/1330
        if (Minecraft.getInstance().world != null) {
            Lucerna.getPipelineManager().preparePipeline(Lucerna.getCurrentDimension());
        }
    }

    private static void destroyEverything() {
        currentPack = null;

        getPipelineManager().destroyPipeline();

        // prevents a FileSystemAlreadyExistsException when reloading shader packs
        if (zipFileSystem != null) {
            try {
                zipFileSystem.close();
            } catch (NoSuchFileException e) {
                logger.warn("Failed to close the shaderpack zip when reloading because it was deleted, proceeding anyways.");
            } catch (IOException e) {
                logger.error("Failed to close zip file system?", e);
            }
        }
    }

    public static DimensionId lastDimension = null;

    public static DimensionId getCurrentDimension() {
        ClientWorld world = Minecraft.getInstance().world;

        if (world != null) {
            switch (world.dimension.getId()) {
                case 1:
                    return DimensionId.END;
                case -1:
                    return DimensionId.NETHER;
                default:
                    return DimensionId.OVERWORLD;
            }
        } else {
            // keep the last dimension, or quitting from the nether reloads the shaders on exit and on entry
            return lastDimension;
        }
    }

    private static WorldRenderingPipeline createPipeline(DimensionId dimensionId) {
        if (currentPack == null) {
            return new FixedFunctionWorldRenderingPipeline();
        }

        ProgramSet programs = currentPack.getProgramSet(dimensionId);

        try {
            return new DeferredWorldRenderingPipeline(programs);
        } catch (Exception e) {
            logger.error("Failed to create shader rendering pipeline, disabling shaders!", e);
            fallback = true;
            ArgentumCompat.setShadersActive(false);

            // chunks may already be meshed in the shader vertex format
            if (Minecraft.getInstance().worldRenderer != null) {
                Minecraft.getInstance().worldRenderer.reload();
            }

            return new FixedFunctionWorldRenderingPipeline();
        }
    }

    @NotNull
    public static PipelineManager getPipelineManager() {
        if (pipelineManager == null) {
            pipelineManager = new PipelineManager(Lucerna::createPipeline);
        }

        return pipelineManager;
    }

    @NotNull
    public static Optional<ShaderPack> getCurrentPack() {
        return Optional.ofNullable(currentPack);
    }

    public static String getCurrentPackName() {
        return currentPackName;
    }

    public static LucernaConfig getLucernaConfig() {
        return lucernaConfig;
    }

    public static boolean isShaderPipelineActive() {
        WorldRenderingPipeline pipeline = pipelineManager.getPipelineNullable();
        return pipeline != null && !(pipeline instanceof FixedFunctionWorldRenderingPipeline);
    }

    public static boolean isShaderPackActive() {
        return currentPack != null && !fallback;
    }

    public static boolean isFallback() {
        return fallback;
    }

    public static String getVersion() {
        if (lucernaVersion == null) {
            return "Version info unknown!";
        }

        return lucernaVersion.getFriendlyString();
    }

    public static Path getShaderpacksDirectory() {
        if (shaderpacksDirectory == null) {
            shaderpacksDirectory = FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
        }

        return shaderpacksDirectory;
    }

    public static ShaderpackDirectoryManager getShaderpacksDirectoryManager() {
        if (shaderpacksDirectoryManager == null) {
            shaderpacksDirectoryManager = new ShaderpackDirectoryManager(getShaderpacksDirectory());
        }

        return shaderpacksDirectoryManager;
    }

    private record LoadedPack(String name, ShaderPack pack, @Nullable FileSystem zip) {
    }

    private record PreparedPack(LoadedPack pack, List<Map<PatchShaderType, String>> sources) {
    }
}
