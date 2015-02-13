/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.dsl.processor.generator;

import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.*;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.*;
import static javax.lang.model.element.Modifier.*;

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.internal.*;
import com.oracle.truffle.api.dsl.internal.DSLOptions.ImplicitCastOptimization;
import com.oracle.truffle.api.dsl.internal.DSLOptions.TypeBoxingOptimization;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.expression.*;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Variable;
import com.oracle.truffle.dsl.processor.java.*;
import com.oracle.truffle.dsl.processor.java.model.*;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.ArrayCodeTypeMirror;
import com.oracle.truffle.dsl.processor.model.*;
import com.oracle.truffle.dsl.processor.parser.*;
import com.oracle.truffle.dsl.processor.parser.SpecializationGroup.TypeGuard;

public class NodeGenFactory {

    private static final String FRAME_VALUE = TemplateMethod.FRAME_NAME;
    private static final String NAME_SUFFIX = "_";
    private static final String NODE_SUFFIX = "NodeGen";

    private final ProcessorContext context;
    private final NodeData node;
    private final TypeSystemData typeSystem;
    private final TypeData genericType;
    private final DSLOptions options;
    private final boolean singleSpecializable;
    private final int varArgsThreshold;

    public NodeGenFactory(ProcessorContext context, NodeData node) {
        this.context = context;
        this.node = node;
        this.typeSystem = node.getTypeSystem();
        this.genericType = typeSystem.getGenericTypeData();
        this.options = typeSystem.getOptions();
        this.singleSpecializable = isSingleSpecializableImpl();
        this.varArgsThreshold = calculateVarArgsThreshold();
    }

    private int calculateVarArgsThreshold() {
        TypeMirror specialization = context.getType(SpecializationNode.class);
        TypeElement specializationType = fromTypeMirror(specialization);

        int maxParameters = 0;
        for (ExecutableElement element : ElementFilter.methodsIn(specializationType.getEnclosedElements())) {
            if (element.getSimpleName().contentEquals("acceptAndExecute")) {
                maxParameters = Math.max(maxParameters, element.getParameters().size());
            }
        }
        return maxParameters;
    }

    public static String nodeTypeName(NodeData node) {
        return resolveNodeId(node) + NODE_SUFFIX;
    }

    private static String assumptionName(AssumptionExpression assumption) {
        return assumption.getId() + NAME_SUFFIX;
    }

    private static String resolveNodeId(NodeData node) {
        String nodeid = node.getNodeId();
        if (nodeid.endsWith("Node") && !nodeid.equals("Node")) {
            nodeid = nodeid.substring(0, nodeid.length() - 4);
        }
        return nodeid;
    }

    public static TypeMirror nodeType(NodeData node) {
        return new GeneratedTypeMirror(ElementUtils.getPackageName(node.getTemplateType()), nodeTypeName(node));
    }

    private static String specializationTypeName(SpecializationData specialization) {
        String id;
        if (specialization == null) {
            id = "Base";
        } else {
            id = specialization.getId();
        }
        return id + "Node_";
    }

    private TypeMirror specializationType(SpecializationData specialization) {
        return new GeneratedTypeMirror(ElementUtils.getPackageName(node.getTemplateType()) + "." + nodeTypeName(node), specializationTypeName(specialization));
    }

    private static String polymorphicTypeProfileFieldName(NodeExecutionData execution) {
        return execution.getName() + "Type" + NAME_SUFFIX;
    }

    private static String nodeFieldName(NodeExecutionData execution) {
        return execution.getName() + NAME_SUFFIX;
    }

    private static String specializationStartFieldName() {
        return "specialization" + NAME_SUFFIX;
    }

    private static String excludedFieldName(SpecializationData specialization) {
        return "exclude" + specialization.getId() + NAME_SUFFIX;
    }

    private static String executeChildMethodName(NodeExecutionData execution, TypeData type) {
        return "execute" + ElementUtils.firstLetterUpperCase(execution.getName()) + (type.isGeneric() ? "" : getTypeId(type.getBoxedType())) + NAME_SUFFIX;
    }

    private CodeTree accessParent(String name) {
        if (singleSpecializable) {
            if (name == null) {
                return CodeTreeBuilder.singleString("this");
            } else {
                return CodeTreeBuilder.singleString(name);
            }
        } else {
            if (name == null) {
                return CodeTreeBuilder.singleString("root");
            } else {
                return CodeTreeBuilder.createBuilder().string("root.").string(name).build();
            }
        }
    }

    public CodeTypeElement create() {
        CodeTypeElement clazz = GeneratorUtils.createClass(node, null, modifiers(FINAL), nodeTypeName(node), node.getTemplateType().asType());
        ElementUtils.setVisibility(clazz.getModifiers(), ElementUtils.getVisibility(node.getTemplateType().getModifiers()));

        for (NodeChildData child : node.getChildren()) {
            clazz.addOptional(createAccessChildMethod(child));
        }

        for (NodeFieldData field : node.getFields()) {
            if (!field.isGenerated()) {
                continue;
            }

            clazz.add(new CodeVariableElement(modifiers(PRIVATE, FINAL), field.getType(), field.getName()));
            if (field.getGetter() != null && field.getGetter().getModifiers().contains(Modifier.ABSTRACT)) {
                CodeExecutableElement method = CodeExecutableElement.clone(context.getEnvironment(), field.getGetter());
                method.getModifiers().remove(Modifier.ABSTRACT);
                method.createBuilder().startReturn().string("this.").string(field.getName()).end();
                clazz.add(method);
            }
        }

        for (ExecutableElement superConstructor : GeneratorUtils.findUserConstructors(node.getTemplateType().asType())) {
            clazz.add(createNodeConstructor(clazz, superConstructor));
        }

        for (NodeExecutionData execution : node.getChildExecutions()) {
            clazz.add(createNodeField(PRIVATE, execution.getNodeType(), nodeFieldName(execution), Child.class));
        }

        for (NodeExecutionData execution : node.getChildExecutions()) {
            if (!resolvePolymorphicExecutables(execution).isEmpty()) {
                clazz.add(createNodeField(PRIVATE, getType(Class.class), polymorphicTypeProfileFieldName(execution), CompilationFinal.class));
            }
        }

        for (SpecializationData specialization : node.getSpecializations()) {
            if (mayBeExcluded(specialization)) {
                clazz.add(createNodeField(PRIVATE, getType(boolean.class), excludedFieldName(specialization), CompilationFinal.class));
            }
        }

        Collection<TypeData> specializedTypes = node.findSpecializedReturnTypes();
        List<ExecutableTypeData> implementedExecutables = new ArrayList<>();
        for (ExecutableTypeData execType : node.getExecutableTypes()) {
            if (shouldImplementExecutableType(specializedTypes, execType)) {
                implementedExecutables.add(execType);
            }
        }
        for (ExecutableTypeData execType : implementedExecutables) {
            clazz.add(createExecutableTypeOverride(implementedExecutables, execType));
        }
        clazz.add(createGetCostMethod());

        avoidFindbugsProblems(clazz);

        if (singleSpecializable) {
            if (node.needsRewrites(context)) {
                clazz.add(createUnsupportedMethod());
            }
        } else {
            clazz.getImplements().add(getType(SpecializedNode.class));
            clazz.add(createMethodGetSpecializationNode());
            clazz.add(createDeepCopyMethod());
            SpecializationData specializationStart = createSpecializations(clazz);
            clazz.add(createNodeField(PRIVATE, specializationType(null), specializationStartFieldName(), Child.class));

            for (ExecutableElement constructor : ElementFilter.constructorsIn(clazz.getEnclosedElements())) {
                CodeTreeBuilder builder = ((CodeExecutableElement) constructor).appendBuilder();
                builder.startStatement();
                builder.string("this.").string(specializationStartFieldName());
                builder.string(" = ").tree(createCallCreateMethod(specializationStart, "this", null));
                builder.end();
            }
        }

        return clazz;
    }

    private void avoidFindbugsProblems(CodeTypeElement clazz) {
        TypeElement type = context.getEnvironment().getElementUtils().getTypeElement("edu.umd.cs.findbugs.annotations.SuppressFBWarnings");
        if (type == null) {
            return;
        }
        boolean foundComparison = false;
        outer: for (SpecializationData specialization : node.getSpecializations()) {
            for (GuardExpression guard : specialization.getGuards()) {
                if (guard.getExpression().containsComparisons()) {
                    foundComparison = true;
                    break outer;
                }
            }
        }

        if (foundComparison) {
            CodeAnnotationMirror annotation = new CodeAnnotationMirror((DeclaredType) type.asType());
            annotation.setElementValue(annotation.findExecutableElement("value"), new CodeAnnotationValue("SA_LOCAL_SELF_COMPARISON"));
            clazz.addAnnotationMirror(annotation);
        }
    }

    private Element createUnsupportedMethod() {
        LocalContext locals = LocalContext.load(this);
        CodeExecutableElement method = locals.createMethod(modifiers(PROTECTED), getType(UnsupportedSpecializationException.class), "unsupported");

        CodeTreeBuilder builder = method.createBuilder();
        builder.startReturn();
        builder.startNew(getType(UnsupportedSpecializationException.class));
        builder.string("this");
        builder.tree(createGetSuppliedChildren());
        locals.addReferencesTo(builder);
        builder.end();
        builder.end();
        return method;
    }

    private CodeExecutableElement createNodeConstructor(CodeTypeElement clazz, ExecutableElement superConstructor) {
        CodeExecutableElement constructor = GeneratorUtils.createConstructorUsingFields(modifiers(), clazz, superConstructor);
        ElementUtils.setVisibility(constructor.getModifiers(), ElementUtils.getVisibility(superConstructor.getModifiers()));

        List<CodeVariableElement> childParameters = new ArrayList<>();
        for (NodeChildData child : node.getChildren()) {
            childParameters.add(new CodeVariableElement(child.getOriginalType(), child.getName()));
        }
        constructor.getParameters().addAll(superConstructor.getParameters().size(), childParameters);

        CodeTreeBuilder builder = constructor.appendBuilder();
        List<String> childValues = new ArrayList<>(node.getChildren().size());
        for (NodeChildData child : node.getChildren()) {
            String name = child.getName();
            if (child.getCardinality().isMany()) {
                CreateCastData createCast = node.findCast(child.getName());
                if (createCast != null) {
                    CodeTree nameTree = CodeTreeBuilder.singleString(name);
                    CodeTreeBuilder callBuilder = builder.create();
                    callBuilder.string(name).string(" != null ? ");
                    callBuilder.tree(callTemplateMethod(null, createCast, nameTree));
                    callBuilder.string(" : null");
                    name += "_";
                    builder.declaration(child.getNodeType(), name, callBuilder.build());
                }
            }
            childValues.add(name);
        }

        for (NodeExecutionData execution : node.getChildExecutions()) {
            CreateCastData createCast = node.findCast(execution.getChild().getName());

            builder.startStatement();
            builder.string("this.").string(nodeFieldName(execution)).string(" = ");

            String name = childValues.get(node.getChildren().indexOf(execution.getChild()));
            CodeTreeBuilder accessorBuilder = builder.create();
            accessorBuilder.string(name);

            if (execution.isIndexed()) {
                accessorBuilder.string("[").string(String.valueOf(execution.getIndex())).string("]");
            }

            CodeTree accessor = accessorBuilder.build();

            if (createCast != null && execution.getChild().getCardinality().isOne()) {
                accessor = callTemplateMethod(null, createCast, accessor);
            }

            if (execution.isIndexed()) {
                CodeTreeBuilder nullCheck = builder.create();
                nullCheck.string(name).string(" != null && ").string(String.valueOf(execution.getIndex())).string(" < ").string(name).string(".length").string(" ? ");
                nullCheck.tree(accessor);
                nullCheck.string(" : null");
                accessor = nullCheck.build();
            }

            builder.tree(accessor);

            builder.end();
        }

        return constructor;
    }

    private static boolean mayBeExcluded(SpecializationData specialization) {
        return !specialization.getExceptions().isEmpty() || !specialization.getExcludedBy().isEmpty();
    }

    private SpecializationData createSpecializations(CodeTypeElement clazz) {
        List<SpecializationData> reachableSpecializations = getReachableSpecializations();

        CodeTypeElement baseSpecialization = clazz.add(createBaseSpecialization());
        TypeMirror baseSpecializationType = baseSpecialization.asType();

        Map<SpecializationData, CodeTypeElement> generated = new LinkedHashMap<>();

        List<SpecializationData> generateSpecializations = new ArrayList<>();
        generateSpecializations.add(node.getUninitializedSpecialization());
        if (needsPolymorphic()) {
            generateSpecializations.add(node.getPolymorphicSpecialization());
        }
        generateSpecializations.addAll(reachableSpecializations);

        for (SpecializationData specialization : generateSpecializations) {
            generated.put(specialization, clazz.add(createSpecialization(specialization, baseSpecializationType)));
        }

        baseSpecialization.addOptional(createCreateNext(generated));
        baseSpecialization.addOptional(createCreateFallback(generated));
        baseSpecialization.addOptional(createCreatePolymorphic(generated));

        return node.getUninitializedSpecialization();
    }

    private boolean needsPolymorphic() {
        List<SpecializationData> reachableSpecializations = getReachableSpecializations();
        if (reachableSpecializations.size() != 1) {
            return true;
        }

        SpecializationData specialization = reachableSpecializations.get(0);
        for (Parameter parameter : specialization.getSignatureParameters()) {
            TypeData type = parameter.getTypeSystemType();
            if (type != null && type.hasImplicitSourceTypes()) {
                return true;
            }
        }
        if (specialization.hasMultipleInstances()) {
            return true;
        }
        return false;

    }

    // create specialization

    private CodeTypeElement createBaseSpecialization() {
        CodeTypeElement clazz = createClass(node, null, modifiers(PRIVATE, ABSTRACT, STATIC), specializationTypeName(null), TypeSystemNodeFactory.nodeType(typeSystem));

        clazz.addOptional(createSpecializationConstructor(clazz, null, null));
        clazz.add(new CodeVariableElement(modifiers(PROTECTED, FINAL), nodeType(node), "root"));

        clazz.addOptional(createUnsupported());
        clazz.add(createGetSuppliedChildrenMethod());

        int signatureSize = node.getSignatureSize();
        Set<Integer> evaluatedCount = getEvaluatedCounts();
        for (int evaluated : evaluatedCount) {
            if (signatureSize != evaluated || signatureSize == 0) {
                clazz.add(createFastPathExecuteMethod(null, evaluated > 0 ? null : genericType, evaluated));
            }
        }

        for (NodeExecutionData execution : node.getChildExecutions()) {
            Collection<TypeData> specializedTypes = node.findSpecializedTypes(execution);
            specializedTypes.add(genericType);
            for (TypeData specializedType : specializedTypes) {
                if (isExecuteChildShared(execution, specializedType)) {
                    clazz.add(createExecuteChildMethod(execution, specializedType));
                }
            }
        }

        return clazz;
    }

    private CodeTypeElement createSpecialization(SpecializationData specialization, TypeMirror baseType) {
        CodeTypeElement clazz = createClass(node, specialization, modifiers(PRIVATE, STATIC, FINAL), specializationTypeName(specialization), baseType);

        CodeExecutableElement constructor = clazz.addOptional(createSpecializationConstructor(clazz, specialization, null));

        for (Parameter p : specialization.getSignatureParameters()) {
            TypeData targetType = p.getTypeSystemType();
            if (targetType.hasImplicitSourceTypes()) {
                NodeExecutionData execution = p.getSpecification().getExecution();
                CodeVariableElement implicitProfile = createImplicitProfileParameter(execution, p.getTypeSystemType());
                if (implicitProfile != null) {
                    implicitProfile.getModifiers().add(PRIVATE);
                    implicitProfile.getModifiers().add(FINAL);
                    clazz.add(implicitProfile);
                }
            }
        }

        if (specialization.isFallback()) {
            clazz.add(createFallbackGuardMethod());
        }

        clazz.addOptional(createSpecializationCreateMethod(specialization, constructor));
        clazz.addOptional(createMergeMethod(specialization));
        clazz.addOptional(createIsSameMethod(specialization));

        TypeData returnType = specialization.getReturnType().getTypeSystemType();
        int signatureSize = specialization.getSignatureSize();

        clazz.add(createFastPathExecuteMethod(specialization, null, signatureSize));

        if (isTypeBoxingEliminated(specialization)) {
            clazz.add(createFastPathExecuteMethod(specialization, returnType, 0));

            if (signatureSize > 0 && !returnType.isGeneric()) {
                clazz.add(createFastPathWrapExecuteMethod(genericType, returnType));
            }

            ExecutableTypeData voidExecutableType = node.findExecutableType(typeSystem.getVoidType(), 0);
            if (voidExecutableType != null && isTypeBoxingOptimized(options.voidBoxingOptimization(), returnType)) {
                clazz.add(createFastPathWrapVoidMethod(returnType));
            }
        }

        return clazz;
    }

    private Element createDeepCopyMethod() {
        if (singleSpecializable) {
            return null;
        }
        CodeExecutableElement executable = new CodeExecutableElement(modifiers(PUBLIC), getType(Node.class), "deepCopy");
        executable.getAnnotationMirrors().add(new CodeAnnotationMirror(context.getDeclaredType(Override.class)));
        CodeTreeBuilder builder = executable.createBuilder();
        builder.startReturn().startStaticCall(getType(SpecializationNode.class), "updateRoot").string("super.deepCopy()").end().end();
        return executable;
    }

    private Element createGetCostMethod() {
        TypeMirror returnType = getType(NodeCost.class);
        CodeExecutableElement executable = new CodeExecutableElement(modifiers(PUBLIC), returnType, "getCost");
        executable.getAnnotationMirrors().add(new CodeAnnotationMirror(context.getDeclaredType(Override.class)));
        CodeTreeBuilder builder = executable.createBuilder();
        if (singleSpecializable) {
            builder.startReturn().staticReference(getType(NodeCost.class), "MONOMORPHIC").end().end();
        } else {
            builder.startReturn().startCall(specializationStartFieldName(), "getNodeCost").end().end();
        }
        return executable;
    }

    private CodeExecutableElement createIsSameMethod(SpecializationData specialization) {
        if (!specialization.isSpecialized() || !options.implicitCastOptimization().isDuplicateTail()) {
            return null;
        }

        List<CodeVariableElement> profiles = new ArrayList<>();
        for (Parameter parameter : specialization.getSignatureParameters()) {
            NodeExecutionData execution = parameter.getSpecification().getExecution();
            if (execution == null) {
                continue;
            }
            CodeVariableElement var = createImplicitProfileParameter(execution, parameter.getTypeSystemType());
            if (var != null) {
                profiles.add(var);
            }
        }

        if (profiles.isEmpty()) {
            return null;
        }

        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), getType(boolean.class), "isSame");
        method.addParameter(new CodeVariableElement(getType(SpecializationNode.class), "other"));
        method.getAnnotationMirrors().add(new CodeAnnotationMirror(context.getDeclaredType(Override.class)));
        CodeTreeBuilder builder = method.createBuilder();

        builder.startReturn();
        builder.string("super.isSame(other)");

        for (CodeVariableElement profile : profiles) {
            builder.string(" && ");
            builder.string("this.").string(profile.getName()).string(" == ").string("(").cast(specializationType(specialization)).string("other).").string(profile.getName());
        }

        builder.end();
        return method;
    }

    private Element createMergeMethod(SpecializationData specialization) {
        boolean cacheBoundGuard = specialization.hasMultipleInstances();
        if (specialization.getExcludedBy().isEmpty() && !specialization.isPolymorphic() && !cacheBoundGuard) {
            return null;
        }
        TypeMirror specializationNodeType = getType(SpecializationNode.class);
        CodeExecutableElement executable = new CodeExecutableElement(modifiers(PUBLIC), specializationNodeType, "merge");
        executable.addParameter(new CodeVariableElement(specializationNodeType, "newNode"));
        executable.getAnnotationMirrors().add(new CodeAnnotationMirror(context.getDeclaredType(Override.class)));
        CodeTreeBuilder builder = executable.createBuilder();

        if (specialization.isPolymorphic()) {
            builder.statement("return polymorphicMerge(newNode)");
        } else {
            boolean elseIf = false;
            for (SpecializationData containedSpecialization : specialization.getExcludedBy()) {
                elseIf = builder.startIf(elseIf);
                builder.string("newNode.getClass() == ").typeLiteral(specializationType(containedSpecialization));
                builder.end();
                builder.startBlock();
                builder.statement("removeSame(\"Contained by " + containedSpecialization.createReferenceName() + "\")");
                builder.end();
            }
            if (cacheBoundGuard) {
                builder.statement("return super.mergeNoSame(newNode)");
            } else {
                builder.statement("return super.merge(newNode)");
            }
        }

        return executable;
    }

    private Element createFastPathWrapVoidMethod(TypeData wrap) {
        CodeExecutableElement executable = new CodeExecutableElement(modifiers(PUBLIC), typeSystem.getVoidType().getPrimitiveType(), TypeSystemNodeFactory.executeName(typeSystem.getVoidType()));
        executable.addParameter(new CodeVariableElement(getType(Frame.class), FRAME_VALUE));
        executable.getAnnotationMirrors().add(new CodeAnnotationMirror(context.getDeclaredType(Override.class)));
        CodeTreeBuilder builder = executable.createBuilder();
        builder.startStatement();
        builder.startCall(TypeSystemNodeFactory.voidBoxingExecuteName(wrap));
        builder.string(FRAME_VALUE);
        builder.end();
        builder.end();

        return executable;
    }

    private Element createFastPathWrapExecuteMethod(TypeData override, TypeData wrap) {
        CodeExecutableElement executable = new CodeExecutableElement(modifiers(PUBLIC), override.getPrimitiveType(), TypeSystemNodeFactory.executeName(override));
        executable.addParameter(new CodeVariableElement(getType(Frame.class), FRAME_VALUE));
        executable.getAnnotationMirrors().add(new CodeAnnotationMirror(context.getDeclaredType(Override.class)));
        CodeTreeBuilder builder = executable.createBuilder();
        if (wrap != null) {
            builder.startTryBlock();
        }
        builder.startReturn();
        builder.startCall(TypeSystemNodeFactory.executeName(wrap));
        builder.string(FRAME_VALUE);
        builder.end();
        builder.end();
        if (wrap != null) {
            builder.end().startCatchBlock(getType(UnexpectedResultException.class), "ex");
            builder.statement("return ex.getResult()");
            builder.end();
        }

        return executable;
    }

    private Element createCreateFallback(Map<SpecializationData, CodeTypeElement> generatedSpecializationClasses) {
        SpecializationData fallback = node.getGenericSpecialization();
        if (fallback == null) {
            return null;
        }
        CodeTypeElement generatedType = generatedSpecializationClasses.get(fallback);
        if (generatedType == null) {
            return null;
        }

        TypeMirror returnType = getType(SpecializationNode.class);
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PROTECTED, FINAL), returnType, "createFallback");
        method.getAnnotationMirrors().add(new CodeAnnotationMirror(context.getDeclaredType(Override.class)));
        method.createBuilder().startReturn().tree(createCallCreateMethod(fallback, null, null)).end();
        return method;
    }

    private Element createCreatePolymorphic(Map<SpecializationData, CodeTypeElement> generatedSpecializationClasses) {
        SpecializationData polymorphic = node.getPolymorphicSpecialization();
        CodeTypeElement generatedPolymorphic = generatedSpecializationClasses.get(polymorphic);
        if (generatedPolymorphic == null) {
            return null;
        }
        TypeMirror returnType = getType(SpecializationNode.class);
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PROTECTED, FINAL), returnType, "createPolymorphic");
        method.getAnnotationMirrors().add(new CodeAnnotationMirror(context.getDeclaredType(Override.class)));
        method.createBuilder().startReturn().tree(createCallCreateMethod(polymorphic, null, null)).end();
        return method;
    }

    private CodeExecutableElement createCreateNext(final Map<SpecializationData, CodeTypeElement> specializationClasses) {
        final LocalContext locals = LocalContext.load(this);

        CodeExecutableElement method = locals.createMethod(modifiers(PROTECTED, FINAL), getType(SpecializationNode.class), "createNext", FRAME_VALUE);
        method.getAnnotationMirrors().add(new CodeAnnotationMirror(context.getDeclaredType(Override.class)));

        CodeTreeBuilder builder = method.createBuilder();
        SpecializationGroup group = createSpecializationGroups();
        CodeTree execution = createGuardAndCast(group, genericType, locals, new SpecializationExecution() {
            public CodeTree createExecute(SpecializationData specialization, LocalContext values) {
                CodeTypeElement generatedType = specializationClasses.get(specialization);
                if (generatedType == null) {
                    throw new AssertionError("No generated type for " + specialization);
                }
                return createSlowPathExecute(specialization, values);
            }

            public boolean isFastPath() {
                return false;
            }
        });

        builder.tree(execution);

        if (hasFallthrough(group, genericType, locals, false, null)) {
            builder.returnNull();
        }
        return method;
    }

    private CodeExecutableElement createFallbackGuardMethod() {
        boolean frameUsed = node.isFrameUsedByAnyGuard();
        LocalContext locals = LocalContext.load(this);

        if (!frameUsed) {
            locals.removeValue(FRAME_VALUE);
        }

        CodeExecutableElement boundaryMethod = locals.createMethod(modifiers(PRIVATE), getType(boolean.class), "guardFallback", FRAME_VALUE);
        if (!frameUsed) {
            boundaryMethod.getAnnotationMirrors().add(new CodeAnnotationMirror(context.getDeclaredType(TruffleBoundary.class)));
        }

        CodeTreeBuilder builder = boundaryMethod.createBuilder();
        builder.startReturn();
        builder.startCall("createNext");
        locals.addReferencesTo(builder, FRAME_VALUE);
        builder.end();
        builder.string(" == null");
        builder.end();
        return boundaryMethod;
    }

    private ExecutableElement createAccessChildMethod(NodeChildData child) {
        if (child.getAccessElement() != null && child.getAccessElement().getModifiers().contains(Modifier.ABSTRACT)) {
            ExecutableElement getter = (ExecutableElement) child.getAccessElement();
            CodeExecutableElement method = CodeExecutableElement.clone(context.getEnvironment(), getter);
            method.getModifiers().remove(Modifier.ABSTRACT);

            List<NodeExecutionData> executions = new ArrayList<>();
            for (NodeExecutionData execution : node.getChildExecutions()) {
                if (execution.getChild() == child) {
                    executions.add(execution);
                }
            }

            CodeTreeBuilder builder = method.createBuilder();
            if (child.getCardinality().isMany()) {
                builder.startReturn().startNewArray((ArrayType) child.getOriginalType(), null);
                for (NodeExecutionData execution : executions) {
                    builder.string(nodeFieldName(execution));
                }
                builder.end().end();
            } else {
                for (NodeExecutionData execution : executions) {
                    builder.startReturn().string("this.").string(nodeFieldName(execution)).end();
                    break;
                }
            }
            return method;
        }
        return null;
    }

    private boolean isTypeBoxingEliminated(SpecializationData specialization) {
        if (specialization.getMethod() == null) {
            return false;
        }

        TypeBoxingOptimization optimization = options.monomorphicTypeBoxingOptimization();
        if (isTypeBoxingOptimized(optimization, specialization.getReturnType().getTypeSystemType())) {
            return true;
        }
        for (Parameter p : specialization.getSignatureParameters()) {
            if (isTypeBoxingOptimized(optimization, p.getTypeSystemType())) {
                return true;
            }
        }
        return false;

    }

    private Set<Integer> getEvaluatedCounts() {
        Set<Integer> evaluatedCount = new TreeSet<>();
        Collection<TypeData> returnSpecializedTypes = node.findSpecializedReturnTypes();
        for (ExecutableTypeData execType : node.getExecutableTypes()) {
            if (shouldImplementExecutableType(returnSpecializedTypes, execType)) {
                evaluatedCount.add(execType.getEvaluatedCount());
            }
        }
        return evaluatedCount;
    }

    private Element createUnsupported() {
        SpecializationData fallback = node.getGenericSpecialization();
        if (fallback == null || optimizeFallback(fallback) || fallback.getMethod() == null) {
            return null;
        }
        LocalContext locals = LocalContext.load(this);

        CodeExecutableElement method = locals.createMethod(modifiers(PROTECTED, FINAL), genericType.getPrimitiveType(), "unsupported", FRAME_VALUE);
        method.getAnnotationMirrors().add(new CodeAnnotationMirror(context.getDeclaredType(Override.class)));

        CodeTreeBuilder builder = method.createBuilder();
        builder.startReturn();
        builder.tree(callTemplateMethod(accessParent(null), fallback, locals));
        builder.end();

        return method;
    }

    private boolean isSingleSpecializableImpl() {
        List<SpecializationData> reachableSpecializations = getReachableSpecializations();
        if (reachableSpecializations.size() != 1) {
            return false;
        }

        SpecializationData specialization = reachableSpecializations.get(0);

        for (Parameter parameter : specialization.getSignatureParameters()) {
            TypeData type = parameter.getTypeSystemType();
            if (type != null && type.hasImplicitSourceTypes()) {
                return false;
            }
        }

        if (!specialization.getAssumptionExpressions().isEmpty()) {
            return false;
        }

        if (specialization.getCaches().size() > 0) {
            // TODO chumer: caches do not yet support single specialization.
            // it could be worthwhile to explore if this is possible
            return false;
        }
        return true;
    }

    private List<SpecializationData> getReachableSpecializations() {
        List<SpecializationData> specializations = new ArrayList<>();
        for (SpecializationData specialization : node.getSpecializations()) {
            if (specialization.isReachable() && //
                            (specialization.isSpecialized() //
                            || (specialization.isFallback() && optimizeFallback(specialization)))) {
                specializations.add(specialization);
            }
        }
        return specializations;
    }

    private boolean optimizeFallback(SpecializationData specialization) {
        switch (options.optimizeFallback()) {
            case NEVER:
                return false;
            case DECLARED:
                return specialization.getMethod() != null;
            case ALWAYS:
                return true;
            default:
                throw new AssertionError();
        }
    }

    private CodeExecutableElement createExecutableTypeOverride(List<ExecutableTypeData> implementedExecutables, ExecutableTypeData execType) {
        final String varArgsName = "args";
        final TypeData returnType = execType.getType();
        final TypeData executedType = execType.getEvaluatedCount() > 0 ? null : returnType;

        CodeExecutableElement method = cloneExecutableTypeOverride(execType, varArgsName);
        LocalContext locals = LocalContext.load(this, execType.getSignatureSize(), Integer.MAX_VALUE);

        // rename varargs parameter
        int signatureIndex = 0;
        for (Parameter parameter : execType.getSignatureParameters()) {
            LocalVariable var = locals.get(parameter, signatureIndex);
            if (var != null) {
                if (parameter.isTypeVarArgs()) {
                    var = var.accessWith(CodeTreeBuilder.singleString(varArgsName + "[" + parameter.getTypeVarArgsIndex() + "]"));
                }
                if (!parameter.getTypeSystemType().isGeneric()) {
                    var = var.newType(parameter.getTypeSystemType());
                }
                locals.setValue(node.getChildExecutions().get(signatureIndex), var);
            }

            signatureIndex++;
        }

        Parameter frame = execType.getFrame();
        CodeTreeBuilder builder = method.createBuilder();
        if (singleSpecializable) {
            LocalVariable frameVar = null;
            if (frame != null) {
                frameVar = locals.get(FRAME_VALUE).newType(frame.getType());
            }
            method.getThrownTypes().clear();
            locals.set(FRAME_VALUE, frameVar);

            SpecializationData specialization = getReachableSpecializations().iterator().next();
            ExecutableTypeData wrappedExecutableType = findWrappedExecutable(specialization, implementedExecutables, execType);
            if (wrappedExecutableType != null) {
                builder.startReturn().tree(callTemplateMethod(null, wrappedExecutableType, locals)).end();
            } else {
                builder.tree(createFastPath(builder, specialization, execType.getType(), locals));
            }
        } else {
            // create acceptAndExecute
            CodeTreeBuilder executeBuilder = builder.create();
            executeBuilder.startCall(specializationStartFieldName(), TypeSystemNodeFactory.executeName(executedType));
            if (frame == null) {
                executeBuilder.nullLiteral();
            } else {
                executeBuilder.string(frame.getLocalName());
            }
            locals.addReferencesTo(executeBuilder);
            executeBuilder.end();

            CodeTreeBuilder contentBuilder = builder.create();
            contentBuilder.startReturn();
            contentBuilder.tree(TypeSystemCodeGenerator.expect(executedType, returnType, executeBuilder.build()));
            contentBuilder.end();
            // try catch assert if unexpected value is not expected
            CodeTree content = contentBuilder.build();
            if (!execType.hasUnexpectedValue(context) && !returnType.isGeneric() && !returnType.isVoid()) {
                content = wrapTryCatchUnexpected(content);
            }
            builder.tree(content);
        }

        return method;
    }

    private CodeTree wrapTryCatchUnexpected(CodeTree content) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.startTryBlock();
        builder.tree(content);
        builder.end().startCatchBlock(getType(UnexpectedResultException.class), "ex");
        builder.startThrow().startNew(getType(AssertionError.class)).end().end();
        builder.end();
        return builder.build();
    }

    private static ExecutableTypeData findWrappedExecutable(SpecializationData specialization, List<ExecutableTypeData> implementedExecutables, ExecutableTypeData executedType) {
        if (specialization.getReturnType().getTypeSystemType() == executedType.getType()) {
            return null;
        }
        for (ExecutableTypeData otherType : implementedExecutables) {
            if (otherType != executedType && //
                            otherType.getType() == specialization.getReturnType().getTypeSystemType() && //
                            otherType.getEvaluatedCount() == executedType.getEvaluatedCount()) {
                return otherType;
            }
        }
        return null;
    }

    private CodeExecutableElement cloneExecutableTypeOverride(ExecutableTypeData execType, final String varArgsName) throws AssertionError {
        CodeExecutableElement method = CodeExecutableElement.clone(context.getEnvironment(), execType.getMethod());

        method.getAnnotationMirrors().clear();
        method.getModifiers().remove(Modifier.ABSTRACT);

        if (!execType.getMethod().isVarArgs() && execType.getParameters().size() != method.getParameters().size()) {
            throw new AssertionError("Should be verified in the parser");
        }

        // align argument names
        int index = 0;
        for (Parameter parameter : execType.getParameters()) {
            CodeVariableElement var = (CodeVariableElement) method.getParameters().get(index);
            if (parameter.isTypeVarArgs()) {
                var.getAnnotationMirrors().clear();
                var.setName(varArgsName);
                break;
            }
            var.setName(LocalVariable.fromParameter(parameter).createParameter().getName());
            var.getAnnotationMirrors().clear();
            index++;
        }
        return method;
    }

    private boolean shouldImplementExecutableType(Collection<TypeData> specializedTypes, ExecutableTypeData execType) {
        TypeData type = execType.getType();
        Set<Modifier> modifiers = execType.getMethod().getModifiers();
        if (modifiers.contains(FINAL) || modifiers.contains(STATIC) || modifiers.contains(PRIVATE)) {
            return false;
        } else if (execType.isAbstract()) {
            return true;
        } else if (type.isGeneric()) {
            return true;
        } else if (type.isVoid()) {
            for (TypeData specializedType : specializedTypes) {
                if (isTypeBoxingOptimized(options.voidBoxingOptimization(), specializedType)) {
                    return true;
                }
            }
            return false;
        } else if (!specializedTypes.contains(type)) {
            return false;
        } else if (!isTypeBoxingOptimized(options.monomorphicTypeBoxingOptimization(), type)) {
            return false;
        }
        return true;
    }

    private Element createMethodGetSpecializationNode() {
        TypeMirror returntype = getType(SpecializationNode.class);
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), returntype, "getSpecializationNode");
        method.createBuilder().startReturn().string(specializationStartFieldName()).end();
        return method;
    }

    private TypeMirror getType(Class<?> clazz) {
        return context.getType(clazz);
    }

    private CodeVariableElement createNodeField(Modifier visibility, TypeMirror type, String name, Class<?> annotationType) {
        CodeVariableElement childField = new CodeVariableElement(modifiers(), type, name);
        childField.getAnnotationMirrors().add(new CodeAnnotationMirror(context.getDeclaredType(annotationType)));
        setVisibility(childField.getModifiers(), visibility);
        return childField;
    }

    private static List<ExecutableTypeData> resolveSpecializedExecutables(NodeExecutionData execution, Collection<TypeData> types, TypeBoxingOptimization optimization) {
        if (optimization == TypeBoxingOptimization.NONE) {
            return Collections.emptyList();
        } else if (types.isEmpty()) {
            return Collections.emptyList();
        }

        List<ExecutableTypeData> executables = new ArrayList<>();
        for (TypeData type : types) {
            if (!isTypeBoxingOptimized(optimization, type)) {
                continue;
            }
            ExecutableTypeData foundType = execution.getChild().getNodeData().findExecutableType(type, execution.getChild().getExecuteWith().size());
            if (foundType != null) {
                executables.add(foundType);
            }
        }
        return executables;
    }

    private static CodeTree callTemplateMethod(CodeTree receiver, TemplateMethod method, CodeTree... boundValues) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        if (method.getMethod().getModifiers().contains(STATIC)) {
            builder.startStaticCall(method.getMethod().getEnclosingElement().asType(), method.getMethodName());
        } else {
            builder.startCall(receiver, method.getMethodName());
        }
        int index = -1;
        for (Parameter parameter : method.getParameters()) {
            index++;
            if (index < boundValues.length) {
                CodeTree tree = boundValues[index];
                if (tree != null) {
                    builder.tree(tree);
                    continue;
                }
            }

            builder.defaultValue(parameter.getType());
        }
        builder.end();
        return builder.build();
    }

    private static CodeTree callTemplateMethod(CodeTree receiver, TemplateMethod method, LocalContext currentValues) {
        CodeTree[] bindings = new CodeTree[method.getParameters().size()];

        int signatureIndex = 0;
        for (int i = 0; i < bindings.length; i++) {
            Parameter parameter = method.getParameters().get(i);

            LocalVariable var = currentValues.get(parameter, signatureIndex);
            if (var == null) {
                var = currentValues.get(parameter.getLocalName());
            }

            if (var != null) {
                CodeTree valueReference = var.createReference();
                if (parameter.getTypeSystemType() != null && var.getType() != null && var.getType().needsCastTo(parameter.getTypeSystemType())) {
                    valueReference = TypeSystemCodeGenerator.cast(parameter.getTypeSystemType(), valueReference);
                } else if (ElementUtils.needsCastTo(var.getTypeMirror(), parameter.getType())) {
                    valueReference = CodeTreeBuilder.createBuilder().cast(parameter.getType(), valueReference).build();
                }
                bindings[i] = valueReference;
            }

            if (parameter.getSpecification().isSignature()) {
                signatureIndex++;
            }
        }
        return callTemplateMethod(receiver, method, bindings);
    }

    private SpecializationGroup createSpecializationGroups() {
        return SpecializationGroup.create(getReachableSpecializations());
    }

    private CodeTree createSlowPathExecute(SpecializationData specialization, LocalContext currentValues) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        if (specialization.isFallback()) {
            return builder.returnNull().build();
        }

        if (node.isFrameUsedByAnyGuard()) {
            builder.tree(createTransferToInterpreterAndInvalidate());
        }

        // caches unbound to guards are invoked after all guards
        for (CacheExpression cache : specialization.getCaches()) {
            if (!specialization.isCacheBoundByGuard(cache)) {
                initializeCache(builder, specialization, cache, currentValues);
            }
        }
        boolean hasAssumptions = !specialization.getAssumptionExpressions().isEmpty();
        if (hasAssumptions) {

            for (AssumptionExpression assumption : specialization.getAssumptionExpressions()) {
                CodeTree assumptions = DSLExpressionGenerator.write(assumption.getExpression(), accessParent(null),
                                castBoundTypes(bindExpressionValues(assumption.getExpression(), specialization, currentValues)));
                String name = assumptionName(assumption);
                // needs specialization index for assumption to make unique
                String varName = name + specialization.getIndex();
                TypeMirror type = assumption.getExpression().getResolvedType();
                builder.declaration(type, varName, assumptions);
                currentValues.set(name, new LocalVariable(null, type, varName, null));
            }

            builder.startIf();
            String sep = "";
            for (AssumptionExpression assumption : specialization.getAssumptionExpressions()) {
                LocalVariable assumptionVar = currentValues.get(assumptionName(assumption));
                if (assumptionVar == null) {
                    throw new AssertionError("assumption var not resolved");
                }
                builder.string(sep);
                builder.startCall("isValid").tree(assumptionVar.createReference()).end();
                sep = " && ";
            }
            builder.end();
            builder.startBlock();
        }

        for (SpecializationData otherSpeciailzation : node.getSpecializations()) {
            if (otherSpeciailzation == specialization) {
                continue;
            }
            if (otherSpeciailzation.getExcludedBy().contains(specialization)) {
                builder.startStatement();
                builder.tree(accessParent(excludedFieldName(otherSpeciailzation)));
                builder.string(" = true");
                builder.end();
            }
        }

        CodeTree create = createCallCreateMethod(specialization, null, currentValues);

        if (specialization.hasMultipleInstances()) {
            builder.declaration(getType(SpecializationNode.class), "s", create);
            DSLExpression limitExpression = specialization.getLimitExpression();
            CodeTree limitExpressionTree;
            if (limitExpression == null) {
                limitExpressionTree = CodeTreeBuilder.singleString("3");
            } else {
                limitExpressionTree = DSLExpressionGenerator.write(limitExpression, accessParent(null), //
                                castBoundTypes(bindExpressionValues(limitExpression, specialization, currentValues)));
            }

            builder.startIf().string("countSame(s) < ").tree(limitExpressionTree).end().startBlock();
            builder.statement("return s");
            builder.end();
        } else {
            builder.startReturn().tree(create).end();
        }

        if (hasAssumptions) {
            builder.end();
        }

        if (mayBeExcluded(specialization)) {
            CodeTreeBuilder checkHasSeenBuilder = builder.create();
            checkHasSeenBuilder.startIf().string("!").tree(accessParent(excludedFieldName(specialization))).end().startBlock();
            checkHasSeenBuilder.tree(builder.build());
            checkHasSeenBuilder.end();
            return checkHasSeenBuilder.build();
        }
        return builder.build();
    }

    private boolean hasFallthrough(SpecializationGroup group, TypeData forType, LocalContext currentValues, boolean fastPath, List<GuardExpression> ignoreGuards) {
        for (TypeGuard guard : group.getTypeGuards()) {
            if (currentValues.getValue(guard.getSignatureIndex()) == null) {
                // not evaluated
                return true;
            }
            LocalVariable value = currentValues.getValue(guard.getSignatureIndex());
            if (value.getType().needsCastTo(guard.getType())) {
                return true;
            }
        }

        List<GuardExpression> guards = new ArrayList<>(group.getGuards());
        List<GuardExpression> elseConnectable = group.findElseConnectableGuards();
        guards.removeAll(elseConnectable);
        if (ignoreGuards != null) {
            guards.removeAll(ignoreGuards);
        }
        SpecializationData specialization = group.getSpecialization();
        if (specialization != null && fastPath) {
            for (ListIterator<GuardExpression> iterator = guards.listIterator(); iterator.hasNext();) {
                GuardExpression guard = iterator.next();
                if (!specialization.isDynamicParameterBound(guard.getExpression())) {
                    iterator.remove();
                }
            }
        }

        if (!guards.isEmpty()) {
            return true;
        }

        if (!fastPath && specialization != null && !specialization.getAssumptionExpressions().isEmpty()) {
            return true;
        }

        if (!fastPath && specialization != null && mayBeExcluded(specialization)) {
            return true;
        }

        if (!elseConnectable.isEmpty()) {
            SpecializationGroup previous = group.getPrevious();
            if (previous != null && hasFallthrough(previous, forType, currentValues, fastPath, previous.getGuards())) {
                return true;
            }
        }

        List<SpecializationGroup> groupChildren = group.getChildren();
        if (!groupChildren.isEmpty()) {
            return hasFallthrough(groupChildren.get(groupChildren.size() - 1), forType, currentValues, fastPath, ignoreGuards);
        }

        return false;
    }

    private Element createGetSuppliedChildrenMethod() {
        ArrayType nodeArray = context.getEnvironment().getTypeUtils().getArrayType(getType(Node.class));

        CodeExecutableElement method = new CodeExecutableElement(modifiers(PROTECTED, FINAL), nodeArray, "getSuppliedChildren");
        method.getAnnotationMirrors().add(new CodeAnnotationMirror(context.getDeclaredType(Override.class)));

        CodeTreeBuilder builder = method.createBuilder();
        builder.startReturn().tree(createGetSuppliedChildren()).end();

        return method;
    }

    private CodeTree createGetSuppliedChildren() {
        ArrayType nodeArray = context.getEnvironment().getTypeUtils().getArrayType(getType(Node.class));
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.startNewArray(nodeArray, null);
        for (int i = 0; i < node.getChildExecutions().size(); i++) {
            NodeExecutionData execution = node.getChildExecutions().get(i);
            if (execution.isShortCircuit()) {
                builder.nullLiteral();
            }
            builder.tree(accessParent(nodeFieldName(execution)));
        }
        builder.end();
        return builder.build();
    }

    // create specialization

    private CodeTree createCallCreateMethod(SpecializationData specialization, String rootName, LocalContext currentValues) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();

        TypeMirror specializationType = specializationType(specialization);
        if (useLazyClassLoading()) {
            builder.startStaticCall(specializationType(specialization), "create");
        } else {
            builder.startNew(specializationType);
        }
        if (rootName != null) {
            builder.string(rootName);
        } else {
            builder.string("root");
        }
        if (currentValues != null) {
            for (Parameter p : specialization.getSignatureParameters()) {
                CodeVariableElement var = createImplicitProfileParameter(p.getSpecification().getExecution(), p.getTypeSystemType());
                if (var != null) {
                    // we need the original name here
                    builder.tree(LocalVariable.fromParameter(p).createReference());
                }
            }
            for (CacheExpression cache : specialization.getCaches()) {
                LocalVariable variable = currentValues.get(cache.getParameter().getLocalName());
                if (variable == null) {
                    throw new AssertionError("Could not bind cached value " + cache.getParameter().getLocalName() + ": " + currentValues);
                }
                builder.tree(variable.createReference());
            }
            for (AssumptionExpression assumption : specialization.getAssumptionExpressions()) {
                LocalVariable variable = currentValues.get(assumptionName(assumption));
                if (variable == null) {
                    throw new AssertionError("Could not bind assumption value " + assumption.getId() + ": " + currentValues);
                }
                builder.tree(variable.createReference());
            }
        }
        builder.end();

        return builder.build();
    }

    private Element createSpecializationCreateMethod(SpecializationData specialization, CodeExecutableElement constructor) {
        if (!useLazyClassLoading()) {
            return null;
        }

        CodeExecutableElement executable = CodeExecutableElement.clone(context.getEnvironment(), constructor);
        executable.setReturnType(specializationType(null));
        executable.setSimpleName(CodeNames.of("create"));
        executable.getModifiers().add(STATIC);

        CodeTreeBuilder builder = executable.createBuilder();
        builder.startReturn().startNew(specializationType(specialization));
        for (VariableElement parameter : executable.getParameters()) {
            builder.string(parameter.getSimpleName().toString());
        }
        builder.end().end();
        return executable;
    }

    private boolean useLazyClassLoading() {
        return options.useLazyClassLoading() && !singleSpecializable;
    }

    private static String implicitClassFieldName(NodeExecutionData execution) {
        return execution.getName() + "ImplicitType";
    }

    private static String implicitNodeFieldName(NodeExecutionData execution) {
        return execution.getName() + "Cast";
    }

    private CodeExecutableElement createSpecializationConstructor(CodeTypeElement clazz, SpecializationData specialization, String constantIndex) {
        CodeExecutableElement constructor = new CodeExecutableElement(modifiers(), null, clazz.getSimpleName().toString());

        constructor.addParameter(new CodeVariableElement(nodeType(node), "root"));
        CodeTreeBuilder builder = constructor.createBuilder();

        if (specialization == null) {
            if (constantIndex == null) {
                builder.statement("super(index)");
                constructor.addParameter(new CodeVariableElement(getType(int.class), "index"));
            } else {
                builder.startStatement().startSuperCall().string(constantIndex).end().end();
            }
            builder.statement("this.root = root");
        } else {
            int index = resolveSpecializationIndex(specialization);
            builder.startStatement().startSuperCall().string("root").string(String.valueOf(index)).end().end();

            for (Parameter p : specialization.getSignatureParameters()) {
                NodeExecutionData execution = p.getSpecification().getExecution();

                CodeVariableElement implicitProfile = createImplicitProfileParameter(execution, p.getTypeSystemType());
                if (implicitProfile != null) {
                    LocalVariable var = LocalVariable.fromParameter(p).makeGeneric();

                    String implicitFieldName = implicitProfile.getName();
                    if (options.implicitCastOptimization().isDuplicateTail()) {
                        constructor.addParameter(var.createParameter());
                        CodeTree implicitType = TypeSystemCodeGenerator.implicitType(p.getTypeSystemType(), var.createReference());
                        builder.startStatement().string("this.").string(implicitFieldName).string(" = ").tree(implicitType).end();
                    } else if (options.implicitCastOptimization().isMergeCasts()) {
                        // use node that supports polymorphism
                        constructor.addParameter(var.createParameter());
                        builder.startStatement().string("this.").string(implicitFieldName).string(" = ").tree(ImplicitCastNodeFactory.create(p.getTypeSystemType(), var.createReference())).end();
                    } else {
                        throw new AssertionError();
                    }
                }
            }
            for (CacheExpression cache : specialization.getCaches()) {
                String name = cache.getParameter().getLocalName();
                TypeMirror type = cache.getParameter().getType();

                if (ElementUtils.isAssignable(type, new ArrayCodeTypeMirror(getType(Node.class)))) {
                    CodeVariableElement var = clazz.add(new CodeVariableElement(modifiers(PRIVATE, FINAL), type, name));
                    var.addAnnotationMirror(new CodeAnnotationMirror(context.getDeclaredType(Children.class)));
                } else if (ElementUtils.isAssignable(type, getType(Node.class))) {
                    CodeVariableElement var = clazz.add(new CodeVariableElement(modifiers(PRIVATE), type, name));
                    var.addAnnotationMirror(new CodeAnnotationMirror(context.getDeclaredType(Child.class)));
                } else {
                    clazz.add(new CodeVariableElement(modifiers(PRIVATE, FINAL), type, name));
                }
                constructor.addParameter(new CodeVariableElement(type, name));
                builder.startStatement().string("this.").string(name).string(" = ").string(name).end();
            }

            for (AssumptionExpression assumption : specialization.getAssumptionExpressions()) {
                String name = assumptionName(assumption);
                TypeMirror type = assumption.getExpression().getResolvedType();
                CodeVariableElement field = clazz.add(new CodeVariableElement(modifiers(PRIVATE, FINAL), type, name));
                field.addAnnotationMirror(new CodeAnnotationMirror(context.getDeclaredType(CompilationFinal.class)));
                constructor.addParameter(new CodeVariableElement(type, name));
                builder.startStatement().string("this.").string(name).string(" = ").string(name).end();
            }
        }

        if (constructor.getParameters().isEmpty()) {
            // do not generate default constructor
            return null;
        }
        return constructor;
    }

    // TODO this logic can be inlined to the parser as soon as the old NodeGen layout is gone
    private static int resolveSpecializationIndex(SpecializationData specialization) {
        if (specialization.isFallback()) {
            return Integer.MAX_VALUE - 1;
        } else if (specialization.isUninitialized()) {
            return Integer.MAX_VALUE;
        } else if (specialization.isPolymorphic()) {
            return 0;
        } else {
            return specialization.getIndex();
        }
    }

    private static CodeTree createThrowUnsupported(LocalContext currentValues) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.startThrow().startCall("unsupported");
        currentValues.addReferencesTo(builder);
        builder.end().end();
        return builder.build();
    }

    private CodeTree createCallNext(TypeData forType, LocalContext currentValues) {
        if (singleSpecializable) {
            return createThrowUnsupported(currentValues);
        }
        CodeTreeBuilder callBuilder = CodeTreeBuilder.createBuilder();
        callBuilder.startCall("next", TypeSystemNodeFactory.executeName(null));
        currentValues.addReferencesTo(callBuilder, FRAME_VALUE);
        callBuilder.end();
        return CodeTreeBuilder.createBuilder().startReturn().tree(TypeSystemCodeGenerator.expect(genericType, forType, callBuilder.build())).end().build();
    }

    private CodeTree createCallRemove(String reason, TypeData forType, LocalContext currentValues) {
        if (singleSpecializable) {
            return createThrowUnsupported(currentValues);
        }
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.startCall("remove");
        builder.doubleQuote(reason);
        currentValues.addReferencesTo(builder, FRAME_VALUE);
        builder.end();
        CodeTree call = builder.build();

        builder = builder.create();
        builder.startReturn();
        builder.tree(TypeSystemCodeGenerator.expect(genericType, forType, call));
        builder.end();
        return builder.build();
    }

    private static CodeTree createCallDelegate(String methodName, String reason, TypeData forType, LocalContext currentValues) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.startCall(methodName);
        if (reason != null) {
            builder.doubleQuote(reason);
        }
        currentValues.addReferencesTo(builder, FRAME_VALUE);
        builder.end();

        TypeData executedType = forType.getTypeSystem().getGenericTypeData();
        return TypeSystemCodeGenerator.expect(executedType, forType, builder.build());
    }

    private Set<ExecutableTypeData> findSpecializedExecutableTypes(NodeExecutionData execution, TypeData type) {
        ExecutableTypeData executableType = resolveExecutableType(execution.getChild(), type);
        Set<ExecutableTypeData> executedTypes = new HashSet<>();
        executedTypes.add(executableType);
        if (type.hasImplicitSourceTypes()) {
            executedTypes.addAll(resolveSpecializedExecutables(execution, type.getImplicitSourceTypes(), options.implicitTypeBoxingOptimization()));
        }
        return executedTypes;
    }

    private ExecutableTypeData resolveExecutableType(NodeChildData child, TypeData type) {
        int executeWithCount = child.getExecuteWith().size();
        ExecutableTypeData executableType = child.getNodeData().findExecutableType(type, executeWithCount);
        if (executableType == null) {
            executableType = child.getNodeData().findAnyGenericExecutableType(context, executeWithCount);
        }
        return executableType;
    }

    private boolean hasUnexpectedResult(NodeExecutionData execution, TypeData type) {
        for (ExecutableTypeData executableType : findSpecializedExecutableTypes(execution, type)) {
            if (executableType != null && (executableType.hasUnexpectedValue(context) || executableType.getType().needsCastTo(type))) {
                return true;
            }
        }
        return false;
    }

    private Element createFastPathExecuteMethod(SpecializationData specialization, final TypeData forType, int evaluatedArguments) {
        TypeData type = forType == null ? genericType : forType;
        LocalContext currentLocals = LocalContext.load(this, evaluatedArguments, varArgsThreshold);

        if (specialization != null) {
            currentLocals.loadFastPathState(specialization);
        }

        CodeExecutableElement executable = currentLocals.createMethod(modifiers(PUBLIC), type.getPrimitiveType(), TypeSystemNodeFactory.executeName(forType), FRAME_VALUE);
        executable.getAnnotationMirrors().add(new CodeAnnotationMirror(context.getDeclaredType(Override.class)));

        if (!type.isGeneric()) {
            executable.getThrownTypes().add(getType(UnexpectedResultException.class));
        }

        CodeTreeBuilder builder = executable.createBuilder();
        builder.tree(createFastPath(builder, specialization, type, currentLocals));

        return executable;
    }

    private CodeTree createFastPath(CodeTreeBuilder parent, SpecializationData specialization, TypeData type, LocalContext currentLocals) {
        final CodeTreeBuilder builder = parent.create();

        for (NodeExecutionData execution : node.getChildExecutions()) {
            LocalVariable var = currentLocals.getValue(execution);
            if (var == null) {
                TypeData targetType;
                if (specialization == null) {
                    targetType = genericType;
                } else {
                    targetType = specialization.findParameterOrDie(execution).getTypeSystemType();
                }
                LocalVariable shortCircuit = resolveShortCircuit(specialization, execution, currentLocals);
                LocalVariable value = currentLocals.createValue(execution, targetType).nextName();
                builder.tree(createAssignExecuteChild(execution, type, value, shortCircuit, currentLocals));
                currentLocals.setValue(execution, value);
            }
        }

        LocalContext originalValues = currentLocals.copy();
        if (specialization == null) {
            builder.startReturn().tree(createCallDelegate("acceptAndExecute", null, type, currentLocals)).end();
        } else if (specialization.isPolymorphic()) {
            builder.tree(createCallNext(type, currentLocals));
        } else if (specialization.isUninitialized()) {
            builder.startReturn().tree(createCallDelegate("uninitialized", null, type, currentLocals)).end();
        } else {
            final TypeData finalType = type;
            SpecializationGroup group = SpecializationGroup.create(specialization);
            SpecializationExecution executionFactory = new SpecializationExecution() {
                public CodeTree createExecute(SpecializationData s, LocalContext values) {
                    return createFastPathExecute(builder, finalType, s, values);
                }

                public boolean isFastPath() {
                    return true;
                }
            };
            builder.tree(createGuardAndCast(group, type, currentLocals, executionFactory));
            if (hasFallthrough(group, type, originalValues, true, null) || group.getSpecialization().isFallback()) {
                builder.tree(createCallNext(type, originalValues));
            }
        }
        return builder.build();
    }

    private LocalVariable resolveShortCircuit(SpecializationData specialization, NodeExecutionData execution, LocalContext currentLocals) {
        LocalVariable shortCircuit = null;
        SpecializationData resolvedSpecialization = specialization;
        if (specialization == null) {
            resolvedSpecialization = node.getGenericSpecialization();
        }

        if (execution.isShortCircuit()) {
            ShortCircuitData shortCircuitData = resolvedSpecialization.getShortCircuits().get(calculateShortCircuitIndex(execution));
            CodeTree access = callTemplateMethod(accessParent(null), shortCircuitData, currentLocals);
            shortCircuit = currentLocals.createShortCircuitValue(execution).accessWith(access);
        }
        return shortCircuit;
    }

    private int calculateShortCircuitIndex(NodeExecutionData execution) {
        int shortCircuitIndex = 0;
        for (NodeExecutionData otherExectuion : node.getChildExecutions()) {
            if (otherExectuion.isShortCircuit()) {
                if (otherExectuion == execution) {
                    break;
                }
                shortCircuitIndex++;
            }
        }
        return shortCircuitIndex;
    }

    private CodeTree createFastPathExecute(CodeTreeBuilder parent, final TypeData forType, SpecializationData specialization, LocalContext currentValues) {
        CodeTreeBuilder builder = parent.create();
        int ifCount = 0;
        if (specialization.isFallback()) {
            builder.startIf().startCall("guardFallback");
            if (node.isFrameUsedByAnyGuard()) {
                builder.string(FRAME_VALUE);
            }
            currentValues.addReferencesTo(builder);

            builder.end();
            builder.end();
            builder.startBlock();
            ifCount++;
        }
        CodeTreeBuilder execute = builder.create();

        if (!specialization.getAssumptionExpressions().isEmpty()) {
            builder.startTryBlock();
            for (AssumptionExpression assumption : specialization.getAssumptionExpressions()) {
                LocalVariable assumptionVar = currentValues.get(assumptionName(assumption));
                if (assumptionVar == null) {
                    throw new AssertionError("Could not resolve assumption var " + currentValues);
                }
                builder.startStatement().startCall("check").tree(assumptionVar.createReference()).end().end();
            }
            builder.end().startCatchBlock(getType(InvalidAssumptionException.class), "ae");
            builder.startReturn();
            List<String> assumptionIds = new ArrayList<>();
            for (AssumptionExpression assumption : specialization.getAssumptionExpressions()) {
                assumptionIds.add(assumption.getId());
            }
            builder.tree(createCallDelegate("removeThis", String.format("Assumption %s invalidated", assumptionIds), forType, currentValues));
            builder.end();
            builder.end();
        }

        execute.startReturn();
        if (specialization.getMethod() == null) {
            execute.startCall("unsupported");
            currentValues.addReferencesTo(execute, FRAME_VALUE);
            execute.end();
        } else {
            execute.tree(callTemplateMethod(accessParent(null), specialization, currentValues));
        }
        execute.end();
        builder.tree(createFastPathTryCatchRewriteException(specialization, forType, currentValues, execute.build()));
        builder.end(ifCount);
        return builder.build();
    }

    private CodeTree createGuardAndCast(SpecializationGroup group, TypeData forType, LocalContext currentValues, SpecializationExecution execution) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();

        Set<TypeGuard> castGuards;
        if (execution.isFastPath()) {
            castGuards = null; // cast all
        } else {
            castGuards = new HashSet<>();
            for (TypeGuard castGuard : group.getTypeGuards()) {
                if (isTypeGuardUsedInAnyGuardOrCacheBelow(group, currentValues, castGuard)) {
                    castGuards.add(castGuard);
                }
            }
        }

        SpecializationData specialization = group.getSpecialization();
        CodeTree[] checkAndCast = createTypeCheckAndLocals(specialization, group.getTypeGuards(), castGuards, currentValues, execution);

        CodeTree check = checkAndCast[0];
        CodeTree cast = checkAndCast[1];

        List<GuardExpression> elseGuardExpressions = group.findElseConnectableGuards();
        List<GuardExpression> guardExpressions = new ArrayList<>(group.getGuards());
        guardExpressions.removeAll(elseGuardExpressions);
        CodeTree[] methodGuardAndAssertions = createMethodGuardCheck(guardExpressions, specialization, currentValues, execution.isFastPath());
        CodeTree methodGuards = methodGuardAndAssertions[0];
        CodeTree guardAssertions = methodGuardAndAssertions[1];

        int ifCount = 0;
        if (!check.isEmpty()) {
            builder.startIf();
            builder.tree(check).end();
            builder.startBlock();
            ifCount++;
        }
        if (!cast.isEmpty()) {
            builder.tree(cast);
        }
        boolean elseIf = !elseGuardExpressions.isEmpty();
        if (!methodGuards.isEmpty()) {
            builder.startIf(elseIf);
            builder.tree(methodGuards).end();
            builder.startBlock();
            ifCount++;
        } else if (elseIf) {
            builder.startElseBlock();
            ifCount++;
        }
        if (!guardAssertions.isEmpty()) {
            builder.tree(guardAssertions);
        }

        boolean reachable = isReachableGroup(group, ifCount);
        if (reachable) {
            for (SpecializationGroup child : group.getChildren()) {
                builder.tree(createGuardAndCast(child, forType, currentValues.copy(), execution));
            }
            if (specialization != null) {
                builder.tree(execution.createExecute(specialization, currentValues));
            }
        }
        builder.end(ifCount);

        return builder.build();
    }

    private static boolean isReachableGroup(SpecializationGroup group, int ifCount) {
        if (ifCount != 0) {
            return true;
        }
        SpecializationGroup previous = group.getPreviousGroup();
        if (previous == null || previous.findElseConnectableGuards().isEmpty()) {
            return true;
        }

        /*
         * Hacky else case. In this case the specialization is not reachable due to previous else
         * branch. This is only true if the minimum state is not checked.
         */
        if (previous.getGuards().size() == 1 && previous.getTypeGuards().isEmpty() &&
                        (previous.getParent() == null || previous.getMaxSpecializationIndex() != previous.getParent().getMaxSpecializationIndex())) {
            return false;
        }

        return true;
    }

    private boolean isTypeGuardUsedInAnyGuardOrCacheBelow(SpecializationGroup group, LocalContext currentValues, TypeGuard typeGuard) {
        String localName = currentValues.getValue(typeGuard.getSignatureIndex()).getName();

        SpecializationData specialization = group.getSpecialization();
        for (GuardExpression guard : group.getGuards()) {
            if (isVariableBoundIn(specialization, guard.getExpression(), localName, currentValues)) {
                return true;
            }
        }
        if (specialization != null) {
            for (CacheExpression cache : specialization.getCaches()) {
                if (isVariableBoundIn(specialization, cache.getExpression(), localName, currentValues)) {
                    return true;
                }
            }
        }

        for (SpecializationGroup child : group.getChildren()) {
            if (isTypeGuardUsedInAnyGuardOrCacheBelow(child, currentValues, typeGuard)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isVariableBoundIn(SpecializationData specialization, DSLExpression expression, String localName, LocalContext currentValues) throws AssertionError {
        Map<Variable, LocalVariable> boundValues = bindExpressionValues(expression, specialization, currentValues);
        for (Variable var : expression.findBoundVariables()) {
            LocalVariable target = boundValues.get(var);
            if (target != null && localName.equals(target.getName())) {
                return true;
            }
        }
        return false;
    }

    private CodeExecutableElement createExecuteChildMethod(NodeExecutionData execution, TypeData targetType) {
        LocalContext locals = LocalContext.load(this, 0, varArgsThreshold);

        CodeExecutableElement method = locals.createMethod(modifiers(PROTECTED, FINAL), targetType.getPrimitiveType(), executeChildMethodName(execution, targetType), FRAME_VALUE);
        if (hasUnexpectedResult(execution, targetType)) {
            method.getThrownTypes().add(getType(UnexpectedResultException.class));
        }

        CodeVariableElement implicitProfile = createImplicitProfileParameter(execution, targetType);
        if (implicitProfile != null) {
            method.addParameter(implicitProfile);
        }

        for (int i = 0; i < execution.getChild().getExecuteWith().size(); i++) {
            NodeExecutionData executeWith = node.getChildExecutions().get(i);
            LocalVariable var = locals.createValue(executeWith, genericType);
            method.addParameter(var.createParameter());
            locals.setValue(executeWith, var);
        }

        CodeTreeBuilder builder = method.createBuilder();
        CodeTree executeChild = createExecuteChild(execution, locals.createValue(execution, targetType), locals, true);
        if (executeChild.isSingleLine()) {
            builder.statement(executeChild);
        } else {
            builder.tree(executeChild);
        }
        return method;
    }

    private CodeVariableElement createImplicitProfileParameter(NodeExecutionData execution, TypeData targetType) {
        if (targetType.hasImplicitSourceTypes()) {
            switch (options.implicitCastOptimization()) {
                case NONE:
                    return null;
                case DUPLICATE_TAIL:
                    return new CodeVariableElement(getType(Class.class), implicitClassFieldName(execution));
                case MERGE_CASTS:
                    return new CodeVariableElement(ImplicitCastNodeFactory.type(targetType), implicitNodeFieldName(execution));
            }
        }
        return null;
    }

    private boolean isExecuteChildShared(NodeExecutionData execution, TypeData targetType) {
        if (targetType.isVoid()) {
            return false;
        } else if (targetType.isGeneric()) {
            return resolvePolymorphicExecutables(execution).size() >= 1;
        } else {
            if (!isTypeBoxingOptimized(options.monomorphicTypeBoxingOptimization(), targetType)) {
                return false;
            }
            if (!targetType.hasImplicitSourceTypes()) {
                return false;
            }

            int uses = 0;
            for (SpecializationData specialization : node.getSpecializations()) {
                List<Parameter> parameters = specialization.findByExecutionData(execution);
                for (Parameter parameter : parameters) {
                    if (targetType.equals(parameter.getTypeSystemType())) {
                        uses++;
                    }
                }
            }
            if (uses > 1) {
                return resolveSpecializedExecutables(execution, targetType.getImplicitSourceTypes(), options.implicitTypeBoxingOptimization()).size() > 1;
            } else {
                return false;
            }
        }
    }

    private CodeTree createAssignExecuteChild(NodeExecutionData execution, TypeData returnType, LocalVariable targetValue, LocalVariable shortCircuit, LocalContext currentValues) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        boolean hasUnexpected = hasUnexpectedResult(execution, targetValue.getType());

        CodeTree executeChild;
        if (isExecuteChildShared(execution, targetValue.getType())) {
            executeChild = createCallSharedExecuteChild(execution, targetValue, currentValues);
        } else {
            executeChild = createExecuteChild(execution, targetValue, currentValues, false);
        }

        builder.tree(createTryExecuteChild(targetValue, executeChild, shortCircuit == null, hasUnexpected));

        if (shortCircuit != null) {
            currentValues.setShortCircuitValue(execution, shortCircuit.accessWith(null));
        }

        builder.end();
        if (hasUnexpected) {
            builder.startCatchBlock(getType(UnexpectedResultException.class), "ex");
            LocalContext slowPathValues = currentValues.copy();

            slowPathValues.setValue(execution, targetValue.makeGeneric().accessWith(CodeTreeBuilder.singleString("ex.getResult()")));
            boolean found = false;
            for (NodeExecutionData otherExecution : node.getChildExecutions()) {
                if (found) {
                    LocalVariable childEvaluatedValue = slowPathValues.createValue(otherExecution, genericType);
                    LocalVariable genericShortCircuit = resolveShortCircuit(null, otherExecution, slowPathValues);
                    builder.tree(createAssignExecuteChild(otherExecution, genericType, childEvaluatedValue, genericShortCircuit, slowPathValues));
                    slowPathValues.setValue(otherExecution, childEvaluatedValue);
                } else {
                    // skip forward already evaluated
                    found = execution == otherExecution;
                }
            }
            builder.tree(createCallNext(returnType, slowPathValues));
            builder.end();
        }

        return createShortCircuit(targetValue, shortCircuit, builder.build());
    }

    private static CodeTree createShortCircuit(LocalVariable targetValue, LocalVariable shortCircuitValue, CodeTree tryExecute) {
        if (shortCircuitValue == null) {
            return tryExecute;
        }

        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();

        builder.tree(shortCircuitValue.createDeclaration(shortCircuitValue.createReference()));
        builder.tree(targetValue.createDeclaration(builder.create().defaultValue(targetValue.getTypeMirror()).build()));

        builder.startIf().string(shortCircuitValue.getName()).end().startBlock();
        builder.tree(tryExecute);
        builder.end();

        return builder.build();
    }

    private static CodeTree createTryExecuteChild(LocalVariable value, CodeTree executeChild, boolean needDeclaration, boolean hasTry) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        boolean hasDeclaration = false;
        if ((hasTry || !executeChild.isSingleLine()) && needDeclaration) {
            builder.tree(value.createDeclaration(null));
            hasDeclaration = true;
        }

        if (hasTry) {
            builder.startTryBlock();
        } else {
            builder.startGroup();
        }

        if (executeChild.isSingleLine()) {
            builder.startStatement();
            if (hasDeclaration || !needDeclaration) {
                builder.tree(executeChild);
            } else {
                builder.type(value.getTypeMirror()).string(" ").tree(executeChild);
            }
            builder.end();
        } else {
            builder.tree(executeChild);
        }

        builder.end();

        return builder.build();
    }

    private CodeTree createCallSharedExecuteChild(NodeExecutionData execution, LocalVariable targetValue, LocalContext currentValues) {
        if (!isExecuteChildShared(execution, targetValue.getType())) {
            throw new AssertionError("Execute child not shared with method but called.");
        }

        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.tree(targetValue.createReference()).string(" = ");
        builder.startCall(executeChildMethodName(execution, targetValue.getType()));
        builder.string(FRAME_VALUE);

        CodeVariableElement implicitProfile = createImplicitProfileParameter(execution, targetValue.getType());
        if (implicitProfile != null) {
            builder.string(implicitProfile.getName());
        }
        for (int i = 0; i < execution.getChild().getExecuteWith().size(); i++) {
            builder.tree(currentValues.getValue(i).createReference());
        }
        builder.end();
        return builder.build();
    }

    private CodeTree createExecuteChild(NodeExecutionData execution, LocalVariable target, LocalContext currentValues, boolean shared) {
        final CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();

        CodeTree assignment = createAssignmentStart(target, shared);

        final Set<ExecutableTypeData> executableTypes = findSpecializedExecutableTypes(execution, target.getType());
        if (executableTypes.isEmpty()) {
            throw new AssertionError(); // cannot execute child
        } else if (executableTypes.size() == 1 && !target.getType().hasImplicitSourceTypes()) {
            ExecutableTypeData executableType = executableTypes.iterator().next();
            if (target.getType().isGeneric() && executableType.getEvaluatedCount() == 0) {
                return createPolymorphicExecuteChild(execution, target, currentValues, shared);
            } else {
                builder.tree(assignment);
                builder.tree(createSingleExecute(execution, target, currentValues, executableType));
            }
        } else {
            if (options.implicitCastOptimization().isNone()) {
                throw new AssertionError("findSpecializedExecutableTypes is always 1 if implicit cast opt is disabled");
            } else if (options.implicitCastOptimization().isDuplicateTail()) {
                builder.tree(createExecuteChildDuplicateTail(builder, execution, assignment, target, currentValues));
            } else if (options.implicitCastOptimization().isMergeCasts()) {
                // TODO
                throw new UnsupportedOperationException();
            } else {
                throw new AssertionError();
            }
        }
        return builder.build();
    }

    private CodeTree createSingleExecute(NodeExecutionData execution, LocalVariable target, LocalContext currentValues, ExecutableTypeData executableType) {
        CodeTree accessChild = accessParent(nodeFieldName(execution));
        CodeTree execute = callTemplateMethod(accessChild, executableType, currentValues);
        return TypeSystemCodeGenerator.expect(executableType.getType(), target.getType(), execute);
    }

    private CodeTree createPolymorphicExecuteChild(NodeExecutionData execution, LocalVariable target, LocalContext currentValues, boolean shared) throws AssertionError {
        ExecutableTypeData genericExecutableType = execution.getChild().getNodeData().findAnyGenericExecutableType(context, execution.getChild().getExecuteWith().size());
        if (genericExecutableType == null) {
            throw new AssertionError("At least one generic executable method must be available.");
        }

        List<ExecutableTypeData> specializedExecutables = resolvePolymorphicExecutables(execution);
        Collections.sort(specializedExecutables, new Comparator<ExecutableTypeData>() {
            public int compare(ExecutableTypeData o1, ExecutableTypeData o2) {
                return o1.getType().compareTo(o2.getType());
            }
        });

        CodeTree assignment = createAssignmentStart(target, shared);
        CodeTree executeGeneric = createSingleExecute(execution, target, currentValues, genericExecutableType);

        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        if (specializedExecutables.isEmpty()) {
            builder.tree(assignment);
            builder.tree(executeGeneric);
        } else {
            final CodeTreeBuilder polyChainBuilder = builder.create();
            final String profileField = polymorphicTypeProfileFieldName(execution);
            final String valueFieldName = "_value";

            builder.declaration(getType(Class.class), profileField, accessParent(profileField));

            boolean encounteredUnexpectedResult = false;
            boolean hasSpecializedTypes = false;
            for (ExecutableTypeData executableType : specializedExecutables) {
                hasSpecializedTypes = polyChainBuilder.startIf(hasSpecializedTypes);
                polyChainBuilder.string(profileField);
                polyChainBuilder.string(" == ").typeLiteral(executableType.getType().getPrimitiveType());
                polyChainBuilder.end();
                polyChainBuilder.startBlock();
                polyChainBuilder.startStatement();
                polyChainBuilder.tree(assignment);
                polyChainBuilder.tree(createSingleExecute(execution, target, currentValues, executableType)).end();
                polyChainBuilder.end();
                encounteredUnexpectedResult |= executableType.hasUnexpectedValue(context);
            }

            // else if null -> specialize
            polyChainBuilder.startElseIf().string(profileField).string(" == null").end();
            polyChainBuilder.startBlock();
            polyChainBuilder.tree(createTransferToInterpreterAndInvalidate());
            polyChainBuilder.declaration(genericExecutableType.getType().getPrimitiveType(), valueFieldName, executeGeneric);

            hasSpecializedTypes = false;
            for (ExecutableTypeData executableType : specializedExecutables) {
                hasSpecializedTypes = polyChainBuilder.startIf(hasSpecializedTypes);
                polyChainBuilder.tree(TypeSystemCodeGenerator.check(executableType.getType(), CodeTreeBuilder.singleString(valueFieldName)));
                polyChainBuilder.end();
                polyChainBuilder.startBlock();
                polyChainBuilder.startStatement().tree(accessParent(profileField)).string(" = ").typeLiteral(executableType.getType().getPrimitiveType()).end();
                polyChainBuilder.end();
            }

            polyChainBuilder.startElseBlock();
            polyChainBuilder.startStatement().tree(accessParent(profileField)).string(" = ").typeLiteral(genericType.getPrimitiveType()).end();
            polyChainBuilder.end();
            polyChainBuilder.startReturn().string(valueFieldName).end();
            polyChainBuilder.end();

            // else -> execute generic
            polyChainBuilder.startElseBlock();
            polyChainBuilder.startStatement().tree(assignment).tree(executeGeneric).end();
            polyChainBuilder.end();

            CodeTree executePolymorphic = polyChainBuilder.build();
            if (encounteredUnexpectedResult) {
                builder.startTryBlock();
                builder.tree(executePolymorphic);
                builder.end();
                builder.startCatchBlock(getType(UnexpectedResultException.class), "ex");
                builder.startStatement().tree(accessParent(profileField)).string(" = ").typeLiteral(genericType.getPrimitiveType()).end();
                builder.startReturn().string("ex.getResult()").end();
                builder.end();
            } else {
                builder.tree(executePolymorphic);
            }
        }
        return builder.build();
    }

    private List<ExecutableTypeData> resolvePolymorphicExecutables(NodeExecutionData execution) {
        if (singleSpecializable) {
            return Collections.emptyList();
        }
        Set<TypeData> specializedTypes = new HashSet<>();
        for (TypeData type : node.findSpecializedTypes(execution)) {
            specializedTypes.addAll(type.getImplicitSourceTypes());
        }
        return resolveSpecializedExecutables(execution, specializedTypes, options.polymorphicTypeBoxingElimination());
    }

    private static CodeTree createAssignmentStart(LocalVariable target, boolean shared) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        if (shared) {
            builder.string("return ");
        } else {
            builder.string(target.getName()).string(" = ");
        }
        return builder.build();
    }

    private CodeTree createExecuteChildDuplicateTail(CodeTreeBuilder parent, NodeExecutionData execution, CodeTree assignment, LocalVariable target, LocalContext currentValues) {
        CodeTreeBuilder builder = parent.create();
        List<TypeData> sourceTypes = target.getType().getImplicitSourceTypes();
        String implicitClassFieldName = implicitClassFieldName(execution);
        String nodeFieldName = nodeFieldName(execution);
        List<ExecutableTypeData> executableTypes = resolveSpecializedExecutables(execution, sourceTypes, options.implicitTypeBoxingOptimization());

        boolean elseIf = false;
        for (ExecutableTypeData executableType : executableTypes) {
            elseIf = builder.startIf(elseIf);
            builder.string(implicitClassFieldName).string(" == ").typeLiteral(executableType.getType().getBoxedType());
            builder.end();
            builder.startBlock();
            builder.startStatement().tree(assignment);

            CodeTree execute = callTemplateMethod(accessParent(nodeFieldName), executableType, currentValues);
            ImplicitCastData cast = typeSystem.lookupCast(executableType.getType(), target.getType());
            if (cast != null) {
                execute = callTemplateMethod(null, cast, execute);
            }
            builder.tree(execute);
            builder.end();
            builder.end();
        }

        if (!executableTypes.isEmpty()) {
            builder.startElseBlock();
        }

        LocalVariable genericValue = target.makeGeneric().nextName();
        builder.tree(createAssignExecuteChild(execution, genericValue.getType(), genericValue, null, currentValues));
        if (executableTypes.size() == sourceTypes.size()) {
            builder.startThrow().startNew(getType(UnexpectedResultException.class)).tree(genericValue.createReference()).end().end();
        } else {
            builder.startStatement().tree(assignment);
            builder.tree(TypeSystemCodeGenerator.implicitExpect(target.getType(), genericValue.createReference(), implicitClassFieldName));
            builder.end();
        }

        if (!executableTypes.isEmpty()) {
            builder.end();
        }
        return builder.build();
    }

    private CodeTree createFastPathTryCatchRewriteException(SpecializationData specialization, TypeData forType, LocalContext currentValues, CodeTree execution) {
        if (specialization.getExceptions().isEmpty()) {
            return execution;
        }
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.startTryBlock();
        builder.tree(execution);
        TypeMirror[] exceptionTypes = new TypeMirror[specialization.getExceptions().size()];
        for (int i = 0; i < exceptionTypes.length; i++) {
            exceptionTypes[i] = specialization.getExceptions().get(i).getJavaClass();
        }
        builder.end().startCatchBlock(exceptionTypes, "ex");
        builder.startStatement().tree(accessParent(excludedFieldName(specialization))).string(" = true").end();
        builder.tree(createCallRemove("threw rewrite exception", forType, currentValues));
        builder.end();
        return builder.build();
    }

    private CodeTree[] createMethodGuardCheck(List<GuardExpression> guardExpressions, SpecializationData specialization, LocalContext currentValues, boolean fastPath) {
        CodeTreeBuilder expressionBuilder = CodeTreeBuilder.createBuilder();
        CodeTreeBuilder assertionBuilder = CodeTreeBuilder.createBuilder();
        String and = "";
        for (GuardExpression guard : guardExpressions) {
            DSLExpression expression = guard.getExpression();

            Map<Variable, CodeTree> resolvedBindings = castBoundTypes(bindExpressionValues(expression, specialization, currentValues));
            CodeTree expressionCode = DSLExpressionGenerator.write(expression, accessParent(null), resolvedBindings);

            if (!specialization.isDynamicParameterBound(expression) && fastPath) {
                /*
                 * Guards where no dynamic parameters are bound can just be executed on the fast
                 * path.
                 */
                assertionBuilder.startAssert().tree(expressionCode).end();
            } else {
                expressionBuilder.string(and);
                expressionBuilder.tree(expressionCode);
                and = " && ";
            }
        }
        return new CodeTree[]{expressionBuilder.build(), assertionBuilder.build()};
    }

    private static Map<Variable, CodeTree> castBoundTypes(Map<Variable, LocalVariable> bindings) {
        Map<Variable, CodeTree> resolvedBindings = new HashMap<>();
        for (Variable variable : bindings.keySet()) {
            LocalVariable localVariable = bindings.get(variable);
            CodeTree resolved = localVariable.createReference();
            TypeMirror sourceType = localVariable.getTypeMirror();
            TypeMirror targetType = variable.getResolvedTargetType();
            if (targetType == null) {
                targetType = variable.getResolvedType();
            }
            if (!ElementUtils.isAssignable(sourceType, targetType)) {
                resolved = CodeTreeBuilder.createBuilder().cast(targetType, resolved).build();
            }
            resolvedBindings.put(variable, resolved);
        }
        return resolvedBindings;
    }

    private static Map<Variable, LocalVariable> bindExpressionValues(DSLExpression expression, SpecializationData specialization, LocalContext currentValues) throws AssertionError {
        Map<Variable, LocalVariable> bindings = new HashMap<>();

        Set<Variable> boundVariables = expression.findBoundVariables();
        if (specialization == null && !boundVariables.isEmpty()) {
            throw new AssertionError("Cannot bind guard variable in non-specialization group. yet.");
        }

        // resolve bindings for local context
        for (Variable variable : boundVariables) {
            Parameter resolvedParameter = specialization.findByVariable(variable.getResolvedVariable());
            if (resolvedParameter != null) {
                LocalVariable localVariable;
                if (resolvedParameter.getSpecification().isSignature()) {
                    NodeExecutionData execution = resolvedParameter.getSpecification().getExecution();
                    localVariable = currentValues.getValue(execution);
                } else {
                    localVariable = currentValues.get(resolvedParameter.getLocalName());
                }
                if (localVariable != null) {
                    bindings.put(variable, localVariable);
                }
            }
        }
        return bindings;
    }

    private CodeTree[] createTypeCheckAndLocals(SpecializationData specialization, List<TypeGuard> typeGuards, Set<TypeGuard> castGuards, LocalContext currentValues,
                    SpecializationExecution specializationExecution) {
        CodeTreeBuilder checksBuilder = CodeTreeBuilder.createBuilder();
        CodeTreeBuilder localsBuilder = CodeTreeBuilder.createBuilder();
        for (TypeGuard typeGuard : typeGuards) {
            int signatureIndex = typeGuard.getSignatureIndex();
            LocalVariable value = currentValues.getValue(signatureIndex);
            TypeData targetType = typeGuard.getType();
            if (!value.getType().needsCastTo(targetType)) {
                continue;
            }
            NodeExecutionData execution = node.getChildExecutions().get(signatureIndex);
            if (!checksBuilder.isEmpty()) {
                checksBuilder.string(" && ");
            }

            CodeTreeBuilder checkBuilder = checksBuilder.create();
            CodeTreeBuilder castBuilder = checksBuilder.create();

            LocalVariable shortCircuit = currentValues.getShortCircuit(execution);
            if (shortCircuit != null) {
                checkBuilder.string("(");
                CodeTreeBuilder referenceBuilder = checkBuilder.create();
                if (!shortCircuit.getType().isPrimitive()) {
                    referenceBuilder.string("(boolean) ");
                }
                referenceBuilder.tree(shortCircuit.createReference());
                checkBuilder.string("!").tree(referenceBuilder.build());
                checkBuilder.string(" || ");
                castBuilder.tree(referenceBuilder.build()).string(" ? ");
            }

            List<ImplicitCastData> sourceTypes = typeSystem.lookupByTargetType(targetType);
            CodeTree valueReference = value.createReference();
            if (sourceTypes.isEmpty()) {
                checkBuilder.tree(TypeSystemCodeGenerator.check(targetType, value.createReference()));
                castBuilder.tree(TypeSystemCodeGenerator.cast(targetType, valueReference));
            } else {
                ImplicitCastOptimization opt = options.implicitCastOptimization();
                if (specializationExecution.isFastPath() && !opt.isNone()) {
                    if (opt.isDuplicateTail()) {
                        String typeHintField = implicitClassFieldName(execution);
                        checkBuilder.tree(TypeSystemCodeGenerator.implicitCheck(targetType, valueReference, typeHintField));
                        castBuilder.tree(TypeSystemCodeGenerator.implicitCast(targetType, valueReference, typeHintField));
                    } else if (opt.isMergeCasts()) {
                        checkBuilder.tree(ImplicitCastNodeFactory.check(implicitNodeFieldName(execution), valueReference));
                        castBuilder.tree(ImplicitCastNodeFactory.cast(implicitNodeFieldName(execution), valueReference));
                    } else {
                        throw new AssertionError("implicit cast opt");
                    }
                } else {
                    checkBuilder.tree(TypeSystemCodeGenerator.implicitCheck(targetType, valueReference, null));
                    castBuilder.tree(TypeSystemCodeGenerator.implicitCast(targetType, valueReference, null));
                }
            }

            if (shortCircuit != null) {
                checkBuilder.string(")");
                castBuilder.string(" : ").defaultValue(targetType.getPrimitiveType());
            }

            if (castGuards == null || castGuards.contains(typeGuard)) {
                LocalVariable castVariable = currentValues.getValue(execution).nextName().newType(typeGuard.getType()).accessWith(null);
                currentValues.setValue(execution, castVariable);
                localsBuilder.tree(castVariable.createDeclaration(castBuilder.build()));
            }

            checksBuilder.tree(checkBuilder.build());
        }

        if (specialization != null && !specializationExecution.isFastPath()) {
            for (CacheExpression cache : specialization.getCaches()) {
                if (specialization.isCacheBoundByGuard(cache)) {
                    initializeCache(localsBuilder, specialization, cache, currentValues);
                }
            }
        }

        return new CodeTree[]{checksBuilder.build(), localsBuilder.build()};
    }

    private void initializeCache(CodeTreeBuilder builder, SpecializationData specialization, CacheExpression cache, LocalContext currentValues) {
        CodeTree initializer = DSLExpressionGenerator.write(cache.getExpression(), accessParent(null), castBoundTypes(bindExpressionValues(cache.getExpression(), specialization, currentValues)));
        String name = cache.getParameter().getLocalName();
        // multiple specializations might use the same name
        String varName = name + specialization.getIndex();
        TypeMirror type = cache.getParameter().getType();
        builder.declaration(type, varName, initializer);
        currentValues.set(name, new LocalVariable(null, type, varName, null));
    }

    public static final class LocalContext {

        private final NodeGenFactory factory;
        private final Map<String, LocalVariable> values = new HashMap<>();

        private LocalContext(NodeGenFactory factory) {
            this.factory = factory;
        }

        public void loadFastPathState(SpecializationData specialization) {
            for (CacheExpression cache : specialization.getCaches()) {
                Parameter cacheParameter = cache.getParameter();
                String name = cacheParameter.getVariableElement().getSimpleName().toString();
                set(cacheParameter.getLocalName(), new LocalVariable(cacheParameter.getTypeSystemType(), cacheParameter.getType(), name, CodeTreeBuilder.singleString("this." + name)));
            }

            for (AssumptionExpression assumption : specialization.getAssumptionExpressions()) {
                String name = assumptionName(assumption);
                TypeMirror type = assumption.getExpression().getResolvedType();
                set(name, new LocalVariable(null, type, name, CodeTreeBuilder.singleString("this." + name)));
            }
        }

        public CodeExecutableElement createMethod(Set<Modifier> modifiers, TypeMirror returnType, String name, String... optionalArguments) {
            CodeExecutableElement method = new CodeExecutableElement(modifiers, returnType, name);
            addParametersTo(method, optionalArguments);
            return method;
        }

        public static LocalContext load(NodeGenFactory factory, int signatureSize, int varargsThreshold) {
            LocalContext context = new LocalContext(factory);
            context.loadValues(signatureSize, varargsThreshold);
            return context;
        }

        public static LocalContext load(NodeGenFactory factory) {
            return load(factory, factory.node.getSignatureSize(), factory.varArgsThreshold);
        }

        public LocalContext copy() {
            LocalContext copy = new LocalContext(factory);
            copy.values.putAll(values);
            return copy;
        }

        private static String fieldValueName(NodeFieldData field) {
            return field.getName() + "Value";
        }

        @SuppressWarnings("static-method")
        public LocalVariable createValue(NodeExecutionData execution, TypeData type) {
            return new LocalVariable(type, type.getPrimitiveType(), valueName(execution), null);
        }

        public LocalVariable createShortCircuitValue(NodeExecutionData execution) {
            return new LocalVariable(factory.typeSystem.getBooleanType(), factory.getType(boolean.class), shortCircuitName(execution), null);
        }

        private static String valueName(NodeExecutionData execution) {
            return execution.getName() + "Value";
        }

        private static String shortCircuitName(NodeExecutionData execution) {
            return "has" + ElementUtils.firstLetterUpperCase(valueName(execution));
        }

        public void set(String id, LocalVariable var) {
            values.put(id, var);
        }

        public LocalVariable get(String id) {
            return values.get(id);
        }

        public LocalVariable get(Parameter parameter, int signatureIndex) {
            LocalVariable var = get(parameter.getLocalName());
            if (var == null && parameter.getSpecification().isSignature()) {
                // lookup by signature index for executeWith
                List<NodeExecutionData> childExecutions = factory.node.getChildExecutions();
                if (signatureIndex < childExecutions.size() && signatureIndex >= 0) {
                    NodeExecutionData execution = childExecutions.get(signatureIndex);
                    var = getValue(execution);
                }
            }
            return var;
        }

        public LocalVariable getValue(NodeExecutionData execution) {
            return get(valueName(execution));
        }

        public LocalVariable getValue(int signatureIndex) {
            return getValue(factory.node.getChildExecutions().get(signatureIndex));
        }

        public void removeValue(String id) {
            values.remove(id);
        }

        public void setValue(NodeExecutionData execution, LocalVariable var) {
            values.put(valueName(execution), var);
        }

        public void setShortCircuitValue(NodeExecutionData execution, LocalVariable var) {
            if (var == null) {
                return;
            }
            values.put(shortCircuitName(execution), var);
        }

        private boolean needsVarargs(boolean requireLoaded, int varArgsThreshold) {
            int size = 0;
            for (NodeExecutionData execution : factory.node.getChildExecutions()) {
                if (requireLoaded && getValue(execution) == null) {
                    continue;
                }
                if (execution.isShortCircuit()) {
                    size += 2;
                } else {
                    size++;
                }
            }
            return size >= varArgsThreshold;
        }

        private void loadValues(int evaluatedArguments, int varargsThreshold) {
            values.put(FRAME_VALUE, new LocalVariable(null, factory.getType(Frame.class), FRAME_VALUE, null));

            for (NodeFieldData field : factory.node.getFields()) {
                String fieldName = fieldValueName(field);
                values.put(fieldName, new LocalVariable(null, field.getType(), fieldName, factory.accessParent(field.getName())));
            }

            boolean varargs = needsVarargs(false, varargsThreshold);
            for (int i = 0; i < evaluatedArguments; i++) {
                List<NodeExecutionData> childExecutions = factory.node.getChildExecutions();
                if (i >= childExecutions.size()) {
                    break;
                }
                NodeExecutionData execution = childExecutions.get(i);
                if (execution.isShortCircuit()) {
                    LocalVariable shortCircuit = createShortCircuitValue(execution).makeGeneric();
                    if (varargs) {
                        shortCircuit = shortCircuit.accessWith(createReadVarargs(i));
                    }
                    values.put(shortCircuit.getName(), shortCircuit);
                }
                LocalVariable value = createValue(execution, factory.genericType);
                if (varargs) {
                    value = value.accessWith(createReadVarargs(i));
                }
                values.put(value.getName(), value);
            }
        }

        private static CodeTree createReadVarargs(int i) {
            return CodeTreeBuilder.createBuilder().string("args_[").string(String.valueOf(i)).string("]").build();
        }

        public void addReferencesTo(CodeTreeBuilder builder, String... optionalNames) {
            for (String var : optionalNames) {
                LocalVariable local = values.get(var);
                if (local == null) {
                    builder.nullLiteral();
                } else {
                    builder.tree(local.createReference());
                }
            }

            List<NodeExecutionData> executions = factory.node.getChildExecutions();
            for (NodeExecutionData execution : executions) {
                if (execution.isShortCircuit()) {
                    LocalVariable shortCircuitVar = getShortCircuit(execution);
                    if (shortCircuitVar != null) {
                        builder.tree(shortCircuitVar.createReference());
                    }
                }
                LocalVariable var = getValue(execution);
                if (var != null) {
                    builder.startGroup();
                    if (executions.size() == 1 && ElementUtils.typeEquals(var.getTypeMirror(), factory.getType(Object[].class))) {
                        // if the current type is Object[] do not use varargs for a single argument
                        builder.string("(Object) ");
                    }
                    builder.tree(var.createReference());
                    builder.end();
                }
            }
        }

        public void addParametersTo(CodeExecutableElement method, String... optionalNames) {
            for (String var : optionalNames) {
                LocalVariable local = values.get(var);
                if (local != null) {
                    method.addParameter(local.createParameter());
                }
            }
            if (needsVarargs(true, factory.varArgsThreshold)) {
                method.addParameter(new CodeVariableElement(factory.getType(Object[].class), "args_"));
                method.setVarArgs(true);
            } else {
                for (NodeExecutionData execution : factory.node.getChildExecutions()) {
                    if (execution.isShortCircuit()) {
                        LocalVariable shortCircuitVar = getShortCircuit(execution);
                        if (shortCircuitVar != null) {
                            method.addParameter(shortCircuitVar.createParameter());
                        }
                    }

                    LocalVariable var = getValue(execution);
                    if (var != null) {
                        method.addParameter(var.createParameter());
                    }
                }
            }
        }

        private LocalVariable getShortCircuit(NodeExecutionData execution) {
            return values.get(shortCircuitName(execution));
        }

        @Override
        public String toString() {
            return "LocalContext [values=" + values + "]";
        }

    }

    public static final class LocalVariable {

        private final TypeData type;
        private final TypeMirror typeMirror;
        private final CodeTree accessorTree;
        private final String name;

        public static LocalVariable fromParameter(Parameter parameter) {
            NodeExecutionData execution = parameter.getSpecification().getExecution();
            String name = null;
            if (execution == null) {
                name = parameter.getLocalName();
            } else {
                name = createName(execution);
            }
            return new LocalVariable(parameter.getTypeSystemType(), parameter.getType(), name, null);
        }

        private LocalVariable(TypeData type, TypeMirror typeMirror, String name, CodeTree accessorTree) {
            Objects.requireNonNull(typeMirror);
            this.typeMirror = typeMirror;
            this.accessorTree = accessorTree;
            this.type = type;
            this.name = name;
        }

        public TypeData getType() {
            return type;
        }

        public String getShortCircuitName() {
            return "has" + ElementUtils.firstLetterUpperCase(getName());
        }

        public String getName() {
            return name;
        }

        private static String createNextName(String name) {
            return name + "_";
        }

        private static String createName(NodeExecutionData execution) {
            if (execution == null) {
                return "<error>";
            }
            return execution.getName() + "Value";
        }

        public TypeMirror getTypeMirror() {
            return typeMirror;
        }

        public CodeVariableElement createParameter() {
            return new CodeVariableElement(getTypeMirror(), getName());
        }

        public CodeTree createDeclaration(CodeTree init) {
            return CodeTreeBuilder.createBuilder().declaration(getTypeMirror(), getName(), init).build();
        }

        public CodeTree createReference() {
            if (accessorTree != null) {
                return accessorTree;
            } else {
                return CodeTreeBuilder.singleString(getName());
            }
        }

        public LocalVariable newType(TypeData newType) {
            return new LocalVariable(newType, newType.getPrimitiveType(), name, accessorTree);
        }

        public LocalVariable newType(TypeMirror newType) {
            return new LocalVariable(type, newType, name, accessorTree);
        }

        public LocalVariable accessWith(CodeTree tree) {
            return new LocalVariable(type, typeMirror, name, tree);
        }

        public LocalVariable nextName() {
            return new LocalVariable(type, typeMirror, createNextName(name), accessorTree);
        }

        public LocalVariable makeGeneric() {
            return newType(type.getTypeSystem().getGenericTypeData());
        }

        @Override
        public String toString() {
            return "Local[type = " + getTypeMirror() + ", name = " + name + ", accessWith = " + accessorTree + "]";
        }

    }

    private interface SpecializationExecution {

        boolean isFastPath();

        CodeTree createExecute(SpecializationData specialization, LocalContext currentValues);

    }

}
