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

import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.createTransferToInterpreterAndInvalidate;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.isObject;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.isSubtypeBoxed;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.isVoid;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.modifiers;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.needsCastTo;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.setVisibility;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Introspection;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInterface;
import com.oracle.truffle.api.nodes.SlowPathException;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.expression.DSLExpression;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Binary;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.BooleanLiteral;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Call;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.DSLExpressionVisitor;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.IntLiteral;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Negate;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Variable;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationValue;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.ArrayCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.DeclaredCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.model.AssumptionExpression;
import com.oracle.truffle.dsl.processor.model.CacheExpression;
import com.oracle.truffle.dsl.processor.model.CreateCastData;
import com.oracle.truffle.dsl.processor.model.ExecutableTypeData;
import com.oracle.truffle.dsl.processor.model.GuardExpression;
import com.oracle.truffle.dsl.processor.model.ImplicitCastData;
import com.oracle.truffle.dsl.processor.model.NodeChildData;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.model.NodeExecutionData;
import com.oracle.truffle.dsl.processor.model.NodeFieldData;
import com.oracle.truffle.dsl.processor.model.Parameter;
import com.oracle.truffle.dsl.processor.model.ShortCircuitData;
import com.oracle.truffle.dsl.processor.model.SpecializationData;
import com.oracle.truffle.dsl.processor.model.TemplateMethod;
import com.oracle.truffle.dsl.processor.model.TypeSystemData;
import com.oracle.truffle.dsl.processor.parser.SpecializationGroup;
import com.oracle.truffle.dsl.processor.parser.SpecializationGroup.TypeGuard;

@SuppressWarnings("deprecation")
public class FlatNodeGenFactory {

    private static final String METHOD_FALLBACK_GUARD = "fallbackGuard_";
    private static final String FRAME_VALUE = TemplateMethod.FRAME_NAME;
    private static final String STATE_VALUE = "state";

    private static final String NAME_SUFFIX = "_";

    private static final String VARARGS_NAME = "args";

    private final ProcessorContext context;
    private final NodeData node;
    private final TypeSystemData typeSystem;
    private final TypeMirror genericType;
    private final com.oracle.truffle.api.dsl.internal.DSLOptions options;
    private final Set<TypeMirror> expectedTypes = new HashSet<>();
    private List<SpecializationData> reachableSpecializations;

    private Map<String, TypeMirror> isValidSignatures = new HashMap<>();

    private final boolean boxingEliminationEnabled;
    private int boxingSplitIndex = 0;

    private final BitSet state;
    private final BitSet exclude;

    private final ExecutableTypeData executeAndSpecializeType;
    private boolean fallbackNeedsState = false;

    public FlatNodeGenFactory(ProcessorContext context, NodeData node) {
        this.context = context;
        this.node = node;
        this.typeSystem = node.getTypeSystem();
        this.genericType = context.getType(Object.class);
        this.options = typeSystem.getOptions();
        this.boxingEliminationEnabled = options.flatLayoutBoxingElimination();
        this.reachableSpecializations = calculateReachableSpecializations();

        List<Object> objects = new ArrayList<>();
        objects.add(node.getUninitializedSpecialization());
        Set<TypeGuard> implicitCasts = new LinkedHashSet<>();
        for (SpecializationData specialization : reachableSpecializations) {
            objects.add(specialization);

            int index = 0;
            for (Parameter p : specialization.getSignatureParameters()) {
                TypeMirror targetType = p.getType();
                List<TypeMirror> sourceTypes = typeSystem.lookupSourceTypes(targetType);
                if (sourceTypes.size() > 1) {
                    implicitCasts.add(new TypeGuard(targetType, index));
                }
                index++;
            }
        }
        objects.addAll(implicitCasts);
        this.state = new StateBitSet(objects);
        this.exclude = new ExcludeBitSet(reachableSpecializations);
        this.executeAndSpecializeType = createExecuteAndSpecializeType();
    }

    private static String createSpecializationTypeName(SpecializationData s) {
        return ElementUtils.firstLetterUpperCase(s.getId()) + "Data";
    }

    private static String createSpecializationFieldName(SpecializationData s) {
        return ElementUtils.firstLetterLowerCase(s.getId()) + "_cache";
    }

    private static String createFieldName(SpecializationData specialization, Parameter cacheParameter) {
        if (useSpecializationClass(specialization)) {
            return cacheParameter.getLocalName() + "_";
        } else {
            return ElementUtils.firstLetterLowerCase(specialization.getId()) + "_" + cacheParameter.getLocalName() + "_";
        }
    }

    private static String createAssumptionFieldName(SpecializationData specialization, AssumptionExpression assumption) {
        if (useSpecializationClass(specialization)) {
            return assumption.getId() + "_";
        } else {
            return ElementUtils.firstLetterLowerCase(specialization.getId()) + "_" + assumption.getId() + "_";
        }
    }

    private static String createSpecializationLocalName(SpecializationData s) {
        return "s" + s.getIndex() + "_";
    }

    private static String assumptionName(AssumptionExpression assumption) {
        return assumption.getId() + NAME_SUFFIX;
    }

    private static String nodeFieldName(NodeExecutionData execution) {
        return execution.getName() + NAME_SUFFIX;
    }

    /* Whether a new class should be generated for specialization instance fields. */
    private static boolean useSpecializationClass(SpecializationData specialization) {
        int size = 0;
        for (CacheExpression expression : specialization.getCaches()) {
            TypeMirror type = expression.getParameter().getType();
            if (ElementUtils.isPrimitive(type)) {
                switch (type.getKind()) {
                    case BOOLEAN:
                    case BYTE:
                        size++;
                        break;
                    case CHAR:
                    case SHORT:
                        size += 2;
                        break;
                    case INT:
                    case FLOAT:
                        size += 4;
                        break;
                    case LONG:
                    case DOUBLE:
                        size += 8;
                        break;
                }
            } else {
                size += 4;
            }
        }
        // if we exceed the size of two references we generate a class
        if (size > 8) {
            return true;
        }
        // we need a data class if we need to support multiple specialization instances
        return specialization.getMaximumNumberOfInstances() > 1;
    }

    private static boolean needsFrame(List<SpecializationData> specializations) {
        for (SpecializationData specialization : specializations) {
            if (specialization.isFrameUsed()) {
                return true;
            }
        }
        return false;
    }

    private static String createImplicitTypeStateLocalName(Parameter execution) {
        String name = ElementUtils.firstLetterLowerCase(ElementUtils.getTypeId(execution.getType()));
        return name + "Cast" + execution.getSpecification().getExecution().getIndex();
    }

    private static boolean mayBeExcluded(SpecializationData specialization) {
        return !specialization.getExceptions().isEmpty() || !specialization.getExcludedBy().isEmpty();
    }

    public CodeTypeElement create(CodeTypeElement clazz) {
        for (NodeChildData child : node.getChildren()) {
            clazz.addOptional(createAccessChildMethod(child));
        }

        for (NodeFieldData field : node.getFields()) {
            if (!field.isGenerated()) {
                continue;
            }

            clazz.add(new CodeVariableElement(modifiers(PRIVATE, FINAL), field.getType(), field.getName()));
            if (field.getGetter() != null && field.getGetter().getModifiers().contains(Modifier.ABSTRACT)) {
                CodeExecutableElement method = CodeExecutableElement.clone(context.getEnvironment(),
                                field.getGetter());
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
                clazz.add(createNodeField(PRIVATE, execution.getNodeType(), nodeFieldName(execution),
                                Child.class));
            }
        }

        createFields(clazz);

        TypeMirror genericReturnType = node.getPolymorphicSpecialization().getReturnType().getType();

        List<ExecutableTypeData> executableTypes = filterExecutableTypes(node.getExecutableTypes(),
                        reachableSpecializations);
        List<ExecutableTypeData> genericExecutableTypes = new ArrayList<>();
        List<ExecutableTypeData> specializedExecutableTypes = new ArrayList<>();
        List<ExecutableTypeData> voidExecutableTypes = new ArrayList<>();

        for (ExecutableTypeData type : executableTypes) {
            if (ElementUtils.isVoid(type.getReturnType())) {
                voidExecutableTypes.add(type);
            } else if (type.hasUnexpectedValue(context) && !ElementUtils.typeEquals(genericReturnType, type.getReturnType())) {
                specializedExecutableTypes.add(type);
            } else {
                genericExecutableTypes.add(type);
            }
        }

        if (genericExecutableTypes.size() > 1) {
            boolean hasGenericTypeMatch = false;
            for (ExecutableTypeData genericExecutable : genericExecutableTypes) {
                if (ElementUtils.typeEquals(genericExecutable.getReturnType(), genericReturnType)) {
                    hasGenericTypeMatch = true;
                    break;
                }
            }

            if (hasGenericTypeMatch) {
                for (ListIterator<ExecutableTypeData> iterator = genericExecutableTypes.listIterator(); iterator.hasNext();) {
                    ExecutableTypeData executableTypeData = iterator.next();
                    if (!ElementUtils.typeEquals(genericReturnType, executableTypeData.getReturnType())) {
                        iterator.remove();
                        specializedExecutableTypes.add(executableTypeData);
                    }
                }
            }
        }

        SpecializationData fallback = node.getGenericSpecialization();
        if (fallback.getMethod() != null && fallback.isReachable()) {
            clazz.add(createFallbackGuard(fallback));
        }

        for (ExecutableTypeData type : genericExecutableTypes) {
            createExecute(clazz, type, Collections.<ExecutableTypeData> emptyList());
        }

        for (ExecutableTypeData type : specializedExecutableTypes) {
            createExecute(clazz, type, genericExecutableTypes);
        }

        for (ExecutableTypeData type : voidExecutableTypes) {
            List<ExecutableTypeData> genericAndSpecialized = new ArrayList<>();
            genericAndSpecialized.addAll(genericExecutableTypes);
            genericAndSpecialized.addAll(specializedExecutableTypes);
            createExecute(clazz, type, genericAndSpecialized);
        }

        clazz.addOptional(createExecuteAndSpecialize());

        clazz.add(createGetCostMethod());

        for (TypeMirror type : ElementUtils.uniqueSortedTypes(expectedTypes, false)) {
            if (!typeSystem.hasType(type)) {
                clazz.addOptional(TypeSystemCodeGenerator.createExpectMethod(PRIVATE, typeSystem,
                                context.getType(Object.class), type));
            }
        }

        for (TypeMirror assumptionType : isValidSignatures.values()) {
            clazz.add(createIsValid(assumptionType));
        }

        clazz.getEnclosedElements().addAll(removeThisMethods.values());

        if (node.isReflectable()) {
            generateReflectionInfo(clazz);
        }

        return clazz;
    }

    private void generateReflectionInfo(CodeTypeElement clazz) {
        clazz.getImplements().add(context.getType(Introspection.Provider.class));
        CodeExecutableElement reflection = new CodeExecutableElement(modifiers(PUBLIC), context.getType(Introspection.class), "getIntrospectionData");

        CodeTreeBuilder builder = reflection.createBuilder();

        List<SpecializationData> filteredSpecializations = new ArrayList<>();
        for (SpecializationData s : node.getSpecializations()) {
            if (s.getMethod() == null) {
                continue;
            }
            filteredSpecializations.add(s);
        }

        ArrayCodeTypeMirror objectArray = new ArrayCodeTypeMirror(context.getType(Object.class));
        builder.declaration(objectArray, "data", builder.create().startNewArray(objectArray, CodeTreeBuilder.singleString(String.valueOf(filteredSpecializations.size() + 1))).end().build());
        builder.declaration(objectArray, "s", (CodeTree) null);

        builder.statement("data[0] = 0"); // declare version 0

        FrameState frameState = FrameState.load(this);
        builder.tree(state.createLoad(frameState, null));
        if (requiresExclude()) {
            builder.tree(exclude.createLoad(frameState, null));
        }

        int index = 1;
        for (SpecializationData specialization : filteredSpecializations) {
            builder.startStatement().string("s = ").startNewArray(objectArray, CodeTreeBuilder.singleString("3")).end().end();
            builder.startStatement().string("s[0] = ").doubleQuote(specialization.getMethodName()).end();

            builder.startIf().tree(state.createContains(frameState, new Object[]{specialization})).end().startBlock();
            builder.startStatement().string("s[1] = (byte)0b01 /* active */").end();
            TypeMirror listType = new DeclaredCodeTypeMirror((TypeElement) context.getDeclaredType(ArrayList.class).asElement(), Arrays.asList(context.getType(Object.class)));

            if (!specialization.getCaches().isEmpty()) {
                builder.declaration(listType, "cached", "new ArrayList<>()");

                boolean useSpecializationClass = useSpecializationClass(specialization);

                String name = createSpecializationLocalName(specialization);

                if (useSpecializationClass) {
                    builder.declaration(createSpecializationTypeName(specialization), name, CodeTreeBuilder.singleString(createSpecializationFieldName(specialization)));

                    if (specialization.getMaximumNumberOfInstances() > 1) {
                        builder.startWhile();
                    } else {
                        builder.startIf();
                    }
                    builder.string(name, " != null");
                    builder.end();
                    builder.startBlock();
                }

                builder.startStatement().startCall("cached", "add");
                builder.startStaticCall(context.getType(Arrays.class), "asList");
                for (CacheExpression cache : specialization.getCaches()) {
                    builder.startGroup();
                    builder.tree(createCacheReference(specialization, cache.getParameter()));
                    builder.end();
                }
                builder.end();
                builder.end().end();

                if (useSpecializationClass) {
                    if (specialization.getMaximumNumberOfInstances() > 1) {
                        builder.startStatement().string(name, " = ", name, ".next_").end();
                    }

                    builder.end(); // cache while or if
                }

                builder.statement("s[2] = cached");
            }
            builder.end();
            if (mayBeExcluded(specialization)) {
                builder.startElseIf().tree(exclude.createContains(frameState, new Object[]{specialization})).end().startBlock();
                builder.startStatement().string("s[1] = (byte)0b10 /* excluded */").end();
                builder.end();
            }
            builder.startElseBlock();
            builder.startStatement().string("s[1] = (byte)0b00 /* inactive */").end();
            builder.end();
            builder.startStatement().string("data[", String.valueOf(index), "] = s").end();
            index++;
        }

        builder.startReturn().startStaticCall(context.getType(Introspection.Provider.class), "create").string("data").end().end();

        clazz.add(reflection);
    }

    private void createFields(CodeTypeElement clazz) {
        CodeVariableElement var = state.declareFields(clazz);
        var.createInitBuilder().string("1");

        if (requiresExclude()) {
            exclude.declareFields(clazz);
        }
        for (SpecializationData specialization : reachableSpecializations) {
            List<CodeVariableElement> fields = new ArrayList<>();
            boolean useSpecializationClass = useSpecializationClass(specialization);

            for (CacheExpression cache : specialization.getCaches()) {
                Parameter parameter = cache.getParameter();
                String fieldName = createFieldName(specialization, parameter);
                TypeMirror type = parameter.getType();
                Modifier visibility = useSpecializationClass ? null : Modifier.PRIVATE;
                CodeVariableElement cachedField;
                if (ElementUtils.isAssignable(type, context.getType(NodeInterface.class))) {
                    cachedField = createNodeField(visibility, type, fieldName, Child.class);
                } else if (type.getKind() == TypeKind.ARRAY && ElementUtils.isAssignable(((ArrayType) type).getComponentType(), context.getType(NodeInterface.class))) {
                    cachedField = createNodeField(visibility, type, fieldName, Children.class, Modifier.FINAL);
                } else {
                    if (useSpecializationClass) {
                        cachedField = createNodeField(visibility, type, fieldName, null, Modifier.FINAL);
                    } else {
                        cachedField = createNodeField(visibility, type, fieldName, null);
                    }
                    setFieldCompilationFinal(cachedField, parameter.getVariableElement().getAnnotation(Cached.class).dimensions());
                }
                fields.add(cachedField);
            }

            for (AssumptionExpression assumption : specialization.getAssumptionExpressions()) {
                String fieldName = createAssumptionFieldName(specialization, assumption);
                TypeMirror type;
                int compilationFinalDimensions;
                if (assumption.getExpression().getResolvedType().getKind() == TypeKind.ARRAY) {
                    type = context.getType(Assumption[].class);
                    compilationFinalDimensions = 1;
                } else {
                    type = context.getType(Assumption.class);
                    compilationFinalDimensions = -1;
                }
                CodeVariableElement assumptionField;
                if (useSpecializationClass) {
                    assumptionField = createNodeField(null, type, fieldName, null, Modifier.FINAL);
                } else {
                    assumptionField = createNodeField(PRIVATE, type, fieldName, null);
                }

                setFieldCompilationFinal(assumptionField, compilationFinalDimensions);

                fields.add(assumptionField);
            }

            if (useSpecializationClass) {
                TypeMirror baseType;
                boolean useNode = specializationClassIsNode(specialization);
                if (useNode) {
                    baseType = context.getType(Node.class);
                } else {
                    baseType = context.getType(Object.class);
                }

                CodeTypeElement cacheType = GeneratorUtils.createClass(node, null, modifiers(PRIVATE, FINAL,
                                STATIC), createSpecializationTypeName(specialization), baseType);

                Class<?> annotationType;
                if (useNode) {
                    annotationType = Child.class;
                    if (specialization.getMaximumNumberOfInstances() > 1) {
                        cacheType.add(createNodeField(null, cacheType.asType(), "next_", Child.class));
                    }

                    CodeExecutableElement getNodeCost = new CodeExecutableElement(modifiers(PUBLIC),
                                    context.getType(NodeCost.class), "getCost");
                    getNodeCost.createBuilder().startReturn().staticReference(context.getType(NodeCost.class),
                                    "NONE").end();
                    cacheType.add(getNodeCost);
                } else {
                    annotationType = CompilationFinal.class;
                    if (specialization.getMaximumNumberOfInstances() > 1) {
                        cacheType.add(createNodeField(null, cacheType.asType(), "next_", annotationType));
                    }
                }

                cacheType.getEnclosedElements().addAll(fields);
                cacheType.add(GeneratorUtils.createConstructorUsingFields(modifiers(), cacheType));

                clazz.add(createNodeField(PRIVATE, cacheType.asType(),
                                createSpecializationFieldName(specialization), annotationType));
                clazz.add(cacheType);

            } else {
                clazz.getEnclosedElements().addAll(fields);
            }

        }
    }

    private static void setFieldCompilationFinal(CodeVariableElement field, int compilationFinalDimensions) {
        if (field.getModifiers().contains(Modifier.FINAL) && compilationFinalDimensions <= 0) {
            // no need for the compilation final annotation.
            return;
        }
        CodeAnnotationMirror annotation = new CodeAnnotationMirror(ProcessorContext.getInstance().getDeclaredType(CompilationFinal.class));
        if (compilationFinalDimensions > 0) {
            annotation.setElementValue(annotation.findExecutableElement("dimensions"), new CodeAnnotationValue(compilationFinalDimensions));
        }
        field.getAnnotationMirrors().add(annotation);
    }

    /* Specialization class needs to be a Node in such a case. */
    private boolean specializationClassIsNode(SpecializationData specialization) {
        boolean useSpecializationClass = useSpecializationClass(specialization);
        if (useSpecializationClass) {
            for (CacheExpression cache : specialization.getCaches()) {
                TypeMirror type = cache.getParameter().getType();
                if (ElementUtils.isAssignable(type, context.getType(NodeInterface.class))) {
                    return true;
                } else if (type.getKind() == TypeKind.ARRAY && ElementUtils.isAssignable(((ArrayType) type).getComponentType(), context.getType(NodeInterface.class))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean requiresExclude() {
        for (SpecializationData specialization : reachableSpecializations) {
            if (mayBeExcluded(specialization)) {
                return true;
            }
        }
        return false;
    }

    private Element createIsValid(TypeMirror assumptionType) {
        CodeExecutableElement isValid = new CodeExecutableElement(modifiers(PRIVATE, STATIC), getType(boolean.class), "isValid_");

        CodeTreeBuilder builder = isValid.createBuilder();
        if (assumptionType.getKind() == TypeKind.ARRAY) {
            isValid.addParameter(new CodeVariableElement(getType(Assumption[].class), "assumptions"));
            builder.startFor().startGroup().type(((ArrayType) assumptionType).getComponentType()).string(" assumption : assumptions").end().end();
            builder.startBlock();
            builder.startIf().string("assumption == null || !assumption.isValid()").end();
            builder.startBlock();
            builder.returnFalse();
            builder.end();
            builder.end();
            builder.returnTrue();
        } else {
            isValid.addParameter(new CodeVariableElement(getType(Assumption.class), "assumption"));
            builder.startReturn().string("assumption != null && assumption.isValid()").end();
        }

        return isValid;
    }

    private Element createFallbackGuard(SpecializationData fallback) {
        boolean frameUsed = node.isFrameUsedByAnyGuard();
        FrameState frameState = FrameState.load(this);

        List<SpecializationData> specializations = new ArrayList<>(reachableSpecializations);
        specializations.remove(fallback);

        SpecializationGroup group = SpecializationGroup.createFlat(specializations);

        ExecutableTypeData executableType = node.findAnyGenericExecutableType(context, -1);

        if (!frameUsed) {
            frameState.removeValue(FRAME_VALUE);
        }

        fallbackNeedsState = false;
        state.createLoad(frameState, null);
        CodeExecutableElement method = frameState.createMethod(modifiers(PRIVATE), getType(boolean.class), METHOD_FALLBACK_GUARD, FRAME_VALUE, STATE_VALUE);

        CodeTree result = visitSpecializationGroup(CodeTreeBuilder.createBuilder(), group, executableType, frameState, null, NodeExecutionMode.FALLBACK_GUARD);

        if (!fallbackNeedsState) {
            VariableElement toRemove = null;
            for (VariableElement v : method.getParameters()) {
                if (v.getSimpleName().toString().equals(STATE_VALUE)) {
                    toRemove = v;
                    break;
                }
            }
            if (toRemove != null) {
                method.getParameters().remove(toRemove);
            }
        }
        final CodeTreeBuilder builder = method.createBuilder();
        for (SpecializationData implemented : specializations) {
            if (implemented.getMaximumNumberOfInstances() > 1) {
                method.getAnnotationMirrors().add(createExplodeLoop());
                break;
            }
        }

        builder.tree(result);
        builder.returnTrue();

        if (!accessesState(reachableSpecializations)) {
            method.getModifiers().add(STATIC);
        }

        return method;
    }

    private static boolean accessesState(List<SpecializationData> specializations) {
        final AtomicBoolean needsState = new AtomicBoolean(false);
        for (final SpecializationData specialization : specializations) {
            if (!specialization.getAssumptionExpressions().isEmpty()) {
                needsState.set(true);
                break;
            }
            for (GuardExpression expression : specialization.getGuards()) {
                expression.getExpression().accept(new DSLExpressionVisitor() {
                    public void visitVariable(Variable binary) {
                        Parameter p = specialization.findByVariable(binary.getResolvedVariable());
                        if (p == null && binary.getResolvedVariable().getModifiers().contains(STATIC)) {
                            needsState.set(true);
                        } else if (p != null && p.getSpecification().isCached()) {
                            needsState.set(true);
                        }
                    }

                    public void visitBooleanLiteral(BooleanLiteral binary) {
                    }

                    public void visitNegate(Negate negate) {
                    }

                    public void visitIntLiteral(IntLiteral binary) {
                    }

                    public void visitCall(Call binary) {
                        if (!binary.getResolvedMethod().getModifiers().contains(STATIC)) {
                            needsState.set(true);
                        }
                    }

                    public void visitBinary(Binary binary) {
                    }
                });
            }
        }
        boolean needsStat = needsState.get();
        return needsStat;
    }

    private CodeAnnotationMirror createExplodeLoop() {
        DeclaredType explodeLoopType = context.getDeclaredType(ExplodeLoop.class);
        CodeAnnotationMirror explodeLoop = new CodeAnnotationMirror(explodeLoopType);

        DeclaredType loopExplosionKind = context.getDeclaredType(ExplodeLoop.LoopExplosionKind.class);
        if (loopExplosionKind != null) {
            VariableElement kindValue = ElementUtils.findVariableElement(loopExplosionKind, "FULL_EXPLODE_UNTIL_RETURN");
            if (kindValue != null) {
                explodeLoop.setElementValue(ElementUtils.findExecutableElement(explodeLoopType, "kind"), new CodeAnnotationValue(kindValue));
            }
        }
        return explodeLoop;
    }

    private List<SpecializationData> filterCompatibleSpecializations(ExecutableTypeData executable, List<SpecializationData> specializations) {
        List<SpecializationData> filteredSpecializations = new ArrayList<>();
        outer: for (SpecializationData specialization : specializations) {
            if (specialization.isFallback() && specialization.getMethod() == null) {
                // undefined fallback can always deoptimize
                continue;
            }

            List<TypeMirror> signatureParameters = executable.getSignatureParameters();
            for (int i = 0; i < signatureParameters.size(); i++) {
                TypeMirror evaluatedType = signatureParameters.get(i);
                TypeMirror specializedType = specialization.findParameterOrDie(node.getChildExecutions().get(i)).getType();

                if (!isSubtypeBoxed(context, evaluatedType, specializedType) && !isSubtypeBoxed(context, specializedType, evaluatedType)) {
                    // not compatible parameter
                    continue outer;
                }
            }

            if (!isVoid(executable.getReturnType()) && !isSubtypeBoxed(context, specialization.getReturnType().getType(), executable.getReturnType()) &&
                            !isSubtypeBoxed(context, executable.getReturnType(), specialization.getReturnType().getType())) {
                continue outer;
            }
            filteredSpecializations.add(specialization);
        }

        return filteredSpecializations;
    }

    private List<SpecializationData> filterImplementedSpecializations(ExecutableTypeData executable, List<SpecializationData> specializations) {
        List<SpecializationData> filteredSpecializations = new ArrayList<>();
        TypeMirror returnType = ElementUtils.boxType(context, executable.getReturnType());

        for (SpecializationData specialization : specializations) {
            TypeMirror specializationReturnType = ElementUtils.boxType(context, specialization.getReturnType().getType());
            if (ElementUtils.typeEquals(specializationReturnType, returnType)) {
                filteredSpecializations.add(specialization);
            }
        }

        return filteredSpecializations;
    }

    private List<ExecutableTypeData> filterCompatibleExecutableTypes(ExecutableTypeData type, List<ExecutableTypeData> genericExecutes) {
        List<ExecutableTypeData> compatible = new ArrayList<>();
        outer: for (ExecutableTypeData genericExecute : genericExecutes) {
            if (genericExecute.getEvaluatedCount() != type.getEvaluatedCount()) {
                continue;
            }
            for (int i = 0; i < genericExecute.getEvaluatedCount(); i++) {
                TypeMirror sourceType = type.getSignatureParameters().get(i);
                TypeMirror targetType = genericExecute.getSignatureParameters().get(i);
                if (!ElementUtils.isAssignable(sourceType, targetType)) {
                    continue outer;
                }
            }
            if (!isVoid(type.getReturnType()) && !isSubtypeBoxed(context, type.getReturnType(), genericExecute.getReturnType()) &&
                            !isSubtypeBoxed(context, genericExecute.getReturnType(), type.getReturnType())) {
                continue outer;
            }

            compatible.add(genericExecute);
        }
        return compatible;
    }

    private CodeExecutableElement createExecute(CodeTypeElement clazz, ExecutableTypeData type, List<ExecutableTypeData> delegateableTypes) {
        final List<SpecializationData> allSpecializations = reachableSpecializations;
        final List<SpecializationData> compatibleSpecializations = filterCompatibleSpecializations(type, allSpecializations);
        List<SpecializationData> implementedSpecializations;
        if (delegateableTypes.isEmpty()) {
            implementedSpecializations = compatibleSpecializations;
        } else {
            implementedSpecializations = filterImplementedSpecializations(type, compatibleSpecializations);
        }

        FrameState frameState = FrameState.load(this, type, Integer.MAX_VALUE);
        CodeExecutableElement method = createExecuteMethod(null, type, frameState, true);
        clazz.add(method);
        CodeTreeBuilder builder = method.createBuilder();

        // do I miss specializations that are reachable from this executable?
        if (compatibleSpecializations.size() != implementedSpecializations.size()) {
            ExecuteDelegationResult delegation = createExecuteDelegation(builder, frameState, type, delegateableTypes, compatibleSpecializations, implementedSpecializations);
            builder.tree(delegation.tree);
            if (!delegation.hasFallthrough) {
                return method;
            }
        }

        if (implementedSpecializations.isEmpty()) {
            implementedSpecializations = compatibleSpecializations;
        }

        if (implementedSpecializations.isEmpty()) {
            builder.tree(createTransferToInterpreterAndInvalidate());
            builder.startThrow().startNew(getType(AssertionError.class)).doubleQuote("Delegation failed.").end().end();
        } else {
            SpecializationGroup group = SpecializationGroup.createFlat(implementedSpecializations);
            builder.tree(createFastPath(builder, implementedSpecializations, group, type, frameState));
        }
        return method;
    }

    private ExecuteDelegationResult createExecuteDelegation(CodeTreeBuilder parent, FrameState frameState, ExecutableTypeData type,
                    List<ExecutableTypeData> delegateableTypes, final List<SpecializationData> compatibleSpecializations, List<SpecializationData> implementedSpecializations) {

        CodeTreeBuilder builder = parent.create();
        List<SpecializationData> notImplemented = new ArrayList<>(compatibleSpecializations);
        for (SpecializationData specialization : implementedSpecializations) {
            notImplemented.remove(specialization);
        }
        if (notImplemented.isEmpty()) {
            throw new AssertionError();
        }

        List<ExecutableTypeData> compatibleDelegateTypes = filterCompatibleExecutableTypes(type, delegateableTypes);
        List<ExecutableTypeData> delegatedDelegateTypes = new ArrayList<>();

        CodeTreeBuilder delegateBuilder = builder.create();
        boolean elseIf = false;
        boolean coversAllSpecializations = false;
        if (boxingEliminationEnabled) {
            Set<TypeMirror> optimizeTypes = new HashSet<>();
            for (SpecializationData specialization : reachableSpecializations) {
                TypeMirror returnType = specialization.getReturnType().getType();
                if (ElementUtils.isPrimitive(returnType)) {
                    optimizeTypes.add(returnType);
                }
            }

            for (TypeMirror optimizedType : ElementUtils.uniqueSortedTypes(optimizeTypes, true)) {
                ExecutableTypeData delegateType = null;
                for (ExecutableTypeData compatibleType : compatibleDelegateTypes) {
                    if (ElementUtils.typeEquals(compatibleType.getReturnType(), optimizedType)) {
                        delegateType = compatibleType;
                        break;
                    }
                }

                if (delegateType != null) {
                    List<SpecializationData> delegateSpecializations = filterImplementedSpecializations(delegateType, filterCompatibleSpecializations(delegateType, reachableSpecializations));
                    coversAllSpecializations = delegateSpecializations.size() == reachableSpecializations.size();
                    if (!coversAllSpecializations) {
                        builder.tree(state.createLoad(frameState, null));
                        elseIf = delegateBuilder.startIf(elseIf);
                        List<SpecializationData> allSpecializations = new ArrayList<>();
                        allSpecializations.add(node.getUninitializedSpecialization());
                        allSpecializations.addAll(reachableSpecializations);
                        delegateBuilder.tree(state.createContainsOnly(frameState, 0, -1, delegateSpecializations.toArray(), allSpecializations.toArray())).end();
                        delegateBuilder.startBlock();
                    }
                    delegatedDelegateTypes.add(delegateType);
                    delegateBuilder.tree(createCallExecute(type, delegateType, frameState));
                    if (!coversAllSpecializations) {
                        delegateBuilder.end();
                    }
                    if (coversAllSpecializations) {
                        break;
                    }
                }
            }
        }

        if (!compatibleDelegateTypes.isEmpty() && !coversAllSpecializations) {
            ExecutableTypeData delegateType = compatibleDelegateTypes.get(0);
            coversAllSpecializations = notImplemented.size() == reachableSpecializations.size();
            if (!coversAllSpecializations) {
                builder.tree(state.createLoad(frameState, null));
                elseIf = delegateBuilder.startIf(elseIf);
                delegateBuilder.tree(state.createContains(frameState, notImplemented.toArray())).end();
                delegateBuilder.startBlock();
            }
            delegatedDelegateTypes.add(delegateType);
            delegateBuilder.tree(createCallExecute(type, delegateType, frameState));
            if (!coversAllSpecializations) {
                delegateBuilder.end();
            }
        }

        boolean hasUnexpected = false;
        for (ExecutableTypeData delegateType : delegatedDelegateTypes) {
            if (needsUnexpectedResultException(delegateType)) {
                hasUnexpected = true;
                break;
            }
        }

        if (hasUnexpected) {
            builder.startTryBlock();
            builder.tree(delegateBuilder.build());
            builder.end().startCatchBlock(context.getType(UnexpectedResultException.class), "ex");
            if (isVoid(type.getReturnType())) {
                builder.returnStatement();
            } else {
                builder.startReturn();
                builder.tree(expectOrCast(getType(Object.class), type, CodeTreeBuilder.singleString("ex")));
                builder.end();
            }
            builder.end();
        } else {
            builder.tree(delegateBuilder.build());
        }
        return new ExecuteDelegationResult(builder.build(), !coversAllSpecializations);
    }

    private CodeExecutableElement createExecuteAndSpecialize() {
        if (!node.needsRewrites(context)) {
            return null;
        }

        final FrameState frameState = FrameState.load(this);
        String frame = null;
        if (needsFrame(reachableSpecializations)) {
            frame = FRAME_VALUE;
        }

        TypeMirror returnType = executeAndSpecializeType.getReturnType();

        CodeExecutableElement method = frameState.createMethod(modifiers(PRIVATE), returnType, "executeAndSpecialize", frame);

        final CodeTreeBuilder builder = method.createBuilder();

        builder.declaration(context.getType(Lock.class), "lock", "getLock()");
        builder.declaration(context.getType(boolean.class), "hasLock", "true");
        builder.statement("lock.lock()");
        builder.startTryBlock();

        builder.tree(state.createLoad(frameState, node.getUninitializedSpecialization()));
        if (requiresExclude()) {
            builder.tree(exclude.createLoad(frameState, null));
        }

        FrameState originalFrameState = frameState.copy();
        SpecializationGroup group = createSpecializationGroups();
        CodeTree execution = visitSpecializationGroup(builder, group, executeAndSpecializeType, frameState, null, NodeExecutionMode.SLOW_PATH);

        builder.tree(execution);

        if (group.hasFallthrough()) {
            builder.tree(createTransferToInterpreterAndInvalidate());
            builder.tree(createThrowUnsupported(builder, originalFrameState));
        }
        builder.end().startFinallyBlock();
        builder.startIf().string("hasLock").end().startBlock();
        builder.statement("lock.unlock()");
        builder.end();
        builder.end();

        return method;
    }

    private CodeTree createThrowUnsupported(final CodeTreeBuilder parent, final FrameState frameState) {
        CodeTreeBuilder builder = parent.create();
        builder.startThrow().startNew(context.getType(UnsupportedSpecializationException.class));
        builder.string("this");
        builder.startNewArray(new ArrayCodeTypeMirror(context.getType(Node.class)), null);
        List<CodeTree> values = new ArrayList<>();

        for (NodeExecutionData execution : node.getChildExecutions()) {
            NodeChildData child = execution.getChild();
            LocalVariable var = frameState.getValue(execution);
            LocalVariable shortCircuit = frameState.getShortCircuit(execution);
            if (shortCircuit != null) {
                builder.string("null");
                values.add(shortCircuit.createReference());
            }
            if (child != null) {
                builder.string("this.", nodeFieldName(execution));
            } else {
                builder.string("null");
            }
            if (var != null) {
                values.add(var.createReference());
            }
        }
        builder.end();
        builder.trees(values.toArray(new CodeTree[0]));
        builder.end().end();
        return builder.build();

    }

    private CodeTree createFastPath(CodeTreeBuilder parent, List<SpecializationData> allSpecializations, SpecializationGroup originalGroup, final ExecutableTypeData currentType,
                    FrameState frameState) {
        final CodeTreeBuilder builder = parent.create();

        builder.tree(state.createLoad(frameState, null));

        int sharedExecutes = 0;
        for (NodeExecutionData execution : node.getChildExecutions()) {
            boolean canExecuteChild = execution.getIndex() < currentType.getEvaluatedCount();
            for (TypeGuard checkedGuard : originalGroup.getTypeGuards()) {
                if (checkedGuard.getSignatureIndex() == execution.getIndex()) {
                    canExecuteChild = true;
                    break;
                }
            }

            if (!canExecuteChild) {
                break;
            }
            for (TypeGuard checkedGuard : originalGroup.getTypeGuards()) {
                // we cannot pull out guards that use optimized implicit source types
                if (resolveOptimizedImplicitSourceTypes(execution, checkedGuard.getType()).size() > 1) {
                    canExecuteChild = false;
                    break;
                }
            }
            if (!canExecuteChild) {
                break;
            }

            builder.tree(createFastPathExecuteChild(builder, frameState.copy(), frameState, currentType, originalGroup, execution));
            sharedExecutes++;
        }

        List<BoxingSplit> boxingSplits = parameterBoxingElimination(originalGroup, sharedExecutes);

        if (boxingSplits.isEmpty()) {
            builder.tree(executeFastPathGroup(builder, frameState, currentType, originalGroup, sharedExecutes, null));
            addExplodeLoop(builder, originalGroup);
        } else {
            FrameState originalFrameState = frameState.copy();

            boolean elseIf = false;
            for (BoxingSplit split : boxingSplits) {
                elseIf = builder.startIf(elseIf);
                List<SpecializationData> specializations = split.group.collectSpecializations();

                List<SpecializationData> containsOnlySpecializations = new ArrayList<>();
                containsOnlySpecializations.add(node.getUninitializedSpecialization());
                containsOnlySpecializations.addAll(allSpecializations);
                List<SpecializationData> maskSpecializations = new ArrayList<>();
                maskSpecializations.add(node.getUninitializedSpecialization());
                maskSpecializations.addAll(reachableSpecializations);
                CodeTree containsOnly = state.createContainsOnly(frameState, 0, -1, specializations.toArray(), maskSpecializations.toArray());
                builder.tree(containsOnly);
                builder.end().startBlock();
                builder.tree(wrapInAMethod(builder, split.group, originalFrameState, split.getName(),
                                executeFastPathGroup(builder, frameState.copy(), currentType, split.group, sharedExecutes, specializations)));
                builder.end();
            }

            builder.startElseBlock();
            builder.tree(wrapInAMethod(builder, originalGroup, originalFrameState, "generic", executeFastPathGroup(builder, frameState, currentType, originalGroup, sharedExecutes, null)));
            builder.end();
        }

        return builder.build();
    }

    private void addExplodeLoop(final CodeTreeBuilder builder, SpecializationGroup originalGroup) {
        for (SpecializationData implemented : originalGroup.collectSpecializations()) {
            if (implemented.getMaximumNumberOfInstances() > 1) {
                ((CodeExecutableElement) builder.findMethod()).getAnnotationMirrors().add(createExplodeLoop());
                break;
            }
        }
    }

    private CodeTree wrapInAMethod(CodeTreeBuilder parent, SpecializationGroup group, FrameState frameState, String suffix, CodeTree codeTree) {
        CodeExecutableElement parentMethod = (CodeExecutableElement) parent.findMethod();
        CodeTypeElement parentClass = (CodeTypeElement) parentMethod.getEnclosingElement();
        String name = parentMethod.getSimpleName().toString() + "_" + suffix + (boxingSplitIndex++);
        CodeExecutableElement method = parentClass.add(
                        frameState.createMethod(modifiers(Modifier.PRIVATE), parentMethod.getReturnType(), name, FRAME_VALUE,
                                        STATE_VALUE));
        CodeTreeBuilder builder = method.createBuilder();
        builder.tree(codeTree);
        method.getThrownTypes().addAll(parentMethod.getThrownTypes());
        addExplodeLoop(builder, group);

        CodeTreeBuilder parentBuilder = parent.create();
        parentBuilder.startReturn();
        parentBuilder.startCall(method.getSimpleName().toString());
        frameState.addReferencesTo(parentBuilder, FRAME_VALUE, STATE_VALUE);
        parentBuilder.end();
        parentBuilder.end();
        return parentBuilder.build();
    }

    private CodeTree executeFastPathGroup(final CodeTreeBuilder parent, FrameState frameState, final ExecutableTypeData currentType, SpecializationGroup group, int sharedExecutes,
                    List<SpecializationData> allowedSpecializations) {
        CodeTreeBuilder builder = parent.create();
        FrameState originalFrameState = frameState.copy();

        for (NodeExecutionData execution : node.getChildExecutions()) {
            if (execution.getIndex() < sharedExecutes) {
                // skip shared executes
                continue;
            }
            builder.tree(createFastPathExecuteChild(builder, originalFrameState, frameState, currentType, group, execution));
        }

        builder.tree(visitSpecializationGroup(builder, group, currentType, frameState, allowedSpecializations, NodeExecutionMode.FAST_PATH));
        if (group.hasFallthrough()) {
            builder.tree(createTransferToInterpreterAndInvalidate());
            builder.tree(createCallExecuteAndSpecialize(currentType, originalFrameState));
        }
        return builder.build();
    }

    /*
     * It duplicates a group into small subgroups of specializations that don't need boxing when
     * executing the children.
     */
    private List<BoxingSplit> parameterBoxingElimination(SpecializationGroup group, int evaluatedcount) {
        if (!boxingEliminationEnabled) {
            return Collections.emptyList();
        }

        List<SpecializationData> allSpecializations = group.collectSpecializations();
        List<Set<TypeGuard>> signatures = new ArrayList<>();
        List<List<SpecializationData>> signatureSpecializations = new ArrayList<>();

        for (SpecializationData specialization : allSpecializations) {
            int index = -1;
            List<TypeGuard> guards = new ArrayList<>();
            for (Parameter p : specialization.getSignatureParameters()) {
                index++;
                if (!ElementUtils.isPrimitive(p.getType())) {
                    continue;
                } else if (index < evaluatedcount) {
                    continue;
                } else {
                    NodeChildData child = p.getSpecification().getExecution().getChild();
                    if (child != null && child.findExecutableType(p.getType()) == null) {
                        // type cannot be executed so it cannot be eliminated
                        continue;
                    }
                }
                guards.add(new TypeGuard(p.getType(), index));
            }
            if (!guards.isEmpty()) {
                boolean directFound = false;
                for (int i = 0; i < signatures.size(); i++) {
                    if (guards.containsAll(signatures.get(i))) {
                        if (signatures.get(i).containsAll(guards)) {
                            directFound = true;
                        }
                        signatureSpecializations.get(i).add(specialization);
                    }
                }
                if (!directFound) {
                    signatures.add(new LinkedHashSet<>(guards));
                    List<SpecializationData> specializations = new ArrayList<>();
                    specializations.add(specialization);
                    signatureSpecializations.add(specializations);
                }
            }
        }
        List<BoxingSplit> groups = new ArrayList<>();

        for (int i = 0; i < signatureSpecializations.size(); i++) {
            List<SpecializationData> groupedSpecialization = signatureSpecializations.get(i);
            if (allSpecializations.size() == groupedSpecialization.size()) {
                // contains all specializations does not make sense to group
                continue;
            }
            Set<TypeGuard> signature = signatures.get(i);

            TypeMirror[] signatureMirrors = new TypeMirror[signature.size()];
            int index = 0;
            for (TypeGuard typeGuard : signature) {
                signatureMirrors[index] = typeGuard.getType();
                index++;
            }

            groups.add(new BoxingSplit(SpecializationGroup.createFlat(groupedSpecialization), signatureMirrors));
        }

        Collections.sort(groups, new Comparator<BoxingSplit>() {
            public int compare(BoxingSplit o1, BoxingSplit o2) {
                return Integer.compare(o2.primitiveSignature.length, o1.primitiveSignature.length);
            }
        });

        return groups;
    }

    private CodeTree createFastPathExecuteChild(final CodeTreeBuilder parent, FrameState originalFrameState, FrameState frameState, final ExecutableTypeData currentType, SpecializationGroup group,
                    NodeExecutionData execution) {
        CodeTreeBuilder builder = parent.create();

        LocalVariable var = frameState.getValue(execution);
        if (var == null) {
            TypeMirror targetType;

            TypeGuard eliminatedGuard = null;
            if (boxingEliminationEnabled) {
                for (TypeGuard checkedGuard : group.getTypeGuards()) {
                    if (!ElementUtils.isPrimitive(checkedGuard.getType())) {
                        // no elimination for non primitive types
                        continue;
                    } else if (node.getChildExecutions().get(checkedGuard.getSignatureIndex()).getChild().findExecutableType(checkedGuard.getType()) == null) {
                        // type cannot be executed so it cannot be eliminated
                        continue;
                    }

                    if (checkedGuard.getSignatureIndex() == execution.getIndex()) {
                        eliminatedGuard = checkedGuard;
                        break;
                    }
                }
            }
            if (eliminatedGuard != null) {
                // we can optimize the type guard away by executing it
                group.getTypeGuards().remove(eliminatedGuard);
                targetType = eliminatedGuard.getType();
            } else {
                targetType = execution.getChild().findAnyGenericExecutableType(context).getReturnType();
            }
            LocalVariable shortCircuit = resolveShortCircuit(null, execution, frameState);
            var = frameState.createValue(execution, targetType).nextName();

            LocalVariable fallbackVar;
            List<TypeMirror> originalSourceTypes = typeSystem.lookupSourceTypes(targetType);
            List<TypeMirror> sourceTypes = resolveOptimizedImplicitSourceTypes(execution, targetType);
            if (sourceTypes.size() > 1) {
                TypeGuard typeGuard = new TypeGuard(targetType, execution.getIndex());
                TypeMirror generic = node.getPolymorphicSpecialization().findParameterOrDie(execution).getType();
                fallbackVar = originalFrameState.createValue(execution, generic);

                // we want to create the check tree in reverse order
                Collections.reverse(sourceTypes);
                CodeTree access = var.createReference();
                boolean first = true;
                for (TypeMirror sType : sourceTypes) {
                    if (ElementUtils.typeEquals(sType, targetType)) {
                        continue;
                    }
                    String localName = createSourceTypeLocalName(var, sType);
                    builder.declaration(sType, localName, CodeTreeBuilder.createBuilder().defaultValue(sType).build());

                    CodeTreeBuilder accessBuilder = builder.create();
                    accessBuilder.startParantheses();
                    accessBuilder.tree(state.createContainsOnly(frameState, originalSourceTypes.indexOf(sType), 1, new Object[]{typeGuard},
                                    new Object[]{typeGuard, node.getUninitializedSpecialization()}));
                    accessBuilder.string(" ? ");
                    if (ElementUtils.isPrimitive(sType)) {
                        accessBuilder.string("(").type(generic).string(") ");
                    }
                    accessBuilder.string(localName);
                    accessBuilder.string(" : ");
                    if (first && ElementUtils.isPrimitive(targetType)) {
                        accessBuilder.string("(").type(generic).string(") ");
                    }
                    accessBuilder.tree(access);
                    accessBuilder.end();
                    access = accessBuilder.build();
                    first = false;
                }
                fallbackVar = fallbackVar.accessWith(access);
            } else {
                fallbackVar = var;
            }

            builder.tree(createAssignExecuteChild(originalFrameState, frameState, builder, execution, currentType, var, shortCircuit));
            frameState.setValue(execution, var);
            originalFrameState.setValue(execution, fallbackVar);
        }
        return builder.build();
    }

    private CodeTree createAssignExecuteChild(FrameState originalFrameState, FrameState frameState, CodeTreeBuilder parent, NodeExecutionData execution, ExecutableTypeData forType,
                    LocalVariable targetValue,
                    LocalVariable shortCircuit) {
        CodeTreeBuilder builder = parent.create();

        ChildExecutionResult executeChild = createExecuteChild(builder, originalFrameState, frameState, execution, targetValue);
        builder.tree(createTryExecuteChild(targetValue, executeChild.code, shortCircuit == null, executeChild.throwsUnexpectedResult));

        if (shortCircuit != null) {
            frameState.setShortCircuitValue(execution, shortCircuit.accessWith(null));
            originalFrameState.setShortCircuitValue(execution, shortCircuit.accessWith(null));
        }

        builder.end();
        if (executeChild.throwsUnexpectedResult) {
            builder.startCatchBlock(getType(UnexpectedResultException.class), "ex");
            FrameState slowPathFrameState = originalFrameState.copy();
            slowPathFrameState.setValue(execution, targetValue.makeGeneric(context).accessWith(CodeTreeBuilder.singleString("ex.getResult()")));

            ExecutableTypeData delegateType = node.getGenericExecutableType(forType);
            boolean found = false;
            for (NodeExecutionData otherExecution : node.getChildExecutions()) {
                if (found) {
                    LocalVariable childEvaluatedValue = slowPathFrameState.createValue(otherExecution, genericType);
                    LocalVariable genericShortCircuit = resolveShortCircuit(null, otherExecution, slowPathFrameState);
                    builder.tree(createAssignExecuteChild(slowPathFrameState.copy(), slowPathFrameState, builder, otherExecution, delegateType, childEvaluatedValue, genericShortCircuit));
                    slowPathFrameState.setValue(otherExecution, childEvaluatedValue);
                } else {
                    // skip forward already evaluated
                    found = execution == otherExecution;
                }
            }
            builder.tree(createCallExecuteAndSpecialize(forType, slowPathFrameState));
            builder.end();
        }

        return createShortCircuit(targetValue, shortCircuit, builder.build());
    }

    private static String createSourceTypeLocalName(LocalVariable targetValue, TypeMirror sType) {
        return targetValue.getName() + ElementUtils.getSimpleName(sType);
    }

    private ChildExecutionResult createCallSingleChildExecute(NodeExecutionData execution, LocalVariable target, FrameState frameState, ExecutableTypeData executableType) {
        CodeTree execute = callChildExecuteMethod(execution, executableType, frameState);
        TypeMirror sourceType = executableType.getReturnType();
        TypeMirror targetType = target.getTypeMirror();
        CodeTree result = expect(sourceType, targetType, execute);
        return new ChildExecutionResult(result, executableType.hasUnexpectedValue(context) || needsCastTo(sourceType, targetType));
    }

    private ChildExecutionResult createExecuteChild(CodeTreeBuilder parent, FrameState originalFrameState, FrameState frameState, NodeExecutionData execution, LocalVariable target) {

        ChildExecutionResult result;
        if (!typeSystem.hasImplicitSourceTypes(target.getTypeMirror())) {
            ExecutableTypeData targetExecutable = resolveTargetExecutable(execution, target.typeMirror);
            final CodeTreeBuilder builder = parent.create();
            result = createCallSingleChildExecute(execution, target, frameState, targetExecutable);
            builder.string(target.getName()).string(" = ");
            builder.tree(result.code);
            result.code = builder.build();
        } else {
            result = createExecuteChildImplicitCast(parent.create(), originalFrameState, frameState, execution, target);
        }
        return result;
    }

    // old code

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
        if (!node.getChildExecutions().isEmpty()) {
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

    private List<ExecutableTypeData> filterExecutableTypes(List<ExecutableTypeData> executableTypes, List<SpecializationData> specializations) {
        Set<TypeMirror> specializedReturnTypes = new HashSet<>();
        for (SpecializationData specialization : specializations) {
            specializedReturnTypes.add(specialization.getReturnType().getType());
        }

        List<ExecutableTypeData> filteredTypes = new ArrayList<>();
        outer: for (ExecutableTypeData executable : executableTypes) {
            if (executable.getMethod() == null) {
                continue;
            }
            if (executable.isAbstract()) {
                filteredTypes.add(executable);
                continue;
            }
            if (executable.isFinal()) {
                // no way to implement that
                continue;
            }

            if (!executable.hasUnexpectedValue(context)) {
                filteredTypes.add(executable);
                continue;
            } else {
                TypeMirror returnType = executable.getReturnType();
                if (boxingEliminationEnabled && (isVoid(returnType) || ElementUtils.isPrimitive(returnType))) {
                    for (TypeMirror specializedReturnType : specializedReturnTypes) {
                        if (isSubtypeBoxed(context, specializedReturnType, returnType)) {
                            filteredTypes.add(executable);
                            continue outer;
                        }
                    }
                }
            }

        }
        Collections.sort(filteredTypes);
        return filteredTypes;
    }

    private Element createGetCostMethod() {
        TypeMirror returnType = getType(NodeCost.class);
        CodeExecutableElement executable = new CodeExecutableElement(modifiers(PUBLIC), returnType, "getCost");
        executable.getAnnotationMirrors().add(new CodeAnnotationMirror(context.getDeclaredType(Override.class)));
        CodeTreeBuilder builder = executable.createBuilder();
        FrameState frameState = FrameState.load(this);
        builder.tree(state.createLoad(frameState, node.getUninitializedSpecialization()));

        if (node.needsRewrites(context)) {
            builder.startIf().tree(state.createIs(frameState, new Object[]{})).end();
            builder.startBlock();
            builder.startReturn().staticReference(getType(NodeCost.class), "UNINITIALIZED").end();
            builder.end();
            if (reachableSpecializations.size() == 1 && !reachableSpecializations.iterator().next().hasMultipleInstances()) {
                builder.startElseBlock();
                builder.startReturn().staticReference(getType(NodeCost.class), "MONOMORPHIC").end();
                builder.end();
            } else {
                builder.startElseIf();
                builder.tree(state.createIsOneBitOf(frameState, reachableSpecializations.toArray()));
                builder.end();
                builder.startBlock();

                List<CodeTree> additionalChecks = new ArrayList<>();
                for (SpecializationData specialization : reachableSpecializations) {
                    if (useSpecializationClass(specialization) && specialization.getMaximumNumberOfInstances() > 1) {
                        String typeName = createSpecializationTypeName(specialization);
                        String fieldName = createSpecializationFieldName(specialization);
                        String localName = createSpecializationLocalName(specialization);
                        builder.declaration(typeName, localName, "this." + fieldName);
                        CodeTree check = builder.create().startParantheses().string(localName, " == null || ",
                                        localName, ".next_ == null").end().build();
                        additionalChecks.add(check);
                    }
                }
                if (!additionalChecks.isEmpty()) {
                    builder.startIf().tree(combineTrees(" && ", additionalChecks.toArray(new CodeTree[0]))).end().startBlock();
                }
                builder.startReturn().staticReference(getType(NodeCost.class), "MONOMORPHIC").end();
                if (!additionalChecks.isEmpty()) {
                    builder.end();
                }
                builder.end();

                builder.startReturn().staticReference(getType(NodeCost.class), "POLYMORPHIC").end();
            }
        } else {
            builder.startReturn().staticReference(getType(NodeCost.class), "MONOMORPHIC").end();
        }

        return executable;

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

    private List<SpecializationData> calculateReachableSpecializations() {
        List<SpecializationData> specializations = new ArrayList<>();
        for (SpecializationData specialization : node.getSpecializations()) {
            if (specialization.isReachable() &&   //
                            (specialization.isSpecialized()   //
                                            || (specialization.isFallback() && specialization.getMethod() != null))) {
                specializations.add(specialization);
            }
        }
        return specializations;
    }

    private TypeMirror getType(Class<?> clazz) {
        return context.getType(clazz);
    }

    private static CodeVariableElement createNodeField(Modifier visibility, TypeMirror type, String name, Class<?> annotationType, Modifier... modifiers) {
        CodeVariableElement childField = new CodeVariableElement(modifiers(modifiers), type, name);
        if (annotationType != null) {
            childField.getAnnotationMirrors().add(new CodeAnnotationMirror(ProcessorContext.getInstance().getDeclaredType(annotationType)));
        }
        setVisibility(childField.getModifiers(), visibility);
        return childField;
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

    private CodeTree[] bindExecuteMethodParameters(NodeExecutionData execution, ExecutableTypeData method, FrameState frameState) {
        List<NodeExecutionData> executeWith = execution != null ? execution.getChild().getExecuteWith() : null;

        List<CodeTree> values = new ArrayList<>();
        if (method.getFrameParameter() != null) {
            LocalVariable frameLocal = frameState.get(FRAME_VALUE);
            if (frameLocal == null) {
                values.add(CodeTreeBuilder.singleString("null"));
            } else {
                values.add(createTypeSafeReference(frameLocal, method.getFrameParameter()));
            }
        }

        int evaluatedIndex = 0;
        for (int executionIndex = 0; executionIndex < node.getExecutionCount(); executionIndex++) {
            NodeExecutionData parameterExecution;
            if (executeWith != null && executionIndex < executeWith.size()) {
                parameterExecution = executeWith.get(executionIndex);
            } else {
                parameterExecution = node.getChildExecutions().get(executionIndex);
            }
            if (parameterExecution.isShortCircuit()) {
                if (evaluatedIndex < method.getEvaluatedCount()) {
                    TypeMirror targetType = method.getEvaluatedParameters().get(evaluatedIndex);
                    LocalVariable shortCircuit = frameState.getShortCircuit(parameterExecution);
                    if (shortCircuit != null) {
                        values.add(createTypeSafeReference(shortCircuit, targetType));
                    } else {
                        values.add(CodeTreeBuilder.createBuilder().defaultValue(targetType).build());
                    }
                    evaluatedIndex++;
                }
            }
            if (evaluatedIndex < method.getEvaluatedCount()) {
                TypeMirror targetType = method.getEvaluatedParameters().get(evaluatedIndex);
                LocalVariable value = frameState.getValue(parameterExecution);
                if (value != null) {
                    values.add(createTypeSafeReference(value, targetType));
                } else {
                    values.add(CodeTreeBuilder.createBuilder().defaultValue(targetType).build());
                }
                evaluatedIndex++;
            }
        }
        return values.toArray(new CodeTree[values.size()]);
    }

    private CodeTree callChildExecuteMethod(NodeExecutionData execution, ExecutableTypeData method, FrameState frameState) {
        return callMethod(CodeTreeBuilder.singleString(nodeFieldName(execution)), method.getMethod(), bindExecuteMethodParameters(execution, method, frameState));
    }

    private CodeTree callTemplateMethod(CodeTree receiver, TemplateMethod method, FrameState frameState) {
        CodeTree[] bindings = new CodeTree[method.getParameters().size()];

        int signatureIndex = 0;
        for (int i = 0; i < bindings.length; i++) {
            Parameter parameter = method.getParameters().get(i);

            if (parameter.getSpecification().isCached() && method instanceof SpecializationData) {
                bindings[i] = createCacheReference((SpecializationData) method, parameter);
            } else {
                LocalVariable var = frameState.get(parameter, signatureIndex);
                if (var == null) {
                    var = frameState.get(parameter.getLocalName());
                }

                if (var != null) {
                    bindings[i] = createTypeSafeReference(var, parameter.getType());
                }
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
        return SpecializationGroup.createFlat(reachableSpecializations);
    }

    private CodeTree expectOrCast(TypeMirror sourceType, ExecutableTypeData targetType, CodeTree content) {
        if (needsUnexpectedResultException(targetType)) {
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
        if (sourceType == null || ElementUtils.needsCastTo(sourceType, forType)) {
            expectedTypes.add(forType);
            return TypeSystemCodeGenerator.expect(typeSystem, forType, tree);
        }
        return tree;
    }

    private CodeExecutableElement createExecuteMethod(SpecializationData specialization, ExecutableTypeData executedType, FrameState frameState, boolean originalOverride) {
        TypeMirror returnType = executedType.getReturnType();

        if (specialization != null) {
            frameState.loadFastPathState(specialization);
        }

        String methodName;
        if (originalOverride && executedType.getMethod() != null) {
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

            if (executable.isVarArgs()) {
                ((CodeVariableElement) executable.getParameters().get(executable.getParameters().size() - 1)).setName(VARARGS_NAME);
            }

            renameOriginalParameters(executedType, executable, frameState);
        } else {
            executable = frameState.createMethod(modifiers(PUBLIC), returnType, methodName, FRAME_VALUE);
        }
        executable.getThrownTypes().clear();

        if (needsUnexpectedResultException(executedType)) {
            executable.getThrownTypes().add(context.getDeclaredType(UnexpectedResultException.class));
        }

        return executable;
    }

    private void renameOriginalParameters(ExecutableTypeData executedType, CodeExecutableElement executable, FrameState frameState) {
        // rename varargs parameter
        int evaluatedIndex = 0;
        for (int executionIndex = 0; executionIndex < node.getExecutionCount(); executionIndex++) {
            NodeExecutionData execution = node.getChildExecutions().get(executionIndex);
            if (execution.isShortCircuit()) {
                if (evaluatedIndex < executedType.getEvaluatedCount()) {
                    TypeMirror evaluatedType = executedType.getEvaluatedParameters().get(evaluatedIndex);
                    LocalVariable shortCircuit = frameState.getShortCircuit(execution);
                    if (shortCircuit != null) {
                        frameState.setShortCircuitValue(execution, renameExecutableTypeParameter(executable, executedType, evaluatedIndex, evaluatedType, shortCircuit));
                    }
                    evaluatedIndex++;
                }
            }
            if (evaluatedIndex < executedType.getEvaluatedCount()) {
                TypeMirror evaluatedType = executedType.getEvaluatedParameters().get(evaluatedIndex);
                LocalVariable value = frameState.getValue(execution);
                if (value != null) {
                    frameState.setValue(execution, renameExecutableTypeParameter(executable, executedType, evaluatedIndex, evaluatedType, value));
                }
                evaluatedIndex++;
            }
        }
    }

    private static LocalVariable renameExecutableTypeParameter(CodeExecutableElement method, ExecutableTypeData executedType, int evaluatedIndex, TypeMirror targetType, LocalVariable var) {
        int parameterIndex = executedType.getParameterIndex(evaluatedIndex);
        int varArgsIndex = executedType.getVarArgsIndex(parameterIndex);
        LocalVariable returnVar = var;
        if (varArgsIndex >= 0) {
            returnVar = returnVar.accessWith(CodeTreeBuilder.singleString(VARARGS_NAME + "[" + varArgsIndex + "]"));
        } else {
            ((CodeVariableElement) method.getParameters().get(parameterIndex)).setName(returnVar.getName());
        }
        if (!isObject(targetType)) {
            returnVar = returnVar.newType(targetType);
        }
        return returnVar;
    }

    private boolean needsUnexpectedResultException(ExecutableTypeData executedType) {
        if (!executedType.hasUnexpectedValue(context)) {
            return false;
        }

        if (isSubtypeBoxed(context, executeAndSpecializeType.getReturnType(), executedType.getReturnType())) {
            return false;
        } else {
            return true;
        }
    }

    private LocalVariable resolveShortCircuit(SpecializationData specialization, NodeExecutionData execution, FrameState frameState) {
        LocalVariable shortCircuit = null;
        if (execution.isShortCircuit()) {
            shortCircuit = frameState.getShortCircuit(execution);

            if (shortCircuit == null) {
                SpecializationData resolvedSpecialization = specialization;
                if (specialization == null) {
                    resolvedSpecialization = node.getGenericSpecialization();
                }
                ShortCircuitData shortCircuitData = resolvedSpecialization.getShortCircuits().get(calculateShortCircuitIndex(execution));
                CodeTree access = callTemplateMethod(null, shortCircuitData, frameState);
                shortCircuit = frameState.createShortCircuitValue(execution).accessWith(access);
            } else {
                CodeTree access = shortCircuit.createReference();
                shortCircuit = shortCircuit.nextName().accessWith(access);
            }
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

    private CodeTree createFastPathExecute(CodeTreeBuilder parent, final ExecutableTypeData forType, SpecializationData specialization, FrameState frameState) {
        CodeTreeBuilder builder = parent.create();
        int ifCount = 0;
        if (specialization.isFallback()) {
            builder.startIf().startCall(METHOD_FALLBACK_GUARD);
            if (node.isFrameUsedByAnyGuard()) {
                if (frameState.get(FRAME_VALUE) != null) {
                    builder.string(FRAME_VALUE);
                } else {
                    builder.nullLiteral();
                }
            }
            if (fallbackNeedsState) {
                builder.string(STATE_VALUE);
            }
            frameState.addReferencesTo(builder);

            builder.end();
            builder.end();
            builder.startBlock();
            ifCount++;
        }
        builder.tree(createExecute(builder, frameState, forType, specialization, NodeExecutionMode.FAST_PATH));
        builder.end(ifCount);
        return builder.build();
    }

    private CodeTree createExecute(CodeTreeBuilder parent, FrameState frameState, final ExecutableTypeData forType, SpecializationData specialization, NodeExecutionMode mode) {
        CodeTreeBuilder builder = parent.create();

        if (mode.isSlowPath()) {
            builder.statement("lock.unlock()");
            builder.statement("hasLock = false");
        }

        if (specialization.getMethod() == null) {
            builder.tree(createThrowUnsupported(builder, frameState));
        } else {
            if (isVoid(specialization.getMethod().getReturnType())) {
                builder.statement(callTemplateMethod(null, specialization, frameState));
                if (isVoid(forType.getReturnType())) {
                    builder.returnStatement();
                } else {
                    builder.startReturn().defaultValue(forType.getReturnType()).end();
                }
            } else {
                builder.startReturn();
                builder.tree(expectOrCast(specialization.getReturnType().getType(), forType, callTemplateMethod(null, specialization, frameState)));
                builder.end();
            }
        }

        return createCatchRewriteException(builder, specialization, forType, frameState, builder.build(), mode);
    }

    private static class IfTriple {

        private CodeTree prepare;
        private CodeTree condition;
        private CodeTree statements;

        IfTriple(CodeTree prepare, CodeTree condition, CodeTree statements) {
            this.prepare = prepare;
            this.condition = condition;
            this.statements = statements;
        }

        private boolean canBeMerged(IfTriple triple) {
            boolean prepareSet = !isEmpty(triple.prepare) || !isEmpty(prepare);
            boolean conditionSet = !isEmpty(triple.condition) || !isEmpty(condition);
            boolean statementsSet = !isEmpty(triple.statements) || !isEmpty(statements);
            return conditionSet ^ (prepareSet || statementsSet);
        }

        private static boolean isEmpty(CodeTree e) {
            return e == null || e.isEmpty();
        }

        @Override
        public String toString() {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
            b.startGroup();
            if (!isEmpty(prepare)) {
                b.tree(prepare);
            }
            if (!isEmpty(condition)) {
                b.startIf().tree(condition).end().startBlock();
            }
            if (!isEmpty(statements)) {
                b.tree(statements);
            }

            if (!isEmpty(condition)) {
                b.end();
            }
            b.end();
            return b.build().toString();
        }

        private static IfTriple merge(String conditionSep, Set<IfTriple> triples) {
            if (triples.isEmpty()) {
                throw new AssertionError();
            }
            if (triples.size() == 1) {
                return triples.iterator().next();
            }
            CodeTree[] prepareTrees = new CodeTree[triples.size()];
            CodeTree[] conditionTrees = new CodeTree[triples.size()];
            CodeTree[] statementTrees = new CodeTree[triples.size()];
            int index = 0;
            for (IfTriple triple : triples) {
                prepareTrees[index] = triple.prepare;
                conditionTrees[index] = triple.condition;
                statementTrees[index] = triple.statements;
                index++;
            }
            return new IfTriple(combineTrees(null, prepareTrees),
                            combineTrees(conditionSep, conditionTrees),
                            combineTrees(null, statementTrees));
        }

        public static List<IfTriple> optimize(List<IfTriple> triples) {
            List<IfTriple> newTriples = new ArrayList<>();
            Set<IfTriple> mergable = new LinkedHashSet<>();
            IfTriple prev = null;
            for (IfTriple triple : triples) {
                if (prev != null) {
                    if (prev.canBeMerged(triple)) {
                        mergable.add(triple);
                    } else {
                        newTriples.add(merge(" && ", mergable));
                        mergable.clear();
                    }
                }
                prev = triple;
                mergable.add(prev);
            }
            if (prev != null) {
                newTriples.add(merge(" && ", mergable));
            }
            return newTriples;
        }

        public static int materialize(CodeTreeBuilder builder, List<IfTriple> triples) {
            int blockCount = 0;
            boolean otherPrepare = false;
            for (IfTriple triple : triples) {
                if (triple.prepare != null && !triple.prepare.isEmpty()) {
                    if (!otherPrepare) {
                        if (blockCount == 0) {
                            builder.startBlock();
                            blockCount++;
                        }
                        otherPrepare = true;
                    }
                    builder.tree(triple.prepare);
                }
                if (triple.condition != null && !triple.condition.isEmpty()) {
                    builder.startIf().tree(triple.condition).end().startBlock();
                    blockCount++;
                }
                if (triple.statements != null && !triple.statements.isEmpty()) {
                    builder.tree(triple.statements);
                }
            }
            return blockCount;
        }

    }

    private CodeTree visitSpecializationGroup(CodeTreeBuilder parent, SpecializationGroup group, ExecutableTypeData forType, FrameState frameState, List<SpecializationData> allowedSpecializations,
                    NodeExecutionMode mode) {
        CodeTreeBuilder builder = parent.create();

        boolean hasFallthrough = false;
        boolean hasImplicitCast = false;
        List<IfTriple> cachedTriples = new ArrayList<>();
        for (TypeGuard guard : group.getTypeGuards()) {
            IfTriple triple = createTypeCheckOrCast(frameState, group, guard, mode, false, true);
            if (triple != null) {
                cachedTriples.add(triple);
            }
            hasImplicitCast = hasImplicitCast || node.getTypeSystem().hasImplicitSourceTypes(guard.getType());
            if (!mode.isGuardFallback()) {
                triple = createTypeCheckOrCast(frameState, group, guard, mode, true, true);
                if (triple != null) {
                    cachedTriples.add(triple);
                }
            }
        }

        SpecializationData specialization = group.getSpecialization();
        SpecializationData[] specializations = group.collectSpecializations().toArray(new SpecializationData[0]);
        List<GuardExpression> guardExpressions = new ArrayList<>(group.getGuards());

        // for specializations with multiple instances we can move certain guards
        // out of the loop.
        if (specialization != null && specialization.hasMultipleInstances()) {
            List<GuardExpression> unboundGuards = new ArrayList<>();
            for (GuardExpression guard : guardExpressions) {
                if (!specialization.isGuardBoundWithCache(guard)) {
                    unboundGuards.add(guard);
                } else {
                    // we need to stop as we need to ensure guard execution order
                    break;
                }
            }
            cachedTriples.addAll(createMethodGuardCheck(frameState, group, unboundGuards, mode));
            guardExpressions.removeAll(unboundGuards);
        }

        boolean useSpecializationClass = specialization != null && useSpecializationClass(specialization);

        if (mode.isFastPath()) {
            int ifCount = 0;
            final boolean stateGuaranteed = group.isLast() && allowedSpecializations != null && allowedSpecializations.size() == 1 &&
                            group.getAllSpecializations().size() == allowedSpecializations.size();
            if ((!group.isEmpty() || specialization != null)) {
                CodeTree stateCheck = state.createContains(frameState, specializations);
                CodeTree stateGuard = null;
                CodeTree assertCheck = null;
                if (stateGuaranteed) {
                    assertCheck = CodeTreeBuilder.createBuilder().startAssert().tree(stateCheck).end().build();
                } else {
                    stateGuard = stateCheck;
                }
                cachedTriples.add(0, new IfTriple(null, stateGuard, assertCheck));
            }

            ifCount += IfTriple.materialize(builder, IfTriple.optimize(cachedTriples));
            cachedTriples = new ArrayList<>(); // reset current triples

            String specializationLocalName = null;
            if (useSpecializationClass) {
                specializationLocalName = createSpecializationLocalName(specialization);
                builder.declaration(createSpecializationTypeName(specialization), specializationLocalName, CodeTreeBuilder.singleString(createSpecializationFieldName(specialization)));
                if (specialization.getMaximumNumberOfInstances() > 1) {
                    builder.startWhile();
                } else {
                    builder.startIf();
                }
                builder.string(specializationLocalName, " != null");
                builder.end();
                builder.startBlock();
                ifCount++;
            }
            if (specialization != null) {
                if (!specialization.getAssumptionExpressions().isEmpty()) {
                    builder.tree(createFastPathAssumptionCheck(builder, specialization, forType, frameState));
                }
            }
            cachedTriples = createMethodGuardCheck(frameState, group, guardExpressions, mode);

            int innerIfCount = IfTriple.materialize(builder, IfTriple.optimize(cachedTriples));

            SpecializationGroup prev = null;
            for (SpecializationGroup child : group.getChildren()) {
                if (prev != null && !prev.hasFallthrough()) {
                    break;
                }
                builder.tree(visitSpecializationGroup(builder, child, forType, frameState.copy(), allowedSpecializations, mode));
            }
            if (specialization != null && (prev == null || prev.hasFallthrough())) {
                builder.tree(createFastPathExecute(builder, forType, specialization, frameState));
            }
            builder.end(innerIfCount);
            hasFallthrough |= innerIfCount > 0;

            if (useSpecializationClass && specialization.getMaximumNumberOfInstances() > 1) {
                String name = createSpecializationLocalName(specialization);
                builder.startStatement().string(name, " = ", name, ".next_").end();
            }

            builder.end(ifCount);
            hasFallthrough |= ifCount > 0;

        } else if (mode.isSlowPath()) {

            if (specialization != null && mayBeExcluded(specialization)) {
                CodeTree excludeCheck = exclude.createNotContains(frameState, specializations);
                cachedTriples.add(0, new IfTriple(null, excludeCheck, null));
            }

            int outerIfCount = 0;
            if (specialization == null) {
                cachedTriples.addAll(createMethodGuardCheck(frameState, group, guardExpressions, mode));

                outerIfCount += IfTriple.materialize(builder, IfTriple.optimize(cachedTriples));

                SpecializationGroup prev = null;
                for (SpecializationGroup child : group.getChildren()) {
                    if (prev != null && !prev.hasFallthrough()) {
                        break;
                    }
                    builder.tree(visitSpecializationGroup(builder, child, forType, frameState.copy(), allowedSpecializations, mode));
                    prev = child;
                }
            } else {
                outerIfCount += IfTriple.materialize(builder, IfTriple.optimize(cachedTriples));

                String countName = specialization != null ? "count" + specialization.getIndex() + "_" : null;
                boolean needsDuplicationCheck = specialization.hasMultipleInstances();
                String duplicateFoundName = specialization.getId() + "_duplicateFound_";

                int innerIfCount = 0;

                String specializationLocalName = createSpecializationLocalName(specialization);
                if (needsDuplicationCheck) {
                    builder.tree(createDuplicationCheck(builder, frameState, group, guardExpressions, useSpecializationClass, countName, duplicateFoundName,
                                    specializationLocalName));

                    builder.startIf();
                    if (useSpecializationClass) {
                        // we reuse the specialization class local name instead of a duplicate found
                        // name
                        builder.string(createSpecializationLocalName(specialization), " == null");
                    } else {
                        builder.string("!", duplicateFoundName);
                    }
                    builder.end().startBlock();
                    innerIfCount++;
                }

                List<IfTriple> innerTripples = createMethodGuardCheck(frameState, group, guardExpressions, mode);

                List<AssumptionExpression> assumptions = specialization.getAssumptionExpressions();
                if (!assumptions.isEmpty()) {
                    for (AssumptionExpression assumption : assumptions) {
                        innerTripples.addAll(createAssumptionSlowPathTriples(frameState, group, assumption));
                    }
                }

                if (specialization.hasMultipleInstances()) {
                    DSLExpression limit = specialization.getLimitExpression();
                    CodeTree limitExpression = DSLExpressionGenerator.write(limit, null, castBoundTypes(bindExpressionValues(frameState, limit, specialization)));
                    CodeTree limitCondition = CodeTreeBuilder.createBuilder().string(countName).string(" < ").tree(limitExpression).build();
                    innerTripples.add(new IfTriple(null, limitCondition, null));
                }

                innerIfCount += IfTriple.materialize(builder, IfTriple.optimize(innerTripples));
                builder.tree(createSpecialize(builder, frameState, specialization, useSpecializationClass, needsDuplicationCheck));

                if (needsDuplicationCheck) {
                    hasFallthrough = true;
                    if (!useSpecializationClass) {
                        builder.startStatement().string(duplicateFoundName, " = true").end();
                    }
                    builder.end(innerIfCount);
                    // need to ensure that we update the implicit cast specializations on duplicates
                    CodeTree updateImplicitCast = createUpdateImplicitCastState(builder, frameState, specialization);
                    if (updateImplicitCast != null) {
                        builder.startElseBlock();
                        builder.tree(createUpdateImplicitCastState(builder, frameState, specialization));
                        builder.tree(state.createSet(frameState, new SpecializationData[]{specialization}, true, true));
                        builder.end();
                    }

                    builder.startIf();
                    if (useSpecializationClass) {
                        builder.string(createSpecializationLocalName(specialization), " != null");
                    } else {
                        builder.string(duplicateFoundName);
                    }
                    builder.end().startBlock();

                    builder.tree(createExecute(builder, frameState, executeAndSpecializeType, specialization, mode));
                    builder.end();
                } else {
                    builder.tree(createExecute(builder, frameState, executeAndSpecializeType, specialization, mode));
                    builder.end(innerIfCount);
                    hasFallthrough |= innerIfCount > 0;
                }
            }

            builder.end(outerIfCount);
            hasFallthrough |= outerIfCount > 0;

        } else if (mode.isGuardFallback()) {
            int ifCount = 0;

            String specializationLocalName = null;
            boolean useClass = useSpecializationClass && specialization.getMaximumNumberOfInstances() > 1;
            if (useClass) {
                specializationLocalName = createSpecializationLocalName(specialization);
                ifCount += IfTriple.materialize(builder, IfTriple.optimize(cachedTriples));
                cachedTriples.clear();

                builder.declaration(createSpecializationTypeName(specialization), specializationLocalName,
                                CodeTreeBuilder.singleString(createSpecializationFieldName(specialization)));
                builder.startWhile();
                builder.string(specializationLocalName, " != null");
                builder.end();
                builder.startBlock();
                hasFallthrough = true;
            }

            int innerIfCount = 0;
            cachedTriples.addAll(createMethodGuardCheck(frameState, group, guardExpressions, mode));
            cachedTriples.addAll(createAssumptionCheckTriples(specialization));

            cachedTriples = IfTriple.optimize(cachedTriples);

            if (!useClass && specialization != null && !hasImplicitCast) {
                IfTriple singleCondition = null;
                if (cachedTriples.size() == 1) {
                    singleCondition = cachedTriples.get(0);
                }
                if (singleCondition != null) {
                    int index = cachedTriples.indexOf(singleCondition);
                    CodeTree stateCheck = state.createNotContains(frameState, specializations);
                    cachedTriples.set(index, new IfTriple(singleCondition.prepare, combineTrees(" && ", stateCheck, singleCondition.condition), singleCondition.statements));
                    fallbackNeedsState = true;
                }
            }

            innerIfCount += IfTriple.materialize(builder, cachedTriples);

            SpecializationGroup prev = null;
            for (SpecializationGroup child : group.getChildren()) {
                if (prev != null && !prev.hasFallthrough()) {
                    break;
                }
                builder.tree(visitSpecializationGroup(builder, child, forType, frameState.copy(), allowedSpecializations, mode));
                prev = child;
            }

            if (specialization != null) {
                builder.returnFalse();
            }

            builder.end(innerIfCount);

            if (useClass) {
                builder.startStatement().string(specializationLocalName, " = ", specializationLocalName, ".next_").end();
                builder.end();
            }

            builder.end(ifCount);
            hasFallthrough |= ifCount > 0 || innerIfCount > 0;

        } else {
            throw new AssertionError("unexpected path");
        }

        group.setFallthrough(hasFallthrough);

        return builder.build();
    }

    private List<IfTriple> createAssumptionCheckTriples(SpecializationData specialization) {
        if (specialization == null || specialization.getAssumptionExpressions().isEmpty()) {
            return Collections.emptyList();
        }

        List<IfTriple> triples = new ArrayList<>();
        List<AssumptionExpression> assumptions = specialization.getAssumptionExpressions();
        for (AssumptionExpression assumption : assumptions) {
            CodeTree assumptionGuard = createAssumptionGuard(assumption, createAssumptionReference(specialization, assumption));
            triples.add(new IfTriple(null, assumptionGuard, null));
        }
        return triples;
    }

    private List<IfTriple> createAssumptionSlowPathTriples(FrameState frameState, SpecializationGroup group, AssumptionExpression assumption) throws AssertionError {
        List<IfTriple> triples = new ArrayList<>();
        LocalVariable var = frameState.get(assumption.getId());
        CodeTree declaration = null;
        if (var == null) {
            triples.addAll(initializeCaches(frameState, group, group.getSpecialization().getBoundCaches(assumption.getExpression()), NodeExecutionMode.SLOW_PATH));

            CodeTree assumptionExpressions = DSLExpressionGenerator.write(assumption.getExpression(), null,
                            castBoundTypes(bindExpressionValues(frameState, assumption.getExpression(), group.getSpecialization())));
            String name = createAssumptionFieldName(group.getSpecialization(), assumption);
            var = new LocalVariable(assumption.getExpression().getResolvedType(), name.substring(0, name.length() - 1), null);
            frameState.set(assumption.getId(), var);
            declaration = var.createDeclaration(assumptionExpressions);
        }
        triples.add(new IfTriple(declaration, createAssumptionGuard(assumption, var.createReference()), null));
        return triples;
    }

    private CodeTree createDuplicationCheck(CodeTreeBuilder parent, FrameState frameState, SpecializationGroup group, List<GuardExpression> guardExpressions,
                    boolean useSpecializationClass, String countName, String duplicateFoundName, String specializationLocalName) {
        SpecializationData specialization = group.getSpecialization();
        CodeTreeBuilder builder = parent.create();
        builder.declaration("int", countName, CodeTreeBuilder.singleString("0"));
        if (useSpecializationClass) {
            builder.declaration(createSpecializationTypeName(specialization), specializationLocalName,
                            CodeTreeBuilder.singleString(createSpecializationFieldName(specialization)));
        } else {
            builder.declaration("boolean", duplicateFoundName, CodeTreeBuilder.singleString("false"));
        }

        builder.startIf().tree(state.createContains(frameState, new Object[]{specialization})).end().startBlock();

        if (useSpecializationClass) {
            builder.startWhile().string(specializationLocalName, " != null").end().startBlock();
        }

        List<IfTriple> duplicationtriples = new ArrayList<>();
        duplicationtriples.addAll(createMethodGuardCheck(frameState, group, guardExpressions, NodeExecutionMode.FAST_PATH));
        duplicationtriples.addAll(createAssumptionCheckTriples(specialization));
        int duplicationIfCount = IfTriple.materialize(builder, IfTriple.optimize(duplicationtriples));
        if (useSpecializationClass) {
            builder.statement("break");
        } else {
            builder.startStatement().string(duplicateFoundName, " = true").end();
        }
        builder.end(duplicationIfCount);

        if (useSpecializationClass) {
            if (specialization.getMaximumNumberOfInstances() > 1) {
                builder.startStatement().string(specializationLocalName, " = ", specializationLocalName, ".next_").end();
            } else {
                builder.statement(specializationLocalName + " = null");
            }
            builder.statement(countName + "++");
            builder.end();
        } else {
            builder.statement(countName + "++");
        }

        builder.end();
        return builder.build();
    }

    private CodeTree createSpecialize(CodeTreeBuilder parent, FrameState frameState, SpecializationData specialization, boolean useSpecializationClass, boolean needsDuplicationCheck)
                    throws AssertionError {
        CodeTreeBuilder builder = parent.create();
        if (useSpecializationClass) {
            // when using a specialization class we want to initialize the caches
            for (CacheExpression cache : specialization.getCaches()) {
                String cacheFieldName = createFieldName(specialization, cache.getParameter());
                LocalVariable evaluatedVar = frameState.get(cacheFieldName);
                if (evaluatedVar == null) {
                    CodeTree initializer = DSLExpressionGenerator.write(cache.getExpression(), null, castBoundTypes(bindExpressionValues(frameState, cache.getExpression(), specialization)));
                    TypeMirror type = cache.getExpression().getResolvedType();
                    LocalVariable var = new LocalVariable(type, cacheFieldName.substring(0, cacheFieldName.length() - 1), null);
                    frameState.set(cacheFieldName, var);
                    builder.tree(var.createDeclaration(initializer));
                }
            }
            builder.startStatement();
            if (!needsDuplicationCheck) {
                // without duplication check the specialization type is not yet declared.
                builder.string(createSpecializationTypeName(specialization)).string(" ");
            }
            builder.string(createSpecializationLocalName(specialization), " = ");
            builder.startNew(createSpecializationTypeName(specialization));
            if (specialization.getMaximumNumberOfInstances() > 1) {
                builder.string(createSpecializationFieldName(specialization));
            }
            for (CacheExpression cache : specialization.getCaches()) {
                builder.tree(frameState.get(createFieldName(specialization, cache.getParameter())).createReference());
            }
            for (AssumptionExpression assumption : specialization.getAssumptionExpressions()) {
                builder.tree(frameState.get(assumption.getId()).createReference());
            }
            builder.end().end(); // statement, new
        } else {
            for (CacheExpression cache : specialization.getCaches()) {
                LocalVariable evaluatedVar = frameState.get(createFieldName(specialization, cache.getParameter()));
                CodeTree cacheValue;
                if (evaluatedVar == null) {
                    cacheValue = DSLExpressionGenerator.write(cache.getExpression(), null, castBoundTypes(bindExpressionValues(frameState, cache.getExpression(), specialization)));
                } else {
                    cacheValue = evaluatedVar.createReference();
                }

                Parameter parameter = cache.getParameter();
                if (ElementUtils.isAssignable(parameter.getType(), context.getType(Node.class)) || ElementUtils.isAssignable(parameter.getType(), context.getType(Node[].class))) {
                    cacheValue = builder.create().startCall("super", "insert").tree(cacheValue).end().build();
                }
                String name = createFieldName(specialization, cache.getParameter());
                builder.startStatement().string("this.").string(name).string(" = ").tree(cacheValue).end();
            }
            for (AssumptionExpression assumption : specialization.getAssumptionExpressions()) {
                LocalVariable var = frameState.get(assumption.getId());
                String name = createAssumptionFieldName(specialization, assumption);
                builder.startStatement();
                builder.string("this.", name, " = ").tree(var.createReference());
                builder.end();
            }
        }

        List<SpecializationData> excludesSpecializations = new ArrayList<>();
        for (SpecializationData otherSpeciailzation : reachableSpecializations) {
            if (otherSpeciailzation == specialization) {
                continue;
            }
            if (otherSpeciailzation.getExcludedBy().contains(specialization)) {
                excludesSpecializations.add(otherSpeciailzation);
            }
        }

        if (useSpecializationClass) {
            boolean isNode = specializationClassIsNode(specialization);
            builder.startStatement().string("this.", createSpecializationFieldName(specialization), " = ");
            if (isNode) {
                builder.startCall("super", "insert").string(createSpecializationLocalName(specialization)).end();
            } else {
                builder.string(createSpecializationLocalName(specialization));
            }

            builder.end();
        }

        if (!excludesSpecializations.isEmpty()) {
            SpecializationData[] excludesArray = excludesSpecializations.toArray(new SpecializationData[0]);
            builder.tree(exclude.createSet(frameState, excludesArray, true, true));

            for (SpecializationData excludes : excludesArray) {
                if (useSpecializationClass(excludes)) {
                    builder.statement("this." + createSpecializationFieldName(excludes) + " = null");
                }
            }
            builder.tree((state.createSet(frameState, excludesArray, false, false)));
        }

        CodeTree updateImplicitCast = createUpdateImplicitCastState(builder, frameState, specialization);
        if (updateImplicitCast != null) {
            builder.tree(createUpdateImplicitCastState(builder, frameState, specialization));
        }

        builder.tree(state.createSet(frameState, new SpecializationData[]{specialization}, true, true));
        return builder.build();
    }

    private CodeTree createUpdateImplicitCastState(CodeTreeBuilder parent, FrameState frameState, SpecializationData specialization) {
        CodeTreeBuilder builder = null;
        int signatureIndex = 0;
        for (Parameter p : specialization.getSignatureParameters()) {
            TypeMirror targetType = p.getType();
            TypeMirror polymorphicType = node.getPolymorphicSpecialization().findParameterOrDie(p.getSpecification().getExecution()).getType();
            if (typeSystem.hasImplicitSourceTypes(targetType) && needsCastTo(polymorphicType, targetType)) {
                String implicitFieldName = createImplicitTypeStateLocalName(p);
                if (builder == null) {
                    builder = parent.create();
                }
                builder.tree(state.createSetInteger(frameState, new TypeGuard(p.getType(), signatureIndex), CodeTreeBuilder.singleString(implicitFieldName)));
            }
            signatureIndex++;
        }
        return builder == null ? null : builder.build();
    }

    private CodeTree createAssumptionGuard(AssumptionExpression assumption, CodeTree assumptionValue) {
        CodeTree assumptionGuard = CodeTreeBuilder.createBuilder().startCall("isValid_").tree(assumptionValue).end().build();
        isValidSignatures.put(ElementUtils.getQualifiedName(assumption.getExpression().getResolvedType()), assumption.getExpression().getResolvedType());
        return assumptionGuard;
    }

    private static CodeTree combineTrees(String sep, CodeTree... trees) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        String s = "";
        for (CodeTree tree : trees) {
            if (tree != null && !tree.isEmpty()) {
                if (sep != null) {
                    builder.string(s);
                }
                builder.tree(tree);
                s = sep;
            }
        }
        return builder.build();
    }

    private CodeTree createFastPathAssumptionCheck(CodeTreeBuilder parent, SpecializationData specialization, ExecutableTypeData forType, FrameState frameState)
                    throws AssertionError {
        CodeTreeBuilder builder = parent.create();
        builder.startIf();
        String sep = "";
        for (AssumptionExpression assumption : specialization.getAssumptionExpressions()) {
            builder.string(sep);
            builder.string("!");

            builder.startCall("isValid_").tree(createAssumptionReference(specialization, assumption)).end();
            isValidSignatures.put(ElementUtils.getQualifiedName(assumption.getExpression().getResolvedType()), assumption.getExpression().getResolvedType());
            sep = " || ";
        }
        builder.end().startBlock();
        builder.tree(createTransferToInterpreterAndInvalidate());
        builder.tree(createRemoveThis(builder, frameState, forType, specialization));
        builder.end();
        return builder.build();
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

    private ExecutableTypeData resolveTargetExecutable(NodeExecutionData execution, TypeMirror target) {
        NodeChildData child = execution.getChild();
        if (child == null) {
            return null;
        }
        ExecutableTypeData targetExecutable = child.findExecutableType(target);
        if (targetExecutable == null) {
            targetExecutable = child.findAnyGenericExecutableType(context);
        }
        return targetExecutable;
    }

    private CodeTree createCatchRewriteException(CodeTreeBuilder parent, SpecializationData specialization, ExecutableTypeData forType, FrameState frameState, CodeTree execution,
                    NodeExecutionMode mode) {
        if (specialization.getExceptions().isEmpty()) {
            return execution;
        }
        CodeTreeBuilder builder = parent.create();
        builder.startTryBlock();
        builder.tree(execution);
        boolean nonSlowPath = false;
        TypeMirror[] exceptionTypes = new TypeMirror[specialization.getExceptions().size()];
        for (int i = 0; i < exceptionTypes.length; i++) {
            TypeMirror type = specialization.getExceptions().get(i).getJavaClass();
            if (!ElementUtils.isAssignable(type, context.getType(SlowPathException.class)) && !ElementUtils.isAssignable(type, context.getType(ArithmeticException.class))) {
                nonSlowPath = true;
            }
            exceptionTypes[i] = type;
        }
        builder.end().startCatchBlock(exceptionTypes, "ex");
        if (nonSlowPath) {
            builder.tree(createTransferToInterpreterAndInvalidate());
        } else {
            builder.lineComment("implicit transferToInterpreterAndInvalidate()");
        }

        builder.tree(createExcludeThis(builder, frameState, forType, specialization, mode));

        builder.end();
        return builder.build();
    }

    private Map<SpecializationData, CodeExecutableElement> removeThisMethods = new HashMap<>();

    private CodeTree createExcludeThis(CodeTreeBuilder parent, FrameState frameState, ExecutableTypeData forType, SpecializationData specialization, NodeExecutionMode mode) {
        CodeTreeBuilder builder = parent.create();

        // slow path might be already already locked
        if (!mode.isSlowPath()) {
            builder.declaration(context.getType(Lock.class), "lock", "getLock()");
        }

        builder.statement("lock.lock()");
        builder.startTryBlock();
        // pass null frame state to ensure values are reloaded.
        builder.tree(this.exclude.createSet(null, Arrays.asList(specialization).toArray(new SpecializationData[0]), true, true));
        // single instance remove
        builder.tree((state.createSet(null, Arrays.asList(specialization).toArray(new SpecializationData[0]), false, true)));
        if (useSpecializationClass(specialization)) {
            String fieldName = createSpecializationFieldName(specialization);
            builder.statement("this." + fieldName + " = null");
        }
        builder.end().startFinallyBlock();
        builder.statement("lock.unlock()");
        builder.end();
        builder.tree(createCallExecuteAndSpecialize(forType, frameState));
        builder.end();
        return builder.build();
    }

    private CodeTree createRemoveThis(CodeTreeBuilder parent, FrameState frameState, ExecutableTypeData forType, SpecializationData specialization) {
        CodeExecutableElement method = removeThisMethods.get(specialization);
        String specializationLocalName = createSpecializationLocalName(specialization);
        boolean useSpecializationClass = useSpecializationClass(specialization);
        if (method == null) {
            method = new CodeExecutableElement(context.getType(void.class), "remove" + specialization.getId() + "_");
            if (useSpecializationClass) {
                method.addParameter(new CodeVariableElement(context.getType(Object.class), specializationLocalName));
            }
            CodeTreeBuilder builder = method.createBuilder();
            builder.declaration(context.getType(Lock.class), "lock", "getLock()");
            builder.statement("lock.lock()");
            builder.startTryBlock();
            String fieldName = createSpecializationFieldName(specialization);
            if (!useSpecializationClass || specialization.getMaximumNumberOfInstances() == 1) {
                // single instance remove
                builder.tree((state.createSet(null, Arrays.asList(specialization).toArray(new SpecializationData[0]), false, true)));
                if (useSpecializationClass) {
                    builder.statement("this." + fieldName + " = null");
                }
            } else {
                // multi instance remove
                String typeName = createSpecializationTypeName(specialization);
                boolean specializedIsNode = specializationClassIsNode(specialization);
                builder.declaration(typeName, "prev", "null");
                builder.declaration(typeName, "cur", "this." + fieldName);
                builder.startWhile();
                builder.string("cur != null");
                builder.end().startBlock();
                builder.startIf().string("cur == ").string(specializationLocalName).end().startBlock();
                builder.startIf().string("prev == null").end().startBlock();
                builder.statement("this." + fieldName + " = cur.next_");
                if (specializedIsNode) {
                    builder.statement("this.adoptChildren()");
                }
                builder.end().startElseBlock();
                builder.statement("prev.next_ = cur.next_");
                if (specializedIsNode) {
                    builder.statement("prev.adoptChildren()");
                }
                builder.end();
                builder.statement("break");
                builder.end(); // if block
                builder.statement("prev = cur");
                builder.statement("cur = cur.next_");
                builder.end(); // while block

                builder.startIf().string("this." + fieldName).string(" == null").end().startBlock();
                builder.tree((state.createSet(null, Arrays.asList(specialization).toArray(new SpecializationData[0]), false, true)));
                builder.end();
            }

            builder.end().startFinallyBlock();
            builder.statement("lock.unlock()");
            builder.end();
            removeThisMethods.put(specialization, method);
        }
        CodeTreeBuilder builder = parent.create();
        builder.startStatement().startCall(method.getSimpleName().toString());
        if (useSpecializationClass) {
            builder.string(specializationLocalName);
        }
        builder.end().end();
        builder.tree(createCallExecuteAndSpecialize(forType, frameState));
        return builder.build();
    }

    private CodeTree createCallExecute(ExecutableTypeData forType, ExecutableTypeData targetType, FrameState frameState) {
        TypeMirror returnType = targetType.getReturnType();

        List<CodeTree> bindings = new ArrayList<>();

        List<TypeMirror> sourceTypes = forType.getSignatureParameters();
        List<TypeMirror> targetTypes = targetType.getSignatureParameters();
        if (sourceTypes.size() != targetTypes.size()) {
            throw new IllegalArgumentException();
        }

        if (targetType.getFrameParameter() != null) {
            LocalVariable parameterLocal = frameState.get(FRAME_VALUE);
            TypeMirror parameterTargetType = targetType.getFrameParameter();
            if (parameterLocal == null) {
                bindings.add(CodeTreeBuilder.createBuilder().defaultValue(parameterTargetType).build());
            } else {
                bindings.add(parameterLocal.createReference());
            }
        }

        for (int i = 0; i < sourceTypes.size(); i++) {
            LocalVariable parameterLocal = frameState.getValue(i);
            TypeMirror parameterTargetType = targetTypes.get(i);
            if (parameterLocal == null) {
                bindings.add(CodeTreeBuilder.createBuilder().defaultValue(parameterTargetType).build());
            } else {
                bindings.add(parameterLocal.createReference());
            }
        }

        CodeTree call = callMethod(null, targetType.getMethod(), bindings.toArray(new CodeTree[0]));
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder = builder.create();
        if (isVoid(forType.getReturnType())) {
            builder.statement(call);
            builder.returnStatement();
        } else {
            builder.startReturn();
            builder.tree(expectOrCast(returnType, forType, call));
            builder.end();
        }
        return builder.build();
    }

    private CodeTree createCallExecuteAndSpecialize(ExecutableTypeData forType, FrameState frameState) {
        TypeMirror returnType = node.getPolymorphicSpecialization().getReturnType().getType();
        String frame = null;
        if (needsFrame(reachableSpecializations)) {
            frame = FRAME_VALUE;
        }

        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.startCall("executeAndSpecialize");
        frameState.addReferencesTo(builder, frame);
        builder.end();
        CodeTree call = builder.build();

        builder = builder.create();
        if (isVoid(forType.getReturnType())) {
            builder.statement(call);
            builder.returnStatement();
        } else {
            builder.startReturn();
            builder.tree(expectOrCast(returnType, forType, call));
            builder.end();
        }
        return builder.build();
    }

    private List<IfTriple> createMethodGuardCheck(FrameState frameState, SpecializationGroup group, List<GuardExpression> guardExpressions, NodeExecutionMode mode) {
        List<IfTriple> triples = new ArrayList<>();
        for (GuardExpression guard : guardExpressions) {
            triples.addAll(initializeCaches(frameState, group, group.getSpecialization().getBoundCaches(guard.getExpression()), mode));
            triples.addAll(initializeCasts(frameState, group, guard.getExpression(), mode));
            triples.add(createMethodGuardCheck(frameState, group.getSpecialization(), guard, mode.isFastPath() || mode.isGuardFallback()));
        }
        return triples;
    }

    private List<IfTriple> initializeCaches(FrameState frameState, SpecializationGroup group, Set<CacheExpression> caches, NodeExecutionMode mode) {
        if (mode != NodeExecutionMode.SLOW_PATH || group.getSpecialization() == null) {
            return Collections.emptyList();
        }
        List<IfTriple> triples = new ArrayList<>();
        if (!caches.isEmpty()) {
            // preinitialize caches for guards in local variables
            for (CacheExpression cache : caches) {
                triples.addAll(initializeCasts(frameState, group, cache.getExpression(), mode));
                IfTriple triple = createCacheInitializer(frameState, group.getSpecialization(), cache);
                if (triple != null) {
                    triples.add(triple);
                }
            }
        }
        return triples;
    }

    private static IfTriple createCacheInitializer(FrameState frameState, SpecializationData specialization, CacheExpression cache) {
        String name = createFieldName(specialization, cache.getParameter());
        // already initialized
        if (frameState.get(name) != null) {
            return null;
        }
        CodeTree initializer = DSLExpressionGenerator.write(cache.getExpression(), null, castBoundTypes(bindExpressionValues(frameState, cache.getExpression(), specialization)));
        TypeMirror type = cache.getParameter().getType();
        LocalVariable var = new LocalVariable(type, name.substring(0, name.length() - 1), null);
        frameState.set(name, var);
        return new IfTriple(var.createDeclaration(initializer), null, null);
    }

    private IfTriple createMethodGuardCheck(FrameState frameState, SpecializationData specialization, GuardExpression guard, boolean fastPath) {
        DSLExpression expression = guard.getExpression();
        Map<Variable, CodeTree> resolvedBindings = castBoundTypes(bindExpressionValues(frameState, expression, specialization));

        CodeTree expressionCode = DSLExpressionGenerator.write(expression, null, resolvedBindings);
        CodeTree assertion = null; // overrule with assertion
        if (fastPath) {
            if (!specialization.isDynamicParameterBound(expression)) {
                assertion = CodeTreeBuilder.createBuilder().startAssert().tree(expressionCode).end().build();
            }
        } else {
            if (guard.isConstantTrueInSlowPath(context)) {
                assertion = CodeTreeBuilder.createBuilder().startStatement().string("// assert ").tree(expressionCode).end().build();
            }
        }

        return new IfTriple(null, assertion == null ? expressionCode : null, assertion);
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
                resolved = CodeTreeBuilder.createBuilder().startParantheses().cast(targetType, resolved).end().build();
            }
            resolvedBindings.put(variable, resolved);
        }
        return resolvedBindings;
    }

    private static Map<Variable, LocalVariable> bindExpressionValues(FrameState frameState, DSLExpression expression, SpecializationData specialization) throws AssertionError {
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
                if (resolvedParameter.getSpecification().isCached()) {
                    // bind cached variable
                    String cachedMemberName = createFieldName(specialization, resolvedParameter);
                    CodeTree cacheReference = createCacheReference(specialization, resolvedParameter);
                    LocalVariable var = frameState.get(cachedMemberName);
                    if (var == null) {
                        var = new LocalVariable(resolvedParameter.getType(), cachedMemberName, cacheReference);
                    }
                    bindings.put(variable, var);
                } else {
                    // bind local variable
                    if (resolvedParameter.getSpecification().isSignature()) {
                        NodeExecutionData execution = resolvedParameter.getSpecification().getExecution();
                        localVariable = frameState.getValue(execution);
                    } else {
                        localVariable = frameState.get(resolvedParameter.getLocalName());
                    }

                    if (localVariable != null) {
                        bindings.put(variable, localVariable);
                    }
                }
            }
        }
        return bindings;
    }

    private static CodeTree createCacheReference(SpecializationData s, Parameter p) {
        String cacheFieldName = createFieldName(s, p);
        return accessInSpecializationClass(s, cacheFieldName);
    }

    private static CodeTree createAssumptionReference(SpecializationData s, AssumptionExpression a) {
        String assumptionFieldName = createAssumptionFieldName(s, a);
        return accessInSpecializationClass(s, assumptionFieldName);
    }

    private static CodeTree accessInSpecializationClass(SpecializationData s, String cacheFieldName) {
        if (!useSpecializationClass(s)) {
            return CodeTreeBuilder.singleString(cacheFieldName);
        } else {
            return CodeTreeBuilder.createBuilder().string(createSpecializationLocalName(s), ".", cacheFieldName).build();
        }
    }

    private IfTriple createTypeCheckOrCast(FrameState frameState, SpecializationGroup group, TypeGuard typeGuard,
                    NodeExecutionMode specializationExecution, boolean castOnly, boolean forceImplicitCast) {
        CodeTreeBuilder prepareBuilder = CodeTreeBuilder.createBuilder();
        CodeTreeBuilder checkBuilder = CodeTreeBuilder.createBuilder();
        int signatureIndex = typeGuard.getSignatureIndex();
        LocalVariable value = frameState.getValue(signatureIndex);
        TypeMirror targetType = typeGuard.getType();

        if (!ElementUtils.needsCastTo(value.getTypeMirror(), targetType)) {
            boolean foundImplicitSubType = false;
            if (forceImplicitCast) {
                List<ImplicitCastData> casts = typeSystem.lookupByTargetType(targetType);
                for (ImplicitCastData cast : casts) {
                    if (ElementUtils.isSubtype(cast.getSourceType(), targetType)) {
                        foundImplicitSubType = true;
                        break;
                    }
                }
            }
            if (!foundImplicitSubType) {
                return null;
            }
        }

        NodeExecutionData execution = node.getChildExecutions().get(signatureIndex);
        CodeTreeBuilder castBuilder = prepareBuilder.create();

        LocalVariable shortCircuit = frameState.getShortCircuit(execution);
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
            checkBuilder.tree(TypeSystemCodeGenerator.check(typeSystem, targetType, valueReference));
            castBuilder.tree(TypeSystemCodeGenerator.cast(typeSystem, targetType, valueReference));
        } else {
            List<SpecializationData> specializations = group.collectSpecializations();
            List<Parameter> parameters = new ArrayList<>();
            for (SpecializationData otherSpecialization : specializations) {
                parameters.add(otherSpecialization.findParameterOrDie(execution));
            }

            if (specializationExecution.isFastPath() || specializationExecution.isGuardFallback()) {
                CodeTree implicitState;
                if (specializationExecution.isGuardFallback()) {
                    implicitState = CodeTreeBuilder.singleString("0b" + allsetMask(sourceTypes.size() + 1));
                } else {
                    implicitState = state.createExtractInteger(frameState, typeGuard);
                }
                checkBuilder.tree(TypeSystemCodeGenerator.implicitCheckFlat(typeSystem, targetType, valueReference, implicitState));
                castBuilder.tree(TypeSystemCodeGenerator.implicitCastFlat(typeSystem, targetType, valueReference, implicitState));
            } else {
                Parameter parameter = parameters.get(0);
                String implicitStateName = createImplicitTypeStateLocalName(parameter);
                CodeTree defaultValue = null;
                if (parameter.getSpecification().getExecution().isShortCircuit()) {
                    defaultValue = CodeTreeBuilder.singleString("0");
                }
                prepareBuilder.declaration(context.getType(int.class), implicitStateName, defaultValue);
                CodeTree specializeCall = TypeSystemCodeGenerator.implicitSpecializeFlat(typeSystem, targetType, valueReference);
                checkBuilder.startParantheses();
                checkBuilder.string(implicitStateName, " = ").tree(specializeCall);
                checkBuilder.end();
                checkBuilder.string(" != 0");
                castBuilder.tree(TypeSystemCodeGenerator.implicitCastFlat(typeSystem, targetType, valueReference, CodeTreeBuilder.singleString(implicitStateName)));
            }
        }

        if (shortCircuit != null) {
            checkBuilder.string(")");
            castBuilder.string(" : ").defaultValue(targetType);
        }

        if (castOnly) {
            LocalVariable currentValue = frameState.getValue(execution);
            CodeTreeBuilder localsBuilder = CodeTreeBuilder.createBuilder();
            LocalVariable castVariable = currentValue.nextName().newType(typeGuard.getType()).accessWith(null);
            frameState.setValue(execution, castVariable);
            localsBuilder.tree(castVariable.createDeclaration(castBuilder.build()));
            return new IfTriple(localsBuilder.build(), null, null);
        } else {
            return new IfTriple(prepareBuilder.build(), checkBuilder.build(), null);
        }
    }

    private List<IfTriple> initializeCasts(FrameState frameState, SpecializationGroup group, DSLExpression expression, NodeExecutionMode specializationExecution) {
        Set<VariableElement> boundElements = expression.findBoundVariableElements();
        if (boundElements.isEmpty()) {
            return Collections.emptyList();
        }
        List<IfTriple> triples = new ArrayList<>();
        for (VariableElement variable : boundElements) {
            Parameter p = group.getSpecialization().findByVariable(variable);
            if (p != null) {
                NodeExecutionData execution = p.getSpecification().getExecution();
                if (execution != null) {
                    LocalVariable var = frameState.getValue(execution);
                    if (var == null) {
                        throw new AssertionError();
                    }

                    IfTriple triple = createTypeCheckOrCast(frameState, group, new TypeGuard(p.getType(), execution.getIndex()), specializationExecution, true, false);
                    if (triple != null) {
                        triples.add(triple);
                    }
                }
            }
        }
        return triples;
    }

    private static String allsetMask(int size) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < size; i++) {
            b.append("1");
        }
        return b.toString();
    }

    private ExecutableTypeData createExecuteAndSpecializeType() {
        SpecializationData polymorphicSpecialization = node.getPolymorphicSpecialization();
        TypeMirror polymorphicType = polymorphicSpecialization.getReturnType().getType();

        List<TypeMirror> parameters = new ArrayList<>();
        for (Parameter param : polymorphicSpecialization.getSignatureParameters()) {
            if (param.getSpecification().getExecution().isShortCircuit()) {
                parameters.add(context.getType(boolean.class));
            }
            parameters.add(param.getType());
        }

        return new ExecutableTypeData(node, polymorphicType, "executeAndSpecialize", node.getFrameType(), parameters);
    }

    private List<TypeMirror> resolveOptimizedImplicitSourceTypes(NodeExecutionData execution, TypeMirror targetType) {
        List<TypeMirror> allSourceTypes = typeSystem.lookupSourceTypes(targetType);
        List<TypeMirror> filteredSourceTypes = new ArrayList<>();
        for (TypeMirror sourceType : allSourceTypes) {

            ExecutableTypeData executableType = resolveTargetExecutable(execution, sourceType);
            if (executableType == null) {
                continue;
            }

            if (!ElementUtils.isPrimitive(sourceType) || !boxingEliminationEnabled) {
                // don't optimize non primitives
                continue;
            }

            if (!ElementUtils.typeEquals(executableType.getReturnType(), sourceType)) {
                // no boxing optimization possible
                continue;
            }

            filteredSourceTypes.add(sourceType);
        }
        return filteredSourceTypes;
    }

    private ChildExecutionResult createExecuteChildImplicitCast(CodeTreeBuilder parent, FrameState originalFrameState, FrameState frameState, NodeExecutionData execution, LocalVariable target) {
        CodeTreeBuilder builder = parent.create();
        List<TypeMirror> originalSourceTypes = typeSystem.lookupSourceTypes(target.getTypeMirror());
        List<TypeMirror> sourceTypes = resolveOptimizedImplicitSourceTypes(execution, target.getTypeMirror());
        TypeGuard typeGuard = new TypeGuard(target.getTypeMirror(), execution.getIndex());
        boolean throwsUnexpected = false;
        boolean elseIf = false;
        for (TypeMirror sourceType : sourceTypes) {
            ExecutableTypeData executableType = resolveTargetExecutable(execution, sourceType);
            elseIf = builder.startIf(elseIf);
            throwsUnexpected |= executableType.hasUnexpectedValue(context);
            builder.tree(state.createContainsOnly(frameState, originalSourceTypes.indexOf(sourceType), 1, new Object[]{typeGuard}, new Object[]{typeGuard, node.getUninitializedSpecialization()}));
            builder.end();
            builder.startBlock();

            CodeTree value = callChildExecuteMethod(execution, executableType, frameState);
            value = expect(executableType.getReturnType(), sourceType, value);

            throwsUnexpected |= needsCastTo(executableType.getReturnType(), sourceType);
            ImplicitCastData cast = typeSystem.lookupCast(sourceType, target.getTypeMirror());
            if (cast != null) {
                // we need to store the original value to restore it in
                // case of a deopt
                String localName = createSourceTypeLocalName(target, sourceType);
                builder.startStatement().string(localName).string(" = ").tree(value).end();
                value = callMethod(null, cast.getMethod(), CodeTreeBuilder.singleString(localName));
            }

            builder.startStatement().string(target.getName()).string(" = ").tree(value).end();
            builder.end();
        }

        if (elseIf) {
            builder.startElseBlock();
        }
        LocalVariable genericValue = target.makeGeneric(context).nextName();
        builder.tree(createAssignExecuteChild(originalFrameState, frameState, builder, execution, node.getGenericExecutableType(null), genericValue, null));
        builder.startStatement().string(target.getName()).string(" = ");
        CodeTree implicitState = state.createExtractInteger(frameState, typeGuard);
        builder.tree(TypeSystemCodeGenerator.implicitExpectFlat(typeSystem, target.getTypeMirror(), genericValue.createReference(), implicitState));
        builder.end();

        if (!sourceTypes.isEmpty()) {
            builder.end();
        }
        return new ChildExecutionResult(builder.build(), throwsUnexpected);
    }

    private static class ChildExecutionResult {

        CodeTree code;
        final boolean throwsUnexpectedResult;

        ChildExecutionResult(CodeTree code, boolean throwsUnexpectedResult) {
            this.code = code;
            this.throwsUnexpectedResult = throwsUnexpectedResult;
        }

    }

    private static class ExecuteDelegationResult {

        public final CodeTree tree;
        public final boolean hasFallthrough;

        ExecuteDelegationResult(CodeTree tree, boolean hasFallthrough) {
            this.tree = tree;
            this.hasFallthrough = hasFallthrough;
        }

    }

    private abstract static class BitSet {

        private final int capacity;
        private final String name;
        private final Map<Object, Integer> offsets = new HashMap<>();
        private final List<? extends Object> allElements;
        private final ProcessorContext context = ProcessorContext.getInstance();

        private final TypeMirror bitSetType;

        BitSet(String name, List<? extends Object> specializations) {
            this.name = name;
            this.allElements = specializations;
            this.capacity = computeStateLength();

            if (capacity <= 32) {
                bitSetType = context.getType(int.class);
            } else if (capacity <= 64) {
                bitSetType = context.getType(long.class);
            } else {
                throw new UnsupportedOperationException("State space too big " + capacity + ". Only <= 64 supported.");
            }
        }

        private int computeStateLength() {
            if (allElements.size() == 0) {
                return 0;
            }

            int bitIndex = 0;
            for (Object specialization : allElements) {
                int specializationSize = calculateRequiredBits(specialization);
                offsets.put(specialization, bitIndex);
                bitIndex += specializationSize;
            }
            return bitIndex - 1;
        }

        public CodeVariableElement declareFields(CodeTypeElement clazz) {
            return clazz.add(createNodeField(PRIVATE, bitSetType, name + "_", CompilationFinal.class));

        }

        public CodeTree createLoad(FrameState frameState, Object maskObject) {
            if (frameState.get(name) != null) {
                // already loaded
                return CodeTreeBuilder.singleString("");
            }
            CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
            String fieldName = name + "_";
            LocalVariable var = new LocalVariable(bitSetType, name, null);
            CodeTreeBuilder init = builder.create();
            init.tree(CodeTreeBuilder.singleString(fieldName));
            if (maskObject != null) {
                long mask = ~createMask(0, -1, new Object[]{maskObject});
                init.string(" & ").string(formatMask(mask));
                init.string("/* mask-active ", toString(maskObject), "*/");
            }

            builder.tree(var.createDeclaration(init.build()));
            frameState.set(name, var);
            return builder.build();
        }

        public CodeTree createContainsOnly(FrameState frameState, int offset, int length, Object[] selectedElements, @SuppressWarnings("hiding") Object[] allElements) {
            CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
            builder.startParantheses();

            long mask = ~createMask(offset, length, selectedElements) & createMask(allElements);

            builder.tree(createReference(frameState)).string(" & ").string(formatMask(mask));
            builder.end();
            builder.string(" == 0");
            builder.string(" /* only-active ", toString(selectedElements, " && "), " */");
            return builder.build();
        }

        public CodeTree createIs(FrameState frameState, Object[] elements) {
            CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
            builder.tree(createReference(frameState)).string(" == ").string(formatMask(createMask(elements)));
            return builder.build();
        }

        private String formatMask(long mask) {
            int bitsUsed = 64 - Long.numberOfLeadingZeros(mask);
            if (bitsUsed <= 16) {
                return "0b" + Integer.toBinaryString((int) mask);
            } else {
                if (bitsUsed <= 32 || capacity <= 32) {
                    return "0x" + Integer.toHexString((int) mask);
                } else {
                    return "0x" + Long.toHexString(mask) + "L";
                }
            }
        }

        public CodeTree createIsOneBitOf(FrameState frameState, Object[] elements) {
            String mask = formatMask(createMask(elements));
            CodeTree masked = CodeTreeBuilder.createBuilder().startParantheses().tree(createReference(frameState)).string(" & ").string(mask).end().build();
            CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();

            // use the calculation of power of two
            // (state & (state - 1L)) == 0L
            builder.startParantheses().tree(masked).string(" & ").startParantheses().tree(masked).string(" - 1").end().end().string(" == 0");

            builder.string(" /* ", label("is-single"), " */");
            return builder.build();
        }

        public CodeTree createContains(FrameState frameState, Object[] elements) {
            CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
            builder.startParantheses();
            builder.tree(createReference(frameState)).string(" & ").string(formatMask(createMask(elements)));
            builder.end();
            builder.string(" != 0");
            builder.string(" /* ", label("is"), toString(elements, " || "), " */");
            return builder.build();
        }

        private String toString(Object[] elements, String elementSep) {
            StringBuilder b = new StringBuilder();
            String sep = "";
            for (int i = 0; i < elements.length; i++) {
                b.append(sep).append(toString(elements[i]));
                sep = elementSep;
            }
            return b.toString();
        }

        protected String toString(Object element) {
            if (element instanceof SpecializationData) {
                SpecializationData specialization = (SpecializationData) element;
                if (specialization.isUninitialized()) {
                    return "uninitialized";
                }
                return ElementUtils.createReferenceName(specialization.getMethod());
            } else if (element instanceof TypeGuard) {
                int index = ((TypeGuard) element).getSignatureIndex();
                String simpleName = ElementUtils.getSimpleName(((TypeGuard) element).getType());
                return index + ":" + simpleName;
            }
            return element.toString();

        }

        private CodeTree createReference(FrameState frameState) {
            LocalVariable var = frameState != null ? frameState.get(name) : null;
            if (var != null) {
                return var.createReference();
            } else {
                return CodeTreeBuilder.createBuilder().string("this.", name, "_").build();
            }
        }

        public CodeTree createNotContains(FrameState frameState, Object[] elements) {
            CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
            builder.startParantheses();
            builder.tree(createReference(frameState)).string(" & ").string(formatMask(createMask(elements)));
            builder.end();
            builder.string(" == 0");
            builder.string(" /* ", label("is-not"), toString(elements, " && "), " */");
            return builder.build();
        }

        private String label(String message) {
            return message + "-" + getName() + " ";
        }

        protected String getName() {
            if (this instanceof ExcludeBitSet) {
                return "excluded";
            } else {
                return "active";
            }
        }

        public CodeTree createExtractInteger(FrameState frameState, Object element) {
            CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
            if (capacity > 32) {
                builder.string("(int)(");
            }

            builder.startParantheses();
            builder.tree(createReference(frameState)).string(" & ");
            builder.string(formatMask(createMask(element)));
            builder.end();
            builder.string(" >>> ", Integer.toString(getStateOffset(element)));
            if (capacity > 32) {
                builder.string(")");
            }
            builder.string(" /* ", label("extract-implicit"), toString(element), " */");
            return builder.build();
        }

        public CodeTree createSet(FrameState frameState, Object[] elements, boolean value, boolean persist) {
            CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
            builder.startStatement();
            if (persist) {
                builder.string("this.", name, "_ = ");
            } else {
                builder.tree(createReference(frameState)).string(" = ");
            }
            builder.tree(createReference(frameState));
            if (value) {
                builder.string(" | ");
                builder.string(formatMask(createMask(elements)));
                builder.string(" /* ", label("add"), toString(elements, ", "), " */");
            } else {
                builder.string(" & ");
                builder.string(formatMask(~createMask(elements)));
                builder.string(" /* ", label("remove"), toString(elements, ", "), " */");
            }
            builder.end();
            return builder.build();
        }

        public CodeTree createSetInteger(FrameState frameState, Object element, CodeTree value) {
            int offset = getStateOffset(element);
            CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
            builder.startStatement();
            builder.tree(createReference(frameState)).string(" = ");
            builder.startParantheses();
            builder.tree(createReference(frameState));
            builder.string(" | (");
            if (capacity > 32) {
                builder.string("(long) ");
            }
            builder.tree(value).string(" << ", Integer.toString(offset), ")");
            builder.string(" /* ", label("set-implicit"), toString(element), " */");
            builder.end();
            builder.end();
            return builder.build();
        }

        private long createMask(Object e) {
            return createMask(new Object[]{e});
        }

        private long createMask(Object[] e) {
            return createMask(0, -1, e);
        }

        private long createMask(int offset, int length, Object[] e) {
            long mask = 0;
            for (Object element : e) {
                if (!offsets.containsKey(element)) {
                    continue;
                }
                int stateOffset = getStateOffset(element);
                int stateLength = calculateRequiredBits(element);
                int realLength = length < 0 ? stateLength : Math.min(stateLength, offset + length);
                for (int i = offset; i < realLength; i++) {
                    mask |= 1L << (stateOffset + i);
                }
            }
            return mask;
        }

        private int getStateOffset(Object stateSpecialization) {
            Integer value = offsets.get(stateSpecialization);
            if (value == null) {
                return 0;
            }
            return value;
        }

        protected abstract int calculateRequiredBits(Object specialization);

    }

    private class StateBitSet extends BitSet {

        StateBitSet(List<Object> objects) {
            super(STATE_VALUE, objects);
        }

        @Override
        protected int calculateRequiredBits(Object object) {
            if (object instanceof SpecializationData) {
                SpecializationData specialization = (SpecializationData) object;
                if (specialization.isPolymorphic()) {
                    return 0;
                } else {
                    return 1;
                }
            } else if (object instanceof TypeGuard) {
                TypeGuard guard = (TypeGuard) object;

                TypeMirror type = guard.getType();
                List<TypeMirror> sourceTypes = typeSystem.lookupSourceTypes(type);
                if (sourceTypes.size() > 1) {
                    return sourceTypes.size();
                }
                throw new AssertionError();
            } else {
                throw new AssertionError();
            }
        }

    }

    private static class ExcludeBitSet extends BitSet {

        ExcludeBitSet(List<SpecializationData> specializations) {
            super("exclude", specializations);
        }

        @Override
        protected int calculateRequiredBits(Object object) {
            if (object instanceof SpecializationData) {
                SpecializationData specialization = (SpecializationData) object;
                if (specialization.isPolymorphic()) {
                    return 0;
                } else if (specialization.isUninitialized()) {
                    return 0;
                }
                if (!specialization.getExceptions().isEmpty() || !specialization.getExcludedBy().isEmpty()) {
                    return 1;
                }
                return 0;
            }
            throw new IllegalArgumentException();
        }

    }

    private static final class FrameState {

        private final FlatNodeGenFactory factory;
        private final Map<String, LocalVariable> values = new HashMap<>();

        private FrameState(FlatNodeGenFactory factory) {
            this.factory = factory;
        }

        public void loadFastPathState(SpecializationData specialization) {
            for (CacheExpression cache : specialization.getCaches()) {
                Parameter cacheParameter = cache.getParameter();
                String name = cacheParameter.getVariableElement().getSimpleName().toString();
                set(cacheParameter.getLocalName(), new LocalVariable(cacheParameter.getType(), name, CodeTreeBuilder.singleString("this." + name)));
            }

            for (AssumptionExpression assumption : specialization.getAssumptionExpressions()) {
                String name = assumptionName(assumption);
                TypeMirror type = assumption.getExpression().getResolvedType();
                set(name, new LocalVariable(type, name, CodeTreeBuilder.singleString("this." + name)));
            }
        }

        public CodeExecutableElement createMethod(Set<Modifier> modifiers, TypeMirror returnType, String name, String... optionalArguments) {
            CodeExecutableElement method = new CodeExecutableElement(modifiers, returnType, name);
            addParametersTo(method, Integer.MAX_VALUE, optionalArguments);
            return method;
        }

        public static FrameState load(FlatNodeGenFactory factory, ExecutableTypeData type, int varargsThreshold) {
            FrameState context = new FrameState(factory);
            context.loadEvaluatedValues(type, varargsThreshold);
            return context;
        }

        private void loadEvaluatedValues(ExecutableTypeData executedType, int varargsThreshold) {
            TypeMirror frame = executedType.getFrameParameter();
            if (frame == null) {
                removeValue(FRAME_VALUE);
            } else {
                set(FRAME_VALUE, new LocalVariable(frame, FRAME_VALUE, null));
            }
            for (NodeFieldData field : factory.node.getFields()) {
                String fieldName = fieldValueName(field);
                values.put(fieldName, new LocalVariable(field.getType(), fieldName, CodeTreeBuilder.singleString(field.getName())));
            }
            boolean varargs = needsVarargs(false, varargsThreshold);
            List<TypeMirror> evaluatedParameter = executedType.getEvaluatedParameters();
            int evaluatedIndex = 0;
            for (int executionIndex = 0; executionIndex < factory.node.getExecutionCount(); executionIndex++) {
                NodeExecutionData execution = factory.node.getChildExecutions().get(executionIndex);
                if (execution.isShortCircuit()) {
                    if (evaluatedIndex < executedType.getEvaluatedCount()) {
                        TypeMirror evaluatedType = evaluatedParameter.get(evaluatedIndex);
                        LocalVariable shortCircuit = createShortCircuitValue(execution).newType(evaluatedType);
                        if (varargs) {
                            shortCircuit = shortCircuit.accessWith(createReadVarargs(evaluatedIndex));
                        }
                        values.put(shortCircuit.getName(), shortCircuit.makeOriginal());
                        evaluatedIndex++;
                    }
                }
                if (evaluatedIndex < executedType.getEvaluatedCount()) {
                    TypeMirror evaluatedType = evaluatedParameter.get(evaluatedIndex);
                    LocalVariable value = createValue(execution, evaluatedType);
                    if (varargs) {
                        value = value.accessWith(createReadVarargs(evaluatedIndex));
                    }
                    values.put(value.getName(), value.makeOriginal());
                    evaluatedIndex++;
                }
            }
        }

        public static FrameState load(FlatNodeGenFactory factory) {
            return load(factory, factory.createExecuteAndSpecializeType(), Integer.MAX_VALUE);
        }

        public FrameState copy() {
            FrameState copy = new FrameState(factory);
            copy.values.putAll(values);
            return copy;
        }

        private static String fieldValueName(NodeFieldData field) {
            return field.getName() + "Value";
        }

        @SuppressWarnings("static-method")
        public LocalVariable createValue(NodeExecutionData execution, TypeMirror type) {
            return new LocalVariable(type, valueName(execution), null);
        }

        public LocalVariable createShortCircuitValue(NodeExecutionData execution) {
            return new LocalVariable(factory.getType(boolean.class), shortCircuitName(execution), null);
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

        private static CodeTree createReadVarargs(int i) {
            return CodeTreeBuilder.createBuilder().string("args_[").string(String.valueOf(i)).string("]").build();
        }

        public void addReferencesTo(CodeTreeBuilder builder, String... optionalNames) {
            for (String var : optionalNames) {
                LocalVariable local = values.get(var);
                if (local != null) {
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
                    builder.startGroup().tree(var.createReference()).end();
                }
            }
        }

        public void addParametersTo(CodeExecutableElement method, int varArgsThreshold, String... optionalNames) {
            for (String var : optionalNames) {
                LocalVariable local = values.get(var);
                if (local != null) {
                    method.addParameter(local.createParameter());
                }
            }
            if (needsVarargs(true, varArgsThreshold)) {
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

    private static final class LocalVariable {

        private final TypeMirror typeMirror;
        private final CodeTree accessorTree;
        private final String name;

        private LocalVariable(TypeMirror typeMirror, String name, CodeTree accessorTree) {
            Objects.requireNonNull(typeMirror);
            this.typeMirror = typeMirror;
            this.accessorTree = accessorTree;
            this.name = name;
        }

        public String getName() {
            return name;
        }

        private static String createNextName(String name) {
            return name + "_";
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
            return new LocalVariable(newType, name, accessorTree);
        }

        public LocalVariable accessWith(CodeTree tree) {
            return new LocalVariable(typeMirror, name, tree);
        }

        public LocalVariable nextName() {
            return new LocalVariable(typeMirror, createNextName(name), accessorTree);
        }

        public LocalVariable makeOriginal() {
            return new LocalVariable(typeMirror, name, accessorTree);
        }

        public LocalVariable makeGeneric(ProcessorContext context) {
            return newType(context.getType(Object.class));
        }

        @Override
        public String toString() {
            return "Local[type = " + getTypeMirror() + ", name = " + name + ", accessWith = " + accessorTree + "]";
        }

    }

    private static class BoxingSplit {

        private final SpecializationGroup group;
        private final TypeMirror[] primitiveSignature;

        BoxingSplit(SpecializationGroup group, TypeMirror[] primitiveSignature) {
            this.group = group;
            this.primitiveSignature = primitiveSignature;
        }

        public String getName() {
            StringBuilder b = new StringBuilder();
            String sep = "";
            for (TypeMirror typeMirror : primitiveSignature) {
                b.append(sep).append(ElementUtils.firstLetterLowerCase(ElementUtils.getSimpleName(typeMirror)));
                sep = "_";
            }
            return b.toString();
        }

    }

    private enum NodeExecutionMode {

        FAST_PATH,
        SLOW_PATH,
        FALLBACK_GUARD;

        public boolean isGuardFallback() {
            return this == FALLBACK_GUARD;
        }

        public boolean isSlowPath() {
            return this == NodeExecutionMode.SLOW_PATH;
        }

        public final boolean isFastPath() {
            return this == FAST_PATH;
        }

    }

}
