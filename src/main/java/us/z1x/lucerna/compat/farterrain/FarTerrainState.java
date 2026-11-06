package us.z1x.lucerna.compat.farterrain;

import com.google.common.collect.ImmutableSet;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import us.z1x.lucerna.api.farterrain.FarTerrainFrame;
import us.z1x.lucerna.gl.LucernaRenderSystem;
import us.z1x.lucerna.gl.framebuffer.GlFramebuffer;
import us.z1x.lucerna.gl.texture.DepthBufferFormat;
import us.z1x.lucerna.rendertarget.DepthTexture;
import us.z1x.lucerna.rendertarget.RenderTargets;
import java.util.ArrayList;
import java.util.List;

/** Pipeline-owned depth. Never attaches DH depth to a cached vanilla gbuffer framebuffer. */
public final class FarTerrainState {
    private final DepthTexture depth, opaque;
    private final GlFramebuffer depthFramebuffer, opaqueFramebuffer;
    private final List<GlFramebuffer> framebuffers = new ArrayList<>();
    public final Matrix4f projection = new Matrix4f(), inverse = new Matrix4f();
    public final Matrix4f previous = new Matrix4f(), previousInverse = new Matrix4f();
    private int width, height;
    public float near = 0.05f, far = 1f;
    public int distance;
    public boolean terrainDrawn, waterDrawn, drawing, failed;
    private FarTerrainFrame frame;
    private boolean historyValid;

    public FarTerrainState(RenderTargets targets) {
        width = targets.getCurrentWidth(); height = targets.getCurrentHeight();
        DepthTexture newDepth = null, newOpaque = null;
        GlFramebuffer newDepthFramebuffer = null, newOpaqueFramebuffer = null;
        try {
            newDepth = new DepthTexture(width, height, DepthBufferFormat.DEPTH24);
            newOpaque = new DepthTexture(width, height, DepthBufferFormat.DEPTH24);
            newDepthFramebuffer = depthFramebuffer(newDepth);
            newOpaqueFramebuffer = depthFramebuffer(newOpaque);
        } catch (RuntimeException | Error failure) {
            if (newDepthFramebuffer != null) newDepthFramebuffer.destroy();
            if (newOpaqueFramebuffer != null) newOpaqueFramebuffer.destroy();
            if (newDepth != null) newDepth.destroy();
            if (newOpaque != null) newOpaque.destroy();
            throw failure;
        }
        depth = newDepth; opaque = newOpaque;
        depthFramebuffer = newDepthFramebuffer; opaqueFramebuffer = newOpaqueFramebuffer;
    }
    private static GlFramebuffer depthFramebuffer(DepthTexture texture) {
        GlFramebuffer framebuffer = new GlFramebuffer();
        try {
            framebuffer.addDepthAttachment(texture.getTextureId());
            framebuffer.noDrawBuffers();
            LucernaRenderSystem.readBuffer(framebuffer.getId(), GL11.GL_NONE);
            if (!framebuffer.isComplete()) throw new IllegalStateException("Incomplete DH depth framebuffer");
            return framebuffer;
        } catch (RuntimeException | Error failure) {
            framebuffer.destroy();
            throw failure;
        }
    }
    public int depth() { return drawing ? opaque.getTextureId() : depth.getTextureId(); }
    public int opaqueDepth() { return opaque.getTextureId(); }
    public void beginFrame(RenderTargets targets) {
        previous.set(projection); previousInverse.set(inverse);
        frame = null; terrainDrawn = waterDrawn = false;
        if (width != targets.getCurrentWidth() || height != targets.getCurrentHeight()) {
            width = targets.getCurrentWidth(); height = targets.getCurrentHeight();
            depth.resize(width, height, DepthBufferFormat.DEPTH24);
            opaque.resize(width, height, DepthBufferFormat.DEPTH24);
        }
        // Do not change GlStateManager's cached state; restore GL state in the caller's scope.
        GL11.glPushAttrib(GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_SCISSOR_BIT);
        try {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDepthMask(true); GL11.glClearDepth(1.0);
            depthFramebuffer.bind(); GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            opaqueFramebuffer.bind(); GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        } finally { GL11.glPopAttrib(); }
    }
    public void setFrame(FarTerrainFrame next) {
        if (frame != null) {
            if (frame != next) throw new IllegalArgumentException("Reuse the same immutable FarTerrainFrame for terrain and water");
            return;
        }
        Matrix4f candidate = new Matrix4f().set(next.projection());
        if (!Float.isFinite(candidate.determinant()) || Math.abs(candidate.determinant()) < 1.0e-12f) {
            throw new IllegalArgumentException("Singular DH projection");
        }
        frame = next;
        projection.set(candidate); inverse.set(candidate).invert();
        near = next.nearPlane(); far = next.farPlane(); distance = next.renderDistance();
        if (!historyValid) {
            previous.set(projection); previousInverse.set(inverse); historyValid = true;
        }
    }
    public GlFramebuffer createFramebuffer(RenderTargets targets, ImmutableSet<Integer> flipped, int[] buffers) {
        GlFramebuffer framebuffer = new GlFramebuffer();
        try {
            int[] attachments = new int[buffers.length];
            for (int i = 0; i < buffers.length; i++) {
                if (buffers[i] < 0 || buffers[i] >= targets.getRenderTargetCount()) throw new IllegalArgumentException("Unsupported DH render target " + buffers[i]);
                var target = targets.get(buffers[i]);
                framebuffer.addColorAttachment(i, flipped.contains(buffers[i]) ? target.getAltTexture() : target.getMainTexture());
                attachments[i] = i;
            }
            framebuffer.addDepthAttachment(depth.getTextureId());
            if (buffers.length == 0) framebuffer.noDrawBuffers(); else framebuffer.drawBuffers(attachments);
            LucernaRenderSystem.readBuffer(framebuffer.getId(), buffers.length == 0 ? GL11.GL_NONE : GL30.GL_COLOR_ATTACHMENT0);
            if (!framebuffer.isComplete()) throw new IllegalStateException("Incomplete DH render target framebuffer");
            framebuffers.add(framebuffer);
            return framebuffer;
        } catch (RuntimeException | Error error) { framebuffer.destroy(); throw error; }
    }
    public void copyOpaqueDepth() {
        LucernaRenderSystem.blitFramebuffer(depthFramebuffer.getId(), opaqueFramebuffer.getId(), 0, 0, width, height,
            0, 0, width, height, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
    }
    public void destroy() {
        framebuffers.forEach(GlFramebuffer::destroy);
        depthFramebuffer.destroy(); opaqueFramebuffer.destroy(); depth.destroy(); opaque.destroy();
    }
}
