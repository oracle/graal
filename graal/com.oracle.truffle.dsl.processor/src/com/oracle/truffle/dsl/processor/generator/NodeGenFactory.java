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
    private final TypeMirror genericType;
    private final DSLOptions options;
    private final boolean singleSpecializable;
    private final int varArgsThreshold;
    private final Set<TypeMirror> expectedTypes = new HashSet<>();
    private boolean nextUsed;

    public NodeGenFactory(ProcessorContext context, NodeData node) {
        this.context = context;
        this.node = node;
        this.typeSystem = node.getTypeSystem();
        this.genericType = context.getType(Object.class);
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

    private static String executeChildMethodName(NodeExecutionData execution, TypeMirror type) {
        return "execute" + ElementUtils.firstLetterUpperCase(execution.getName()) + (ElementUtils.isObject(type) ? "" : getTypeId(type)) + NAME_SUFFIX;
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
            if (execution.getChild() != null) {
                clazz.add(createNodeField(PRIVATE, execution.getNodeType(), nodeFieldName(execution), Child.class));
            }
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

        List<ExecutableTypeData> usedTypes = filterBaseExecutableTypes(node.getExecutableTypes(), getReachableSpecializations());
        for (ExecutableTypeData execType : usedTypes) {
            if (execType.getMethod() == null) {
                continue;
            }
            clazz.add(createExecutableTypeOverride(usedTypes, execType));
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

        for (TypeMirror type : ElementUtils.uniqueSortedTypes(expectedTypes)) {
            if (!typeSystem.hasType(type)) {
                clazz.addOptional(TypeSystemCodeGenerator.createExpectMethod(PRIVATE, typeSystem, context.getType(Object.class), type));
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
                    callBuilder.tree(callMethod(null, createCast.getMethod(), nameTree));
                    callBuilder.string(" : null");
                    name += "_";
                    builder.declaration(child.getNodeType(), name, callBuilder.build());
                }
            }
            childValues.add(name);
        }

        for (NodeExecutionData execution : node.getChildExecutions()) {
            if (execution.getChild() == null) {
                continue;
            }
            CreateCastData createCast = node.findCast(execution.getChild().getName());

            builder.startStatement();
            builder.string("this.").string(nodeFieldName(execution)).string(" = ");

            String name = childValues.get(node.getChildren().indexOf(execution.getChild()));
            CodeTreeBuilder accessorBuilder = builder.create();
            accessorBuilder.string(name);

            if (execution.isIndexed()) {
                accessorBuilder.string("[").string(String.valueOf(execution.getChildIndex())).string("]");
            }

            CodeTree accessor = accessorBuilder.build();

            if (createCast != null && execution.getChild().getCardinality().isOne()) {
                accessor = callMethod(null, createCast.getMethod(), accessor);
            }

            if (execution.isIndexed()) {
                CodeTreeBuilder nullCheck = builder.create();
                nullCheck.string(name).string(" != null && ").string(String.valueOf(execution.getChildIndex())).string(" < ").string(name).string(".length").string(" ? ");
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

        if (nextUsed) {
            baseSpecialization.addOptional(createCreateNext(generated));
        }
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
            TypeMirror type = parameter.getType();
            if (type != null && typeSystem.hasImplicitSourceTypes(type)) {
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
        clazz.add(createGetNext(clazz));
        clazz.add(createAcceptAndExecute());

        List<ExecutableTypeData> usedTypes = filterBaseExecutableTypes(node.getExecutableTypes(), getReachableSpecializations());
        for (ExecutableTypeData type : usedTypes) {
            clazz.add(createFastPathExecuteMethod(null, type, usedTypes));
        }

        for (NodeExecutionData execution : node.getChildExecutions()) {
            Collection<TypeMirror> specializedTypes = node.findSpecializedTypes(execution);
            specializedTypes.add(genericType);
            for (TypeMirror specializedType : specializedTypes) {
                if (isExecuteChildShared(execution, specializedType)) {
                    clazz.add(createExecuteChildMethod(execution, specializedType));
                }
            }
        }

        return clazz;
    }

    private Element createAcceptAndExecute() {

        TypeMirror[] parameters = new TypeMirror[node.getSignatureSize()];
        Arrays.fill(parameters, genericType);

        ExecutableTypeData executableElement = new ExecutableTypeData(genericType, "acceptAndExecute", context.getType(Frame.class), Arrays.asList(parameters));

        LocalContext currentLocals = LocalContext.load(this, node.getSignatureSize(), varArgsThreshold);
        CodeExecutableElement executable = createExecuteMethod(null, executableElement, currentLocals, false);

        executable.getModifiers().add(FINAL);
        CodeTreeBuilder builder = executable.createBuilder();

        CodeTree receiver = CodeTreeBuilder.singleString("this");

        builder.tree(createCallDelegateExecute(builder, receiver, currentLocals, executableElement, node.getGenericExecutableType(null)));

        return executable;
    }

    private boolean shouldImplementExecutableType(SpecializationData specialization, ExecutableTypeData executableType) {
        // always implement the root execute method. they are declared abstract in the base node.
        if (executableType.getDelegatedTo() == null) {
            return true;
        }

        if (!isSubtypeBoxed(context, specialization.getReturnType().getType(), executableType.getReturnType())) {
            return false;
        }

        // specializations with more parameters are just ignored
        if (executableType.getEvaluatedCount() > node.getSignatureSize()) {
            return false;
        }

        // the evaluated signature might be compatible to the specialization
        boolean specializationCompatible = true;
        for (int i = 0; i < executableType.getEvaluatedCount(); i++) {
            TypeMirror evaluatedType = executableType.getEvaluatedParameters().get(i);
            TypeMirror specializedType = specialization.findParameterOrDie(node.getChildExecutions().get(i)).getType();

            if (!isSubtypeBoxed(context, evaluatedType, specializedType) && !isSubtypeBoxed(context, specializedType, evaluatedType)) {
                specializationCompatible = false;
                break;
            }
        }
        if (!specializationCompatible) {
            return false;
        }

        // possibly trigger void optimization for a specialization if it is enabled
        if (isVoid(executableType.getReturnType())) {
            if (isTypeBoxingOptimized(options.voidBoxingOptimization(), specialization.getReturnType().getType())) {
                return true;
            }
        }

        // trigger type boxing elimination for unevaluated arguments
        for (int i = executableType.getEvaluatedCount(); i < node.getSignatureSize(); i++) {
            NodeExecutionData execution = node.getChildExecutions().get(i);
            TypeMirror specializedType = specialization.findParameterOrDie(execution).getType();
            if (isTypeBoxingOptimized(options.monomorphicTypeBoxingOptimization(), specializedType)) {
                // it does not make sense to do type boxing elimination for children with
                // no type specialized execute method
                if (execution.getChild() != null) {
                    ExecutableTypeData executedType = execution.getChild().findExecutableType(specializedType);
                    if (executedType != null) {
                        return true;
                    }
                }
            }
        }

        // trigger type boxing elimination for return types
        if (typeEquals(executableType.getReturnType(), specialization.getReturnType().getType())) {
            if (isTypeBoxingOptimized(options.monomorphicTypeBoxingOptimization(), executableType.getReturnType())) {
                return true;
            }
        }

        // trigger generation for evaluated assignable type matches other than generic
        for (int i = 0; i < executableType.getEvaluatedCount(); i++) {
            TypeMirror evaluatedType = executableType.getEvaluatedParameters().get(i);
            NodeExecutionData execution = node.getChildExecutions().get(i);
            TypeMirror specializedType = specialization.findParameterOrDie(execution).getType();

            if (isSubtypeBoxed(context, evaluatedType, specializedType) && !isObject(specializedType)) {
                return true;
            }
        }

        return false;
    }

    private List<ExecutableTypeData> filterBaseExecutableTypes(List<ExecutableTypeData> executableTypes, List<SpecializationData> specializations) {
        Set<ExecutableTypeData> usedTypes = new HashSet<>();
        type: for (ExecutableTypeData type : executableTypes) {
            for (SpecializationData specialization : specializations) {
                if (shouldImplementExecutableType(specialization, type) || type.isAbstract() || !(type.hasUnexpectedValue(context) && type.getMethod() != null)) {
                    usedTypes.add(type);
                    continue type;
                }
            }
        }
        Set<ExecutableTypeData> delegatesToAdd = new HashSet<>();
        do {
            delegatesToAdd.clear();
            for (ExecutableTypeData type : usedTypes) {
                ExecutableTypeData delegate = type.getDelegatedTo();
                if (delegate != null && !usedTypes.contains(delegate)) {
                    delegatesToAdd.add(delegate);
                }
            }
            usedTypes.addAll(delegatesToAdd);
        } while (!delegatesToAdd.isEmpty());
        List<ExecutableTypeData> newUsedTypes = new ArrayList<>(usedTypes);
        Collections.sort(newUsedTypes);
        return newUsedTypes;
    }

    private CodeTypeElement createSpecialization(SpecializationData specialization, TypeMirror baseType) {
        CodeTypeElement clazz = createClass(node, specialization, modifiers(PRIVATE, STATIC, FINAL), specializationTypeName(specialization), baseType);

        CodeExecutableElement constructor = clazz.addOptional(createSpecializationConstructor(clazz, specialization, null));

        for (Parameter p : specialization.getSignatureParameters()) {
            TypeMirror targetType = p.getType();
            if (typeSystem.hasImplicitSourceTypes(targetType)) {
                NodeExecutionData execution = p.getSpecification().getExecution();
                CodeVariableElement implicitProfile = createImplicitProfileParameter(execution, p.getType());
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
        clazz.addOptional(createIsIdenticalMethod(specialization));

        // get types that should get implemented
        List<ExecutableTypeData> types = new ArrayList<>();
        for (ExecutableTypeData type : node.getExecutableTypes()) {
            if (shouldImplementExecutableType(specialization, type)) {
                types.add(type);
            }
        }
        for (ExecutableTypeData type : types) {
            clazz.add(createFastPathExecuteMethod(specialization, type, types));
        }

        return clazz;
    }

    public static List<Parameter> getDynamicParameters(TemplateMethod method) {
        List<Parameter> parameters = new ArrayList<>();
        for (Parameter param : method.getReturnTypeAndParameters()) {
            if (param.getSpecification().isLocal()) {
                // ignore parameters passed by locals
                continue;
            } else if (param.getVariableElement() != null && param.getVariableElement().getAnnotation(Cached.class) != null) {
                // ignore cached parameters
                continue;
            }
            parameters.add(param);
        }
        return parameters;
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

    private Element createIsIdenticalMethod(SpecializationData specialization) {
        boolean cacheBoundGuard = specialization.hasMultipleInstances();
        if (!cacheBoundGuard) {
            return null;
        }

        LocalContext currentLocals = LocalContext.load(this, node.getSignatureSize(), varArgsThreshold);
        currentLocals.loadFastPathState(specialization);

        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), getType(boolean.class), "isIdentical");
        method.addParameter(new CodeVariableElement(getType(SpecializationNode.class), "other"));
        currentLocals.addParametersTo(method, FRAME_VALUE);
        method.getAnnotationMirrors().add(new CodeAnnotationMirror(context.getDeclaredType(Override.class)));
        final CodeTreeBuilder builder = method.createBuilder();

        SpecializationGroup group = SpecializationGroup.create(specialization);
        SpecializationBody executionFactory = new SpecializationBody(true, false) {
            @Override
            public CodeTree createBody(SpecializationData s, LocalContext values) {
                return builder.create().returnTrue().build();
            }
        };

        builder.tree(createGuardAndCast(group, genericType, currentLocals, executionFactory));
        builder.returnFalse();
        return method;
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
            CodeVariableElement var = createImplicitProfileParameter(execution, parameter.getType());
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
        LocalContext currentLocals = LocalContext.load(this, node.getSignatureSize(), varArgsThreshold);

        CodeExecutableElement executable = new CodeExecutableElement(modifiers(PUBLIC), specializationNodeType, "merge");
        executable.addParameter(new CodeVariableElement(specializationNodeType, "newNode"));
        currentLocals.addParametersTo(executable, FRAME_VALUE);
        executable.getAnnotationMirrors().add(new CodeAnnotationMirror(context.getDeclaredType(Override.class)));
        CodeTreeBuilder builder = executable.createBuilder();

        if (specialization.isPolymorphic()) {
            builder.startReturn();
            builder.startCall("polymorphicMerge");
            builder.string("newNode");
            builder.startCall("super", "merge");
            builder.string("newNode");
            currentLocals.addReferencesTo(builder, FRAME_VALUE);
            builder.end();
            builder.end();
            builder.end();

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
            builder.startReturn();
            builder.startCall("super", "merge");
            builder.string("newNode");
            currentLocals.addReferencesTo(builder, FRAME_VALUE);
            builder.end();
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
        CodeTree execution = createGuardAndCast(group, genericType, locals, new SpecializationBody(false, false) {
            @Override
            public CodeTree createBody(SpecializationData specialization, LocalContext values) {
                CodeTypeElement generatedType = specializationClasses.get(specialization);
                if (generatedType == null) {
                    throw new AssertionError("No generated type for " + specialization);
                }
                return createSlowPathExecute(specialization, values);
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

    private Element createUnsupported() {
        SpecializationData fallback = node.getGenericSpecialization();
        if (fallback == null || optimizeFallback(fallback) || fallback.getMethod() == null) {
            return null;
        }
        LocalContext locals = LocalContext.load(this);

        CodeExecutableElement method = locals.createMethod(modifiers(PROTECTED, FINAL), genericType, "unsupported", FRAME_VALUE);
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
            TypeMirror type = parameter.getType();
            if (type != null && typeSystem.hasImplicitSourceTypes(type)) {
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

    private CodeExecutableElement createExecutableTypeOverride(List<ExecutableTypeData> usedExecutables, ExecutableTypeData execType) {
        LocalContext locals = LocalContext.load(this, execType.getEvaluatedCount(), Integer.MAX_VALUE);
        CodeExecutableElement method = createExecuteMethod(null, execType, locals, true);

        CodeTreeBuilder builder = method.createBuilder();
        if (singleSpecializable) {
            SpecializationData specialization = getReachableSpecializations().iterator().next();
            builder.tree(createFastPath(builder, specialization, execType, usedExecutables, locals));
        } else {
            // create acceptAndExecute
            ExecutableTypeData delegate = execType;
            CodeTree receiver = CodeTreeBuilder.singleString(specializationStartFieldName());
            builder.tree(createCallDelegateExecute(builder, receiver, locals, execType, delegate));
        }
        return method;
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

    private static List<ExecutableTypeData> resolveSpecializedExecutables(NodeExecutionData execution, Collection<TypeMirror> types, TypeBoxingOptimization optimization) {
        if (optimization == TypeBoxingOptimization.NONE) {
            return Collections.emptyList();
        } else if (types.isEmpty()) {
            return Collections.emptyList();
        }

        List<ExecutableTypeData> executables = new ArrayList<>();
        for (TypeMirror type : types) {
            if (!isTypeBoxingOptimized(optimization, type)) {
                continue;
            }
            if (execution.getChild() == null) {
                continue;
            }
            ExecutableTypeData foundType = execution.getChild().getNodeData().findExecutableType(type, execution.getChild().getExecuteWith().size());
            if (foundType != null) {
                executables.add(foundType);
            }
        }
        return executables;
    }

    private static CodeTree callMethod(CodeTree receiver, ExecutableElement method, CodeTree... boundValues) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        if (method.getModifiers().contains(STATIC)) {
            builder.startStaticCall(method.getEnclosingElement().asType(), method.getSimpleName().toString());
        } else {
            builder.startCall(receiver, method.getSimpleName().toString());
        }
        int index = -1;
        for (VariableElement parameter : method.getParameters()) {
            index++;
            if (index < boundValues.length) {
                CodeTree tree = boundValues[index];
                if (tree != null) {
                    builder.tree(tree);
                    continue;
                }
            }

            builder.defaultValue(parameter.asType());
        }
        builder.end();
        return builder.build();
    }

    private CodeTree[] bindExecuteMethodParameters(NodeExecutionData execution, ExecutableTypeData method, LocalContext currentValues) {
        List<NodeExecutionData> executeWith = execution != null ? execution.getChild().getExecuteWith() : null;

        List<CodeTree> values = new ArrayList<>();
        if (method.getFrameParameter() != null) {
            LocalVariable frameLocal = currentValues.get(FRAME_VALUE);
            if (frameLocal == null) {
                values.add(CodeTreeBuilder.singleString("null"));
            } else {
                values.add(createTypeSafeReference(frameLocal, method.getFrameParameter()));
            }
        }
        for (int parameterIndex = 0; parameterIndex < method.getEvaluatedCount(); parameterIndex++) {
            TypeMirror targetParameter = method.getEvaluatedParameters().get(parameterIndex);
            LocalVariable variable;
            if (executeWith != null && parameterIndex < executeWith.size()) {
                variable = currentValues.getValue(executeWith.get(parameterIndex));
            } else {
                variable = currentValues.getValue(parameterIndex);
            }
            values.add(createTypeSafeReference(variable, targetParameter));
        }

        return values.toArray(new CodeTree[values.size()]);
    }

    private CodeTree callExecuteMethod(NodeExecutionData execution, ExecutableTypeData method, LocalContext currentValues) {
        CodeTree receiver = execution != null ? accessParent(nodeFieldName(execution)) : null;
        return callMethod(receiver, method.getMethod(), bindExecuteMethodParameters(execution, method, currentValues));
    }

    private CodeTree callTemplateMethod(CodeTree receiver, TemplateMethod method, LocalContext currentValues) {
        CodeTree[] bindings = new CodeTree[method.getParameters().size()];

        int signatureIndex = 0;
        for (int i = 0; i < bindings.length; i++) {
            Parameter parameter = method.getParameters().get(i);

            LocalVariable var = currentValues.get(parameter, signatureIndex);
            if (var == null) {
                var = currentValues.get(parameter.getLocalName());
            }

            if (var != null) {
                bindings[i] = createTypeSafeReference(var, parameter.getType());
            }

            if (parameter.getSpecification().isSignature()) {
                signatureIndex++;
            }
        }
        return callMethod(receiver, method.getMethod(), bindings);
    }

    private CodeTree createTypeSafeReference(LocalVariable var, TypeMirror targetType) {
        CodeTree valueReference = var.createReference();
        TypeMirror sourceType = var.getTypeMirror();
        if (targetType == null || sourceType == null) {
            return valueReference;
        }
        if (needsCastTo(sourceType, targetType)) {
            valueReference = TypeSystemCodeGenerator.cast(typeSystem, targetType, valueReference);
        }
        return valueReference;
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
                currentValues.set(name, new LocalVariable(type, varName, null, null));
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

    private boolean hasFallthrough(SpecializationGroup group, TypeMirror forType, LocalContext currentValues, boolean fastPath, List<GuardExpression> ignoreGuards) {
        for (TypeGuard guard : group.getTypeGuards()) {
            if (currentValues.getValue(guard.getSignatureIndex()) == null) {
                // not evaluated
                return true;
            }
            LocalVariable value = currentValues.getValue(guard.getSignatureIndex());
            if (needsCastTo(value.getTypeMirror(), guard.getType())) {
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

    private static Element createGetNext(CodeTypeElement type) {
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PROTECTED, FINAL), type.asType(), "getNext");
        CodeTreeBuilder builder = method.createBuilder();
        builder.startReturn().cast(type.asType(), CodeTreeBuilder.singleString("this.next")).end();
        return method;
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
            if (execution.getChild() == null) {
                builder.nullLiteral();
            } else {
                builder.tree(accessParent(nodeFieldName(execution)));
            }
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
                CodeVariableElement var = createImplicitProfileParameter(p.getSpecification().getExecution(), p.getType());
                if (var != null) {
                    LocalVariable variable = currentValues.get(p.getLocalName());
                    if (variable == null) {
                        throw new AssertionError("Could not bind cached value " + p.getLocalName() + ": " + currentValues);
                    }
                    builder.tree(variable.original().createReference());
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

                CodeVariableElement implicitProfile = createImplicitProfileParameter(execution, p.getType());
                if (implicitProfile != null) {
                    LocalVariable var = LocalVariable.fromParameter(p).makeGeneric(context);

                    String implicitFieldName = implicitProfile.getName();
                    if (options.implicitCastOptimization().isDuplicateTail()) {
                        constructor.addParameter(var.createParameter());
                        CodeTree implicitType = TypeSystemCodeGenerator.implicitType(typeSystem, p.getType(), var.createReference());
                        builder.startStatement().string("this.").string(implicitFieldName).string(" = ").tree(implicitType).end();
                    } else if (options.implicitCastOptimization().isMergeCasts()) {
                        // use node that supports polymorphism
                        constructor.addParameter(var.createParameter());
                        builder.startStatement().string("this.").string(implicitFieldName).string(" = ").tree(ImplicitCastNodeFactory.create(typeSystem, p.getType(), var.createReference())).end();
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

    private CodeTree createCallNext(CodeTreeBuilder parent, ExecutableTypeData currentType, ExecutableTypeData callType, LocalContext currentValues) {
        if (singleSpecializable) {
            return createThrowUnsupported(currentValues);
        }
        CodeTreeBuilder callBuilder = parent.create();
        callBuilder.tree(createCallDelegateExecute(callBuilder, CodeTreeBuilder.singleString("getNext()"), currentValues, currentType, callType));
        nextUsed = true;
        return callBuilder.build();
    }

    private CodeTree createCallRemove(String reason, ExecutableTypeData forType, LocalContext currentValues) {
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
        builder.tree(expectOrCast(genericType, forType, call));
        builder.end();
        return builder.build();
    }

    private CodeTree createCallDelegate(String methodName, String reason, ExecutableTypeData forType, LocalContext currentValues) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.startCall(methodName);
        if (reason != null) {
            builder.doubleQuote(reason);
        }
        currentValues.addReferencesTo(builder, FRAME_VALUE);
        builder.end();

        CodeTree expectOrCast = expectOrCast(genericType, forType, builder.build());
        return expectOrCast;
    }

    private CodeTree expectOrCast(TypeMirror sourceType, ExecutableTypeData targetType, CodeTree content) {
        if (targetType.hasUnexpectedValue(context)) {
            return expect(sourceType, targetType.getReturnType(), content);
        } else {
            return cast(sourceType, targetType.getReturnType(), content);
        }
    }

    private CodeTree cast(TypeMirror sourceType, TypeMirror targetType, CodeTree content) {
        if (ElementUtils.needsCastTo(sourceType, targetType) && !isVoid(sourceType)) {
            return TypeSystemCodeGenerator.cast(typeSystem, targetType, content);
        } else {
            return content;
        }
    }

    private CodeTree expect(TypeMirror sourceType, TypeMirror forType, CodeTree tree) {
        expectedTypes.add(forType);
        return TypeSystemCodeGenerator.expect(typeSystem, sourceType, forType, tree);
    }

    private Set<ExecutableTypeData> findSpecializedExecutableTypes(NodeExecutionData execution, TypeMirror type) {
        if (execution.getChild() == null) {
            return Collections.emptySet();
        }
        ExecutableTypeData executableType = resolveExecutableType(execution.getChild(), type);
        Set<ExecutableTypeData> executedTypes = new HashSet<>();
        executedTypes.add(executableType);
        if (typeSystem.hasImplicitSourceTypes(type)) {
            executedTypes.addAll(resolveSpecializedExecutables(execution, typeSystem.lookupSourceTypes(type), options.implicitTypeBoxingOptimization()));
        }
        return executedTypes;
    }

    private ExecutableTypeData resolveExecutableType(NodeChildData child, TypeMirror type) {
        int executeWithCount = child.getExecuteWith().size();
        ExecutableTypeData executableType = child.getNodeData().findExecutableType(type, executeWithCount);
        if (executableType == null) {
            executableType = child.getNodeData().findAnyGenericExecutableType(context, executeWithCount);
        }
        return executableType;
    }

    private boolean hasUnexpectedResult(NodeExecutionData execution, TypeMirror type) {
        for (ExecutableTypeData executableType : findSpecializedExecutableTypes(execution, type)) {
            if (executableType != null && (executableType.hasUnexpectedValue(context) || needsCastTo(executableType.getReturnType(), type))) {
                return true;
            }
        }
        return false;
    }

    private Element createFastPathExecuteMethod(SpecializationData specialization, ExecutableTypeData executedType, List<ExecutableTypeData> allTypes) {
        LocalContext currentLocals = LocalContext.load(this, executedType.getEvaluatedCount(), varArgsThreshold);
        CodeExecutableElement executable = createExecuteMethod(specialization, executedType, currentLocals, false);
        CodeTreeBuilder builder = executable.createBuilder();
        if (specialization == null) {
            if (executedType.getDelegatedTo() == null) {
                executable.getModifiers().add(ABSTRACT);
            }
        } else {
            executable.getAnnotationMirrors().add(new CodeAnnotationMirror(context.getDeclaredType(Override.class)));
        }
        builder.tree(createFastPath(builder, specialization, executedType, allTypes, currentLocals));

        return executable;
    }

    private CodeExecutableElement createExecuteMethod(SpecializationData specialization, ExecutableTypeData executedType, LocalContext currentLocals, boolean originalOverride) {
        TypeMirror returnType = executedType.getReturnType();
        TypeMirror frame = executedType.getFrameParameter();
        List<TypeMirror> evaluatedParameters = executedType.getEvaluatedParameters();

        if (specialization != null) {
            currentLocals.loadFastPathState(specialization);
        }

        if (frame == null) {
            currentLocals.removeValue(FRAME_VALUE);
        } else {
            currentLocals.set(FRAME_VALUE, currentLocals.get(FRAME_VALUE).newType(frame));
        }

        for (int i = 0; i < Math.min(node.getChildExecutions().size(), evaluatedParameters.size()); i++) {
            NodeExecutionData execution = node.getChildExecutions().get(i);
            currentLocals.setValue(execution, currentLocals.getValue(execution).newType(evaluatedParameters.get(i)));
        }

        String methodName;
        if (originalOverride) {
            methodName = executedType.getMethod().getSimpleName().toString();
        } else {
            methodName = executedType.getUniqueName();
        }

        CodeExecutableElement executable;
        if (originalOverride && executedType.getMethod() != null) {
            executable = CodeExecutableElement.clone(context.getEnvironment(), executedType.getMethod());
            executable.getAnnotationMirrors().clear();
            executable.getModifiers().remove(ABSTRACT);
            for (VariableElement var : executable.getParameters()) {
                ((CodeVariableElement) var).getAnnotationMirrors().clear();
            }
            if (executedType.getFrameParameter() != null) {
                ((CodeVariableElement) executable.getParameters().get(0)).setName(FRAME_VALUE);
            }

            final String varArgsName = "args";
            if (executable.isVarArgs()) {
                ((CodeVariableElement) executable.getParameters().get(executable.getParameters().size() - 1)).setName(varArgsName);
            }

            // rename varargs parameter
            int signatureIndex = 0;
            for (TypeMirror parameter : executedType.getEvaluatedParameters()) {
                LocalVariable var = currentLocals.getValue(signatureIndex);
                if (var != null) {
                    int varArgsIndex = executedType.getVarArgsIndex(executedType.getParameterIndex(signatureIndex));
                    if (varArgsIndex >= 0) {
                        var = var.accessWith(CodeTreeBuilder.singleString(varArgsName + "[" + varArgsIndex + "]"));
                    } else {
                        ((CodeVariableElement) executable.getParameters().get(executedType.getParameterIndex(signatureIndex))).setName(var.getName());
                    }
                    if (!isObject(parameter)) {
                        var = var.newType(parameter);
                    }
                    currentLocals.setValue(node.getChildExecutions().get(signatureIndex), var);
                }

                signatureIndex++;
            }
        } else {
            executable = currentLocals.createMethod(modifiers(PUBLIC), returnType, methodName, FRAME_VALUE);
            if (executedType.hasUnexpectedValue(context)) {
                executable.getThrownTypes().add(context.getDeclaredType(UnexpectedResultException.class));
            }
        }

        return executable;
    }

    private CodeTree createFastPath(CodeTreeBuilder parent, SpecializationData specialization, final ExecutableTypeData executableType, List<ExecutableTypeData> allTypes, LocalContext currentLocals) {
        final CodeTreeBuilder builder = parent.create();
        TypeMirror returnType = executableType.getReturnType();

        ExecutableTypeData delegate = null;
        if (specialization == null) {
            delegate = executableType.getDelegatedTo();
        }

        if (delegate == null) {
            delegate = findFastPathDelegate((specialization != null ? specialization.getReturnType().getType() : genericType), executableType, allTypes);
        }

        for (NodeExecutionData execution : node.getChildExecutions()) {
            if (specialization == null && delegate != null && execution.getIndex() >= delegate.getEvaluatedCount()) {
                // we just evaluate children for the next delegate
                continue;
            } else if (specialization != null && delegate != null) {
                // skip if already delegated
                break;
            }

            LocalVariable var = currentLocals.getValue(execution);
            if (var == null) {
                TypeMirror targetType;
                if (specialization == null) {
                    List<TypeMirror> genericTypes = node.getGenericTypes(execution);
                    if (genericTypes.isEmpty()) {
                        targetType = genericType;
                    } else {
                        targetType = genericTypes.get(0);
                    }
                } else {
                    targetType = specialization.findParameterOrDie(execution).getType();
                }
                LocalVariable shortCircuit = resolveShortCircuit(specialization, execution, currentLocals);
                var = currentLocals.createValue(execution, targetType).nextName();
                builder.tree(createAssignExecuteChild(builder, execution, executableType, var, shortCircuit, currentLocals));
                currentLocals.setValue(execution, var);

            }
        }

        LocalContext originalValues = currentLocals.copy();
        if (delegate != null) {
            builder.tree(createCallDelegateExecute(builder, null, currentLocals, executableType, delegate));
        } else if (specialization == null) {
            // nothing to do. abstract anyway
        } else if (specialization.isPolymorphic()) {
            builder.tree(createCallNext(builder, executableType, node.getGenericExecutableType(executableType), currentLocals));
        } else if (specialization.isUninitialized()) {
            builder.startReturn().tree(createCallDelegate("uninitialized", null, executableType, currentLocals)).end();
        } else {
            SpecializationGroup group = SpecializationGroup.create(specialization);
            SpecializationBody executionFactory = new SpecializationBody(true, true) {
                @Override
                public CodeTree createBody(SpecializationData s, LocalContext values) {
                    return createFastPathExecute(builder, executableType, s, values);
                }
            };
            builder.tree(createGuardAndCast(group, returnType, currentLocals, executionFactory));
            if (hasFallthrough(group, returnType, originalValues, true, null) || group.getSpecialization().isFallback()) {
                builder.tree(createCallNext(builder, executableType, executableType, originalValues));
            }
        }
        return builder.build();
    }

    private CodeTree createCallDelegateExecute(final CodeTreeBuilder parent, CodeTree receiver, LocalContext currentLocals, ExecutableTypeData source, ExecutableTypeData delegate) {
        CodeTreeBuilder callBuilder = parent.create();

        if (singleSpecializable) {
            callBuilder.startCall(receiver, delegate.getMethod().getSimpleName().toString());
        } else {
            callBuilder.startCall(receiver, delegate.getUniqueName());
        }
        callBuilder.trees(bindExecuteMethodParameters(null, delegate, currentLocals));
        callBuilder.end();
        CodeTree call = expectOrCast(delegate.getReturnType(), source, callBuilder.build());

        CodeTreeBuilder returnBuilder = parent.create();
        if (isVoid(source.getReturnType())) {
            returnBuilder.statement(call);
            returnBuilder.returnStatement();
        } else if (isVoid(delegate.getReturnType())) {
            returnBuilder.statement(call);
            returnBuilder.returnDefault();
        } else {
            returnBuilder.startReturn().tree(call).end();
        }

        CodeTreeBuilder builder = parent.create();

        if (!source.hasUnexpectedValue(context) && delegate.hasUnexpectedValue(context)) {
            builder.startTryBlock();
            builder.tree(returnBuilder.build());
            builder.end().startCatchBlock(context.getType(UnexpectedResultException.class), "ex");
            if (!isVoid(source.getReturnType())) {
                builder.startReturn().tree(cast(context.getType(Object.class), source.getReturnType(), CodeTreeBuilder.singleString("ex.getResult()"))).end();
            }
            builder.end();
        } else {
            builder.tree(returnBuilder.build());
        }
        return builder.build();
    }

    private ExecutableTypeData findFastPathDelegate(TypeMirror targetType, ExecutableTypeData executableType, List<ExecutableTypeData> allTypes) {
        if (typeEquals(executableType.getReturnType(), targetType)) {
            // type matches look for even better delegates
            for (ExecutableTypeData type : allTypes) {
                if (typeEquals(type.getReturnType(), targetType) && executableType.sameParameters(type)) {
                    if (type != executableType) {
                        return type;
                    }
                }
            }
            return null;
        } else {
            for (ExecutableTypeData type : allTypes) {
                if (typeEquals(type.getReturnType(), targetType) && executableType.sameParameters(type)) {
                    return type;
                }
            }
            int executableIndex = allTypes.indexOf(executableType);
            int compareIndex = 0;
            for (ExecutableTypeData type : allTypes) {
                if (executableIndex != compareIndex && executableType.sameParameters(type)) {
                    int result = ExecutableTypeData.compareType(context, type.getReturnType(), executableType.getReturnType());
                    if (result < 0) {
                        return type;
                    } else if (result == 0 && executableIndex < compareIndex) {
                        return type;
                    }
                }
                compareIndex++;
            }
            return null;
        }
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

    private CodeTree createFastPathExecute(CodeTreeBuilder parent, final ExecutableTypeData forType, SpecializationData specialization, LocalContext currentValues) {
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

        if (specialization.getMethod() == null) {
            execute.startReturn();
            execute.startCall("unsupported");
            currentValues.addReferencesTo(execute, FRAME_VALUE);
            execute.end();
            execute.end();
        } else {
            boolean doReturn = !isVoid(specialization.getMethod().getReturnType());
            if (doReturn) {
                execute.startReturn();
            } else {
                execute.startStatement();
            }
            execute.tree(callTemplateMethod(accessParent(null), specialization, currentValues));
            execute.end();
            if (!doReturn) {
                if (isVoid(forType.getReturnType())) {
                    execute.returnStatement();
                } else {
                    execute.startReturn();
                    execute.defaultValue(forType.getReturnType());
                    execute.end();
                }
            }
        }
        builder.tree(createFastPathTryCatchRewriteException(specialization, forType, currentValues, execute.build()));
        builder.end(ifCount);
        return builder.build();
    }

    private CodeTree createGuardAndCast(SpecializationGroup group, TypeMirror forType, LocalContext currentValues, SpecializationBody execution) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();

        Set<TypeGuard> castGuards;
        if (execution.needsCastedValues()) {
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
                builder.tree(execution.createBody(specialization, currentValues));
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

    private CodeExecutableElement createExecuteChildMethod(NodeExecutionData execution, TypeMirror targetType) {
        LocalContext locals = LocalContext.load(this, 0, varArgsThreshold);

        CodeExecutableElement method = locals.createMethod(modifiers(PROTECTED, FINAL), targetType, executeChildMethodName(execution, targetType), FRAME_VALUE);
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

    private CodeVariableElement createImplicitProfileParameter(NodeExecutionData execution, TypeMirror targetType) {
        if (typeSystem.hasImplicitSourceTypes(targetType)) {
            switch (options.implicitCastOptimization()) {
                case NONE:
                    return null;
                case DUPLICATE_TAIL:
                    return new CodeVariableElement(getType(Class.class), implicitClassFieldName(execution));
                case MERGE_CASTS:
                    return new CodeVariableElement(ImplicitCastNodeFactory.type(typeSystem, targetType), implicitNodeFieldName(execution));
            }
        }
        return null;
    }

    private boolean isExecuteChildShared(NodeExecutionData execution, TypeMirror targetType) {
        if (isVoid(targetType)) {
            return false;
        } else if (isObject(targetType)) {
            return resolvePolymorphicExecutables(execution).size() >= 1;
        } else {
            if (!isTypeBoxingOptimized(options.monomorphicTypeBoxingOptimization(), targetType)) {
                return false;
            }
            if (!typeSystem.hasImplicitSourceTypes(targetType)) {
                return false;
            }

            int uses = 0;
            for (SpecializationData specialization : node.getSpecializations()) {
                List<Parameter> parameters = specialization.findByExecutionData(execution);
                for (Parameter parameter : parameters) {
                    if (targetType.equals(parameter.getType())) {
                        uses++;
                    }
                }
            }
            if (uses > 1) {
                return resolveSpecializedExecutables(execution, typeSystem.lookupSourceTypes(targetType), options.implicitTypeBoxingOptimization()).size() > 1;
            } else {
                return false;
            }
        }
    }

    private CodeTree createAssignExecuteChild(CodeTreeBuilder parent, NodeExecutionData execution, ExecutableTypeData type, LocalVariable targetValue, LocalVariable shortCircuit,
                    LocalContext currentValues) {
        CodeTreeBuilder builder = parent.create();
        boolean hasUnexpected = hasUnexpectedResult(execution, targetValue.getTypeMirror());

        CodeTree executeChild;
        if (isExecuteChildShared(execution, targetValue.getTypeMirror())) {
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
            slowPathValues.setValue(execution, targetValue.makeGeneric(context).accessWith(CodeTreeBuilder.singleString("ex.getResult()")));

            ExecutableTypeData delegateType = node.getGenericExecutableType(type);
            boolean found = false;
            for (NodeExecutionData otherExecution : node.getChildExecutions()) {
                if (found) {
                    LocalVariable childEvaluatedValue = slowPathValues.createValue(otherExecution, genericType);
                    LocalVariable genericShortCircuit = resolveShortCircuit(null, otherExecution, slowPathValues);
                    builder.tree(createAssignExecuteChild(builder, otherExecution, delegateType, childEvaluatedValue, genericShortCircuit, slowPathValues));
                    slowPathValues.setValue(otherExecution, childEvaluatedValue);
                } else {
                    // skip forward already evaluated
                    found = execution == otherExecution;
                }
            }
            builder.tree(createCallNext(builder, type, delegateType, slowPathValues));
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
        if (!isExecuteChildShared(execution, targetValue.getTypeMirror())) {
            throw new AssertionError("Execute child not shared with method but called.");
        }

        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.tree(targetValue.createReference()).string(" = ");
        builder.startCall(executeChildMethodName(execution, targetValue.getTypeMirror()));
        builder.string(FRAME_VALUE);

        CodeVariableElement implicitProfile = createImplicitProfileParameter(execution, targetValue.getTypeMirror());
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

        final Set<ExecutableTypeData> executableTypes = findSpecializedExecutableTypes(execution, target.getTypeMirror());
        if (executableTypes.isEmpty()) {
            throw new AssertionError(); // cannot execute child
        } else if (executableTypes.size() == 1 && !typeSystem.hasImplicitSourceTypes(target.getTypeMirror())) {
            ExecutableTypeData executableType = executableTypes.iterator().next();
            if (isObject(target.getTypeMirror()) && executableType.getEvaluatedCount() == 0) {
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
        CodeTree execute = callExecuteMethod(execution, executableType, currentValues);
        return expect(executableType.getReturnType(), target.getTypeMirror(), execute);
    }

    private CodeTree createPolymorphicExecuteChild(NodeExecutionData execution, LocalVariable target, LocalContext currentValues, boolean shared) throws AssertionError {
        ExecutableTypeData genericExecutableType = execution.getChild().getNodeData().findAnyGenericExecutableType(context, execution.getChild().getExecuteWith().size());
        if (genericExecutableType == null) {
            throw new AssertionError("At least one generic executable method must be available.");
        }

        List<ExecutableTypeData> specializedExecutables = resolvePolymorphicExecutables(execution);
        Collections.sort(specializedExecutables, new Comparator<ExecutableTypeData>() {
            public int compare(ExecutableTypeData o1, ExecutableTypeData o2) {
                return compareType(o1.getReturnType(), o2.getReturnType());
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
                polyChainBuilder.string(" == ").typeLiteral(executableType.getReturnType());
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
            polyChainBuilder.declaration(genericExecutableType.getReturnType(), valueFieldName, executeGeneric);

            hasSpecializedTypes = false;
            for (ExecutableTypeData executableType : specializedExecutables) {
                hasSpecializedTypes = polyChainBuilder.startIf(hasSpecializedTypes);
                polyChainBuilder.tree(TypeSystemCodeGenerator.check(typeSystem, executableType.getReturnType(), CodeTreeBuilder.singleString(valueFieldName)));
                polyChainBuilder.end();
                polyChainBuilder.startBlock();
                polyChainBuilder.startStatement().tree(accessParent(profileField)).string(" = ").typeLiteral(executableType.getReturnType()).end();
                polyChainBuilder.end();
            }

            polyChainBuilder.startElseBlock();
            polyChainBuilder.startStatement().tree(accessParent(profileField)).string(" = ").typeLiteral(genericType).end();
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
                builder.startStatement().tree(accessParent(profileField)).string(" = ").typeLiteral(genericType).end();
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
        Set<TypeMirror> specializedTypes = new HashSet<>();
        for (TypeMirror type : node.findSpecializedTypes(execution)) {
            specializedTypes.addAll(typeSystem.lookupSourceTypes(type));
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
        List<TypeMirror> sourceTypes = typeSystem.lookupSourceTypes(target.getTypeMirror());
        String implicitClassFieldName = implicitClassFieldName(execution);
        List<ExecutableTypeData> executableTypes = resolveSpecializedExecutables(execution, sourceTypes, options.implicitTypeBoxingOptimization());

        boolean elseIf = false;
        for (ExecutableTypeData executableType : executableTypes) {
            elseIf = builder.startIf(elseIf);
            builder.string(implicitClassFieldName).string(" == ").typeLiteral(executableType.getReturnType());
            builder.end();
            builder.startBlock();
            builder.startStatement().tree(assignment);

            CodeTree execute = callExecuteMethod(execution, executableType, currentValues);
            ImplicitCastData cast = typeSystem.lookupCast(executableType.getReturnType(), target.getTypeMirror());
            if (cast != null) {
                execute = callMethod(null, cast.getMethod(), execute);
            }
            builder.tree(execute);
            builder.end();
            builder.end();
        }

        if (!executableTypes.isEmpty()) {
            builder.startElseBlock();
        }

        LocalVariable genericValue = target.makeGeneric(context).nextName();
        builder.tree(createAssignExecuteChild(builder, execution, node.getGenericExecutableType(null), genericValue, null, currentValues));
        if (executableTypes.size() == sourceTypes.size()) {
            builder.startThrow().startNew(getType(UnexpectedResultException.class)).tree(genericValue.createReference()).end().end();
        } else {
            builder.startStatement().tree(assignment);
            builder.tree(TypeSystemCodeGenerator.implicitExpect(typeSystem, target.getTypeMirror(), genericValue.createReference(), implicitClassFieldName));
            builder.end();
        }

        if (!executableTypes.isEmpty()) {
            builder.end();
        }
        return builder.build();
    }

    private CodeTree createFastPathTryCatchRewriteException(SpecializationData specialization, ExecutableTypeData forType, LocalContext currentValues, CodeTree execution) {
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
                    SpecializationBody specializationExecution) {
        CodeTreeBuilder checksBuilder = CodeTreeBuilder.createBuilder();
        CodeTreeBuilder localsBuilder = CodeTreeBuilder.createBuilder();
        for (TypeGuard typeGuard : typeGuards) {
            int signatureIndex = typeGuard.getSignatureIndex();
            LocalVariable value = currentValues.getValue(signatureIndex);
            TypeMirror targetType = typeGuard.getType();
            if (!ElementUtils.needsCastTo(value.getTypeMirror(), targetType)) {
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
                if (!ElementUtils.isPrimitive(shortCircuit.getTypeMirror())) {
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
                checkBuilder.tree(TypeSystemCodeGenerator.check(typeSystem, targetType, value.createReference()));
                castBuilder.tree(TypeSystemCodeGenerator.cast(typeSystem, targetType, valueReference));
            } else {
                ImplicitCastOptimization opt = options.implicitCastOptimization();
                if (specializationExecution.isFastPath() && !opt.isNone()) {
                    if (opt.isDuplicateTail()) {
                        String typeHintField = implicitClassFieldName(execution);
                        checkBuilder.tree(TypeSystemCodeGenerator.implicitCheck(typeSystem, targetType, valueReference, typeHintField));
                        castBuilder.tree(TypeSystemCodeGenerator.implicitCast(typeSystem, targetType, valueReference, typeHintField));
                    } else if (opt.isMergeCasts()) {
                        checkBuilder.tree(ImplicitCastNodeFactory.check(implicitNodeFieldName(execution), valueReference));
                        castBuilder.tree(ImplicitCastNodeFactory.cast(implicitNodeFieldName(execution), valueReference));
                    } else {
                        throw new AssertionError("implicit cast opt");
                    }
                } else {
                    checkBuilder.tree(TypeSystemCodeGenerator.implicitCheck(typeSystem, targetType, valueReference, null));
                    castBuilder.tree(TypeSystemCodeGenerator.implicitCast(typeSystem, targetType, valueReference, null));
                }
            }

            if (shortCircuit != null) {
                checkBuilder.string(")");
                castBuilder.string(" : ").defaultValue(targetType);
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
        currentValues.set(name, new LocalVariable(type, varName, null, null));
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
                set(cacheParameter.getLocalName(), new LocalVariable(cacheParameter.getType(), name, CodeTreeBuilder.singleString("this." + name), null));
            }

            for (AssumptionExpression assumption : specialization.getAssumptionExpressions()) {
                String name = assumptionName(assumption);
                TypeMirror type = assumption.getExpression().getResolvedType();
                set(name, new LocalVariable(type, name, CodeTreeBuilder.singleString("this." + name), null));
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
        public LocalVariable createValue(NodeExecutionData execution, TypeMirror type) {
            return new LocalVariable(type, valueName(execution), null, null);
        }

        public LocalVariable createShortCircuitValue(NodeExecutionData execution) {
            return new LocalVariable(factory.getType(boolean.class), shortCircuitName(execution), null, null);
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
            List<NodeExecutionData> childExecutions = factory.node.getChildExecutions();
            if (signatureIndex < childExecutions.size()) {
                return getValue(childExecutions.get(signatureIndex));
            } else {
                return null;
            }
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
            values.put(FRAME_VALUE, new LocalVariable(factory.getType(Frame.class), FRAME_VALUE, null, null));

            for (NodeFieldData field : factory.node.getFields()) {
                String fieldName = fieldValueName(field);
                values.put(fieldName, new LocalVariable(field.getType(), fieldName, factory.accessParent(field.getName()), null));
            }

            boolean varargs = needsVarargs(false, varargsThreshold);
            for (int i = 0; i < evaluatedArguments; i++) {
                List<NodeExecutionData> childExecutions = factory.node.getChildExecutions();
                if (i >= childExecutions.size()) {
                    break;
                }
                NodeExecutionData execution = childExecutions.get(i);
                if (execution.isShortCircuit()) {
                    LocalVariable shortCircuit = createShortCircuitValue(execution).makeGeneric(factory.context);
                    if (varargs) {
                        shortCircuit = shortCircuit.accessWith(createReadVarargs(i));
                    }
                    values.put(shortCircuit.getName(), shortCircuit.makeOriginal());
                }
                LocalVariable value = createValue(execution, factory.genericType);
                if (varargs) {
                    value = value.accessWith(createReadVarargs(i));
                }
                values.put(value.getName(), value.makeOriginal());
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

        private final TypeMirror typeMirror;
        private final CodeTree accessorTree;
        private final String name;
        private final LocalVariable previous;

        public static LocalVariable fromParameter(Parameter parameter) {
            NodeExecutionData execution = parameter.getSpecification().getExecution();
            String name = null;
            if (execution == null) {
                name = parameter.getLocalName();
            } else {
                name = createName(execution);
            }
            return new LocalVariable(parameter.getType(), name, null, null);
        }

        private LocalVariable(TypeMirror typeMirror, String name, CodeTree accessorTree, LocalVariable previous) {
            Objects.requireNonNull(typeMirror);
            this.typeMirror = typeMirror;
            this.accessorTree = accessorTree;
            this.name = name;
            this.previous = previous;
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

        public LocalVariable newType(TypeMirror newType) {
            return new LocalVariable(newType, name, accessorTree, this);
        }

        public LocalVariable accessWith(CodeTree tree) {
            return new LocalVariable(typeMirror, name, tree, this);
        }

        public LocalVariable nextName() {
            return new LocalVariable(typeMirror, createNextName(name), accessorTree, this);
        }

        public LocalVariable makeOriginal() {
            return new LocalVariable(typeMirror, name, accessorTree, null);
        }

        public LocalVariable original() {
            LocalVariable variable = this;
            while (variable.previous != null) {
                variable = variable.previous;
            }
            return variable;
        }

        public LocalVariable makeGeneric(ProcessorContext context) {
            return newType(context.getType(Object.class));
        }

        @Override
        public String toString() {
            return "Local[type = " + getTypeMirror() + ", name = " + name + ", accessWith = " + accessorTree + "]";
        }

    }

    private abstract class SpecializationBody {

        private final boolean fastPath;
        private final boolean needsCastedValues;

        public SpecializationBody(boolean fastPath, boolean needsCastedValues) {
            this.fastPath = fastPath;
            this.needsCastedValues = needsCastedValues;
        }

        public final boolean isFastPath() {
            return fastPath;
        }

        public final boolean needsCastedValues() {
            return needsCastedValues;
        }

        public abstract CodeTree createBody(SpecializationData specialization, LocalContext currentValues);

    }

}
