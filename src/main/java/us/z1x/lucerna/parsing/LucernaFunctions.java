package us.z1x.lucerna.parsing;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.joml.Vector4i;
import us.z1x.lucerna.stareval.expression.Expression;
import us.z1x.lucerna.stareval.function.AbstractTypedFunction;
import us.z1x.lucerna.stareval.function.B2BFunction;
import us.z1x.lucerna.stareval.function.BB2BFunction;
import us.z1x.lucerna.stareval.function.F2FFunction;
import us.z1x.lucerna.stareval.function.F2IFunction;
import us.z1x.lucerna.stareval.function.FF2BFunction;
import us.z1x.lucerna.stareval.function.FF2FFunction;
import us.z1x.lucerna.stareval.function.FFF2BFunction;
import us.z1x.lucerna.stareval.function.FFF2FFunction;
import us.z1x.lucerna.stareval.function.FunctionContext;
import us.z1x.lucerna.stareval.function.FunctionResolver;
import us.z1x.lucerna.stareval.function.FunctionReturn;
import us.z1x.lucerna.stareval.function.I2IFunction;
import us.z1x.lucerna.stareval.function.II2BFunction;
import us.z1x.lucerna.stareval.function.II2IFunction;
import us.z1x.lucerna.stareval.function.III2BFunction;
import us.z1x.lucerna.stareval.function.III2IFunction;
import us.z1x.lucerna.stareval.function.Type;
import us.z1x.lucerna.stareval.function.TypedFunction;
import us.z1x.lucerna.stareval.function.TypedFunction.Parameter;
import us.z1x.lucerna.stareval.function.V2FFunction;
import us.z1x.lucerna.stareval.function.V2IFunction;

import java.util.Arrays;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class LucernaFunctions {
    public static final FunctionResolver functions;
    static final FunctionResolver.Builder builder = new FunctionResolver.Builder();

    static {
        {
            {
                LucernaFunctions.<I2IFunction>addVectorizable("negate", (a) -> -a);
                LucernaFunctions.<F2FFunction>add("negate", (a) -> -a);

                LucernaFunctions.addUnaryOpJOML("negate", VectorType.VEC2, Vector2f::negate);
                LucernaFunctions.addUnaryOpJOML("negate", VectorType.VEC3, Vector3f::negate);
                LucernaFunctions.addUnaryOpJOML("negate", VectorType.VEC4, Vector4f::negate);
            }
        }
        {
            {
                LucernaFunctions.<II2IFunction>addVectorizable("add", Integer::sum);
                LucernaFunctions.<FF2FFunction>add("add", Float::sum);

                LucernaFunctions.addBinaryOpJOML("add", VectorType.VEC2, Vector2f::add);
                LucernaFunctions.addBinaryOpJOML("add", VectorType.VEC3, Vector3f::add);
                LucernaFunctions.addBinaryOpJOML("add", VectorType.VEC4, Vector4f::add);
            }

            {
                LucernaFunctions.<II2IFunction>addVectorizable("subtract", (a, b) -> a - b);
                LucernaFunctions.<FF2FFunction>add("subtract", (a, b) -> a - b);

                LucernaFunctions.addBinaryOpJOML("subtract", VectorType.VEC2, Vector2f::sub);
                LucernaFunctions.addBinaryOpJOML("subtract", VectorType.VEC3, Vector3f::sub);
                LucernaFunctions.addBinaryOpJOML("subtract", VectorType.VEC4, Vector4f::sub);
            }

            {
                LucernaFunctions.<II2IFunction>addVectorizable("multiply", (a, b) -> a * b);
                LucernaFunctions.<FF2FFunction>add("multiply", (a, b) -> a * b);

                LucernaFunctions.addBinaryOpJOML("multiply", VectorType.VEC2, Vector2f::mul);
                LucernaFunctions.addBinaryOpJOML("multiply", VectorType.VEC3, Vector3f::mul);
                LucernaFunctions.addBinaryOpJOML("multiply", VectorType.VEC4, Vector4f::mul);

                LucernaFunctions.<Vector2f>addScalarOpJOML("multiply", VectorType.VEC2, Vector2f::mul, true);
                LucernaFunctions.<Vector3f>addScalarOpJOML("multiply", VectorType.VEC3, Vector3f::mul, true);
                LucernaFunctions.<Vector4f>addScalarOpJOML("multiply", VectorType.VEC4, Vector4f::mul, true);
            }

            {
                LucernaFunctions.<FF2FFunction>add("divide", (a, b) -> a / b);

                LucernaFunctions.addBinaryOpJOML("divide", VectorType.VEC2, Vector2f::div);
                LucernaFunctions.addBinaryOpJOML("divide", VectorType.VEC3, Vector3f::div);
                LucernaFunctions.addBinaryOpJOML("divide", VectorType.VEC4, Vector4f::div);

                LucernaFunctions.<Vector2f>addScalarOpJOML("divide", VectorType.VEC2, Vector2f::div, false);
                LucernaFunctions.<Vector3f>addScalarOpJOML("divide", VectorType.VEC3, Vector3f::div, false);
                LucernaFunctions.<Vector4f>addScalarOpJOML("divide", VectorType.VEC4, Vector4f::div, false);
            }

            {
                LucernaFunctions.<II2IFunction>addVectorizable("remainder", (a, b) -> a % b);
                LucernaFunctions.<FF2FFunction>add("remainder", (a, b) -> a % b);
            }
        }
        {
            LucernaFunctions.<II2BFunction>addBooleanVectorizable("equals", (a, b) -> a == b);
            LucernaFunctions.<FF2BFunction>add("equals", (a, b) -> a == b);

            LucernaFunctions.addBinaryToBooleanOpJOML("equal", VectorType.VEC2, false, Vector2f::equals);
            LucernaFunctions.addBinaryToBooleanOpJOML("equal", VectorType.VEC3, false, Vector3f::equals);
            LucernaFunctions.addBinaryToBooleanOpJOML("equal", VectorType.VEC4, false, Vector4f::equals);

            LucernaFunctions.<II2BFunction>addBooleanVectorizable("notEquals", (a, b) -> a != b);
            LucernaFunctions.<FF2BFunction>add("notEquals", (a, b) -> a != b);

            LucernaFunctions.addBinaryToBooleanOpJOML("equal", VectorType.VEC2, true, Vector2f::equals);
            LucernaFunctions.addBinaryToBooleanOpJOML("equal", VectorType.VEC3, true, Vector3f::equals);
            LucernaFunctions.addBinaryToBooleanOpJOML("equal", VectorType.VEC4, true, Vector4f::equals);

            LucernaFunctions.<II2BFunction>add("lessThanOrEquals", (a, b) -> a <= b);
            LucernaFunctions.<FF2BFunction>add("lessThanOrEquals", (a, b) -> a <= b);

            LucernaFunctions.<II2BFunction>add("moreThanOrEquals", (a, b) -> a >= b);
            LucernaFunctions.<FF2BFunction>add("moreThanOrEquals", (a, b) -> a >= b);

            LucernaFunctions.<II2BFunction>add("lessThan", (a, b) -> a < b);
            LucernaFunctions.<FF2BFunction>add("lessThan", (a, b) -> a < b);

            LucernaFunctions.<II2BFunction>add("moreThan", (a, b) -> a > b);
            LucernaFunctions.<FF2BFunction>add("moreThan", (a, b) -> a > b);
        }
        {

            LucernaFunctions.<BB2BFunction>addVectorizable("equals", (a, b) -> a == b);
            LucernaFunctions.<BB2BFunction>addVectorizable("notEquals", (a, b) -> a != b);
            LucernaFunctions.<BB2BFunction>addVectorizable("and", (a, b) -> a && b);
            LucernaFunctions.<BB2BFunction>addVectorizable("or", (a, b) -> a || b);
            LucernaFunctions.<B2BFunction>addVectorizable("not", (a) -> !a);
        }

        {
            // these are also vectorizable in glsl, see page 4 of http://learnwebgl.brown37.net/12_shader_language/documents/webgl-reference-card-1_0.pdf

            {
                // optifine
                LucernaFunctions.<F2FFunction>add("torad", (a) -> (float) Math.toRadians(a));
                LucernaFunctions.<F2FFunction>add("todeg", (a) -> (float) Math.toDegrees(a));

                LucernaFunctions.<F2FFunction>add("radians", (a) -> (float) Math.toRadians(a));
                LucernaFunctions.<F2FFunction>add("degrees", (a) -> (float) Math.toDegrees(a));

                LucernaFunctions.<F2FFunction>add("sin", (a) -> (float) Math.sin(a));
                LucernaFunctions.<F2FFunction>add("cos", (a) -> (float) Math.cos(a));
                LucernaFunctions.<F2FFunction>add("tan", (a) -> (float) Math.tan(a));
                LucernaFunctions.<F2FFunction>add("asin", (a) -> (float) Math.asin(a));
                LucernaFunctions.<F2FFunction>add("acos", (a) -> (float) Math.acos(a));
                LucernaFunctions.<F2FFunction>add("atan", (a) -> (float) Math.atan(a));
                LucernaFunctions.<FF2FFunction>add("atan", (y, x) -> (float) Math.atan2(y, x));
                // optifine
                LucernaFunctions.<FF2FFunction>add("atan2", (y, x) -> (float) Math.atan2(y, x));
            }
            {
                LucernaFunctions.<FF2FFunction>add("pow", (a, b) -> (float) Math.pow(a, b));
                LucernaFunctions.<F2FFunction>add("exp", (a) -> (float) Math.exp(a));
                LucernaFunctions.<F2FFunction>add("log", (a) -> (float) Math.log(a));
                // java has no built in exp2 or log2: https://bugs.java.com/bugdatabase/view_bug.do?bug_id=4851627
                LucernaFunctions.<F2FFunction>add("exp2", (a) -> (float) Math.pow(2, a));
                LucernaFunctions.<F2FFunction>add("log2", (a) -> (float) (Math.log(a) / Math.log(2)));

                LucernaFunctions.<F2FFunction>add("sqrt", (a) -> (float) Math.sqrt(a));

                // optifine
                LucernaFunctions.<F2FFunction>add("log10", (a) -> (float) Math.log10(a));

                LucernaFunctions.<FF2FFunction>add("log",
                    (base, value) -> (float) (Math.log(value) / Math.log(base)));

                LucernaFunctions.<F2FFunction>add("exp10", (a) -> (float) Math.pow(10, a));

            }

            {
                LucernaFunctions.<I2IFunction>addVectorizable("abs", Math::abs);
                LucernaFunctions.<F2FFunction>add("abs", Math::abs);

                LucernaFunctions.addUnaryOpJOML("abs", VectorType.VEC2, Vector2f::absolute);
                LucernaFunctions.addUnaryOpJOML("abs", VectorType.VEC3, Vector3f::absolute);
                LucernaFunctions.addUnaryOpJOML("abs", VectorType.VEC4, Vector4f::absolute);

                LucernaFunctions.<F2FFunction>add("sign", Math::signum);

                // optifine
                LucernaFunctions.<F2FFunction>add("signum", Math::signum);

                // an int overload of floor avoids a float round trip that changes large results

                LucernaFunctions.<F2FFunction>add("floor", (a) -> (float) Math.floor(a));
                LucernaFunctions.<F2IFunction>add("floor", (a) -> (int) Math.floor(a));

                LucernaFunctions.addUnaryOpJOML("floor", VectorType.VEC2, Vector2f::floor);
                LucernaFunctions.addUnaryOpJOML("floor", VectorType.VEC3, Vector3f::floor);
                LucernaFunctions.addUnaryOpJOML("floor", VectorType.VEC4, Vector4f::floor);

                LucernaFunctions.<F2FFunction>add("ceil", (a) -> (float) Math.ceil(a));
                LucernaFunctions.<F2IFunction>add("ceil", (a) -> (int) Math.ceil(a));

                LucernaFunctions.addUnaryOpJOML("ceil", VectorType.VEC2, Vector2f::ceil);
                LucernaFunctions.addUnaryOpJOML("ceil", VectorType.VEC3, Vector3f::ceil);
                LucernaFunctions.addUnaryOpJOML("ceil", VectorType.VEC4, Vector4f::ceil);

                LucernaFunctions.<F2FFunction>add("frac", (a) -> (float) (a - Math.floor(a)));

                LucernaFunctions.<II2IFunction>addVectorizable("min", Math::min);
                LucernaFunctions.<FF2FFunction>add("min", Math::min);

                LucernaFunctions.addBinaryOpJOML("min", VectorType.VEC2, Vector2f::min);
                LucernaFunctions.addBinaryOpJOML("min", VectorType.VEC3, Vector3f::min);
                LucernaFunctions.addBinaryOpJOML("min", VectorType.VEC4, Vector4f::min);

                LucernaFunctions.<II2IFunction>addVectorizable("max", Math::max);
                LucernaFunctions.<FF2FFunction>add("max", Math::max);

                LucernaFunctions.addBinaryOpJOML("max", VectorType.VEC2, Vector2f::max);
                LucernaFunctions.addBinaryOpJOML("max", VectorType.VEC3, Vector3f::max);
                LucernaFunctions.addBinaryOpJOML("max", VectorType.VEC4, Vector4f::max);

                {
                    // fake varargs
                    for (int length = 3; length <= 16; length++) {
                        {
                            Type[] inputs = new Type[length];
                            Arrays.fill(inputs, Type.Float);
                            LucernaFunctions.add("min", new AbstractTypedFunction(Type.Float, inputs) {
                                @Override
                                public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                                    params[0].evaluateTo(context, functionReturn);
                                    float min = functionReturn.floatReturn;
                                    for (int i = 1; i < params.length; i++) {
                                        params[1].evaluateTo(context, functionReturn);
                                        min = Math.min(min, functionReturn.floatReturn);
                                    }
                                    functionReturn.floatReturn = min;
                                }
                            });
                        }
                        {
                            Type[] inputs = new Type[length];
                            Arrays.fill(inputs, Type.Float);
                            LucernaFunctions.add("max", new AbstractTypedFunction(Type.Float, inputs) {
                                @Override
                                public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                                    params[0].evaluateTo(context, functionReturn);
                                    float max = functionReturn.floatReturn;
                                    for (int i = 1; i < params.length; i++) {
                                        params[1].evaluateTo(context, functionReturn);
                                        max = Math.max(max, functionReturn.floatReturn);
                                    }
                                    functionReturn.floatReturn = max;
                                }
                            });
                        }
                        {
                            Type[] inputs = new Type[length];
                            Arrays.fill(inputs, Type.Int);
                            LucernaFunctions.addVectorizable("min", new AbstractTypedFunction(Type.Int, inputs) {
                                @Override
                                public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                                    params[0].evaluateTo(context, functionReturn);
                                    int min = functionReturn.intReturn;
                                    for (int i = 1; i < params.length; i++) {
                                        params[1].evaluateTo(context, functionReturn);
                                        min = Math.min(min, functionReturn.intReturn);
                                    }
                                    functionReturn.intReturn = min;
                                }
                            });
                        }
                        {
                            Type[] inputs = new Type[length];
                            Arrays.fill(inputs, Type.Int);
                            LucernaFunctions.addVectorizable("max", new AbstractTypedFunction(Type.Int, inputs) {
                                @Override
                                public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                                    params[0].evaluateTo(context, functionReturn);
                                    int max = functionReturn.intReturn;
                                    for (int i = 1; i < params.length; i++) {
                                        params[1].evaluateTo(context, functionReturn);
                                        max = Math.max(max, functionReturn.intReturn);
                                    }
                                    functionReturn.intReturn = max;
                                }
                            });
                        }
                    }
                }

                // undefined when max < min
                LucernaFunctions.<III2IFunction>addVectorizable("clamp",
                    (val, min, max) -> Math.max(min, Math.min(max, val)));
                LucernaFunctions.<FFF2FFunction>add("clamp",
                    (val, min, max) -> Math.max(min, Math.min(max, val)));
                LucernaFunctions.addTernaryOpJOML("clamp", VectorType.VEC2, (val, min, max, dest) -> {
                    val.min(max, dest);
                    dest.max(min);
                });
                LucernaFunctions.addTernaryOpJOML("clamp", VectorType.VEC3, (val, min, max, dest) -> {
                    val.min(max, dest);
                    dest.max(min);
                });
                LucernaFunctions.addTernaryOpJOML("clamp", VectorType.VEC4, (val, min, max, dest) -> {
                    val.min(max, dest);
                    dest.max(min);
                });

                LucernaFunctions.<FFF2FFunction>add("mix", (x, y, a) -> x + (y - x) * a);

                LucernaFunctions.<II2IFunction>addVectorizable("edge", (edge, x) -> (x < edge) ? 0 : 1);
                LucernaFunctions.<FF2FFunction>add("edge", (edge, x) -> (x < edge) ? 0 : 1);
            }

            {
            }

            {
            }

            {
            }

            {
                {
                    LucernaFunctions.<II2IFunction>addVectorizable("fmod", Math::floorMod);
                    LucernaFunctions.<FF2FFunction>add("fmod", (a, b) -> (a % b + b) % b);
                }
                {
                    Random random = new Random();
                    // randomInt(), randomInt(int bound), randomInt(int inclusiveMin, int exclusiveMax)
                    LucernaFunctions.<V2IFunction>addVectorizable("randomInt", random::nextInt);
                    LucernaFunctions.<I2IFunction>addVectorizable("randomInt", random::nextInt);
                    LucernaFunctions.<II2IFunction>addVectorizable("randomInt", (a, b) -> random.nextInt(b - a) + a);

                    // random, random(float min, float max)
                    LucernaFunctions.<V2FFunction>add("random", random::nextFloat);
                    LucernaFunctions.<FF2FFunction>add("random", (min, max) ->
                        min + random.nextFloat() * (max - min));
                }
                {
                    // if(boolean, primitive, primitive) -> primitive and if(boolean, xvec, xvec) -> xvec
                    for (Type.Primitive type : Type.AllPrimitives) {
                        add("if", new AbstractTypedFunction(type, new Type[]{Type.Boolean, type, type}) {
                            @Override
                            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                                params[0].evaluateTo(context, functionReturn);

                                params[
                                    functionReturn.booleanReturn ? 1 : 2
                                    ].evaluateTo(context, functionReturn);

                            }
                        });
                    }

                    for (Type type : VectorType.AllVectorTypes) {
                        add("if", new AbstractTypedFunction(type, new Type[]{Type.Boolean, type, type}) {
                            @Override
                            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                                params[0].evaluateTo(context, functionReturn);

                                params[
                                    functionReturn.booleanReturn ? 1 : 2
                                    ].evaluateTo(context, functionReturn);

                            }
                        });
                    }

                    {
                        // fake varargs
                        for (int length = 2; length <= 16; length++) {
                            for (Type.Primitive type : Type.AllPrimitives) {
                                Type[] params = new Type[length * 2 + 1];
                                for (int i = 0; i < length * 2; i += 2) {
                                    params[i] = Type.Boolean;
                                    params[i + 1] = type;
                                }
                                params[length * 2] = type;
                                int finalLength = length * 2;
                                add("if", new AbstractTypedFunction(type, params) {
                                    @Override
                                    public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                                        for (int i = 0; i < finalLength; i += 2) {
                                            params[i].evaluateTo(context, functionReturn);
                                            if (functionReturn.booleanReturn) {
                                                params[i + 1].evaluateTo(context, functionReturn);
                                                return;
                                            }
                                            params[finalLength].evaluateTo(context, functionReturn);
                                        }
                                    }
                                });
                            }

                            for (Type type : VectorType.AllVectorTypes) {
                                add("if", new AbstractTypedFunction(type, new Type[]{Type.Boolean, type, type}) {
                                    @Override
                                    public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                                        params[0].evaluateTo(context, functionReturn);

                                        params[
                                            functionReturn.booleanReturn ? 1 : 2
                                            ].evaluateTo(context, functionReturn);

                                    }
                                });
                            }
                        }
                    }
                }
                {
                    // smooth(target)
                    builder.addDynamicFunction("smooth", Type.Float, () ->
                        new AbstractTypedFunction(
                            Type.Float,
                            new Parameter[]{
                                new Parameter(Type.Float, false),
                            },
                            0,
                            false
                        ) {
                            private final SmoothFloat smoothFloat = new SmoothFloat();

                            @Override
                            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                                params[0].evaluateTo(context, functionReturn);
                                float target = functionReturn.floatReturn;
                                functionReturn.floatReturn = smoothFloat.updateAndGet(
                                    target,
                                    1,
                                    1
                                );
                            }
                        });

                    // smooth(id, target)
                    builder.addDynamicFunction("smooth", Type.Float, () ->
                        new AbstractTypedFunction(
                            Type.Float,
                            new Parameter[]{
                                new Parameter(Type.Float, true), // id for backward compat with optifine
                                new Parameter(Type.Float, false),
                            },
                            1,
                            false
                        ) {
                            private final SmoothFloat smoothFloat = new SmoothFloat();

                            @Override
                            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                                params[1].evaluateTo(context, functionReturn);
                                float target = functionReturn.floatReturn;
                                functionReturn.floatReturn = smoothFloat.updateAndGet(
                                    target,
                                    1,
                                    1
                                );
                            }
                        });

                    // smooth(target, fadeTime)
                    builder.addDynamicFunction("smooth", Type.Float, () ->
                        new AbstractTypedFunction(
                            Type.Float,
                            new Parameter[]{
                                new Parameter(Type.Float, false),
                                new Parameter(Type.Float, false),
                            },
                            0,
                            false
                        ) {
                            private final SmoothFloat smoothFloat = new SmoothFloat();

                            @Override
                            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                                params[0].evaluateTo(context, functionReturn);
                                float target = functionReturn.floatReturn;

                                params[1].evaluateTo(context, functionReturn);
                                float fadeTime = functionReturn.floatReturn;

                                functionReturn.floatReturn = smoothFloat.updateAndGet(
                                    target,
                                    fadeTime,
                                    fadeTime
                                );
                            }
                        });

                    // smooth(id, target, fadeTime)
                    builder.addDynamicFunction("smooth", Type.Float, () ->
                        new AbstractTypedFunction(
                            Type.Float,
                            new Parameter[]{
                                new Parameter(Type.Float, true), // id for backward compat with optifine
                                new Parameter(Type.Float, false),
                                new Parameter(Type.Float, false),
                            },
                            1,
                            false
                        ) {
                            private final SmoothFloat smoothFloat = new SmoothFloat();

                            @Override
                            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                                params[1].evaluateTo(context, functionReturn);
                                float target = functionReturn.floatReturn;

                                params[2].evaluateTo(context, functionReturn);
                                float fadeTime = functionReturn.floatReturn;

                                functionReturn.floatReturn = smoothFloat.updateAndGet(
                                    target,
                                    fadeTime,
                                    fadeTime
                                );
                            }
                        });

                    // smooth(target, fadeUpTime, fadeDownTime)
                    builder.addDynamicFunction("smooth", Type.Float, () ->
                        new AbstractTypedFunction(
                            Type.Float,
                            new Parameter[]{
                                new Parameter(Type.Float, false),
                                new Parameter(Type.Float, false),
                                new Parameter(Type.Float, false),
                            },
                            0,
                            false
                        ) {
                            private final SmoothFloat smoothFloat = new SmoothFloat();

                            @Override
                            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                                params[0].evaluateTo(context, functionReturn);
                                float target = functionReturn.floatReturn;

                                params[1].evaluateTo(context, functionReturn);
                                float fadeUpTime = functionReturn.floatReturn;
                                params[2].evaluateTo(context, functionReturn);
                                float fadeDownTime = functionReturn.floatReturn;

                                functionReturn.floatReturn = smoothFloat.updateAndGet(
                                    target,
                                    fadeUpTime,
                                    fadeDownTime
                                );
                            }
                        });

                    // smooth(id, target, fadeUpTime, fadeDownTime)
                    builder.addDynamicFunction("smooth", Type.Float, () ->
                        new AbstractTypedFunction(
                            Type.Float,
                            new Parameter[]{
                                new Parameter(Type.Float, true), // id for backward compat with optifine
                                new Parameter(Type.Float, false),
                                new Parameter(Type.Float, false),
                                new Parameter(Type.Float, false),
                            },
                            1,
                            false
                        ) {
                            private final SmoothFloat smoothFloat = new SmoothFloat();

                            @Override
                            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                                params[1].evaluateTo(context, functionReturn);
                                float target = functionReturn.floatReturn;

                                params[2].evaluateTo(context, functionReturn);
                                float fadeUpTime = functionReturn.floatReturn;
                                params[3].evaluateTo(context, functionReturn);
                                float fadeDownTime = functionReturn.floatReturn;

                                functionReturn.floatReturn = smoothFloat.updateAndGet(
                                    target,
                                    fadeUpTime,
                                    fadeDownTime
                                );
                            }
                        });
                }
            }
        }

        {
            addImplicitCast(Type.Int, Type.Float, r -> {
                r.floatReturn = r.intReturn;
            });
            addExplicitCast(Type.Float, Type.Int, r -> {
                r.intReturn = (int) r.floatReturn;
            });
        }

        {
            LucernaFunctions.<III2BFunction>add("between", (a, min, max) -> a >= min && a <= max);
            LucernaFunctions.<FFF2BFunction>add("between", (a, min, max) -> a >= min && a <= max);

            LucernaFunctions.<FFF2BFunction>add("equals", (a, b, epsilon) -> Math.abs(a - b) <= epsilon);

            {
                // fake varargs
                for (int length = 2; length <= 32; length++) {
                    Type[] params = new Type[length];
                    Arrays.fill(params, Type.Float);
                    int finalLength = length;
                    LucernaFunctions.add("in", new AbstractTypedFunction(
                        Type.Boolean,
                        params
                    ) {
                        @Override
                        public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                            params[0].evaluateTo(context, functionReturn);
                            float value = functionReturn.floatReturn;
                            for (int i = 1; i < finalLength; i++) {
                                params[i].evaluateTo(context, functionReturn);
                                if (functionReturn.floatReturn == value) {
                                    functionReturn.booleanReturn = true;
                                    return;
                                }
                            }
                            functionReturn.booleanReturn = false;
                        }
                    });
                }
            }
        }

        {
            for (Type.Primitive type : new Type.Primitive[]{Type.Boolean, Type.Int}) {
                for (int size = 2; size <= 4; size++) {
                    TypedFunction function = new VectorConstructor(type, size);
                    add(
                        Character.toLowerCase(
                            type.getClass().getSimpleName().charAt(0)
                        ) + "vec" + size, function);
                }
            }

            add("vec2", new AbstractTypedFunction(
                VectorType.VEC2,
                new Type[]{Type.Float, Type.Float}
            ) {
                @Override
                public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                    params[0].evaluateTo(context, functionReturn);
                    float x = functionReturn.floatReturn;

                    params[1].evaluateTo(context, functionReturn);
                    float y = functionReturn.floatReturn;

                    functionReturn.objectReturn = new Vector2f(x, y);
                }
            });

            add("vec3", new AbstractTypedFunction(
                VectorType.VEC3,
                new Type[]{Type.Float, Type.Float, Type.Float}
            ) {
                @Override
                public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                    params[0].evaluateTo(context, functionReturn);
                    float x = functionReturn.floatReturn;

                    params[1].evaluateTo(context, functionReturn);
                    float y = functionReturn.floatReturn;

                    params[2].evaluateTo(context, functionReturn);
                    float z = functionReturn.floatReturn;

                    functionReturn.objectReturn = new Vector3f(x, y, z);
                }
            });

            add("vec4", new AbstractTypedFunction(
                VectorType.VEC4,
                new Type[]{Type.Float, Type.Float, Type.Float, Type.Float}
            ) {
                @Override
                public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                    params[0].evaluateTo(context, functionReturn);
                    float x = functionReturn.floatReturn;

                    params[1].evaluateTo(context, functionReturn);
                    float y = functionReturn.floatReturn;

                    params[2].evaluateTo(context, functionReturn);
                    float z = functionReturn.floatReturn;

                    params[3].evaluateTo(context, functionReturn);
                    float w = functionReturn.floatReturn;

                    functionReturn.objectReturn = new Vector4f(x, y, z, w);
                }
            });
        }

        {
            String[][] accessNames = new String[][]{
                new String[]{"0", "r", "x", "s"},
                new String[]{"1", "g", "y", "t"},
                new String[]{"2", "b", "z", "p"},
                new String[]{"3", "a", "w", "q"}
            };

            {
                for (String access : accessNames[0]) {
                    LucernaFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
                        Type.Float,
                        new Type[]{VectorType.VEC2}
                    ) {
                        @Override
                        public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                            params[0].evaluateTo(context, functionReturn);
                            functionReturn.floatReturn = ((Vector2f) functionReturn.objectReturn).x;
                        }
                    });

                    LucernaFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
                        Type.Int,
                        new Type[]{VectorType.I_VEC2}
                    ) {
                        @Override
                        public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                            params[0].evaluateTo(context, functionReturn);
                            functionReturn.intReturn = ((Vector2i) functionReturn.objectReturn).x;
                        }
                    });

                    LucernaFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
                        Type.Float,
                        new Type[]{VectorType.VEC3}
                    ) {
                        @Override
                        public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                            params[0].evaluateTo(context, functionReturn);
                            functionReturn.floatReturn = ((Vector3f) functionReturn.objectReturn).x;
                        }
                    });

                    LucernaFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
                        Type.Int,
                        new Type[]{VectorType.I_VEC3}
                    ) {
                        @Override
                        public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                            params[0].evaluateTo(context, functionReturn);
                            functionReturn.intReturn = ((Vector3i) functionReturn.objectReturn).x;
                        }
                    });

                    LucernaFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
                        Type.Float,
                        new Type[]{VectorType.VEC4}
                    ) {
                        @Override
                        public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                            params[0].evaluateTo(context, functionReturn);
                            functionReturn.floatReturn = ((Vector4f) functionReturn.objectReturn).x;
                        }
                    });

                    LucernaFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
                        Type.Int,
                        new Type[]{VectorType.I_VEC4}
                    ) {
                        @Override
                        public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                            params[0].evaluateTo(context, functionReturn);
                            functionReturn.intReturn = ((Vector4i) functionReturn.objectReturn).x;
                        }
                    });
                }
            }

            {
                for (String access : accessNames[1]) {
                    LucernaFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
                        Type.Float,
                        new Type[]{VectorType.VEC2}
                    ) {
                        @Override
                        public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                            params[0].evaluateTo(context, functionReturn);
                            functionReturn.floatReturn = ((Vector2f) functionReturn.objectReturn).y;
                        }
                    });

                    LucernaFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
                        Type.Int,
                        new Type[]{VectorType.I_VEC2}
                    ) {
                        @Override
                        public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                            params[0].evaluateTo(context, functionReturn);
                            functionReturn.intReturn = ((Vector2i) functionReturn.objectReturn).y;
                        }
                    });

                    LucernaFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
                        Type.Float,
                        new Type[]{VectorType.VEC3}
                    ) {
                        @Override
                        public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                            params[0].evaluateTo(context, functionReturn);
                            functionReturn.floatReturn = ((Vector3f) functionReturn.objectReturn).y;
                        }
                    });

                    LucernaFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
                        Type.Int,
                        new Type[]{VectorType.I_VEC3}
                    ) {
                        @Override
                        public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                            params[0].evaluateTo(context, functionReturn);
                            functionReturn.intReturn = ((Vector3i) functionReturn.objectReturn).y;
                        }
                    });

                    LucernaFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
                        Type.Float,
                        new Type[]{VectorType.VEC4}
                    ) {
                        @Override
                        public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                            params[0].evaluateTo(context, functionReturn);
                            functionReturn.floatReturn = ((Vector4f) functionReturn.objectReturn).y;
                        }
                    });

                    LucernaFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
                        Type.Int,
                        new Type[]{VectorType.I_VEC4}
                    ) {
                        @Override
                        public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                            params[0].evaluateTo(context, functionReturn);
                            functionReturn.intReturn = ((Vector4i) functionReturn.objectReturn).y;
                        }
                    });
                }
            }

            {
                for (String access : accessNames[2]) {
                    LucernaFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
                        Type.Float,
                        new Type[]{VectorType.VEC3}
                    ) {
                        @Override
                        public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                            params[0].evaluateTo(context, functionReturn);
                            functionReturn.floatReturn = ((Vector3f) functionReturn.objectReturn).z;
                        }
                    });

                    LucernaFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
                        Type.Int,
                        new Type[]{VectorType.I_VEC3}
                    ) {
                        @Override
                        public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                            params[0].evaluateTo(context, functionReturn);
                            functionReturn.intReturn = ((Vector3i) functionReturn.objectReturn).z;
                        }
                    });

                    LucernaFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
                        Type.Float,
                        new Type[]{VectorType.VEC4}
                    ) {
                        @Override
                        public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                            params[0].evaluateTo(context, functionReturn);
                            functionReturn.floatReturn = ((Vector4f) functionReturn.objectReturn).z;
                        }
                    });

                    LucernaFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
                        Type.Int,
                        new Type[]{VectorType.I_VEC4}
                    ) {
                        @Override
                        public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                            params[0].evaluateTo(context, functionReturn);
                            functionReturn.intReturn = ((Vector4i) functionReturn.objectReturn).z;
                        }
                    });
                }
            }

            {
                for (String access : accessNames[3]) {
                    LucernaFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
                        Type.Float,
                        new Type[]{VectorType.VEC4}
                    ) {
                        @Override
                        public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                            params[0].evaluateTo(context, functionReturn);
                            functionReturn.floatReturn = ((Vector4f) functionReturn.objectReturn).w;
                        }
                    });

                    LucernaFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
                        Type.Int,
                        new Type[]{VectorType.I_VEC4}
                    ) {
                        @Override
                        public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                            params[0].evaluateTo(context, functionReturn);
                            functionReturn.intReturn = ((Vector4i) functionReturn.objectReturn).w;
                        }
                    });
                }
            }

            {
                for (int i = 0; i < 4; i++) {
                    for (String access : accessNames[i]) {
                        int finalI = i;
                        LucernaFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
                            VectorType.VEC4,
                            new Type[]{MatrixType.MAT4}
                        ) {
                            @Override
                            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                                params[0].evaluateTo(context, functionReturn);
                                functionReturn.objectReturn = ((Matrix4f) functionReturn.objectReturn).getColumn(finalI, new Vector4f());
                            }
                        });
                    }
                }
            }
        }

        functions = builder.build();
    }

    static <T extends TypedFunction> void addVectorized(String name, T function) {
        if (function.getReturnType() instanceof Type.Primitive) {
            add(name, new VectorizedFunction(function, 2));
            add(name, new VectorizedFunction(function, 3));
            add(name, new VectorizedFunction(function, 4));
        } else {
            throw new IllegalArgumentException(name + " is not vectorizable");
        }
    }

    static <T extends TypedFunction> void addVectorizable(String name, T function) {
        add(name, function);
        addVectorized(name, function);
    }

    static <T extends TypedFunction> void addBooleanVectorizable(String name, T function) {
        assert function.getReturnType().equals(Type.Boolean);
        add(name, function);
        if (function.getReturnType() instanceof Type.Primitive) {
            add(name, new BooleanVectorizedFunction(function, 2));
            add(name, new BooleanVectorizedFunction(function, 3));
            add(name, new BooleanVectorizedFunction(function, 4));
        } else {
            throw new IllegalArgumentException(name + " is not vectorizable");
        }
    }

    static <T> void addUnaryOpJOML(String name, VectorType.JOMLVector<T> type, BiConsumer<T, T> function) {
        builder.add(name, new AbstractTypedFunction(
            type,
            new Type[]{type}
        ) {
            final private T vector = type.create();

            @SuppressWarnings("unchecked")
            @Override
            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                params[0].evaluateTo(context, functionReturn);
                T a = (T) functionReturn.objectReturn;

                function.accept(a, this.vector);
                functionReturn.objectReturn = this.vector;
            }
        });
    }

    static <T> void addBinaryOpJOML(String name, VectorType.JOMLVector<T> type, TriConsumer<T, T, T> function) {
        builder.add(name, new AbstractTypedFunction(
            type,
            new Type[]{type, type}
        ) {
            final private T vector = type.create();

            @SuppressWarnings("unchecked")
            @Override
            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                params[0].evaluateTo(context, functionReturn);
                T a = (T) functionReturn.objectReturn;

                params[1].evaluateTo(context, functionReturn);
                T b = (T) functionReturn.objectReturn;

                function.accept(a, b, this.vector);
                functionReturn.objectReturn = this.vector;
            }
        });
    }

    // vector with scalar, and scalar with vector when the operation commutes
    static <T> void addScalarOpJOML(String name, VectorType.JOMLVector<T> type, ScalarOp<T> function, boolean commutes) {
        builder.add(name, new AbstractTypedFunction(type, new Type[]{type, Type.Float}) {
            final private T vector = type.create();

            @SuppressWarnings("unchecked")
            @Override
            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                params[1].evaluateTo(context, functionReturn);
                float b = functionReturn.floatReturn;

                params[0].evaluateTo(context, functionReturn);
                function.accept((T) functionReturn.objectReturn, b, this.vector);
                functionReturn.objectReturn = this.vector;
            }
        });

        if (commutes) {
            builder.add(name, new AbstractTypedFunction(type, new Type[]{Type.Float, type}) {
                final private T vector = type.create();

                @SuppressWarnings("unchecked")
                @Override
                public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                    params[0].evaluateTo(context, functionReturn);
                    float a = functionReturn.floatReturn;

                    params[1].evaluateTo(context, functionReturn);
                    function.accept((T) functionReturn.objectReturn, a, this.vector);
                    functionReturn.objectReturn = this.vector;
                }
            });
        }
    }

    static <T> void addTernaryOpJOML(String name, VectorType.JOMLVector<T> type, QuadConsumer<T, T, T, T> function) {
        builder.add(name, new AbstractTypedFunction(
            type,
            new Type[]{type, type, type}
        ) {
            final private T vector = type.create();

            @SuppressWarnings("unchecked")
            @Override
            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                params[0].evaluateTo(context, functionReturn);
                T a = (T) functionReturn.objectReturn;

                params[1].evaluateTo(context, functionReturn);
                T b = (T) functionReturn.objectReturn;

                params[2].evaluateTo(context, functionReturn);
                T c = (T) functionReturn.objectReturn;

                function.accept(a, b, c, this.vector);
                functionReturn.objectReturn = this.vector;
            }
        });
    }

    static <T> void addBinaryToBooleanOpJOML(
        String name,
        VectorType.JOMLVector<T> type,
        boolean inverted,
        ObjectObject2BooleanFunction<T, T> function) {
        builder.add(name, new AbstractTypedFunction(
            type,
            new Type[]{type, type}
        ) {
            @SuppressWarnings("unchecked")
            @Override
            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                params[0].evaluateTo(context, functionReturn);
                T a = (T) functionReturn.objectReturn;

                params[1].evaluateTo(context, functionReturn);
                T b = (T) functionReturn.objectReturn;

                functionReturn.objectReturn = function.apply(a, b) != inverted;
            }
        });
    }

    static <T extends TypedFunction> void add(String name, T function) {
        builder.add(name, function);
    }

    static void addCast(final String name, final Type from, final Type to, final Consumer<FunctionReturn> function) {
        add(name, new TypedFunction() {
            @Override
            public Type getReturnType() {
                return to;
            }

            @Override
            public Parameter[] getParameters() {
                return new Parameter[]{new Parameter(from)};
            }

            @Override
            public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
                params[0].evaluateTo(context, functionReturn);
                function.accept(functionReturn);
            }
        });
    }

    static void addImplicitCast(final Type from, final Type to, final Consumer<FunctionReturn> function) {
        addCast("<cast>", from, to, function);
        addExplicitCast(from, to, function);
    }

    static void addExplicitCast(final Type from, final Type to, final Consumer<FunctionReturn> function) {
        addCast("to" + to.getClass().getSimpleName(), from, to, function);
    }

    interface ObjectObject2BooleanFunction<T, U> {
        boolean apply(T t, U u);
    }

    interface ScalarOp<T> {
        void accept(T vector, float scalar, T dest);
    }

    interface TriConsumer<T, U, V> {
        void accept(T t, U u, V v);
    }

    interface QuadConsumer<T, U, V, W> {
        void accept(T t, U u, V v, W w);
    }
}

