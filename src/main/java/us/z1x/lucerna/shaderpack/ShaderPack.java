package us.z1x.lucerna.shaderpack;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resource.language.I18n;
import org.jetbrains.annotations.Nullable;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.features.FeatureFlags;
import us.z1x.lucerna.gl.texture.InternalTextureFormat;
import us.z1x.lucerna.gl.texture.PixelFormat;
import us.z1x.lucerna.gl.texture.PixelType;
import us.z1x.lucerna.gui.FeatureMissingErrorScreen;
import us.z1x.lucerna.gui.screen.ShaderPackScreen;
import us.z1x.lucerna.shaderpack.include.AbsolutePackPath;
import us.z1x.lucerna.shaderpack.include.IncludeGraph;
import us.z1x.lucerna.shaderpack.include.IncludeProcessor;
import us.z1x.lucerna.shaderpack.include.ShaderPackSourceNames;
import us.z1x.lucerna.shaderpack.option.ProfileSet;
import us.z1x.lucerna.shaderpack.option.ShaderPackOptions;
import us.z1x.lucerna.shaderpack.option.menu.OptionMenuContainer;
import us.z1x.lucerna.shaderpack.option.values.MutableOptionValues;
import us.z1x.lucerna.shaderpack.option.values.OptionValues;
import us.z1x.lucerna.shaderpack.preprocessor.JcppProcessor;
import us.z1x.lucerna.shaderpack.texture.CustomTextureData;
import us.z1x.lucerna.shaderpack.texture.TextureFilteringData;
import us.z1x.lucerna.shaderpack.texture.TextureStage;
import us.z1x.lucerna.uniforms.custom.CustomUniforms;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ShaderPack {
    private static final Gson GSON = new Gson();

    private final ProgramSet base;
    @Nullable
    private final ProgramSet overworld;
    private final ProgramSet nether;
    private final ProgramSet end;

    private final IdMap idMap;
    private final LanguageMap languageMap;
    private final EnumMap<TextureStage, Object2ObjectMap<String, CustomTextureData>> customTextureDataMap = new EnumMap<>(TextureStage.class);
    private final CustomTextureData customNoiseTexture;
    private final ShaderProperties shaderProperties;
    private final CustomUniforms.Builder customUniforms;
    private final ShaderPackOptions shaderPackOptions;
    private final OptionMenuContainer menuContainer;

    private final ProfileSet.ProfileResult profile;
    private final String profileInfo;

    public ShaderPack(Path root, Iterable<StringPair> environmentDefines) throws IOException, IllegalStateException {
        this(root, Collections.emptyMap(), environmentDefines);
    }

    public ShaderPack(Path root, Map<String, String> changedConfigs, Iterable<StringPair> environmentDefines) throws IOException, IllegalStateException {
        Objects.requireNonNull(root);

        ImmutableList.Builder<AbsolutePackPath> starts = ImmutableList.builder();
        ImmutableList<String> potentialFileNames = ShaderPackSourceNames.POTENTIAL_STARTS;

        ShaderPackSourceNames.findPresentSources(starts, root, AbsolutePackPath.fromAbsolutePath("/"),
                potentialFileNames);

        boolean hasWorld0 = ShaderPackSourceNames.findPresentSources(starts, root,
                AbsolutePackPath.fromAbsolutePath("/world0"), potentialFileNames);

        boolean hasNether = ShaderPackSourceNames.findPresentSources(starts, root,
                AbsolutePackPath.fromAbsolutePath("/world-1"), potentialFileNames);

        boolean hasEnd = ShaderPackSourceNames.findPresentSources(starts, root,
                AbsolutePackPath.fromAbsolutePath("/world1"), potentialFileNames);

        IncludeGraph graph = new IncludeGraph(root, starts.build());

        if (!graph.getFailures().isEmpty()) {
            graph.getFailures().forEach((path, error) -> {
                Lucerna.logger.error("{}", error.toString());
            });

            throw new IOException("Failed to resolve some #include directives, see previous messages for details");
        }

        this.languageMap = new LanguageMap(root.resolve("lang"));

        this.shaderPackOptions = new ShaderPackOptions(graph, changedConfigs);
        graph = this.shaderPackOptions.getIncludes();

        Iterable<StringPair> finalEnvironmentDefines = environmentDefines;
        ShaderProperties shaderProperties = loadProperties(root, "shaders.properties")
                .map(source -> new ShaderProperties(source, shaderPackOptions, finalEnvironmentDefines))
                .orElseGet(ShaderProperties::empty);

        this.customUniforms = shaderProperties.getCustomUniforms();

        List<FeatureFlags> invalidFlagList = shaderProperties.getRequiredFeatureFlags().stream().filter(FeatureFlags::isInvalid).map(FeatureFlags::getValue).collect(Collectors.toList());
        List<String> invalidFeatureFlags = invalidFlagList.stream().map(FeatureFlags::getHumanReadableName).collect(Collectors.toList());

        if (!invalidFeatureFlags.isEmpty()) {
            // packs also load on a worker thread
            Minecraft.getInstance().executeTask((Runnable) () -> {
                if (Minecraft.getInstance().screen instanceof ShaderPackScreen) {
                    Minecraft.getInstance().openScreen(new FeatureMissingErrorScreen(Minecraft.getInstance().screen, I18n.translate("lucerna.unsupported.pack"), I18n.translate("lucerna.unsupported.pack.description", FeatureFlags.getInvalidStatus(invalidFlagList), invalidFeatureFlags.stream()
                        .collect(Collectors.joining(", ", ": ", ".")))));
                }
            });
            // reloading here would close the zip this pack is still read from, so let the loader fall back instead
            throw new IllegalStateException("The shader pack needs unsupported features: " + String.join(", ", invalidFeatureFlags));
        }

        // required flags are all usable at this point
        List<String> featureFlags = Stream.concat(shaderProperties.getRequiredFeatureFlags().stream(), shaderProperties.getOptionalFeatureFlags().stream())
            .filter(flag -> !FeatureFlags.isInvalid(flag)).distinct().collect(Collectors.toList());

        if (!featureFlags.isEmpty()) {
            List<StringPair> newEnvDefines = new ArrayList<>();
            environmentDefines.forEach(newEnvDefines::add);
            featureFlags.forEach(flag -> newEnvDefines.add(new StringPair("IRIS_FEATURE_" + flag, "")));
            environmentDefines = ImmutableList.copyOf(newEnvDefines);
        }

        ProfileSet profiles = ProfileSet.fromTree(shaderProperties.getProfiles(), this.shaderPackOptions.getOptionSet());
        this.profile = profiles.scan(this.shaderPackOptions.getOptionSet(), this.shaderPackOptions.getOptionValues());

        List<String> disabledPrograms = new ArrayList<>();
        this.profile.current.ifPresent(profile -> disabledPrograms.addAll(profile.disabledPrograms));
        shaderProperties.getConditionallyEnabledPrograms().forEach((program, shaderOption) -> {
            if (!ProgramCondition.evaluate(shaderOption, this.shaderPackOptions.getOptionValues())) {
                disabledPrograms.add(program);
            }
        });

        this.menuContainer = new OptionMenuContainer(shaderProperties, this.shaderPackOptions, profiles);

        {
            String profileName = getCurrentProfileName();
            OptionValues profileOptions = new MutableOptionValues(
                    this.shaderPackOptions.getOptionSet(), this.profile.current.map(p -> p.optionValues).orElse(new HashMap<>()));

            int userOptionsChanged = this.shaderPackOptions.getOptionValues().getOptionsChanged() - profileOptions.getOptionsChanged();

            this.profileInfo = "Profile: " + profileName + " (+" + userOptionsChanged + " option" + (userOptionsChanged == 1 ? "" : "s") + " changed by user)";
        }

        Lucerna.logger.info(this.profileInfo);

        IncludeProcessor includeProcessor = new IncludeProcessor(graph);

        Iterable<StringPair> finalEnvironmentDefines1 = environmentDefines;
        Function<AbsolutePackPath, String> sourceProvider = (path) -> {
            String pathString = path.getPathString();
            // strip a leading slash and the extension to get the program name
            String programString = pathString.substring(pathString.indexOf("/") == 0 ? 1 : 0, pathString.lastIndexOf("."));

            if (disabledPrograms.contains(programString)) {
                return null;
            }

            ImmutableList<String> lines = includeProcessor.getIncludedFile(path);

            if (lines == null) {
                return null;
            }

            StringBuilder builder = new StringBuilder();

            for (String line : lines) {
                builder.append(line);
                builder.append('\n');
            }

            // environment defines go to the preprocessor, not into the source, to keep error line numbers accurate
            String source = builder.toString();
            source = JcppProcessor.glslPreprocessSource(source, finalEnvironmentDefines1);

            return source;
        };

        this.base = new ProgramSet(AbsolutePackPath.fromAbsolutePath("/"), sourceProvider, shaderProperties, this);

        this.overworld = loadOverrides(hasWorld0, AbsolutePackPath.fromAbsolutePath("/world0"), sourceProvider,
                shaderProperties, this);
        this.nether = loadOverrides(hasNether, AbsolutePackPath.fromAbsolutePath("/world-1"), sourceProvider,
                shaderProperties, this);
        this.end = loadOverrides(hasEnd, AbsolutePackPath.fromAbsolutePath("/world1"), sourceProvider,
                shaderProperties, this);

        this.idMap = new IdMap(root, shaderPackOptions, environmentDefines);

        customNoiseTexture = shaderProperties.getNoiseTexturePath().map(path -> {
            try {
                return readTexture(root, path);
            } catch (IOException e) {
                Lucerna.logger.error("Unable to read the custom noise texture at {}", path, e);

                return null;
            }
        }).orElse(null);

        this.shaderProperties = shaderProperties;

        shaderProperties.getCustomTextures().forEach((textureStage, customTexturePropertiesMap) -> {
            Object2ObjectMap<String, CustomTextureData> innerCustomTextureDataMap = new Object2ObjectOpenHashMap<>();
            customTexturePropertiesMap.forEach((samplerName, path) -> {
                try {
                    innerCustomTextureDataMap.put(samplerName, readTexture(root, path));
                } catch (IOException e) {
                    Lucerna.logger.error("Unable to read the custom texture at {}", path, e);
                }
            });

            customTextureDataMap.put(textureStage, innerCustomTextureDataMap);
        });
    }

    private String getCurrentProfileName() {
        return profile.current.map(p -> p.name).orElse("Custom");
    }

    @Nullable
    private static ProgramSet loadOverrides(boolean has, AbsolutePackPath path, Function<AbsolutePackPath, String> sourceProvider,
                                            ShaderProperties shaderProperties, ShaderPack pack) {
        if (has) {
            return new ProgramSet(path, sourceProvider, shaderProperties, pack);
        }

        return null;
    }

    private static Optional<String> loadProperties(Path shaderPath, String name) {
        String fileContents = readProperties(shaderPath, name);
        if (fileContents == null) {
            return Optional.empty();
        }

        return Optional.of(fileContents);
    }

    public CustomTextureData readTexture(Path root, String value) throws IOException {
        CustomTextureData customTextureData;
        String[] parts = value.split(" ");
        String path = parts[0];

        if (path.contains(":")) {
            String[] location = path.split(":");

            if (location.length > 2) {
                Lucerna.logger.warn("Resource location {} contained more than two parts?", path);
            }

            if (location[0].equals("minecraft") && (location[1].equals("dynamic/lightmap_1") || location[1].equals("dynamic/light_map_1"))) {
                customTextureData = new CustomTextureData.LightmapMarker();
            } else {
                customTextureData = new CustomTextureData.ResourceData(location[0], location[1]);
            }
        } else {
            if (path.startsWith("/")) {
                // fixes packs like continuum 2.0.4 that use a leading slash
                path = path.substring(1);
            }

            boolean blur = false;
            boolean clamp = false;

            String mcMetaPath = path + ".mcmeta";
            Path mcMetaResolvedPath = root.resolve(mcMetaPath);

            if (Files.exists(mcMetaResolvedPath)) {
                try {
                    JsonObject meta = loadMcMeta(mcMetaResolvedPath);
                    if (meta.get("texture") != null) {
                        if (meta.get("texture").getAsJsonObject().get("blur") != null) {
                            blur = meta.get("texture").getAsJsonObject().get("blur").getAsBoolean();
                        }
                        if (meta.get("texture").getAsJsonObject().get("clamp") != null) {
                            clamp = meta.get("texture").getAsJsonObject().get("clamp").getAsBoolean();
                        }
                    }
                } catch (IOException e) {
                    Lucerna.logger.error("Unable to read the custom texture mcmeta at {}, ignoring: {}", mcMetaPath, e);
                }
            }

            byte[] content = Files.readAllBytes(root.resolve(path));
            TextureFilteringData filtering = new TextureFilteringData(blur, clamp);

            if (parts.length > 1) {
                customTextureData = readRawTexture(parts, content, filtering);
            } else {
                customTextureData = new CustomTextureData.PngData(filtering, content);
            }
        }
        return customTextureData;
    }

    // <path> <type> <internal format> <size x> [size y] [size z] <pixel format> <pixel type>
    private static CustomTextureData readRawTexture(String[] parts, byte[] content, TextureFilteringData filtering) throws IOException {
        InternalTextureFormat internalFormat = InternalTextureFormat.fromString(parts[2]).orElseThrow(() -> new IOException("Unknown internal format " + parts[2]));
        PixelFormat pixelFormat = PixelFormat.fromString(parts[parts.length - 2]).orElseThrow(() -> new IOException("Unknown pixel format " + parts[parts.length - 2]));
        PixelType pixelType = PixelType.fromString(parts[parts.length - 1]).orElseThrow(() -> new IOException("Unknown pixel type " + parts[parts.length - 1]));

        return switch (parts[1]) {
            case "TEXTURE_1D" -> new CustomTextureData.RawData1D(content, internalFormat, pixelFormat, pixelType, filtering, Integer.parseInt(parts[3]));
            case "TEXTURE_2D", "TEXTURE_RECTANGLE" -> new CustomTextureData.RawData2D(content, internalFormat, pixelFormat, pixelType, filtering,
                Integer.parseInt(parts[3]), Integer.parseInt(parts[4]));
            case "TEXTURE_3D" -> new CustomTextureData.RawData3D(content, internalFormat, pixelFormat, pixelType, filtering,
                Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), Integer.parseInt(parts[5]));
            default -> throw new IOException("Unknown texture type " + parts[1]);
        };
    }

    private JsonObject loadMcMeta(Path mcMetaPath) throws IOException, JsonParseException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(mcMetaPath), StandardCharsets.UTF_8))) {
            JsonReader jsonReader = new JsonReader(reader);
            return GSON.getAdapter(JsonObject.class).read(jsonReader);
        }
    }

    private static String readProperties(Path shaderPath, String name) {
        try {
            return new String(Files.readAllBytes(shaderPath.resolve(name)), StandardCharsets.ISO_8859_1);
        } catch (NoSuchFileException e) {
            Lucerna.logger.debug("An {} file was not found in the current shaderpack", name);

            return null;
        } catch (IOException e) {
            Lucerna.logger.error("An IOException occurred reading {} from the current shaderpack", name, e);

            return null;
        }
    }

    public ProgramSet getProgramSet(DimensionId dimension) {
        ProgramSet overrides;

        switch (dimension) {
            case OVERWORLD:
                overrides = overworld;
                break;
            case NETHER:
                overrides = nether;
                break;
            case END:
                overrides = end;
                break;
            default:
                throw new IllegalArgumentException("Unknown dimension " + dimension);
        }

        // a dimension override replaces the base programs fully with no merging, so it can drop base passes
        if (overrides != null) {
            return overrides;
        } else {
            return base;
        }
    }

    public IdMap getIdMap() {
        return idMap;
    }

    public EnumMap<TextureStage, Object2ObjectMap<String, CustomTextureData>> getCustomTextureDataMap() {
        return customTextureDataMap;
    }

    public Object2ObjectMap<String, String> getCustomTexturePatching(TextureStage stage) {
        return shaderProperties.getCustomTexturePatching(stage);
    }

    public Optional<CustomTextureData> getCustomNoiseTexture() {
        return Optional.ofNullable(customNoiseTexture);
    }

    public LanguageMap getLanguageMap() {
        return languageMap;
    }

    public ShaderPackOptions getShaderPackOptions() {
        return shaderPackOptions;
    }

    public OptionMenuContainer getMenuContainer() {
        return menuContainer;
    }

    public CustomUniforms.Builder getCustomUniforms() {
        return customUniforms;
    }
}
