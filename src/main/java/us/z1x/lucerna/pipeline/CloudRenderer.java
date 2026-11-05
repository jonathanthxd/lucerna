package us.z1x.lucerna.pipeline;

import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import us.z1x.lucerna.uniforms.CapturedRenderingState;

import java.nio.ByteBuffer;

// fancy clouds from a display list that is rebuilt only when the cell, color, visible faces or texture change
public final class CloudRenderer {
    public static final CloudRenderer INSTANCE = new CloudRenderer();

    // vanilla draws 8 cells of 8 texels around the camera
    private static final int RADIUS = 32;

    private int list;
    private int texture;
    private int width;
    private int height;
    private boolean[] opaque;
    private int cellX;
    private int cellZ;
    private int red;
    private int green;
    private int blue;
    private boolean bottom;
    private boolean top;

    private CloudRenderer() {
    }

    public void invalidate() {
        texture = 0;
    }

    public void render(double x, double z, float height, float red, float green, float blue, int anaglyphRenderPass) {
        int cellX = MathHelper.floor(x);
        int cellZ = MathHelper.floor(z);
        boolean bottom = height > -5.0F;
        boolean top = height <= 5.0F;
        int redBits = Float.floatToIntBits(red);
        int greenBits = Float.floatToIntBits(green);
        int blueBits = Float.floatToIntBits(blue);

        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        if (list == 0 || texture != this.texture || cellX != this.cellX || cellZ != this.cellZ || bottom != this.bottom || top != this.top
                || redBits != this.red || greenBits != this.green || blueBits != this.blue) {
            this.cellX = cellX;
            this.cellZ = cellZ;
            this.bottom = bottom;
            this.top = top;
            this.red = redBits;
            this.green = greenBits;
            this.blue = blueBits;
            readTexture(texture);
            compile(cellX, cellZ, red, green, blue);
        }

        GlStateManager.scalef(12.0F, 1.0F, 12.0F);
        GlStateManager.translatef((float) (cellX - x), height, (float) (cellZ - z));

        GlStateManager.colorMask(false, false, false, false);
        GlStateManager.callList(list);

        switch (anaglyphRenderPass) {
            case 0:
                GlStateManager.colorMask(false, true, true, true);
                break;
            case 1:
                GlStateManager.colorMask(true, false, false, true);
                break;
            default:
                GlStateManager.colorMask(true, true, true, true);
                break;
        }

        GlStateManager.callList(list);
    }

    private void compile(int cellX, int cellZ, float red, float green, float blue) {
        if (list == 0) {
            list = GL11.glGenLists(1);
        }

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuffer();

        CapturedRenderingState.INSTANCE.setCompilingDisplayList(true);
        GL11.glNewList(list, GL11.GL_COMPILE);
        builder.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);

        // only opaque texels get geometry, like modern clouds, since packs that skip the texture would draw the whole layer
        for (int x = -RADIUS; x < RADIUS; x++) {
            for (int z = -RADIUS; z < RADIUS; z++) {
                if (!isOpaque(cellX + x, cellZ + z)) {
                    continue;
                }

                float u = (Math.floorMod(cellX + x, width) + 0.5F) / width;
                float v = (Math.floorMod(cellZ + z, height) + 0.5F) / height;

                if (bottom) {
                    quad(builder, x, 0.0F, z, x + 1, 0.0F, z, x + 1, 0.0F, z + 1, x, 0.0F, z + 1, u, v, red * 0.7F, green * 0.7F, blue * 0.7F, 0.0F, -1.0F, 0.0F);
                }

                if (top) {
                    quad(builder, x, 4.0F, z + 1, x + 1, 4.0F, z + 1, x + 1, 4.0F, z, x, 4.0F, z, u, v, red, green, blue, 0.0F, 1.0F, 0.0F);
                }

                if (!isOpaque(cellX + x - 1, cellZ + z)) {
                    quad(builder, x, 0.0F, z, x, 0.0F, z + 1, x, 4.0F, z + 1, x, 4.0F, z, u, v, red * 0.9F, green * 0.9F, blue * 0.9F, -1.0F, 0.0F, 0.0F);
                }

                if (!isOpaque(cellX + x + 1, cellZ + z)) {
                    quad(builder, x + 1, 0.0F, z + 1, x + 1, 0.0F, z, x + 1, 4.0F, z, x + 1, 4.0F, z + 1, u, v, red * 0.9F, green * 0.9F, blue * 0.9F, 1.0F, 0.0F, 0.0F);
                }

                if (!isOpaque(cellX + x, cellZ + z - 1)) {
                    quad(builder, x + 1, 0.0F, z, x, 0.0F, z, x, 4.0F, z, x + 1, 4.0F, z, u, v, red * 0.8F, green * 0.8F, blue * 0.8F, 0.0F, 0.0F, -1.0F);
                }

                if (!isOpaque(cellX + x, cellZ + z + 1)) {
                    quad(builder, x, 0.0F, z + 1, x + 1, 0.0F, z + 1, x + 1, 4.0F, z + 1, x, 4.0F, z + 1, u, v, red * 0.8F, green * 0.8F, blue * 0.8F, 0.0F, 0.0F, 1.0F);
                }
            }
        }

        tesselator.end();
        GL11.glEndList();
        CapturedRenderingState.INSTANCE.setCompilingDisplayList(false);
    }

    // the cloud texture is bound, and a resource reload gives it a new id
    private void readTexture(int texture) {
        if (texture == this.texture) {
            return;
        }

        this.texture = texture;
        width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        opaque = new boolean[width * height];

        for (int i = 0; i < opaque.length; i++) {
            opaque[i] = (pixels.get(i * 4 + 3) & 0xFF) > 25;
        }
    }

    private boolean isOpaque(int x, int z) {
        return opaque[Math.floorMod(z, height) * width + Math.floorMod(x, width)];
    }

    private static void quad(BufferBuilder builder, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4,
                             float u, float v, float red, float green, float blue, float normalX, float normalY, float normalZ) {
        vertex(builder, x1, y1, z1, u, v, red, green, blue, normalX, normalY, normalZ);
        vertex(builder, x2, y2, z2, u, v, red, green, blue, normalX, normalY, normalZ);
        vertex(builder, x3, y3, z3, u, v, red, green, blue, normalX, normalY, normalZ);
        vertex(builder, x4, y4, z4, u, v, red, green, blue, normalX, normalY, normalZ);
    }

    private static void vertex(BufferBuilder builder, float x, float y, float z, float u, float v,
                               float red, float green, float blue, float normalX, float normalY, float normalZ) {
        builder.vertex(x, y, z)
            .texture(u, v)
            .color(red, green, blue, 0.8F)
            .normal(normalX, normalY, normalZ)
            .nextVertex();
    }
}
