package us.z1x.lucerna.compat.farterrain;

import net.minecraft.client.render.platform.GlStateManager;
import org.lwjgl.opengl.*;
import us.z1x.lucerna.gl.LucernaRenderSystem;
import us.z1x.lucerna.gl.program.ProgramUniforms;
import us.z1x.lucerna.gl.program.ProgramSamplers;

/** Compatibility-context state scope. State queries do not read back GPU image/vertex data. */
public final class FarTerrainGlScope implements AutoCloseable {
    private final int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
    private final int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
    private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    private final int array = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
    private final int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
    private final int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final int cachedActive = GlStateManager.texture;
    private final int[] cachedTextures = new int[GlStateManager.TEXTURES.length];
    public FarTerrainGlScope() {
        for (int i = 0; i < cachedTextures.length; i++) cachedTextures[i] = GlStateManager.TEXTURES[i].texture;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix();
        for (int unit = 0; unit < 2; unit++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            GL11.glMatrixMode(GL11.GL_TEXTURE); GL11.glPushMatrix(); GL11.glLoadIdentity();
        }
        GL13.glActiveTexture(active);
        GL11.glMatrixMode(matrixMode);
    }
    @Override public void close() {
        for (int unit = 1; unit >= 0; unit--) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            GL11.glMatrixMode(GL11.GL_TEXTURE); GL11.glPopMatrix();
        }
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix();
        GL11.glMatrixMode(matrixMode);
        GL11.glPopClientAttrib(); GL11.glPopAttrib();
        for (int i = 0; i < cachedTextures.length; i++) GlStateManager.TEXTURES[i].texture = cachedTextures[i];
        GlStateManager.texture = cachedActive;
        GL13.glActiveTexture(active);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, array);
        LucernaRenderSystem.bindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        LucernaRenderSystem.bindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        ProgramUniforms.clearActiveUniforms(); ProgramSamplers.clearActiveSamplers();
        LucernaRenderSystem.useProgram(program);
    }
}
