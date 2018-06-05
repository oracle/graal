/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import static com.oracle.truffle.dsl.processor.java.ElementUtils.modifiers;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;

import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.java.model.GeneratedTypeMirror;
import com.oracle.truffle.dsl.processor.model.MessageContainer.Message;
import com.oracle.truffle.dsl.processor.model.NodeChildData;
import com.oracle.truffle.dsl.processor.model.NodeData;

public class NodeCodeGenerator extends CodeTypeElementFactory<NodeData> {

    @Override
    public CodeTypeElement create(ProcessorContext context, NodeData node) {
        List<CodeTypeElement> enclosedTypes = new ArrayList<>();
        for (NodeData childNode : node.getEnclosingNodes()) {
            CodeTypeElement type = create(context, childNode);
            if (type != null) {
                enclosedTypes.add(type);
            }
        }
        List<CodeTypeElement> generatedNodes = generateNodes(context, node);
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

            return type;
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

            new NodeFactoryFactory(context, node, second).createFactoryMethods(first);
            ElementUtils.setVisibility(first.getModifiers(), ElementUtils.getVisibility(node.getTemplateType().getModifiers()));

            return first;
        }
    }

    private static CodeTypeElement createContainer(NodeData node) {
        CodeTypeElement container;
        Modifier visibility = ElementUtils.getVisibility(node.getTemplateType().getModifiers());
        String containerName = NodeFactoryFactory.factoryClassName(node);
        container = GeneratorUtils.createClass(node, null, modifiers(), containerName, null);
        if (visibility != null) {
            container.getModifiers().add(visibility);
        }
        container.getModifiers().add(Modifier.FINAL);

        return container;
    }

    private static String getAccessorClassName(NodeData node) {
        return node.isGenerateFactory() ? NodeFactoryFactory.factoryClassName(node) : createNodeTypeName(node);
    }

    static TypeMirror nodeType(NodeData node) {
        return new GeneratedTypeMirror(ElementUtils.getPackageName(node.getTemplateType()), createNodeTypeName(node));
    }

    private static final String NODE_SUFFIX = "NodeGen";

    private static String resolveNodeId(NodeData node) {
        String nodeid = node.getNodeId();
        if (nodeid.endsWith("Node") && !nodeid.equals("Node")) {
            nodeid = nodeid.substring(0, nodeid.length() - 4);
        }
        return nodeid;
    }

    static String createNodeTypeName(NodeData node) {
        return resolveNodeId(node) + NODE_SUFFIX;
    }

    private static List<CodeTypeElement> generateNodes(ProcessorContext context, NodeData node) {
        if (!node.needsFactory()) {
            return Collections.emptyList();
        }

        CodeTypeElement type = GeneratorUtils.createClass(node, null, modifiers(FINAL), createNodeTypeName(node), node.getTemplateType().asType());
        ElementUtils.setVisibility(type.getModifiers(), ElementUtils.getVisibility(node.getTemplateType().getModifiers()));
        if (node.hasErrors()) {
            generateErrorNode(context, node, type);
            return Arrays.asList(type);
        }

        type = new FlatNodeGenFactory(context, node).create(type);

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
                CodeExecutableElement overrideMethod = CodeExecutableElement.clone(context.getEnvironment(), method);
                overrideMethod.getModifiers().remove(Modifier.ABSTRACT);
                List<Message> messages = node.collectMessages();

                String message = messages.toString();
                message = message.replaceAll("\"", "\\\\\"");
                message = message.replaceAll("\n", "\\\\n");
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
        TypeMirror factoryType = context.getType(NodeFactory.class);
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
