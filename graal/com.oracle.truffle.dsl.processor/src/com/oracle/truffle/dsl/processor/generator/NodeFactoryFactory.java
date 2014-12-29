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

import static com.oracle.truffle.dsl.processor.java.ElementUtils.*;
import static javax.lang.model.element.Modifier.*;

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.dsl.processor.java.*;
import com.oracle.truffle.dsl.processor.java.model.*;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.ArrayCodeTypeMirror;
import com.oracle.truffle.dsl.processor.model.*;

class NodeFactoryFactory extends AbstractClassElementFactory<NodeData> {

    static final String FACTORY_METHOD_NAME = "create0";

    private final Map<NodeData, List<TypeElement>> childTypes;
    private CodeTypeElement generatedNode;

    NodeFactoryFactory(Map<NodeData, List<TypeElement>> childElements) {
        this.childTypes = childElements;
    }

    private static String factoryClassName(NodeData node) {
        return node.getNodeId() + "Factory";
    }

    @Override
    protected CodeTypeElement create(NodeData node) {
        Modifier visibility = ElementUtils.getVisibility(node.getTemplateType().getModifiers());

        CodeTypeElement clazz = createClass(node, modifiers(), factoryClassName(node), null, false);
        if (visibility != null) {
            clazz.getModifiers().add(visibility);
        }
        clazz.getModifiers().add(Modifier.FINAL);
        return clazz;
    }

    @Override
    protected void createChildren(NodeData node) {
        CodeTypeElement clazz = getElement();

        Modifier createVisibility = ElementUtils.getVisibility(clazz.getModifiers());

        if (node.needsFactory()) {
            NodeBaseFactory factory = new NodeBaseFactory();
            add(factory, node.getGenericSpecialization() == null ? node.getSpecializations().get(0) : node.getGenericSpecialization());
            generatedNode = factory.getElement();

            createFactoryMethods(node, clazz, createVisibility);

            for (SpecializationData specialization : node.getSpecializations()) {
                if (!specialization.isReachable() || specialization.isGeneric()) {
                    continue;
                }

                if (specialization.isPolymorphic() && node.isPolymorphic(context)) {
                    PolymorphicNodeFactory polymorphicFactory = new PolymorphicNodeFactory(generatedNode);
                    add(polymorphicFactory, specialization);
                    continue;
                }

                add(new SpecializedNodeFactory(generatedNode), specialization);
            }

            TypeMirror nodeFactory = ElementUtils.getDeclaredType(ElementUtils.fromTypeMirror(getContext().getTruffleTypes().getNodeFactoryBase()), node.getNodeType());
            clazz.setSuperClass(nodeFactory);
            clazz.add(createNodeFactoryConstructor(node));
            clazz.add(createCreateNodeMethod(node));
            clazz.add(createGetInstanceMethod(node, createVisibility));
            clazz.add(createInstanceConstant(node, clazz.asType()));
        }

        for (NodeData childNode : childTypes.keySet()) {
            if (childNode.getTemplateType().getModifiers().contains(Modifier.PRIVATE)) {
                continue;
            }

            for (TypeElement type : childTypes.get(childNode)) {
                Set<Modifier> typeModifiers = ((CodeTypeElement) type).getModifiers();
                Modifier visibility = ElementUtils.getVisibility(type.getModifiers());
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

    private Element createNodeFactoryConstructor(NodeData node) {
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PRIVATE), null, factoryClassName(node));
        CodeTreeBuilder builder = method.createBuilder();
        builder.startStatement();
        builder.startCall("super");

        // node type
        builder.typeLiteral(node.getNodeType());

        // execution signature
        builder.startGroup();
        if (node.getChildExecutions().isEmpty()) {
            builder.staticReference(context.getTruffleTypes().getDslMetadata(), NodeBaseFactory.EMPTY_CLASS_ARRAY);
        } else {
            builder.startNewArray(new ArrayCodeTypeMirror(context.getType(Class.class)), null);
            for (NodeExecutionData execution : node.getChildExecutions()) {
                builder.typeLiteral(execution.getNodeType());
            }
            builder.end();
        }
        builder.end();

        // node signatures
        builder.startGroup();
        builder.startNewArray(new ArrayCodeTypeMirror(new ArrayCodeTypeMirror(context.getType(Class.class))), null);
        List<ExecutableElement> constructors = NodeBaseFactory.findUserConstructors(generatedNode.asType());
        for (ExecutableElement constructor : constructors) {
            builder.startGroup();
            if (constructor.getParameters().isEmpty()) {
                builder.staticReference(context.getTruffleTypes().getDslMetadata(), NodeBaseFactory.EMPTY_CLASS_ARRAY);
            } else {
                builder.startNewArray(new ArrayCodeTypeMirror(context.getType(Class.class)), null);
                for (VariableElement var : constructor.getParameters()) {
                    builder.typeLiteral(var.asType());
                }
                builder.end();
            }
            builder.end();
        }
        builder.end();
        builder.end();

        builder.end().end().end();
        return method;
    }

    private CodeExecutableElement createCreateNodeMethod(NodeData node) {
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), node.getNodeType(), "createNode");
        CodeVariableElement arguments = new CodeVariableElement(getContext().getType(Object.class), "arguments");
        method.setVarArgs(true);
        method.addParameter(arguments);

        CodeTreeBuilder builder = method.createBuilder();
        List<ExecutableElement> signatures = NodeBaseFactory.findUserConstructors(generatedNode.asType());
        boolean ifStarted = false;

        for (ExecutableElement element : signatures) {
            ifStarted = builder.startIf(ifStarted);
            builder.string("arguments.length == " + element.getParameters().size());

            int index = 0;
            for (VariableElement param : element.getParameters()) {
                if (ElementUtils.isObject(param.asType())) {
                    continue;
                }
                builder.string(" && ");
                if (!param.asType().getKind().isPrimitive()) {
                    builder.string("(arguments[" + index + "] == null || ");
                }
                builder.string("arguments[" + index + "] instanceof ");
                builder.type(ElementUtils.boxType(getContext(), param.asType()));
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
                if (!ElementUtils.isObject(param.asType())) {
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
        TypeElement nodeFactoryType = ElementUtils.fromTypeMirror(getContext().getType(NodeFactory.class));
        TypeMirror returnType = ElementUtils.getDeclaredType(nodeFactoryType, node.getNodeType());

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

    private static String instanceVarName(NodeData node) {
        if (node.getDeclaringNode() != null) {
            return ElementUtils.firstLetterLowerCase(factoryClassName(node)) + "Instance";
        } else {
            return "instance";
        }
    }

    private static CodeVariableElement createInstanceConstant(NodeData node, TypeMirror factoryType) {
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
            if (prev != null && !ElementUtils.typeEquals(child.getNodeType(), prev)) {
                allSame = false;
            }
            prev = child.getNodeType();
        }
        TypeMirror commonNodeSuperType = ElementUtils.getCommonSuperType(getContext(), nodeTypesList.toArray(new TypeMirror[nodeTypesList.size()]));

        Types types = getContext().getEnvironment().getTypeUtils();
        TypeMirror factoryType = getContext().getType(NodeFactory.class);
        TypeMirror baseType;
        if (allSame) {
            baseType = ElementUtils.getDeclaredType(ElementUtils.fromTypeMirror(factoryType), commonNodeSuperType);
        } else {
            baseType = ElementUtils.getDeclaredType(ElementUtils.fromTypeMirror(factoryType), types.getWildcardType(commonNodeSuperType, null));
        }
        TypeMirror listType = ElementUtils.getDeclaredType(ElementUtils.fromTypeMirror(getContext().getType(List.class)), baseType);

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
        List<ExecutableElement> constructors = NodeBaseFactory.findUserConstructors(generatedNode.asType());
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
            body.startCall(NodeBaseFactory.nodeSpecializationClassName(node.getSpecializations().get(0)), FACTORY_METHOD_NAME);
            for (VariableElement var : method.getParameters()) {
                body.string(var.getSimpleName().toString());
            }
            body.end();
        }
        body.end();
        return method;
    }

}