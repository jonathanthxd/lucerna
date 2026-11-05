package us.z1x.lucerna.gui.element.widget;

import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Formatting;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.gui.GuiUtil;
import us.z1x.lucerna.gui.NavigationController;
import us.z1x.lucerna.gui.screen.ShaderPackScreen;
import us.z1x.lucerna.shaderpack.option.BooleanOption;
import us.z1x.lucerna.shaderpack.option.menu.OptionMenuBooleanOptionElement;

public class BooleanElementWidget extends BaseOptionElementWidget<OptionMenuBooleanOptionElement> {
    private final BooleanOption option;

    private boolean appliedValue;
    private boolean value;
    private boolean defaultValue;

    public BooleanElementWidget(OptionMenuBooleanOptionElement element) {
        super(element);

        this.option = element.option;
    }

    @Override
    public void init(ShaderPackScreen screen, NavigationController navigation) {
        super.init(screen, navigation);

        this.appliedValue = this.element.getAppliedOptionValues().getBooleanValueOrDefault(this.option.getName());

        this.value = this.element.getPendingOptionValues().getBooleanValueOrDefault(this.option.getName());

        this.defaultValue = this.element.getAppliedOptionValues().getOptionSet().getBooleanOptions()
            .get(this.option.getName()).getOption().getDefaultValue();

        this.setLabel(GuiUtil.translateOrDefault(this.option.getName(), "option." + this.option.getName()));
    }

    @Override
    public void render(int x, int y, int width, int height, int mouseX, int mouseY, boolean hovered) {
        this.updateRenderParams(width, 28);

        this.renderOptionWithValue(x, y, width, height, hovered);
        this.tryRenderTooltip(mouseX, mouseY, hovered);
    }

    @Override
    protected String createValueLabel() {
        String text = I18n.translate(this.value ? "label.lucerna.true" : "label.lucerna.false");

        // default values get no color, red off and green on made people switch options on for no reason
        if (this.value == this.defaultValue) {
            return text;
        }

        return (this.value ? Formatting.GREEN : Formatting.RED) + text;
    }

    @Override
    public String getCommentKey() {
        return "option." + this.option.getName() + ".comment";
    }

    public String getValue() {
        return Boolean.toString(this.value);
    }

    private void queue() {
        Lucerna.getShaderPackOptionQueue().put(this.option.getName(), this.getValue());
    }

    @Override
    public boolean applyNextValue() {
        this.value = !this.value;
        this.queue();

        return true;
    }

    @Override
    public boolean applyPreviousValue() {
        return this.applyNextValue();
    }

    @Override
    public boolean applyOriginalValue() {
        this.value = this.option.getDefaultValue();
        this.queue();

        return true;
    }

    @Override
    public boolean isValueModified() {
        return this.value != this.appliedValue;
    }
}
