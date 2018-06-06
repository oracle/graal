/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

import java.util.Arrays;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationValue;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeNames;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.model.NodeExecutionData;

class NodeFactoryFactory {

    private final ProcessorContext context;
    private final NodeData node;
    private final CodeTypeElement createdFactoryElement;

    NodeFactoryFactory(ProcessorContext context, NodeData node, CodeTypeElement createdClass) {
        this.context = context;
        this.node = node;
        this.createdFactoryElement = createdClass;
    }

    public static String factoryClassName(NodeData node) {
        return node.getNodeId() + "Factory";
    }

    public CodeTypeElement create() {
        Modifier visibility = ElementUtils.getVisibility(node.getTemplateType().getModifiers());
        TypeMirror nodeFactory = ElementUtils.getDeclaredType(ElementUtils.fromTypeMirror(context.getType(NodeFactory.class)), node.getNodeType());

        CodeTypeElement clazz = GeneratorUtils.createClass(node, null, modifiers(), factoryClassName(node), null);
        if (visibility != null) {
            clazz.getModifiers().add(visibility);
        }
        clazz.getModifiers().add(Modifier.FINAL);

        if (createdFactoryElement != null) {
            clazz.getImplements().add(nodeFactory);

            CodeAnnotationMirror supressWarnings = new CodeAnnotationMirror(context.getDeclaredType(SuppressWarnings.class));
            supressWarnings.setElementValue(supressWarnings.findExecutableElement("value"),
                            new CodeAnnotationValue(Arrays.asList(new CodeAnnotationValue("unchecked"), new CodeAnnotationValue("rawtypes"))));
            clazz.getAnnotationMirrors().add(supressWarnings);

            clazz.add(createNodeFactoryConstructor());
            clazz.add(createCreateGetNodeClass());
            clazz.add(createCreateGetExecutionSignature());
            clazz.add(createCreateGetNodeSignatures());
            clazz.add(createCreateNodeMethod());
            clazz.add(createGetInstanceMethod(visibility));
            clazz.add(createInstanceConstant(clazz.asType()));
            createFactoryMethods(clazz);
        }

        return clazz;
    }

    private Element createNodeFactoryConstructor() {
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PRIVATE), null, factoryClassName(node));
        return method;
    }

    private CodeExecutableElement createCreateGetNodeClass() {
        TypeMirror returnValue = ElementUtils.getDeclaredType(ElementUtils.fromTypeMirror(context.getType(Class.class)), node.getNodeType());
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), returnValue, "getNodeClass");
        method.createBuilder().startReturn().typeLiteral(node.getNodeType()).end();
        return method;
    }

    private CodeExecutableElement createCreateGetNodeSignatures() {
        TypeMirror returnValue = ElementUtils.getDeclaredType(ElementUtils.fromTypeMirror(context.getType(List.class)));
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), returnValue, "getNodeSignatures");
        CodeTreeBuilder builder = method.createBuilder();
        builder.startReturn();

        builder.startGroup();
        builder.startStaticCall(context.getType(Arrays.class), "asList");
        List<ExecutableElement> constructors = GeneratorUtils.findUserConstructors(createdFactoryElement.asType());
        for (ExecutableElement constructor : constructors) {
            builder.startGroup();
            builder.startStaticCall(context.getType(Arrays.class), "asList");
            for (VariableElement var : constructor.getParameters()) {
                builder.typeLiteral(var.asType());
            }
            builder.end();
            builder.end();
        }
        builder.end();
        builder.end();

        builder.end();
        return method;
    }

    private CodeExecutableElement createCreateGetExecutionSignature() {
        TypeMirror returnValue = ElementUtils.getDeclaredType(ElementUtils.fromTypeMirror(context.getType(List.class)));
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), returnValue, "getExecutionSignature");
        CodeTreeBuilder builder = method.createBuilder();
        builder.startReturn();

        builder.startStaticCall(context.getType(Arrays.class), "asList");
        for (NodeExecutionData execution : node.getChildExecutions()) {
            TypeMirror nodeType = execution.getNodeType();
            if (nodeType != null) {
                builder.typeLiteral(nodeType);
            } else {
                builder.typeLiteral(context.getType(Node.class));
            }
        }
        builder.end();

        builder.end();
        return method;
    }

    private CodeExecutableElement createCreateNodeMethod() {
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), node.getNodeType(), "createNode");
        CodeVariableElement arguments = new CodeVariableElement(context.getType(Object.class), "arguments");
        method.setVarArgs(true);
        method.addParameter(arguments);

        CodeTreeBuilder builder = method.createBuilder();
        List<ExecutableElement> signatures = GeneratorUtils.findUserConstructors(createdFactoryElement.asType());
        boolean ifStarted = false;

        for (ExecutableElement element : signatures) {
            ifStarted = builder.startIf(ifStarted);
            builder.string("arguments.length == " + element.getParameters().size());

            int index = 0;
            for (VariableElement param : element.getParameters()) {
                if (ElementUtils.isObject(param.asType())) {
                    index++;
                    continue;
                }
                builder.string(" && ");
                if (!param.asType().getKind().isPrimitive()) {
                    builder.string("(arguments[" + index + "] == null || ");
                }
                builder.string("arguments[" + index + "] instanceof ");
                builder.type(ElementUtils.eraseGenericTypes(ElementUtils.boxType(context, param.asType())));
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
        builder.startThrow().startNew(context.getType(IllegalArgumentException.class));
        builder.doubleQuote("Invalid create signature.");
        builder.end().end();
        builder.end(); // else block
        return method;
    }

    private ExecutableElement createGetInstanceMethod(Modifier visibility) {
        TypeElement nodeFactoryType = ElementUtils.fromTypeMirror(context.getType(NodeFactory.class));
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

    private CodeVariableElement createInstanceConstant(TypeMirror factoryType) {
        String varName = instanceVarName(node);
        CodeVariableElement var = new CodeVariableElement(modifiers(), factoryType, varName);
        var.getModifiers().add(Modifier.PRIVATE);
        var.getModifiers().add(Modifier.STATIC);
        return var;
    }

    public void createFactoryMethods(CodeTypeElement clazz) {
        List<ExecutableElement> constructors = GeneratorUtils.findUserConstructors(createdFactoryElement.asType());
        for (ExecutableElement constructor : constructors) {
            clazz.add(createCreateMethod(constructor));
            if (constructor instanceof CodeExecutableElement) {
                ElementUtils.setVisibility(constructor.getModifiers(), Modifier.PRIVATE);
            }
        }
    }

    private CodeExecutableElement createCreateMethod(ExecutableElement constructor) {
        CodeExecutableElement method = CodeExecutableElement.clone(context.getEnvironment(), constructor);
        method.setSimpleName(CodeNames.of("create"));
        method.getModifiers().clear();
        method.getModifiers().add(Modifier.PUBLIC);
        method.getModifiers().add(Modifier.STATIC);
        method.setReturnType(node.getNodeType());

        CodeTreeBuilder body = method.createBuilder();
        body.startReturn();
        if (node.getSpecializations().isEmpty()) {
            body.nullLiteral();
        } else {
            body.startNew(NodeCodeGenerator.nodeType(node));
            for (VariableElement var : method.getParameters()) {
                body.string(var.getSimpleName().toString());
            }
            body.end();

        }
        body.end();
        return method;
    }
}
