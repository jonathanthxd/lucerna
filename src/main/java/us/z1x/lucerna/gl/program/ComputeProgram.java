package us.z1x.lucerna.gl.program;

import org.joml.Vector2f;
import org.joml.Vector3i;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43C;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.gl.GlResource;
import us.z1x.lucerna.gl.LucernaRenderSystem;
import us.z1x.lucerna.pipeline.WorldRenderingPipeline;

public final class ComputeProgram extends GlResource {
    private final ProgramUniforms uniforms;
    private final ProgramSamplers samplers;
    private final ProgramImages images;
    private Vector3i absoluteWorkGroups;
    private Vector2f relativeWorkGroups;
    private int[] localSize;
    private float cachedWidth;
    private float cachedHeight;
    private Vector3i cachedWorkGroups;

    ComputeProgram(int program, ProgramUniforms uniforms, ProgramSamplers samplers, ProgramImages images) {
        super(program);

        localSize = new int[3];
        LucernaRenderSystem.getProgramiv(program, GL43C.GL_COMPUTE_WORK_GROUP_SIZE, localSize);
        this.uniforms = uniforms;
        this.samplers = samplers;
        this.images = images;
    }

    public void setWorkGroupInfo(Vector2f relativeWorkGroups, Vector3i absoluteWorkGroups) {
        this.relativeWorkGroups = relativeWorkGroups;
        this.absoluteWorkGroups = absoluteWorkGroups;
    }

    public Vector3i getWorkGroups(float width, float height) {
        if (cachedWidth != width || cachedHeight != height || cachedWorkGroups == null) {
            this.cachedWidth = width;
            this.cachedHeight = height;
            if (this.absoluteWorkGroups != null) {
                this.cachedWorkGroups = this.absoluteWorkGroups;
            } else if (relativeWorkGroups != null) {
                // best guess at optifine's behavior, the actual localSize is not wanted here
                this.cachedWorkGroups = new Vector3i((int) Math.ceil(Math.ceil((width * relativeWorkGroups.x)) / localSize[0]), (int) Math.ceil(Math.ceil((height * relativeWorkGroups.y)) / localSize[1]), 1);
            } else {
                this.cachedWorkGroups = new Vector3i((int) Math.ceil(width / localSize[0]), (int) Math.ceil(height / localSize[1]), 1);
            }
        }

        return cachedWorkGroups;
    }

    public void dispatch(float width, float height) {
        LucernaRenderSystem.useProgram(getGlId());
        uniforms.update();
        samplers.update();
        images.update();

        if (!Lucerna.getPipelineManager().getPipeline().map(WorldRenderingPipeline::allowConcurrentCompute).orElse(false)) {
            LucernaRenderSystem.memoryBarrier(40);
        }

        LucernaRenderSystem.dispatchCompute(getWorkGroups(width, height));
    }

    public static void unbind() {
        ProgramUniforms.clearActiveUniforms();
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
