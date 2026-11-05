package us.z1x.lucerna.shaderpack.option.menu;

import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.shaderpack.ShaderProperties;
import us.z1x.lucerna.shaderpack.option.values.MutableOptionValues;
import us.z1x.lucerna.shaderpack.option.values.OptionValues;

public abstract class OptionMenuOptionElement extends OptionMenuElement {
    public final boolean slider;
    public final OptionMenuContainer container;

    private final OptionValues packAppliedValues;

    public OptionMenuOptionElement(String elementString, OptionMenuContainer container, ShaderProperties shaderProperties, OptionValues packAppliedValues) {
        this.slider = shaderProperties.getSliderOptions().contains(elementString);
        this.container = container;
        this.packAppliedValues = packAppliedValues;
    }

    public OptionValues getAppliedOptionValues() {
        return packAppliedValues;
    }

    public OptionValues getPendingOptionValues() {
        MutableOptionValues values = getAppliedOptionValues().mutableCopy();
        values.addAll(Lucerna.getShaderPackOptionQueue());

        return values;
    }
}
