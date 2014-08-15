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

    private final GraphNodeProcessor processor;

    public GraphNodeGenerator(GraphNodeProcessor processor) {
        this.processor = processor;
    }

    public ProcessingEnvironment getProcessingEnv() {
        return processor.getProcessingEnv();
    }

    private String getGeneratedClassName(TypeElement node) {

        TypeElement typeElement = node;

        String newClassName = typeElement.getSimpleName().toString() + "Gen";
        Element enclosing = typeElement.getEnclosingElement();
        while (enclosing != null) {
            if (enclosing.getKind() == ElementKind.CLASS || enclosing.getKind() == ElementKind.INTERFACE) {
                if (enclosing.getModifiers().contains(Modifier.PRIVATE)) {
                    processor.errorMessage(enclosing, "%s %s cannot be private", enclosing.getKind().name().toLowerCase(), enclosing);
                    return null;
                }
                newClassName = enclosing.getSimpleName() + "$" + newClassName;
            } else {
                assert enclosing.getKind() == ElementKind.PACKAGE;
            }
            enclosing = enclosing.getEnclosingElement();
        }
        return newClassName;
    }

    public CodeCompilationUnit process(TypeElement node) {
        CodeCompilationUnit compilationUnit = new CodeCompilationUnit();

        PackageElement packageElement = ElementUtils.findPackageElement(node);

        String newClassName = getGeneratedClassName(node);

        CodeTypeElement nodeGenElement = new CodeTypeElement(modifiers(), ElementKind.CLASS, packageElement, newClassName);

        if (node.getModifiers().contains(Modifier.ABSTRACT)) {
            // we do not support implementation of abstract methods yet.
            nodeGenElement.getModifiers().add(Modifier.ABSTRACT);
        }

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

        nodeGenElement.add(createDummyExampleMethod());

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

    public ExecutableElement createDummyExampleMethod() {
        CodeExecutableElement method = new CodeExecutableElement(modifiers(Modifier.PROTECTED), ElementUtils.getType(getProcessingEnv(), int.class), "computeTheMeaningOfLife");

        CodeTreeBuilder builder = method.createBuilder();
        builder.string("// this method got partially evaluated").newLine();
        builder.startReturn().string("42").end();

        return method;
    }

}
