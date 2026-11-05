package us.z1x.lucerna.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.sound.instance.SimpleSoundInstance;
import net.minecraft.locale.I18n;
import net.minecraft.resource.Identifier;
import org.lwjgl.opengl.GL11;
import us.z1x.lucerna.Lucerna;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public final class GuiUtil {
    public static final Identifier WIDGETS_TEX = new Identifier(Lucerna.MODID, "textures/gui/widgets.png");
    private static final String ELLIPSIS = "...";

    private GuiUtil() {}

    private static Minecraft client() {
        return Minecraft.getInstance();
    }

    public static void bindWidgetsTexture() {
        client().getTextureManager().bind(WIDGETS_TEX);
    }

    // textures use the vanilla widgets layout, max size is 398x20
    public static void drawButton(int x, int y, int width, int height, boolean hovered, boolean disabled) {
        int halfWidth = width / 2;
        int halfHeight = height / 2;

        // disabled, hovered and normal texture rows
        int vOffset = disabled ? 46 : (hovered ? 86 : 66);

        GlStateManager.color4f(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GuiElement.drawTexture(x, y, 0, vOffset, halfWidth, halfHeight, 256, 256);
        GuiElement.drawTexture(x + halfWidth, y, 200 - (width - halfWidth), vOffset, width - halfWidth, halfHeight, 256, 256);
        GuiElement.drawTexture(x, y + halfHeight, 0, vOffset + (20 - (height - halfHeight)), halfWidth, height - halfHeight, 256, 256);
        GuiElement.drawTexture(x + halfWidth, y + halfHeight, 200 - (width - halfWidth), vOffset + (20 - (height - halfHeight)), width - halfWidth, height - halfHeight, 256, 256);
    }

    public static void drawPanel(int x, int y, int width, int height) {
        int borderColor = 0xDEDEDEDE;
        int innerColor = 0xDE000000;

        GuiElement.fill(x, y, x + width, y + 1, borderColor);
        GuiElement.fill(x, (y + height) - 1, x + width, y + height, borderColor);
        GuiElement.fill(x, y + 1, x + 1, (y + height) - 1, borderColor);
        GuiElement.fill((x + width) - 1, y + 1, x + width, (y + height) - 1, borderColor);
        GuiElement.fill(x + 1, y + 1, (x + width) - 1, (y + height) - 1, innerColor);
    }

    public static void drawTextPanel(TextRenderer font, String text, int x, int y) {
        drawPanel(x, y, font.getWidth(text) + 8, 16);
        font.drawWithShadow(text, x + 4, y + 4, 0xFFFFFF);
    }

    public static String shortenText(TextRenderer font, String text, int width) {
        if (font.getWidth(text) > width) {
            return font.trim(text, width - font.getWidth(ELLIPSIS)) + ELLIPSIS;
        }
        return text;
    }

    public static Optional<String> getTranslation(String key) {
        if (I18n.hasTranslation(key)) {
            return Optional.of(I18n.translate(key));
        }

        // shader packs use legacy language codes, fall back to english
        return Lucerna.getCurrentPack().flatMap(pack -> Stream.of(client().options.language.toLowerCase(Locale.ROOT), "en_us")
                .map(pack.getLanguageMap()::getTranslations)
                .filter(Objects::nonNull)
                .map(translations -> translations.get(key))
                .filter(Objects::nonNull)
                .findFirst());
    }

    public static String translateOrDefault(String defaultText, String translationDesc) {
        return getTranslation(translationDesc).orElse(defaultText);
    }

    public static void playButtonClickSound() {
        client().getSoundManager().play(SimpleSoundInstance.of(new Identifier("gui.button.press"), 1.0F));
    }

    public static class Icon {
        public static final Icon REFRESH = new Icon(12, 0, 10, 10);

        private final int u;
        private final int v;
        private final int width;
        private final int height;

        public Icon(int u, int v, int width, int height) {
            this.u = u;
            this.v = v;
            this.width = width;
            this.height = height;
        }

        public void draw(int x, int y) {
            GlStateManager.color4f(1.0f, 1.0f, 1.0f, 1.0f);
            GlStateManager.enableBlend();

            GuiElement.drawTexture(x, y, u, v, width, height, 256, 256);
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }
    }
}
