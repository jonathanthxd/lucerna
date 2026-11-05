package us.z1x.lucerna.gl.program;

import org.lwjgl.opengl.GL20;
import us.z1x.lucerna.gl.GlResource;
import us.z1x.lucerna.gl.LucernaRenderSystem;

public final class Program extends GlResource {
    private final ProgramUniforms uniforms;
    private final ProgramSamplers samplers;
    private final ProgramImages images;

    Program(int program, ProgramUniforms uniforms, ProgramSamplers samplers, ProgramImages images) {
        super(program);

        this.uniforms = uniforms;
        this.samplers = samplers;
        this.images = images;
    }

    public void use() {
        LucernaRenderSystem.useProgram(getGlId());

        uniforms.update();
        samplers.update();
        images.update();
    }

    public static void unbind() {
        ProgramUniforms.clearActiveUniforms();
        ProgramSamplers.clearActiveSamplers();
        LucernaRenderSystem.useProgram(0);
    }

    public void destroyInternal() {
        GL20.glDeleteProgram(getGlId());
    }

    @Deprecated
    public int getProgramId() {
        return getGlId();
    }

    public int getActiveImages() {
        return images.getActiveImages();
    }
}
