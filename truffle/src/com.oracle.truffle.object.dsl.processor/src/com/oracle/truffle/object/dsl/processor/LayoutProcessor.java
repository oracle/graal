/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object.dsl.processor;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

import com.oracle.truffle.api.object.dsl.Layout;
import com.oracle.truffle.object.dsl.processor.model.LayoutModel;

@SupportedAnnotationTypes("com.oracle.truffle.api.object.dsl.Layout")
public class LayoutProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        for (Element element : roundEnvironment.getElementsAnnotatedWith(Layout.class)) {
            processLayout((TypeElement) element);
        }

        return true;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    public ProcessingEnvironment getProcessingEnv() {
        return processingEnv;
    }

    private void processLayout(TypeElement layoutElement) {
        try {
            final LayoutParser parser = new LayoutParser(this);
            parser.parse(layoutElement);

            final LayoutModel layout = parser.build();

            final LayoutGenerator generator = new LayoutGenerator(layout);

            final JavaFileObject output = processingEnv.getFiler().createSourceFile(generator.getGeneratedClassName(), layoutElement);

            try (PrintStream stream = new PrintStream(output.openOutputStream(), false, "UTF8")) {
                generator.generate(stream);
            }
        } catch (IOException e) {
            reportError(layoutElement, "IO error %s while writing code generated from @Layout", e.getMessage());
        }
    }

    public void reportError(Element element, String messageFormat, Object... formatArgs) {
        final String message = String.format(messageFormat, formatArgs);
        processingEnv.getMessager().printMessage(Kind.ERROR, message, element);
    }

}
