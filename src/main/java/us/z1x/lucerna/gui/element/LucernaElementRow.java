package us.z1x.lucerna.gui.element;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.TextRenderer;
import us.z1x.lucerna.gui.GuiUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class LucernaElementRow {
    private final Map<Element, Integer> elements = new HashMap<>();
    private final List<Element> orderedElements = new ArrayList<>();
    private final int spacing;
    private int x;
    private int y;
    private int width;
    private int height;

    public LucernaElementRow(int spacing) {
        this.spacing = spacing;
    }

    public LucernaElementRow() {
        this(1);
    }

    public LucernaElementRow add(Element element, int width) {
        if (!this.orderedElements.contains(element)) {
            this.orderedElements.add(element);
        }
        this.elements.put(element, width);

        this.width += width + this.spacing;

        return this;
    }

    public void setWidth(Element element, int width) {
        if (!this.elements.containsKey(element)) {
            return;
        }

        this.width -= this.elements.get(element) + 2;

        add(element, width);
    }

    public void render(int x, int y, int height, int mouseX, int mouseY, boolean rowHovered) {
        this.x = x;
        this.y = y;
        this.height = height;

        int currentX = x;

        for (Element element : this.orderedElements) {
            int currentWidth = this.elements.get(element);

            element.render(currentX, y, currentWidth, height, mouseX, mouseY,
                    rowHovered && sectionHovered(currentX, currentWidth, mouseX, mouseY));

            currentX += currentWidth + this.spacing;
        }
    }

    public void renderRightAligned(int x, int y, int height, int mouseX, int mouseY, boolean hovered) {
        render(x - this.width, y, height, mouseX, mouseY, hovered);
    }

    private boolean sectionHovered(int sectionX, int sectionWidth, int mx, int my) {
        return mx > sectionX && mx < sectionX + sectionWidth &&
                my > this.y && my < this.y + this.height;
    }

    private Optional<Element> getHovered(int mx, int my) {
        int currentX = this.x;

        for (Element element : this.orderedElements) {
            int currentWidth = this.elements.get(element);

            if (sectionHovered(currentX, currentWidth, mx, my)) {
                return Optional.of(element);
            }

            currentX += currentWidth + this.spacing;
        }

        return Optional.empty();
    }

    public boolean mouseClicked(int mx, int my, int button) {
        return getHovered(mx, my).map(element -> element.mouseClicked(mx, my, button)).orElse(false);
    }

    public static abstract class Element {
        public boolean disabled = false;
        private boolean hovered = false;

        public void render(int x, int y, int width, int height, int mouseX, int mouseY, boolean hovered) {
            GuiUtil.bindWidgetsTexture();
            GuiUtil.drawButton(x, y, width, height, hovered, this.disabled);

            this.hovered = hovered;
            this.renderLabel(x, y, width, height, mouseX, mouseY, hovered);
        }

        public abstract void renderLabel(int x, int y, int width, int height, int mouseX, int mouseY, boolean hovered);

        public boolean mouseClicked(int mx, int my, int button) {
            return false;
        }

        public boolean isHovered() {
            return hovered;
        }
    }

    public static abstract class ButtonElement<T extends ButtonElement<T>> extends Element {
        private final Function<T, Boolean> onClick;

        protected ButtonElement(Function<T, Boolean> onClick) {
            this.onClick = onClick;
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean mouseClicked(int mx, int my, int button) {
            if (this.disabled) {
                return false;
            }

            if (button == 0) {
                return this.onClick.apply((T) this);
            }

            return super.mouseClicked(mx, my, button);
        }
    }

    public static class IconButtonElement extends ButtonElement<IconButtonElement> {
        public GuiUtil.Icon icon;

        public IconButtonElement(GuiUtil.Icon icon, Function<IconButtonElement, Boolean> onClick) {
            super(onClick);
            this.icon = icon;
        }

        @Override
        public void renderLabel(int x, int y, int width, int height, int mouseX, int mouseY, boolean hovered) {
            int iconX = x + (int)((width - this.icon.getWidth()) * 0.5);
            int iconY = y + (int)((height - this.icon.getHeight()) * 0.5);

            GuiUtil.bindWidgetsTexture();
            this.icon.draw(iconX, iconY);
        }
    }

    public static class TextButtonElement extends ButtonElement<TextButtonElement> {
        protected final TextRenderer font;
        public String text;

        public TextButtonElement(String text, Function<TextButtonElement, Boolean> onClick) {
            super(onClick);

            this.font = Minecraft.getInstance().textRenderer;
            this.text = text;
        }

        @Override
        public void renderLabel(int x, int y, int width, int height, int mouseX, int mouseY, boolean hovered) {
            int textX = x + (int)((width - this.font.getWidth(this.text)) * 0.5);
            int textY = y + (int)((height - 8) * 0.5);

            this.font.drawWithShadow(this.text, textX, textY, 0xFFFFFF);
        }
    }
}
