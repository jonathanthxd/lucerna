package us.z1x.lucerna.gui.element.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Formatting;
import org.jetbrains.annotations.Nullable;
import us.z1x.lucerna.gui.GuiUtil;
import us.z1x.lucerna.gui.NavigationController;
import us.z1x.lucerna.gui.screen.ShaderPackScreen;
import us.z1x.lucerna.shaderpack.option.menu.OptionMenuElement;

import java.util.Optional;

public abstract class BaseOptionElementWidget<T extends OptionMenuElement> extends CommentedElementWidget<T> {
    protected static final String DIVIDER = ": ";

    protected String unmodifiedLabel;
    protected ShaderPackScreen screen;
    protected NavigationController navigation;
    private String label;

    protected String trimmedLabel;
    protected String valueLabel;

    private boolean isLabelTrimmed;
    private int maxLabelWidth;
    private int valueSectionWidth;

    public BaseOptionElementWidget(T element) {
        super(element);
    }

    @Override
    public void init(ShaderPackScreen screen, NavigationController navigation) {
        this.screen = screen;
        this.navigation = navigation;
        this.valueLabel = null;
        this.trimmedLabel = null;
    }

    protected final void setLabel(String label) {
        this.label = label + DIVIDER;
        this.unmodifiedLabel = label;
    }

    protected final void updateRenderParams(int width, int minValueSectionWidth) {
        if (this.valueLabel == null) {
            this.valueLabel = createValueLabel();
        }

        TextRenderer font = Minecraft.getInstance().textRenderer;
        this.valueSectionWidth = Math.max(minValueSectionWidth, font.getWidth(this.valueLabel) + 8);

        this.maxLabelWidth = (width - 8) - this.valueSectionWidth;

        if (this.trimmedLabel == null || font.getWidth(this.label) > this.maxLabelWidth != isLabelTrimmed) {
            updateLabels();
        }

        this.isLabelTrimmed = font.getWidth(this.label) > this.maxLabelWidth;
    }

    protected final void renderOptionWithValue(int x, int y, int width, int height, boolean hovered, float sliderPosition, int sliderWidth) {
        GuiUtil.bindWidgetsTexture();

        GuiUtil.drawButton(x, y, width, height, hovered, false);

        GuiUtil.drawButton((x + width) - (this.valueSectionWidth + 2), y + 2, this.valueSectionWidth, height - 4, false, true);

        if (sliderPosition >= 0) {
            int sliderSpace = (this.valueSectionWidth - 4) - sliderWidth;

            int sliderPos = ((x + width) - this.valueSectionWidth) + (int)(sliderPosition * sliderSpace);

            GuiUtil.drawButton(sliderPos, y + 4, sliderWidth, height - 8, false, false);
        }

        TextRenderer font = Minecraft.getInstance().textRenderer;

        font.drawWithShadow(this.trimmedLabel, x + 6, y + 7, this.isValueModified() ? 0xFFC94A : 0xFFFFFF);
        font.drawWithShadow(this.valueLabel, (x + (width - 2)) - (int)(this.valueSectionWidth * 0.5) - (int)(font.getWidth(this.valueLabel) * 0.5), y + 7, 0xFFFFFF);
    }

    protected final void renderOptionWithValue(int x, int y, int width, int height, boolean hovered) {
        this.renderOptionWithValue(x, y, width, height, hovered, -1, 0);
    }

    protected final void tryRenderTooltip(int mouseX, int mouseY, boolean hovered) {
        if (Screen.isShiftDown()) {
            renderTooltip(Formatting.GREEN + I18n.translate("options.lucerna.setToDefault"), mouseX, mouseY, hovered);
        } else if (this.isLabelTrimmed && !this.screen.isDisplayingComment()) {
            renderTooltip(this.unmodifiedLabel, mouseX, mouseY, hovered);
        }
    }

    protected final void renderTooltip(String text, int mouseX, int mouseY, boolean hovered) {
        if (hovered) {
            ShaderPackScreen.TOP_LAYER_RENDER_QUEUE.add(() -> GuiUtil.drawTextPanel(Minecraft.getInstance().textRenderer, text, mouseX + 2, mouseY - 16));
        }
    }

    protected final void updateLabels() {
        this.trimmedLabel = GuiUtil.shortenText(Minecraft.getInstance().textRenderer, this.label, this.maxLabelWidth);
        this.valueLabel = createValueLabel();
    }

    protected abstract String createValueLabel();

    public abstract boolean applyNextValue();

    public abstract boolean applyPreviousValue();

    public abstract boolean applyOriginalValue();

    public abstract boolean isValueModified();

    public abstract @Nullable String getCommentKey();

    @Override
    public Optional<String> getCommentTitle() {
        return Optional.of(this.unmodifiedLabel);
    }

    @Override
    public Optional<String> getCommentBody() {
        return Optional.ofNullable(getCommentKey()).flatMap(GuiUtil::getTranslation);
    }

    @Override
    public boolean mouseClicked(int mx, int my, int button) {
        if (button == 0 || button == 1) {
            boolean refresh = false;

            if (Screen.isShiftDown()) {
                refresh = applyOriginalValue();
            }
            if (!refresh) {
                if (button == 0) {
                    refresh = applyNextValue();
                } else {
                    refresh = applyPreviousValue();
                }
            }

            if (refresh) {
                this.navigation.refresh();
            }

            GuiUtil.playButtonClickSound();

            return true;
        }
        return super.mouseClicked(mx, my, button);
    }
}
