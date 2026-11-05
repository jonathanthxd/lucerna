package us.z1x.lucerna.uniforms;

import org.joml.Matrix4f;
import us.z1x.lucerna.gl.uniform.UniformHolder;
import us.z1x.lucerna.shaderpack.PackDirectives;
import us.z1x.lucerna.shadows.ShadowMatrices;

import java.util.function.Supplier;

import static us.z1x.lucerna.gl.uniform.UniformUpdateFrequency.PER_FRAME;

public final class MatrixUniforms {
    private MatrixUniforms() {
    }

    public static void addMatrixUniforms(UniformHolder uniforms, PackDirectives directives) {
        addMatrix(uniforms, "ModelView", CapturedRenderingState.INSTANCE::getGbufferModelView);
        addMatrix(uniforms, "Projection", CapturedRenderingState.INSTANCE::getGbufferProjection);
        addShadowMatrix(uniforms, "ModelView", () ->
                ShadowMatrices.createShadowModelView(directives.getSunPathRotation(), directives.getShadowDirectives().getIntervalSize()));
        addShadowArrayMatrix(uniforms, "Projection", () -> ShadowMatrices.createOrthoMatrix(directives.getShadowDirectives().getDistance()));
    }

    private static void addMatrix(UniformHolder uniforms, String name, Supplier<Matrix4f> supplier) {
        uniforms
            .uniformMatrix(PER_FRAME, "gbuffer" + name, supplier)
            .uniformMatrix(PER_FRAME, "gbuffer" + name + "Inverse", new Inverted(supplier))
            .uniformMatrix(PER_FRAME, "gbufferPrevious" + name, new Previous(supplier));
    }

    private static void addShadowMatrix(UniformHolder uniforms, String name, Supplier<Matrix4f> supplier) {
        uniforms
                .uniformMatrix(PER_FRAME, "shadow" + name, supplier)
                .uniformMatrix(PER_FRAME, "shadow" + name + "Inverse", new Inverted(supplier));
    }

    private static void addShadowArrayMatrix(UniformHolder uniforms, String name, Supplier<float[]> supplier) {
        uniforms
                .uniformMatrixFromArray(PER_FRAME, "shadow" + name, supplier)
                .uniformMatrix(PER_FRAME, "shadow" + name + "Inverse", new InvertedArrayMatrix(supplier));
    }

    private static class Inverted implements Supplier<Matrix4f> {
        private final Supplier<Matrix4f> parent;
        private final Matrix4f inverted = new Matrix4f();

        Inverted(Supplier<Matrix4f> parent) {
            this.parent = parent;
        }

        @Override
        public Matrix4f get() {
            return parent.get().invert(inverted);
        }
    }

    private static class InvertedArrayMatrix implements Supplier<Matrix4f> {
        private final Supplier<float[]> parent;
        private final Matrix4f inverted = new Matrix4f();

        InvertedArrayMatrix(Supplier<float[]> parent) {
            this.parent = parent;
        }

        @Override
        public Matrix4f get() {
            return inverted.set(parent.get()).invert();
        }
    }

    private static class Previous implements Supplier<Matrix4f> {
        private final Supplier<Matrix4f> parent;
        private final Matrix4f previous = new Matrix4f();
        private final Matrix4f current = new Matrix4f();

        Previous(Supplier<Matrix4f> parent) {
            this.parent = parent;
        }

        @Override
        public Matrix4f get() {
            previous.set(current);
            current.set(parent.get());
            return previous;
        }
    }
}
