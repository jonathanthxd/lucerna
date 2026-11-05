package us.z1x.lucerna.shaderpack.materialmap;

import org.jetbrains.annotations.NotNull;
import us.z1x.lucerna.Lucerna;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class BlockEntry {
    private final NamespacedId id;
    private final Map<String, String> propertyPredicates;

    public BlockEntry(NamespacedId id, Map<String, String> propertyPredicates) {
        this.id = id;
        this.propertyPredicates = propertyPredicates;
    }

    @NotNull
    public static final String LEGACY_METADATA = "<metadata>";

    private static boolean isLegacyId(String part) {
        return !part.isEmpty() && part.chars().allMatch(Character::isDigit);
    }

    private static boolean isLegacyMetadata(String part) {
        return !part.isEmpty() && part.chars().allMatch(c -> Character.isDigit(c) || c == ',');
    }

    public static BlockEntry parse(@NotNull String entry) {
        if (entry.isEmpty()) {
            throw new IllegalArgumentException("Called BlockEntry::parse with an empty string");
        }

        // never empty since the entry is non-empty
        String[] splitStates = entry.split(":");

        if (splitStates.length == 1) {
            return new BlockEntry(new NamespacedId("minecraft", entry), Collections.emptyMap());
        }

        // legacy optifine entries can use numeric block ids and metadata, like "18:0,4,8" or "minecraft:leaves:0"
        if (isLegacyId(splitStates[0]) || splitStates.length > 2 && isLegacyMetadata(splitStates[2])) {
            boolean numeric = isLegacyId(splitStates[0]);
            NamespacedId id = numeric ? new NamespacedId("minecraft", splitStates[0]) : new NamespacedId(splitStates[0], splitStates[1]);
            Map<String, String> map = new HashMap<>();
            int metadataIndex = numeric ? 1 : 2;

            if (splitStates.length > metadataIndex) {
                map.put(LEGACY_METADATA, splitStates[metadataIndex]);
            }

            return new BlockEntry(id, map);
        }

        if (splitStates.length == 2 && !splitStates[1].contains("=")) {
            return new BlockEntry(new NamespacedId(splitStates[0], splitStates[1]), Collections.emptyMap());
        }

        int statesStart;
        NamespacedId id;

        if (splitStates[1].contains("=")) {
            // form "tall_grass:half=upper"
            statesStart = 1;
            id = new NamespacedId("minecraft", splitStates[0]);
        } else {
            // form "minecraft:tall_grass:half=upper"
            statesStart = 2;
            id = new NamespacedId(splitStates[0], splitStates[1]);
        }

        // states act as a filter, so "lantern:hanging=false" must also match the waterlogged variant
        Map<String, String> map = new HashMap<>();

        for (int index = statesStart; index < splitStates.length; index++) {
            String[] propertyParts = splitStates[index].split("=");

            if (propertyParts.length != 2) {
                Lucerna.logger.warn("Warning: the block ID map entry \"{}\" could not be fully parsed:", entry);
                Lucerna.logger.warn("- Block state property filters must be of the form \"key=value\", but {} is not of that form!", splitStates[index]);

                continue;
            }

            map.put(propertyParts[0], propertyParts[1]);
        }

        return new BlockEntry(id, map);
    }

    public NamespacedId getId() {
        return id;
    }

    public Map<String, String> getPropertyPredicates() {
        return propertyPredicates;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BlockEntry that = (BlockEntry) o;
        return Objects.equals(id, that.id) && Objects.equals(propertyPredicates, that.propertyPredicates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, propertyPredicates);
    }
}
