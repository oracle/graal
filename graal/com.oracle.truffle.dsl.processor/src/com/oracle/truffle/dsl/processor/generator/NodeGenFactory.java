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

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.internal.*;
import com.oracle.truffle.api.dsl.internal.DSLOptions.ImplicitCastOptimization;
import com.oracle.truffle.api.dsl.internal.DSLOptions.TypeBoxingOptimization;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.java.*;
import com.oracle.truffle.dsl.processor.java.model.*;
import com.oracle.truffle.dsl.processor.model.*;
import com.oracle.truffle.dsl.processor.parser.*;
import com.oracle.truffle.dsl.processor.parser.SpecializationGroup.TypeGuard;

public class NodeGenFactory {

    private static final String FRAME_VALUE = "frameValue";

    private static final String NAME_SUFFIX = "_";

    private final ProcessorContext context;
    private final NodeData node;
    private final TypeSystemData typeSystem;
    private final TypeData genericType;
    private final DSLOptions options;

    public NodeGenFactory(ProcessorContext context, NodeData node) {
        this.context = context;
        this.node = node;
        this.typeSystem = node.getTypeSystem();
        this.genericType = typeSystem.getGenericTypeData();
        this.options = typeSystem.getOptions();
    }

    public static String nodeTypeName(NodeData node) {
        return resolveNodeId(node) + "NodeGen";
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
        return specialization.getId() + "Node_";
    }

    private static TypeMirror specializationType(SpecializationData specialization) {
        return new GeneratedTypeMirror(ElementUtils.getPackageName(specialization.getNode().getTemplateType()) + "." + nodeTypeName(specialization.getNode()), specializationTypeName(specialization));
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

    private static CodeTree accessParent(String name) {
        if (name == null) {
            return CodeTreeBuilder.singleString("root");
        } else {
            return CodeTreeBuilder.createBuilder().string("root.").string(name).build();
        }
    }

    private static String assumptionName(String assumption) {
        return assumption + "_";
    }

    public CodeTypeElement create() {
        CodeTypeElement clazz = GeneratorUtils.createClass(node, null, modifiers(FINAL), nodeTypeName(node), node.getTemplateType().asType());
        ElementUtils.setVisibility(clazz.getModifiers(), ElementUtils.getVisibility(node.getTemplateType().getModifiers()));
        clazz.getImplements().add(getType(SpecializedNode.class));

        for (String assumption : node.getAssumptions()) {
            clazz.add(new CodeVariableElement(modifiers(PRIVATE, FINAL), getType(Assumption.class), assumptionName(assumption)));
        }

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

        List<? extends ExecutableElement> superConstructors = ElementFilter.constructorsIn(node.getTemplateType().getEnclosedElements());
        for (ExecutableElement superConstructor : superConstructors) {
            if (getVisibility(superConstructor.getModifiers()) == PRIVATE) {
                continue;
            }
            if (superConstructors.size() > 1 && superConstructor.getParameters().size() > 0 &&
                            ElementUtils.typeEquals(superConstructor.getEnclosingElement().asType(), superConstructor.getParameters().get(0).asType())) {
                // constructor is copy constructor
                continue;
            }
            clazz.add(createNodeConstructor(clazz, superConstructor));
        }

        for (NodeExecutionData execution : node.getChildExecutions()) {
            clazz.add(createNodeField(PRIVATE, execution.getNodeType(), nodeFieldName(execution), Child.class));
        }

        for (NodeExecutionData execution : node.getChildExecutions()) {
            if (findSpecializedExecutables(execution, node.findSpecializedTypes(execution), options.polymorphicTypeBoxingElimination()).isEmpty()) {
                continue;
            }
            clazz.add(createNodeField(PRIVATE, getType(Class.class), polymorphicTypeProfileFieldName(execution), CompilationFinal.class));
        }

        for (SpecializationData specialization : node.getSpecializations()) {
            if (mayBeExcluded(specialization)) {
                clazz.add(createNodeField(PRIVATE, getType(boolean.class), excludedFieldName(specialization), CompilationFinal.class));
            }
        }

        clazz.add(createNodeField(PRIVATE, TypeSystemNodeFactory.nodeType(node.getTypeSystem()), specializationStartFieldName(), Child.class));
        clazz.add(createMethodGetSpecializationNode());
        clazz.add(createDeepCopyMethod());
        clazz.add(createGetCostMethod());

        Collection<TypeData> specializedTypes = node.findSpecializedReturnTypes();
        for (ExecutableTypeData execType : node.getExecutableTypes()) {
            if (shouldImplementExecutableType(specializedTypes, execType)) {
                clazz.add(createExecutableTypeOverride(execType));
            }
        }

        SpecializationData initialSpecialization = createSpecializations(clazz);

        for (ExecutableElement constructor : ElementFilter.constructorsIn(clazz.getEnclosedElements())) {
            CodeTreeBuilder builder = ((CodeExecutableElement) constructor).appendBuilder();
            builder.startStatement();
            builder.string("this.").string(specializationStartFieldName());
            builder.string(" = ").tree(createCallCreateMethod(initialSpecialization, "this", null));
            builder.end();
        }

        return clazz;
    }

    private CodeExecutableElement createNodeConstructor(CodeTypeElement clazz, ExecutableElement superConstructor) {
        CodeExecutableElement constructor = GeneratorUtils.createConstructorUsingFields(modifiers(PUBLIC), clazz, superConstructor);

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
                    callBuilder.tree(callTemplateMethod(builder, null, createCast, nameTree));
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
                accessor = callTemplateMethod(builder, null, createCast, accessor);
            }

            if (execution.isIndexed()) {
                CodeTreeBuilder nullCheck = builder.create();
                nullCheck.string(name).string(" != null ? ");
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

        if (isSingleSpecializable(reachableSpecializations)) {
            SpecializationData single = reachableSpecializations.get(0);
            clazz.add(createSingleSpecialization(single));
            return single;
        } else {
            CodeTypeElement baseSpecialization = clazz.add(createBaseSpecialization(clazz));
            TypeMirror baseSpecializationType = baseSpecialization.asType();

            Map<SpecializationData, CodeTypeElement> generated = new LinkedHashMap<>();

            List<SpecializationData> generateSpecializations = new ArrayList<>();
            generateSpecializations.add(node.getUninitializedSpecialization());
            if (needsPolymorphic(reachableSpecializations)) {
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
    }

    // create specialization

    private CodeTypeElement createBaseSpecialization(CodeTypeElement parentClass) {
        CodeTypeElement clazz = createClass(node, null, modifiers(PRIVATE, ABSTRACT, STATIC), "BaseNode_", TypeSystemNodeFactory.nodeType(typeSystem));

        clazz.addOptional(createSpecializationConstructor(clazz, null, null));
        clazz.add(new CodeVariableElement(modifiers(PROTECTED, FINAL), nodeType(node), "root"));

        clazz.addOptional(createUnsupported());
        clazz.add(createGetSuppliedChildren());

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
                    if (specializedType.isGeneric()) {
                        parentClass.add(createExecuteChildMethod(execution, specializedType));
                    } else {
                        clazz.add(createExecuteChildMethod(execution, specializedType));
                    }
                }
            }
        }

        return clazz;
    }

    private CodeTypeElement createSingleSpecialization(SpecializationData specialization) {
        CodeTypeElement clazz = createClass(node, specialization, modifiers(PRIVATE, STATIC, FINAL), specializationTypeName(specialization), TypeSystemNodeFactory.nodeType(typeSystem));
        CodeExecutableElement constructor = clazz.addOptional(createSpecializationConstructor(clazz, null, "0"));
        clazz.add(new CodeVariableElement(modifiers(PROTECTED, FINAL), nodeType(node), "root"));
        TypeData returnType = specialization.getReturnType().getTypeSystemType();
        Set<Integer> evaluatedCount = getEvaluatedCounts();
        for (int evaluated : evaluatedCount) {
            clazz.add(createFastPathExecuteMethod(specialization, null, evaluated));
        }
        if (isTypeBoxingEliminated(specialization)) {
            clazz.add(createFastPathExecuteMethod(specialization, returnType, 0));
        }
        clazz.add(createFastPathWrapExecuteMethod(genericType, null));

        clazz.addOptional(createUnsupported());
        clazz.addOptional(createSpecializationCreateMethod(specialization, constructor));
        clazz.add(createGetSuppliedChildren());

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
        builder.startReturn().startCall(specializationStartFieldName(), "getNodeCost").end().end();
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
        if (specialization.getExcludedBy().isEmpty() && !specialization.isPolymorphic()) {
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
            builder.statement("return super.merge(newNode)");
        }

        return executable;
    }

    private Element createFastPathWrapVoidMethod(TypeData wrap) {
        CodeExecutableElement executable = new CodeExecutableElement(modifiers(PUBLIC), typeSystem.getVoidType().getPrimitiveType(), TypeSystemNodeFactory.executeName(typeSystem.getVoidType()));
        executable.addParameter(new CodeVariableElement(getType(VirtualFrame.class), FRAME_VALUE));
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
        executable.addParameter(new CodeVariableElement(getType(VirtualFrame.class), FRAME_VALUE));
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

    private boolean needsPolymorphic(List<SpecializationData> reachableSpecializations) {
        if (reachableSpecializations.size() > 1) {
            return true;
        }
        if (options.implicitCastOptimization().isDuplicateTail()) {
            SpecializationData specialization = reachableSpecializations.get(0);
            for (Parameter parameter : specialization.getSignatureParameters()) {
                if (parameter.getTypeSystemType().hasImplicitSourceTypes()) {
                    return true;
                }
            }
        }
        return false;
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
                return createSlowPathExecute(specialization, locals);
            }

            public boolean isFastPath() {
                return false;
            }
        });

        builder.tree(execution);

        if (hasFallthrough(group, genericType, locals, false)) {
            builder.returnNull();
        }
        return method;
    }

    private CodeExecutableElement createFallbackGuardMethod() {
        boolean frameUsed = node.isFrameUsedByAnyGuard(context);
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

    // create specialization

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
        builder.tree(callTemplateMethod(builder, accessParent(null), fallback, locals));
        builder.end();

        return method;
    }

    private boolean isSingleSpecializable(List<SpecializationData> reachableSpecializations) {
        if (reachableSpecializations.size() != 1) {
            return false;
        }
        return !reachableSpecializations.get(0).hasRewrite(context);
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

    private CodeExecutableElement createExecutableTypeOverride(ExecutableTypeData execType) {
        final String varArgsName = "args";
        final TypeData returnType = execType.getType();
        final TypeData executedType = execType.getEvaluatedCount() > 0 ? null : returnType;

        CodeExecutableElement method = cloneExecutableTypeOverride(execType, varArgsName);

        LocalContext locals = LocalContext.load(this, execType.getSignatureSize());

        // rename varargs parameter
        int signatureIndex = 0;
        for (Parameter parameter : execType.getSignatureParameters()) {
            if (parameter.isTypeVarArgs()) {
                String newName = varArgsName + "[" + parameter.getTypeVarArgsIndex() + "]";
                NodeExecutionData execution = node.getChildExecutions().get(signatureIndex);
                locals.setValue(execution, locals.getValue(execution).accessWith(CodeTreeBuilder.singleString(newName)));
            }
            signatureIndex++;
        }

        CodeTreeBuilder builder = method.createBuilder();

        // create acceptAndExecute
        CodeTreeBuilder executeBuilder = builder.create();
        executeBuilder.startCall(specializationStartFieldName(), TypeSystemNodeFactory.executeName(executedType));
        Parameter frame = execType.getFrame();
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
        if (!execType.hasUnexpectedValue(context) && !returnType.isGeneric() && !returnType.isVoid()) {
            builder.startTryBlock();
            builder.tree(contentBuilder.build());
            builder.end().startCatchBlock(getType(UnexpectedResultException.class), "ex");
            builder.startThrow().startNew(getType(AssertionError.class)).end().end();
            builder.end();
        } else {
            builder.tree(contentBuilder.build());
        }

        return method;
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

    private static List<ExecutableTypeData> findSpecializedExecutables(NodeExecutionData execution, Collection<TypeData> types, TypeBoxingOptimization optimization) {
        if (optimization == TypeBoxingOptimization.NONE) {
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

    private static CodeTree callTemplateMethod(CodeTreeBuilder parent, CodeTree receiver, TemplateMethod method, CodeTree... boundValues) {
        CodeTreeBuilder builder = parent.create();
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
            builder.string(parameter.getLocalName());
        }
        builder.end();
        return builder.build();
    }

    private static CodeTree callTemplateMethod(CodeTreeBuilder parent, CodeTree receiver, TemplateMethod method, LocalContext currentValues) {
        CodeTree[] bindings = new CodeTree[method.getParameters().size()];

        int signatureIndex = 0;
        for (int i = 0; i < bindings.length; i++) {
            Parameter parameter = method.getParameters().get(i);
            LocalVariable var = currentValues.get(parameter, signatureIndex);
            if (var != null) {
                CodeTree valueReference = bindings[i] = var.createReference();
                if (parameter.getTypeSystemType() != null && var.getType() != null && var.getType().needsCastTo(parameter.getTypeSystemType())) {
                    valueReference = TypeSystemCodeGenerator.cast(parameter.getTypeSystemType(), valueReference);
                }
                bindings[i] = valueReference;
            }
            if (parameter.getSpecification().isSignature()) {
                signatureIndex++;
            }
        }
        return callTemplateMethod(parent, receiver, method, bindings);
    }

    private SpecializationGroup createSpecializationGroups() {
        return SpecializationGroup.create(getReachableSpecializations());
    }

    private CodeTree createSlowPathExecute(SpecializationData specialization, LocalContext currentValues) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        if (specialization.isFallback()) {
            return builder.returnNull().build();
        }
        if (node.isFrameUsedByAnyGuard(context)) {
            builder.tree(createTransferToInterpreterAndInvalidate());
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

        builder.startReturn().tree(createCallCreateMethod(specialization, null, currentValues)).end();

        if (mayBeExcluded(specialization)) {
            CodeTreeBuilder checkHasSeenBuilder = builder.create();
            checkHasSeenBuilder.startIf().string("!").tree(accessParent(excludedFieldName(specialization))).end().startBlock();
            checkHasSeenBuilder.tree(builder.build());
            checkHasSeenBuilder.end();
            return checkHasSeenBuilder.build();
        }
        return builder.build();
    }

    private static boolean hasFallthrough(SpecializationGroup group, TypeData forType, LocalContext currentValues, boolean fastPath) {
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

        List<GuardExpression> expressions = new ArrayList<>(group.getGuards());
        expressions.removeAll(group.findElseConnectableGuards());
        if (!expressions.isEmpty()) {
            return true;
        }

        if ((!fastPath || forType.isGeneric()) && !group.getAssumptions().isEmpty()) {
            return true;
        }

        if (!fastPath && group.getSpecialization() != null && !group.getSpecialization().getExceptions().isEmpty()) {
            return true;
        }

        if (!group.getChildren().isEmpty()) {
            return hasFallthrough(group.getChildren().get(group.getChildren().size() - 1), forType, currentValues, fastPath);
        }

        return false;
    }

    private Element createGetSuppliedChildren() {
        ArrayType nodeArray = context.getEnvironment().getTypeUtils().getArrayType(getType(Node.class));

        CodeExecutableElement method = new CodeExecutableElement(modifiers(PROTECTED, FINAL), nodeArray, "getSuppliedChildren");
        method.getAnnotationMirrors().add(new CodeAnnotationMirror(context.getDeclaredType(Override.class)));

        CodeTreeBuilder builder = method.createBuilder();

        builder.startReturn().startNewArray(nodeArray, null);
        for (int i = 0; i < node.getChildExecutions().size(); i++) {
            NodeExecutionData execution = node.getChildExecutions().get(i);
            if (execution.isShortCircuit()) {
                builder.nullLiteral();
            }
            builder.tree(accessParent(nodeFieldName(execution)));
        }
        builder.end().end();

        return method;
    }

    // create specialization

    private CodeTree createCallCreateMethod(SpecializationData specialization, String rootName, LocalContext currentValues) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();

        TypeMirror specializationType = specializationType(specialization);
        if (options.useLazyClassLoading()) {
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
                LocalVariable local = currentValues.get(p.getLocalName());
                CodeVariableElement var = createImplicitProfileParameter(p.getSpecification().getExecution(), p.getTypeSystemType());
                if (var != null) {
                    builder.tree(local.createReference());
                }
            }
        }
        builder.end();

        return builder.build();
    }

    private Element createSpecializationCreateMethod(SpecializationData specialization, CodeExecutableElement constructor) {
        if (!options.useLazyClassLoading()) {
            return null;
        }

        CodeExecutableElement executable = CodeExecutableElement.clone(context.getEnvironment(), constructor);

        TypeMirror specializationType = specializationType(specialization);

        executable.setReturnType(TypeSystemNodeFactory.nodeType(typeSystem));
        executable.setSimpleName(CodeNames.of("create"));
        executable.getModifiers().add(STATIC);

        CodeTreeBuilder builder = executable.createBuilder();
        builder.startReturn().startNew(specializationType);
        for (VariableElement parameter : executable.getParameters()) {
            builder.string(parameter.getSimpleName().toString());
        }
        builder.end().end();
        return executable;
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

    private CodeTree createCallNext(TypeData forType, LocalContext currentValues) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.startCall("next", TypeSystemNodeFactory.executeName(null));
        currentValues.addReferencesTo(builder, FRAME_VALUE);
        builder.end();
        return TypeSystemCodeGenerator.expect(genericType, forType, builder.build());
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

    private static ExecutableTypeData findSpecializedExecutableType(NodeExecutionData execution, TypeData type) {
        NodeChildData child = execution.getChild();
        int executeWithCount = child.getExecuteWith().size();
        return child.getNodeData().findExecutableType(type, executeWithCount);
    }

    private boolean hasUnexpectedResult(NodeExecutionData execution, TypeData type) {
        if (type.isGeneric() || type.isVoid()) {
            return false;
        }
        List<ExecutableTypeData> executableTypes = new ArrayList<>();
        executableTypes.add(findSpecializedExecutableType(execution, type));

        if (!options.implicitCastOptimization().isNone()) {
            executableTypes.addAll(findSpecializedExecutables(execution, type.getImplicitSourceTypes(), options.implicitTypeBoxingOptimization()));
        }

        for (ExecutableTypeData executableType : executableTypes) {
            if (executableType != null && executableType.hasUnexpectedValue(context)) {
                return true;
            }
        }
        return false;
    }

    private Element createFastPathExecuteMethod(SpecializationData specialization, final TypeData forType, int evaluatedArguments) {
        TypeData type = forType == null ? genericType : forType;
        LocalContext currentLocals = LocalContext.load(this, evaluatedArguments);

        CodeExecutableElement executable = currentLocals.createMethod(modifiers(PUBLIC), type.getPrimitiveType(), TypeSystemNodeFactory.executeName(forType), FRAME_VALUE);
        executable.getAnnotationMirrors().add(new CodeAnnotationMirror(context.getDeclaredType(Override.class)));

        if (!type.isGeneric()) {
            executable.getThrownTypes().add(getType(UnexpectedResultException.class));
        }

        CodeTreeBuilder builder = executable.createBuilder();

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
            builder.startReturn().tree(createCallNext(type, currentLocals)).end();
        } else if (specialization.isUninitialized()) {
            builder.startReturn().tree(createCallDelegate("uninitialized", null, type, currentLocals)).end();
        } else {
            final TypeData finalType = type;
            SpecializationGroup group = SpecializationGroup.create(specialization);
            SpecializationExecution executionFactory = new SpecializationExecution() {
                public CodeTree createExecute(SpecializationData s, LocalContext values) {
                    return createFastPathExecute(finalType, s, values);
                }

                public boolean isFastPath() {
                    return true;
                }
            };
            builder.tree(createGuardAndCast(group, type, currentLocals, executionFactory));
            if (hasFallthrough(group, type, originalValues, true) || group.getSpecialization().isFallback()) {
                builder.startReturn().tree(createCallNext(type, originalValues)).end();
            }
        }

        return executable;
    }

    private LocalVariable resolveShortCircuit(SpecializationData specialization, NodeExecutionData execution, LocalContext currentLocals) {
        LocalVariable shortCircuit = null;
        SpecializationData resolvedSpecialization = specialization;
        if (specialization == null) {
            resolvedSpecialization = node.getGenericSpecialization();
        }

        if (execution.isShortCircuit()) {
            ShortCircuitData shortCircuitData = resolvedSpecialization.getShortCircuits().get(calculateShortCircuitIndex(execution));
            CodeTree access = callTemplateMethod(CodeTreeBuilder.createBuilder(), accessParent(null), shortCircuitData, currentLocals);
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

    private CodeTree createFastPathExecute(final TypeData forType, SpecializationData specialization, LocalContext currentValues) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        int ifCount = 0;
        if (specialization.isFallback()) {
            builder.startIf().startCall("guardFallback");
            if (node.isFrameUsedByAnyGuard(context)) {
                builder.string(FRAME_VALUE);
            }
            currentValues.addReferencesTo(builder);

            builder.end();
            builder.end();
            builder.startBlock();
            ifCount++;
        }
        CodeTreeBuilder execute = builder.create();
        execute.startReturn();
        if (specialization.getMethod() == null) {
            execute.startCall("unsupported");
            currentValues.addReferencesTo(execute, FRAME_VALUE);
            execute.end();
        } else {
            execute.tree(callTemplateMethod(execute, accessParent(null), specialization, currentValues));
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
                if (isTypeGuardUsedInAnyGuardBelow(group, currentValues, castGuard)) {
                    castGuards.add(castGuard);
                }
            }
        }
        CodeTree[] checkAndCast = createTypeCheckAndCast(group.getTypeGuards(), castGuards, currentValues, execution);
        CodeTree check = checkAndCast[0];
        CodeTree cast = checkAndCast[1];

        List<GuardExpression> elseGuardExpressions = group.findElseConnectableGuards();
        List<GuardExpression> guardExpressions = new ArrayList<>(group.getGuards());
        guardExpressions.removeAll(elseGuardExpressions);
        CodeTree methodGuards = createMethodGuardCheck(guardExpressions, currentValues);

        if (!group.getAssumptions().isEmpty()) {
            if (execution.isFastPath() && !forType.isGeneric()) {
                cast = appendAssumptionFastPath(cast, group.getAssumptions(), forType, currentValues);
            } else {
                methodGuards = appendAssumptionSlowPath(methodGuards, group.getAssumptions());
            }
        }

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

        boolean reachable = isReachableGroup(group, ifCount);
        if (reachable) {
            for (SpecializationGroup child : group.getChildren()) {
                builder.tree(createGuardAndCast(child, forType, currentValues.copy(), execution));
            }
            SpecializationData specialization = group.getSpecialization();
            if (specialization != null) {
                builder.tree(execution.createExecute(specialization, currentValues));
            }
        }
        builder.end(ifCount);

        return builder.build();
    }

    private static CodeTree appendAssumptionSlowPath(CodeTree methodGuards, List<String> assumptions) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();

        builder.tree(methodGuards);
        String connect = methodGuards.isEmpty() ? "" : " && ";
        for (String assumption : assumptions) {
            builder.string(connect);
            builder.startCall(accessParent(assumptionName(assumption)), "isValid").end();
            connect = " && ";
        }

        return builder.build();
    }

    private CodeTree appendAssumptionFastPath(CodeTree casts, List<String> assumptions, TypeData forType, LocalContext currentValues) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.tree(casts);
        builder.startTryBlock();
        for (String assumption : assumptions) {
            builder.startStatement().startCall(accessParent(assumptionName(assumption)), "check").end().end();
        }
        builder.end().startCatchBlock(getType(InvalidAssumptionException.class), "ae");
        builder.startReturn().tree(createCallNext(forType, currentValues)).end();
        builder.end();
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
        if (previous.getGuards().size() == 1 && previous.getTypeGuards().isEmpty() && previous.getAssumptions().isEmpty() &&
                        (previous.getParent() == null || previous.getMaxSpecializationIndex() != previous.getParent().getMaxSpecializationIndex())) {
            return false;
        }

        return true;
    }

    private boolean isTypeGuardUsedInAnyGuardBelow(SpecializationGroup group, LocalContext currentValues, TypeGuard typeGuard) {
        NodeExecutionData execution = node.getChildExecutions().get(typeGuard.getSignatureIndex());

        for (GuardExpression guard : group.getGuards()) {
            List<Parameter> guardParameters = guard.getResolvedGuard().findByExecutionData(execution);
            TypeData sourceType = currentValues.getValue(typeGuard.getSignatureIndex()).getType();

            for (Parameter guardParameter : guardParameters) {
                if (sourceType.needsCastTo(guardParameter.getType())) {
                    return true;
                }
            }
        }

        for (SpecializationGroup child : group.getChildren()) {
            if (isTypeGuardUsedInAnyGuardBelow(child, currentValues, typeGuard)) {
                return true;
            }
        }

        return false;
    }

    private CodeExecutableElement createExecuteChildMethod(NodeExecutionData execution, TypeData targetType) {
        LocalContext locals = LocalContext.load(this, 0);

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
        CodeTree executeChild = createExecuteChild(execution, targetType, locals.createValue(execution, targetType), locals, true);
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
            if (isSingleSpecializable(getReachableSpecializations())) {
                return false;
            }
            return findSpecializedExecutables(execution, node.findSpecializedTypes(execution), options.polymorphicTypeBoxingElimination()).size() >= 1;
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
                return findSpecializedExecutables(execution, targetType.getImplicitSourceTypes(), options.implicitTypeBoxingOptimization()).size() > 1;
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
            executeChild = createExecuteChild(execution, targetValue.getType(), targetValue, currentValues, false);
        }

        builder.tree(createTryExecuteChild(targetValue, executeChild, shortCircuit == null, hasUnexpected));
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
            builder.startReturn().tree(createCallNext(returnType, slowPathValues)).end();
            builder.end();
        }

        if (shortCircuit != null) {
            currentValues.setShortCircuitValue(execution, shortCircuit.accessWith(null));
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
        if (targetValue.getType().isGeneric()) {
            builder.startCall("root", executeChildMethodName(execution, targetValue.getType()));
        } else {
            builder.startCall(executeChildMethodName(execution, targetValue.getType()));
        }
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

    private CodeTree createExecuteChild(NodeExecutionData execution, TypeData returnType, LocalVariable target, LocalContext currentValues, boolean shared) {
        final CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        final ExecutableTypeData executableType = findSpecializedExecutableType(execution, target.getType());

        CodeTree assignment = createAssignmentStart(target, shared, false);

        if (executableType == null) {
            if (target.getType().isGeneric()) {
                throw new AssertionError("Should be caught by the parser.");
            }
            CodeTree genericExecute = createExecuteChild(execution, returnType, target.makeGeneric(), currentValues, shared);
            builder.tree(genericExecute);
        } else {
            if (target.getType().isGeneric() && executableType.getEvaluatedCount() == 0) {
                return createPolymorphicExecuteChild(execution, target, currentValues, shared);
            } else if (target.getType().hasImplicitSourceTypes()) {
                if (options.implicitCastOptimization().isNone()) {
                    CodeTree execute = createCallSharedExecuteChild(execution, target.makeGeneric(), currentValues);
                    return TypeSystemCodeGenerator.implicitExpect(target.getType(), execute, null);
                } else if (options.implicitCastOptimization().isDuplicateTail()) {
                    builder.tree(createExecuteChildDuplicateTail(builder, execution, assignment, target, currentValues));
                } else if (options.implicitCastOptimization().isMergeCasts()) {
                    // TODO
                } else {
                    throw new AssertionError();
                }
            } else {
                builder.tree(assignment);

                CodeTree accessChild;
                if (shared && target.getType().isGeneric()) {
                    accessChild = CodeTreeBuilder.singleString(nodeFieldName(execution));
                } else {
                    accessChild = accessParent(nodeFieldName(execution));
                }

                CodeTree execute = callTemplateMethod(builder, accessChild, executableType, currentValues);
                CodeTree expect = TypeSystemCodeGenerator.expect(executableType.getType(), returnType, execute);
                builder.tree(expect);
            }
        }
        return builder.build();
    }

    private CodeTree createPolymorphicExecuteChild(NodeExecutionData execution, LocalVariable target, LocalContext currentValues, boolean shared) throws AssertionError {
        ExecutableTypeData genericExecutableType = execution.getChild().getNodeData().findAnyGenericExecutableType(context, execution.getChild().getExecuteWith().size());
        if (genericExecutableType == null) {
            throw new AssertionError("error should be caught by the parser");
        }

        CodeTree assignment = createAssignmentStart(target, shared, true);

        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        CodeTreeBuilder polyChainBuilder = builder.create();
        boolean hasUnexpectedResult = false;

        Set<TypeData> specializedTypes = new HashSet<>();
        for (TypeData type : node.findSpecializedTypes(execution)) {
            specializedTypes.addAll(type.getImplicitSourceTypes());
        }

        List<ExecutableTypeData> specializedExecutables = findSpecializedExecutables(execution, specializedTypes, options.polymorphicTypeBoxingElimination());

        Collections.sort(specializedExecutables, new Comparator<ExecutableTypeData>() {
            public int compare(ExecutableTypeData o1, ExecutableTypeData o2) {
                return o1.getType().compareTo(o2.getType());
            }
        });

        if (isSingleSpecializable(getReachableSpecializations())) {
            specializedExecutables = Collections.emptyList();
        }

        boolean hasSpecializedTypes = false;
        for (ExecutableTypeData executableType : specializedExecutables) {
            hasSpecializedTypes = polyChainBuilder.startIf(hasSpecializedTypes);
            polyChainBuilder.tree(createAccessPolymorphicField(execution, shared));
            polyChainBuilder.string(" == ").typeLiteral(executableType.getType().getPrimitiveType());
            polyChainBuilder.end();
            polyChainBuilder.startBlock();
            polyChainBuilder.startStatement();
            polyChainBuilder.tree(assignment);
            polyChainBuilder.tree(callTemplateMethod(polyChainBuilder, CodeTreeBuilder.singleString(nodeFieldName(execution)), executableType, currentValues)).end();
            polyChainBuilder.end();
            hasUnexpectedResult |= executableType.hasUnexpectedValue(context);
        }

        CodeTree executeGeneric = callTemplateMethod(polyChainBuilder, CodeTreeBuilder.singleString(nodeFieldName(execution)), genericExecutableType, currentValues);

        if (specializedExecutables.isEmpty()) {
            builder.tree(assignment);
            builder.tree(executeGeneric);
        } else {
            CodeTree accessPolymorphicProfile = createAccessPolymorphicField(execution, shared);
            polyChainBuilder.startElseIf().tree(accessPolymorphicProfile).string(" == null").end();
            polyChainBuilder.startBlock();
            polyChainBuilder.tree(createTransferToInterpreterAndInvalidate());
            polyChainBuilder.declaration(genericExecutableType.getType().getPrimitiveType(), "value_", executeGeneric);

            hasSpecializedTypes = false;
            for (ExecutableTypeData executableType : specializedExecutables) {
                hasSpecializedTypes = polyChainBuilder.startIf(hasSpecializedTypes);
                polyChainBuilder.tree(TypeSystemCodeGenerator.check(executableType.getType(), CodeTreeBuilder.singleString("value_")));
                polyChainBuilder.end();
                polyChainBuilder.startBlock();
                polyChainBuilder.startStatement().tree(accessPolymorphicProfile).string(" = ").typeLiteral(executableType.getType().getPrimitiveType()).end();
                polyChainBuilder.end();
            }

            polyChainBuilder.startElseBlock();
            polyChainBuilder.startStatement().tree(accessPolymorphicProfile).string(" = ").typeLiteral(genericType.getPrimitiveType()).end();
            polyChainBuilder.end();

            polyChainBuilder.startReturn().string("value_").end();

            polyChainBuilder.end();
            polyChainBuilder.startElseBlock();
            polyChainBuilder.startStatement().tree(assignment).tree(executeGeneric).end();
            polyChainBuilder.end();

            if (hasUnexpectedResult) {
                builder.startTryBlock();
            }

            builder.tree(polyChainBuilder.build());

            if (hasUnexpectedResult) {
                builder.end();
                builder.startCatchBlock(getType(UnexpectedResultException.class), "ex");
                builder.startStatement().tree(accessPolymorphicProfile).string(" = ").typeLiteral(genericType.getPrimitiveType()).end();
                builder.startReturn().string("ex.getResult()").end();
                builder.end();
            }
        }
        return builder.build();
    }

    private static CodeTree createAssignmentStart(LocalVariable target, boolean shared, boolean accessParent) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        if (shared) {
            builder.string("return ");
        } else {
            builder.string(target.getName()).string(" = ");
            if (accessParent) {
                builder.tree(accessParent(null)).string(".");
            }
        }
        return builder.build();
    }

    private static CodeTree createAccessPolymorphicField(NodeExecutionData execution, boolean shared) {
        String name = polymorphicTypeProfileFieldName(execution);
        if (shared) {
            return CodeTreeBuilder.singleString(name);
        } else {
            return accessParent(name);
        }
    }

    private CodeTree createExecuteChildDuplicateTail(CodeTreeBuilder parent, NodeExecutionData execution, CodeTree assignment, LocalVariable target, LocalContext currentValues) {
        CodeTreeBuilder builder = parent.create();
        List<TypeData> sourceTypes = target.getType().getImplicitSourceTypes();
        String implicitClassFieldName = implicitClassFieldName(execution);
        String nodeFieldName = nodeFieldName(execution);
        List<ExecutableTypeData> executableTypes = findSpecializedExecutables(execution, sourceTypes, options.implicitTypeBoxingOptimization());

        boolean elseIf = false;
        for (ExecutableTypeData executableType : executableTypes) {
            elseIf = builder.startIf(elseIf);
            builder.string(implicitClassFieldName).string(" == ").typeLiteral(executableType.getType().getBoxedType());
            builder.end();
            builder.startBlock();
            builder.startStatement().tree(assignment);

            CodeTree execute = callTemplateMethod(builder, accessParent(nodeFieldName), executableType, currentValues);
            ImplicitCastData cast = typeSystem.lookupCast(executableType.getType(), target.getType());
            if (cast != null) {
                execute = callTemplateMethod(builder, null, cast, execute);
            }
            builder.tree(execute);
            builder.end();
            builder.end();
        }

        if (!executableTypes.isEmpty()) {
            builder.startElseBlock();
        }

        LocalVariable genericValue = target.makeGeneric().nextName();
        LocalVariable genericShortCircuit = resolveShortCircuit(null, execution, currentValues);

        builder.tree(createAssignExecuteChild(execution, genericValue.getType(), genericValue, genericShortCircuit, currentValues));
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

    private static CodeTree createFastPathTryCatchRewriteException(SpecializationData specialization, TypeData forType, LocalContext currentValues, CodeTree execution) {
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
        builder.startReturn();
        builder.tree(createCallDelegate("remove", "threw rewrite exception", forType, currentValues));
        builder.end();
        builder.end();
        return builder.build();
    }

    private static CodeTree createMethodGuardCheck(List<GuardExpression> guardExpressions, LocalContext currentValues) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        String and = "";
        for (GuardExpression guard : guardExpressions) {
            builder.string(and);
            if (guard.isNegated()) {
                builder.string("!");
            }
            builder.tree(callTemplateMethod(builder, accessParent(null), guard.getResolvedGuard(), currentValues));
            and = " && ";
        }
        return builder.build();
    }

    private CodeTree[] createTypeCheckAndCast(List<TypeGuard> typeGuards, Set<TypeGuard> castGuards, LocalContext currentValues, SpecializationExecution specializationExecution) {
        CodeTreeBuilder checksBuilder = CodeTreeBuilder.createBuilder();
        CodeTreeBuilder castsBuilder = CodeTreeBuilder.createBuilder();
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
                castsBuilder.tree(castVariable.createDeclaration(castBuilder.build()));
            }

            checksBuilder.tree(checkBuilder.build());
        }
        return new CodeTree[]{checksBuilder.build(), castsBuilder.build()};
    }

    public static final class LocalContext {

        private final NodeGenFactory factory;
        private final Map<String, LocalVariable> values = new HashMap<>();

        private LocalContext(NodeGenFactory factory) {
            this.factory = factory;
        }

        public CodeExecutableElement createMethod(Set<Modifier> modifiers, TypeMirror returnType, String name, String... optionalArguments) {
            CodeExecutableElement method = new CodeExecutableElement(modifiers, returnType, name);
            addParametersTo(method, optionalArguments);
            return method;
        }

        public static LocalContext load(NodeGenFactory factory, int signatureSize) {
            LocalContext context = new LocalContext(factory);
            context.loadValues(signatureSize);
            return context;
        }

        public static LocalContext load(NodeGenFactory factory) {
            return load(factory, factory.node.getSignatureSize());
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

        public LocalVariable get(String id) {
            return values.get(id);
        }

        public LocalVariable get(Parameter parameter, int signatureIndex) {
            LocalVariable var = get(parameter.getLocalName());
            if (var == null && parameter.getSpecification().isSignature()) {
                // lookup by signature index for executeWith
                NodeExecutionData execution = factory.node.getChildExecutions().get(signatureIndex);
                var = getValue(execution);
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

        private boolean needsVarargs(boolean requireLoaded) {
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
            return size > 4;
        }

        private void loadValues(int evaluatedArguments) {
            values.put(FRAME_VALUE, new LocalVariable(null, factory.getType(VirtualFrame.class), FRAME_VALUE, null));

            for (NodeFieldData field : factory.node.getFields()) {
                String fieldName = fieldValueName(field);
                values.put(fieldName, new LocalVariable(null, field.getType(), fieldName, NodeGenFactory.accessParent(field.getName())));
            }

            boolean varargs = needsVarargs(false);
            for (int i = 0; i < evaluatedArguments; i++) {
                NodeExecutionData execution = factory.node.getChildExecutions().get(i);
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
            if (needsVarargs(true)) {
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

        public LocalVariable accessWith(CodeTree tree) {
            return new LocalVariable(type, typeMirror, name, tree);
        }

        public LocalVariable nextName() {
            return new LocalVariable(type, typeMirror, createNextName(name), accessorTree);
        }

        public LocalVariable makeGeneric() {
            return newType(type.getTypeSystem().getGenericTypeData());
        }

    }

    private interface SpecializationExecution {

        boolean isFastPath();

        CodeTree createExecute(SpecializationData specialization, LocalContext currentValues);

    }

}
