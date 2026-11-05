package us.z1x.lucerna.postprocess;

import net.minecraft.client.render.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20C;
import us.z1x.lucerna.gl.LucernaRenderSystem;

public class FullScreenQuadRenderer {
    private final int quadBuffer;

    public static final FullScreenQuadRenderer INSTANCE = new FullScreenQuadRenderer();

    private FullScreenQuadRenderer() {
        this.quadBuffer = createQuad();
    }

    public void render() {
        begin();

        renderQuad();

        end();
    }

    @SuppressWarnings("deprecation")
    public void begin() {
        GlStateManager.disableDepthTest();

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        // scale the quad from [0, 1] to [-1, 1]
        GlStateManager.translatef(-1.0F, -1.0F, 0.0F);
        GlStateManager.scalef(2.0F, 2.0F, 0.0F);

        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();

        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);

        GL15.glBindBuffer(GL20C.GL_ARRAY_BUFFER, quadBuffer);
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glVertexPointer(3, GL11.GL_FLOAT, 20, 0L);
        GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL11.glTexCoordPointer(2, GL11.GL_FLOAT, 20, 12L);
    }

    public void renderQuad() {
        GL11.glDrawArrays(GL20C.GL_TRIANGLE_STRIP, 0, 4);
    }

    @SuppressWarnings("deprecation")
    public static void end() {
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL15.glBindBuffer(GL20C.GL_ARRAY_BUFFER, 0);

        GlStateManager.enableDepthTest();

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.popMatrix();
    }

    private static int createQuad() {
        float[] vertices = new float[] {
            // top right
            1.0F, 1.0F, 0.0F,
            1.0F, 1.0F,
            // top left
            0.0F, 1.0F, 0.0F,
            0.0F, 1.0F,
            // bottom right
            1.0F, 0.0F, 0.0F,
            1.0F, 0.0F,
            // bottom left
            0.0F, 0.0F, 0.0F,
            0.0F, 0.0F
        };

        return LucernaRenderSystem.bufferStorage(GL20C.GL_ARRAY_BUFFER, vertices, GL20C.GL_STATIC_DRAW);
    }
}
