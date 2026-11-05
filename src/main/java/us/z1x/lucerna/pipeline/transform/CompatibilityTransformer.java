package us.z1x.lucerna.pipeline.transform;

import io.github.douira.glsl_transformer.ast.node.Identifier;
import io.github.douira.glsl_transformer.ast.node.Profile;
import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.node.Version;
import io.github.douira.glsl_transformer.ast.node.VersionStatement;
import io.github.douira.glsl_transformer.ast.node.basic.ASTNode;
import io.github.douira.glsl_transformer.ast.node.declaration.DeclarationMember;
import io.github.douira.glsl_transformer.ast.node.declaration.FunctionParameter;
import io.github.douira.glsl_transformer.ast.node.declaration.TypeAndInitDeclaration;
import io.github.douira.glsl_transformer.ast.node.expression.Expression;
import io.github.douira.glsl_transformer.ast.node.expression.LiteralExpression;
import io.github.douira.glsl_transformer.ast.node.expression.ReferenceExpression;
import io.github.douira.glsl_transformer.ast.node.expression.unary.FunctionCallExpression;
import io.github.douira.glsl_transformer.ast.node.external_declaration.DeclarationExternalDeclaration;
import io.github.douira.glsl_transformer.ast.node.external_declaration.EmptyDeclaration;
import io.github.douira.glsl_transformer.ast.node.external_declaration.ExternalDeclaration;
import io.github.douira.glsl_transformer.ast.node.external_declaration.FunctionDefinition;
import io.github.douira.glsl_transformer.ast.node.statement.Statement;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier.StorageType;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.TypeQualifier;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.TypeQualifierPart;
import io.github.douira.glsl_transformer.ast.node.type.specifier.BuiltinNumericTypeSpecifier;
import io.github.douira.glsl_transformer.ast.node.type.specifier.FunctionPrototype;
import io.github.douira.glsl_transformer.ast.node.type.specifier.TypeSpecifier;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.query.match.AutoHintedMatcher;
import io.github.douira.glsl_transformer.ast.query.match.Matcher;
import io.github.douira.glsl_transformer.ast.transform.ASTInjectionPoint;
import io.github.douira.glsl_transformer.ast.transform.ASTParser;
import io.github.douira.glsl_transformer.ast.transform.Template;
import io.github.douira.glsl_transformer.util.Type;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.gl.shader.ShaderType;
import us.z1x.lucerna.pipeline.PatchedShaderPrinter;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CompatibilityTransformer {
    private static final Logger LOGGER = LogManager.getLogger(CompatibilityTransformer.class);

    private static final AutoHintedMatcher<Expression> sildursWaterFract = new AutoHintedMatcher<>(
        "fract(worldpos.y + 0.001)", Matcher.expressionPattern);

    private static final String[][] CORE_INPUTS = { { "vaPosition", "gl_Vertex.xyz" }, { "vaColor", "gl_Color" }, { "vaUV0", "gl_MultiTexCoord0.xy" },
        { "vaUV1", "ivec2(0, 10)" }, { "vaUV2", "ivec2(gl_MultiTexCoord1.xy)" }, { "vaNormal", "gl_Normal" } };
    private static final String[][] CORE_MATRICES = { { "modelViewMatrix", "gl_ModelViewMatrix" }, { "modelViewMatrixInverse", "gl_ModelViewMatrixInverse" },
        { "projectionMatrix", "gl_ProjectionMatrix" }, { "projectionMatrixInverse", "gl_ProjectionMatrixInverse" }, { "normalMatrix", "gl_NormalMatrix" },
        { "textureMatrix", "gl_TextureMatrix[0]" }, { "chunkOffset", "vec3(0.0)" } };

    private static final String[][] IMPLICIT_UNIFORMS = { { "int", "entityId" }, { "int", "blockEntityId" }, { "int", "currentRenderedItemId" },
        { "ivec2", "eyeBrightnessSmooth" }, { "float", "alphaTestRef" }, { "int", "isEyeInWater" } };

    private static StorageQualifier getConstQualifier(TypeQualifier qualifier) {
        if (qualifier == null) {
            return null;
        }
        for (TypeQualifierPart constQualifier : qualifier.getChildren()) {
            if (constQualifier instanceof StorageQualifier) {
                StorageQualifier storageQualifier = (StorageQualifier) constQualifier;
                if (storageQualifier.storageType == StorageQualifier.StorageType.CONST) {
                    return storageQualifier;
                }
            }
        }
        return null;
    }

    // core profile packs read the vanilla core attributes and matrices, which 1.8.9 only has as fixed function builtins
    public static void transformCore(ASTParser t, TranslationUnit tree, Root root, Parameters parameters) {
        VersionStatement versionStatement = tree.getVersionStatement();

        if (!versionStatement.getNormalizedProfile().isCore()) {
            return;
        }

        versionStatement.profile = Profile.COMPATIBILITY;

        if (parameters.type == PatchShaderType.VERTEX) {
            replaceCore(t, tree, root, CORE_INPUTS);
        }

        replaceCore(t, tree, root, CORE_MATRICES);
    }

    // the declarations become globals that main fills, so function parameters with the same names still work
    private static void replaceCore(ASTParser t, TranslationUnit tree, Root root, String[][] replacements) {
        for (String[] replacement : replacements) {
            TypeAndInitDeclaration declaration = root.identifierIndex.getStream(replacement[0])
                .filter(id -> id.getParent() instanceof DeclarationMember && id.getAncestor(FunctionDefinition.class) == null)
                .map(id -> id.getAncestor(TypeAndInitDeclaration.class))
                .findFirst().orElse(null);

            if (declaration == null) {
                continue;
            }

            TypeQualifier qualifier = declaration.getType().getTypeQualifier();

            if (qualifier != null) {
                qualifier.getChildren().stream().filter(StorageQualifier.class::isInstance).collect(Collectors.toList()).forEach(ASTNode::detachAndDelete);

                if (qualifier.getChildren().isEmpty()) {
                    qualifier.detachAndDelete();
                }
            }

            tree.prependMain(t, replacement[0] + " = " + replacement[1] + ";");
        }
    }

    public static void transformEach(ASTParser t, TranslationUnit tree, Root root, Parameters parameters) {
        if (parameters.type == PatchShaderType.VERTEX) {
            if (root.replaceExpressionMatches(t, sildursWaterFract, "fract(worldpos.y + 0.01)")) {
                Lucerna.logger.warn("Patched fract(worldpos.y + 0.001) to fract(worldpos.y + 0.01) to fix waving water disconnecting from other water blocks; See https://github.com/IrisShaders/Iris/issues/509");
            }
        }

        VersionStatement versionStatement = tree.getVersionStatement();

        // 140 drops the fixed function builtins that packs still use, profiles only exist from 150 on
        if (versionStatement.version.number == 140) {
            versionStatement.version = Version.GLSL15;
            versionStatement.profile = Profile.COMPATIBILITY;
        }

        // varying and attribute are gone from 150 core and cannot take flat, in and out work from 130 on
        if (versionStatement.version.number >= 130 && parameters.type != PatchShaderType.GEOMETRY) {
            for (StorageQualifier qualifier : root.nodeIndex.get(StorageQualifier.class)) {
                if (qualifier.storageType == StorageType.VARYING) {
                    qualifier.storageType = parameters.type == PatchShaderType.VERTEX ? StorageType.OUT : StorageType.IN;
                } else if (qualifier.storageType == StorageType.ATTRIBUTE) {
                    qualifier.storageType = StorageType.IN;
                }
            }
        }

        // texture() is a builtin from 130 on, so a sampler with that name shadows it, iris renames it the same way
        if (versionStatement.version.number >= 130 && root.identifierIndex.getStream("texture").anyMatch(id -> id.getParent() instanceof DeclarationMember)) {
            root.identifierIndex.getStream("texture")
                .filter(id -> id.getParent() instanceof DeclarationMember || id.getParent() instanceof ReferenceExpression)
                .collect(Collectors.toList())
                .forEach(id -> id.setName("gtexture"));
        }

        // chocapic based packs use flat, integer operators and texelFetch2D in 120 shaders without declaring the extension
        if (versionStatement.version.number <= 120 || root.identifierIndex.has("texelFetch2D")) {
            tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "#extension GL_EXT_gpu_shader4 : enable\n");
        }

        // iris provides these as uniforms even when a pack does not declare them or declares them as vertex attributes
        for (String[] uniform : IMPLICIT_UNIFORMS) {
            String id = uniform[1];

            if (root.identifierIndex.has(id) && root.identifierIndex.getStream(id).noneMatch(identifier -> identifier.getParent() instanceof DeclarationMember)) {
                tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "uniform " + uniform[0] + " " + id + ";");
            } else if (parameters.type == PatchShaderType.VERTEX && uniform[0].equals("int")) {
                for (Identifier identifier : root.identifierIndex.get(id)) {
                    TypeAndInitDeclaration declaration = identifier.getAncestor(TypeAndInitDeclaration.class);

                    if (declaration == null || declaration.getType().getTypeQualifier() == null || identifier.getAncestor(FunctionDefinition.class) != null) {
                        continue;
                    }

                    for (TypeQualifierPart part : declaration.getType().getTypeQualifier().getChildren()) {
                        if (part instanceof StorageQualifier storage && (storage.storageType == StorageType.ATTRIBUTE || storage.storageType == StorageType.IN)) {
                            storage.storageType = StorageType.UNIFORM;
                        }
                    }
                }
            }
        }

        // writing both is a link error on strict drivers, optifine treats gl_FragColor as gl_FragData[0]
        if (parameters.type == PatchShaderType.FRAGMENT && root.identifierIndex.has("gl_FragColor") && root.identifierIndex.has("gl_FragData")) {
            root.replaceReferenceExpressions(t, "gl_FragColor", "gl_FragData[0]");
        }

        // drop const from declarations initialized with const parameters, those need constant, not just immutable, expressions
        // see https://wiki.shaderlabs.org/wiki/Compiler_Behavior_Notes
        Map<FunctionDefinition, Set<String>> constFunctions = new HashMap<>();
        Set<String> processingSet = new HashSet<>();
        List<FunctionDefinition> unusedFunctions = new LinkedList<>();
        for (FunctionDefinition definition : root.nodeIndex.get(FunctionDefinition.class)) {
            FunctionPrototype prototype = definition.getFunctionPrototype();
            String functionName = prototype.getName().getName();
            if (!functionName.equals("main") && root.identifierIndex.getStream(functionName).count() <= 1) {
                // some drivers skip checks on unused functions, removing them also avoids bugs in unused code
                unusedFunctions.add(definition);
                if (PatchedShaderPrinter.PRETTY_PRINT_SHADERS) {
                    LOGGER.info("Removing unused function {}", functionName);
                }
                continue;
            }

            if (prototype.getChildren().isEmpty()) {
                continue;
            }

            Set<String> names = new HashSet<>(prototype.getChildren().size());
            for (FunctionParameter parameter : prototype.getChildren()) {
                if (getConstQualifier(parameter.getType().getTypeQualifier()) != null) {
                    String name = parameter.getName().getName();
                    names.add(name);
                    processingSet.add(name);
                }
            }
            if (!names.isEmpty()) {
                constFunctions.put(definition, names);
            }
        }

        for (FunctionDefinition definition : unusedFunctions) {
            definition.detachAndDelete();
        }

        boolean constDeclarationHit = false;
        Deque<String> processingQueue = new ArrayDeque<>(processingSet);
        while (!processingQueue.isEmpty()) {
            String name = processingQueue.poll();
            processingSet.remove(name);
            for (Identifier id : root.identifierIndex.get(name)) {
                // searching reference expressions cannot match a declaration member name
                ReferenceExpression reference = id.getAncestor(ReferenceExpression.class);
                if (reference == null) {
                    continue;
                }
                TypeAndInitDeclaration taid = reference.getAncestor(TypeAndInitDeclaration.class);
                if (taid == null) {
                    continue;
                }
                FunctionDefinition inDefinition = taid.getAncestor(FunctionDefinition.class);
                if (inDefinition == null) {
                    continue;
                }
                Set<String> constIdsInFunction = constFunctions.get(inDefinition);
                if (constIdsInFunction == null) {
                    continue;
                }
                if (constIdsInFunction.contains(name)) {
                    TypeQualifier qualifier = taid.getType().getTypeQualifier();
                    StorageQualifier constQualifier = getConstQualifier(qualifier);
                    if (constQualifier == null) {
                        continue;
                    }
                    constQualifier.detachAndDelete();
                    if (qualifier.getChildren().isEmpty()) {
                        qualifier.detachAndDelete();
                    }
                    constDeclarationHit = true;

                    for (DeclarationMember member : taid.getMembers()) {
                        String memberName = member.getName().getName();

                        // the name may not be the same as the parameter name
                        if (constIdsInFunction.contains(memberName)) {
                            throw new IllegalStateException("Illegal redefinition of const parameter " + name);
                        }

                        constIdsInFunction.add(memberName);

                        // don't add to the queue twice if it's already been added by a different scope
                        if (!processingSet.contains(memberName)) {
                            processingQueue.add(memberName);
                            processingSet.add(memberName);
                        }
                    }
                }
            }
        }

        if (constDeclarationHit) {
            LOGGER.warn(
                    "Removed the const keyword from declarations that use const parameters. See debugging.md for more information.");
        }

        boolean emptyDeclarationHit = root.process(
                root.nodeIndex.getStream(EmptyDeclaration.class),
                ASTNode::detachAndDelete);
        if (emptyDeclarationHit) {
            LOGGER.warn(
                    "Removed empty external declarations (\";\").");
        }
    }

    private static class DeclarationMatcher extends AutoHintedMatcher<ExternalDeclaration> {
        private final StorageType storageType;

        public DeclarationMatcher(StorageType storageType) {
            super("out float name;", Matcher.externalDeclarationPattern);
            this.storageType = storageType;
            markClassWildcard("qualifier", pattern.getRoot().nodeIndex.getOne(TypeQualifier.class));
            markClassWildcard("type", pattern.getRoot().nodeIndex.getOne(BuiltinNumericTypeSpecifier.class));
            markClassWildcard("name*", pattern.getRoot().identifierIndex.getOne("name").getAncestor(DeclarationMember.class));
        }

        public boolean matchesSpecialized(ExternalDeclaration tree, ShaderType shaderType) {
            boolean result = super.matchesExtract(tree);
            if (!result) {
                return false;
            }
            TypeQualifier qualifier = getNodeMatch("qualifier", TypeQualifier.class);
            for (TypeQualifierPart part : qualifier.getParts()) {
                if (part instanceof StorageQualifier) {
                    StorageQualifier storageQualifier = (StorageQualifier) part;
                    StorageType qualifierStorageType = storageQualifier.storageType;
                    if (qualifierStorageType == storageType ||
                            qualifierStorageType == StorageType.VARYING &&
                                    (shaderType == ShaderType.VERTEX && storageType == StorageType.OUT ||
                                            shaderType == ShaderType.GEOMETRY && storageType == StorageType.OUT ||
                                            shaderType == ShaderType.FRAGMENT && storageType == StorageType.IN)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private static final ShaderType[] pipeline = { ShaderType.VERTEX, ShaderType.GEOMETRY, ShaderType.FRAGMENT };
    private static final DeclarationMatcher outDeclarationMatcher = new DeclarationMatcher(StorageType.OUT);
    private static final DeclarationMatcher inDeclarationMatcher = new DeclarationMatcher(StorageType.IN);

    private static final String TAG_PREFIX = "iris_template_";
    private static final Template<ExternalDeclaration> declarationTemplate = Template
            .withExternalDeclaration("out __type __name;");
    private static final Template<Statement> initTemplate = Template.withStatement("__decl = __value;");
    private static final Template<ExternalDeclaration> variableTemplate = Template
            .withExternalDeclaration("__type __internalDecl;");
    private static final Template<Statement> statementTemplate = Template
            .withStatement("__oldDecl = vec3(__internalDecl);");
    private static final Template<Statement> statementTemplateVector = Template
            .withStatement("__oldDecl = vec3(__internalDecl, vec4(0));");

    static {
        declarationTemplate.markLocalReplacement(declarationTemplate.getSourceRoot().nodeIndex.getOne(TypeQualifier.class));
        declarationTemplate.markLocalReplacement("__type", TypeSpecifier.class);
        declarationTemplate.markIdentifierReplacement("__name");
        initTemplate.markIdentifierReplacement("__decl");
        initTemplate.markLocalReplacement("__value", ReferenceExpression.class);
        variableTemplate.markLocalReplacement("__type", TypeSpecifier.class);
        variableTemplate.markIdentifierReplacement("__internalDecl");
        statementTemplate.markIdentifierReplacement("__oldDecl");
        statementTemplate.markIdentifierReplacement("__internalDecl");
        statementTemplate.markLocalReplacement(
                statementTemplate.getSourceRoot().nodeIndex.getStream(BuiltinNumericTypeSpecifier.class)
                        .filter(specifier -> specifier.type == Type.F32VEC3).findAny().get());
        statementTemplateVector.markIdentifierReplacement("__oldDecl");
        statementTemplateVector.markIdentifierReplacement("__internalDecl");
        statementTemplateVector.markLocalReplacement(
                statementTemplateVector.getSourceRoot().nodeIndex.getStream(BuiltinNumericTypeSpecifier.class)
                        .filter(specifier -> specifier.type == Type.F32VEC3).findAny().get());
    }

    private static Statement getInitializer(Root root, String name, Type type) {
        return initTemplate.getInstanceFor(root,
                new Identifier(name),
                type.isScalar()
                        ? LiteralExpression.getDefaultValue(type)
                        : Root.indexNodes(root, () -> new FunctionCallExpression(
                                new Identifier(type.getMostCompactName()),
                                Stream.of(LiteralExpression.getDefaultValue(type)))));
    }

    private static TypeQualifier makeQualifierOut(TypeQualifier typeQualifier) {
        for (TypeQualifierPart qualifierPart : typeQualifier.getParts()) {
            if (qualifierPart instanceof StorageQualifier) {
                StorageQualifier storageQualifier = (StorageQualifier) qualifierPart;
                if (((StorageQualifier) qualifierPart).storageType == StorageType.IN) {
                    storageQualifier.storageType = StorageType.OUT;
                }
            }
        }
        return typeQualifier;
    }

    // does transformations that require cross-shader type data
    public static void transformGrouped(
            ASTParser t,
            Map<PatchShaderType, TranslationUnit> trees,
            Parameters parameters) {
        // adds missing out declarations for in declarations in geometry or fragment that the previous stage lacks
        ShaderType prevType = null;
        for (int i = 0; i < pipeline.length; i++) {
            ShaderType type = pipeline[i];
            PatchShaderType[] patchTypes = PatchShaderType.fromGlShaderType(type);

            boolean hasAny = false;
            for (PatchShaderType currentType : patchTypes) {
                if (trees.get(currentType) != null) {
                    hasAny = true;
                }
            }
            if (!hasAny) {
                continue;
            }

            if (prevType == null) {
                prevType = type;
                continue;
            }

            PatchShaderType prevPatchTypes = PatchShaderType.fromGlShaderType(prevType)[0];
            TranslationUnit prevTree = trees.get(prevPatchTypes);
            Root prevRoot = prevTree.getRoot();

            if (prevRoot.identifierIndex.prefixQueryFlat(TAG_PREFIX).findAny().isPresent()) {
                LOGGER.warn("The prefix tag {} is used in the shader, bailing compatibility transformation.", TAG_PREFIX);
                return;
            }

            Map<String, BuiltinNumericTypeSpecifier> outDeclarations = new HashMap<>();
            for (DeclarationExternalDeclaration declaration : prevRoot.nodeIndex.get(DeclarationExternalDeclaration.class)) {
                if (outDeclarationMatcher.matchesSpecialized(declaration, prevType)) {
                    BuiltinNumericTypeSpecifier extractedType = outDeclarationMatcher.getNodeMatch("type",
                            BuiltinNumericTypeSpecifier.class);
                    for (DeclarationMember member : outDeclarationMatcher
                            .getNodeMatch("name*", DeclarationMember.class)
                            .getAncestor(TypeAndInitDeclaration.class)
                            .getMembers()) {
                                String name = member.getName().getName();
                                if (!name.startsWith("gl_")) {
                                    outDeclarations.put(name, extractedType);
                                }
                    }
                }
            }

            for (PatchShaderType currentType : patchTypes) {
                TranslationUnit currentTree = trees.get(currentType);
                if (currentTree == null) {
                    continue;
                }
                Root currentRoot = currentTree.getRoot();

                for (ExternalDeclaration declaration : currentRoot.nodeIndex.get(DeclarationExternalDeclaration.class)) {
                    if (!inDeclarationMatcher.matchesSpecialized(declaration, currentType.glShaderType)) {
                        continue;
                    }

                    BuiltinNumericTypeSpecifier inTypeSpecifier = inDeclarationMatcher.getNodeMatch("type",
                            BuiltinNumericTypeSpecifier.class);
                    for (DeclarationMember inDeclarationMember : inDeclarationMatcher
                            .getNodeMatch("name*", DeclarationMember.class)
                            .getAncestor(TypeAndInitDeclaration.class)
                            .getMembers()) {
                        String name = inDeclarationMember.getName().getName();
                        if (name.startsWith("gl_")) {
                            continue;
                        }

                        // add missing out declarations with an initializer
                        if (!outDeclarations.containsKey(name)) {
                            if (!currentRoot.identifierIndex.getAncestors(name, ReferenceExpression.class).findAny().isPresent()) {
                                continue;
                            }

                            if (inTypeSpecifier == null) {
                                LOGGER.warn("The in declaration '{}' in the {} shader that has a missing corresponding out declaration in the previous stage {} has a non-numeric type and could not be compatibility-patched. See debugging.md for more information.", name, currentType.glShaderType.name(), prevType.name());
                                continue;
                            }
                            Type inType = inTypeSpecifier.type;

                            TypeQualifier outQualifier = (TypeQualifier) inDeclarationMatcher
                                    .getNodeMatch("qualifier").cloneInto(prevRoot);
                            makeQualifierOut(outQualifier);
                            prevTree.injectNode(ASTInjectionPoint.BEFORE_DECLARATIONS, declarationTemplate.getInstanceFor(prevRoot,
                                    outQualifier,
                                    inTypeSpecifier.cloneInto(prevRoot),
                                    new Identifier(name)));

                            prevTree.prependMain(getInitializer(prevRoot, name, inType));

                            // mark as handled to prevent duplicates
                            outDeclarations.put(name, null);

                            LOGGER.warn("The in declaration '{}' in the {} shader is missing a corresponding out declaration in the previous stage {} and has been compatibility-patched. See debugging.md for more information.", name, currentType.glShaderType.name(), prevType.name());
                        }

                        // mismatched types, patch with a global variable and a cast
                        else {
                            BuiltinNumericTypeSpecifier outTypeSpecifier = outDeclarations.get(name);

                            // null marks a newly inserted out declaration
                            if (outTypeSpecifier == null) {
                                continue;
                            }

                            Type inType = inTypeSpecifier.type;
                            Type outType = outTypeSpecifier.type;

                            if (inType == outType) {
                                // matching types that are never assigned get an initializer
                                if (prevRoot.identifierIndex.get(name).size() > 1) {
                                    continue;
                                }

                                prevTree.prependMain(getInitializer(prevRoot, name, inType));
                                outDeclarations.put(name, null);
                                continue;
                            }

                            if (outType.getDimension() != inType.getDimension()) {
                                LOGGER.warn("The in declaration '{}' in the {} shader has a mismatching dimensionality (scalar/vector/matrix) with the out declaration in the previous stage {} and could not be compatibility-patched. See debugging.md for more information.", name, currentType.glShaderType.name(), prevType.name());
                                continue;
                            }

                            boolean isVector = outType.isVector();

                            String newName = TAG_PREFIX + name;
                            prevRoot.identifierIndex.rename(name, newName);

                            // then restore the original name on the out declaration
                            TypeAndInitDeclaration outDeclaration = outTypeSpecifier.getAncestor(TypeAndInitDeclaration.class);
                            if (outDeclaration == null) {
                                continue;
                            }

                            List<DeclarationMember> outMembers = outDeclaration.getMembers();
                            DeclarationMember outMember = null;
                            for (DeclarationMember member : outMembers) {
                                if (member.getName().getName().equals(newName)) {
                                    outMember = member;
                                }
                            }
                            if (outMember == null) {
                                throw new IllegalStateException("The targeted out declaration member is missing!");
                            }
                            outMember.getName().replaceByAndDelete(new Identifier(name));

                            // split multi-member declarations so the other members keep their type
                            if (outMembers.size() > 1) {
                                outMember.detach();
                                outTypeSpecifier = outTypeSpecifier.cloneInto(prevRoot);
                                DeclarationExternalDeclaration singleOutDeclaration = (DeclarationExternalDeclaration) declarationTemplate
                                        .getInstanceFor(prevRoot,
                                                makeQualifierOut(outDeclaration.getType().getTypeQualifier().cloneInto(prevRoot)),
                                                outTypeSpecifier,
                                                new Identifier(name));
                                ((TypeAndInitDeclaration) singleOutDeclaration.getDeclaration()).getMembers().set(0, outMember);
                                prevTree.injectNode(ASTInjectionPoint.BEFORE_DECLARATIONS, singleOutDeclaration);
                            }

                            prevTree.injectNode(ASTInjectionPoint.BEFORE_DECLARATIONS, variableTemplate.getInstanceFor(prevRoot,
                                    outTypeSpecifier.cloneInto(prevRoot),
                                    new Identifier(newName)));

                            // at the end of main, assign the global to the out declaration with a cast
                            prevTree.appendMain(
                                    (isVector && outType.getDimensions()[0] < inType.getDimensions()[0] ? statementTemplateVector
                                            : statementTemplate).getInstanceFor(prevRoot,
                                                    new Identifier(name),
                                                    new Identifier(newName),
                                                    inTypeSpecifier.cloneInto(prevRoot)));

                            // the out declaration takes the type of the next stage
                            outTypeSpecifier.replaceByAndDelete(inTypeSpecifier.cloneInto(prevRoot));

                            outDeclarations.put(name, null);

                            LOGGER.warn("The out declaration '{}' in the {} shader has a different type {} than the corresponding in declaration of type {} in the following stage {} and has been compatibility-patched. See debugging.md for more information.", name, prevType.name(), outType.getMostCompactName(), inType.getMostCompactName(), currentType.glShaderType.name());
                        }
                    }
                }
            }

            prevType = type;
        }
    }
}
