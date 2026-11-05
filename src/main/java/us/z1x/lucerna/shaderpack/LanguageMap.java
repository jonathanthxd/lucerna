package us.z1x.lucerna.shaderpack;

import com.google.common.collect.ImmutableMap;
import us.z1x.lucerna.Lucerna;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

public class LanguageMap {
    private final Map<String, Map<String, String>> translationMaps;

    public LanguageMap(Path root) throws IOException {
        this.translationMaps = new HashMap<>();

        if (!Files.exists(root)) {
            return;
        }

        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(path -> !Files.isDirectory(path)).forEach(path -> {
                // packs use optifine names like en_US.lang, so compare in lowercase
                String currentFileName = path.getFileName().toString().toLowerCase(Locale.ROOT);

                if (!currentFileName.endsWith(".lang")) {
                    return;
                }

                String currentLangCode = currentFileName.substring(0, currentFileName.lastIndexOf("."));
                Properties properties = new Properties();

                // lang files are utf-8, not the iso-8859-1 default
                try (InputStreamReader isr = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
                    properties.load(isr);
                } catch (IOException e) {
                    Lucerna.logger.error("Failed to parse shader pack language file {}", path, e);
                }

                ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();

                properties.forEach((key, value) -> builder.put(key.toString(), value.toString()));

                translationMaps.put(currentLangCode, builder.build());
            });
        }
    }

    public Map<String, String> getTranslations(String language) {
        return translationMaps.get(language);
    }
}
