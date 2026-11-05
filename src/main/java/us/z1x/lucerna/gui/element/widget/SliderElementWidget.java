package us.z1x.lucerna.gui.element.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Formatting;
import net.minecraft.util.math.MathHelper;
import us.z1x.lucerna.gui.GuiUtil;
import us.z1x.lucerna.shaderpack.option.menu.OptionMenuStringOptionElement;

public class SliderElementWidget extends StringElementWidget {
    private static final int PREVIEW_SLIDER_WIDTH = 4;
    private static final int ACTIVE_SLIDER_WIDTH = 6;

    private boolean mouseDown = false;

    public SliderElementWidget(OptionMenuStringOptionElement element) {
        super(element);
    }

    @Override
    public void render(int x, int y, int width, int height, int mouseX, int mouseY, boolean hovered) {
        this.updateRenderParams(width, 35);

        if (!hovered) {
            this.renderOptionWithValue(x, y, width, height, false, (float)valueIndex / (valueCount - 1), PREVIEW_SLIDER_WIDTH);
        } else {
            this.renderSlider(x, y, width, height);
        }

        if (Screen.isShiftDown()) {
            renderTooltip(Formatting.GREEN + I18n.translate("options.lucerna.setToDefault"), mouseX, mouseY, hovered);
        } else if (!this.screen.isDisplayingComment()) {
            renderTooltip(this.unmodifiedLabel, mouseX, mouseY, hovered);
        }

        if (this.mouseDown) {
            if (!hovered) {
                this.onReleased();
            }

            whileDragging(x, width, mouseX);
        }
    }

    private void renderSlider(int x, int y, int width, int height) {
        GuiUtil.bindWidgetsTexture();

        GuiUtil.drawButton(x, y, width, height, false, false);
        GuiUtil.drawButton(x + 2, y + 2, width - 4, height - 4, false, true);

        int sliderSpace = (width - 8) - ACTIVE_SLIDER_WIDTH;
        int sliderPos = (x + 4) + (int)(((float)valueIndex / (valueCount - 1)) * sliderSpace);
        GuiUtil.drawButton(sliderPos, y + 4, ACTIVE_SLIDER_WIDTH, height - 8, this.mouseDown, false);

        TextRenderer font = Minecraft.getInstance().textRenderer;
        font.drawWithShadow(this.valueLabel, (int)(x + (width * 0.5)) - (int)(font.getWidth(this.valueLabel) * 0.5), y + 7, 0xFFFFFF);
    }

    private void whileDragging(int x, int width, int mouseX) {
        float mousePositionAcrossWidget = MathHelper.clamp((float)(mouseX - (x + 4)) / (width - 8), 0, 1);

        int newValueIndex = Math.min(valueCount - 1, (int)(mousePositionAcrossWidget * valueCount));

        if (valueIndex != newValueIndex) {
            this.valueIndex = newValueIndex;

            this.updateLabels();
        }
    }

    private void onReleased() {
        mouseDown = false;

        this.queue();
        this.navigation.refresh();

        GuiUtil.playButtonClickSound();
    }

    @Override
    public boolean mouseClicked(int mx, int my, int button) {
        if (button == 0) {
            if (Screen.isShiftDown()) {
                if (this.applyOriginalValue()) {
                    this.navigation.refresh();
                }
                GuiUtil.playButtonClickSound();

                return true;
            }

            mouseDown = true;
            GuiUtil.playButtonClickSound();

            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(int mx, int my, int button) {
        if (button == 0) {
            this.onReleased();

            return true;
        }
        return super.mouseReleased(mx, my, button);
    }
}
