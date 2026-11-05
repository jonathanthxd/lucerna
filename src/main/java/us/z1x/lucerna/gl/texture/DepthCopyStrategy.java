package us.z1x.lucerna.gl.texture;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.system.MemoryUtil;
import us.z1x.lucerna.gl.LucernaRenderSystem;
import us.z1x.lucerna.gl.framebuffer.GlFramebuffer;

public interface DepthCopyStrategy {
    class Gl20CopyTexture implements DepthCopyStrategy {
        private Gl20CopyTexture() {
        }

        @Override
        public void copy(GlFramebuffer sourceFb, int sourceTexture, GlFramebuffer destFb, int destTexture, int width, int height) {
            sourceFb.bindAsReadBuffer();

            LucernaRenderSystem.copyTexSubImage2D(
                destTexture,
                GL20C.GL_TEXTURE_2D,
                0,
                0, 0,
                0, 0,
                width,
                height);
        }
    }

    class Gl30BlitFbCombinedDepthStencil implements DepthCopyStrategy {
        private Gl30BlitFbCombinedDepthStencil() {
        }

        @Override
        public void copy(GlFramebuffer sourceFb, int sourceTexture, GlFramebuffer destFb, int destTexture, int width, int height) {
            LucernaRenderSystem.blitFramebuffer(sourceFb.getId(), destFb.getId(), 0, 0, width, height,
                0, 0, width, height,
                GL30C.GL_DEPTH_BUFFER_BIT | GL30C.GL_STENCIL_BUFFER_BIT,
                GL30C.GL_NEAREST);
        }
    }

    class Gl43CopyImage implements DepthCopyStrategy {
        private Gl43CopyImage() {
        }

        @Override
        public void copy(GlFramebuffer sourceFb, int sourceTexture, GlFramebuffer destFb, int destTexture, int width, int height) {
            GL43C.glCopyImageSubData(
                sourceTexture,
                GL43C.GL_TEXTURE_2D,
                0,
                0,
                0,
                0,
                destTexture,
                GL43C.GL_TEXTURE_2D,
                0,
                0,
                0,
                0,
                width,
                height,
                1
            );
        }
    }

    static DepthCopyStrategy fastest(boolean combinedStencilRequired) {
        // the OpenGL43 capability can be false even when the function is supported
        if (GL.getCapabilities().glCopyImageSubData != MemoryUtil.NULL) {
            return new Gl43CopyImage();
        }

        if (combinedStencilRequired) {
            return new Gl30BlitFbCombinedDepthStencil();
        } else {
            return new Gl20CopyTexture();
        }
    }

    // may clobber the read and draw framebuffer bindings but must keep texture bindings
    // destFb can be null when needsDestFramebuffer is false
    void copy(GlFramebuffer sourceFb, int sourceTexture, GlFramebuffer destFb, int destTexture, int width, int height);
}
