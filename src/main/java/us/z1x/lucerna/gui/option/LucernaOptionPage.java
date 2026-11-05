package us.z1x.lucerna.gui.option;

import org.embeddedt.embeddium.impl.gui.framework.TextComponent;
import org.taumc.celeritas.api.options.OptionIdentifier;
import org.taumc.celeritas.api.options.control.ControlValueFormatter;
import org.taumc.celeritas.api.options.control.SliderControl;
import org.taumc.celeritas.api.options.structure.OptionGroup;
import org.taumc.celeritas.api.options.structure.OptionImpact;
import org.taumc.celeritas.api.options.structure.OptionImpl;
import org.taumc.celeritas.api.options.structure.OptionPage;
import org.taumc.celeritas.api.options.structure.OptionStorage;
import us.z1x.lucerna.Lucerna;

import java.io.IOException;
import java.util.Collections;

public final class LucernaOptionPage {
    private static final OptionStorage<Void> STORAGE = new OptionStorage<Void>() {
        @Override
        public Void getData() {
            return null;
        }

        @Override
        public void save() {
            try {
                Lucerna.getLucernaConfig().save();
                Lucerna.reloadInBackground();
            } catch (IOException e) {
                Lucerna.logger.error("Failed to apply the shader video settings", e);
            }
        }
    };

    private LucernaOptionPage() {
    }

    public static OptionPage create() {
        OptionGroup group = OptionGroup.createBuilder()
            .setId(OptionIdentifier.create(Lucerna.MODID, "shaders"))
            .add(OptionImpl.createBuilder(int.class, STORAGE)
                .setId(OptionIdentifier.create(Lucerna.MODID, "shadow_distance", int.class))
                .setName(TextComponent.translatable("options.lucerna.shadowDistance"))
                .setTooltip(TextComponent.translatable("options.lucerna.shadowDistance.tooltip"))
                .setControl(option -> new SliderControl(option, 0, 32, 1, value -> TextComponent.translatable("options.chunks", value)))
                .setBinding((data, value) -> LucernaVideoSettings.shadowDistance = value, data -> LucernaVideoSettings.shadowDistance)
                .setEnabledPredicate(LucernaVideoSettings::isShadowDistanceSliderEnabled)
                .setImpact(OptionImpact.HIGH)
                .build())
            .add(OptionImpl.createBuilder(int.class, STORAGE)
                .setId(OptionIdentifier.create(Lucerna.MODID, "render_quality", int.class))
                .setName(TextComponent.translatable("options.lucerna.renderQuality"))
                .setTooltip(TextComponent.translatable("options.lucerna.renderQuality.tooltip"))
                .setControl(option -> new SliderControl(option, 25, 200, 5, ControlValueFormatter.percentage()))
                .setBinding((data, value) -> LucernaVideoSettings.renderQuality = value / 100.0F, data -> Math.round(LucernaVideoSettings.renderQuality * 100.0F))
                .setImpact(OptionImpact.HIGH)
                .build())
            .add(OptionImpl.createBuilder(int.class, STORAGE)
                .setId(OptionIdentifier.create(Lucerna.MODID, "shadow_quality", int.class))
                .setName(TextComponent.translatable("options.lucerna.shadowQuality"))
                .setTooltip(TextComponent.translatable("options.lucerna.shadowQuality.tooltip"))
                .setControl(option -> new SliderControl(option, 25, 200, 5, ControlValueFormatter.percentage()))
                .setBinding((data, value) -> LucernaVideoSettings.shadowQuality = value / 100.0F, data -> Math.round(LucernaVideoSettings.shadowQuality * 100.0F))
                .setImpact(OptionImpact.HIGH)
                .build())
            .build();

        return new OptionPage(OptionIdentifier.create(Lucerna.MODID, "shaders"), TextComponent.translatable("options.lucerna.shaders"), Collections.singletonList(group));
    }
}
