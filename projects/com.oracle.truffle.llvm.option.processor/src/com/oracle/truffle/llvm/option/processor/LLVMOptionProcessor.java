/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.option.processor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic.Kind;

import com.oracle.truffle.llvm.option.Option;
import com.oracle.truffle.llvm.option.OptionCategory;

public final class LLVMOptionProcessor extends AbstractProcessor {

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotations = new HashSet<>();
        annotations.add("com.oracle.truffle.llvm.option.OptionCategory");
        return annotations;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }
        process0(roundEnv);
        return true;
    }

    private void process0(RoundEnvironment roundEnv) {
        for (Element e : roundEnv.getElementsAnnotatedWith(OptionCategory.class)) {
            try {
                processElement(e);
            } catch (Throwable ex) {
                ex.printStackTrace();
                String message = "Uncaught error in " + this.getClass();
                processingEnv.getMessager().printMessage(Kind.ERROR, message + ": " + Utils.printException(ex), e);
            }
        }
    }

    private void processElement(Element e) throws IOException {
        if (e.getKind() != ElementKind.CLASS) {
            return;
        }
        OptionCategory optionsImplementation = e.getAnnotation(OptionCategory.class);
        if (optionsImplementation == null) {
            return;
        }

        if (!e.getModifiers().contains(Modifier.ABSTRACT)) {
            emitError("Class must be abstract", e);
            return;
        }

        if (e.getModifiers().contains(Modifier.PUBLIC)) {
            emitError("Class must not be public", e);
            return;
        }

        List<VariableElement> options = new ArrayList<>();
        for (Element option : e.getEnclosedElements()) {
            if (option.getKind() != ElementKind.FIELD) {
                continue;
            }

            if (option.getAnnotation(Option.class) != null) {
                if (!option.getModifiers().contains(Modifier.PROTECTED)) {
                    emitError("Field must be protected", option);
                    return;
                }
                if (!option.getModifiers().contains(Modifier.STATIC)) {
                    emitError("Field must be static", option);
                    return;
                }
                if (!option.getModifiers().contains(Modifier.FINAL)) {
                    emitError("Field must be final", option);
                    return;
                }
                VariableElement optionTypeElement = (VariableElement) option;
                if (!(Utils.isBoolean(processingEnv, optionTypeElement.asType()) || Utils.isInt(processingEnv, optionTypeElement.asType()) ||
                                Utils.isString(processingEnv, optionTypeElement.asType()) || Utils.isStringArr(optionTypeElement.asType()))) {
                    emitError("Field must be java.lang.Integer/java.lang.Boolean/java.lang.String/java.lang.String[]", option);
                    return;
                }
                options.add(optionTypeElement);
            }
        }

        try {
            OptionsClassGenerator optionsClassGenerator = new OptionsClassGenerator(processingEnv, (TypeElement) e, options);
            optionsClassGenerator.generate();
        } catch (FilerException ex) {
            emitError("Foreign factory class with same name already exists", e);
            return;
        }
    }

    private void emitError(String msg, Element e) {
        processingEnv.getMessager().printMessage(Kind.ERROR, msg, e);
    }
}
