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
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.ast.*;
import com.oracle.truffle.dsl.processor.node.NodeChildData.Cardinality;
import com.oracle.truffle.dsl.processor.node.SpecializationGroup.TypeGuard;
import com.oracle.truffle.dsl.processor.template.*;
import com.oracle.truffle.dsl.processor.typesystem.*;

public class NodeCodeGenerator extends CompilationUnitFactory<NodeData> {

    private static final String THIS_NODE_LOCAL_VAR_NAME = "thisNode";

    private static final String EXECUTE_GENERIC_NAME = "executeGeneric0";
    private static final String EXECUTE_SPECIALIZE_NAME = "executeAndSpecialize0";
    private static final String EXECUTE_POLYMORPHIC_NAME = "executePolymorphic0";

    private static final String UPDATE_TYPES_NAME = "updateTypes";

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
        String nodeid = resolveNodeId(specialization.getNode());
        String name = Utils.firstLetterUpperCase(nodeid);
        name += Utils.firstLetterUpperCase(specialization.getId());
        name += "Node";
        return name;
    }

    private static String nodePolymorphicClassName(NodeData node) {
        return Utils.firstLetterUpperCase(resolveNodeId(node)) + "PolymorphicNode";
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

    private static String implicitTypeName(ActualParameter param) {
        return param.getLocalName() + "ImplicitType";
    }

    private static String polymorphicTypeName(ActualParameter param) {
        return param.getLocalName() + "PolymorphicType";
    }

    private static String valueName(ActualParameter param) {
        return param.getLocalName();
    }

    private static CodeTree createAccessChild(NodeExecutionData targetExecution) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        Element accessElement = targetExecution.getChild().getAccessElement();
        if (accessElement == null || accessElement.getKind() == ElementKind.METHOD) {
            builder.string("this.").string(targetExecution.getChild().getName());
        } else if (accessElement.getKind() == ElementKind.FIELD) {
            builder.string("this.").string(accessElement.getSimpleName().toString());
        } else {
            throw new AssertionError();
        }
        if (targetExecution.isIndexed()) {
            builder.string("[" + targetExecution.getIndex() + "]");
        }
        return builder.getRoot();
    }

    private static String castValueName(ActualParameter parameter) {
        return valueName(parameter) + "Cast";
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

    private void addInternalValueParameterNames(CodeTreeBuilder builder, TemplateMethod source, TemplateMethod specialization, String unexpectedValueName, boolean forceFrame,
                    Map<String, String> customNames) {
        if (forceFrame && specialization.getSpecification().findParameterSpec("frame") != null) {
            builder.string("frameValue");
        }
        for (ActualParameter parameter : specialization.getParameters()) {
            ParameterSpec spec = parameter.getSpecification();
            if (forceFrame && spec.getName().equals("frame")) {
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
        if (!sourceParameter.getSpecification().isSignature()) {
            return valueName(targetParameter);
        } else if (sourceParameter.getTypeSystemType() != null && targetParameter.getTypeSystemType() != null) {
            if (sourceParameter.getTypeSystemType().needsCastTo(getContext(), targetParameter.getTypeSystemType())) {
                return castValueName(targetParameter);
            }
        }
        return valueName(targetParameter);
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
                    ActualParameter firstParameter = null;
                    for (ActualParameter searchParameter : targetMethod.getParameters()) {
                        if (searchParameter.getSpecification().isSignature()) {
                            firstParameter = searchParameter;
                            break;
                        }
                    }
                    if (firstParameter == null) {
                        throw new AssertionError();
                    }

                    ActualParameter sourceParameter = sourceMethod.findParameter(firstParameter.getLocalName());

                    if (castedValues && sourceParameter != null) {
                        builder.string(valueName(sourceParameter, firstParameter));
                    } else {
                        builder.string(valueName(firstParameter));
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
                builder.cast(targetParameter.getType(), CodeTreeBuilder.singleString("ex.getResult()"));
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

    private static CodeTree createCallTypeSystemMethod(ProcessorContext context, CodeTreeBuilder parent, NodeData node, String methodName, CodeTree... args) {
        CodeTreeBuilder builder = new CodeTreeBuilder(parent);
        startCallTypeSystemMethod(context, builder, node.getTypeSystem(), methodName);
        for (CodeTree arg : args) {
            builder.tree(arg);
        }
        builder.end().end();
        return builder.getRoot();
    }

    private static void startCallTypeSystemMethod(ProcessorContext context, CodeTreeBuilder body, TypeSystemData typeSystem, String methodName) {
        VariableElement singleton = TypeSystemCodeGenerator.findSingleton(context, typeSystem);
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
        CodeTreeBuilder nodes = builder.create();
        CodeTreeBuilder arguments = builder.create();
        nodes.startCommaGroup();
        arguments.startCommaGroup();
        boolean empty = true;
        for (ActualParameter parameter : current.getParameters()) {
            NodeExecutionData executionData = parameter.getSpecification().getExecution();
            if (executionData != null) {
                if (executionData.isShortCircuit()) {
                    nodes.nullLiteral();
                    arguments.string(valueName(parameter.getPreviousParameter()));
                }
                nodes.tree(createAccessChild(executionData));
                arguments.string(valueName(parameter));
                empty = false;
            }
        }
        nodes.end();
        arguments.end();

        builder.startThrow().startNew(getContext().getType(UnsupportedSpecializationException.class));
        builder.string("this");
        builder.startNewArray(getContext().getTruffleTypes().getNodeArray(), null);

        builder.tree(nodes.getRoot());
        builder.end();
        if (!empty) {
            builder.tree(arguments.getRoot());
        }
        builder.end().end();
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
        for (NodeData nodeChild : node.getEnclosingNodes()) {
            NodeCodeGenerator generator = new NodeCodeGenerator(getContext());
            childTypes.put(nodeChild, generator.process(null, nodeChild).getEnclosedElements());
        }

        if (node.needsFactory() || node.getNodeDeclaringChildren().size() > 0) {
            NodeFactoryFactory factory = new NodeFactoryFactory(context, childTypes);
            add(factory, node);
            factory.getElement().getEnclosedElements().addAll(casts);
        }
    }

    protected CodeTree createCastType(TypeSystemData typeSystem, TypeData sourceType, TypeData targetType, boolean expect, CodeTree value) {
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
        startCallTypeSystemMethod(getContext(), builder, typeSystem, targetMethodName);
        builder.tree(value);
        builder.end().end();
        return builder.getRoot();
    }

    protected CodeTree createExpectType(TypeSystemData typeSystem, TypeData sourceType, TypeData targetType, CodeTree expression) {
        return createCastType(typeSystem, sourceType, targetType, true, expression);
    }

    public CodeTree createDeoptimize(CodeTreeBuilder parent) {
        CodeTreeBuilder builder = new CodeTreeBuilder(parent);
        builder.startStatement();
        builder.startStaticCall(getContext().getTruffleTypes().getCompilerDirectives(), "transferToInterpreterAndInvalidate").end();
        builder.end();
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

            CodeTypeElement polymorphicNode = null;
            if (node.needsFactory()) {
                NodeBaseFactory factory = new NodeBaseFactory(context);
                add(factory, node.getGenericSpecialization() == null ? node.getSpecializations().get(0) : node.getGenericSpecialization());
                generatedNode = factory.getElement();

                if (node.needsRewrites(context)) {
                    clazz.add(createCreateGenericMethod(node, createVisibility));
                }

                createFactoryMethods(node, clazz, createVisibility);

                for (SpecializationData specialization : node.getSpecializations()) {
                    if (!specialization.isReachable()) {
                        continue;
                    }

                    if (specialization.isPolymorphic() && node.isPolymorphic()) {
                        PolymorphicNodeFactory polymorphicFactory = new PolymorphicNodeFactory(getContext(), generatedNode);
                        add(polymorphicFactory, specialization);
                        polymorphicNode = polymorphicFactory.getElement();
                        continue;
                    }

                    add(new SpecializedNodeFactory(context, generatedNode), specialization);
                }

                TypeMirror nodeFactory = Utils.getDeclaredType(Utils.fromTypeMirror(getContext().getType(NodeFactory.class)), node.getNodeType());
                clazz.getImplements().add(nodeFactory);
                clazz.add(createCreateNodeMethod(node));
                clazz.add(createGetNodeClassMethod(node));
                clazz.add(createGetNodeSignaturesMethod());
                clazz.add(createGetChildrenSignatureMethod(node));
                clazz.add(createGetInstanceMethod(node, createVisibility));
                clazz.add(createInstanceConstant(node, clazz.asType()));
            }

            if (polymorphicNode != null) {
                patchParameterType(clazz, UPDATE_TYPES_NAME, generatedNode.asType(), polymorphicNode.asType());
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
            if (node.getDeclaringNode() == null && children.size() > 0) {
                clazz.add(createGetFactories(node));
            }

        }

        private void patchParameterType(CodeTypeElement enclosingClass, String methodName, TypeMirror originalType, TypeMirror newType) {
            for (TypeElement enclosedType : ElementFilter.typesIn(enclosingClass.getEnclosedElements())) {
                CodeTypeElement type = (CodeTypeElement) enclosedType;
                ExecutableElement method = type.getMethod(methodName);
                for (VariableElement v : method.getParameters()) {
                    CodeVariableElement var = (CodeVariableElement) v;
                    if (Utils.typeEquals(var.getType(), originalType)) {
                        var.setType(newType);
                    }
                }
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

            for (ActualParameter parameter : data.getSignatureParameters()) {
                signatureTypes.add(parameter.getSpecification().getExecution().getNodeType());
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
            if (node.getDeclaringNode() != null) {
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
                while (childNode.getDeclaringNode() != null) {
                    factories.add(childNode);
                    childNode = childNode.getDeclaringNode();
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
                    builder.statement("this.next0 = insert(next0)");
                    clazz.add(setter);

                    CodeExecutableElement genericCachedExecute = createCachedExecute(node, node.getPolymorphicSpecialization());
                    clazz.add(genericCachedExecute);

                    getElement().add(createUpdateTypes(clazz.asType()));
                }

                for (CodeExecutableElement method : createImplicitChildrenAccessors()) {
                    clazz.add(method);
                }
                clazz.add(createGenericExecuteAndSpecialize(node, rootGroup));
                clazz.add(createInfoMessage(node));
            }

            if (needsInvokeCopyConstructorMethod()) {
                clazz.add(createInvokeCopyConstructor(clazz.asType(), null));
                clazz.add(createCopyPolymorphicConstructor(clazz.asType()));
            }

            if (node.getGenericSpecialization() != null && node.getGenericSpecialization().isReachable()) {
                clazz.add(createGenericExecute(node, rootGroup));
            }

            clazz.add(createGetCost(node, null, NodeCost.MONOMORPHIC));
        }

        protected boolean needsInvokeCopyConstructorMethod() {
            return getModel().getNode().isPolymorphic();
        }

        protected CodeExecutableElement createGetCost(NodeData node, SpecializationData specialization, NodeCost cost) {
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), context.getTruffleTypes().getNodeCost(), "getCost");

            TypeMirror nodeInfoKind = context.getTruffleTypes().getNodeCost();

            CodeTreeBuilder builder = method.createBuilder();
            if (node.isPolymorphic() && specialization == null) {
                // assume next0 exists
                builder.startIf().string("next0 != null && next0.getCost() != ").staticReference(nodeInfoKind, "UNINITIALIZED").end();
                builder.startBlock();
                builder.startReturn().staticReference(nodeInfoKind, "POLYMORPHIC").end();
                builder.end();
            }

            builder.startReturn().staticReference(nodeInfoKind, cost.name()).end();
            return method;
        }

        protected CodeExecutableElement createInvokeCopyConstructor(TypeMirror baseType, SpecializationData specialization) {
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PROTECTED), baseType, "invokeCopyConstructor");
            if (specialization == null) {
                method.getModifiers().add(ABSTRACT);
            } else {
                CodeTreeBuilder builder = method.createBuilder();
                builder.startReturn();
                builder.startNew(getElement().asType());
                builder.string("this");
                for (ActualParameter param : getImplicitTypeParamters(specialization)) {
                    builder.string(implicitTypeName(param));
                }
                builder.end().end();
            }
            return method;
        }

        protected CodeExecutableElement createCopyPolymorphicConstructor(TypeMirror baseType) {
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PROTECTED, FINAL), baseType, "copyPolymorphic");
            CodeTreeBuilder builder = method.createBuilder();
            CodeTreeBuilder nullBuilder = builder.create();
            CodeTreeBuilder oldBuilder = builder.create();
            CodeTreeBuilder resetBuilder = builder.create();

            for (ActualParameter param : getModel().getSignatureParameters()) {
                NodeExecutionData execution = param.getSpecification().getExecution();

                CodeTree access = createAccessChild(execution);

                String oldName = "old" + Utils.firstLetterUpperCase(param.getLocalName());
                oldBuilder.declaration(execution.getChild().getNodeData().getNodeType(), oldName, access);
                nullBuilder.startStatement().tree(access).string(" = null").end();
                resetBuilder.startStatement().tree(access).string(" = ").string(oldName).end();
            }

            builder.tree(oldBuilder.getRoot());
            builder.tree(nullBuilder.getRoot());

            builder.startStatement().type(baseType).string(" copy = ");
            builder.startCall("invokeCopyConstructor").end();
            builder.end();

            builder.tree(resetBuilder.getRoot());
            builder.startReturn().string("copy").end();
            return method;
        }

        private List<CodeExecutableElement> createImplicitChildrenAccessors() {
            NodeData node = getModel().getNode();
            // Map<NodeChildData, Set<TypeData>> expectTypes = new HashMap<>();
            @SuppressWarnings("unchecked")
            List<Set<TypeData>> expectTypes = Arrays.<Set<TypeData>> asList(new Set[node.getGenericSpecialization().getParameters().size()]);

            for (ExecutableTypeData executableType : node.getExecutableTypes()) {
                for (int i = 0; i < executableType.getEvaluatedCount(); i++) {
                    ActualParameter parameter = executableType.getSignatureParameter(i);
                    if (i >= expectTypes.size()) {
                        break;
                    }
                    Set<TypeData> types = expectTypes.get(i);
                    if (types == null) {
                        types = new TreeSet<>();
                        expectTypes.set(i, types);
                    }
                    types.add(parameter.getTypeSystemType());
                }
            }

            List<CodeExecutableElement> methods = new ArrayList<>();
            @SuppressWarnings("unchecked")
            List<Set<TypeData>> visitedList = Arrays.<Set<TypeData>> asList(new Set[node.getGenericSpecialization().getParameters().size()]);
            for (SpecializationData spec : node.getSpecializations()) {
                int signatureIndex = -1;
                for (ActualParameter param : spec.getParameters()) {
                    if (!param.getSpecification().isSignature()) {
                        continue;
                    }
                    signatureIndex++;
                    Set<TypeData> visitedTypeData = visitedList.get(signatureIndex);
                    if (visitedTypeData == null) {
                        visitedTypeData = new TreeSet<>();
                        visitedList.set(signatureIndex, visitedTypeData);
                    }

                    if (visitedTypeData.contains(param.getTypeSystemType())) {
                        continue;
                    }
                    visitedTypeData.add(param.getTypeSystemType());

                    Set<TypeData> expect = expectTypes.get(signatureIndex);
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
            for (ActualParameter parameter : node.getGenericSpecialization().getSignatureParameters()) {
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

        private CodeExecutableElement createCachedExecute(NodeData node, SpecializationData polymorph) {
            CodeExecutableElement cachedExecute = new CodeExecutableElement(modifiers(PROTECTED, ABSTRACT), polymorph.getReturnType().getType(), EXECUTE_POLYMORPHIC_NAME);
            addInternalValueParameters(cachedExecute, polymorph, true, false);

            ExecutableTypeData sourceExecutableType = node.findExecutableType(polymorph.getReturnType().getTypeSystemType(), 0);
            boolean sourceThrowsUnexpected = sourceExecutableType != null && sourceExecutableType.hasUnexpectedValue(getContext());
            if (sourceThrowsUnexpected && sourceExecutableType.getType().equals(node.getGenericSpecialization().getReturnType().getTypeSystemType())) {
                sourceThrowsUnexpected = false;
            }
            if (sourceThrowsUnexpected) {
                cachedExecute.getThrownTypes().add(getContext().getType(UnexpectedResultException.class));
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

            if (superConstructor != null) {
                builder.startStatement().startSuperCall();
                for (VariableElement param : superConstructor.getParameters()) {
                    builder.string(param.getSimpleName().toString());
                }
                builder.end().end();
            }

            for (VariableElement var : type.getFields()) {
                NodeChildData child = node.findChild(var.getSimpleName().toString());

                if (child != null) {
                    method.getParameters().add(new CodeVariableElement(child.getOriginalType(), child.getName()));
                } else {
                    method.getParameters().add(new CodeVariableElement(var.asType(), var.getSimpleName().toString()));
                }

                builder.startStatement();
                String fieldName = var.getSimpleName().toString();

                CodeTree init = createStaticCast(builder, child, fieldName);

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
                CodeTree init = CodeTreeBuilder.singleString(copyAccess);
                builder.startStatement().string("this.").string(varName).string(" = ").tree(init).end();
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

            String currentNode = "this";
            for (SpecializationData specialization : node.getSpecializations()) {
                if (!specialization.getExceptions().isEmpty()) {
                    currentNode = "current";
                    builder.declaration(baseClassName(node), currentNode, "this");
                    break;
                }
            }

            builder.startStatement().string("String message = ").startCall("createInfo0").string("reason");
            addInternalValueParameterNames(builder, node.getGenericSpecialization(), node.getGenericSpecialization(), null, false, null);
            builder.end().end();

            final String currentNodeVar = currentNode;
            builder.tree(createExecuteTree(builder, node.getGenericSpecialization(), rootGroup, true, new CodeBlock<SpecializationData>() {

                public CodeTree create(CodeTreeBuilder b, SpecializationData current) {
                    return createGenericInvokeAndSpecialize(b, node.getGenericSpecialization(), current, currentNodeVar);
                }
            }, null, false, true, false));

            boolean firstUnreachable = true;
            for (SpecializationData current : node.getSpecializations()) {
                if (current.isReachable()) {
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
                if (current.isUninitialized() || current.isPolymorphic() || !current.isReachable()) {
                    continue;
                }
                filteredSpecializations.add(current);
            }

            return SpecializationGroup.create(filteredSpecializations);
        }

        private CodeExecutableElement createGenericExecute(NodeData node, SpecializationGroup group) {
            TypeMirror genericReturnType = node.getGenericSpecialization().getReturnType().getType();
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PROTECTED), genericReturnType, EXECUTE_GENERIC_NAME);

            if (!node.needsFrame(getContext())) {
                method.getAnnotationMirrors().add(new CodeAnnotationMirror(getContext().getTruffleTypes().getSlowPath()));
            }
            addInternalValueParameters(method, node.getGenericSpecialization(), node.needsFrame(getContext()), false);
            final CodeTreeBuilder builder = method.createBuilder();

            builder.tree(createExecuteTree(builder, node.getGenericSpecialization(), group, false, new CodeBlock<SpecializationData>() {

                public CodeTree create(CodeTreeBuilder b, SpecializationData current) {
                    return createGenericInvoke(builder, current.getNode().getGenericSpecialization(), current);
                }
            }, null, false, true, false));

            emitUnreachableSpecializations(builder, node);

            return method;
        }

        private void emitUnreachableSpecializations(final CodeTreeBuilder builder, NodeData node) {
            for (SpecializationData current : node.getSpecializations()) {
                if (current.isReachable()) {
                    continue;
                }
                builder.string("// unreachable ").string(current.getId()).newLine();
            }
        }

        protected CodeTree createExecuteTree(CodeTreeBuilder outerParent, final SpecializationData source, final SpecializationGroup group, final boolean checkMinimumState,
                        final CodeBlock<SpecializationData> guardedblock, final CodeTree elseBlock, boolean forceElse, final boolean emitAssumptions, final boolean typedCasts) {
            return guard(outerParent, source, group, checkMinimumState, new CodeBlock<Integer>() {

                public CodeTree create(CodeTreeBuilder parent, Integer ifCount) {
                    CodeTreeBuilder builder = parent.create();

                    if (group.getSpecialization() != null) {
                        builder.tree(guardedblock.create(builder, group.getSpecialization()));

                        assert group.getChildren().isEmpty() : "missed a specialization";

                    } else {
                        for (SpecializationGroup childGroup : group.getChildren()) {
                            builder.tree(createExecuteTree(builder, source, childGroup, checkMinimumState, guardedblock, null, false, emitAssumptions, typedCasts));
                        }
                    }

                    return builder.getRoot();
                }
            }, elseBlock, forceElse, emitAssumptions, typedCasts);
        }

        private CodeTree guard(CodeTreeBuilder parent, SpecializationData source, SpecializationGroup group, boolean checkMinimumState, CodeBlock<Integer> bodyBlock, CodeTree elseBlock,
                        boolean forceElse, boolean emitAssumptions, boolean typedCasts) {
            CodeTreeBuilder builder = parent.create();

            int ifCount = emitGuards(builder, source, group, checkMinimumState, emitAssumptions, typedCasts);

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
            if (previous == null || previous.findElseConnectableGuards(checkMinimumState).isEmpty()) {
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

        private int emitGuards(CodeTreeBuilder builder, SpecializationData source, SpecializationGroup group, boolean checkMinimumState, boolean emitAssumptions, boolean typedCasts) {
            NodeData node = source.getNode();

            CodeTreeBuilder guardsBuilder = builder.create();
            CodeTreeBuilder castBuilder = builder.create();
            CodeTreeBuilder guardsCastBuilder = builder.create();

            String guardsAnd = "";
            String guardsCastAnd = "";

            boolean minimumState = checkMinimumState;
            if (minimumState) {
                int groupMaxIndex = group.getUncheckedSpecializationIndex();

                if (groupMaxIndex > -1) {
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

                NodeExecutionData execution = valueParam.getSpecification().getExecution();
                CodeTree implicitGuard = createTypeGuard(guardsBuilder, execution, valueParam, typeGuard.getType(), typedCasts);
                if (implicitGuard != null) {
                    guardsBuilder.string(guardsAnd);
                    guardsBuilder.tree(implicitGuard);
                    guardsAnd = " && ";
                }

                CodeTree cast = createCast(castBuilder, execution, valueParam, typeGuard.getType(), checkMinimumState, typedCasts);
                if (cast != null) {
                    castBuilder.tree(cast);
                }
            }
            List<GuardData> elseGuards = group.findElseConnectableGuards(checkMinimumState);

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

        private CodeTree createTypeGuard(CodeTreeBuilder parent, NodeExecutionData execution, ActualParameter source, TypeData targetType, boolean typedCasts) {
            NodeData node = execution.getChild().getNodeData();

            CodeTreeBuilder builder = new CodeTreeBuilder(parent);

            TypeData sourceType = source.getTypeSystemType();

            if (!sourceType.needsCastTo(getContext(), targetType)) {
                return null;
            }

            builder.startGroup();

            if (execution.isShortCircuit()) {
                ActualParameter shortCircuit = source.getPreviousParameter();
                assert shortCircuit != null;
                builder.string("(");
                builder.string("!").string(valueName(shortCircuit));
                builder.string(" || ");
            }

            String castMethodName;
            String castTypeName = null;
            List<TypeData> types = getModel().getNode().getTypeSystem().lookupSourceTypes(targetType);
            if (types.size() > 1) {
                castMethodName = TypeSystemCodeGenerator.isImplicitTypeMethodName(targetType);
                if (typedCasts) {
                    castTypeName = implicitTypeName(source);
                }
            } else {
                castMethodName = TypeSystemCodeGenerator.isTypeMethodName(targetType);
            }

            startCallTypeSystemMethod(getContext(), builder, node.getTypeSystem(), castMethodName);
            builder.string(valueName(source));
            if (castTypeName != null) {
                builder.string(castTypeName);
            }
            builder.end().end(); // call

            if (execution.isShortCircuit()) {
                builder.string(")");
            }

            builder.end(); // group

            return builder.getRoot();
        }

        // TODO merge redundancies with #createTypeGuard
        private CodeTree createCast(CodeTreeBuilder parent, NodeExecutionData execution, ActualParameter source, TypeData targetType, boolean checkMinimumState, boolean typedCasts) {
            NodeData node = execution.getChild().getNodeData();
            TypeData sourceType = source.getTypeSystemType();

            if (!sourceType.needsCastTo(getContext(), targetType)) {
                return null;
            }

            CodeTree condition = null;
            if (execution.isShortCircuit()) {
                ActualParameter shortCircuit = source.getPreviousParameter();
                assert shortCircuit != null;
                condition = CodeTreeBuilder.singleString(valueName(shortCircuit));
            }

            String castMethodName;
            String castTypeName = null;
            List<TypeData> types = getModel().getNode().getTypeSystem().lookupSourceTypes(targetType);
            if (types.size() > 1) {
                castMethodName = TypeSystemCodeGenerator.asImplicitTypeMethodName(targetType);
                if (typedCasts) {
                    castTypeName = implicitTypeName(source);
                }
            } else {
                castMethodName = TypeSystemCodeGenerator.asTypeMethodName(targetType);
            }

            List<CodeTree> args = new ArrayList<>();
            args.add(CodeTreeBuilder.singleString(valueName(source)));
            if (castTypeName != null) {
                args.add(CodeTreeBuilder.singleString(castTypeName));
            }

            CodeTree value = createCallTypeSystemMethod(context, parent, node, castMethodName, args.toArray(new CodeTree[0]));

            CodeTreeBuilder builder = parent.create();
            builder.tree(createLazyAssignment(parent, castValueName(source), targetType.getPrimitiveType(), condition, value));
            if (checkMinimumState && types.size() > 1) {
                CodeTree castType = createCallTypeSystemMethod(context, parent, node, TypeSystemCodeGenerator.getImplicitClass(targetType), CodeTreeBuilder.singleString(valueName(source)));
                builder.tree(createLazyAssignment(builder, implicitTypeName(source), getContext().getType(Class.class), condition, castType));
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
            builder.string("!").startParantheses().instanceOf("root", nodePolymorphicClassName(node)).end();
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
                addInternalValueParameterNames(builder, source, current, null, current.getNode().needsFrame(getContext()), null);
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
            for (ActualParameter param : current.getSignatureParameters()) {
                NodeChildData child = param.getSpecification().getExecution().getChild();
                List<TypeData> types = child.getNodeData().getTypeSystem().lookupSourceTypes(param.getTypeSystemType());
                if (types.size() > 1) {
                    replaceCall.string(implicitTypeName(param));
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
            String polyClassName = nodePolymorphicClassName(node);
            String uninitializedName = nodeSpecializationClassName(node.getUninitializedSpecialization());
            CodeTreeBuilder builder = parent.create();

            builder.declaration(getElement().asType(), "currentCopy", currentNode + ".copyPolymorphic()");
            builder.declaration(polyClassName, "polymorphic", builder.create().startNew(polyClassName).string(currentNode).end());
            builder.startStatement().startCall(currentNode, "replace").string("polymorphic").string("message").end().end();
            builder.startStatement().startCall("polymorphic", "setNext0").string("currentCopy").end().end();
            builder.startStatement().startCall("currentCopy", "setNext0").startNew(uninitializedName).string(currentNode).end().end().end();

            builder.startReturn();
            builder.startCall("currentCopy.next0", EXECUTE_POLYMORPHIC_NAME);
            addInternalValueParameterNames(builder, node.getGenericSpecialization(), node.getGenericSpecialization(), null, true, null);
            builder.end();
            builder.end();

            return builder.getRoot();
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
            for (ActualParameter sourceParameter : executable.getSignatureParameters()) {
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
            return createCastType(node.getTypeSystem(), sourceType, castedType.getType(), hasUnexpected, value);
        }

        protected CodeTree createExecuteChildren(CodeTreeBuilder parent, ExecutableTypeData sourceExecutable, SpecializationData specialization, List<ActualParameter> targetParameters,
                        ActualParameter unexpectedParameter) {
            CodeTreeBuilder builder = parent.create();
            for (ActualParameter targetParameter : targetParameters) {
                if (!targetParameter.getSpecification().isSignature()) {
                    continue;
                }
                NodeExecutionData execution = targetParameter.getSpecification().getExecution();
                CodeTree executionExpressions = createExecuteChild(builder, execution, sourceExecutable, targetParameter, unexpectedParameter);
                CodeTree unexpectedTree = createCatchUnexpectedTree(builder, executionExpressions, specialization, sourceExecutable, targetParameter, execution.isShortCircuit(), unexpectedParameter);
                CodeTree shortCircuitTree = createShortCircuitTree(builder, unexpectedTree, specialization, targetParameter, unexpectedParameter);

                if (shortCircuitTree == executionExpressions) {
                    if (containsNewLine(executionExpressions)) {
                        builder.declaration(targetParameter.getType(), valueName(targetParameter));
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

        private ExecutableTypeData resolveExecutableType(NodeExecutionData execution, TypeData type) {
            ExecutableTypeData targetExecutable = execution.getChild().findExecutableType(getContext(), type);
            if (targetExecutable == null) {
                targetExecutable = execution.getChild().findAnyGenericExecutableType(getContext());
            }
            return targetExecutable;
        }

        private CodeTree createExecuteChild(CodeTreeBuilder parent, NodeExecutionData execution, ExecutableTypeData sourceExecutable, ActualParameter targetParameter,
                        ActualParameter unexpectedParameter) {
            SpecializationData specialization = getModel();
            TreeSet<TypeData> possiblePolymorphicTypes = lookupPolymorphicTargetTypes(targetParameter);
            if (specialization.isPolymorphic() && targetParameter.getTypeSystemType().isGeneric() && unexpectedParameter == null && possiblePolymorphicTypes.size() > 1) {

                CodeTreeBuilder builder = parent.create();

                boolean elseIf = false;
                for (TypeData possiblePolymoprhicType : possiblePolymorphicTypes) {
                    if (possiblePolymoprhicType.isGeneric()) {
                        continue;
                    }
                    elseIf = builder.startIf(elseIf);

                    ActualParameter sourceParameter = sourceExecutable.findParameter(targetParameter.getLocalName());
                    TypeData sourceType = sourceParameter != null ? sourceParameter.getTypeSystemType() : null;
                    builder.string(polymorphicTypeName(targetParameter)).string(" == ").typeLiteral(possiblePolymoprhicType.getPrimitiveType());
                    builder.end().startBlock();
                    builder.startStatement();
                    builder.tree(createExecuteChildExpression(parent, execution, sourceType, new ActualParameter(targetParameter, possiblePolymoprhicType), unexpectedParameter, null));
                    builder.end();
                    builder.end();
                }

                builder.startElseBlock();
                builder.startStatement().tree(createExecuteChildImplicit(parent, execution, sourceExecutable, targetParameter, unexpectedParameter)).end();
                builder.end();

                return builder.getRoot();
            } else {
                return createExecuteChildImplicit(parent, execution, sourceExecutable, targetParameter, unexpectedParameter);
            }
        }

        protected final List<ActualParameter> getImplicitTypeParamters(SpecializationData model) {
            List<ActualParameter> parameter = new ArrayList<>();
            for (ActualParameter param : model.getSignatureParameters()) {
                NodeChildData child = param.getSpecification().getExecution().getChild();
                List<TypeData> types = child.getNodeData().getTypeSystem().lookupSourceTypes(param.getTypeSystemType());
                if (types.size() > 1) {
                    parameter.add(param);
                }
            }
            return parameter;
        }

        protected final TreeSet<TypeData> lookupPolymorphicTargetTypes(ActualParameter param) {
            SpecializationData specialization = getModel();
            TreeSet<TypeData> possiblePolymorphicTypes = new TreeSet<>();
            for (SpecializationData otherSpecialization : specialization.getNode().getSpecializations()) {
                if (!otherSpecialization.isSpecialized()) {
                    continue;
                }
                ActualParameter otherParameter = otherSpecialization.findParameter(param.getLocalName());
                if (otherParameter != null) {
                    possiblePolymorphicTypes.add(otherParameter.getTypeSystemType());
                }
            }
            return possiblePolymorphicTypes;
        }

        private CodeTree createExecuteChildImplicit(CodeTreeBuilder parent, NodeExecutionData execution, ExecutableTypeData sourceExecutable, ActualParameter param, ActualParameter unexpectedParameter) {
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

                builder.string(implicitTypeName(param));

                builder.end();
            } else {
                List<TypeData> sourceTypes = execution.getChild().getNodeData().getTypeSystem().lookupSourceTypes(param.getTypeSystemType());
                TypeData expectType = sourceParameter != null ? sourceParameter.getTypeSystemType() : null;
                if (sourceTypes.size() > 1) {
                    builder.tree(createExecuteChildImplicitExpressions(parent, param, expectType));
                } else {
                    builder.tree(createExecuteChildExpression(parent, execution, expectType, param, unexpectedParameter, null));
                }
            }
            return builder.getRoot();
        }

        private String createExecuteChildMethodName(ActualParameter param, boolean expect) {
            NodeExecutionData execution = param.getSpecification().getExecution();
            NodeChildData child = execution.getChild();
            if (child.getExecuteWith().size() > 0) {
                return null;
            }
            List<TypeData> sourceTypes = child.getNodeData().getTypeSystem().lookupSourceTypes(param.getTypeSystemType());
            if (sourceTypes.size() <= 1) {
                return null;
            }
            String prefix = expect ? "expect" : "execute";
            String suffix = execution.getIndex() > -1 ? String.valueOf(execution.getIndex()) : "";
            return prefix + Utils.firstLetterUpperCase(child.getName()) + Utils.firstLetterUpperCase(Utils.getSimpleName(param.getType())) + suffix;
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

            CodeExecutableElement method = new CodeExecutableElement(modifiers(PROTECTED, expectType != null ? STATIC : FINAL), param.getType(), childExecuteName);
            method.getThrownTypes().add(getContext().getTruffleTypes().getUnexpectedValueException());
            method.addParameter(new CodeVariableElement(getContext().getTruffleTypes().getFrame(), "frameValue"));
            if (expectType != null) {
                method.addParameter(new CodeVariableElement(expectType.getPrimitiveType(), valueNameEvaluated(param)));
            }
            method.addParameter(new CodeVariableElement(getContext().getType(Class.class), implicitTypeName(param)));

            CodeTreeBuilder builder = method.createBuilder();
            builder.declaration(param.getType(), valueName(param));
            builder.tree(createExecuteChildImplicitExpressions(builder, param, expectType));
            builder.startReturn().string(valueName(param)).end();

            return method;
        }

        private CodeTree createExecuteChildImplicitExpressions(CodeTreeBuilder parent, ActualParameter targetParameter, TypeData expectType) {
            CodeTreeBuilder builder = parent.create();
            NodeData node = getModel().getNode();
            NodeExecutionData execution = targetParameter.getSpecification().getExecution();
            List<TypeData> sourceTypes = node.getTypeSystem().lookupSourceTypes(targetParameter.getTypeSystemType());
            boolean elseIf = false;
            int index = 0;
            for (TypeData sourceType : sourceTypes) {
                if (index < sourceTypes.size() - 1) {
                    elseIf = builder.startIf(elseIf);
                    builder.string(implicitTypeName(targetParameter)).string(" == ").typeLiteral(sourceType.getPrimitiveType());
                    builder.end();
                    builder.startBlock();
                } else {
                    builder.startElseBlock();
                }

                ExecutableTypeData implictExecutableTypeData = execution.getChild().findExecutableType(getContext(), sourceType);
                if (implictExecutableTypeData == null) {
                    /*
                     * For children with executeWith.size() > 0 an executable type may not exist so
                     * use the generic executable type which is guaranteed to exist. An expect call
                     * is inserted automatically by #createExecuteExpression.
                     */
                    implictExecutableTypeData = execution.getChild().getNodeData().findExecutableType(node.getTypeSystem().getGenericTypeData(), execution.getChild().getExecuteWith().size());
                }

                ImplicitCastData cast = execution.getChild().getNodeData().getTypeSystem().lookupCast(sourceType, targetParameter.getTypeSystemType());
                CodeTree execute = createExecuteChildExpression(builder, execution, expectType, targetParameter, null, cast);
                builder.statement(execute);
                builder.end();
                index++;
            }
            return builder.getRoot();
        }

        private CodeTree createExecuteChildExpression(CodeTreeBuilder parent, NodeExecutionData execution, TypeData sourceParameterType, ActualParameter targetParameter,
                        ActualParameter unexpectedParameter, ImplicitCastData cast) {
            // assignments: targetType <- castTargetType <- castSourceType <- sourceType
            TypeData sourceType = sourceParameterType;
            TypeData targetType = targetParameter.getTypeSystemType();
            TypeData castSourceType = targetType;
            TypeData castTargetType = targetType;

            if (cast != null) {
                castSourceType = cast.getSourceType();
                castTargetType = cast.getTargetType();
            }

            CodeTree expression;
            if (sourceType == null) {
                ExecutableTypeData targetExecutable = resolveExecutableType(execution, castSourceType);
                expression = createExecuteChildExpression(parent, execution, targetExecutable, unexpectedParameter);
                sourceType = targetExecutable.getType();
            } else {
                expression = CodeTreeBuilder.singleString(valueNameEvaluated(targetParameter));
            }

            // target = expectTargetType(implicitCast(expectCastSourceType(source)))
            TypeSystemData typeSystem = execution.getChild().getNodeData().getTypeSystem();
            expression = createExpectType(typeSystem, sourceType, castSourceType, expression);
            expression = createImplicitCast(parent, typeSystem, cast, expression);
            expression = createExpectType(typeSystem, castTargetType, targetType, expression);

            CodeTreeBuilder builder = parent.create();
            builder.string(valueName(targetParameter));
            builder.string(" = ");
            builder.tree(expression);
            return builder.getRoot();
        }

        private CodeTree createImplicitCast(CodeTreeBuilder parent, TypeSystemData typeSystem, ImplicitCastData cast, CodeTree expression) {
            if (cast == null) {
                return expression;
            }
            CodeTreeBuilder builder = parent.create();
            startCallTypeSystemMethod(getContext(), builder, typeSystem, cast.getMethodName());
            builder.tree(expression);
            builder.end().end();
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

        private boolean hasUnexpected(ActualParameter sourceParameter, ActualParameter targetParameter, ActualParameter unexpectedParameter) {
            NodeExecutionData execution = targetParameter.getSpecification().getExecution();

            if (getModel().isPolymorphic() && targetParameter.getTypeSystemType().isGeneric() && unexpectedParameter == null) {
                // check for other polymorphic types
                TreeSet<TypeData> polymorphicTargetTypes = lookupPolymorphicTargetTypes(targetParameter);
                if (polymorphicTargetTypes.size() > 1) {
                    for (TypeData polymorphicTargetType : polymorphicTargetTypes) {
                        if (hasUnexpectedType(execution, sourceParameter, polymorphicTargetType)) {
                            return true;
                        }
                    }
                }
            }

            if (hasUnexpectedType(execution, sourceParameter, targetParameter.getTypeSystemType())) {
                return true;
            }
            return false;
        }

        private boolean hasUnexpectedType(NodeExecutionData execution, ActualParameter sourceParameter, TypeData targetType) {
            List<TypeData> implicitSourceTypes = getModel().getNode().getTypeSystem().lookupSourceTypes(targetType);

            for (TypeData implicitSourceType : implicitSourceTypes) {
                TypeData sourceType;
                ExecutableTypeData targetExecutable = resolveExecutableType(execution, implicitSourceType);
                if (sourceParameter != null) {
                    sourceType = sourceParameter.getTypeSystemType();
                } else {
                    if (targetExecutable.hasUnexpectedValue(getContext())) {
                        return true;
                    }
                    sourceType = targetExecutable.getType();
                }

                ImplicitCastData cast = getModel().getNode().getTypeSystem().lookupCast(implicitSourceType, targetType);
                if (cast != null) {
                    if (cast.getSourceType().needsCastTo(getContext(), targetType)) {
                        return true;
                    }
                }

                if (sourceType.needsCastTo(getContext(), targetType)) {
                    return true;
                }
            }
            return false;
        }

        private CodeTree createCatchUnexpectedTree(CodeTreeBuilder parent, CodeTree body, SpecializationData specialization, ExecutableTypeData currentExecutable, ActualParameter param,
                        boolean shortCircuit, ActualParameter unexpectedParameter) {
            CodeTreeBuilder builder = new CodeTreeBuilder(parent);
            ActualParameter sourceParameter = currentExecutable.findParameter(param.getLocalName());
            boolean unexpected = hasUnexpected(sourceParameter, param, unexpectedParameter);
            if (!unexpected) {
                return body;
            }

            if (!shortCircuit) {
                builder.declaration(param.getType(), valueName(param));
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
            SpecializationData polymorphic = node.getPolymorphicSpecialization();

            CodeTreeBuilder builder = new CodeTreeBuilder(parent);
            builder.startStatement().string(polymorphicTypeName(param)).string(" = ").typeLiteral(getContext().getType(Object.class)).end();

            builder.startReturn();

            CodeTreeBuilder execute = new CodeTreeBuilder(builder);
            execute.startCall("next0", EXECUTE_POLYMORPHIC_NAME);
            addInternalValueParameterNames(execute, specialization, polymorphic, param.getLocalName(), true, null);
            execute.end();

            TypeData sourceType = polymorphic.getReturnType().getTypeSystemType();

            builder.tree(createExpectExecutableType(node, sourceType, currentExecutable, execute.getRoot()));

            builder.end();
            return builder.getRoot();
        }

        private CodeTree createExecuteChildExpression(CodeTreeBuilder parent, NodeExecutionData targetExecution, ExecutableTypeData targetExecutable, ActualParameter unexpectedParameter) {
            CodeTreeBuilder builder = new CodeTreeBuilder(parent);
            if (targetExecution != null) {
                builder.tree(createAccessChild(targetExecution));
                builder.string(".");
            }

            builder.startCall(targetExecutable.getMethodName());

            // TODO this should be merged with #createTemplateMethodCall
            int index = 0;
            for (ActualParameter parameter : targetExecutable.getParameters()) {

                if (!parameter.getSpecification().isSignature()) {
                    builder.string(parameter.getLocalName());
                } else {

                    if (index < targetExecution.getChild().getExecuteWith().size()) {
                        NodeChildData child = targetExecution.getChild().getExecuteWith().get(index);

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

        private CodeTree createShortCircuitTree(CodeTreeBuilder parent, CodeTree body, SpecializationData specialization, ActualParameter parameter, ActualParameter exceptionParam) {
            NodeExecutionData execution = parameter.getSpecification().getExecution();
            if (execution == null || !execution.isShortCircuit()) {
                return body;
            }

            CodeTreeBuilder builder = new CodeTreeBuilder(parent);
            ActualParameter shortCircuitParam = specialization.getPreviousParam(parameter);
            builder.tree(createShortCircuitValue(builder, specialization, execution, shortCircuitParam, exceptionParam));
            builder.declaration(parameter.getType(), valueName(parameter), CodeTreeBuilder.createBuilder().defaultValue(parameter.getType()));
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

        private CodeTree createShortCircuitValue(CodeTreeBuilder parent, SpecializationData specialization, NodeExecutionData execution, ActualParameter shortCircuitParam,
                        ActualParameter exceptionParam) {
            CodeTreeBuilder builder = new CodeTreeBuilder(parent);
            int shortCircuitIndex = 0;
            for (NodeExecutionData otherExectuion : specialization.getNode().getChildExecutions()) {
                if (otherExectuion.isShortCircuit()) {
                    if (otherExectuion == execution) {
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

        protected CodeTree createReturnExecuteAndSpecialize(CodeTreeBuilder parent, ExecutableTypeData executable, SpecializationData current, ActualParameter exceptionParam, String reason) {
            NodeData node = current.getNode();
            SpecializationData generic = node.getGenericSpecialization();
            CodeTreeBuilder specializeCall = new CodeTreeBuilder(parent);
            specializeCall.startCall(EXECUTE_SPECIALIZE_NAME);
            specializeCall.string(String.valueOf(node.getSpecializations().indexOf(current)));
            addInternalValueParameterNames(specializeCall, generic, node.getGenericSpecialization(), exceptionParam != null ? exceptionParam.getLocalName() : null, true, null);
            specializeCall.doubleQuote(reason);
            specializeCall.end().end();

            CodeTreeBuilder builder = new CodeTreeBuilder(parent);

            builder.startReturn();
            builder.tree(createExpectExecutableType(node, generic.getReturnType().getTypeSystemType(), executable, specializeCall.getRoot()));
            builder.end();

            return builder.getRoot();
        }

        protected final CodeExecutableElement createUpdateTypes(TypeMirror polymorphicType) {
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PROTECTED), getContext().getType(void.class), UPDATE_TYPES_NAME);
            method.getParameters().add(new CodeVariableElement(polymorphicType, "polymorphic"));
            CodeTreeBuilder builder = method.createBuilder();

            if (getModel().isPolymorphic()) {
                builder.startStatement();
                builder.startCall("next0", "updateTypes").string("polymorphic").end();
                builder.end();
            } else if (getModel().isSpecialized()) {
                for (ActualParameter parameter : getModel().getParameters()) {
                    if (!parameter.getSpecification().isSignature()) {
                        continue;
                    }
                    if (lookupPolymorphicTargetTypes(parameter).size() <= 1) {
                        continue;
                    }
                    builder.startStatement();
                    builder.startCall("polymorphic", createUpdateTypeName(parameter));
                    builder.typeLiteral(parameter.getType());
                    builder.end().end();
                }

                builder.startStatement().startCall("super", UPDATE_TYPES_NAME).string("polymorphic").end().end();
            }
            return method;
        }

        protected String createUpdateTypeName(ActualParameter parameter) {
            return "update" + Utils.firstLetterUpperCase(parameter.getLocalName()) + "Type";
        }
    }

    private class PolymorphicNodeFactory extends SpecializedNodeFactory {

        public PolymorphicNodeFactory(ProcessorContext context, CodeTypeElement nodeGen) {
            super(context, nodeGen);
        }

        @Override
        public CodeTypeElement create(SpecializationData polymorph) {
            NodeData node = polymorph.getNode();
            TypeMirror baseType = node.getNodeType();
            if (nodeGen != null) {
                baseType = nodeGen.asType();
            }
            CodeTypeElement clazz = createClass(node, modifiers(PRIVATE, STATIC, FINAL), nodePolymorphicClassName(node), baseType, false);

            clazz.getAnnotationMirrors().add(createNodeInfo(node, NodeCost.NONE));

            for (ActualParameter polymorphParameter : polymorph.getSignatureParameters()) {
                if (!polymorphParameter.getTypeSystemType().isGeneric()) {
                    continue;
                }
                Set<TypeData> types = new HashSet<>();
                for (SpecializationData specialization : node.getSpecializations()) {
                    if (!specialization.isSpecialized()) {
                        continue;
                    }
                    ActualParameter parameter = specialization.findParameter(polymorphParameter.getLocalName());
                    assert parameter != null;
                    types.add(parameter.getTypeSystemType());
                }
                CodeVariableElement var = new CodeVariableElement(modifiers(PRIVATE), getContext().getType(Class.class), polymorphicTypeName(polymorphParameter));
                var.getAnnotationMirrors().add(new CodeAnnotationMirror(getContext().getTruffleTypes().getCompilationFinal()));
                clazz.add(var);
            }

            return clazz;
        }

        @Override
        protected void createChildren(SpecializationData specialization) {
            CodeTypeElement clazz = getElement();

            createConstructors(clazz);
            createExecuteMethods(specialization);

            getElement().add(createUpdateTypes(nodeGen.asType()));

            for (ActualParameter parameter : specialization.getParameters()) {
                if (!parameter.getSpecification().isSignature()) {
                    continue;
                }
                if (lookupPolymorphicTargetTypes(parameter).size() <= 1) {
                    continue;
                }
                getElement().add(createUpdateType(parameter));
            }

            if (needsInvokeCopyConstructorMethod()) {
                clazz.add(createInvokeCopyConstructor(nodeGen.asType(), specialization));
            }

            createCachedExecuteMethods(specialization);
            clazz.add(createGetCost(specialization.getNode(), specialization, NodeCost.NONE));
        }

        private ExecutableElement createUpdateType(ActualParameter parameter) {
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PROTECTED), getContext().getType(void.class), createUpdateTypeName(parameter));
            method.getParameters().add(new CodeVariableElement(getContext().getType(Class.class), "type"));
            CodeTreeBuilder builder = method.createBuilder();

            String fieldName = polymorphicTypeName(parameter);
            builder.startIf().string(fieldName).isNull().end().startBlock();
            builder.startStatement().string(fieldName).string(" = ").string("type").end();
            builder.end();
            builder.startElseIf().string(fieldName).string(" != ").string("type").end();
            builder.startBlock();
            builder.startStatement().string(fieldName).string(" = ").typeLiteral(getContext().getType(Object.class)).end();
            builder.end();

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

            NodeCost cost;
            if (specialization.isGeneric()) {
                cost = NodeCost.MEGAMORPHIC;
            } else if (specialization.isUninitialized()) {
                cost = NodeCost.UNINITIALIZED;
            } else if (specialization.isPolymorphic()) {
                cost = NodeCost.NONE;
            } else if (specialization.isSpecialized()) {
                cost = NodeCost.MONOMORPHIC;
            } else {
                throw new AssertionError();
            }
            clazz.getAnnotationMirrors().add(createNodeInfo(node, cost));

            return clazz;
        }

        protected CodeAnnotationMirror createNodeInfo(NodeData node, NodeCost cost) {
            String shortName = node.getShortName();
            CodeAnnotationMirror nodeInfoMirror = new CodeAnnotationMirror(getContext().getTruffleTypes().getNodeInfoAnnotation());
            if (shortName != null) {
                nodeInfoMirror.setElementValue(nodeInfoMirror.findExecutableElement("shortName"), new CodeAnnotationValue(shortName));
            }

            DeclaredType nodeinfoCost = getContext().getTruffleTypes().getNodeCost();
            VariableElement varKind = Utils.findVariableElement(nodeinfoCost, cost.name());

            nodeInfoMirror.setElementValue(nodeInfoMirror.findExecutableElement("cost"), new CodeAnnotationValue(varKind));
            return nodeInfoMirror;
        }

        @Override
        protected void createChildren(SpecializationData specialization) {
            CodeTypeElement clazz = getElement();
            createConstructors(clazz);

            createExecuteMethods(specialization);
            createCachedExecuteMethods(specialization);
            if (specialization.getNode().isPolymorphic()) {
                getElement().add(createUpdateTypes(nodeGen.asType()));
            }
            if (needsInvokeCopyConstructorMethod()) {
                clazz.add(createInvokeCopyConstructor(nodeGen.asType(), specialization));
            }

            if (specialization.isGeneric()) {
                clazz.add(createGetCost(specialization.getNode(), specialization, NodeCost.MEGAMORPHIC));
            } else if (specialization.isUninitialized()) {
                clazz.add(createGetCost(specialization.getNode(), specialization, NodeCost.UNINITIALIZED));
            }
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
                if (superConstructor == null) {
                    continue;
                }
                CodeTree body = superConstructor.getBodyTree();
                CodeTreeBuilder builder = superConstructor.createBuilder();
                builder.tree(body);

                if (node.isPolymorphic()) {
                    if (specialization.isSpecialized() || specialization.isPolymorphic()) {
                        builder.statement("this.next0 = copy.next0");
                    }
                }
                if (superConstructor != null) {
                    for (ActualParameter param : getImplicitTypeParamters(getModel())) {
                        clazz.add(new CodeVariableElement(modifiers(PRIVATE, FINAL), getContext().getType(Class.class), implicitTypeName(param)));
                        superConstructor.getParameters().add(new CodeVariableElement(getContext().getType(Class.class), implicitTypeName(param)));

                        builder.startStatement();
                        builder.string("this.").string(implicitTypeName(param)).string(" = ").string(implicitTypeName(param));
                        builder.end();
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
                CodeTreeBuilder builder = executeMethod.getBuilder();
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
            if (!node.isPolymorphic()) {
                return;
            }

            CodeTypeElement clazz = getElement();

            final SpecializationData polymorphic = node.getPolymorphicSpecialization();
            ExecutableElement executeCached = nodeGen.getMethod(EXECUTE_POLYMORPHIC_NAME);
            CodeExecutableElement executeMethod = CodeExecutableElement.clone(getContext().getEnvironment(), executeCached);
            executeMethod.getModifiers().remove(Modifier.ABSTRACT);
            CodeTreeBuilder builder = executeMethod.createBuilder();

            if (specialization.isGeneric() || specialization.isPolymorphic()) {
                builder.startThrow().startNew(getContext().getType(AssertionError.class));
                builder.doubleQuote("Should not be reached.");
                builder.end().end();
            } else if (specialization.isUninitialized()) {
                builder.tree(createAppendPolymorphic(builder, specialization));
            } else {
                CodeTreeBuilder elseBuilder = new CodeTreeBuilder(builder);
                elseBuilder.startReturn().startCall("this.next0", EXECUTE_POLYMORPHIC_NAME);
                addInternalValueParameterNames(elseBuilder, polymorphic, polymorphic, null, true, null);
                elseBuilder.end().end();

                boolean forceElse = specialization.getExceptions().size() > 0;
                builder.tree(createExecuteTree(builder, polymorphic, SpecializationGroup.create(specialization), false, new CodeBlock<SpecializationData>() {

                    public CodeTree create(CodeTreeBuilder b, SpecializationData current) {
                        return createGenericInvoke(b, polymorphic, current);
                    }
                }, elseBuilder.getRoot(), forceElse, true, true));
            }
            clazz.add(executeMethod);
        }

        private CodeTree createAppendPolymorphic(CodeTreeBuilder parent, SpecializationData specialization) {
            NodeData node = specialization.getNode();

            CodeTreeBuilder builder = new CodeTreeBuilder(parent);
            builder.tree(createDeoptimize(builder));

            builder.declaration(getContext().getTruffleTypes().getNode(), "root", "this");
            builder.declaration(getContext().getType(int.class), "depth", "0");
            builder.tree(createFindRoot(builder, node, true));
            builder.newLine();

            builder.startIf().string("depth > ").string(String.valueOf(node.getPolymorphicDepth())).end();
            builder.startBlock();
            String message = "Polymorphic limit reached (" + node.getPolymorphicDepth() + ")";
            String castRoot = "(" + baseClassName(node) + ") root";
            builder.tree(createGenericInvoke(builder, node.getPolymorphicSpecialization(), node.getGenericSpecialization(),
                            createReplaceCall(builder, node.getGenericSpecialization(), "root", castRoot, message), null));
            builder.end();

            builder.startElseBlock();
            builder.startStatement().startCall("setNext0");
            builder.startNew(nodeSpecializationClassName(node.getUninitializedSpecialization())).string("this").end();
            builder.end().end();

            CodeTreeBuilder specializeCall = new CodeTreeBuilder(builder);
            specializeCall.startCall(EXECUTE_SPECIALIZE_NAME);
            specializeCall.string("0");
            addInternalValueParameterNames(specializeCall, specialization, node.getGenericSpecialization(), null, true, null);
            specializeCall.startGroup().doubleQuote("Uninitialized polymorphic (").string(" + depth + ").doubleQuote("/" + node.getPolymorphicDepth() + ")").end();
            specializeCall.end().end();

            builder.declaration(node.getGenericSpecialization().getReturnType().getType(), "result", specializeCall.getRoot());

            CodeTree root = builder.create().cast(nodePolymorphicClassName(node)).string("root").getRoot();
            builder.startIf().string("this.next0 != null").end().startBlock();
            builder.startStatement().string("(").tree(root).string(").").startCall(UPDATE_TYPES_NAME).tree(root).end().end();
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

            CodeTreeBuilder builder = method.createBuilder();
            int i = 0;
            int signatureIndex = -1;
            for (VariableElement param : method.getParameters()) {
                CodeVariableElement var = CodeVariableElement.clone(param);
                ActualParameter actualParameter = i < execType.getParameters().size() ? execType.getParameters().get(i) : null;
                String name;
                if (actualParameter != null) {
                    if (actualParameter.getSpecification().isSignature()) {
                        signatureIndex++;
                    }

                    if (evaluated && actualParameter.getSpecification().isSignature()) {
                        name = valueNameEvaluated(actualParameter);
                    } else {
                        name = valueName(actualParameter);
                    }

                    int varArgCount = getModel().getSignatureSize() - signatureIndex;
                    if (evaluated && actualParameter.isTypeVarArgs()) {
                        ActualParameter baseVarArgs = actualParameter;
                        name = valueName(baseVarArgs) + "Args";

                        builder.startAssert().string(name).string(" != null").end();
                        builder.startAssert().string(name).string(".length == ").string(String.valueOf(varArgCount)).end();
                        if (varArgCount > 0) {
                            List<ActualParameter> varArgsParameter = execType.getParameters().subList(i, execType.getParameters().size());
                            for (ActualParameter varArg : varArgsParameter) {
                                if (varArgCount <= 0) {
                                    break;
                                }
                                TypeMirror type = baseVarArgs.getType();
                                if (type.getKind() == TypeKind.ARRAY) {
                                    type = ((ArrayType) type).getComponentType();
                                }
                                builder.declaration(type, valueNameEvaluated(varArg), name + "[" + varArg.getTypeVarArgsIndex() + "]");
                                varArgCount--;
                            }
                        }
                    }
                } else {
                    name = "arg" + i;
                }
                var.setName(name);
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
            }, returnSpecialized, false, false, false));

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
                returnBuilder.startCall("next0", EXECUTE_POLYMORPHIC_NAME);
                addInternalValueParameterNames(returnBuilder, specialization, specialization, null, true, null);
                returnBuilder.end();
            } else if (specialization.isUninitialized()) {
                returnBuilder.startCall("super", EXECUTE_SPECIALIZE_NAME);
                returnBuilder.string("0");
                addInternalValueParameterNames(returnBuilder, specialization, specialization, null, true, null);
                returnBuilder.doubleQuote("Uninitialized monomorphic");
                returnBuilder.end();
            } else if (specialization.getMethod() == null && !node.needsRewrites(context)) {
                emitEncounteredSynthetic(builder, specialization);
            } else if (specialization.isGeneric()) {
                returnBuilder.startCall("super", EXECUTE_GENERIC_NAME);
                addInternalValueParameterNames(returnBuilder, specialization, specialization, null, node.needsFrame(getContext()), null);
                returnBuilder.end();
            } else {
                returnBuilder.tree(createTemplateMethodCall(returnBuilder, null, specialization, specialization, null));
            }

            if (!returnBuilder.isEmpty()) {
                TypeData targetType = node.getTypeSystem().findTypeData(builder.findMethod().getReturnType());
                TypeData sourceType = specialization.getReturnType().getTypeSystemType();

                builder.startReturn();
                if (targetType == null || sourceType == null) {
                    builder.tree(returnBuilder.getRoot());
                } else if (sourceType.needsCastTo(getContext(), targetType)) {
                    String castMethodName = TypeSystemCodeGenerator.expectTypeMethodName(targetType);
                    if (!executable.hasUnexpectedValue(context)) {
                        castMethodName = TypeSystemCodeGenerator.asTypeMethodName(targetType);
                    }
                    builder.tree(createCallTypeSystemMethod(context, parent, node, castMethodName, returnBuilder.getRoot()));
                } else {
                    builder.tree(returnBuilder.getRoot());
                }
                builder.end();
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
