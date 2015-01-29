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

import com.oracle.truffle.dsl.processor.java.*;
import com.oracle.truffle.dsl.processor.java.compiler.*;
import com.oracle.truffle.dsl.processor.java.compiler.Compiler;

/**
 * Verifies static constraints on nodes.
 */
public class GraphNodeVerifier {

    private final GraphNodeProcessor env;
    private final Types types;
    private final Elements elements;

    // Checkstyle: stop
    private final TypeElement Input;
    private final TypeElement OptionalInput;
    private final TypeElement Successor;

    final TypeElement Node;
    private final TypeElement NodeInputList;
    private final TypeElement NodeSuccessorList;

    // Checkstyle: resume

    public GraphNodeVerifier(GraphNodeProcessor processor) {
        this.env = processor;

        this.types = processor.getProcessingEnv().getTypeUtils();
        this.elements = processor.getProcessingEnv().getElementUtils();

        this.Input = getTypeElement("com.oracle.graal.graph.Node.Input");
        this.OptionalInput = getTypeElement("com.oracle.graal.graph.Node.OptionalInput");
        this.Successor = getTypeElement("com.oracle.graal.graph.Node.Successor");
        this.Node = getTypeElement("com.oracle.graal.graph.Node");
        this.NodeInputList = getTypeElement("com.oracle.graal.graph.NodeInputList");
        this.NodeSuccessorList = getTypeElement("com.oracle.graal.graph.NodeSuccessorList");
    }

    /**
     * Returns a type element given a canonical name.
     *
     * @throw {@link NoClassDefFoundError} if a type element does not exist for {@code name}
     */
    public TypeElement getTypeElement(String name) {
        TypeElement typeElement = elements.getTypeElement(name);
        if (typeElement == null) {
            throw new NoClassDefFoundError(name);
        }
        return typeElement;
    }

    public TypeElement getTypeElement(Class<?> cls) {
        return getTypeElement(cls.getName());
    }

    public TypeMirror getType(String name) {
        return getTypeElement(name).asType();
    }

    public TypeMirror getType(Class<?> cls) {
        return ElementUtils.getType(getProcessingEnv(), cls);
    }

    public ProcessingEnvironment getProcessingEnv() {
        return env.getProcessingEnv();
    }

    public boolean isAssignableWithErasure(Element from, Element to) {
        TypeMirror fromType = types.erasure(from.asType());
        TypeMirror toType = types.erasure(to.asType());
        return types.isAssignable(fromType, toType);
    }

    private void scanFields(TypeElement node) {
        Compiler compiler = CompilerFactory.getCompiler(node);
        TypeElement currentClazz = node;
        do {
            for (VariableElement field : ElementFilter.fieldsIn(compiler.getEnclosedElementsInDeclarationOrder(currentClazz))) {
                Set<Modifier> modifiers = field.getModifiers();
                if (modifiers.contains(STATIC) || modifiers.contains(TRANSIENT)) {
                    continue;
                }

                List<? extends AnnotationMirror> annotations = field.getAnnotationMirrors();

                boolean isNonOptionalInput = findAnnotationMirror(annotations, Input.asType()) != null;
                boolean isOptionalInput = findAnnotationMirror(annotations, OptionalInput) != null;
                boolean isSuccessor = findAnnotationMirror(annotations, Successor) != null;

                if (isNonOptionalInput || isOptionalInput) {
                    if (findAnnotationMirror(annotations, Successor) != null) {
                        throw new ElementException(field, "Field cannot be both input and successor");
                    } else if (isNonOptionalInput && isOptionalInput) {
                        throw new ElementException(field, "Inputs must be either optional or non-optional");
                    } else if (isAssignableWithErasure(field, NodeInputList)) {
                        if (modifiers.contains(FINAL)) {
                            throw new ElementException(field, "Input list field must not be final");
                        }
                        if (modifiers.contains(PUBLIC) || modifiers.contains(PRIVATE)) {
                            throw new ElementException(field, "Input list field must be protected or package-private");
                        }
                    } else {
                        if (!isAssignableWithErasure(field, Node) && field.getKind() == ElementKind.INTERFACE) {
                            throw new ElementException(field, "Input field type must be an interface or assignable to Node");
                        }
                        if (modifiers.contains(FINAL)) {
                            throw new ElementException(field, "Input field must not be final");
                        }
                        if (modifiers.contains(PUBLIC) || modifiers.contains(PRIVATE)) {
                            throw new ElementException(field, "Input field must be protected or package-private");
                        }
                    }
                } else if (isSuccessor) {
                    if (isAssignableWithErasure(field, NodeSuccessorList)) {
                        if (modifiers.contains(FINAL)) {
                            throw new ElementException(field, "Successor list field must not be final");
                        }
                        if (modifiers.contains(PUBLIC)) {
                            throw new ElementException(field, "Successor list field must not be public");
                        }
                    } else {
                        if (!isAssignableWithErasure(field, Node)) {
                            throw new ElementException(field, "Successor field must be a Node type");
                        }
                        if (modifiers.contains(FINAL)) {
                            throw new ElementException(field, "Successor field must not be final");
                        }
                        if (modifiers.contains(PUBLIC) || modifiers.contains(PRIVATE)) {
                            throw new ElementException(field, "Successor field must be protected or package-private");
                        }
                    }

                } else {
                    if (isAssignableWithErasure(field, Node) && !field.getSimpleName().contentEquals("Null")) {
                        throw new ElementException(field, "Node field must be annotated with @" + Input.getSimpleName() + ", @" + OptionalInput.getSimpleName() + " or @" + Successor.getSimpleName());
                    }
                    if (isAssignableWithErasure(field, NodeInputList)) {
                        throw new ElementException(field, "NodeInputList field must be annotated with @" + Input.getSimpleName() + " or @" + OptionalInput.getSimpleName());
                    }
                    if (isAssignableWithErasure(field, NodeSuccessorList)) {
                        throw new ElementException(field, "NodeSuccessorList field must be annotated with @" + Successor.getSimpleName());
                    }
                    if (modifiers.contains(PUBLIC)) {
                        if (!modifiers.contains(FINAL)) {
                            throw new ElementException(field, "Data field must be final if public otherwise it must be protected");
                        }
                    } else if (!modifiers.contains(PROTECTED)) {
                        throw new ElementException(field, "Data field must be protected");
                    }
                }
            }
            currentClazz = getSuperType(currentClazz);
        } while (!isObject(getSuperType(currentClazz).asType()));
    }

    void verify(TypeElement node) {
        scanFields(node);

        boolean foundValidConstructor = false;
        for (ExecutableElement constructor : ElementFilter.constructorsIn(node.getEnclosedElements())) {
            if (constructor.getModifiers().contains(PRIVATE)) {
                continue;
            } else if (!constructor.getModifiers().contains(PUBLIC) && !constructor.getModifiers().contains(PROTECTED)) {
                throw new ElementException(constructor, "Node class constructor must be public or protected");
            }

            foundValidConstructor = true;
        }

        if (!foundValidConstructor) {
            throw new ElementException(node, "Node class must have at least one protected constructor");
        }
    }
}
