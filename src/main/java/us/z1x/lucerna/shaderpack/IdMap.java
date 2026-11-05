package us.z1x.lucerna.shaderpack;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.shaderpack.materialmap.BlockEntry;
import us.z1x.lucerna.shaderpack.materialmap.BlockRenderType;
import us.z1x.lucerna.shaderpack.materialmap.NamespacedId;
import us.z1x.lucerna.shaderpack.option.ShaderPackOptions;
import us.z1x.lucerna.shaderpack.preprocessor.PropertiesPreprocessor;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

public class IdMap {
    private final Object2IntMap<NamespacedId> itemIdMap;

    private final Object2IntMap<NamespacedId> entityIdMap;

    private Int2ObjectMap<List<BlockEntry>> blockPropertiesMap;

    private Map<NamespacedId, BlockRenderType> blockRenderTypeMap;

    IdMap(Path shaderPath, ShaderPackOptions shaderPackOptions, Iterable<StringPair> environmentDefines) {
        itemIdMap = loadProperties(shaderPath, "item.properties", shaderPackOptions, environmentDefines)
            .map(IdMap::parseItemIdMap).orElse(Object2IntMaps.emptyMap());

        entityIdMap = loadProperties(shaderPath, "entity.properties", shaderPackOptions, environmentDefines)
            .map(IdMap::parseEntityIdMap).orElse(Object2IntMaps.emptyMap());

        loadProperties(shaderPath, "block.properties", shaderPackOptions, environmentDefines).ifPresent(blockProperties -> {
            blockPropertiesMap = parseBlockMap(blockProperties, "block.", "block.properties");
            blockRenderTypeMap = parseRenderTypeMap(blockProperties, "layer.", "block.properties");
        });

        if (blockPropertiesMap == null) {
            blockPropertiesMap = new Int2ObjectOpenHashMap<>();
            LegacyIdMap.addLegacyValues(blockPropertiesMap);
        }

        if (blockRenderTypeMap == null) {
            blockRenderTypeMap = Collections.emptyMap();
        }
    }

    private static Optional<Properties> loadProperties(Path shaderPath, String name, ShaderPackOptions shaderPackOptions,
                                                       Iterable<StringPair> environmentDefines) {
        String fileContents = readProperties(shaderPath, name);
        if (fileContents == null) {
            return Optional.empty();
        }

        String processed = PropertiesPreprocessor.preprocessSource(fileContents, shaderPackOptions, environmentDefines);

        StringReader propertiesReader = new StringReader(processed);

        // property order matters, see https://github.com/IrisShaders/Iris/issues/1327 and putIfAbsent in BlockMaterialMapping
        Properties properties = new OrderBackedProperties();
        try {
            properties.load(propertiesReader);
        } catch (IOException e) {
            Lucerna.logger.error("Error loading {} at {}", name, shaderPath, e);

            return Optional.empty();
        }

        return Optional.of(properties);
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

    private static Object2IntMap<NamespacedId> parseItemIdMap(Properties properties) {
        return parseIdMap(properties, "item.", "item.properties");
    }

    private static Object2IntMap<NamespacedId> parseEntityIdMap(Properties properties) {
        return parseIdMap(properties, "entity.", "entity.properties");
    }

    private static Object2IntMap<NamespacedId> parseIdMap(Properties properties, String keyPrefix, String fileName) {
        Object2IntMap<NamespacedId> idMap = new Object2IntOpenHashMap<>();
        idMap.defaultReturnValue(-1);

        properties.forEach((keyObject, valueObject) -> {
            String key = (String) keyObject;
            String value = (String) valueObject;

            if (!key.startsWith(keyPrefix)) {
                return;
            }

            int intId;

            try {
                intId = Integer.parseInt(key.substring(keyPrefix.length()));
            } catch (NumberFormatException e) {
                Lucerna.logger.warn("Failed to parse line in {}: invalid key {}", fileName, key);
                return;
            }

            for (String part : value.split("\\s+")) {
                if (part.contains("=")) {
                    Lucerna.logger.warn("Failed to parse an ResourceLocation in {} for the key {}: state properties are currently not supported: {}", fileName, key, part);
                    continue;
                }

                // NamespacedId does not validate, the consumer must
                idMap.put(new NamespacedId(part), intId);
            }
        });

        return Object2IntMaps.unmodifiable(idMap);
    }

    private static Int2ObjectMap<List<BlockEntry>> parseBlockMap(Properties properties, String keyPrefix, String fileName) {
        Int2ObjectMap<List<BlockEntry>> entriesById = new Int2ObjectOpenHashMap<>();

        properties.forEach((keyObject, valueObject) -> {
            String key = (String) keyObject;
            String value = (String) valueObject;

            if (!key.startsWith(keyPrefix)) {
                return;
            }

            int intId;

            try {
                intId = Integer.parseInt(key.substring(keyPrefix.length()));
            } catch (NumberFormatException e) {
                Lucerna.logger.warn("Failed to parse line in {}: invalid key {}", fileName, key);
                return;
            }

            List<BlockEntry> entries = new ArrayList<>();

            for (String part : value.split("\\s+")) {
                if (part.isEmpty()) {
                    continue;
                }

                try {
                    entries.add(BlockEntry.parse(part));
                } catch (Exception e) {
                    Lucerna.logger.warn("Unexpected error while parsing an entry from {} for the key {}:", fileName, key, e);
                }
            }

            entriesById.put(intId, Collections.unmodifiableList(entries));
        });

        return Int2ObjectMaps.unmodifiable(entriesById);
    }

    // used by chocapic v9 and wisdom shaders
    private static Map<NamespacedId, BlockRenderType> parseRenderTypeMap(Properties properties, String keyPrefix, String fileName) {
        Map<NamespacedId, BlockRenderType> overrides = new HashMap<>();

        properties.forEach((keyObject, valueObject) -> {
            String key = (String) keyObject;
            String value = (String) valueObject;

            if (!key.startsWith(keyPrefix)) {
                return;
            }

            String keyWithoutPrefix = key.substring(keyPrefix.length());

            BlockRenderType renderType = BlockRenderType.fromString(keyWithoutPrefix).orElse(null);

            if (renderType == null) {
                Lucerna.logger.warn("Failed to parse line in {}: invalid block render type: {}", fileName, key);
                return;
            }

            for (String part : value.split("\\s+")) {
                // NamespacedId does not validate, the consumer must
                overrides.put(new NamespacedId(part), renderType);
            }
        });

        return overrides;
    }

    public Int2ObjectMap<List<BlockEntry>> getBlockProperties() {
        return blockPropertiesMap;
    }

    public Object2IntFunction<NamespacedId> getItemIdMap() {
        return itemIdMap;
    }

    public Object2IntFunction<NamespacedId> getEntityIdMap() {
        return entityIdMap;
    }

    public Map<NamespacedId, BlockRenderType> getBlockRenderTypeMap() {
        return blockRenderTypeMap;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        IdMap idMap = (IdMap) o;

        return Objects.equals(itemIdMap, idMap.itemIdMap)
                && Objects.equals(entityIdMap, idMap.entityIdMap)
                && Objects.equals(blockPropertiesMap, idMap.blockPropertiesMap)
                && Objects.equals(blockRenderTypeMap, idMap.blockRenderTypeMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemIdMap, entityIdMap, blockPropertiesMap, blockRenderTypeMap);
    }
}
