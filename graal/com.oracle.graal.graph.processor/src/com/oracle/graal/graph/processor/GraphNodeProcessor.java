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
package com.oracle.graal.graph.processor;

import java.util.*;

import javax.annotation.processing.*;
import javax.lang.model.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.graal.graph.*;
import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.java.*;
import com.oracle.truffle.dsl.processor.java.model.*;
import com.oracle.truffle.dsl.processor.java.transform.*;

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes({"com.oracle.graal.graph.NodeInfo"})
public class GraphNodeProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }

        GraphNodeParser parser = new GraphNodeParser(processingEnv);
        GraphNodeGenerator gen = new GraphNodeGenerator(processingEnv);

        for (Element annotatedElement : roundEnv.getElementsAnnotatedWith(parser.getAnnotationType())) {
            if (annotatedElement.getKind() != ElementKind.CLASS) {
                // TODO fail?
                continue;
            }

            NodeInfo nodeInfo = annotatedElement.getAnnotation(NodeInfo.class);
            if (nodeInfo == null) {
                // TODO assert nodeInfo != null?
                continue;
            }

            TypeElement typeElement = (TypeElement) annotatedElement;
            GraphNode graphNode = parser.parse(typeElement, nodeInfo);
            if (graphNode == null) {
                // TODO fail?`
                continue;
            }

            CodeCompilationUnit unit = gen.process(graphNode);
            unit.setGeneratorElement(typeElement);

            DeclaredType overrideType = (DeclaredType) ElementUtils.getType(processingEnv, Override.class);
            DeclaredType unusedType = (DeclaredType) ElementUtils.getType(processingEnv, SuppressWarnings.class);
            unit.accept(new GenerateOverrideVisitor(overrideType), null);
            unit.accept(new FixWarningsVisitor(processingEnv, unusedType, overrideType), null);
            unit.accept(new CodeWriter(processingEnv, typeElement), null);
        }
        return false;
    }

}
