/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.object.dsl.processor.model.LayoutModel;

@SupportedAnnotationTypes(TruffleTypes.Layout_Name)
public class LayoutProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        ProcessorContext context = ProcessorContext.enter(processingEnv);
        try {
            TruffleTypes types = context.getTypes();
            for (Element element : roundEnvironment.getElementsAnnotatedWith(ElementUtils.castTypeElement(types.Layout))) {
                processLayout((TypeElement) element);
            }
        } finally {
            ProcessorContext.leave();
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

            final LayoutGenerator generator = new LayoutGenerator(layout, processingEnv);

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
