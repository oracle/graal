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
import com.oracle.truffle.dsl.processor.node.SpecializationGroup.TypeGuard;
import com.oracle.truffle.dsl.processor.template.*;
import com.oracle.truffle.dsl.processor.template.TemplateMethod.Signature;
import com.oracle.truffle.dsl.processor.typesystem.*;
import com.sun.org.apache.xml.internal.dtm.ref.DTMDefaultBaseIterators.*;

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

    private static String nodeCastClassName(NodeData node, TypeData type) {
        String nodeid = resolveNodeId(node);
        if (type == null) {
            return nodeid + "ImplicitCast";
        } else {
            return Utils.firstLetterUpperCase(Utils.getSimpleName(type.getPrimitiveType())) + "Cast";
        }
    }

    private static String nodeSpecializationClassName(SpecializationData specialization) {
        String nodeid = resolveNodeId(specialization.getNode());
        String name = Utils.firstLetterUpperCase(nodeid);
        name += Utils.firstLetterUpperCase(specialization.getId());
        name += "Node";
        return name;
    }

    private static String nodePolymorphicClassName(NodeData node, SpecializationData specialization) {
        String nodeid = resolveNodeId(node);

        String name = Utils.firstLetterUpperCase(nodeid);
        if (specialization == node.getGenericPolymorphicSpecialization()) {
            name += "PolymorphicNode";
        } else {
            name += "Polymorphic" + polymorphicIndex(node, specialization) + "Node";
        }
        return name;
    }

    private static String resolveNodeId(NodeData node) {
        String nodeid = node.getNodeId();
        if (nodeid.endsWith("Node") && !nodeid.equals("Node")) {
            nodeid = nodeid.substring(0, nodeid.length() - 4);
        }
        return nodeid;
    }

    private static String valueNameEvaluated(ActualParameter targetParameter) {
        return valueName(targetParameter) + "Evaluated";
    }

    private static String typeName(ActualParameter param) {
        return param.getLocalName() + "Type";
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

    private void addInternalValueParameterNames(CodeTreeBuilder builder, TemplateMethod source, TemplateMethod specialization, String unexpectedValueName, boolean forceFrame, boolean includeImplicit,
                    Map<String, String> customNames) {
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

            if (customNames != null && customNames.containsKey(parameter.getLocalName())) {
                builder.string(customNames.get(parameter.getLocalName()));
            } else if (unexpectedValueName != null && parameter.getLocalName().equals(unexpectedValueName)) {
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
            TypeMirror targetType = targetParameter.getType();

            if (targetParameter.isImplicit() || valueParameter.isImplicit()) {
                continue;
            }

            TypeMirror valueType = null;
            if (valueParameter != null) {
                valueType = valueParameter.getType();
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
            } else if (!Utils.needsCastTo(getContext(), valueType, targetType)) {
                builder.startGroup();
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
        String nodeid = resolveNodeId(node);
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
        addInternalValueParameterNames(builder, current, current, null, false, true, null);
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
    @SuppressWarnings("unchecked")
    protected void createChildren(NodeData node) {
        List<CodeTypeElement> casts = new ArrayList<>(getElement().getEnclosedElements());
        getElement().getEnclosedElements().clear();

        Map<NodeData, List<TypeElement>> childTypes = new LinkedHashMap<>();
        if (node.getDeclaredNodes() != null && !node.getDeclaredNodes().isEmpty()) {
            for (NodeData nodeChild : node.getDeclaredNodes()) {
                NodeCodeGenerator generator = new NodeCodeGenerator(getContext());
                childTypes.put(nodeChild, generator.process(null, nodeChild).getEnclosedElements());
            }
        }

        if (node.needsFactory() || node.getNodeDeclaringChildren().size() > 0) {
            NodeFactoryFactory factory = new NodeFactoryFactory(context, childTypes);
            add(factory, node);
            factory.getElement().getEnclosedElements().addAll(casts);
        }
    }

    protected CodeTree createCastType(NodeData node, TypeData sourceType, TypeData targetType, boolean expect, CodeTree value) {
        if (targetType == null) {
            return value;
        } else if (sourceType != null && !sourceType.needsCastTo(getContext(), targetType)) {
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
                    if (Utils.isObject(param.asType())) {
                        continue;
                    }
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
                    if (!Utils.isObject(param.asType())) {
                        builder.string("(").type(param.asType()).string(") ");
                    }
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
                    CodeTreeBuilder builder = method.createBuilder();
                    builder.startReturn().string("this.").string(child.getName()).end();
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

            SpecializationGroup rootGroup = createSpecializationGroups(node);

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

                for (CodeExecutableElement method : createImplicitChildrenAccessors(node, clazz)) {
                    clazz.add(method);
                }

                clazz.add(createGenericExecuteAndSpecialize(node, rootGroup));
                clazz.add(createInfoMessage(node));
            }

            if (node.getGenericSpecialization() != null && node.getGenericSpecialization().isReachable()) {
                clazz.add(createGenericExecute(node, rootGroup));
            }
        }

        private List<CodeExecutableElement> createImplicitChildrenAccessors(NodeData node, CodeTypeElement clazz) {
            List<CodeExecutableElement> methods = new ArrayList<>();
            Map<NodeChildData, Set<TypeData>> expectTypes = new HashMap<>();
            for (ExecutableTypeData executableType : node.getExecutableTypes()) {
                for (int i = 0; i < executableType.getEvaluatedCount(); i++) {
                    ActualParameter parameter = executableType.getSignatureParameter(i);
                    NodeChildData child = node.findChild(parameter.getSpecification().getName());
                    Set<TypeData> types = expectTypes.get(child);
                    if (types == null) {
                        types = new TreeSet<>();
                        expectTypes.put(child, types);
                    }
                    types.add(parameter.getTypeSystemType());
                }
            }

            Map<NodeChildData, Set<TypeData>> visitedMap = new HashMap<>();
            for (SpecializationData spec : node.getSpecializations()) {
                for (ActualParameter param : spec.getParameters()) {
                    if (!param.getSpecification().isSignature()) {
                        continue;
                    }
                    NodeChildData child = node.findChild(param.getSpecification().getName());
                    Set<TypeData> visitedTypeData = visitedMap.get(child);
                    if (visitedTypeData == null) {
                        visitedTypeData = new TreeSet<>();
                        visitedMap.put(child, visitedTypeData);
                    }
                    if (visitedTypeData.contains(param.getTypeSystemType())) {
                        continue;
                    }
                    visitedTypeData.add(param.getTypeSystemType());

                    Set<TypeData> expect = expectTypes.get(child);
                    if (expect == null) {
                        expect = Collections.emptySet();
                    }

                    methods.addAll(createExecuteChilds(param, expect));
                }
            }
            return methods;
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
            ExecutableElement sourceSectionConstructor = null;
            if (constructors.isEmpty()) {
                clazz.add(createUserConstructor(clazz, null));
            } else {
                for (ExecutableElement constructor : constructors) {
                    clazz.add(createUserConstructor(clazz, constructor));
                    if (NodeParser.isSourceSectionConstructor(context, constructor)) {
                        sourceSectionConstructor = constructor;
                    }
                }
            }
            if (node.needsRewrites(getContext())) {
                clazz.add(createCopyConstructor(clazz, findCopyConstructor(node.getNodeType()), sourceSectionConstructor));
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

                NodeChildData child = node.findChild(fieldName);

                CodeTree init = createStaticCast(builder, child, fieldName);
                init = createAdoptChild(builder, var.asType(), init);

                builder.string("this.").string(fieldName).string(" = ").tree(init);
                builder.end();
            }
            return method;
        }

        private CodeTree createStaticCast(CodeTreeBuilder parent, NodeChildData child, String fieldName) {
            NodeData parentNode = getModel().getNode();
            if (child != null) {
                CreateCastData createCast = parentNode.findCast(child.getName());
                if (createCast != null) {
                    return createTemplateMethodCall(parent, null, parentNode.getGenericSpecialization(), createCast, null, fieldName);
                }
            }
            return CodeTreeBuilder.singleString(fieldName);
        }

        private CodeTree createAdoptChild(CodeTreeBuilder parent, TypeMirror type, CodeTree value) {
            CodeTreeBuilder builder = new CodeTreeBuilder(parent);
            if (Utils.isAssignable(getContext(), type, getContext().getTruffleTypes().getNode())) {
                builder.string("adoptChild(").tree(value).string(")");
            } else if (Utils.isAssignable(getContext(), type, getContext().getTruffleTypes().getNodeArray())) {
                builder.string("adoptChildren(").tree(value).string(")");
            } else {
                builder.tree(value);
            }
            return builder.getRoot();
        }

        private CodeExecutableElement createCopyConstructor(CodeTypeElement type, ExecutableElement superConstructor, ExecutableElement sourceSectionConstructor) {
            CodeExecutableElement method = new CodeExecutableElement(null, type.getSimpleName().toString());
            CodeTreeBuilder builder = method.createBuilder();
            method.getParameters().add(new CodeVariableElement(type.asType(), "copy"));

            if (superConstructor != null) {
                builder.startStatement().startSuperCall().string("copy").end().end();
            } else if (sourceSectionConstructor != null) {
                builder.startStatement().startSuperCall().string("copy.getSourceSection()").end().end();
            }

            for (VariableElement var : type.getFields()) {
                builder.startStatement();
                final String varName = var.getSimpleName().toString();
                final TypeMirror varType = var.asType();

                String copyAccess = "copy." + varName;
                if (Utils.isAssignable(getContext(), varType, getContext().getTruffleTypes().getNodeArray())) {
                    copyAccess += ".clone()";
                }
                CodeTree init = createAdoptChild(builder, varType, CodeTreeBuilder.singleString(copyAccess));
                builder.startStatement().string("this.").string(varName).string(" = ").tree(init).end();
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
            TypeMirror type = child.getNodeType();
            CodeVariableElement var = new CodeVariableElement(type, child.getName());
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

        private CodeExecutableElement createGenericExecuteAndSpecialize(final NodeData node, SpecializationGroup rootGroup) {
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

            String currentNode = "this";
            for (SpecializationData specialization : node.getSpecializations()) {
                if (!specialization.getExceptions().isEmpty()) {
                    currentNode = "current";
                    builder.declaration(baseClassName(node), currentNode, "this");
                    break;
                }
            }

            builder.startStatement().string("String message = ").startCall("createInfo0").string("reason");
            addInternalValueParameterNames(builder, node.getGenericSpecialization(), node.getGenericSpecialization(), null, false, true, null);
            builder.end().end();

            final String currentNodeVar = currentNode;
            builder.tree(createExecuteTree(builder, node.getGenericSpecialization(), rootGroup, true, new CodeBlock<SpecializationData>() {

                public CodeTree create(CodeTreeBuilder b, SpecializationData current) {
                    return createGenericInvokeAndSpecialize(b, node.getGenericSpecialization(), current, currentNodeVar);
                }
            }, null, false, true));

            boolean firstUnreachable = true;
            for (SpecializationData current : node.getSpecializations()) {
                if (current.isUninitialized() || current.isReachable()) {
                    continue;
                }
                if (firstUnreachable) {
                    emitEncounteredSynthetic(builder, current);
                    firstUnreachable = false;
                }
            }
            emitUnreachableSpecializations(builder, node);

            return method;
        }

        private SpecializationGroup createSpecializationGroups(final NodeData node) {
            List<SpecializationData> specializations = node.getSpecializations();
            List<SpecializationData> filteredSpecializations = new ArrayList<>();
            for (SpecializationData current : specializations) {
                if (current.isUninitialized() || !current.isReachable()) {
                    continue;
                }
                filteredSpecializations.add(current);
            }

            return SpecializationGroup.create(filteredSpecializations);
        }

        private CodeExecutableElement createGenericExecute(NodeData node, SpecializationGroup group) {
            TypeMirror genericReturnType = node.getGenericSpecialization().getReturnType().getType();
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PROTECTED), genericReturnType, EXECUTE_GENERIC_NAME);

            method.getAnnotationMirrors().add(new CodeAnnotationMirror(getContext().getTruffleTypes().getSlowPath()));

            addInternalValueParameters(method, node.getGenericSpecialization(), node.needsFrame(), false);
            final CodeTreeBuilder builder = method.createBuilder();

            builder.tree(createExecuteTree(builder, node.getGenericSpecialization(), group, false, new CodeBlock<SpecializationData>() {

                public CodeTree create(CodeTreeBuilder b, SpecializationData current) {
                    return createGenericInvoke(builder, current.getNode().getGenericSpecialization(), current);
                }
            }, null, false, true));

            emitUnreachableSpecializations(builder, node);

            return method;
        }

        private void emitUnreachableSpecializations(final CodeTreeBuilder builder, NodeData node) {
            for (SpecializationData current : node.getSpecializations()) {
                if (current.isUninitialized() || current.isReachable()) {
                    continue;
                }
                builder.string("// unreachable ").string(current.getId()).newLine();
            }
        }

        protected CodeTree createExecuteTree(CodeTreeBuilder outerParent, final SpecializationData source, final SpecializationGroup group, final boolean checkMinimumState,
                        final CodeBlock<SpecializationData> guardedblock, final CodeTree elseBlock, boolean forceElse, final boolean emitAssumptions) {
            return guard(outerParent, source, group, checkMinimumState, new CodeBlock<Integer>() {

                public CodeTree create(CodeTreeBuilder parent, Integer ifCount) {
                    CodeTreeBuilder builder = parent.create();

                    if (group.getSpecialization() != null) {
                        builder.tree(guardedblock.create(builder, group.getSpecialization()));

                        assert group.getChildren().isEmpty() : "missed a specialization";

                    } else {
                        for (SpecializationGroup childGroup : group.getChildren()) {
                            builder.tree(createExecuteTree(builder, source, childGroup, checkMinimumState, guardedblock, null, false, emitAssumptions));
                        }
                    }

                    return builder.getRoot();
                }
            }, elseBlock, forceElse, emitAssumptions);
        }

        private CodeTree guard(CodeTreeBuilder parent, SpecializationData source, SpecializationGroup group, boolean checkMinimumState, CodeBlock<Integer> bodyBlock, CodeTree elseBlock,
                        boolean forceElse, boolean emitAssumptions) {
            CodeTreeBuilder builder = parent.create();

            int ifCount = emitGuards(builder, source, group, checkMinimumState, emitAssumptions);

            if (isReachableGroup(group, ifCount, checkMinimumState)) {
                builder.tree(bodyBlock.create(builder, ifCount));
            }

            builder.end(ifCount);

            if (elseBlock != null) {
                if (ifCount > 0 || forceElse) {
                    builder.tree(elseBlock);
                }
            }

            return builder.getRoot();
        }

        private boolean isReachableGroup(SpecializationGroup group, int ifCount, boolean checkMinimumState) {
            if (ifCount != 0) {
                return true;
            }
            SpecializationGroup previous = group.getPreviousGroup();
            if (previous == null || previous.getElseConnectableGuards().isEmpty()) {
                return true;
            }

            /*
             * Hacky else case. In this case the specialization is not reachable due to previous
             * else branch. This is only true if the minimum state is not checked.
             */
            if (previous.getGuards().size() == 1 && previous.getTypeGuards().isEmpty() && previous.getAssumptions().isEmpty() && !checkMinimumState &&
                            (previous.getParent() == null || previous.getMaxSpecializationIndex() != previous.getParent().getMaxSpecializationIndex())) {
                return false;
            }

            return true;
        }

        private int emitGuards(CodeTreeBuilder builder, SpecializationData source, SpecializationGroup group, boolean checkMinimumState, boolean emitAssumptions) {
            NodeData node = source.getNode();

            CodeTreeBuilder guardsBuilder = builder.create();
            CodeTreeBuilder castBuilder = builder.create();
            CodeTreeBuilder guardsCastBuilder = builder.create();

            String guardsAnd = "";
            String guardsCastAnd = "";

            List<GuardData> elseGuards = group.getElseConnectableGuards();

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

            if (emitAssumptions) {
                for (String assumption : group.getAssumptions()) {
                    guardsBuilder.string(guardsAnd);
                    guardsBuilder.string("this");
                    guardsBuilder.string(".").string(assumption).string(".isValid()");
                    guardsAnd = " && ";
                }
            }

            for (TypeGuard typeGuard : group.getTypeGuards()) {
                ActualParameter valueParam = source.getSignatureParameter(typeGuard.getSignatureIndex());

                if (valueParam == null) {
                    /*
                     * If used inside a execute evaluated method then the value param may not exist.
                     * In that case we assume that the value is executed generic or of the current
                     * specialization.
                     */
                    if (group.getSpecialization() != null) {
                        valueParam = group.getSpecialization().getSignatureParameter(typeGuard.getSignatureIndex());
                    } else {
                        valueParam = node.getGenericSpecialization().getSignatureParameter(typeGuard.getSignatureIndex());
                    }
                }

                NodeChildData child = node.findChild(valueParam.getSpecification().getName());
                if (child == null) {
                    throw new IllegalStateException();
                }

                CodeTree implicitGuard = createTypeGuard(guardsBuilder, child, valueParam, typeGuard.getType());
                if (implicitGuard != null) {
                    guardsBuilder.string(guardsAnd);
                    guardsBuilder.tree(implicitGuard);
                    guardsAnd = " && ";
                }

                CodeTree cast = createCast(castBuilder, child, valueParam, typeGuard.getType(), checkMinimumState);
                if (cast != null) {
                    castBuilder.tree(cast);
                }
            }

            for (GuardData guard : group.getGuards()) {
                if (elseGuards.contains(guard)) {
                    continue;
                }

                if (needsTypeGuard(source, group, guard)) {
                    guardsCastBuilder.tree(createMethodGuard(builder, guardsCastAnd, source, guard));
                    guardsCastAnd = " && ";
                } else {
                    guardsBuilder.tree(createMethodGuard(builder, guardsAnd, source, guard));
                    guardsAnd = " && ";
                }
            }

            int ifCount = startGuardIf(builder, guardsBuilder, 0, elseGuards);
            builder.tree(castBuilder.getRoot());
            ifCount = startGuardIf(builder, guardsCastBuilder, ifCount, elseGuards);
            return ifCount;
        }

        private int startGuardIf(CodeTreeBuilder builder, CodeTreeBuilder conditionBuilder, int ifCount, List<GuardData> elseGuard) {
            int newIfCount = ifCount;

            if (!conditionBuilder.isEmpty()) {
                if (ifCount == 0 && !elseGuard.isEmpty()) {
                    builder.startElseIf();
                } else {
                    builder.startIf();
                }
                builder.tree(conditionBuilder.getRoot());
                builder.end().startBlock();
                newIfCount++;
            } else if (ifCount == 0 && !elseGuard.isEmpty()) {
                builder.startElseBlock();
                newIfCount++;
            }
            return newIfCount;
        }

        private boolean needsTypeGuard(SpecializationData source, SpecializationGroup group, GuardData guard) {
            int signatureIndex = 0;
            for (ActualParameter parameter : guard.getParameters()) {
                if (!parameter.getSpecification().isSignature()) {
                    continue;
                }

                TypeGuard typeGuard = group.findTypeGuard(signatureIndex);
                if (typeGuard != null) {
                    TypeData requiredType = typeGuard.getType();

                    ActualParameter sourceParameter = source.findParameter(parameter.getLocalName());
                    if (sourceParameter == null) {
                        sourceParameter = source.getNode().getGenericSpecialization().findParameter(parameter.getLocalName());
                    }

                    if (Utils.needsCastTo(getContext(), sourceParameter.getType(), requiredType.getPrimitiveType())) {
                        return true;
                    }
                }

                signatureIndex++;
            }
            return false;
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

            String castMethodName;
            List<TypeData> types = getModel().getNode().getTypeSystem().lookupSourceTypes(targetType);
            if (types.size() > 1) {
                castMethodName = TypeSystemCodeGenerator.isImplicitTypeMethodName(targetType);
            } else {
                castMethodName = TypeSystemCodeGenerator.isTypeMethodName(targetType);
            }

            startCallTypeSystemMethod(getContext(), builder, node, castMethodName);
            builder.string(valueName(source));
            builder.end().end(); // call

            if (field.isShortCircuit()) {
                builder.string(")");
            }

            builder.end(); // group

            return builder.getRoot();
        }

        private CodeTree createCast(CodeTreeBuilder parent, NodeChildData field, ActualParameter source, TypeData targetType, boolean checkMinimumState) {
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

            String castMethodName;
            List<TypeData> types = getModel().getNode().getTypeSystem().lookupSourceTypes(targetType);
            if (types.size() > 1) {
                castMethodName = TypeSystemCodeGenerator.asImplicitTypeMethodName(targetType);
            } else {
                castMethodName = TypeSystemCodeGenerator.asTypeMethodName(targetType);
            }

            CodeTree value = createCallTypeSystemMethod(context, parent, node, castMethodName, CodeTreeBuilder.singleString(valueName(source)));

            CodeTreeBuilder builder = parent.create();
            builder.tree(createLazyAssignment(parent, castValueName(source), targetType.getPrimitiveType(), condition, value));
            if (checkMinimumState && types.size() > 1) {
                CodeTree castType = createCallTypeSystemMethod(context, parent, node, TypeSystemCodeGenerator.getImplicitClass(targetType), CodeTreeBuilder.singleString(valueName(source)));
                builder.tree(createLazyAssignment(builder, typeName(source), getContext().getType(Class.class), condition, castType));
            }

            return builder.getRoot();
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

            return encloseThrowsWithFallThrough(current, builder.getRoot());
        }

        protected CodeTree createGenericInvokeAndSpecialize(CodeTreeBuilder parent, SpecializationData source, SpecializationData current, String currentNodeVar) {
            CodeTreeBuilder builder = parent.create();
            CodeTreeBuilder prefix = parent.create();

            NodeData node = current.getNode();

            if (current.isGeneric() && node.isPolymorphic()) {
                builder.startIf().string(currentNodeVar).string(".next0 == null && minimumState > 0").end().startBlock();
                builder.tree(createRewritePolymorphic(builder, node, currentNodeVar));
                builder.end();
                builder.startElseBlock();
                builder.tree(createRewriteGeneric(builder, source, current, currentNodeVar));
                builder.end();
            } else {
                if (current.getExceptions().isEmpty()) {
                    builder.tree(createGenericInvoke(builder, source, current, createReplaceCall(builder, current, currentNodeVar, currentNodeVar, null), null));
                } else {
                    builder.startStatement().string(currentNodeVar).string(" = ").tree(createReplaceCall(builder, current, currentNodeVar, currentNodeVar, null)).end();
                    builder.tree(createGenericInvoke(builder, source, current, null, CodeTreeBuilder.singleString(currentNodeVar)));
                }
            }
            CodeTreeBuilder root = parent.create();
            root.tree(prefix.getRoot());
            root.tree(encloseThrowsWithFallThrough(current, builder.getRoot()));
            return root.getRoot();
        }

        private CodeTree createRewriteGeneric(CodeTreeBuilder parent, SpecializationData source, SpecializationData current, String currentNode) {
            NodeData node = current.getNode();

            CodeTreeBuilder builder = parent.create();
            builder.declaration(getContext().getTruffleTypes().getNode(), "root", currentNode);
            builder.startIf().string(currentNode).string(".next0 != null").end().startBlock();
            /*
             * Communicates to the caller of executeAndSpecialize that it was rewritten to generic.
             * Its important that this is used instead of the currentNode since the caller is this.
             * CurrentNode may not be this anymore at this place.
             */
            builder.statement("this.next0 = null");
            builder.tree(createFindRoot(builder, node, false));
            builder.end();
            builder.end();
            builder.tree(createGenericInvoke(builder, source, current, createReplaceCall(builder, current, "root", "(" + baseClassName(node) + ") root", null), null));
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

        private CodeTree encloseThrowsWithFallThrough(SpecializationData current, CodeTree tree) {
            if (current.getExceptions().isEmpty()) {
                return tree;
            }
            CodeTreeBuilder builder = new CodeTreeBuilder(null);

            builder.startTryBlock();
            builder.tree(tree);
            for (SpecializationThrowsData exception : current.getExceptions()) {
                builder.end().startCatchBlock(exception.getJavaClass(), "rewriteEx");
                builder.string("// fall through").newLine();
            }
            builder.end();

            return builder.getRoot();
        }

        protected CodeTree createGenericInvoke(CodeTreeBuilder parent, SpecializationData source, SpecializationData current, CodeTree replaceCall, CodeTree replaceVar) {
            assert replaceCall == null || replaceVar == null;
            CodeTreeBuilder builder = parent.create();
            CodeTree replace = replaceVar;
            if (replace == null) {
                replace = replaceCall;
            }
            if (current.isGeneric()) {
                builder.startReturn().tree(replace).string(".").startCall(EXECUTE_GENERIC_NAME);
                addInternalValueParameterNames(builder, source, current, null, current.getNode().needsFrame(), true, null);
                builder.end().end();
            } else if (current.getMethod() == null) {
                if (replaceCall != null) {
                    builder.statement(replaceCall);
                }
                emitEncounteredSynthetic(builder, current);
            } else if (!current.canBeAccessedByInstanceOf(getContext(), source.getNode().getNodeType())) {
                if (replaceCall != null) {
                    builder.statement(replaceCall);
                }
                builder.startReturn().tree(createTemplateMethodCall(parent, null, source, current, null)).end();
            } else {
                replace.add(new CodeTree(CodeTreeKind.STRING, null, "."));
                builder.startReturn().tree(createTemplateMethodCall(parent, replace, source, current, null)).end();
            }
            return builder.getRoot();
        }

        protected CodeTree createReplaceCall(CodeTreeBuilder builder, SpecializationData current, String target, String source, String message) {
            String className = nodeSpecializationClassName(current);
            CodeTreeBuilder replaceCall = builder.create();
            if (target != null) {
                replaceCall.startCall(target, "replace");
            } else {
                replaceCall.startCall("replace");
            }
            replaceCall.startGroup().startNew(className).string(source);
            for (ActualParameter param : current.getParameters()) {
                if (!param.getSpecification().isSignature()) {
                    continue;
                }
                NodeChildData child = getModel().getNode().findChild(param.getSpecification().getName());
                List<TypeData> types = child.getNodeData().getTypeSystem().lookupSourceTypes(param.getTypeSystemType());
                if (types.size() > 1) {
                    replaceCall.string(typeName(param));
                }
            }
            replaceCall.end().end();

            if (message == null) {
                replaceCall.string("message");
            } else {
                replaceCall.doubleQuote(message);
            }
            replaceCall.end();
            return replaceCall.getRoot();
        }

        private CodeTree createRewritePolymorphic(CodeTreeBuilder parent, NodeData node, String currentNode) {
            String polyClassName = nodePolymorphicClassName(node, node.getGenericPolymorphicSpecialization());
            String uninitializedName = nodeSpecializationClassName(node.getUninitializedSpecialization());
            CodeTreeBuilder builder = parent.create();

            builder.declaration(polyClassName, "polymorphic", builder.create().startNew(polyClassName).string(currentNode).end());

            for (ActualParameter param : node.getGenericSpecialization().getParameters()) {
                if (!param.getSpecification().isSignature()) {
                    continue;
                }
                NodeChildData child = node.findChild(param.getSpecification().getName());
                if (child != null) {
                    builder.startStatement().string(currentNode).string(".").string(child.getName());
                    if (child.getCardinality().isMany()) {
                        builder.string("[").string(String.valueOf(param.getIndex())).string("]");
                    }
                    builder.string(" = null").end();
                }
            }
            builder.startStatement().startCall(currentNode, "replace").string("polymorphic").string("message").end().end();
            builder.startStatement().startCall("polymorphic", "setNext0").string(currentNode).end().end();
            builder.startStatement().startCall(currentNode, "setNext0").startNew(uninitializedName).string(currentNode).end().end().end();

            builder.startReturn();
            builder.startCall(currentNode + ".next0", executeCachedName(node.getGenericPolymorphicSpecialization()));
            addInternalValueParameterNames(builder, node.getGenericSpecialization(), node.getGenericSpecialization(), null, true, true, null);
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

            // execute names are enforced no cast
            String[] executeParameterNames = new String[executeParameters.size()];
            for (int i = 0; i < executeParameterNames.length; i++) {
                executeParameterNames[i] = valueName(executeParameters.get(i));
            }

            builder.tree(createExecuteChildren(builder, executable, specialization, executeParameters, null));

            CodeTree primaryExecuteCall = createTemplateMethodCall(builder, null, executable, castExecutable, null, executeParameterNames);
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

        protected CodeTree createExecuteChildren(CodeTreeBuilder parent, ExecutableTypeData sourceExecutable, SpecializationData specialization, List<ActualParameter> targetParameters,
                        ActualParameter unexpectedParameter) {
            CodeTreeBuilder builder = parent.create();
            NodeData node = specialization.getNode();
            for (ActualParameter targetParameter : targetParameters) {
                NodeChildData child = node.findChild(targetParameter.getSpecification().getName());
                if (!targetParameter.getSpecification().isSignature()) {
                    continue;
                }
                TypeData targetType = targetParameter.getTypeSystemType();
                ExecutableTypeData targetExecutable = null;
                if (child != null) {
                    targetExecutable = child.findExecutableType(getContext(), targetType);
                }

                if (targetExecutable == null) {
                    // TODO what to do? assertion?
                    continue;
                }

                CodeTree executionExpressions = createExecutionExpresssions(builder, child, sourceExecutable, targetExecutable, targetParameter, unexpectedParameter);

                String targetVarName = valueName(targetParameter);
                CodeTree unexpectedTree = createCatchUnexpectedTree(builder, executionExpressions, targetVarName, specialization, sourceExecutable, targetExecutable, targetParameter,
                                isShortCircuit(child));
                CodeTree shortCircuitTree = createShortCircuitTree(builder, unexpectedTree, targetVarName, specialization, targetParameter, unexpectedParameter);

                if (shortCircuitTree == executionExpressions) {
                    if (containsNewLine(executionExpressions)) {
                        builder.declaration(sourceExecutable.getType().getPrimitiveType(), targetVarName);
                        builder.tree(shortCircuitTree);
                    } else {
                        builder.startStatement().type(targetParameter.getType()).string(" ").tree(shortCircuitTree).end();
                    }
                } else {
                    builder.tree(shortCircuitTree);
                }

            }
            return builder.getRoot();
        }

        private CodeTree createExecutionExpresssions(CodeTreeBuilder parent, NodeChildData child, ExecutableTypeData sourceExecutable, ExecutableTypeData targetExecutable, ActualParameter param,
                        ActualParameter unexpectedParameter) {
            CodeTreeBuilder builder = parent.create();

            ActualParameter sourceParameter = sourceExecutable.findParameter(param.getLocalName());

            String childExecuteName = createExecuteChildMethodName(param, sourceParameter != null);
            if (childExecuteName != null) {
                builder.string(valueName(param));
                builder.string(" = ");
                builder.startCall(childExecuteName);

                for (ActualParameter parameters : sourceExecutable.getParameters()) {
                    if (parameters.getSpecification().isSignature()) {
                        continue;
                    }
                    builder.string(parameters.getLocalName());
                }

                if (sourceParameter != null) {
                    builder.string(valueNameEvaluated(sourceParameter));
                }

                builder.string(typeName(param));

                builder.end();
            } else {
                TypeData expectType = sourceParameter != null ? sourceParameter.getTypeSystemType() : null;
                builder.tree(createExecuteExpression(parent, child, expectType, targetExecutable, param, unexpectedParameter, null));
            }
            return builder.getRoot();
        }

        private String createExecuteChildMethodName(ActualParameter param, boolean expect) {
            NodeChildData child = getModel().getNode().findChild(param.getSpecification().getName());
            List<TypeData> sourceTypes = child.getNodeData().getTypeSystem().lookupSourceTypes(param.getTypeSystemType());
            if (sourceTypes.size() <= 1) {
                return null;
            }
            String prefix = expect ? "expect" : "execute";
            return prefix + Utils.firstLetterUpperCase(child.getName()) + Utils.firstLetterUpperCase(Utils.getSimpleName(param.getType())) + param.getIndex();
        }

        private List<CodeExecutableElement> createExecuteChilds(ActualParameter param, Set<TypeData> expectTypes) {
            CodeExecutableElement executeMethod = createExecuteChild(param, null);
            if (executeMethod == null) {
                return Collections.emptyList();
            }
            List<CodeExecutableElement> childs = new ArrayList<>();
            childs.add(executeMethod);

            for (TypeData expectType : expectTypes) {
                CodeExecutableElement method = createExecuteChild(param, expectType);
                if (method != null) {
                    childs.add(method);
                }
            }
            return childs;
        }

        private CodeExecutableElement createExecuteChild(ActualParameter param, TypeData expectType) {
            String childExecuteName = createExecuteChildMethodName(param, expectType != null);
            if (childExecuteName == null) {
                return null;
            }

            NodeData node = getModel().getNode();
            NodeChildData child = node.findChild(param.getSpecification().getName());
            List<TypeData> sourceTypes = node.getTypeSystem().lookupSourceTypes(param.getTypeSystemType());
            assert sourceTypes.size() >= 1;

            CodeExecutableElement method = new CodeExecutableElement(modifiers(PROTECTED, expectType != null ? STATIC : FINAL), param.getType(), childExecuteName);

            method.addParameter(new CodeVariableElement(getContext().getTruffleTypes().getFrame(), "frameValue"));
            if (expectType != null) {
                method.addParameter(new CodeVariableElement(expectType.getPrimitiveType(), valueNameEvaluated(param)));
            }
            method.addParameter(new CodeVariableElement(getContext().getType(Class.class), typeName(param)));
            CodeTreeBuilder builder = method.createBuilder();

            builder.declaration(param.getType(), valueName(param));

            boolean unexpected = false;
            boolean elseIf = false;
            int index = 0;
            for (TypeData typeData : sourceTypes) {
                if (index < sourceTypes.size() - 1) {
                    elseIf = builder.startIf(elseIf);
                    builder.string(typeName(param)).string(" == ").typeLiteral(typeData.getPrimitiveType());
                    builder.end();
                    builder.startBlock();
                } else {
                    builder.startElseBlock();
                }

                ExecutableTypeData implictExecutableTypeData = child.getNodeData().findExecutableType(typeData, child.getExecuteWith().size());
                if (!unexpected && implictExecutableTypeData.hasUnexpectedValue(getContext())) {
                    unexpected = true;
                }
                ImplicitCastData cast = child.getNodeData().getTypeSystem().lookupCast(typeData, param.getTypeSystemType());
                CodeTree execute = createExecuteExpression(builder, child, expectType, implictExecutableTypeData, param, null, cast);
                builder.statement(execute);
                builder.end();
                index++;
            }

            builder.startReturn().string(valueName(param)).end();

            if (unexpected) {
                method.getThrownTypes().add(getContext().getTruffleTypes().getUnexpectedValueException());
            }

            return method;
        }

        private CodeTree createExecuteExpression(CodeTreeBuilder parent, NodeChildData child, TypeData expectType, ExecutableTypeData targetExecutable, ActualParameter targetParameter,
                        ActualParameter unexpectedParameter, ImplicitCastData cast) {
            CodeTreeBuilder builder = parent.create();
            builder.string(valueName(targetParameter));
            builder.string(" = ");
            if (cast != null) {
                startCallTypeSystemMethod(getContext(), builder, child.getNodeData(), cast.getMethodName());
            }

            if (targetExecutable.getType().needsCastTo(context, targetParameter.getTypeSystemType()) && cast == null) {
                startCallTypeSystemMethod(getContext(), builder, child.getNodeData(), TypeSystemCodeGenerator.expectTypeMethodName(targetParameter.getTypeSystemType()));
            }

            NodeData node = getModel().getNode();
            if (expectType == null) {
                builder.tree(createExecuteChildExpression(builder, child, targetParameter, targetExecutable, unexpectedParameter));
            } else {
                CodeTree var = CodeTreeBuilder.singleString(valueNameEvaluated(targetParameter));
                builder.tree(createExpectExecutableType(node, expectType, targetExecutable, var));
            }

            if (targetExecutable.getType().needsCastTo(context, targetParameter.getTypeSystemType())) {
                builder.end().end();
            }

            if (cast != null) {
                builder.end().end();
            }

            return builder.getRoot();
        }

        private boolean containsNewLine(CodeTree tree) {
            if (tree.getCodeKind() == CodeTreeKind.NEW_LINE) {
                return true;
            }

            for (CodeTree codeTree : tree.getEnclosedElements()) {
                if (containsNewLine(codeTree)) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasUnexpected(ExecutableTypeData target, ActualParameter sourceParameter, ActualParameter targetParameter) {
            List<TypeData> types = getModel().getNode().getTypeSystem().lookupSourceTypes(targetParameter.getTypeSystemType());
            NodeChildData child = getModel().getNode().findChild(targetParameter.getSpecification().getName());
            boolean hasUnexpected = false;
            for (TypeData type : types) {
                if (hasUnexpected) {
                    continue;
                }
                ExecutableTypeData execTarget = target;
                if (type != execTarget.getType()) {
                    execTarget = child.findExecutableType(getContext(), type);
                }
                hasUnexpected = hasUnexpected || hasUnexpectedType(execTarget, sourceParameter, type);
            }
            return hasUnexpected;
        }

        private boolean hasUnexpectedType(ExecutableTypeData target, ActualParameter sourceParameter, TypeData type) {
            boolean targetCast = target.getType().needsCastTo(context, type);
            if (targetCast && getModel().getNode().getTypeSystem().lookupCast(target.getType(), type) == null) {
                return true;
            }
            if (sourceParameter == null) {
                return target.hasUnexpectedValue(getContext());
            } else {
                if (sourceParameter.getTypeSystemType().needsCastTo(getContext(), type)) {
                    return target.hasUnexpectedValue(getContext());
                }
                return false;
            }
        }

        private CodeTree createCatchUnexpectedTree(CodeTreeBuilder parent, CodeTree body, String targetVariableName, SpecializationData specialization, ExecutableTypeData currentExecutable,
                        ExecutableTypeData targetExecutable, ActualParameter param, boolean shortCircuit) {
            CodeTreeBuilder builder = new CodeTreeBuilder(parent);
            ActualParameter sourceParameter = currentExecutable.findParameter(param.getLocalName());
            boolean unexpected = hasUnexpected(targetExecutable, sourceParameter, param);
            if (!unexpected) {
                return body;
            }

            if (!shortCircuit) {
                builder.declaration(param.getType(), targetVariableName);
            }
            builder.startTryBlock();

            if (containsNewLine(body)) {
                builder.tree(body);
            } else {
                builder.statement(body);
            }

            builder.end().startCatchBlock(getUnexpectedValueException(), "ex");
            SpecializationData generic = specialization.getNode().getGenericSpecialization();
            ActualParameter genericParameter = generic.findParameter(param.getLocalName());

            List<ActualParameter> genericParameters = generic.getParametersAfter(genericParameter);
            builder.tree(createDeoptimize(builder));
            builder.tree(createExecuteChildren(parent, currentExecutable, generic, genericParameters, genericParameter));
            if (specialization.isPolymorphic()) {
                builder.tree(createReturnOptimizeTypes(builder, currentExecutable, specialization, param));
            } else {
                builder.tree(createReturnExecuteAndSpecialize(builder, currentExecutable, specialization, param,
                                "Expected " + param.getLocalName() + " instanceof " + Utils.getSimpleName(param.getType())));
            }
            builder.end(); // catch block

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
            addInternalValueParameterNames(execute, specialization, generic, param.getLocalName(), true, true, null);
            execute.end();

            TypeData sourceType = generic.getReturnType().getTypeSystemType();

            builder.tree(createExpectExecutableType(node, sourceType, currentExecutable, execute.getRoot()));

            builder.end();
            return builder.getRoot();
        }

        private CodeTree createExecuteChildExpression(CodeTreeBuilder parent, NodeChildData targetChild, ActualParameter targetParameter, ExecutableTypeData targetExecutable,
                        ActualParameter unexpectedParameter) {
            CodeTreeBuilder builder = new CodeTreeBuilder(parent);
            if (targetChild != null) {
                builder.tree(createAccessChild(builder, targetChild, targetParameter));
                builder.string(".");
            }

            builder.startCall(targetExecutable.getMethodName());

            // TODO this should be merged with #createTemplateMethodCall
            int index = 0;
            for (ActualParameter parameter : targetExecutable.getParameters()) {

                if (!parameter.getSpecification().isSignature()) {
                    builder.string(parameter.getLocalName());
                } else {

                    if (index < targetChild.getExecuteWith().size()) {
                        NodeChildData child = targetChild.getExecuteWith().get(index);

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

        private CodeTree createAccessChild(CodeTreeBuilder parent, NodeChildData targetChild, ActualParameter targetParameter) throws AssertionError {
            CodeTreeBuilder builder = parent.create();
            Element accessElement = targetChild.getAccessElement();
            if (accessElement == null || accessElement.getKind() == ElementKind.METHOD) {
                builder.string("this.").string(targetChild.getName());
            } else if (accessElement.getKind() == ElementKind.FIELD) {
                builder.string("this.").string(accessElement.getSimpleName().toString());
            } else {
                throw new AssertionError();
            }
            if (targetParameter.getSpecification().isIndexed()) {
                builder.string("[" + targetParameter.getIndex() + "]");
            }
            return builder.getRoot();
        }

        private CodeTree createShortCircuitTree(CodeTreeBuilder parent, CodeTree body, String targetVariableName, SpecializationData specialization, ActualParameter parameter,
                        ActualParameter exceptionParam) {
            NodeChildData forField = specialization.getNode().findChild(parameter.getSpecification().getName());
            if (!isShortCircuit(forField)) {
                return body;
            }

            CodeTreeBuilder builder = new CodeTreeBuilder(parent);
            ActualParameter shortCircuitParam = specialization.getPreviousParam(parameter);
            builder.tree(createShortCircuitValue(builder, specialization, forField, shortCircuitParam, exceptionParam));
            builder.declaration(parameter.getType(), targetVariableName, CodeTreeBuilder.createBuilder().defaultValue(parameter.getType()));
            builder.startIf().string(shortCircuitParam.getLocalName()).end();
            builder.startBlock();

            if (containsNewLine(body)) {
                builder.tree(body);
            } else {
                builder.statement(body);
            }
            builder.end();

            return builder.getRoot();
        }

        private boolean isShortCircuit(NodeChildData forField) {
            return forField != null && forField.getExecutionKind() == ExecutionKind.SHORT_CIRCUIT;
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
            addInternalValueParameterNames(specializeCall, generic, node.getGenericSpecialization(), exceptionParam != null ? exceptionParam.getLocalName() : null, true, true, null);
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

    private class BaseCastNodeFactory extends ClassElementFactory<NodeData> {

        protected final Set<TypeData> usedTargetTypes;

        public BaseCastNodeFactory(ProcessorContext context, Set<TypeData> usedTargetTypes) {
            super(context);
            this.usedTargetTypes = usedTargetTypes;
        }

        @Override
        protected CodeTypeElement create(NodeData m) {
            CodeTypeElement type = createClass(m, modifiers(STATIC), nodeCastClassName(m, null), context.getTruffleTypes().getNode(), false);

            CodeVariableElement delegate = new CodeVariableElement(m.getNodeType(), "delegate");
            delegate.getModifiers().add(PROTECTED);
            delegate.getAnnotationMirrors().add(new CodeAnnotationMirror(getContext().getTruffleTypes().getChildAnnotation()));

            type.add(delegate);
            type.add(createConstructorUsingFields(modifiers(), type));
            return type;
        }

        @Override
        protected void createChildren(NodeData m) {
            CodeTypeElement type = getElement();
            type.add(createExecute(EXECUTE_SPECIALIZE_NAME, true));
            type.add(createExecute(EXECUTE_GENERIC_NAME, false));

            for (ExecutableTypeData targetExecutable : m.getExecutableTypes()) {
                if (!usedTargetTypes.contains(targetExecutable.getType()) && targetExecutable.hasUnexpectedValue(getContext())) {
                    continue;
                }
                CodeExecutableElement execute = createCastExecute(targetExecutable, targetExecutable, false);
                CodeExecutableElement expect = createCastExecute(targetExecutable, targetExecutable, true);
                if (execute != null) {
                    getElement().add(execute);
                }
                if (expect != null) {
                    getElement().add(expect);
                }
            }
            Set<TypeData> sourceTypes = new TreeSet<>();
            List<ImplicitCastData> casts = getModel().getTypeSystem().getImplicitCasts();
            for (ImplicitCastData cast : casts) {
                sourceTypes.add(cast.getSourceType());
            }

            CodeTypeElement baseType = getElement();
            for (TypeData sourceType : sourceTypes) {
                add(new SpecializedCastNodeFactory(context, baseType, sourceType, usedTargetTypes), getModel());
            }
        }

        private CodeExecutableElement createExecute(String name, boolean specialize) {
            NodeData node = getModel();
            TypeMirror objectType = node.getTypeSystem().getGenericType();
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), objectType, name, new CodeVariableElement(objectType, "value"));
            if (specialize) {
                method.getModifiers().add(FINAL);
            }
            CodeTreeBuilder builder = method.createBuilder();

            List<ImplicitCastData> casts = node.getTypeSystem().getImplicitCasts();
            boolean elseIf = false;
            for (ImplicitCastData cast : casts) {
                elseIf = builder.startIf(elseIf);
                startCallTypeSystemMethod(context, builder, getModel(), TypeSystemCodeGenerator.isTypeMethodName(cast.getSourceType()));
                builder.string("value");
                builder.end().end();
                builder.end();
                builder.startBlock();

                if (specialize) {
                    builder.startStatement().startCall("replace").startNew(nodeCastClassName(getModel(), cast.getSourceType())).string("delegate").end().doubleQuote("Added cast").end().end();
                }
                builder.startReturn();

                startCallTypeSystemMethod(context, builder, getModel(), cast.getMethodName());
                startCallTypeSystemMethod(context, builder, getModel(), TypeSystemCodeGenerator.asTypeMethodName(cast.getSourceType()));
                builder.string("value");
                builder.end().end();
                builder.end().end();

                builder.end();
                builder.end();
            }

            builder.startReturn().string("value").end();

            return method;
        }

        protected CodeExecutableElement createCastExecute(ExecutableTypeData sourceExecutable, ExecutableTypeData targetExecutable, boolean expect) {
            ImplicitCastData cast = null;
            if (!sourceExecutable.getType().equals(targetExecutable.getType())) {
                cast = getModel().getTypeSystem().lookupCast(sourceExecutable.getType(), targetExecutable.getType());
                if (cast == null) {
                    return null;
                }
            }

            if (expect) {
                if (targetExecutable.getEvaluatedCount() > 0) {
                    return null;
                } else if (Utils.isObject(targetExecutable.getType().getPrimitiveType())) {
                    return null;
                }
            }

            boolean hasTargetUnexpected = targetExecutable.hasUnexpectedValue(getContext());
            boolean hasSourceUnexpected = sourceExecutable.hasUnexpectedValue(getContext());

            CodeExecutableElement method = copyTemplateMethod(targetExecutable);
            method.getModifiers().add(PUBLIC);

            CodeTreeBuilder builder = method.createBuilder();

            if (hasSourceUnexpected && cast != null) {
                builder.startTryBlock();
            }

            if (expect) {
                method.getParameters().clear();
                String expectMethodName;
                if (hasTargetUnexpected) {
                    expectMethodName = TypeSystemCodeGenerator.expectTypeMethodName(targetExecutable.getType());
                } else {
                    expectMethodName = TypeSystemCodeGenerator.asTypeMethodName(targetExecutable.getType());
                }
                method.setSimpleName(CodeNames.of(expectMethodName));
                method.addParameter(new CodeVariableElement(getModel().getTypeSystem().getGenericType(), "value"));
            }

            builder.startReturn();
            CodeTree executeCall;
            if (expect) {
                executeCall = createCastType(getModel(), getModel().getTypeSystem().getGenericTypeData(), sourceExecutable.getType(), hasSourceUnexpected, CodeTreeBuilder.singleString("value"));
            } else {
                executeCall = createTemplateMethodCall(builder, CodeTreeBuilder.singleString("delegate."), targetExecutable, sourceExecutable, null);
            }
            if (cast != null) {
                startCallTypeSystemMethod(context, builder, getModel(), cast.getMethodName());
                builder.tree(executeCall);
                builder.end().end();
            } else {
                builder.tree(executeCall);
            }
            builder.end();

            if (hasSourceUnexpected && cast != null) {
                builder.end();
                builder.startCatchBlock(getContext().getTruffleTypes().getUnexpectedValueException(), "ex");
                builder.startStatement().startCall("replace").startNew(nodeCastClassName(getModel(), null)).string("delegate").end().doubleQuote("Removed cast").end().end();

                if (hasTargetUnexpected) {
                    builder.startThrow().string("ex").end();
                } else {
                    builder.startThrow().startNew(getContext().getType(AssertionError.class)).end().end();
                }
                builder.end();
            }

            return method;
        }

        private CodeExecutableElement copyTemplateMethod(TemplateMethod targetExecutable) {
            CodeExecutableElement method = CodeExecutableElement.clone(getContext().getEnvironment(), targetExecutable.getMethod());
            method.getModifiers().remove(ABSTRACT);
            method.getAnnotationMirrors().clear();
            Modifier visibility = Utils.getVisibility(method.getModifiers());
            if (visibility != null) {
                method.getModifiers().remove(visibility);
            }
            int index = 0;
            for (ActualParameter parameter : targetExecutable.getParameters()) {
                ((CodeVariableElement) method.getParameters().get(index)).setName(parameter.getLocalName());
                index++;
            }
            return method;
        }

    }

    private class SpecializedCastNodeFactory extends BaseCastNodeFactory {

        private final CodeTypeElement baseType;
        private final TypeData sourceType;

        public SpecializedCastNodeFactory(ProcessorContext context, CodeTypeElement baseType, TypeData type, Set<TypeData> usedTargetTypes) {
            super(context, usedTargetTypes);
            this.baseType = baseType;
            this.sourceType = type;
        }

        @Override
        protected CodeTypeElement create(NodeData m) {
            CodeTypeElement type = createClass(m, modifiers(PRIVATE, STATIC, FINAL), nodeCastClassName(m, sourceType), baseType.asType(), false);
            type.add(createConstructorUsingFields(modifiers(), type));
            return type;
        }

        @Override
        protected void createChildren(NodeData node) {
            for (TypeData targetType : usedTargetTypes) {
                for (ExecutableTypeData targetExecutable : node.getExecutableTypes()) {
                    if (targetExecutable.getType().equals(targetType)) {
                        ExecutableTypeData sourceExecutable = node.findExecutableType(sourceType, targetExecutable.getEvaluatedCount());
                        if (sourceExecutable == null) {
                            // TODO what if there is no evaluated version?
                            continue;
                        }
                        CodeExecutableElement execute = createCastExecute(sourceExecutable, targetExecutable, false);
                        CodeExecutableElement expect = createCastExecute(sourceExecutable, targetExecutable, true);
                        if (execute != null) {
                            getElement().add(execute);
                        }
                        if (expect != null) {
                            getElement().add(expect);
                        }
                    }
                }

            }
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
                CodeTree body = superConstructor.getBodyTree();
                CodeTreeBuilder builder = superConstructor.createBuilder();
                builder.tree(body);

                if (superConstructor != null) {
                    if (getModel().isGeneric() && node.isPolymorphic()) {
                        builder.statement("this.next0 = null");
                    }

                    for (ActualParameter param : getModel().getParameters()) {
                        if (!param.getSpecification().isSignature()) {
                            continue;
                        }
                        NodeChildData child = getModel().getNode().findChild(param.getSpecification().getName());
                        List<TypeData> types = child.getNodeData().getTypeSystem().lookupSourceTypes(param.getTypeSystemType());
                        if (types.size() > 1) {
                            clazz.add(new CodeVariableElement(modifiers(PRIVATE, FINAL), getContext().getType(Class.class), typeName(param)));
                            superConstructor.getParameters().add(new CodeVariableElement(getContext().getType(Class.class), typeName(param)));

                            builder.startStatement();
                            builder.string("this.").string(typeName(param)).string(" = ").string(typeName(param));
                            builder.end();
                        }
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
            for (final SpecializationData polymorphic : node.getPolymorphicSpecializations()) {
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
                    addInternalValueParameterNames(elseBuilder, polymorphic, polymorphic, null, true, true, null);
                    elseBuilder.end().end();

                    boolean forceElse = specialization.getExceptions().size() > 0;
                    builder.tree(createExecuteTree(builder, polymorphic, SpecializationGroup.create(specialization), false, new CodeBlock<SpecializationData>() {

                        public CodeTree create(CodeTreeBuilder b, SpecializationData current) {
                            return createGenericInvoke(b, polymorphic, current);
                        }
                    }, elseBuilder.getRoot(), forceElse, true));
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
            String message = "Polymorphic limit reached (" + node.getPolymorphicDepth() + ")";
            String castRoot = "(" + baseClassName(node) + ") root";
            builder.tree(createGenericInvoke(builder, node.getGenericPolymorphicSpecialization(), node.getGenericSpecialization(),
                            createReplaceCall(builder, node.getGenericSpecialization(), "root", castRoot, message), null));
            builder.end();

            builder.startElseBlock();
            builder.startStatement().startCall("setNext0");
            builder.startNew(nodeSpecializationClassName(node.getUninitializedSpecialization())).string("this").end();
            builder.end().end();

            CodeTreeBuilder specializeCall = new CodeTreeBuilder(builder);
            specializeCall.startCall(EXECUTE_SPECIALIZE_NAME);
            specializeCall.string("0");
            addInternalValueParameterNames(specializeCall, specialization, node.getGenericSpecialization(), null, true, true, null);
            specializeCall.startGroup().doubleQuote("Uninitialized polymorphic (").string(" + depth + ").doubleQuote("/" + node.getPolymorphicDepth() + ")").end();
            specializeCall.end().end();

            builder.declaration(node.getGenericSpecialization().getReturnType().getType(), "result", specializeCall.getRoot());

            builder.startIf().string("this.next0 != null").end().startBlock();
            builder.startStatement().string("(").cast(nodePolymorphicClassName(node, node.getGenericPolymorphicSpecialization())).string("root).optimizeTypes()").end();
            builder.end();

            if (Utils.isVoid(builder.findMethod().getReturnType())) {
                builder.returnStatement();
            } else {
                builder.startReturn().string("result").end();
            }

            builder.end();

            return builder.getRoot();
        }

        private CodeTree createExecuteBody(CodeTreeBuilder parent, SpecializationData specialization, ExecutableTypeData execType) {
            CodeTreeBuilder builder = new CodeTreeBuilder(parent);

            List<ExecutableTypeData> primaryExecutes = findFunctionalExecutableType(specialization, execType.getEvaluatedCount());

            if (primaryExecutes.contains(execType) || primaryExecutes.isEmpty()) {
                builder.tree(createFunctionalExecute(builder, specialization, execType));
            } else if (needsCastingExecuteMethod(execType)) {
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

        private boolean needsCastingExecuteMethod(ExecutableTypeData execType) {
            if (execType.isAbstract()) {
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

        private CodeTree createFunctionalExecute(CodeTreeBuilder parent, final SpecializationData specialization, final ExecutableTypeData executable) {
            CodeTreeBuilder builder = new CodeTreeBuilder(parent);
            if (specialization.isUninitialized()) {
                builder.tree(createDeoptimize(builder));
            }

            builder.tree(createExecuteChildren(builder, executable, specialization, specialization.getParameters(), null));

            CodeTree returnSpecialized = null;

            if (specialization.findNextSpecialization() != null) {
                CodeTreeBuilder returnBuilder = new CodeTreeBuilder(builder);
                returnBuilder.tree(createDeoptimize(builder));
                returnBuilder.tree(createReturnExecuteAndSpecialize(builder, executable, specialization, null, "One of guards " + specialization.getGuardDefinitions() + " failed"));
                returnSpecialized = returnBuilder.getRoot();
            }

            builder.tree(createExecuteTree(builder, specialization, SpecializationGroup.create(specialization), false, new CodeBlock<SpecializationData>() {

                public CodeTree create(CodeTreeBuilder b, SpecializationData current) {
                    return createExecute(b, executable, specialization);
                }
            }, returnSpecialized, false, false));

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
                addInternalValueParameterNames(returnBuilder, specialization, specialization, null, true, true, null);
                returnBuilder.end();
            } else if (specialization.isUninitialized()) {
                returnBuilder.startCall("super", EXECUTE_SPECIALIZE_NAME);
                returnBuilder.string("0");
                addInternalValueParameterNames(returnBuilder, specialization, specialization, null, true, true, null);
                returnBuilder.doubleQuote("Uninitialized monomorphic");
                returnBuilder.end();
            } else if (specialization.getMethod() == null && !node.needsRewrites(context)) {
                emitEncounteredSynthetic(builder, specialization);
            } else if (specialization.isGeneric()) {
                returnBuilder.startCall("super", EXECUTE_GENERIC_NAME);
                addInternalValueParameterNames(returnBuilder, specialization, specialization, null, node.needsFrame(), true, null);
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
