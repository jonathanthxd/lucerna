package us.z1x.lucerna.uniforms.custom;

import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.texture.TextureAtlas;
import org.joml.Vector2i;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.gl.uniform.LocationalUniformHolder;
import us.z1x.lucerna.gl.uniform.Uniform;
import us.z1x.lucerna.gl.uniform.UniformUpdateFrequency;
import us.z1x.lucerna.parsing.LucernaFunctions;
import us.z1x.lucerna.parsing.LucernaOptions;
import us.z1x.lucerna.parsing.VectorType;
import us.z1x.lucerna.shaderpack.IdMap;
import us.z1x.lucerna.shaderpack.PackDirectives;
import us.z1x.lucerna.stareval.element.ExpressionElement;
import us.z1x.lucerna.stareval.expression.Expression;
import us.z1x.lucerna.stareval.expression.VariableExpression;
import us.z1x.lucerna.stareval.function.FunctionContext;
import us.z1x.lucerna.stareval.function.FunctionReturn;
import us.z1x.lucerna.stareval.function.Type;
import us.z1x.lucerna.stareval.parser.Parser;
import us.z1x.lucerna.stareval.resolver.ExpressionResolver;
import us.z1x.lucerna.texture.TextureInfoCache;
import us.z1x.lucerna.texture.TextureInfoCache.TextureInfo;
import us.z1x.lucerna.uniforms.CommonUniforms;
import us.z1x.lucerna.uniforms.FrameUpdateNotifier;
import us.z1x.lucerna.uniforms.HardcodedCustomUniforms;
import us.z1x.lucerna.uniforms.custom.cached.CachedUniform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CustomUniforms implements FunctionContext {
    private final Map<String, CachedUniform> variables = new Object2ObjectLinkedOpenHashMap<>();
    private final Map<String, Expression> variablesExpressions = new Object2ObjectLinkedOpenHashMap<>();
    private final CustomUniformFixedInputUniformsHolder inputHolder;
    private final Set<CachedUniform> uniforms = new ObjectOpenHashSet<>();
    private final Set<CachedUniform> assigned = new ObjectOpenHashSet<>();
    private final List<CachedUniform> orderedUniforms;
    private final List<CachedUniform> uniformOrder = new ArrayList<>();
    private final Map<CachedUniform, List<CachedUniform>> dependsOn;
    private final Map<CachedUniform, List<CachedUniform>> requiredBy;
    private boolean optimised;

    private CustomUniforms(CustomUniformFixedInputUniformsHolder inputHolder, CustomUniformFixedInputUniformsHolder hardcodedHolder, Map<String, Builder.Variable> variables) {
        this.inputHolder = inputHolder;
        ExpressionResolver resolver = new ExpressionResolver(
            LucernaFunctions.functions,
            (name) -> {
                Type type = this.inputHolder.getType(name);
                if (type != null)
                    return type;
                Builder.Variable variable = variables.get(name);
                if (variable != null)
                    return variable.type;
                return null;
            },
            true);

        for (Builder.Variable variable : variables.values()) {
            try {
                Expression expression = resolver.resolveExpression(variable.type, variable.expression);
                CachedUniform cachedUniform = CachedUniform
                    .forExpression(variable.name, variable.type, expression, this);
                this.addVariable(expression, cachedUniform);
                if (variable.uniform) {
                    this.uniforms.add(cachedUniform);
                }
            } catch (Exception e) {
                Lucerna.logger
                    .warn("Failed to resolve uniform " + variable.name + ", reason: " + e
                        .getMessage() + " ( = " + variable.expression + ")", e);
            }
        }

        {
            // toposort

            this.dependsOn = new Object2ObjectOpenHashMap<>();
            this.requiredBy = new Object2ObjectOpenHashMap<>();
            Object2IntMap<CachedUniform> dependsOnCount = new Object2IntOpenHashMap<>();

            for (CachedUniform input : this.inputHolder.getAll()) {
                requiredBy.put(input, new ObjectArrayList<>());
            }

            for (CachedUniform input : this.variables.values()) {
                requiredBy.put(input, new ObjectArrayList<>());
            }

            // hardcoded uniforms are only provided when the pack does not define one with the same name
            for (CachedUniform hardcoded : hardcodedHolder.getAll()) {
                if (!this.hasVariable(hardcoded.getName())) {
                    requiredBy.put(hardcoded, new ObjectArrayList<>());
                    this.uniforms.add(hardcoded);
                }
            }

            FunctionReturn functionReturn = new FunctionReturn();
            Set<VariableExpression> requires = new ObjectOpenHashSet<>();
            Set<CachedUniform> brokenUniforms = new ObjectOpenHashSet<>();

            for (Map.Entry<String, Expression> entry : this.variablesExpressions.entrySet()) {
                requires.clear();

                entry.getValue().listVariables(requires);
                if (requires.isEmpty()) {
                    continue;
                }

                CachedUniform uniform = this.variables.get(entry.getKey());

                List<CachedUniform> dependencies = new ArrayList<>();
                for (VariableExpression v : requires) {
                    Expression evaluated = v.partialEval(this, functionReturn);
                    if (evaluated instanceof CachedUniform) {
                        dependencies.add((CachedUniform) evaluated);
                    } else {
                        brokenUniforms.add(uniform);
                    }
                }

                if (dependencies.isEmpty()) {
                    // empty when every dependency is broken
                    continue;
                }

                dependsOn.put(uniform, dependencies);
                dependsOnCount.put(uniform, dependencies.size());

                for (CachedUniform dependency : dependencies) {
                    requiredBy.get(dependency).add(uniform);
                }
            }

            List<CachedUniform> ordered = new ObjectArrayList<>();
            List<CachedUniform> free = new ObjectArrayList<>();

            for (CachedUniform entry : requiredBy.keySet()) {
                if (!dependsOnCount.containsKey(entry)) {
                    free.add(entry);
                }
            }

            while (!free.isEmpty()) {
                CachedUniform pop = free.remove(free.size() - 1);
                if (!brokenUniforms.contains(pop)) {
                    ordered.add(pop);
                } else {
                    brokenUniforms.addAll(requiredBy.get(pop));
                }
                for (CachedUniform dependent : requiredBy.get(pop)) {
                    int count = dependsOnCount.mergeInt(dependent, -1, Integer::sum);
                    assert count >= 0;
                    if (count == 0) {
                        free.add(dependent);
                        dependsOnCount.removeInt(dependent);
                    }
                }
            }

            if (!brokenUniforms.isEmpty()) {
                Lucerna.logger.warn("The following uniforms won't work, either because they are broken, or reference a broken uniform: \n{}", brokenUniforms.stream().map(CachedUniform::getName).collect(Collectors.joining(", ")));
            }

            if (!dependsOnCount.isEmpty()) {
                throw new IllegalStateException("Circular reference detected between: " +
                    dependsOnCount.object2IntEntrySet()
                        .stream()
                        .map(entry -> entry.getKey().getName() + " (" + entry.getIntValue() + ")")
                        .collect(Collectors.joining(", "))
                );
            }

            this.orderedUniforms = ordered;
        }
    }

    private void addVariable(Expression expression, CachedUniform uniform) throws Exception {
        String name = uniform.getName();
        if (this.variables.containsKey(name))
            throw new Exception("Duplicated variable: " + name);
        if (this.inputHolder.containsKey(name))
            throw new Exception("Variable shadows build in uniform: " + name);

        this.variables.put(name, uniform);
        this.variablesExpressions.put(name, expression);
    }

    public void assignTo(LocationalUniformHolder targetHolder) {
        for (CachedUniform uniform : this.orderedUniforms) {
            if (!this.uniforms.contains(uniform)) {
                continue;
            }

            try {
                targetHolder.location(uniform.getName(), Type.convert(uniform.getType())).ifPresent(location -> {
                    targetHolder.addUniform(UniformUpdateFrequency.CUSTOM, new PushedUniform(location, uniform));

                    if (this.assigned.add(uniform)) {
                        this.optimised = false;
                    }
                });
            } catch (Exception e) {
                throw new RuntimeException(uniform.getName(), e);
            }
        }
    }

    public void update() {
        if (!this.optimised) {
            this.optimise();
        }

        for (CachedUniform value : this.uniformOrder) {
            value.update();
        }
    }

    private void optimise() {
        this.optimised = true;

        Object2IntMap<CachedUniform> dependedByCount = new Object2IntOpenHashMap<>();

        for (List<CachedUniform> dependencies : this.dependsOn.values()) {
            for (CachedUniform dependency : dependencies) {
                dependedByCount.mergeInt(dependency, 1, Integer::sum);
            }
        }

        // uniforms assigned to a pass are never removed
        for (CachedUniform cachedUniform : this.assigned) {
            dependedByCount.mergeInt(cachedUniform, 1, Integer::sum);
        }

        Set<CachedUniform> unused = new ObjectOpenHashSet<>();
        for (int i = this.orderedUniforms.size() - 1; i >= 0; i--) {
            CachedUniform uniform = this.orderedUniforms.get(i);
            if (dependedByCount.getInt(uniform) == 0) {
                unused.add(uniform);
                List<CachedUniform> dependencies = this.dependsOn.get(uniform);
                if (dependencies != null) {
                    for (CachedUniform dependency : dependencies) {
                        dependedByCount.computeIntIfPresent(dependency, (key, value) -> value - 1);
                    }
                }
            }
        }

        this.uniformOrder.clear();
        for (CachedUniform uniform : this.orderedUniforms) {
            if (!unused.contains(uniform)) {
                this.uniformOrder.add(uniform);
            }
        }
    }

    @Override
    public boolean hasVariable(String name) {
        return this.inputHolder.containsKey(name) || this.variables.containsKey(name);
    }

    @Override
    public Expression getVariable(String name) {
        final CachedUniform inputUniform = this.inputHolder.getUniform(name);
        if (inputUniform != null)
            return inputUniform;
        final CachedUniform customUniform = this.variables.get(name);
        if (customUniform != null)
            return customUniform;
        throw new RuntimeException("Unknown variable: " + name);
    }

    private static final class PushedUniform extends Uniform {
        private final CachedUniform uniform;
        private int lastPushedVersion;

        private PushedUniform(int location, CachedUniform uniform) {
            super(location);
            this.uniform = uniform;
        }

        @Override
        public void update() {
            // versions start at 1, so the zeroed field pushes on the first bind
            int version = this.uniform.getVersion();

            if (this.lastPushedVersion != version) {
                this.lastPushedVersion = version;
                this.uniform.push(this.location);
            }
        }
    }

    public static class Builder {
        final private static Map<String, Type> types = new ImmutableMap.Builder<String, Type>()
            .put("bool", Type.Boolean)
            .put("float", Type.Float)
            .put("int", Type.Int)
            .put("vec2", VectorType.VEC2)
            .put("vec3", VectorType.VEC3)
            .put("vec4", VectorType.VEC4)
            .build();
        Map<String, Variable> variables = new Object2ObjectLinkedOpenHashMap<>();

        public void addVariable(String type, String name, String expression, boolean isUniform) {
            if (variables.containsKey(name)) {
                Lucerna.logger.warn("Ignoring duplicated custom uniform name: {}", name);
                return;
            }

            Type parsedType = types.get(type);
            if (parsedType == null) {
                Lucerna.logger.warn("Ignoring invalid uniform type: {} of {}", type, name);
                return;
            }

            try {
                ExpressionElement ast = Parser.parse(expression, LucernaOptions.options);
                variables.put(name, new Variable(parsedType, name, ast, isUniform));
            } catch (Exception e) {
                Lucerna.logger.warn("Failed to parse custom variable/uniform {} with expression {}", name, expression, e);
            }
        }

        public CustomUniforms build(IdMap idMap, PackDirectives directives, FrameUpdateNotifier updateNotifier) {
            Lucerna.logger.info("Starting custom uniform resolving");
            CustomUniformFixedInputUniformsHolder.Builder inputs = new CustomUniformFixedInputUniformsHolder.Builder();
            CommonUniforms.addNonDynamicUniforms(inputs, idMap, directives, updateNotifier);
            // constants of optifine's custom uniform expressions, a pack may define its own
            if (!variables.containsKey("pi")) {
                inputs.uniform1f(UniformUpdateFrequency.ONCE, "pi", () -> (float) Math.PI);
            }

            inputs.uniform2i(UniformUpdateFrequency.PER_FRAME, "atlasSize", () -> {
                TextureInfo info = TextureInfoCache.INSTANCE.getInfo(Minecraft.getInstance().getTextureManager().get(TextureAtlas.BLOCKS_LOCATION).getGlId());
                return new Vector2i(info.getWidth(), info.getHeight());
            });
            inputs.uniform1b(UniformUpdateFrequency.ONCE, "true", () -> true);
            inputs.uniform1b(UniformUpdateFrequency.ONCE, "false", () -> false);
            CustomUniformFixedInputUniformsHolder.Builder hardcoded = new CustomUniformFixedInputUniformsHolder.Builder();
            HardcodedCustomUniforms.addHardcodedCustomUniforms(hardcoded, updateNotifier);
            return new CustomUniforms(inputs.build(), hardcoded.build(), this.variables);
        }

        private record Variable(Type type, String name, ExpressionElement expression, boolean uniform) {
        }

    }
}
