package us.z1x.lucerna.gui.element;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Formatting;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.gui.GuiUtil;
import us.z1x.lucerna.gui.screen.ShaderPackScreen;

import java.util.Collection;
import java.util.function.Function;

public class ShaderPackSelectionList extends LucernaObjectSelectionList<ShaderPackSelectionList.BaseEntry> {
    private final ShaderPackScreen screen;
    private final TopButtonRowEntry topButtonRow;
    private ShaderPackEntry applied = null;

    public ShaderPackSelectionList(ShaderPackScreen screen, Minecraft client, int width, int height, int top, int bottom, int left, int right) {
        super(client, width, height, top, bottom, left, right, 20);

        this.screen = screen;
        this.topButtonRow = new TopButtonRowEntry(this, Lucerna.getLucernaConfig().areShadersEnabled());

        refresh();
    }

    @Override
    public int getRowWidth() {
        return Math.min(308, width - 50);
    }

    @Override
    protected void renderList(int x, int y, int mouseX, int mouseY) {
        super.renderList(x, y + 2, mouseX, mouseY);
    }

    public void refresh() {
        this.clearEntries();

        Collection<String> names;

        try {
            names = Lucerna.getShaderpacksDirectoryManager().enumerate();
        } catch (Throwable e) {
            Lucerna.logger.error("Error reading files while constructing selection UI", e);

            // untranslated on purpose, it only shows for https://github.com/IrisShaders/Iris/issues/785
            this.addLabelEntries(
                    "",
                    Formatting.RED + "" + Formatting.BOLD + "There was an error reading your shaderpacks directory",
                    "",
                    "Check your logs for more information.",
                    "Please file an issue report including a log file.",
                    "If you are able to identify the file causing this, " +
                            "please include it in your report as well.",
                    "Note that this might be an issue with folder " +
                            "permissions; ensure those are correct first."
            );

            return;
        }

        this.addEntry(topButtonRow);

        topButtonRow.allowEnableShadersButton = names.size() > 0;
        topButtonRow.enableDisableButton.text = topButtonRow.getEnableDisableLabel();

        int index = 0;

        for (String name : names) {
            index++;
            addPackEntry(index, name);
        }
    }

    public void addPackEntry(int index, String name) {
        ShaderPackEntry entry = new ShaderPackEntry(index, this, name);

        Lucerna.getLucernaConfig().getShaderPackName().ifPresent(currentPackName -> {
            if (name.equals(currentPackName)) {
                setSelected(entry);
                setApplied(entry);
            }
        });

        this.addEntry(entry);
    }

    public void addLabelEntries(String ... lines) {
        for (String text : lines) {
            this.addEntry(new LabelEntry(text));
        }
    }

    public void setApplied(ShaderPackEntry entry) {
        this.applied = entry;
    }

    public ShaderPackEntry getApplied() {
        return this.applied;
    }

    public TopButtonRowEntry getTopButtonRow() {
        return topButtonRow;
    }

    public static abstract class BaseEntry extends LucernaObjectSelectionList.Entry {
        protected BaseEntry() {}
    }

    public static class ShaderPackEntry extends BaseEntry {
        private final String packName;
        private final ShaderPackSelectionList list;
        private final int index;

        public ShaderPackEntry(int index, ShaderPackSelectionList list, String packName) {
            this.packName = packName;
            this.list = list;
            this.index = index;
        }

        public boolean isApplied() {
            return list.getApplied() == this;
        }

        public boolean isSelected() {
            return list.getSelected() == this;
        }

        public String getPackName() {
            return packName;
        }

        @Override
        public void render(int index, int x, int y, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered) {
            TextRenderer font = Minecraft.getInstance().textRenderer;
            int color = 0xFFFFFF;
            String name = packName;

            boolean shadersEnabled = list.getTopButtonRow().shadersEnabled;

            if (font.getWidth(Formatting.BOLD + name) > this.list.getRowWidth() - 3) {
                name = font.trim(name, this.list.getRowWidth() - 8) + "...";
            }

            if (hovered) {
                name = Formatting.BOLD + name;
            }

            if (shadersEnabled && this.isApplied()) {
                color = 0xFFF263;
            }

            if (!shadersEnabled && !hovered) {
                color = 0xA2A2A2;
            }

            drawCenteredString(font, name, (x + entryWidth / 2) - 2, y + (entryHeight - 11) / 2, color);
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int button) {
            if (button != 0) {
                return false;
            }

            boolean didAnything = false;

            // with shaders disabled, picking a pack also enables shaders on apply
            if (!list.getTopButtonRow().shadersEnabled) {
                list.getTopButtonRow().setShadersEnabled(true);
                didAnything = true;
            }

            if (!this.isSelected()) {
                this.list.select(this.index);
                didAnything = true;
            }

            return didAnything;
        }
    }

    public static class LabelEntry extends BaseEntry {
        private final String label;

        public LabelEntry(String label) {
            this.label = label;
        }

        @Override
        public void render(int index, int x, int y, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered) {
            drawCenteredString(Minecraft.getInstance().textRenderer, label, (x + entryWidth / 2) - 2, y + (entryHeight - 11) / 2, 0xC2C2C2);
        }
    }

    public static class TopButtonRowEntry extends BaseEntry {
        private static final int REFRESH_BUTTON_WIDTH = 18;

        private final String refreshShaderPacksLabel = Formatting.AQUA + I18n.translate("options.lucerna.refreshShaderPacks");
        private final String nonePresentLabel = Formatting.GRAY + I18n.translate("options.lucerna.shaders.nonePresent");
        private final String shadersDisabledLabel = I18n.translate("options.lucerna.shaders.disabled");
        private final String shadersEnabledLabel = I18n.translate("options.lucerna.shaders.enabled");

        private final ShaderPackSelectionList list;
        private final LucernaElementRow buttons = new LucernaElementRow();
        private final EnableShadersButtonElement enableDisableButton;
        private final LucernaElementRow.Element refreshPacksButton;

        public boolean allowEnableShadersButton = true;
        public boolean shadersEnabled;

        public TopButtonRowEntry(ShaderPackSelectionList list, boolean shadersEnabled) {
            this.list = list;
            this.shadersEnabled = shadersEnabled;
            this.enableDisableButton = new EnableShadersButtonElement(
                    getEnableDisableLabel(),
                    button -> {
                        if (this.allowEnableShadersButton) {
                            setShadersEnabled(!this.shadersEnabled);
                            GuiUtil.playButtonClickSound();
                            return true;
                        }

                        return false;
                    });
            this.refreshPacksButton = new LucernaElementRow.IconButtonElement(
                    GuiUtil.Icon.REFRESH,
                    button -> {
                        this.list.refresh();

                        GuiUtil.playButtonClickSound();
                        return true;
                    });
            this.buttons.add(this.enableDisableButton, 0).add(this.refreshPacksButton, REFRESH_BUTTON_WIDTH);
        }

        public void setShadersEnabled(boolean shadersEnabled) {
            this.shadersEnabled = shadersEnabled;
            this.enableDisableButton.text = getEnableDisableLabel();
            this.list.screen.refreshScreenSwitchButton();
        }

        @Override
        public void render(int index, int x, int y, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered) {
            this.buttons.setWidth(this.enableDisableButton, (entryWidth - 1) - REFRESH_BUTTON_WIDTH);
            this.enableDisableButton.centerX = x + (int)(entryWidth * 0.5);

            this.buttons.render(x - 2, y - 3, 18, mouseX, mouseY, hovered);

            if (this.refreshPacksButton.isHovered()) {
                TextRenderer font = Minecraft.getInstance().textRenderer;

                ShaderPackScreen.TOP_LAYER_RENDER_QUEUE.add(() ->
                        GuiUtil.drawTextPanel(font, refreshShaderPacksLabel,
                                (mouseX - 8) - font.getWidth(refreshShaderPacksLabel), mouseY - 16));
            }
        }

        private String getEnableDisableLabel() {
            return this.allowEnableShadersButton ? (this.shadersEnabled ? shadersEnabledLabel : shadersDisabledLabel) : nonePresentLabel;
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int button) {
            return this.buttons.mouseClicked(mouseX, mouseY, button);
        }

        // offset so the label lines up with the rest of the menu
        public static class EnableShadersButtonElement extends LucernaElementRow.TextButtonElement {
            private int centerX;

            public EnableShadersButtonElement(String text, Function<LucernaElementRow.TextButtonElement, Boolean> onClick) {
                super(text, onClick);
            }

            @Override
            public void renderLabel(int x, int y, int width, int height, int mouseX, int mouseY, boolean hovered) {
                int textX = this.centerX - (int)(this.font.getWidth(this.text) * 0.5);
                int textY = y + (int)((height - 8) * 0.5);

                this.font.drawWithShadow(this.text, textX, textY, 0xFFFFFF);
            }
        }
    }
}
