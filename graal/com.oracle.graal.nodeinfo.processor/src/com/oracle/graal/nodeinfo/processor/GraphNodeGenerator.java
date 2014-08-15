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
package com.oracle.graal.nodeinfo.processor;

import static com.oracle.truffle.dsl.processor.java.ElementUtils.*;
import static javax.lang.model.element.Modifier.*;

import java.util.*;

import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;

import com.oracle.graal.nodeinfo.*;
import com.oracle.truffle.dsl.processor.java.*;
import com.oracle.truffle.dsl.processor.java.model.*;

/**
 * Generates the source code for a Node class.
 */
public class GraphNodeGenerator {

    final GraphNodeProcessor env;
    final TypeElement Input;
    final TypeElement OptionalInput;
    final TypeElement Successor;

    final TypeElement Node;
    final TypeElement NodeInputList;
    final TypeElement NodeSuccessorList;

    public GraphNodeGenerator(GraphNodeProcessor processor) {
        this.env = processor;
        this.Input = getType("com.oracle.graal.graph.Node.Input");
        this.OptionalInput = getType("com.oracle.graal.graph.Node.OptionalInput");
        this.Successor = getType("com.oracle.graal.graph.Node.Successor");
        this.Node = getType("com.oracle.graal.graph.Node");
        this.NodeInputList = getType("com.oracle.graal.graph.NodeInputList");
        this.NodeSuccessorList = getType("com.oracle.graal.graph.NodeSuccessorList");
    }

    public TypeElement getType(String name) {
        TypeElement typeElement = env.getProcessingEnv().getElementUtils().getTypeElement(name);
        if (typeElement == null) {
            throw new NoClassDefFoundError(name);
        }
        return typeElement;
    }

    public ProcessingEnvironment getProcessingEnv() {
        return env.getProcessingEnv();
    }

    private static String getGeneratedClassName(TypeElement node) {

        TypeElement typeElement = node;

        String newClassName = typeElement.getSimpleName().toString() + "Gen";
        Element enclosing = typeElement.getEnclosingElement();
        while (enclosing != null) {
            if (enclosing.getKind() == ElementKind.CLASS || enclosing.getKind() == ElementKind.INTERFACE) {
                if (enclosing.getModifiers().contains(Modifier.PRIVATE)) {
                    throw new ElementException(enclosing, "%s %s cannot be private", enclosing.getKind().name().toLowerCase(), enclosing);
                }
                newClassName = enclosing.getSimpleName() + "_" + newClassName;
            } else {
                assert enclosing.getKind() == ElementKind.PACKAGE;
            }
            enclosing = enclosing.getEnclosingElement();
        }
        return newClassName;
    }

    public class FieldScanner {
        /**
         * @param field
         * @param isOptional
         * @param isList
         * @return true if field scanning should continue
         */
        public boolean scanInputField(VariableElement field, boolean isOptional, boolean isList) {
            return true;
        }

        /**
         * @param field
         * @param isList
         * @return true if field scanning should continue
         */
        public boolean scanSuccessorField(VariableElement field, boolean isList) {
            return true;
        }

        /**
         * @param field
         * @return true if field scanning should continue
         */
        public boolean scanDataField(VariableElement field) {
            return true;
        }
    }

    public boolean isAssignable(Element from, Element to) {
        Types types = env.getProcessingEnv().getTypeUtils();
        TypeMirror fromType = types.erasure(from.asType());
        TypeMirror toType = types.erasure(to.asType());
        boolean res = types.isAssignable(fromType, toType);
        // System.out.printf("%s:%s is %sassignable to %s:%s%n", from, fromType, res ? "" : "NOT ",
// to, toType);
        return res;
    }

    public void scanFields(TypeElement node, FieldScanner scanner) {
        TypeElement currentClazz = node;
        do {
            for (VariableElement field : ElementFilter.fieldsIn(currentClazz.getEnclosedElements())) {
                Set<Modifier> modifiers = field.getModifiers();
                if (modifiers.contains(STATIC) || modifiers.contains(TRANSIENT)) {
                    continue;
                }

                List<? extends AnnotationMirror> annotations = field.getAnnotationMirrors();

                boolean isNonOptionalInput = findAnnotationMirror(annotations, Input) != null;
                boolean isOptionalInput = findAnnotationMirror(annotations, OptionalInput) != null;
                boolean isSuccessor = findAnnotationMirror(annotations, Successor) != null;

                if (isNonOptionalInput || isOptionalInput) {
                    if (findAnnotationMirror(annotations, Successor) != null) {
                        throw new ElementException(field, "Field cannot be both input and successor");
                    } else if (isNonOptionalInput && isOptionalInput) {
                        throw new ElementException(field, "Inputs must be either optional or non-optional");
                    } else if (isAssignable(field, NodeInputList)) {
                        if (!modifiers.contains(FINAL)) {
                            throw new ElementException(field, "Input list field must be final");
                        }
                        if (modifiers.contains(PUBLIC)) {
                            throw new ElementException(field, "Input list field must not be public");
                        }
                        if (!scanner.scanInputField(field, isOptionalInput, true)) {
                            return;
                        }
                    } else {
                        if (!isAssignable(field, Node) && field.getKind() == ElementKind.INTERFACE) {
                            throw new ElementException(field, "Input field type must be an interface or assignable to Node");
                        }
                        if (modifiers.contains(FINAL)) {
                            throw new ElementException(field, "Input field must not be final");
                        }
// if (modifiers.contains(PRIVATE) || modifiers.contains(PUBLIC) || modifiers.contains(PROTECTED)) {
// throw new ElementException(field, "Input field must be package-private");
// }
                        if (!modifiers.contains(PRIVATE)) {
                            throw new ElementException(field, "Input field must be private");
                        }
                        if (!scanner.scanInputField(field, isOptionalInput, false)) {
                            return;
                        }
                    }
                } else if (isSuccessor) {
                    if (isAssignable(field, NodeSuccessorList)) {
                        if (!modifiers.contains(FINAL)) {
                            throw new ElementException(field, "Successor list field must be final");
                        }
                        if (modifiers.contains(PUBLIC)) {
                            throw new ElementException(field, "Successor list field must not be public");
                        }
                        if (!scanner.scanSuccessorField(field, true)) {
                            return;
                        }
                    } else {
                        if (!isAssignable(field, Node)) {
                            throw new ElementException(field, "Successor field must be a Node type");
                        }
                        if (modifiers.contains(FINAL)) {
                            throw new ElementException(field, "Successor field must not be final");
                        }
// if (modifiers.contains(PRIVATE) || modifiers.contains(PUBLIC) || modifiers.contains(PROTECTED)) {
// throw new ElementException(field, "Successor field must be package-private");
// }
                        if (!modifiers.contains(PRIVATE)) {
                            throw new ElementException(field, "Successor field must be private");
                        }
                        if (!scanner.scanSuccessorField(field, false)) {
                            return;
                        }
                    }

                } else {
                    if (isAssignable(field, Node) && !field.getSimpleName().contentEquals("Null")) {
                        throw new ElementException(field, "Suspicious Node field: " + field);
                    }
                    if (isAssignable(field, NodeInputList)) {
                        throw new ElementException(field, "Suspicious NodeInputList field");
                    }
                    if (isAssignable(field, NodeSuccessorList)) {
                        throw new ElementException(field, "Suspicious NodeSuccessorList field");
                    }
                    if (!scanner.scanDataField(field)) {
                        return;
                    }
                }
            }
            currentClazz = getSuperType(currentClazz);
        } while (!isObject(getSuperType(currentClazz).asType()));
    }

    public CodeCompilationUnit process(TypeElement node) {

        CodeCompilationUnit compilationUnit = new CodeCompilationUnit();

        PackageElement packageElement = ElementUtils.findPackageElement(node);

        String newClassName = getGeneratedClassName(node);

        CodeTypeElement nodeGenElement = new CodeTypeElement(modifiers(), ElementKind.CLASS, packageElement, newClassName);

        nodeGenElement.setSuperClass(node.asType());

        for (ExecutableElement constructor : ElementFilter.constructorsIn(node.getEnclosedElements())) {
            if (constructor.getModifiers().contains(Modifier.PRIVATE)) {
                // ignore private constructors
                continue;
            }
            nodeGenElement.add(createSuperConstructor(nodeGenElement, constructor));
        }

        DeclaredType generatedNode = (DeclaredType) ElementUtils.getType(getProcessingEnv(), GeneratedNode.class);
        CodeAnnotationMirror generatedByMirror = new CodeAnnotationMirror(generatedNode);
        generatedByMirror.setElementValue(generatedByMirror.findExecutableElement("value"), new CodeAnnotationValue(node.asType()));
        nodeGenElement.getAnnotationMirrors().add(generatedByMirror);

        nodeGenElement.add(createIsLeafNodeMethod(node));

        compilationUnit.add(nodeGenElement);
        return compilationUnit;
    }

    private CodeExecutableElement createSuperConstructor(TypeElement type, ExecutableElement element) {
        CodeExecutableElement executable = CodeExecutableElement.clone(getProcessingEnv(), element);

        // to create a constructor we have to set the return type to null.(TODO needs fix)
        executable.setReturnType(null);
        // we have to set the name manually otherwise <init> is inferred (TODO needs fix)
        executable.setSimpleName(CodeNames.of(type.getSimpleName().toString()));

        CodeTreeBuilder b = executable.createBuilder();
        b.startStatement().startSuperCall();
        for (VariableElement v : element.getParameters()) {
            b.string(v.getSimpleName().toString());
        }
        b.end().end();

        return executable;
    }

    public ExecutableElement createIsLeafNodeMethod(TypeElement node) {
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), ElementUtils.getType(getProcessingEnv(), boolean.class), "isLeafNode");
        boolean[] isLeafNode = {true};
        scanFields(node, new FieldScanner() {

            @Override
            public boolean scanInputField(VariableElement field, boolean isOptional, boolean isList) {
                isLeafNode[0] = false;
                return false;
            }

            @Override
            public boolean scanSuccessorField(VariableElement field, boolean isList) {
                isLeafNode[0] = false;
                return false;
            }
        });

        CodeTreeBuilder builder = method.createBuilder();
        builder.startReturn().string(String.valueOf(isLeafNode[0])).end();

        return method;
    }
}
