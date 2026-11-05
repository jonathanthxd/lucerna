package us.z1x.lucerna.config;

import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.gui.option.LucernaVideoSettings;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

public class LucernaConfig {
    private static final String COMMENT =
        "This file stores configuration options for Lucerna, such as the currently active shaderpack";

    private String shaderPackName;

    private boolean enableShaders;

    // gives detailed opengl error output at a performance cost
    private boolean enableDebugOptions;

    private boolean disableUpdateMessage;

    private final Path propertiesPath;

    public LucernaConfig(Path propertiesPath) {
        shaderPackName = null;
        enableShaders = true;
        enableDebugOptions = false;
        disableUpdateMessage = false;
        this.propertiesPath = propertiesPath;
    }

    public void initialize() throws IOException {
        load();
        if (!Files.exists(propertiesPath)) {
            save();
        }
    }

    public Optional<String> getShaderPackName() {
        return Optional.ofNullable(shaderPackName);
    }

    public void setShaderPackName(String name) {
        if (name == null || name.equals("(internal)") || name.isEmpty()) {
            this.shaderPackName = null;
        } else {
            this.shaderPackName = name;
        }
    }

    public boolean areShadersEnabled() {
        return enableShaders;
    }

    public boolean areDebugOptionsEnabled() {
        return enableDebugOptions;
    }

    public void setDebugEnabled(boolean enabled) {
        enableDebugOptions = enabled;
    }

    public void setShadersEnabled(boolean enabled) {
        this.enableShaders = enabled;
    }

    public void load() throws IOException {
        if (!Files.exists(propertiesPath)) {
            return;
        }

        Properties properties = new Properties();
        // properties files use iso-8859-1 with unicode escapes
        try (InputStream is = Files.newInputStream(propertiesPath)) {
            properties.load(is);
        }
        shaderPackName = properties.getProperty("shaderPack");
        enableShaders = !"false".equals(properties.getProperty("enableShaders"));
        enableDebugOptions = "true".equals(properties.getProperty("enableDebugOptions"));
        disableUpdateMessage = "true".equals(properties.getProperty("disableUpdateMessage"));
        try {
            LucernaVideoSettings.shadowDistance = Integer.parseInt(properties.getProperty("maxShadowRenderDistance", "32"));
            LucernaVideoSettings.renderQuality = Float.parseFloat(properties.getProperty("renderQuality", "1.0"));
            LucernaVideoSettings.shadowQuality = Float.parseFloat(properties.getProperty("shadowQuality", "1.0"));
        } catch (NumberFormatException e) {
            Lucerna.logger.error("Video settings reset; a value is invalid.");
            LucernaVideoSettings.shadowDistance = 32;
            LucernaVideoSettings.renderQuality = 1.0F;
            LucernaVideoSettings.shadowQuality = 1.0F;
            save();
        }

        if (shaderPackName != null) {
            if (shaderPackName.equals("(internal)") || shaderPackName.isEmpty()) {
                shaderPackName = null;
            }
        }
    }

    public void save() throws IOException {
        Properties properties = new Properties();
        properties.setProperty("shaderPack", getShaderPackName().orElse(""));
        properties.setProperty("enableShaders", enableShaders ? "true" : "false");
        properties.setProperty("enableDebugOptions", enableDebugOptions ? "true" : "false");
        properties.setProperty("disableUpdateMessage", disableUpdateMessage ? "true" : "false");
        properties.setProperty("maxShadowRenderDistance", String.valueOf(LucernaVideoSettings.shadowDistance));
        properties.setProperty("renderQuality", String.valueOf(LucernaVideoSettings.renderQuality));
        properties.setProperty("shadowQuality", String.valueOf(LucernaVideoSettings.shadowQuality));
        // properties files use iso-8859-1 with unicode escapes
        try (OutputStream os = Files.newOutputStream(propertiesPath)) {
            properties.store(os, COMMENT);
        }
    }
}
