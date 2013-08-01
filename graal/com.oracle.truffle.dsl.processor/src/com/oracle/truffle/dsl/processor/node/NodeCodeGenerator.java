/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.node;

import static com.oracle.truffle.dsl.processor.Utils.*;
import static javax.lang.model.element.Modifier.*;

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.nodes.NodeInfo.Kind;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.ast.*;
import com.oracle.truffle.dsl.processor.node.NodeChildData.Cardinality;
import com.oracle.truffle.dsl.processor.node.NodeChildData.ExecutionKind;
import com.oracle.truffle.dsl.processor.template.*;
import com.oracle.truffle.dsl.processor.template.TemplateMethod.Signature;
import com.oracle.truffle.dsl.processor.typesystem.*;

public class NodeCodeGenerator extends CompilationUnitFactory<NodeData> {

    private static final String THIS_NODE_LOCAL_VAR_NAME = "thisNode";

    private static final String EXECUTE_GENERIC_NAME = "executeGeneric0";
    private static final String EXECUTE_SPECIALIZE_NAME = "executeAndSpecialize0";

    public NodeCodeGenerator(ProcessorContext context) {
        super(context);
    }

    private TypeMirror getUnexpectedValueException() {
        return getContext().getTruffleTypes().getUnexpectedValueException();
    }

    private static String factoryClassName(NodeData node) {
        return node.getNodeId() + "Factory";
    }

    private static String nodeSpecializationClassName(SpecializationData specialization) {
        String nodeid = specialization.getNode().getNodeId();
        if (nodeid.endsWith("Node") && !nodeid.equals("Node")) {
            nodeid = nodeid.substring(0, nodeid.length() - 4);
        }

        String name = Utils.firstLetterUpperCase(nodeid);
        name += Utils.firstLetterUpperCase(specialization.getId());
        name += "Node";
        return name;
    }

    private static String nodePolymorphicClassName(NodeData node, SpecializationData specialization) {
        String nodeid = node.getNodeId();
        if (nodeid.endsWith("Node") && !nodeid.equals("Node")) {
            nodeid = nodeid.substring(0, nodeid.length() - 4);
        }

        String name = Utils.firstLetterUpperCase(nodeid);
        if (specialization == node.getGenericPolymorphicSpecialization()) {
            name += "PolymorphicNode";
        } else {
            name += "Polymorphic" + polymorphicIndex(node, specialization) + "Node";
        }
        return name;
    }

    private static String valueNameEvaluated(ActualParameter targetParameter) {
        return valueName(targetParameter) + "Evaluated";
    }

    private static String valueName(ActualParameter param) {
        return param.getLocalName();
    }

    private static String castValueName(ActualParameter parameter) {
        return valueName(parameter) + "Cast";
    }

    private static String executeCachedName(SpecializationData polymorphic) {
        NodeData node = polymorphic.getNode();
        boolean generic = polymorphic == node.getGenericPolymorphicSpecialization();

        if (generic) {
            return "executeCachedGeneric0";
        } else {
            return "executeCached" + polymorphicIndex(node, polymorphic);
        }
    }

    private static int polymorphicIndex(NodeData node, SpecializationData polymorphic) {
        int index = 0;
        for (SpecializationData specialization : node.getPolymorphicSpecializations()) {
            if (specialization == polymorphic) {
                break;
            }
            if (specialization != node.getGenericPolymorphicSpecialization()) {
                index++;
            }
        }
        return index;
    }

    private void addInternalValueParameters(CodeExecutableElement method, TemplateMethod specialization, boolean forceFrame, boolean evaluated) {
        if (forceFrame && specialization.getSpecification().findParameterSpec("frame") != null) {
            method.addParameter(new CodeVariableElement(getContext().getTruffleTypes().getFrame(), "frameValue"));
        }
        for (ActualParameter parameter : specialization.getParameters()) {
            ParameterSpec spec = parameter.getSpecification();
            if (forceFrame && spec.getName().equals("frame")) {
                continue;
            }
            if (spec.isLocal()) {
                continue;
            }

            String name = valueName(parameter);
            if (evaluated && spec.isSignature()) {
                name = valueNameEvaluated(parameter);
            }

            method.addParameter(new CodeVariableElement(parameter.getType(), name));
        }
    }

    private void addInternalValueParameterNames(CodeTreeBuilder builder, TemplateMethod source, TemplateMethod specialization, String unexpectedValueName, boolean forceFrame, boolean includeImplicit) {
        if (forceFrame && specialization.getSpecification().findParameterSpec("frame") != null) {
            builder.string("frameValue");
        }
        for (ActualParameter parameter : specialization.getParameters()) {
            ParameterSpec spec = parameter.getSpecification();
            if (forceFrame && spec.getName().equals("frame")) {
                continue;
            }

            if (!includeImplicit && (parameter.isImplicit())) {
                continue;
            }
            if (parameter.getSpecification().isLocal()) {
                continue;
            }

            ActualParameter sourceParameter = source.findParameter(parameter.getLocalName());

            if (unexpectedValueName != null && parameter.getLocalName().equals(unexpectedValueName)) {
                builder.cast(parameter.getType(), CodeTreeBuilder.singleString("ex.getResult()"));
            } else if (sourceParameter != null) {
                builder.string(valueName(sourceParameter, parameter));
            } else {
                builder.string(valueName(parameter));
            }
        }
    }

    private String valueName(ActualParameter sourceParameter, ActualParameter targetParameter) {
        if (sourceParameter != null) {
            if (!sourceParameter.getSpecification().isSignature()) {
                return valueName(targetParameter);
            } else if (sourceParameter.getTypeSystemType() != null && targetParameter.getTypeSystemType() != null) {
                if (sourceParameter.getTypeSystemType().needsCastTo(getContext(), targetParameter.getTypeSystemType())) {
                    return castValueName(targetParameter);
                }
            }
            return valueName(targetParameter);
        } else {
            return valueName(targetParameter);
        }
    }

    private CodeTree createTemplateMethodCall(CodeTreeBuilder parent, CodeTree target, TemplateMethod sourceMethod, TemplateMethod targetMethod, String unexpectedValueName,
                    String... customSignatureValueNames) {
        CodeTreeBuilder builder = parent.create();

        boolean castedValues = sourceMethod != targetMethod;

        builder.startGroup();
        ExecutableElement method = targetMethod.getMethod();
        if (method == null) {
            throw new UnsupportedOperationException();
        }
        TypeElement targetClass = Utils.findNearestEnclosingType(method.getEnclosingElement());
        NodeData node = (NodeData) targetMethod.getTemplate();

        if (target == null) {
            boolean accessible = targetMethod.canBeAccessedByInstanceOf(getContext(), node.getNodeType());
            if (accessible) {
                if (builder.findMethod().getModifiers().contains(STATIC)) {
                    if (method.getModifiers().contains(STATIC)) {
                        builder.type(targetClass.asType());
                    } else {
                        builder.string(THIS_NODE_LOCAL_VAR_NAME);
                    }
                } else {
                    if (targetMethod instanceof ExecutableTypeData) {
                        builder.string("this");
                    } else {
                        builder.string("super");
                    }
                }
            } else {
                if (method.getModifiers().contains(STATIC)) {
                    builder.type(targetClass.asType());
                } else {
                    ActualParameter parameter = null;
                    for (ActualParameter searchParameter : targetMethod.getParameters()) {
                        if (searchParameter.getSpecification().isSignature()) {
                            parameter = searchParameter;
                            break;
                        }
                    }
                    ActualParameter sourceParameter = sourceMethod.findParameter(parameter.getLocalName());
                    assert parameter != null;

                    if (castedValues && sourceParameter != null) {
                        builder.string(valueName(sourceParameter, parameter));
                    } else {
                        builder.string(valueName(parameter));
                    }
                }
            }
            builder.string(".");
        } else {
            builder.tree(target);
        }
        builder.startCall(method.getSimpleName().toString());

        int signatureIndex = 0;

        for (ActualParameter targetParameter : targetMethod.getParameters()) {
            ActualParameter valueParameter = null;
            if (sourceMethod != null) {
                valueParameter = sourceMethod.findParameter(targetParameter.getLocalName());
            }
            if (valueParameter == null) {
                valueParameter = targetParameter;
            }
            TypeData targetType = targetParameter.getTypeSystemType();

            if (targetParameter.isImplicit() || valueParameter.isImplicit()) {
                continue;
            }

            TypeData valueType = null;
            if (valueParameter != null) {
                valueType = valueParameter.getTypeSystemType();
            }

            if (signatureIndex < customSignatureValueNames.length && targetParameter.getSpecification().isSignature()) {
                builder.string(customSignatureValueNames[signatureIndex]);
                signatureIndex++;
            } else if (targetParameter.getSpecification().isLocal()) {
                builder.startGroup();
                if (builder.findMethod().getModifiers().contains(Modifier.STATIC)) {
                    builder.string(THIS_NODE_LOCAL_VAR_NAME).string(".");
                } else {
                    builder.string("this.");
                }
                builder.string(targetParameter.getSpecification().getName());
                builder.end();
            } else if (unexpectedValueName != null && targetParameter.getLocalName().equals(unexpectedValueName)) {
                builder.string("ex.getResult()");
            } else if (targetType == null || targetType.isGeneric() || (valueType != null && valueType.equalsType(targetType))) {
                builder.startGroup();

                if (valueType != null && sourceMethod.getMethodName().equals(targetMethod.getMethodName()) && !valueType.isGeneric() && targetType.isGeneric()) {
                    builder.string("(");
                    builder.type(targetType.getPrimitiveType());
                    builder.string(") ");
                }
                builder.string(valueName(targetParameter));
                builder.end();
            } else {
                builder.string(castValueName(targetParameter));
            }
        }

        builder.end().end();

        return builder.getRoot();
    }

    private static String baseClassName(NodeData node) {
        String nodeid = node.getNodeId();
        if (nodeid.endsWith("Node") && !nodeid.equals("Node")) {
            nodeid = nodeid.substring(0, nodeid.length() - 4);
        }
        String name = Utils.firstLetterUpperCase(nodeid);
        name += "BaseNode";
        return name;
    }

    private static CodeTree createCallTypeSystemMethod(ProcessorContext context, CodeTreeBuilder parent, NodeData node, String methodName, CodeTree value) {
        CodeTreeBuilder builder = new CodeTreeBuilder(parent);
        startCallTypeSystemMethod(context, builder, node, methodName);
        builder.tree(value);
        builder.end().end();
        return builder.getRoot();
    }

    private static void startCallTypeSystemMethod(ProcessorContext context, CodeTreeBuilder body, NodeData node, String methodName) {
        VariableElement singleton = TypeSystemCodeGenerator.findSingleton(context, node.getTypeSystem());
        assert singleton != null;

        body.startGroup();
        body.staticReference(singleton.getEnclosingElement().asType(), singleton.getSimpleName().toString());
        body.string(".").startCall(methodName);
    }

    private CodeTree createGuardAndCast(CodeTreeBuilder parent, String conditionPrefix, SpecializationData sourceSpecialization, SpecializationData targetSpecialization, boolean castValues,
                    CodeTree guardedStatements, CodeTree elseStatements, boolean emitAssumptions, boolean forceElse) {

        NodeData node = targetSpecialization.getNode();
        CodeTreeBuilder builder = new CodeTreeBuilder(parent);
        CodeTree implicitGuards = createImplicitGuards(parent, conditionPrefix, sourceSpecialization, targetSpecialization, emitAssumptions);
        CodeTree explicitGuards = createExplicitGuards(parent, implicitGuards == null ? conditionPrefix : null, sourceSpecialization, targetSpecialization);

        Set<String> valuesNeedsCast;
        if (castValues) {
            // cast all
            valuesNeedsCast = null;
        } else {
            // find out which values needs a cast
            valuesNeedsCast = new HashSet<>();
            for (GuardData guard : targetSpecialization.getGuards()) {
                for (ActualParameter targetParameter : guard.getParameters()) {
                    NodeChildData field = node.findChild(targetParameter.getSpecification().getName());
                    if (field == null) {
                        continue;
                    }
                    TypeData targetType = targetParameter.getTypeSystemType();
                    ActualParameter sourceParameter = sourceSpecialization.findParameter(targetParameter.getLocalName());
                    if (sourceParameter == null) {
                        sourceParameter = targetParameter;
                    }
                    TypeData sourceType = sourceParameter.getTypeSystemType();

                    if (sourceType.needsCastTo(getContext(), targetType)) {
                        valuesNeedsCast.add(targetParameter.getLocalName());
                    }
                }
            }
        }

        int ifCount = 0;

        if (implicitGuards != null) {
            builder.startIf();
            builder.tree(implicitGuards);
            builder.end();
            builder.startBlock();
            ifCount++;
        }

        builder.tree(createCasts(parent, valuesNeedsCast, sourceSpecialization, targetSpecialization));

        if (explicitGuards != null) {
            builder.startIf();
            builder.tree(explicitGuards);
            builder.end();
            builder.startBlock();
            ifCount++;
        }

        if (implicitGuards == null && explicitGuards == null && conditionPrefix != null && !conditionPrefix.isEmpty()) {
            builder.startIf();
            builder.string(conditionPrefix);
            builder.end().startBlock();
            ifCount++;
        }

        builder.tree(guardedStatements);

        builder.end(ifCount);
        if (elseStatements != null && (forceElse || ifCount > 0)) {
            builder.tree(elseStatements);
        }
        return builder.getRoot();
    }

    private CodeTree createExplicitGuards(CodeTreeBuilder parent, String conditionPrefix, TemplateMethod valueSpecialization, SpecializationData guardedSpecialization) {
        CodeTreeBuilder builder = new CodeTreeBuilder(parent);
        String andOperator = conditionPrefix != null ? conditionPrefix + " && " : "";
        if (guardedSpecialization.getGuards().size() > 0) {
            // Explicitly specified guards
            for (GuardData guard : guardedSpecialization.getGuards()) {
                builder.string(andOperator);
                if (guard.isNegated()) {
                    builder.string("!");
                }
                builder.tree(createTemplateMethodCall(parent, null, valueSpecialization, guard, null));
                andOperator = " && ";
            }
        }

        return builder.isEmpty() ? null : builder.getRoot();
    }

    private CodeTree createCasts(CodeTreeBuilder parent, Set<String> castWhiteList, TemplateMethod valueSpecialization, SpecializationData guardedSpecialization) {
        CodeTreeBuilder builder = new CodeTreeBuilder(parent);
        // Implict guards based on method signature
        for (ActualParameter guardedParam : guardedSpecialization.getParameters()) {
            NodeChildData field = guardedSpecialization.getNode().findChild(guardedParam.getSpecification().getName());
            if (field == null) {
                continue;
            }
            ActualParameter valueParam = valueSpecialization.findParameter(guardedParam.getLocalName());

            if (valueParam == null) {
                /*
                 * If used inside a function execute method. The value param may not exist. In that
                 * case it assumes that the value is already converted.
                 */
                valueParam = guardedParam;
            }

            if (castWhiteList != null && !castWhiteList.contains(guardedParam.getLocalName())) {
                continue;
            }

            CodeTree cast = createCast(parent, field, valueParam, guardedParam.getTypeSystemType());
            if (cast == null) {
                continue;
            }
            builder.tree(cast);
        }

        return builder.getRoot();
    }

    private CodeTree createImplicitGuards(CodeTreeBuilder parent, String conditionPrefix, SpecializationData valueSpecialization, SpecializationData guardedSpecialization, boolean emitAssumptions) {
        CodeTreeBuilder builder = new CodeTreeBuilder(parent);
        // Implict guards based on method signature
        String andOperator = conditionPrefix != null ? conditionPrefix + " && " : "";

        if (emitAssumptions) {
            for (String assumption : guardedSpecialization.getAssumptions()) {
                builder.string(andOperator);
                builder.string("this");
                builder.string(".").string(assumption).string(".isValid()");
                andOperator = " && ";
            }
        }

        for (ActualParameter guardedParam : guardedSpecialization.getParameters()) {
            NodeChildData field = guardedSpecialization.getNode().findChild(guardedParam.getSpecification().getName());
            if (field == null) {
                continue;
            }
            ActualParameter valueParam = valueSpecialization.findParameter(guardedParam.getLocalName());

            if (valueParam == null) {
                /*
                 * If used inside a function execute method. The value param may not exist. In that
                 * case it assumes that the value is already converted.
                 */
                valueParam = guardedParam;
            }

            CodeTree implicitGuard = createTypeGuard(builder, field, valueParam, guardedParam.getTypeSystemType());
            if (implicitGuard == null) {
                continue;
            }

            builder.string(andOperator);
            builder.tree(implicitGuard);
            andOperator = " && ";
        }

        return builder.isEmpty() ? null : builder.getRoot();
    }

    private CodeTree createTypeGuard(CodeTreeBuilder parent, NodeChildData field, ActualParameter source, TypeData targetType) {
        NodeData node = field.getNodeData();

        CodeTreeBuilder builder = new CodeTreeBuilder(parent);

        TypeData sourceType = source.getTypeSystemType();

        if (!sourceType.needsCastTo(getContext(), targetType)) {
            return null;
        }

        builder.startGroup();

        if (field.isShortCircuit()) {
            ActualParameter shortCircuit = source.getPreviousParameter();
            assert shortCircuit != null;
            builder.string("(");
            builder.string("!").string(valueName(shortCircuit));
            builder.string(" || ");
        }

        startCallTypeSystemMethod(getContext(), builder, node, TypeSystemCodeGenerator.isTypeMethodName(targetType));
        builder.string(valueName(source));
        builder.end().end(); // call

        if (field.isShortCircuit()) {
            builder.string(")");
        }

        builder.end(); // group

        return builder.getRoot();
    }

    private CodeTree createCast(CodeTreeBuilder parent, NodeChildData field, ActualParameter source, TypeData targetType) {
        NodeData node = field.getNodeData();
        TypeData sourceType = source.getTypeSystemType();

        if (!sourceType.needsCastTo(getContext(), targetType)) {
            return null;
        }

        CodeTree condition = null;
        if (field.isShortCircuit()) {
            ActualParameter shortCircuit = source.getPreviousParameter();
            assert shortCircuit != null;
            condition = CodeTreeBuilder.singleString(valueName(shortCircuit));
        }

        CodeTree value = createCallTypeSystemMethod(context, parent, node, TypeSystemCodeGenerator.asTypeMethodName(targetType), CodeTreeBuilder.singleString(valueName(source)));

        return createLazyAssignment(parent, castValueName(source), targetType.getPrimitiveType(), condition, value);
    }

    /**
     * <pre>
     * variant1 $condition != null
     * 
     * $type $name = defaultValue($type);
     * if ($condition) {
     *     $name = $value;
     * }
     * 
     * variant2 $condition != null
     * $type $name = $value;
     * </pre>
     * 
     * .
     */
    private static CodeTree createLazyAssignment(CodeTreeBuilder parent, String name, TypeMirror type, CodeTree condition, CodeTree value) {
        CodeTreeBuilder builder = new CodeTreeBuilder(parent);
        if (condition == null) {
            builder.declaration(type, name, value);
        } else {
            builder.declaration(type, name, new CodeTreeBuilder(parent).defaultValue(type).getRoot());

            builder.startIf().tree(condition).end();
            builder.startBlock();
            builder.startStatement();
            builder.string(name);
            builder.string(" = ");
            builder.tree(value);
            builder.end(); // statement
            builder.end(); // block
        }
        return builder.getRoot();
    }

    protected void emitEncounteredSynthetic(CodeTreeBuilder builder, TemplateMethod current) {
        builder.startThrow().startNew(getContext().getType(UnsupportedOperationException.class));
        builder.startCall("createInfo0");
        builder.doubleQuote("Unsupported values");
        addInternalValueParameterNames(builder, current, current, null, false, true);
        builder.end().end().end();
    }

    private static List<ExecutableElement> findUserConstructors(TypeMirror nodeType) {
        List<ExecutableElement> constructors = new ArrayList<>();
        for (ExecutableElement constructor : ElementFilter.constructorsIn(Utils.fromTypeMirror(nodeType).getEnclosedElements())) {
            if (constructor.getModifiers().contains(PRIVATE)) {
                continue;
            }
            if (isCopyConstructor(constructor)) {
                continue;
            }
            constructors.add(constructor);
        }

        if (constructors.isEmpty()) {
            constructors.add(new CodeExecutableElement(null, Utils.getSimpleName(nodeType)));
        }

        return constructors;
    }

    private static ExecutableElement findCopyConstructor(TypeMirror type) {
        for (ExecutableElement constructor : ElementFilter.constructorsIn(Utils.fromTypeMirror(type).getEnclosedElements())) {
            if (constructor.getModifiers().contains(PRIVATE)) {
                continue;
            }
            if (isCopyConstructor(constructor)) {
                return constructor;
            }
        }

        return null;
    }

    private static boolean isCopyConstructor(ExecutableElement element) {
        if (element.getParameters().size() != 1) {
            return false;
        }
        VariableElement var = element.getParameters().get(0);
        TypeElement enclosingType = Utils.findNearestEnclosingType(var);
        if (Utils.typeEquals(var.asType(), enclosingType.asType())) {
            return true;
        }
        List<TypeElement> types = Utils.getDirectSuperTypes(enclosingType);
        for (TypeElement type : types) {
            if (!(type instanceof CodeTypeElement)) {
                // no copy constructors which are not generated types
                return false;
            }

            if (Utils.typeEquals(var.asType(), type.asType())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void createChildren(NodeData node) {
        Map<NodeData, List<TypeElement>> childTypes = new LinkedHashMap<>();
        if (node.getDeclaredNodes() != null && !node.getDeclaredNodes().isEmpty()) {
            for (NodeData nodeChild : node.getDeclaredNodes()) {
                NodeCodeGenerator generator = new NodeCodeGenerator(getContext());
                childTypes.put(nodeChild, generator.process(null, nodeChild).getEnclosedElements());
            }
        }

        if (node.needsFactory() || node.getNodeDeclaringChildren().size() > 0) {
            add(new NodeFactoryFactory(context, childTypes), node);
        }
    }

    private class NodeFactoryFactory extends ClassElementFactory<NodeData> {

        private final Map<NodeData, List<TypeElement>> childTypes;

        private CodeTypeElement generatedNode;

        public NodeFactoryFactory(ProcessorContext context, Map<NodeData, List<TypeElement>> childElements) {
            super(context);
            this.childTypes = childElements;
        }

        @Override
        protected CodeTypeElement create(NodeData node) {
            Modifier visibility = Utils.getVisibility(node.getTemplateType().getModifiers());
            CodeTypeElement clazz = createClass(node, modifiers(), factoryClassName(node), null, false);
            if (visibility != null) {
                clazz.getModifiers().add(visibility);
            }
            clazz.getModifiers().add(Modifier.FINAL);
            clazz.add(createConstructorUsingFields(modifiers(PRIVATE), clazz));
            return clazz;
        }

        @Override
        protected void createChildren(NodeData node) {
            CodeTypeElement clazz = getElement();

            Modifier createVisibility = Utils.getVisibility(clazz.getModifiers());

            if (node.needsFactory()) {
                NodeBaseFactory factory = new NodeBaseFactory(context);
                add(factory, node.getGenericSpecialization() == null ? node.getSpecializations().get(0) : node.getGenericSpecialization());
                generatedNode = factory.getElement();

                if (node.needsRewrites(context)) {
                    clazz.add(createCreateGenericMethod(node, createVisibility));
                }

                createFactoryMethods(node, clazz, createVisibility);

                if (node.isPolymorphic()) {
                    PolymorphicNodeFactory generic = new PolymorphicNodeFactory(getContext(), generatedNode, true);
                    add(generic, node.getGenericPolymorphicSpecialization());

                    for (SpecializationData specialization : node.getPolymorphicSpecializations()) {
                        if (specialization == node.getGenericPolymorphicSpecialization()) {
                            continue;
                        }
                        add(new PolymorphicNodeFactory(context, generic.getElement(), false), specialization);
                    }
                }
                for (SpecializationData specialization : node.getSpecializations()) {
                    if (!specialization.isReachable()) {
                        continue;
                    }
                    add(new SpecializedNodeFactory(context, generatedNode), specialization);
                }

                TypeMirror nodeFactory = Utils.getDeclaredType(Utils.fromTypeMirror(getContext().getType(NodeFactory.class)), node.getNodeType());
                clazz.getImplements().add(nodeFactory);
                clazz.add(createCreateNodeMethod(node));
                clazz.add(createCreateNodeGenericMethod(node));
                clazz.add(createGetNodeClassMethod(node));
                clazz.add(createGetNodeSignaturesMethod());
                clazz.add(createGetChildrenSignatureMethod(node));
                clazz.add(createGetInstanceMethod(node, createVisibility));
                clazz.add(createInstanceConstant(node, clazz.asType()));
            }

            for (NodeData childNode : childTypes.keySet()) {
                if (childNode.getTemplateType().getModifiers().contains(Modifier.PRIVATE)) {
                    continue;
                }

                for (TypeElement type : childTypes.get(childNode)) {
                    Set<Modifier> typeModifiers = ((CodeTypeElement) type).getModifiers();
                    Modifier visibility = Utils.getVisibility(type.getModifiers());
                    typeModifiers.clear();
                    if (visibility != null) {
                        typeModifiers.add(visibility);
                    }

                    typeModifiers.add(Modifier.STATIC);
                    typeModifiers.add(Modifier.FINAL);
                    clazz.add(type);
                }
            }

            List<NodeData> children = node.getNodeDeclaringChildren();
            if (node.getParent() == null && children.size() > 0) {
                clazz.add(createGetFactories(node));
            }

        }

        private CodeExecutableElement createGetNodeClassMethod(NodeData node) {
            TypeMirror returnType = Utils.getDeclaredType(Utils.fromTypeMirror(getContext().getType(Class.class)), node.getNodeType());
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), returnType, "getNodeClass");
            CodeTreeBuilder builder = method.createBuilder();
            builder.startReturn().typeLiteral(node.getNodeType()).end();
            return method;
        }

        private CodeExecutableElement createGetNodeSignaturesMethod() {
            TypeElement listType = Utils.fromTypeMirror(getContext().getType(List.class));
            TypeMirror classType = getContext().getType(Class.class);
            TypeMirror returnType = Utils.getDeclaredType(listType, Utils.getDeclaredType(listType, classType));
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), returnType, "getNodeSignatures");
            CodeTreeBuilder builder = method.createBuilder();
            builder.startReturn();
            builder.startStaticCall(getContext().getType(Arrays.class), "asList");
            List<ExecutableElement> constructors = findUserConstructors(generatedNode.asType());
            for (ExecutableElement constructor : constructors) {
                builder.tree(createAsList(builder, Utils.asTypeMirrors(constructor.getParameters()), classType));
            }
            builder.end();
            builder.end();
            return method;
        }

        private CodeExecutableElement createGetChildrenSignatureMethod(NodeData node) {
            Types types = getContext().getEnvironment().getTypeUtils();
            TypeElement listType = Utils.fromTypeMirror(getContext().getType(List.class));
            TypeMirror classType = getContext().getType(Class.class);
            TypeMirror nodeType = getContext().getTruffleTypes().getNode();
            TypeMirror wildcardNodeType = types.getWildcardType(nodeType, null);
            classType = Utils.getDeclaredType(Utils.fromTypeMirror(classType), wildcardNodeType);
            TypeMirror returnType = Utils.getDeclaredType(listType, classType);

            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), returnType, "getExecutionSignature");
            CodeTreeBuilder builder = method.createBuilder();

            List<TypeMirror> signatureTypes = new ArrayList<>();
            assert !node.getSpecializations().isEmpty();
            SpecializationData data = node.getSpecializations().get(0);
            for (ActualParameter parameter : data.getParameters()) {
                ParameterSpec spec = parameter.getSpecification();
                NodeChildData field = node.findChild(spec.getName());
                if (field == null) {
                    continue;
                }

                TypeMirror type;
                if (field.getCardinality() == Cardinality.MANY && field.getNodeType().getKind() == TypeKind.ARRAY) {
                    type = ((ArrayType) field.getNodeType()).getComponentType();
                } else {
                    type = field.getNodeType();
                }

                signatureTypes.add(type);
            }

            builder.startReturn().tree(createAsList(builder, signatureTypes, classType)).end();
            return method;
        }

        private CodeTree createAsList(CodeTreeBuilder parent, List<TypeMirror> types, TypeMirror elementClass) {
            CodeTreeBuilder builder = parent.create();
            builder.startGroup();
            builder.type(getContext().getType(Arrays.class));
            builder.string(".<").type(elementClass).string(">");
            builder.startCall("asList");
            for (TypeMirror typeMirror : types) {
                builder.typeLiteral(typeMirror);
            }
            builder.end().end();
            return builder.getRoot();
        }

        private CodeExecutableElement createCreateNodeMethod(NodeData node) {
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), node.getNodeType(), "createNode");
            CodeVariableElement arguments = new CodeVariableElement(getContext().getType(Object.class), "arguments");
            method.setVarArgs(true);
            method.addParameter(arguments);

            CodeTreeBuilder builder = method.createBuilder();
            List<ExecutableElement> signatures = findUserConstructors(generatedNode.asType());
            boolean ifStarted = false;

            for (ExecutableElement element : signatures) {
                ifStarted = builder.startIf(ifStarted);
                builder.string("arguments.length == " + element.getParameters().size());

                int index = 0;
                for (VariableElement param : element.getParameters()) {
                    builder.string(" && ");
                    if (!param.asType().getKind().isPrimitive()) {
                        builder.string("(arguments[" + index + "] == null || ");
                    }
                    builder.string("arguments[" + index + "] instanceof ");
                    builder.type(Utils.boxType(getContext(), param.asType()));
                    if (!param.asType().getKind().isPrimitive()) {
                        builder.string(")");
                    }
                    index++;
                }
                builder.end();
                builder.startBlock();

                builder.startReturn().startCall("create");
                index = 0;
                for (VariableElement param : element.getParameters()) {
                    builder.startGroup();
                    builder.string("(").type(param.asType()).string(") ");
                    builder.string("arguments[").string(String.valueOf(index)).string("]");
                    builder.end();
                    index++;
                }
                builder.end().end();

                builder.end(); // block
            }

            builder.startElseBlock();
            builder.startThrow().startNew(getContext().getType(IllegalArgumentException.class));
            builder.doubleQuote("Invalid create signature.");
            builder.end().end();
            builder.end(); // else block
            return method;
        }

        private CodeExecutableElement createCreateNodeGenericMethod(NodeData node) {
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), node.getNodeType(), "createNodeGeneric");
            CodeVariableElement nodeParam = new CodeVariableElement(node.getNodeType(), THIS_NODE_LOCAL_VAR_NAME);
            method.addParameter(nodeParam);

            CodeTreeBuilder builder = method.createBuilder();
            if (!node.needsRewrites(getContext())) {
                builder.startThrow().startNew(getContext().getType(UnsupportedOperationException.class)).doubleQuote("No specialized version.").end().end();
            } else {
                builder.startReturn().startCall("createGeneric");
                builder.string(THIS_NODE_LOCAL_VAR_NAME);
                builder.end().end();
            }
            return method;
        }

        private ExecutableElement createGetInstanceMethod(NodeData node, Modifier visibility) {
            TypeElement nodeFactoryType = Utils.fromTypeMirror(getContext().getType(NodeFactory.class));
            TypeMirror returnType = Utils.getDeclaredType(nodeFactoryType, node.getNodeType());

            CodeExecutableElement method = new CodeExecutableElement(modifiers(), returnType, "getInstance");
            if (visibility != null) {
                method.getModifiers().add(visibility);
            }
            method.getModifiers().add(Modifier.STATIC);

            String varName = instanceVarName(node);

            CodeTreeBuilder builder = method.createBuilder();
            builder.startIf();
            builder.string(varName).string(" == null");
            builder.end().startBlock();

            builder.startStatement();
            builder.string(varName);
            builder.string(" = ");
            builder.startNew(factoryClassName(node)).end();
            builder.end();

            builder.end();
            builder.startReturn().string(varName).end();
            return method;
        }

        private String instanceVarName(NodeData node) {
            if (node.getParent() != null) {
                return Utils.firstLetterLowerCase(factoryClassName(node)) + "Instance";
            } else {
                return "instance";
            }
        }

        private CodeVariableElement createInstanceConstant(NodeData node, TypeMirror factoryType) {
            String varName = instanceVarName(node);
            CodeVariableElement var = new CodeVariableElement(modifiers(), factoryType, varName);
            var.getModifiers().add(Modifier.PRIVATE);
            var.getModifiers().add(Modifier.STATIC);
            return var;
        }

        private ExecutableElement createGetFactories(NodeData node) {
            List<NodeData> children = node.getNodeDeclaringChildren();
            if (node.needsFactory()) {
                children.add(node);
            }

            List<TypeMirror> nodeTypesList = new ArrayList<>();
            TypeMirror prev = null;
            boolean allSame = true;
            for (NodeData child : children) {
                nodeTypesList.add(child.getNodeType());
                if (prev != null && !Utils.typeEquals(child.getNodeType(), prev)) {
                    allSame = false;
                }
                prev = child.getNodeType();
            }
            TypeMirror commonNodeSuperType = Utils.getCommonSuperType(getContext(), nodeTypesList.toArray(new TypeMirror[nodeTypesList.size()]));

            Types types = getContext().getEnvironment().getTypeUtils();
            TypeMirror factoryType = getContext().getType(NodeFactory.class);
            TypeMirror baseType;
            if (allSame) {
                baseType = Utils.getDeclaredType(Utils.fromTypeMirror(factoryType), commonNodeSuperType);
            } else {
                baseType = Utils.getDeclaredType(Utils.fromTypeMirror(factoryType), types.getWildcardType(commonNodeSuperType, null));
            }
            TypeMirror listType = Utils.getDeclaredType(Utils.fromTypeMirror(getContext().getType(List.class)), baseType);

            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC, STATIC), listType, "getFactories");

            CodeTreeBuilder builder = method.createBuilder();
            builder.startReturn();
            builder.startStaticCall(getContext().getType(Arrays.class), "asList");

            for (NodeData child : children) {
                builder.startGroup();
                NodeData childNode = child;
                List<NodeData> factories = new ArrayList<>();
                while (childNode.getParent() != null) {
                    factories.add(childNode);
                    childNode = childNode.getParent();
                }
                Collections.reverse(factories);
                for (NodeData nodeData : factories) {
                    builder.string(factoryClassName(nodeData)).string(".");
                }
                builder.string("getInstance()");
                builder.end();
            }
            builder.end();
            builder.end();
            return method;
        }

        private void createFactoryMethods(NodeData node, CodeTypeElement clazz, Modifier createVisibility) {
            List<ExecutableElement> constructors = findUserConstructors(generatedNode.asType());
            for (ExecutableElement constructor : constructors) {
                clazz.add(createCreateMethod(node, createVisibility, constructor));
            }
        }

        private CodeExecutableElement createCreateMethod(NodeData node, Modifier visibility, ExecutableElement constructor) {
            CodeExecutableElement method = CodeExecutableElement.clone(getContext().getEnvironment(), constructor);
            method.setSimpleName(CodeNames.of("create"));
            method.getModifiers().clear();
            if (visibility != null) {
                method.getModifiers().add(visibility);
            }
            method.getModifiers().add(Modifier.STATIC);
            method.setReturnType(node.getNodeType());

            CodeTreeBuilder body = method.createBuilder();
            body.startReturn();
            if (node.getSpecializations().isEmpty()) {
                body.nullLiteral();
            } else {
                body.startNew(nodeSpecializationClassName(node.getSpecializations().get(0)));
                for (VariableElement var : method.getParameters()) {
                    body.string(var.getSimpleName().toString());
                }
                body.end();
            }
            body.end();
            return method;
        }

        private CodeExecutableElement createCreateGenericMethod(NodeData node, Modifier visibility) {
            CodeExecutableElement method = new CodeExecutableElement(modifiers(), node.getNodeType(), "createGeneric");
            if (visibility != null) {
                method.getModifiers().add(visibility);
            }
            method.getModifiers().add(Modifier.STATIC);
            method.addParameter(new CodeVariableElement(node.getNodeType(), THIS_NODE_LOCAL_VAR_NAME));

            CodeTreeBuilder body = method.createBuilder();

            SpecializationData found = null;
            List<SpecializationData> specializations = node.getSpecializations();
            for (int i = 0; i < specializations.size(); i++) {
                if (specializations.get(i).isReachable()) {
                    found = specializations.get(i);
                }
            }

            if (found == null) {
                body.startThrow().startNew(getContext().getType(UnsupportedOperationException.class)).end().end();
            } else {
                body.startReturn().startNew(nodeSpecializationClassName(found)).startGroup().cast(baseClassName(node)).string(THIS_NODE_LOCAL_VAR_NAME).end().end().end();
            }
            return method;
        }
    }

    private class NodeBaseFactory extends ClassElementFactory<SpecializationData> {

        public NodeBaseFactory(ProcessorContext context) {
            super(context);
        }

        @Override
        protected CodeTypeElement create(SpecializationData specialization) {
            NodeData node = specialization.getNode();
            CodeTypeElement clazz = createClass(node, modifiers(PRIVATE, ABSTRACT, STATIC), baseClassName(node), node.getNodeType(), false);

            for (NodeChildData child : node.getChildren()) {
                clazz.add(createChildField(child));

                if (child.getAccessElement() != null && child.getAccessElement().getModifiers().contains(Modifier.ABSTRACT)) {
                    ExecutableElement getter = (ExecutableElement) child.getAccessElement();
                    CodeExecutableElement method = CodeExecutableElement.clone(getContext().getEnvironment(), getter);
                    method.getModifiers().remove(Modifier.ABSTRACT);
                    method.createBuilder().startReturn().string("this.").string(child.getName()).end();
                    clazz.add(method);
                }
            }

            for (NodeFieldData field : node.getFields()) {
                if (!field.isGenerated()) {
                    continue;
                }

                clazz.add(new CodeVariableElement(modifiers(PROTECTED, FINAL), field.getType(), field.getName()));
                if (field.getGetter() != null && field.getGetter().getModifiers().contains(Modifier.ABSTRACT)) {
                    CodeExecutableElement method = CodeExecutableElement.clone(getContext().getEnvironment(), field.getGetter());
                    method.getModifiers().remove(Modifier.ABSTRACT);
                    method.createBuilder().startReturn().string("this.").string(field.getName()).end();
                    clazz.add(method);
                }
            }

            for (String assumption : node.getAssumptions()) {
                clazz.add(createAssumptionField(assumption));
            }

            createConstructors(node, clazz);

            return clazz;
        }

        @Override
        protected void createChildren(SpecializationData specialization) {
            NodeData node = specialization.getNode();
            CodeTypeElement clazz = getElement();

            if (node.needsRewrites(context)) {

                if (node.isPolymorphic()) {

                    CodeVariableElement var = new CodeVariableElement(modifiers(PROTECTED), clazz.asType(), "next0");
                    var.getAnnotationMirrors().add(new CodeAnnotationMirror(getContext().getTruffleTypes().getChildAnnotation()));
                    clazz.add(var);

                    CodeExecutableElement setter = new CodeExecutableElement(modifiers(PROTECTED), context.getType(void.class), "setNext0");
                    setter.getParameters().add(new CodeVariableElement(clazz.asType(), "next0"));
                    CodeTreeBuilder builder = setter.createBuilder();
                    builder.statement("this.next0 = adoptChild(next0)");
                    clazz.add(setter);

                    createIsCompatible(clazz, null);

                    CodeExecutableElement genericCachedExecute = createCachedExecute(node, node.getGenericPolymorphicSpecialization(), null);
                    clazz.add(genericCachedExecute);
                    for (SpecializationData polymorph : node.getPolymorphicSpecializations()) {
                        if (polymorph == node.getGenericPolymorphicSpecialization()) {
                            continue;
                        }
                        clazz.add(createCachedExecute(node, polymorph, genericCachedExecute));
                    }
                }

                clazz.add(createGenericExecuteAndSpecialize(node));
                clazz.add(createInfoMessage(node));
            }

            if (node.getGenericSpecialization() != null && node.getGenericSpecialization().isReachable()) {
                clazz.add(createGenericExecute(node));
            }
        }

        private CodeTree truffleBooleanOption(CodeTreeBuilder parent, String name) {
            CodeTreeBuilder builder = parent.create();
            builder.staticReference(getContext().getTruffleTypes().getTruffleOptions(), name);
            return builder.getRoot();
        }

        private Element createInfoMessage(NodeData node) {
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PROTECTED, STATIC), getContext().getType(String.class), "createInfo0");
            method.addParameter(new CodeVariableElement(getContext().getType(String.class), "message"));
            addInternalValueParameters(method, node.getGenericSpecialization(), false, false);

            CodeTreeBuilder builder = method.createBuilder();

            builder.startIf().tree(truffleBooleanOption(builder, TruffleTypes.OPTION_DETAILED_REWRITE_REASONS)).end();
            builder.startBlock();

            builder.startStatement().string("StringBuilder builder = new StringBuilder(message)").end();
            builder.startStatement().startCall("builder", "append").doubleQuote(" (").end().end();

            String sep = null;
            for (ActualParameter parameter : node.getGenericSpecialization().getParameters()) {
                if (!parameter.getSpecification().isSignature()) {
                    continue;
                }

                builder.startStatement();
                builder.string("builder");
                if (sep != null) {
                    builder.startCall(".append").doubleQuote(sep).end();
                }
                builder.startCall(".append").doubleQuote(parameter.getLocalName()).end();
                builder.startCall(".append").doubleQuote(" = ").end();
                builder.startCall(".append").string(parameter.getLocalName()).end();
                builder.end();

                if (!Utils.isPrimitive(parameter.getType())) {
                    builder.startIf().string(parameter.getLocalName() + " != null").end();
                    builder.startBlock();
                }
                builder.startStatement();
                if (Utils.isPrimitive(parameter.getType())) {
                    builder.startCall("builder.append").doubleQuote(" (" + Utils.getSimpleName(parameter.getType()) + ")").end();
                } else {
                    builder.startCall("builder.append").doubleQuote(" (").end();
                    builder.startCall(".append").string(parameter.getLocalName() + ".getClass().getSimpleName()").end();
                    builder.startCall(".append").doubleQuote(")").end();
                }
                builder.end();
                if (!Utils.isPrimitive(parameter.getType())) {
                    builder.end();
                }

                sep = ", ";
            }

            builder.startStatement().startCall("builder", "append").doubleQuote(")").end().end();
            builder.startReturn().string("builder.toString()").end();

            builder.end();
            builder.startElseBlock();
            builder.startReturn().string("message").end();
            builder.end();

            return method;
        }

        protected void createIsCompatible(CodeTypeElement clazz, SpecializationData specialization) {
            CodeExecutableElement isCompatible = new CodeExecutableElement(modifiers(PROTECTED), context.getType(boolean.class), "isCompatible0");
            isCompatible.addParameter(new CodeVariableElement(getContext().getType(Class.class), "type"));

            if (specialization == null) {
                isCompatible.getModifiers().add(ABSTRACT);
            } else if (specialization.isGeneric()) {
                isCompatible.createBuilder().startThrow().startNew(getContext().getType(AssertionError.class)).end().end();
            } else if (specialization.isPolymorphic()) {
                isCompatible.createBuilder().startReturn().string("type != getClass() && next0.isCompatible0(type)").end();
            } else if (specialization.isUninitialized()) {
                isCompatible.createBuilder().returnTrue();
            } else {
                NodeData node = specialization.getNode();
                CodeTreeBuilder builder = isCompatible.createBuilder();

                Signature specializationSignature = specialization.getSignature();
                List<SpecializationData> compatible = new ArrayList<>();
                for (SpecializationData polymorphic : node.getPolymorphicSpecializations()) {
                    if (specializationSignature.isCompatibleTo(polymorphic.getSignature())) {
                        compatible.add(polymorphic);
                    }
                }

                if (compatible.isEmpty()) {
                    builder.returnFalse();
                } else {
                    builder.startIf();
                    String and = "";
                    for (SpecializationData polymorphic : compatible) {
                        builder.string(and);
                        builder.string("type == ").string(nodePolymorphicClassName(node, polymorphic)).string(".class");
                        and = " || ";
                    }
                    builder.end().startBlock();
                    builder.startReturn().startCall("next0", "isCompatible0").string("type").end().end();
                    builder.end();
                    builder.returnFalse();
                }
            }

            clazz.add(isCompatible);
        }

        private CodeExecutableElement createCachedExecute(NodeData node, SpecializationData polymorph, CodeExecutableElement genericPolymorphMethod) {
            String name = executeCachedName(polymorph);
            CodeExecutableElement cachedExecute = new CodeExecutableElement(modifiers(PROTECTED), polymorph.getReturnType().getType(), name);

            ExecutableTypeData sourceExecutableType = node.findExecutableType(polymorph.getReturnType().getTypeSystemType(), 0);
            boolean sourceThrowsUnexpected = sourceExecutableType != null && sourceExecutableType.hasUnexpectedValue(getContext());
            if (sourceExecutableType.getType().equals(node.getGenericSpecialization().getReturnType().getTypeSystemType())) {
                sourceThrowsUnexpected = false;
            }
            if (sourceThrowsUnexpected) {
                cachedExecute.getThrownTypes().add(getContext().getType(UnexpectedResultException.class));
            }
            addInternalValueParameters(cachedExecute, polymorph, true, true);

            if (polymorph == node.getGenericPolymorphicSpecialization()) {
                cachedExecute.getModifiers().add(ABSTRACT);
            } else {
                SpecializationData genericPolymorph = node.getGenericPolymorphicSpecialization();
                CodeTreeBuilder builder = cachedExecute.createBuilder();
                ExecutableTypeData genericExecutable = new ExecutableTypeData(genericPolymorph, genericPolymorphMethod, node.getTypeSystem(), genericPolymorph.getReturnType().getTypeSystemType());
                ExecutableTypeData specificExecutable = new ExecutableTypeData(polymorph, cachedExecute, node.getTypeSystem(), polymorph.getReturnType().getTypeSystemType());
                builder.tree(createCastingExecute(builder, polymorph, specificExecutable, genericExecutable));
            }

            return cachedExecute;

        }

        private void createConstructors(NodeData node, CodeTypeElement clazz) {
            List<ExecutableElement> constructors = findUserConstructors(node.getNodeType());
            if (constructors.isEmpty()) {
                clazz.add(createUserConstructor(clazz, null));
            } else {
                for (ExecutableElement constructor : constructors) {
                    clazz.add(createUserConstructor(clazz, constructor));
                }
            }
            if (node.needsRewrites(getContext())) {
                clazz.add(createCopyConstructor(clazz, findCopyConstructor(node.getNodeType())));
            }
        }

        private CodeExecutableElement createUserConstructor(CodeTypeElement type, ExecutableElement superConstructor) {
            CodeExecutableElement method = new CodeExecutableElement(null, type.getSimpleName().toString());
            CodeTreeBuilder builder = method.createBuilder();

            NodeData node = getModel().getNode();

            if (superConstructor != null) {
                for (VariableElement param : superConstructor.getParameters()) {
                    method.getParameters().add(CodeVariableElement.clone(param));
                }
            }

            for (VariableElement var : type.getFields()) {
                NodeChildData child = node.findChild(var.getSimpleName().toString());
                if (child != null) {
                    method.getParameters().add(new CodeVariableElement(child.getOriginalType(), child.getName()));
                } else {
                    method.getParameters().add(new CodeVariableElement(var.asType(), var.getSimpleName().toString()));
                }
            }

            if (superConstructor != null) {
                builder.startStatement().startSuperCall();
                for (VariableElement param : superConstructor.getParameters()) {
                    builder.string(param.getSimpleName().toString());
                }
                builder.end().end();
            }

            for (VariableElement var : type.getFields()) {
                builder.startStatement();
                String fieldName = var.getSimpleName().toString();

                CodeTree fieldInit = CodeTreeBuilder.singleString(var.getSimpleName().toString());
                builder.string("this.").string(var.getSimpleName().toString());

                NodeChildData child = node.findChild(fieldName);
                if (child != null) {
                    CreateCastData createCast = node.findCast(child.getName());
                    if (createCast != null) {
                        fieldInit = createTemplateMethodCall(builder, null, node.getGenericSpecialization(), createCast, null, child.getName());
                    }
                }

                if (Utils.isAssignable(getContext(), var.asType(), getContext().getTruffleTypes().getNode())) {
                    builder.string(" = adoptChild(").tree(fieldInit).string(")");
                } else if (Utils.isAssignable(getContext(), var.asType(), getContext().getTruffleTypes().getNodeArray())) {
                    builder.string(" = adoptChildren(").tree(fieldInit).string(")");
                } else {
                    builder.string(" = ").tree(fieldInit);
                }
                builder.end();
            }
            return method;
        }

        private CodeExecutableElement createCopyConstructor(CodeTypeElement type, ExecutableElement superConstructor) {
            CodeExecutableElement method = new CodeExecutableElement(null, type.getSimpleName().toString());
            CodeTreeBuilder builder = method.createBuilder();
            method.getParameters().add(new CodeVariableElement(type.asType(), "copy"));

            if (superConstructor != null) {
                builder.startStatement().startSuperCall().string("copy").end().end();
            }

            for (VariableElement var : type.getFields()) {
                builder.startStatement();
                String varName = var.getSimpleName().toString();
                builder.string("this.").string(varName);
                if (Utils.isAssignable(getContext(), var.asType(), getContext().getTruffleTypes().getNode())) {
                    builder.string(" = adoptChild(copy.").string(varName).string(")");
                } else if (Utils.isAssignable(getContext(), var.asType(), getContext().getTruffleTypes().getNodeArray())) {
                    NodeData node = getModel().getNode();
                    NodeChildData child = node.findChild(varName);
                    if (child != null) {
                        builder.string(" = adoptChildren(");
                        builder.string("new ").type((child.getNodeType())).string(" {");
                        builder.startCommaGroup();
                        for (ActualParameter parameter : getModel().getParameters()) {
                            NodeChildData foundChild = node.findChild(parameter.getSpecification().getName());
                            if (foundChild == child) {
                                builder.startGroup();
                                builder.string("copy.").string(varName).string("[").string(String.valueOf(parameter.getIndex())).string("]");
                                builder.end();
                            }
                        }

                        builder.end().string("})");
                    } else {
                        builder.string(" = adoptChildren(copy.").string(varName).string(")");
                    }
                } else {
                    builder.string(" = copy.").string(varName);
                }
                builder.end();
            }
            if (getModel().getNode().isPolymorphic()) {
                builder.statement("this.next0 = adoptChild(copy.next0)");
            }

            return method;
        }

        private CodeVariableElement createAssumptionField(String assumption) {
            CodeVariableElement var = new CodeVariableElement(getContext().getTruffleTypes().getAssumption(), assumption);
            var.getModifiers().add(Modifier.FINAL);
            return var;
        }

        private CodeVariableElement createChildField(NodeChildData child) {
            CodeVariableElement var = new CodeVariableElement(child.getNodeType(), child.getName());
            var.getModifiers().add(Modifier.PROTECTED);

            DeclaredType annotationType;
            if (child.getCardinality() == Cardinality.MANY) {
                var.getModifiers().add(Modifier.FINAL);
                annotationType = getContext().getTruffleTypes().getChildrenAnnotation();
            } else {
                annotationType = getContext().getTruffleTypes().getChildAnnotation();
            }

            var.getAnnotationMirrors().add(new CodeAnnotationMirror(annotationType));
            return var;
        }

        private CodeExecutableElement createGenericExecuteAndSpecialize(final NodeData node) {
            TypeMirror genericReturnType = node.getGenericSpecialization().getReturnType().getType();
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PROTECTED), genericReturnType, EXECUTE_SPECIALIZE_NAME);
            method.addParameter(new CodeVariableElement(getContext().getType(int.class), "minimumState"));
            addInternalValueParameters(method, node.getGenericSpecialization(), true, false);
            method.addParameter(new CodeVariableElement(getContext().getType(String.class), "reason"));

            CodeTreeBuilder builder = method.createBuilder();
            builder.startStatement();
            builder.startStaticCall(getContext().getTruffleTypes().getCompilerAsserts(), "neverPartOfCompilation").end();
            builder.end();

            emitSpecializationListeners(builder, node);

            builder.startStatement().string("String message = ").startCall("createInfo0").string("reason");
            addInternalValueParameterNames(builder, node.getGenericSpecialization(), node.getGenericSpecialization(), null, false, true);
            builder.end().end();

            List<SpecializationData> specializations = node.getSpecializations();
            List<SpecializationData> filteredSpecializations = new ArrayList<>();
            for (SpecializationData current : specializations) {
                if (current.isUninitialized() || !current.isReachable()) {
                    continue;
                }
                filteredSpecializations.add(current);
            }

            List<SpecializationGroup> groups = SpecializationGroup.create(filteredSpecializations);

            for (SpecializationGroup group : groups) {
                builder.tree(createExecuteTree(builder, node.getGenericSpecialization(), group, true, new CodeBlock<SpecializationData>() {

                    public CodeTree create(CodeTreeBuilder b, SpecializationData current) {
                        return createGenericInvokeAndSpecialize(b, node.getGenericSpecialization(), current);
                    }
                }));
            }

            boolean firstUnreachable = true;
            for (SpecializationData current : specializations) {
                if (current.isUninitialized() || current.isReachable()) {
                    continue;
                }
                if (firstUnreachable) {
                    emitEncounteredSynthetic(builder, current);
                    firstUnreachable = false;
                }

                builder.string("// unreachable ").string(current.getId()).newLine();
            }

            return method;
        }

        private CodeExecutableElement createGenericExecute(NodeData node) {
            TypeMirror genericReturnType = node.getGenericSpecialization().getReturnType().getType();
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PROTECTED), genericReturnType, EXECUTE_GENERIC_NAME);

            method.getAnnotationMirrors().add(new CodeAnnotationMirror(getContext().getTruffleTypes().getSlowPath()));

            addInternalValueParameters(method, node.getGenericSpecialization(), node.needsFrame(), false);
            final CodeTreeBuilder builder = method.createBuilder();

            List<SpecializationData> specializations = node.getSpecializations();
            List<SpecializationData> filteredSpecializations = new ArrayList<>();
            for (SpecializationData current : specializations) {
                if (current.isUninitialized() || !current.isReachable()) {
                    continue;
                }
                filteredSpecializations.add(current);
            }

            List<SpecializationGroup> groups = SpecializationGroup.create(filteredSpecializations);

            for (SpecializationGroup group : groups) {
                builder.tree(createExecuteTree(builder, node.getGenericSpecialization(), group, false, new CodeBlock<SpecializationData>() {

                    public CodeTree create(CodeTreeBuilder b, SpecializationData current) {
                        return createGenericInvoke(builder, current.getNode().getGenericSpecialization(), current);
                    }
                }));
            }

            for (SpecializationData current : specializations) {
                if (current.isUninitialized() || current.isReachable()) {
                    continue;
                }
                builder.string("// unreachable ").string(current.getId()).newLine();
            }

            return method;
        }

        private CodeTree createExecuteTree(CodeTreeBuilder outerParent, final SpecializationData source, final SpecializationGroup group, final boolean checkMinimumState,
                        final CodeBlock<SpecializationData> guardedblock) {
            return guard(outerParent, source, group, checkMinimumState, new CodeBlock<Void>() {

                public CodeTree create(CodeTreeBuilder parent, Void value) {
                    CodeTreeBuilder builder = parent.create();

                    if (group.getSpecialization() != null) {
                        builder.tree(guardedblock.create(builder, group.getSpecialization()));

                        assert group.getChildren().isEmpty() : "missed a specialization";
                    } else {
                        for (SpecializationGroup childGroup : group.getChildren()) {
                            builder.tree(createExecuteTree(builder, source, childGroup, checkMinimumState, guardedblock));
                        }
                    }

                    return builder.getRoot();
                }
            });
        }

        private CodeTree guard(CodeTreeBuilder parent, SpecializationData source, SpecializationGroup group, boolean checkMinimumState, CodeBlock<Void> bodyBlock) {
            NodeData node = source.getNode();

            CodeTreeBuilder guardsBuilder = parent.create();
            CodeTreeBuilder castBuilder = parent.create();
            CodeTreeBuilder guardsCastBuilder = parent.create();

            String guardsAnd = "";
            String guardsCastAnd = "";

            boolean minimumState = checkMinimumState;
            if (minimumState) {
                int groupMaxIndex = group.getMaxSpecializationIndex();

                int genericIndex = node.getSpecializations().indexOf(node.getGenericSpecialization());
                if (groupMaxIndex >= genericIndex) {
                    // no minimum state check for an generic index
                    minimumState = false;
                }

                if (minimumState) {
                    // no minimum state check if alread checked by parent group
                    int parentMaxIndex = -1;
                    if (group.getParent() != null) {
                        parentMaxIndex = group.getParent().getMaxSpecializationIndex();
                    }
                    if (groupMaxIndex == parentMaxIndex) {
                        minimumState = false;
                    }
                }

                if (minimumState) {
                    guardsBuilder.string(guardsAnd);
                    guardsBuilder.string("minimumState < " + groupMaxIndex);
                    guardsAnd = " && ";
                }
            }

            for (String assumption : group.getAssumptions()) {
                guardsBuilder.string(guardsAnd);
                guardsBuilder.string("this");
                guardsBuilder.string(".").string(assumption).string(".isValid()");
                guardsAnd = " && ";
            }

            int argOffset = group.getTypeGuardOffset();
            int argIndex = argOffset;
            for (TypeData typeData : group.getTypeGuards()) {

                ActualParameter valueParam = source.getSignatureParameter(argIndex);

                if (valueParam == null) {
                    /*
                     * If used inside a execute evaluated method then the value param may not exist.
                     * In that case we assume that the value is executed generic or of the current
                     * specialization.
                     */
                    if (group.getSpecialization() != null) {
                        valueParam = group.getSpecialization().getSignatureParameter(argIndex);
                    } else {
                        valueParam = node.getGenericSpecialization().getSignatureParameter(argIndex);
                    }
                }

                NodeChildData child = node.findChild(valueParam.getSpecification().getName());
                if (child == null) {
                    throw new IllegalStateException();
                }

                CodeTree implicitGuard = createTypeGuard(guardsBuilder, child, valueParam, typeData);
                if (implicitGuard != null) {
                    guardsBuilder.string(guardsAnd);
                    guardsBuilder.tree(implicitGuard);
                    guardsAnd = " && ";
                }

                CodeTree cast = createCast(castBuilder, child, valueParam, typeData);
                if (cast != null) {
                    castBuilder.tree(cast);
                }

                argIndex++;
            }
            CodeTreeBuilder builder = parent.create();

            int ifCount = 0;
            if (isElseConnectableGroup(group)) {
                if (minimumState) {
                    builder.startElseIf().tree(guardsBuilder.getRoot()).end().startBlock();
                } else {
                    builder.startElseBlock();
                }
                ifCount++;

            } else {
                for (GuardData guard : group.getGuards()) {
                    if (needsTypeGuard(source, group, guard)) {
                        guardsCastBuilder.tree(createMethodGuard(parent, guardsCastAnd, source, guard));
                        guardsCastAnd = " && ";
                    } else {
                        guardsBuilder.tree(createMethodGuard(parent, guardsAnd, source, guard));
                        guardsAnd = " && ";
                    }
                }

                if (!guardsBuilder.isEmpty()) {
                    builder.startIf().tree(guardsBuilder.getRoot()).end().startBlock();
                    ifCount++;
                }
                builder.tree(castBuilder.getRoot());

                if (!guardsCastBuilder.isEmpty()) {
                    builder.startIf().tree(guardsCastBuilder.getRoot()).end().startBlock();
                    ifCount++;
                }
            }

            builder.tree(bodyBlock.create(builder, null));

            builder.end(ifCount);
            return builder.getRoot();
        }

        private boolean isElseConnectableGroup(SpecializationGroup group) {
            if (!group.getTypeGuards().isEmpty() || !group.getAssumptions().isEmpty()) {
                return false;
            }

            SpecializationGroup previousGroup = group.getPreviousGroup();
            if (previousGroup != null && group.getGuards().size() == 1 && previousGroup.getGuards().size() == 1) {
                GuardData guard = group.getGuards().get(0);
                GuardData previousGuard = previousGroup.getGuards().get(0);

                if (guard.getMethod().equals(previousGuard.getMethod())) {
                    assert guard.isNegated() != previousGuard.isNegated();
                    return true;
                }
            }
            return false;
        }

        private boolean needsTypeGuard(SpecializationData source, SpecializationGroup group, GuardData guard) {
            int offset = group.getTypeGuardOffset();
            int argIndex = 0;
            for (ActualParameter parameter : guard.getParameters()) {
                if (!parameter.getSpecification().isSignature()) {
                    continue;
                }
                if (argIndex < offset) {
                    // type casted in parent group
                    continue;
                }

                int guardIndex = argIndex - offset;
                if (guardIndex < group.getTypeGuards().size()) {
                    TypeData requiredType = group.getTypeGuards().get(guardIndex);

                    ActualParameter sourceParameter = source.findParameter(parameter.getLocalName());
                    if (sourceParameter == null) {
                        sourceParameter = source.getNode().getGenericSpecialization().findParameter(parameter.getLocalName());
                    }

                    if (sourceParameter.getTypeSystemType().needsCastTo(getContext(), requiredType)) {
                        return true;
                    }
                }
                argIndex++;
            }
            return false;
        }

        private CodeTree createMethodGuard(CodeTreeBuilder parent, String prefix, SpecializationData source, GuardData guard) {
            CodeTreeBuilder builder = parent.create();
            builder.string(prefix);
            if (guard.isNegated()) {
                builder.string("!");
            }
            builder.tree(createTemplateMethodCall(builder, null, source, guard, null));
            return builder.getRoot();
        }

        protected CodeTree createGenericInvoke(CodeTreeBuilder parent, SpecializationData source, SpecializationData current) {
            CodeTreeBuilder builder = new CodeTreeBuilder(parent);

            if (current.getMethod() == null) {
                emitEncounteredSynthetic(builder, current);
            } else {
                builder.startReturn().tree(createTemplateMethodCall(builder, null, source, current, null)).end();
            }

            return encloseThrowsWithFallThrough(current, builder.getRoot(), null);
        }

        protected CodeTree createGenericInvokeAndSpecialize(CodeTreeBuilder parent, SpecializationData source, SpecializationData current) {
            CodeTreeBuilder builder = parent.create();
            CodeTreeBuilder prefix = parent.create();

            NodeData node = current.getNode();

            String restoreNode = null;
            if (current.isGeneric() && node.isPolymorphic()) {
                builder.startIf().string("next0 == null && minimumState > 0").end().startBlock();
                builder.tree(createRewritePolymorphic(builder, node));
                builder.end();
                builder.startElseBlock();
                builder.tree(createRewriteGeneric(builder, source, current));
                builder.end();
            } else {
                // simple rewrite
                if (current.getExceptions().isEmpty()) {
                    builder.tree(createGenericInvoke(builder, source, current, createReplaceCall(builder, current, null, null)));
                } else {
                    prefix.declaration(baseClassName(node), "restoreNode", createReplaceCall(builder, current, null, null));
                    builder.tree(createGenericInvoke(builder, source, current, CodeTreeBuilder.singleString("restoreNode")));
                    restoreNode = "restoreNode";
                }
            }
            CodeTreeBuilder root = parent.create();
            root.tree(prefix.getRoot());
            root.tree(encloseThrowsWithFallThrough(current, builder.getRoot(), restoreNode));
            return root.getRoot();
        }

        private CodeTree createRewriteGeneric(CodeTreeBuilder parent, SpecializationData source, SpecializationData current) {
            NodeData node = current.getNode();

            CodeTreeBuilder builder = parent.create();
            builder.declaration(getContext().getTruffleTypes().getNode(), "root", "this");
            builder.startIf().string("next0 != null").end().startBlock();
            builder.tree(createFindRoot(builder, node, false));
            builder.end();
            builder.end();
            builder.tree(createGenericInvoke(builder, source, current, createReplaceCall(builder, current, "root", null)));
            return builder.getRoot();
        }

        protected CodeTree createFindRoot(CodeTreeBuilder parent, NodeData node, boolean countDepth) {
            CodeTreeBuilder builder = parent.create();
            builder.startDoBlock();
            builder.startAssert().string("root != null").string(" : ").doubleQuote("No polymorphic parent node.").end();
            builder.startStatement().string("root = ").string("root.getParent()").end();
            if (countDepth) {
                builder.statement("depth++");
            }
            builder.end();
            builder.startDoWhile();
            builder.string("!").startParantheses().instanceOf("root", nodePolymorphicClassName(node, node.getGenericPolymorphicSpecialization())).end();
            builder.end();
            return builder.getRoot();
        }

        private CodeTree encloseThrowsWithFallThrough(SpecializationData current, CodeTree tree, String restoreNodeVarName) {
            if (current.getExceptions().isEmpty()) {
                return tree;
            }
            CodeTreeBuilder builder = new CodeTreeBuilder(null);

            builder.startTryBlock();
            builder.tree(tree);
            for (SpecializationThrowsData exception : current.getExceptions()) {
                builder.end().startCatchBlock(exception.getJavaClass(), "rewriteEx");
                if (restoreNodeVarName != null) {
                    builder.startStatement().startCall(restoreNodeVarName, "replace").string("this");
                    builder.startGroup();
                    builder.startCall("createInfo0").doubleQuote("Rewrite exception thrown " + Utils.getSimpleName(exception.getJavaClass()) + ".");
                    addInternalValueParameterNames(builder, current, current, null, false, true);
                    builder.end();
                    builder.end();
                    builder.end().end();
                }

                builder.string("// fall through").newLine();
            }
            builder.end();

            return builder.getRoot();
        }

        protected CodeTree createGenericInvoke(CodeTreeBuilder parent, SpecializationData source, SpecializationData current, CodeTree replaceCall) {
            CodeTreeBuilder builder = parent.create();
            if (current.isGeneric()) {
                builder.startReturn().tree(replaceCall).string(".").startCall(EXECUTE_GENERIC_NAME);
                addInternalValueParameterNames(builder, source, current, null, current.getNode().needsFrame(), true);
                builder.end().end();

            } else if (current.getMethod() == null) {
                builder.statement(replaceCall);
                emitEncounteredSynthetic(builder, current);
            } else if (!current.canBeAccessedByInstanceOf(getContext(), source.getNode().getNodeType())) {
                builder.statement(replaceCall);
                builder.startReturn().tree(createTemplateMethodCall(parent, null, source, current, null)).end();
            } else {
                replaceCall.add(new CodeTree(CodeTreeKind.STRING, null, "."));
                builder.startReturn().tree(createTemplateMethodCall(parent, replaceCall, source, current, null)).end();
            }
            return builder.getRoot();
        }

        protected CodeTree createReplaceCall(CodeTreeBuilder builder, SpecializationData current, String target, String message) {
            String className = nodeSpecializationClassName(current);
            CodeTreeBuilder replaceCall = builder.create();
            if (target != null) {
                replaceCall.startCall(target, "replace");
            } else {
                replaceCall.startCall("replace");
            }
            replaceCall.startGroup().startNew(className).string("this").end().end();
            if (message == null) {
                replaceCall.string("message");
            } else {
                replaceCall.doubleQuote(message);
            }
            replaceCall.end();
            return replaceCall.getRoot();
        }

        private CodeTree createRewritePolymorphic(CodeTreeBuilder parent, NodeData node) {
            String polyClassName = nodePolymorphicClassName(node, node.getGenericPolymorphicSpecialization());
            String uninitializedName = nodeSpecializationClassName(node.getUninitializedSpecialization());
            CodeTreeBuilder builder = parent.create();

            builder.declaration(polyClassName, "polymorphic", builder.create().startNew(polyClassName).string("this").end());

            for (ActualParameter param : node.getGenericSpecialization().getParameters()) {
                if (!param.getSpecification().isSignature()) {
                    continue;
                }
                NodeChildData child = node.findChild(param.getSpecification().getName());
                if (child != null) {
                    builder.startStatement().string("this.").string(child.getName());
                    if (child.getCardinality().isMany()) {
                        builder.string("[").string(String.valueOf(param.getIndex())).string("]");
                    }
                    builder.string(" = null").end();
                }
            }
            builder.startStatement().startCall("super", "replace").string("polymorphic").string("message").end().end();
            builder.startStatement().startCall("polymorphic", "setNext0").string("this").end().end();
            builder.startStatement().startCall("setNext0").startNew(uninitializedName).string("this").end().end().end();

            builder.startReturn();
            builder.startCall("next0", executeCachedName(node.getGenericPolymorphicSpecialization()));
            addInternalValueParameterNames(builder, node.getGenericSpecialization(), node.getGenericSpecialization(), null, true, true);
            builder.end();
            builder.end();

            return builder.getRoot();
        }

        private void emitSpecializationListeners(CodeTreeBuilder builder, NodeData node) {
            for (TemplateMethod listener : node.getSpecializationListeners()) {
                builder.startStatement();
                builder.tree(createTemplateMethodCall(builder, null, listener, listener, null));
                builder.end(); // statement
            }
        }

        protected CodeTree createCastingExecute(CodeTreeBuilder parent, SpecializationData specialization, ExecutableTypeData executable, ExecutableTypeData castExecutable) {
            TypeData type = executable.getType();
            CodeTreeBuilder builder = new CodeTreeBuilder(parent);
            NodeData node = specialization.getNode();

            ExecutableTypeData castedType = node.findExecutableType(type, 0);
            TypeData primaryType = castExecutable.getType();

            boolean needsTry = castExecutable.hasUnexpectedValue(getContext());
            boolean returnVoid = type.isVoid();

            List<ActualParameter> executeParameters = new ArrayList<>();
            for (ActualParameter sourceParameter : executable.getParameters()) {
                if (!sourceParameter.getSpecification().isSignature()) {
                    continue;
                }

                ActualParameter targetParameter = castExecutable.findParameter(sourceParameter.getLocalName());
                if (targetParameter != null) {
                    executeParameters.add(targetParameter);
                }
            }

            builder.tree(createExecuteChildren(builder, executable, specialization, executeParameters, null, true));

            CodeTree primaryExecuteCall = createTemplateMethodCall(builder, null, executable, castExecutable, null);
            if (needsTry) {
                if (!returnVoid) {
                    builder.declaration(primaryType.getPrimitiveType(), "value");
                }
                builder.startTryBlock();

                if (returnVoid) {
                    builder.statement(primaryExecuteCall);
                } else {
                    builder.startStatement();
                    builder.string("value = ");
                    builder.tree(primaryExecuteCall);
                    builder.end();
                }

                builder.end().startCatchBlock(getUnexpectedValueException(), "ex");
                if (returnVoid) {
                    builder.string("// ignore").newLine();
                } else {
                    builder.startReturn();
                    builder.tree(createExpectExecutableType(node, specialization.getNode().getTypeSystem().getGenericTypeData(), castedType, CodeTreeBuilder.singleString("ex.getResult()")));
                    builder.end();
                }
                builder.end();

                if (!returnVoid) {
                    builder.startReturn();
                    builder.tree(createExpectExecutableType(node, castExecutable.getReturnType().getTypeSystemType(), executable, CodeTreeBuilder.singleString("value")));
                    builder.end();
                }
            } else {
                if (returnVoid) {
                    builder.statement(primaryExecuteCall);
                } else {
                    builder.startReturn();
                    builder.tree(createExpectExecutableType(node, castExecutable.getReturnType().getTypeSystemType(), executable, primaryExecuteCall));
                    builder.end();
                }
            }

            return builder.getRoot();
        }

        protected CodeTree createExpectExecutableType(NodeData node, TypeData sourceType, ExecutableTypeData castedType, CodeTree value) {
            boolean hasUnexpected = castedType.hasUnexpectedValue(getContext());
            return createCastType(node, sourceType, castedType.getType(), hasUnexpected, value);
        }

        protected CodeTree createCastType(NodeData node, TypeData sourceType, TypeData targetType, boolean expect, CodeTree value) {
            if (targetType == null) {
                return value;
            } else if (!sourceType.needsCastTo(getContext(), targetType)) {
                return value;
            }

            CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
            String targetMethodName;
            if (expect) {
                targetMethodName = TypeSystemCodeGenerator.expectTypeMethodName(targetType);
            } else {
                targetMethodName = TypeSystemCodeGenerator.asTypeMethodName(targetType);
            }
            startCallTypeSystemMethod(getContext(), builder, node, targetMethodName);

            builder.tree(value);
            builder.end().end();
            return builder.getRoot();
        }

        protected CodeTree createExecuteChildren(CodeTreeBuilder parent, ExecutableTypeData sourceExecutable, SpecializationData specialization, List<ActualParameter> targetParameters,
                        ActualParameter unexpectedParameter, boolean cast) {
            NodeData sourceNode = specialization.getNode();

            CodeTreeBuilder builder = new CodeTreeBuilder(parent);

            for (ActualParameter targetParameter : targetParameters) {
                NodeChildData field = sourceNode.findChild(targetParameter.getSpecification().getName());
                if (!targetParameter.getSpecification().isSignature()) {
                    continue;
                }

                TypeData targetType = targetParameter.getTypeSystemType();
                ExecutableTypeData targetExecutable = null;
                if (field != null) {
                    targetExecutable = field.findExecutableType(getContext(), targetType);
                }

                ActualParameter sourceParameter = sourceExecutable.findParameter(targetParameter.getLocalName());

                String targetVariableName = valueName(targetParameter);
                CodeTree executionExpression = null;
                if ((sourceParameter != null && cast) || sourceParameter != null) {
                    TypeData sourceType = sourceParameter.getTypeSystemType();
                    if (targetExecutable == null || !sourceType.needsCastTo(getContext(), targetType)) {
                        if (field != null && field.isShortCircuit() && sourceParameter != null) {
                            builder.tree(createShortCircuitValue(builder, specialization, field, targetParameter.getPreviousParameter(), unexpectedParameter));
                        }
                        builder.startStatement();
                        builder.type(targetParameter.getType()).string(" ");
                        builder.string(valueName(targetParameter)).string(" = ");
                        builder.tree(CodeTreeBuilder.singleString(valueNameEvaluated(targetParameter)));
                        builder.end();
                        continue;
                    } else {
                        CodeTree valueTree = CodeTreeBuilder.singleString(valueNameEvaluated(targetParameter));
                        executionExpression = createExpectExecutableType(sourceNode, sourceType, targetExecutable, valueTree);
                    }
                } else if (sourceParameter == null) {
                    executionExpression = createExecuteChildExpression(builder, field, targetParameter, unexpectedParameter);
                }

                if (executionExpression != null) {
                    CodeTreeVariable executionVar = new CodeTreeVariable();
                    CodeTree shortCircuitTree = createShortCircuitTree(builder, executionVar, targetVariableName, specialization, targetParameter, unexpectedParameter);
                    CodeTree unexpectedTree = createCatchUnexpectedTree(builder, executionExpression, targetVariableName, specialization, sourceExecutable, targetExecutable, targetParameter,
                                    shortCircuitTree != executionVar);

                    executionVar.set(unexpectedTree);
                    builder.tree(shortCircuitTree);
                }
            }
            return builder.getRoot();
        }

        private CodeTree createCatchUnexpectedTree(CodeTreeBuilder parent, CodeTree body, String targetVariableName, SpecializationData specialization, ExecutableTypeData currentExecutable,
                        ExecutableTypeData targetExecutable, ActualParameter param, boolean shortCircuit) {
            CodeTreeBuilder builder = new CodeTreeBuilder(parent);
            boolean unexpected = targetExecutable.hasUnexpectedValue(getContext());
            boolean cast = false;
            if (targetExecutable.getType().needsCastTo(getContext(), param.getTypeSystemType())) {
                unexpected = true;
                cast = true;
            }

            if (specialization.isGeneric() && unexpected) {
                throw new AssertionError("Generic has unexpected parameters. " + specialization.toString());
            }

            builder.startStatement();

            if (!shortCircuit) {
                builder.type(param.getType()).string(" ").string(targetVariableName);
            }

            if (unexpected) {
                if (!shortCircuit) {
                    builder.end();
                }
                builder.startTryBlock();
                builder.startStatement();
                builder.string(targetVariableName);
            } else if (shortCircuit) {
                builder.startStatement();
                builder.string(targetVariableName);
            }
            builder.string(" = ");
            if (cast) {
                builder.tree(createCastType(specialization.getNode(), targetExecutable.getType(), param.getTypeSystemType(), true, body));
            } else {
                builder.tree(body);
            }
            builder.end();

            if (unexpected) {
                builder.end().startCatchBlock(getUnexpectedValueException(), "ex");
                SpecializationData generic = specialization.getNode().getGenericSpecialization();
                ActualParameter genericParameter = generic.findParameter(param.getLocalName());

                List<ActualParameter> genericParameters = generic.getParametersAfter(genericParameter);
                builder.tree(createDeoptimize(builder));
                builder.tree(createExecuteChildren(parent, currentExecutable, generic, genericParameters, genericParameter, false));
                if (specialization.isPolymorphic()) {
                    builder.tree(createReturnOptimizeTypes(builder, currentExecutable, specialization, param));
                } else {
                    builder.tree(createReturnExecuteAndSpecialize(builder, currentExecutable, specialization, param,
                                    "Expected " + param.getLocalName() + " instanceof " + Utils.getSimpleName(param.getType())));
                }
                builder.end(); // catch block
            }

            return builder.getRoot();
        }

        private CodeTree createReturnOptimizeTypes(CodeTreeBuilder parent, ExecutableTypeData currentExecutable, SpecializationData specialization, ActualParameter param) {
            NodeData node = specialization.getNode();
            assert !node.getPolymorphicSpecializations().isEmpty();
            SpecializationData generic = node.getGenericPolymorphicSpecialization();

            CodeTreeBuilder builder = new CodeTreeBuilder(parent);
            builder.startReturn();

            CodeTreeBuilder execute = new CodeTreeBuilder(builder);
            execute.startCall("next0", executeCachedName(generic));
            addInternalValueParameterNames(execute, specialization, generic, param.getLocalName(), true, true);
            execute.end();

            TypeData sourceType = generic.getReturnType().getTypeSystemType();

            builder.tree(createExpectExecutableType(node, sourceType, currentExecutable, execute.getRoot()));

            builder.end();
            return builder.getRoot();
        }

        private CodeTree createExecuteChildExpression(CodeTreeBuilder parent, NodeChildData targetField, ActualParameter sourceParameter, ActualParameter unexpectedParameter) {
            TypeData type = sourceParameter.getTypeSystemType();
            ExecutableTypeData execType = targetField.findExecutableType(getContext(), type);

            CodeTreeBuilder builder = new CodeTreeBuilder(parent);
            if (targetField != null) {
                Element accessElement = targetField.getAccessElement();
                if (accessElement == null || accessElement.getKind() == ElementKind.METHOD) {
                    builder.string("this.").string(targetField.getName());
                } else if (accessElement.getKind() == ElementKind.FIELD) {
                    builder.string("this.").string(accessElement.getSimpleName().toString());
                } else {
                    throw new AssertionError();
                }
                if (sourceParameter.getSpecification().isIndexed()) {
                    builder.string("[" + sourceParameter.getIndex() + "]");
                }
                builder.string(".");
            }

            builder.startCall(execType.getMethodName());

            int index = 0;
            for (ActualParameter parameter : execType.getParameters()) {

                if (!parameter.getSpecification().isSignature()) {
                    builder.string(parameter.getLocalName());
                } else {

                    if (index < targetField.getExecuteWith().size()) {
                        NodeChildData child = targetField.getExecuteWith().get(index);

                        ParameterSpec spec = getModel().getSpecification().findParameterSpec(child.getName());
                        List<ActualParameter> specializationParams = getModel().findParameters(spec);

                        if (specializationParams.isEmpty()) {
                            builder.defaultValue(parameter.getType());
                            continue;
                        }

                        ActualParameter specializationParam = specializationParams.get(0);

                        TypeData targetType = parameter.getTypeSystemType();
                        TypeData sourceType = specializationParam.getTypeSystemType();
                        String localName = specializationParam.getLocalName();

                        if (unexpectedParameter != null && unexpectedParameter.getLocalName().equals(specializationParam.getLocalName())) {
                            localName = "ex.getResult()";
                            sourceType = getModel().getNode().getTypeSystem().getGenericTypeData();
                        }

                        CodeTree value = CodeTreeBuilder.singleString(localName);

                        if (sourceType.needsCastTo(getContext(), targetType)) {
                            value = createCallTypeSystemMethod(getContext(), builder, getModel().getNode(), TypeSystemCodeGenerator.asTypeMethodName(targetType), value);
                        }
                        builder.tree(value);
                    } else {
                        builder.defaultValue(parameter.getType());
                    }
                    index++;
                }
            }

            builder.end();

            return builder.getRoot();
        }

        private CodeTree createShortCircuitTree(CodeTreeBuilder parent, CodeTree body, String targetVariableName, SpecializationData specialization, ActualParameter parameter,
                        ActualParameter exceptionParam) {
            CodeTreeBuilder builder = new CodeTreeBuilder(parent);

            NodeChildData forField = specialization.getNode().findChild(parameter.getSpecification().getName());
            if (forField == null) {
                return body;
            }

            if (forField.getExecutionKind() != ExecutionKind.SHORT_CIRCUIT) {
                return body;
            }

            ActualParameter shortCircuitParam = specialization.getPreviousParam(parameter);

            builder.tree(createShortCircuitValue(builder, specialization, forField, shortCircuitParam, exceptionParam));

            builder.declaration(parameter.getType(), targetVariableName, CodeTreeBuilder.createBuilder().defaultValue(parameter.getType()));
            builder.startIf().string(shortCircuitParam.getLocalName()).end();
            builder.startBlock();
            builder.tree(body);
            builder.end();

            return builder.getRoot();
        }

        private CodeTree createShortCircuitValue(CodeTreeBuilder parent, SpecializationData specialization, NodeChildData forField, ActualParameter shortCircuitParam, ActualParameter exceptionParam) {
            CodeTreeBuilder builder = new CodeTreeBuilder(parent);
            int shortCircuitIndex = 0;
            for (NodeChildData field : specialization.getNode().getChildren()) {
                if (field.getExecutionKind() == ExecutionKind.SHORT_CIRCUIT) {
                    if (field == forField) {
                        break;
                    }
                    shortCircuitIndex++;
                }
            }

            builder.startStatement().type(shortCircuitParam.getType()).string(" ").string(valueName(shortCircuitParam)).string(" = ");
            ShortCircuitData shortCircuitData = specialization.getShortCircuits().get(shortCircuitIndex);
            builder.tree(createTemplateMethodCall(builder, null, specialization, shortCircuitData, exceptionParam != null ? exceptionParam.getLocalName() : null));
            builder.end(); // statement

            return builder.getRoot();
        }

        protected CodeTree createDeoptimize(CodeTreeBuilder parent) {
            CodeTreeBuilder builder = new CodeTreeBuilder(parent);
            builder.startStatement();
            builder.startStaticCall(getContext().getTruffleTypes().getCompilerDirectives(), "transferToInterpreter").end();
            builder.end();
            return builder.getRoot();
        }

        protected CodeTree createReturnExecuteAndSpecialize(CodeTreeBuilder parent, ExecutableTypeData executable, SpecializationData current, ActualParameter exceptionParam, String reason) {
            NodeData node = current.getNode();
            SpecializationData generic = node.getGenericSpecialization();
            CodeTreeBuilder specializeCall = new CodeTreeBuilder(parent);
            specializeCall.startCall(EXECUTE_SPECIALIZE_NAME);
            specializeCall.string(String.valueOf(node.getSpecializations().indexOf(current)));
            addInternalValueParameterNames(specializeCall, generic, node.getGenericSpecialization(), exceptionParam != null ? exceptionParam.getLocalName() : null, true, true);
            specializeCall.doubleQuote(reason);
            specializeCall.end().end();

            CodeTreeBuilder builder = new CodeTreeBuilder(parent);

            builder.startReturn();
            builder.tree(createExpectExecutableType(node, generic.getReturnType().getTypeSystemType(), executable, specializeCall.getRoot()));
            builder.end();

            return builder.getRoot();
        }
    }

    private class PolymorphicNodeFactory extends SpecializedNodeFactory {

        private final boolean generic;

        public PolymorphicNodeFactory(ProcessorContext context, CodeTypeElement nodeGen, boolean generic) {
            super(context, nodeGen);
            this.generic = generic;
        }

        @Override
        public CodeTypeElement create(SpecializationData specialization) {
            NodeData node = specialization.getNode();
            TypeMirror baseType = node.getNodeType();
            if (nodeGen != null) {
                baseType = nodeGen.asType();
            }
            CodeTypeElement clazz = createClass(node, modifiers(PRIVATE, STATIC), nodePolymorphicClassName(node, specialization), baseType, false);

            if (!generic) {
                clazz.getModifiers().add(Modifier.FINAL);
            }

            clazz.getAnnotationMirrors().add(createNodeInfo(node, Kind.POLYMORPHIC));

            return clazz;
        }

        @Override
        protected void createChildren(SpecializationData specialization) {
            CodeTypeElement clazz = getElement();

            createConstructors(clazz);
            createExecuteMethods(specialization);

            if (generic) {
                getElement().add(createOptimizeTypes());
                createCachedExecuteMethods(specialization);
                createIsCompatible(clazz, specialization);
            }
        }

        private CodeExecutableElement createOptimizeTypes() {
            NodeData node = getModel().getNode();
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PROTECTED, FINAL), getContext().getType(void.class), "optimizeTypes");
            CodeTreeBuilder builder = method.createBuilder();

            boolean elseIf = false;
            for (SpecializationData polymorphic : node.getPolymorphicSpecializations()) {
                String className = nodePolymorphicClassName(node, polymorphic);

                String sep = "";
                StringBuilder reason = new StringBuilder("Optimized polymorphic types for (");
                for (ActualParameter parameter : polymorphic.getReturnTypeAndParameters()) {
                    if (!parameter.getSpecification().isSignature()) {
                        continue;
                    }
                    reason.append(sep).append(Utils.getSimpleName(parameter.getType()));
                    sep = ", ";
                }
                reason.append(")");

                elseIf = builder.startIf(elseIf);
                builder.startCall("isCompatible0");
                builder.startGroup().string(className).string(".class").end();
                builder.end().end().startBlock();

                builder.startStatement().startCall("super", "replace");
                builder.startNew(className).string("this").end();
                builder.doubleQuote(reason.toString());
                builder.end().end(); // call
                builder.end();
            }
            return method;
        }
    }

    private class SpecializedNodeFactory extends NodeBaseFactory {

        protected final CodeTypeElement nodeGen;

        public SpecializedNodeFactory(ProcessorContext context, CodeTypeElement nodeGen) {
            super(context);
            this.nodeGen = nodeGen;
        }

        @Override
        public CodeTypeElement create(SpecializationData specialization) {
            NodeData node = specialization.getNode();
            TypeMirror baseType = node.getNodeType();
            if (nodeGen != null) {
                baseType = nodeGen.asType();
            }
            CodeTypeElement clazz = createClass(node, modifiers(PRIVATE, STATIC, FINAL), nodeSpecializationClassName(specialization), baseType, false);

            Kind kind;
            if (specialization.isGeneric()) {
                kind = Kind.GENERIC;
            } else if (specialization.isUninitialized()) {
                kind = Kind.UNINITIALIZED;
            } else {
                kind = Kind.SPECIALIZED;
            }
            clazz.getAnnotationMirrors().add(createNodeInfo(node, kind));

            return clazz;
        }

        protected CodeAnnotationMirror createNodeInfo(NodeData node, Kind kind) {
            String shortName = node.getShortName();
            CodeAnnotationMirror nodeInfoMirror = new CodeAnnotationMirror(getContext().getTruffleTypes().getNodeInfoAnnotation());
            if (shortName != null) {
                nodeInfoMirror.setElementValue(nodeInfoMirror.findExecutableElement("shortName"), new CodeAnnotationValue(shortName));
            }

            DeclaredType nodeinfoKind = getContext().getTruffleTypes().getNodeInfoKind();
            VariableElement varKind = Utils.findVariableElement(nodeinfoKind, kind.name());

            nodeInfoMirror.setElementValue(nodeInfoMirror.findExecutableElement("kind"), new CodeAnnotationValue(varKind));
            return nodeInfoMirror;
        }

        @Override
        protected void createChildren(SpecializationData specialization) {
            CodeTypeElement clazz = getElement();
            createConstructors(clazz);

            NodeData node = specialization.getNode();

            if (node.needsRewrites(getContext()) && node.isPolymorphic()) {
                createIsCompatible(clazz, specialization);
            }

            createExecuteMethods(specialization);
            createCachedExecuteMethods(specialization);
        }

        protected void createConstructors(CodeTypeElement clazz) {
            TypeElement superTypeElement = Utils.fromTypeMirror(clazz.getSuperclass());
            SpecializationData specialization = getModel();
            NodeData node = specialization.getNode();
            for (ExecutableElement constructor : ElementFilter.constructorsIn(superTypeElement.getEnclosedElements())) {
                if (specialization.isUninitialized()) {
                    // ignore copy constructors for uninitialized if not polymorphic
                    if (isCopyConstructor(constructor) && !node.isPolymorphic()) {
                        continue;
                    }
                } else if (node.getUninitializedSpecialization() != null) {
                    // ignore others than copy constructors for specialized nodes
                    if (!isCopyConstructor(constructor)) {
                        continue;
                    }
                }

                CodeExecutableElement superConstructor = createSuperConstructor(clazz, constructor);

                if (superConstructor != null) {
                    if (getModel().isGeneric() && node.isPolymorphic()) {
                        CodeTree body = superConstructor.getBodyTree();
                        CodeTreeBuilder builder = superConstructor.createBuilder();
                        builder.tree(body);
                        builder.statement("this.next0 = null");
                    }

                    clazz.add(superConstructor);
                }
            }
        }

        protected void createExecuteMethods(SpecializationData specialization) {
            NodeData node = specialization.getNode();
            CodeTypeElement clazz = getElement();

            for (ExecutableTypeData execType : node.getExecutableTypes()) {
                if (execType.isFinal()) {
                    continue;
                }
                CodeExecutableElement executeMethod = createExecutableTypeOverride(execType, true);
                clazz.add(executeMethod);
                CodeTreeBuilder builder = executeMethod.createBuilder();
                CodeTree result = createExecuteBody(builder, specialization, execType);
                if (result != null) {
                    builder.tree(result);
                } else {
                    clazz.remove(executeMethod);
                }
            }
        }

        protected void createCachedExecuteMethods(SpecializationData specialization) {
            NodeData node = specialization.getNode();
            CodeTypeElement clazz = getElement();
            for (SpecializationData polymorphic : node.getPolymorphicSpecializations()) {
                if (!specialization.getSignature().isCompatibleTo(polymorphic.getSignature())) {
                    continue;
                }
                ExecutableElement executeCached = nodeGen.getMethod(executeCachedName(polymorphic));
                ExecutableTypeData execType = new ExecutableTypeData(polymorphic, executeCached, node.getTypeSystem(), polymorphic.getReturnType().getTypeSystemType());

                CodeExecutableElement executeMethod = createExecutableTypeOverride(execType, false);
                CodeTreeBuilder builder = executeMethod.createBuilder();

                if (specialization.isGeneric() || specialization.isPolymorphic()) {
                    builder.startThrow().startNew(getContext().getType(AssertionError.class));
                    builder.doubleQuote("Should not be reached.");
                    builder.end().end();
                } else if (specialization.isUninitialized()) {
                    builder.tree(createAppendPolymorphic(builder, specialization));
                } else {
                    CodeTreeBuilder elseBuilder = new CodeTreeBuilder(builder);
                    elseBuilder.startReturn().startCall("this.next0", executeCachedName(polymorphic));
                    addInternalValueParameterNames(elseBuilder, polymorphic, polymorphic, null, true, true);
                    elseBuilder.end().end();
                    CodeTreeBuilder execute = new CodeTreeBuilder(builder);
                    execute.tree(createGenericInvoke(builder, polymorphic, specialization));
                    boolean forceElse = !specialization.getExceptions().isEmpty();
                    builder.tree(createGuardAndCast(builder, null, polymorphic, specialization, true, execute.getRoot(), elseBuilder.getRoot(), true, forceElse));
                }
                clazz.add(executeMethod);
            }
        }

        private CodeTree createAppendPolymorphic(CodeTreeBuilder parent, SpecializationData specialization) {
            NodeData node = specialization.getNode();

            CodeTreeBuilder builder = new CodeTreeBuilder(parent);
            builder.startStatement().startStaticCall(getContext().getTruffleTypes().getCompilerDirectives(), "transferToInterpreter").end().end();

            builder.declaration(getContext().getTruffleTypes().getNode(), "root", "this");
            builder.declaration(getContext().getType(int.class), "depth", "0");
            builder.tree(createFindRoot(builder, node, true));
            builder.newLine();

            builder.startIf().string("depth > ").string(String.valueOf(node.getPolymorphicDepth())).end();
            builder.startBlock();
            String message = ("Polymorphic limit reached (" + node.getPolymorphicDepth() + ")");
            builder.tree(createGenericInvoke(builder, node.getGenericPolymorphicSpecialization(), node.getGenericSpecialization(),
                            createReplaceCall(builder, node.getGenericSpecialization(), "root", message)));
            builder.end();

            builder.startElseBlock();
            builder.startStatement().startCall("setNext0");
            builder.startNew(nodeSpecializationClassName(node.getUninitializedSpecialization())).string("this").end();
            builder.end().end();

            CodeTreeBuilder specializeCall = new CodeTreeBuilder(builder);
            specializeCall.startCall(EXECUTE_SPECIALIZE_NAME);
            specializeCall.string("0");
            addInternalValueParameterNames(specializeCall, specialization, node.getGenericSpecialization(), null, true, true);
            specializeCall.startGroup().doubleQuote("Uninitialized polymorphic (").string(" + depth + ").doubleQuote("/" + node.getPolymorphicDepth() + ")").end();
            specializeCall.end().end();

            builder.declaration(node.getGenericSpecialization().getReturnType().getType(), "result", specializeCall.getRoot());

            builder.startStatement().string("(").cast(nodePolymorphicClassName(node, node.getGenericPolymorphicSpecialization())).string("root).optimizeTypes()").end();

            if (Utils.isVoid(builder.findMethod().getReturnType())) {
                builder.returnStatement();
            } else {
                builder.startReturn().string("result").end();
            }

            builder.end();

            return builder.getRoot();
        }

        private CodeTree createExecuteBody(CodeTreeBuilder parent, SpecializationData specialization, ExecutableTypeData execType) {
            TypeData primaryType = specialization.getReturnType().getTypeSystemType();

            CodeTreeBuilder builder = new CodeTreeBuilder(parent);

            List<ExecutableTypeData> primaryExecutes = findFunctionalExecutableType(specialization, execType.getEvaluatedCount());

            if (primaryExecutes.contains(execType) || primaryExecutes.isEmpty()) {
                builder.tree(createFunctionalExecute(builder, specialization, execType));
            } else if (needsCastingExecuteMethod(execType, primaryType)) {
                assert !primaryExecutes.isEmpty();
                builder.tree(createCastingExecute(builder, specialization, execType, primaryExecutes.get(0)));
            } else {
                return null;
            }

            return builder.getRoot();
        }

        private CodeExecutableElement createExecutableTypeOverride(ExecutableTypeData execType, boolean evaluated) {
            CodeExecutableElement method = CodeExecutableElement.clone(getContext().getEnvironment(), execType.getMethod());

            int i = 0;
            for (VariableElement param : method.getParameters()) {
                CodeVariableElement var = CodeVariableElement.clone(param);
                ActualParameter actualParameter = execType.getParameters().get(i);
                if (evaluated && actualParameter.getSpecification().isSignature()) {
                    var.setName(valueNameEvaluated(actualParameter));
                } else {
                    var.setName(valueName(actualParameter));
                }
                method.getParameters().set(i, var);
                i++;
            }

            method.getAnnotationMirrors().clear();
            method.getModifiers().remove(Modifier.ABSTRACT);
            return method;
        }

        private boolean needsCastingExecuteMethod(ExecutableTypeData execType, TypeData primaryType) {
            if (execType.isAbstract()) {
                return true;
            }
            if (Utils.isPrimitiveOrVoid(primaryType.getPrimitiveType()) && Utils.isPrimitiveOrVoid(execType.getType().getPrimitiveType())) {
                return true;
            }
            if (execType.getType().isGeneric()) {
                return true;
            }
            return false;
        }

        private List<ExecutableTypeData> findFunctionalExecutableType(SpecializationData specialization, int evaluatedCount) {
            TypeData primaryType = specialization.getReturnType().getTypeSystemType();
            List<ExecutableTypeData> otherTypes = specialization.getNode().getExecutableTypes(evaluatedCount);

            List<ExecutableTypeData> filteredTypes = new ArrayList<>();
            for (ExecutableTypeData compareType : otherTypes) {
                if (!Utils.typeEquals(compareType.getType().getPrimitiveType(), primaryType.getPrimitiveType())) {
                    continue;
                }
                filteredTypes.add(compareType);
            }

            // no direct matches found use generic where the type is Object
            if (filteredTypes.isEmpty()) {
                for (ExecutableTypeData compareType : otherTypes) {
                    if (compareType.getType().isGeneric() && !compareType.hasUnexpectedValue(getContext())) {
                        filteredTypes.add(compareType);
                    }
                }
            }

            if (filteredTypes.isEmpty()) {
                for (ExecutableTypeData compareType : otherTypes) {
                    if (compareType.getType().isGeneric()) {
                        filteredTypes.add(compareType);
                    }
                }
            }

            return filteredTypes;
        }

        private CodeTree createFunctionalExecute(CodeTreeBuilder parent, SpecializationData specialization, ExecutableTypeData executable) {
            CodeTreeBuilder builder = new CodeTreeBuilder(parent);
            if (specialization.isUninitialized()) {
                builder.tree(createDeoptimize(builder));
            }

            builder.tree(createExecuteChildren(builder, executable, specialization, specialization.getParameters(), null, false));

            CodeTree executeNode = createExecute(builder, executable, specialization);

            CodeTree returnSpecialized = null;

            if (specialization.findNextSpecialization() != null) {
                CodeTreeBuilder returnBuilder = new CodeTreeBuilder(builder);
                returnBuilder.tree(createDeoptimize(builder));
                returnBuilder.tree(createReturnExecuteAndSpecialize(builder, executable, specialization, null, "One of guards " + specialization.getGuardDefinitions() + " failed"));
                returnSpecialized = returnBuilder.getRoot();
            }

            builder.tree(createGuardAndCast(builder, null, specialization, specialization, true, executeNode, returnSpecialized, false, false));

            return builder.getRoot();
        }

        private CodeTree createExecute(CodeTreeBuilder parent, ExecutableTypeData executable, SpecializationData specialization) {
            NodeData node = specialization.getNode();
            CodeTreeBuilder builder = new CodeTreeBuilder(parent);
            if (!specialization.getExceptions().isEmpty() || !specialization.getAssumptions().isEmpty()) {
                builder.startTryBlock();
            }

            for (String assumption : specialization.getAssumptions()) {
                builder.startStatement();
                builder.string("this.").string(assumption).string(".check()");
                builder.end();
            }

            CodeTreeBuilder returnBuilder = new CodeTreeBuilder(parent);
            if (specialization.isPolymorphic()) {
                returnBuilder.startCall("next0", executeCachedName(specialization));
                addInternalValueParameterNames(returnBuilder, specialization, specialization, null, true, true);
                returnBuilder.end();
            } else if (specialization.isUninitialized()) {
                returnBuilder.startCall("super", EXECUTE_SPECIALIZE_NAME);
                returnBuilder.string("0");
                addInternalValueParameterNames(returnBuilder, specialization, specialization, null, true, true);
                returnBuilder.doubleQuote("Uninitialized monomorphic");
                returnBuilder.end();
            } else if (specialization.getMethod() == null && !node.needsRewrites(context)) {
                emitEncounteredSynthetic(builder, specialization);
            } else if (specialization.isGeneric()) {
                returnBuilder.startCall("super", EXECUTE_GENERIC_NAME);
                addInternalValueParameterNames(returnBuilder, specialization, specialization, null, node.needsFrame(), true);
                returnBuilder.end();
            } else {
                returnBuilder.tree(createTemplateMethodCall(returnBuilder, null, specialization, specialization, null));
            }

            if (!returnBuilder.isEmpty()) {

                ExecutableTypeData sourceExecutableType = node.findExecutableType(specialization.getReturnType().getTypeSystemType(), 0);
                boolean sourceThrowsUnexpected = sourceExecutableType != null && sourceExecutableType.hasUnexpectedValue(getContext());
                boolean targetSupportsUnexpected = executable.hasUnexpectedValue(getContext());

                TypeData targetType = node.getTypeSystem().findTypeData(builder.findMethod().getReturnType());
                TypeData sourceType = specialization.getReturnType().getTypeSystemType();

                if (specialization.isPolymorphic() && sourceThrowsUnexpected && !targetSupportsUnexpected) {
                    builder.startTryBlock();
                }
                builder.startReturn();
                if (targetType == null || sourceType == null) {
                    builder.tree(returnBuilder.getRoot());
                } else if (sourceType.needsCastTo(getContext(), targetType)) {
                    builder.tree(createCallTypeSystemMethod(context, parent, node, TypeSystemCodeGenerator.expectTypeMethodName(targetType), returnBuilder.getRoot()));
                } else {
                    builder.tree(returnBuilder.getRoot());
                }
                builder.end();
                if (specialization.isPolymorphic() && sourceThrowsUnexpected && !targetSupportsUnexpected) {
                    builder.end();
                    builder.startCatchBlock(getUnexpectedValueException(), "ex");
                    builder.startReturn();
                    CodeTree returns = CodeTreeBuilder.singleString("ex.getResult()");
                    if (sourceType.needsCastTo(getContext(), targetType)) {
                        builder.tree(createCallTypeSystemMethod(context, parent, node, TypeSystemCodeGenerator.asTypeMethodName(targetType), returns));
                    } else {
                        builder.tree(returns);
                    }
                    builder.end();
                    builder.end();
                }
            }

            if (!specialization.getExceptions().isEmpty()) {
                for (SpecializationThrowsData exception : specialization.getExceptions()) {
                    builder.end().startCatchBlock(exception.getJavaClass(), "ex");
                    builder.tree(createDeoptimize(builder));
                    builder.tree(createReturnExecuteAndSpecialize(parent, executable, specialization, null, "Thrown " + Utils.getSimpleName(exception.getJavaClass())));
                }
                builder.end();
            }
            if (!specialization.getAssumptions().isEmpty()) {
                builder.end().startCatchBlock(getContext().getTruffleTypes().getInvalidAssumption(), "ex");
                builder.tree(createReturnExecuteAndSpecialize(parent, executable, specialization, null, "Assumption failed"));
                builder.end();
            }

            return builder.getRoot();
        }

    }

    private interface CodeBlock<T> {

        CodeTree create(CodeTreeBuilder parent, T value);

    }
}
