package us.z1x.lucerna.gui.element;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.gui.widget.ListWidget;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class LucernaObjectSelectionList<E extends LucernaObjectSelectionList.Entry> extends ListWidget {
    private final List<E> entries = new ArrayList<>();
    private E selected;
    private E focused;
    private boolean renderBackground = true;

    public LucernaObjectSelectionList(Minecraft client, int width, int height, int top, int bottom, int left, int right, int itemHeight) {
        super(client, width, height, top, bottom, itemHeight);

        this.minX = left;
        this.maxX = right;
    }

    @Override
    protected int getScrollbarPosition() {
        // scrollbar at the right edge of the screen instead of near the center
        return width - 6;
    }

    public void select(int entry) {
        setSelected(this.getEntry(entry));
    }

    public void setSelected(E entry) {
        this.selected = entry;
    }

    public E getSelected() {
        return this.selected;
    }

    public E getEntry(int index) {
        return this.entries.get(index);
    }

    protected void addEntry(E entry) {
        this.entries.add(entry);
    }

    protected void clearEntries() {
        this.entries.clear();
        this.selected = null;
        this.focused = null;
    }

    public void setScrollAmount(float amount) {
        this.scrollAmount = amount;
        this.capScrolling();
    }

    public void setRenderBackground(boolean renderBackground) {
        this.renderBackground = renderBackground;
    }

    @Override
    protected int size() {
        return this.entries.size();
    }

    @Override
    protected void entryClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
    }

    @Override
    protected boolean isEntrySelected(int index) {
        return this.entries.get(index) == this.selected;
    }

    @Override
    protected void renderBackground() {
    }

    @Override
    protected void renderEntry(int index, int x, int y, int rowHeight, int mouseX, int mouseY) {
        boolean hovered = this.isMouseInList(mouseY) && this.getEntryAt(mouseX, mouseY) == index;

        this.entries.get(index).render(index, x, y, this.getRowWidth(), rowHeight, mouseX, mouseY, hovered);
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (this.isMouseInList(mouseY)) {
            int index = this.getEntryAt(mouseX, mouseY);

            if (index >= 0 && index < this.entries.size()) {
                // a click can open a submenu, which rebuilds the entries
                E entry = this.entries.get(index);

                if (entry.mouseClicked(mouseX, mouseY, button)) {
                    this.focused = entry;
                    this.setScrolling(false);

                    return true;
                }
            }
        }

        return false;
    }

    public void mouseReleased(int mouseX, int mouseY, int button) {
        if (this.focused != null) {
            this.focused.mouseReleased(mouseX, mouseY, button);
        }

        this.setScrolling(true);
    }

    // mirrors ListWidget#render, but allows skipping the list background so that the world stays visible
    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        int scrollbarStart = this.getScrollbarPosition();
        int scrollbarEnd = scrollbarStart + 6;
        this.capScrolling();
        GlStateManager.disableLighting();
        GlStateManager.disableFog();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuffer();

        if (this.renderBackground) {
            this.minecraft.getTextureManager().bind(GuiElement.BACKGROUND_LOCATION);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            buffer.vertex(this.minX, this.maxY, 0.0).texture(this.minX / 32.0F, (this.maxY + (int) this.scrollAmount) / 32.0F).color(32, 32, 32, 255).nextVertex();
            buffer.vertex(this.maxX, this.maxY, 0.0).texture(this.maxX / 32.0F, (this.maxY + (int) this.scrollAmount) / 32.0F).color(32, 32, 32, 255).nextVertex();
            buffer.vertex(this.maxX, this.minY, 0.0).texture(this.maxX / 32.0F, (this.minY + (int) this.scrollAmount) / 32.0F).color(32, 32, 32, 255).nextVertex();
            buffer.vertex(this.minX, this.minY, 0.0).texture(this.minX / 32.0F, (this.minY + (int) this.scrollAmount) / 32.0F).color(32, 32, 32, 255).nextVertex();
            tesselator.end();
        }

        this.renderList(this.minX + this.width / 2 - this.getRowWidth() / 2 + 2, this.minY + 4 - (int) this.scrollAmount, mouseX, mouseY);

        GlStateManager.disableDepthTest();
        this.renderHoleBackground(0, this.minY, 255, 255);
        this.renderHoleBackground(this.maxY, this.height, 255, 255);
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
        GlStateManager.disableAlphaTest();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GlStateManager.disableTexture();

        buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.vertex(this.minX, this.minY + 4, 0.0).texture(0.0, 1.0).color(0, 0, 0, 0).nextVertex();
        buffer.vertex(this.maxX, this.minY + 4, 0.0).texture(1.0, 1.0).color(0, 0, 0, 0).nextVertex();
        buffer.vertex(this.maxX, this.minY, 0.0).texture(1.0, 0.0).color(0, 0, 0, 255).nextVertex();
        buffer.vertex(this.minX, this.minY, 0.0).texture(0.0, 0.0).color(0, 0, 0, 255).nextVertex();
        tesselator.end();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.vertex(this.minX, this.maxY, 0.0).texture(0.0, 1.0).color(0, 0, 0, 255).nextVertex();
        buffer.vertex(this.maxX, this.maxY, 0.0).texture(1.0, 1.0).color(0, 0, 0, 255).nextVertex();
        buffer.vertex(this.maxX, this.maxY - 4, 0.0).texture(1.0, 0.0).color(0, 0, 0, 0).nextVertex();
        buffer.vertex(this.minX, this.maxY - 4, 0.0).texture(0.0, 0.0).color(0, 0, 0, 0).nextVertex();
        tesselator.end();

        int maxScroll = this.getMaxScroll();

        if (maxScroll > 0) {
            int barHeight = MathHelper.clamp((this.maxY - this.minY) * (this.maxY - this.minY) / this.getHeight(), 32, this.maxY - this.minY - 8);
            int barTop = Math.max(this.minY, (int) this.scrollAmount * (this.maxY - this.minY - barHeight) / maxScroll + this.minY);

            buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            buffer.vertex(scrollbarStart, this.maxY, 0.0).texture(0.0, 1.0).color(0, 0, 0, 255).nextVertex();
            buffer.vertex(scrollbarEnd, this.maxY, 0.0).texture(1.0, 1.0).color(0, 0, 0, 255).nextVertex();
            buffer.vertex(scrollbarEnd, this.minY, 0.0).texture(1.0, 0.0).color(0, 0, 0, 255).nextVertex();
            buffer.vertex(scrollbarStart, this.minY, 0.0).texture(0.0, 0.0).color(0, 0, 0, 255).nextVertex();
            tesselator.end();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            buffer.vertex(scrollbarStart, barTop + barHeight, 0.0).texture(0.0, 1.0).color(128, 128, 128, 255).nextVertex();
            buffer.vertex(scrollbarEnd, barTop + barHeight, 0.0).texture(1.0, 1.0).color(128, 128, 128, 255).nextVertex();
            buffer.vertex(scrollbarEnd, barTop, 0.0).texture(1.0, 0.0).color(128, 128, 128, 255).nextVertex();
            buffer.vertex(scrollbarStart, barTop, 0.0).texture(0.0, 0.0).color(128, 128, 128, 255).nextVertex();
            tesselator.end();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            buffer.vertex(scrollbarStart, barTop + barHeight - 1, 0.0).texture(0.0, 1.0).color(192, 192, 192, 255).nextVertex();
            buffer.vertex(scrollbarEnd - 1, barTop + barHeight - 1, 0.0).texture(1.0, 1.0).color(192, 192, 192, 255).nextVertex();
            buffer.vertex(scrollbarEnd - 1, barTop, 0.0).texture(1.0, 0.0).color(192, 192, 192, 255).nextVertex();
            buffer.vertex(scrollbarStart, barTop, 0.0).texture(0.0, 0.0).color(192, 192, 192, 255).nextVertex();
            tesselator.end();
        }

        GlStateManager.enableTexture();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableAlphaTest();
        GlStateManager.disableBlend();
    }

    public abstract static class Entry extends GuiElement {
        public abstract void render(int index, int x, int y, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered);

        public boolean mouseClicked(int mouseX, int mouseY, int button) {
            return false;
        }

        public boolean mouseReleased(int mouseX, int mouseY, int button) {
            return false;
        }
    }
}
