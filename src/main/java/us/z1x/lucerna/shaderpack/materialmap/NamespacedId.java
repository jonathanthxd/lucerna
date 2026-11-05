package us.z1x.lucerna.shaderpack.materialmap;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.item.Item;
import net.minecraft.resource.Identifier;

import java.util.Map;
import java.util.Objects;

public class NamespacedId {
    private static final Map<Item, NamespacedId> ITEMS = new Reference2ObjectOpenHashMap<>();

    private final String namespace;
    private final String name;

    public static NamespacedId of(Item item) {
        return ITEMS.computeIfAbsent(item, key -> {
            Identifier id = Item.REGISTRY.getKey(key);
            return new NamespacedId(id.getNamespace(), id.getPath());
        });
    }

    public NamespacedId(String combined) {
        int colonIdx = combined.indexOf(':');

        if (colonIdx == -1) {
            namespace = "minecraft";
            name = combined;
        } else {
            namespace = combined.substring(0, colonIdx);
            name = combined.substring(colonIdx + 1);
        }
    }

    public NamespacedId(String namespace, String name) {
        this.namespace = Objects.requireNonNull(namespace);
        this.name = Objects.requireNonNull(name);
    }

    public String getNamespace() {
        return namespace;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        NamespacedId that = (NamespacedId) o;

        return namespace.equals(that.namespace) && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, name);
    }

    @Override
    public String toString() {
        return "NamespacedId{" +
                "namespace='" + namespace + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}
