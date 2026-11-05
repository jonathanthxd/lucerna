package us.z1x.lucerna.features;

import net.minecraft.client.resource.language.I18n;
import org.apache.commons.lang3.text.WordUtils;
import us.z1x.lucerna.gl.LucernaRenderSystem;

import java.util.List;
import java.util.function.BooleanSupplier;

public enum FeatureFlags {
    SEPARATE_HARDWARE_SAMPLERS(() -> true, () -> true),
    PER_BUFFER_BLENDING(() -> true, LucernaRenderSystem::supportsBufferBlending),
    COMPUTE_SHADERS(() -> true, LucernaRenderSystem::supportsCompute),
    ENTITY_TRANSLUCENT(() -> true, () -> true),
    CUSTOM_IMAGES(() -> true, LucernaRenderSystem::supportsCompute),
    SSBO(() -> true, LucernaRenderSystem::supportsCompute),
    UNKNOWN(() -> false, () -> false);

    private final BooleanSupplier lucernaRequirement;
    private final BooleanSupplier hardwareRequirement;

    FeatureFlags(BooleanSupplier lucernaRequirement, BooleanSupplier hardwareRequirement) {
        this.lucernaRequirement = lucernaRequirement;
        this.hardwareRequirement = hardwareRequirement;
    }

    public static String getInvalidStatus(List<FeatureFlags> invalidFeatureFlags) {
        boolean unsupportedHardware = false, unsupportedLucerna = false;
        FeatureFlags[] flags = invalidFeatureFlags.toArray(new FeatureFlags[0]);
        for (FeatureFlags flag : flags) {
            unsupportedLucerna |= !flag.lucernaRequirement.getAsBoolean();
            unsupportedHardware |= !flag.hardwareRequirement.getAsBoolean();
        }

        if (unsupportedLucerna) {
            if (unsupportedHardware) {
                return I18n.translate("lucerna.unsupported.lucernaorpc");
            }

            return I18n.translate("lucerna.unsupported.lucerna");
        } else if (unsupportedHardware) {
            return I18n.translate("lucerna.unsupported.pc");
        } else {
            return null;
        }
    }

    public String getHumanReadableName() {
        return WordUtils.capitalize(name().replace("_", " ").toLowerCase());
    }

    public boolean isUsable() {
        return lucernaRequirement.getAsBoolean() && hardwareRequirement.getAsBoolean();
    }

    public static boolean isInvalid(String name) {
        try {
            return !FeatureFlags.valueOf(name).isUsable();
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    public static FeatureFlags getValue(String value) {
        try {
            return FeatureFlags.valueOf(value);
        } catch (IllegalArgumentException e) {
            return FeatureFlags.UNKNOWN;
        }
    }
}
