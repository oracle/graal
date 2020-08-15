/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.dsl.processor.generator;

import static com.oracle.truffle.dsl.processor.java.ElementUtils.modifiers;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;

import com.oracle.truffle.dsl.processor.AnnotationProcessor;
import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.GeneratorMode;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.model.MessageContainer.Message;
import com.oracle.truffle.dsl.processor.model.NodeChildData;
import com.oracle.truffle.dsl.processor.model.NodeData;

public class NodeCodeGenerator extends CodeTypeElementFactory<NodeData> {

    @Override
    public List<CodeTypeElement> create(ProcessorContext context, AnnotationProcessor<?> processor, NodeData node) {
        Map<String, CodeVariableElement> libraryConstants = new LinkedHashMap<>();
        List<CodeTypeElement> rootTypes = createImpl(context, node, libraryConstants);
        if (rootTypes != null) {
            if (rootTypes.size() != 1) {
                throw new AssertionError();
            }
            rootTypes.get(0).addAll(libraryConstants.values());
        }
        return rootTypes;
    }

    private static List<CodeTypeElement> createImpl(ProcessorContext context, NodeData node, Map<String, CodeVariableElement> libraryConstants) {
        List<CodeTypeElement> enclosedTypes = new ArrayList<>();
        for (NodeData childNode : node.getEnclosingNodes()) {
            List<CodeTypeElement> type = createImpl(context, childNode, libraryConstants);
            if (type != null) {
                enclosedTypes.addAll(type);
            }
        }
        List<CodeTypeElement> generatedNodes = generateNodes(context, node, libraryConstants);
        if (!generatedNodes.isEmpty() || !enclosedTypes.isEmpty()) {
            CodeTypeElement type;
            if (generatedNodes.isEmpty()) {
                type = createContainer(node);
            } else {
                type = wrapGeneratedNodes(context, node, generatedNodes);
            }

            for (CodeTypeElement enclosedFactory : enclosedTypes) {
                type.add(makeInnerClass(enclosedFactory));
            }

            if (node.getDeclaringNode() == null && enclosedTypes.size() > 0) {
                ExecutableElement getFactories = createGetFactories(context, node);
                if (getFactories != null) {
                    type.add(getFactories);
                }
            }

            return Arrays.asList(type);
        } else {
            return null;
        }
    }

    private static CodeTypeElement makeInnerClass(CodeTypeElement type) {
        Set<Modifier> modifiers = type.getModifiers();
        modifiers.add(Modifier.STATIC);
        return type;
    }

    private static CodeTypeElement wrapGeneratedNodes(ProcessorContext context, NodeData node, List<CodeTypeElement> generatedNodes) {
        if (node.isGenerateFactory()) {
            // wrap all types into a generated factory
            CodeTypeElement factoryElement = new NodeFactoryFactory(context, node, generatedNodes.get(0)).create();
            for (CodeTypeElement generatedNode : generatedNodes) {
                factoryElement.add(makeInnerClass(generatedNode));
            }
            return factoryElement;
        } else {
            // wrap all types into the first node
            CodeTypeElement first = generatedNodes.get(0);
            CodeTypeElement second = first;
            if (generatedNodes.size() > 1) {
                second = generatedNodes.get(1);
                for (CodeTypeElement generatedNode : generatedNodes) {
                    if (first != generatedNode) {
                        first.add(makeInnerClass(generatedNode));
                    }
                }
            }
            List<ExecutableElement> constructors = GeneratorUtils.findUserConstructors(second.asType());
            first.getEnclosedElements().addAll(NodeFactoryFactory.createFactoryMethods(node, constructors));
            ElementUtils.setVisibility(first.getModifiers(), ElementUtils.getVisibility(node.getTemplateType().getModifiers()));

            return first;
        }
    }

    private static CodeTypeElement createContainer(NodeData node) {
        CodeTypeElement container;
        Modifier visibility = ElementUtils.getVisibility(node.getTemplateType().getModifiers());
        String containerName = NodeFactoryFactory.factoryClassName(node.getTemplateType());
        container = GeneratorUtils.createClass(node, null, modifiers(), containerName, null);
        if (visibility != null) {
            container.getModifiers().add(visibility);
        }
        container.getModifiers().add(Modifier.FINAL);

        return container;
    }

    private static String getAccessorClassName(NodeData node) {
        return node.isGenerateFactory() ? NodeFactoryFactory.factoryClassName(node.getTemplateType()) : createNodeTypeName(node.getTemplateType());
    }

    private static Element buildClassName(Element nodeElement, boolean first, boolean generateFactory) {
        if (nodeElement == null || nodeElement.getKind() == ElementKind.PACKAGE) {
            return nodeElement;
        }
        if (nodeElement.getKind().isClass()) {
            Element enclosingElement = buildClassName(nodeElement.getEnclosingElement(), false, generateFactory);
            PackageElement enclosingPackage = null;
            CodeTypeElement enclosingClass = null;
            if (enclosingElement != null) {
                if (enclosingElement.getKind() == ElementKind.PACKAGE) {
                    enclosingPackage = (PackageElement) enclosingElement;
                } else if (enclosingElement.getKind().isClass()) {
                    enclosingClass = (CodeTypeElement) enclosingElement;
                }
            }
            if (first) {
                if (generateFactory) {
                    enclosingClass = createClass(enclosingPackage, enclosingClass, NodeFactoryFactory.factoryClassName(nodeElement));
                }
                enclosingClass = createClass(enclosingPackage, enclosingClass, createNodeTypeName((TypeElement) nodeElement));
            } else {
                if (isSpecializedNode(nodeElement)) {
                    enclosingClass = createClass(enclosingPackage, enclosingClass, createNodeTypeName((TypeElement) nodeElement));
                } else {
                    enclosingClass = createClass(enclosingPackage, enclosingClass, NodeFactoryFactory.factoryClassName(nodeElement));
                }
            }
            return enclosingClass;
        }
        return null;
    }

    private static CodeTypeElement createClass(PackageElement pack, CodeTypeElement enclosingClass, String name) {
        CodeTypeElement type = new CodeTypeElement(modifiers(PUBLIC), ElementKind.CLASS, pack, name);
        if (enclosingClass != null) {
            enclosingClass.add(type);
        }
        return type;
    }

    public static boolean isSpecializedNode(TypeMirror mirror) {
        TypeElement type = ElementUtils.castTypeElement(mirror);
        if (type != null) {
            return isSpecializedNode(type);
        }
        return false;
    }

    static boolean isSpecializedNode(Element element) {
        if (element.getKind().isClass()) {
            if (ElementUtils.isAssignable(element.asType(), ProcessorContext.getInstance().getTypes().Node)) {
                for (ExecutableElement method : ElementFilter.methodsIn(element.getEnclosedElements())) {
                    if (ElementUtils.findAnnotationMirror(method, ProcessorContext.getInstance().getTypes().Specialization) != null) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static TypeMirror nodeType(NodeData node) {
        TypeElement element = node.getTemplateType();
        CodeTypeElement type = (CodeTypeElement) buildClassName(element, true, node.isGenerateFactory());
        return type.asType();
    }

    public static TypeMirror factoryOrNodeType(NodeData node) {
        TypeElement element = node.getTemplateType();
        CodeTypeElement type = (CodeTypeElement) buildClassName(element, true, node.isGenerateFactory());
        if (node.isGenerateFactory()) {
            return type.getEnclosingElement().asType();
        } else {
            return type.asType();
        }
    }

    private static final String NODE_SUFFIX = "NodeGen";

    private static String resolveNodeId(TypeElement node) {
        String nodeid = node.getSimpleName().toString();
        if (nodeid.endsWith("Node") && !nodeid.equals("Node")) {
            nodeid = nodeid.substring(0, nodeid.length() - 4);
        }
        return nodeid;
    }

    static String createNodeTypeName(TypeElement nodeType) {
        return resolveNodeId(nodeType) + NODE_SUFFIX;
    }

    private static List<CodeTypeElement> generateNodes(ProcessorContext context, NodeData node, Map<String, CodeVariableElement> libraryConstants) {
        if (!node.needsFactory()) {
            return Collections.emptyList();
        }

        CodeTypeElement type = GeneratorUtils.createClass(node, null, modifiers(FINAL), createNodeTypeName(node.getTemplateType()), node.getTemplateType().asType());
        ElementUtils.setVisibility(type.getModifiers(), ElementUtils.getVisibility(node.getTemplateType().getModifiers()));
        if (node.hasErrors()) {
            generateErrorNode(context, node, type);
            return Arrays.asList(type);
        }

        type = new FlatNodeGenFactory(context, GeneratorMode.DEFAULT, node, libraryConstants).create(type);

        return Arrays.asList(type);
    }

    private static void generateErrorNode(ProcessorContext context, NodeData node, CodeTypeElement type) {
        for (ExecutableElement superConstructor : GeneratorUtils.findUserConstructors(node.getTemplateType().asType())) {
            CodeExecutableElement constructor = GeneratorUtils.createConstructorUsingFields(modifiers(), type, superConstructor);
            ElementUtils.setVisibility(constructor.getModifiers(), ElementUtils.getVisibility(superConstructor.getModifiers()));

            List<CodeVariableElement> childParameters = new ArrayList<>();
            for (NodeChildData child : node.getChildren()) {
                childParameters.add(new CodeVariableElement(child.getOriginalType(), child.getName()));
            }
            constructor.getParameters().addAll(superConstructor.getParameters().size(), childParameters);

            type.add(constructor);
        }
        for (ExecutableElement method : ElementFilter.methodsIn(context.getEnvironment().getElementUtils().getAllMembers(node.getTemplateType()))) {
            if (method.getModifiers().contains(Modifier.ABSTRACT) && ElementUtils.getVisibility(method.getModifiers()) != Modifier.PRIVATE) {
                CodeExecutableElement overrideMethod = CodeExecutableElement.clone(method);
                overrideMethod.getModifiers().remove(Modifier.ABSTRACT);
                List<Message> messages = node.collectMessages();

                String message = messages.toString();
                message = message.replace("\"", "\\\"");
                message = message.replace(System.lineSeparator(), "\\\\n");
                overrideMethod.createBuilder().startThrow().startNew(context.getType(RuntimeException.class)).doubleQuote("Truffle DSL compiler errors: " + message).end().end();
                type.add(overrideMethod);
            }
        }
    }

    private static ExecutableElement createGetFactories(ProcessorContext context, NodeData node) {
        List<NodeData> factoryList = node.getNodesWithFactories();
        if (node.needsFactory() && node.isGenerateFactory()) {
            factoryList.add(node);
        }

        if (factoryList.isEmpty()) {
            return null;
        }

        List<TypeMirror> nodeTypesList = new ArrayList<>();
        TypeMirror prev = null;
        boolean allSame = true;
        for (NodeData child : factoryList) {
            nodeTypesList.add(child.getNodeType());
            if (prev != null && !ElementUtils.typeEquals(child.getNodeType(), prev)) {
                allSame = false;
            }
            prev = child.getNodeType();
        }
        TypeMirror commonNodeSuperType = ElementUtils.getCommonSuperType(context, nodeTypesList);

        Types types = context.getEnvironment().getTypeUtils();
        TypeMirror factoryType = context.getTypes().NodeFactory;
        TypeMirror baseType;
        if (allSame) {
            baseType = ElementUtils.getDeclaredType(ElementUtils.fromTypeMirror(factoryType), commonNodeSuperType);
        } else {
            baseType = ElementUtils.getDeclaredType(ElementUtils.fromTypeMirror(factoryType), types.getWildcardType(commonNodeSuperType, null));
        }
        TypeMirror listType = ElementUtils.getDeclaredType(ElementUtils.fromTypeMirror(context.getType(List.class)), baseType);

        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC, STATIC), listType, "getFactories");

        CodeTreeBuilder builder = method.createBuilder();
        builder.startReturn();

        if (factoryList.size() > 1) {
            builder.startStaticCall(context.getType(Arrays.class), "asList");
        } else {
            builder.startStaticCall(context.getType(Collections.class), "singletonList");
        }

        for (NodeData child : factoryList) {
            builder.startGroup();
            NodeData childNode = child;
            List<NodeData> factories = new ArrayList<>();
            while (childNode.getDeclaringNode() != null) {
                factories.add(childNode);
                childNode = childNode.getDeclaringNode();
            }
            Collections.reverse(factories);
            for (NodeData nodeData : factories) {

                builder.string(getAccessorClassName(nodeData)).string(".");
            }
            builder.string("getInstance()");
            builder.end();
        }
        builder.end();
        builder.end();

        return method;
    }

}
