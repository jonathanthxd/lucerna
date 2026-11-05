package us.z1x.lucerna.shaderpack.discovery;

import us.z1x.lucerna.Lucerna;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ShaderpackDirectoryManager {
    private final Path root;

    public ShaderpackDirectoryManager(Path root) {
        this.root = root;
    }

    public Collection<String> enumerate() throws IOException {
        // not all systems sort directory listings, sort case-insensitively then naturally
        // chat formatting is ignored since some packs use it in their file names
        Comparator<String> baseComparator = String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());
        Comparator<String> comparator = (a, b) -> {
            a = removeFormatting(a);
            b = removeFormatting(b);

            return baseComparator.compare(a, b);
        };

        try (Stream<Path> list = Files.list(root)) {
            return list.filter(Lucerna::isValidShaderpack)
                .map(path -> path.getFileName().toString())
                .sorted(comparator).collect(Collectors.toList());
        }
    }

    private static String removeFormatting(String formatted) {
        char[] original = formatted.toCharArray();
        char[] cleaned = new char[original.length];
        int c = 0;

        for (int i = 0; i < original.length; i++) {
            if (original[i] == '\u00a7') {
                i++;
            } else {
                cleaned[c++] = original[i];
            }
        }

        return new String(cleaned, 0, c);
    }

    public URI getDirectoryUri() {
        return root.toUri();
    }
}
