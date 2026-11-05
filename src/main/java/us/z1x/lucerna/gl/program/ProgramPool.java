package us.z1x.lucerna.gl.program;

import org.lwjgl.opengl.ARBParallelShaderCompile;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.KHRParallelShaderCompile;
import us.z1x.lucerna.gl.shader.ProgramCreator;
import us.z1x.lucerna.gl.shader.ShaderType;
import us.z1x.lucerna.gl.shader.ShaderWorkarounds;
import us.z1x.lucerna.pipeline.transform.PatchShaderType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

// links programs ahead of a pipeline swap without blocking the render thread, a missing program just compiles the normal way
public final class ProgramPool {
    public static final ProgramPool INSTANCE = new ProgramPool();

    private static final int COMPLETION_STATUS = KHRParallelShaderCompile.GL_COMPLETION_STATUS_KHR;
    // the driver picks its own thread count
    private static final int ALL_THREADS = 0xFFFFFFFF;
    private static final long FRAME_BUDGET_NANOS = 4_000_000L;

    // fallback programs share sources, and each pipeline program needs its own link
    private final Map<List<String>, Deque<Integer>> programs = new HashMap<>();
    private final Deque<Map<PatchShaderType, String>> queue = new ArrayDeque<>();
    private final List<Integer> linking = new ArrayList<>();
    private boolean parallel;

    private ProgramPool() {
    }

    public void init() {
        GLCapabilities capabilities = GL.getCapabilities();
        parallel = capabilities.GL_KHR_parallel_shader_compile || capabilities.GL_ARB_parallel_shader_compile;

        if (capabilities.GL_KHR_parallel_shader_compile) {
            KHRParallelShaderCompile.glMaxShaderCompilerThreadsKHR(ALL_THREADS);
        } else if (capabilities.GL_ARB_parallel_shader_compile) {
            ARBParallelShaderCompile.glMaxShaderCompilerThreadsARB(ALL_THREADS);
        }
    }

    public void submit(List<Map<PatchShaderType, String>> sources) {
        queue.addAll(sources);
    }

    // submits and finishes programs within the frame budget, true once everything is linked
    public boolean tick() {
        long deadline = System.nanoTime() + FRAME_BUDGET_NANOS;

        while (!queue.isEmpty() && System.nanoTime() < deadline) {
            link(queue.poll());
        }

        Iterator<Integer> iterator = linking.iterator();

        while (iterator.hasNext() && System.nanoTime() < deadline) {
            int program = iterator.next();

            if (!parallel || isComplete(program)) {
                // the driver finishes a program on the first status query, which takes milliseconds
                GL20.glGetProgrami(program, GL20C.GL_LINK_STATUS);
                iterator.remove();
            }
        }

        return queue.isEmpty() && linking.isEmpty();
    }

    private static boolean isComplete(int program) {
        return GL20.glGetProgrami(program, COMPLETION_STATUS) == GL20C.GL_TRUE;
    }

    private void link(Map<PatchShaderType, String> sources) {
        String vertex = sources.get(PatchShaderType.VERTEX);
        String geometry = sources.get(PatchShaderType.GEOMETRY);
        String fragment = sources.get(PatchShaderType.FRAGMENT);
        List<String> key = Arrays.asList(vertex, geometry, fragment);

        int program = GL20.glCreateProgram();

        ProgramCreator.bindAttributes(program);
        attach(program, ShaderType.VERTEX, vertex);

        if (geometry != null) {
            attach(program, ShaderType.GEOMETRY, geometry);
        }

        attach(program, ShaderType.FRAGMENT, fragment);
        GL20.glLinkProgram(program);
        programs.computeIfAbsent(key, ignored -> new ArrayDeque<>()).add(program);
        linking.add(program);
    }

    private static void attach(int program, ShaderType type, String source) {
        int shader = GL20.glCreateShader(type.id);

        ShaderWorkarounds.safeShaderSource(shader, source);
        GL20.glCompileShader(shader);
        GL20.glAttachShader(program, shader);
        GL20.glDeleteShader(shader);
    }

    // a failed link is left to the normal path, which reports the error
    public int take(String vertex, String geometry, String fragment) {
        Deque<Integer> linked = programs.get(Arrays.asList(vertex, geometry, fragment));

        if (linked == null || linked.isEmpty()) {
            return -1;
        }

        int program = linked.poll();

        if (GL20.glGetProgrami(program, GL20C.GL_LINK_STATUS) != GL20C.GL_TRUE) {
            GL20.glDeleteProgram(program);
            return -1;
        }

        return program;
    }

    public void clear() {
        queue.clear();
        linking.clear();
        programs.values().forEach(linked -> linked.forEach(GL20::glDeleteProgram));
        programs.clear();
    }
}
