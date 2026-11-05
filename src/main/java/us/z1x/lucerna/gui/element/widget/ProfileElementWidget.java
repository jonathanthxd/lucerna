package us.z1x.lucerna.gui.element.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Formatting;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.gui.GuiUtil;
import us.z1x.lucerna.gui.NavigationController;
import us.z1x.lucerna.gui.screen.ShaderPackScreen;
import us.z1x.lucerna.shaderpack.option.OptionSet;
import us.z1x.lucerna.shaderpack.option.Profile;
import us.z1x.lucerna.shaderpack.option.ProfileSet;
import us.z1x.lucerna.shaderpack.option.menu.OptionMenuProfileElement;
import us.z1x.lucerna.shaderpack.option.values.OptionValues;

import java.util.Optional;

public class ProfileElementWidget extends BaseOptionElementWidget<OptionMenuProfileElement> {
    private Profile next;
    private Profile previous;
    private String profileLabel;

    public ProfileElementWidget(OptionMenuProfileElement element) {
        super(element);
    }

    @Override
    public void init(ShaderPackScreen screen, NavigationController navigation) {
        super.init(screen, navigation);
        this.setLabel(I18n.translate("options.lucerna.profile"));

        ProfileSet profiles = this.element.profiles;
        OptionSet options = this.element.options;
        OptionValues pendingValues = this.element.getPendingOptionValues();

        ProfileSet.ProfileResult result = profiles.scan(options, pendingValues);

        this.next = result.next;
        this.previous = result.previous;
        Optional<String> profileName = result.current.map(p -> p.name);

        this.profileLabel = profileName.map(name -> GuiUtil.translateOrDefault(name, "profile." + name))
                .orElse(Formatting.YELLOW + I18n.translate("options.lucerna.profile.custom"));
    }

    @Override
    public void render(int x, int y, int width, int height, int mouseX, int mouseY, boolean hovered) {
        this.updateRenderParams(width, width - (Minecraft.getInstance().textRenderer.getWidth(this.unmodifiedLabel) + 16));

        this.renderOptionWithValue(x, y, width, height, hovered);
    }

    @Override
    protected String createValueLabel() {
        return this.profileLabel;
    }

    @Override
    public String getCommentKey() {
        return "profile.comment";
    }

    @Override
    public boolean applyNextValue() {
        if (this.next == null) {
            return false;
        }

        Lucerna.queueShaderPackOptionsFromProfile(this.next);

        return true;
    }

    @Override
    public boolean applyPreviousValue() {
        if (this.previous == null) {
            return false;
        }

        Lucerna.queueShaderPackOptionsFromProfile(this.previous);

        return true;
    }

    @Override
    public boolean applyOriginalValue() {
        return false; // resetting options is the way to return to the "default profile"
    }

    @Override
    public boolean isValueModified() {
        return false;
    }
}
