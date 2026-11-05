package us.z1x.lucerna.gui.element.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.TextRenderer;
import us.z1x.lucerna.gui.GuiUtil;
import us.z1x.lucerna.gui.NavigationController;
import us.z1x.lucerna.gui.screen.ShaderPackScreen;
import us.z1x.lucerna.shaderpack.option.menu.OptionMenuLinkElement;

import java.util.Optional;

public class LinkElementWidget extends CommentedElementWidget<OptionMenuLinkElement> {
    private static final String ARROW = ">";

    private final String targetScreenId;
    private final String label;

    private NavigationController navigation;
    private String trimmedLabel = null;
    private boolean isLabelTrimmed = false;

    public LinkElementWidget(OptionMenuLinkElement element) {
        super(element);

        this.targetScreenId = element.targetScreenId;
        this.label = GuiUtil.translateOrDefault(element.targetScreenId, "screen." + element.targetScreenId);
    }

    @Override
    public void init(ShaderPackScreen screen, NavigationController navigation) {
        this.navigation = navigation;
    }

    @Override
    public void render(int x, int y, int width, int height, int mouseX, int mouseY, boolean hovered) {
        GuiUtil.bindWidgetsTexture();
        GuiUtil.drawButton(x, y, width, height, hovered, false);

        TextRenderer font = Minecraft.getInstance().textRenderer;

        int maxLabelWidth = width - 9;

        if (font.getWidth(this.label) > maxLabelWidth) {
            this.isLabelTrimmed = true;
        }

        if (this.trimmedLabel == null) {
            this.trimmedLabel = GuiUtil.shortenText(font, this.label, maxLabelWidth);
        }

        int labelWidth = font.getWidth(this.trimmedLabel);

        font.drawWithShadow(this.trimmedLabel, x + (int)(width * 0.5) - (int)(labelWidth * 0.5) - (int)(0.5 * Math.max(labelWidth - (width - 18), 0)), y + 7, 0xFFFFFF);
        font.draw(ARROW, (x + width) - 9, y + 7, 0xFFFFFF);

        if (hovered && this.isLabelTrimmed) {
            // queued so other elements never draw over the tooltip
            ShaderPackScreen.TOP_LAYER_RENDER_QUEUE.add(() -> GuiUtil.drawTextPanel(font, this.label, mouseX + 2, mouseY - 16));
        }
    }

    @Override
    public boolean mouseClicked(int mx, int my, int button) {
        if (button == 0) {
            this.navigation.open(targetScreenId);
            GuiUtil.playButtonClickSound();

            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public Optional<String> getCommentTitle() {
        return Optional.of(this.label);
    }

    @Override
    public Optional<String> getCommentBody() {
        return GuiUtil.getTranslation("screen." + this.targetScreenId + ".comment");
    }
}
