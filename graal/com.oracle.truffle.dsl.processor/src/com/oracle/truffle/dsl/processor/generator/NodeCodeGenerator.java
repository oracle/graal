/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.dsl.processor.java.ElementUtils.*;
import static javax.lang.model.element.Modifier.*;

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.java.*;
import com.oracle.truffle.dsl.processor.java.model.*;
import com.oracle.truffle.dsl.processor.model.*;

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
        return node.isGenerateFactory() ? NodeFactoryFactory.factoryClassName(node) : NodeGenFactory.nodeTypeName(node);
    }

    private static List<CodeTypeElement> generateNodes(ProcessorContext context, NodeData node) {
        if (!node.needsFactory()) {
            return Collections.emptyList();
        }
        return Arrays.asList(new NodeGenFactory(context, node).create());
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
        builder.startStaticCall(context.getType(Arrays.class), "asList");

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
