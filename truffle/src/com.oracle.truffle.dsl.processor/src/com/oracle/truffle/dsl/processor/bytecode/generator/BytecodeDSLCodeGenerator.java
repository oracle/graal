/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.bytecode.generator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;

import com.oracle.truffle.dsl.processor.AnnotationProcessor;
import com.oracle.truffle.dsl.processor.ExpectError;
import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.bytecode.model.BytecodeDSLModel;
import com.oracle.truffle.dsl.processor.bytecode.model.BytecodeDSLModels;
import com.oracle.truffle.dsl.processor.generator.CodeTypeElementFactory;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;

public class BytecodeDSLCodeGenerator extends CodeTypeElementFactory<BytecodeDSLModels> {

    @Override
    public List<CodeTypeElement> create(ProcessorContext context, AnnotationProcessor<?> processor, BytecodeDSLModels modelList) {
        List<CodeTypeElement> results = new ArrayList<>();

        // For testing: when using {@code @ExpectError}, we don't want to actually generate the
        // code, since compilation will likely fail.
        if (hasExpectErrors(modelList.getTemplateType())) {
            return results;
        }

        for (BytecodeDSLModel model : modelList.getModels()) {
            if (modelList.hasErrors()) {
                results.add(new BytecodeDSLNodeFactory.ErrorFactory(model).create());
            } else {
                results.add(new BytecodeDSLNodeFactory(model).create());
            }
        }

        if (results.size() == 1) {
            return results;
        }

        // For testing: when using {@link GenerateBytecodeTestVariants}, we want to have a single
        // shared Builder interface that can be used in tests.
        List<CodeTypeElement> builders = results.stream().map(result -> (CodeTypeElement) ElementUtils.findTypeElement(result, "Builder")).toList();

        boolean first = true;
        List<ExecutableElement> expectedPublicInterface = new ArrayList<>();
        Set<String> expectedPublicMethodNames = new HashSet<>();

        for (TypeElement builder : builders) {
            Set<String> publicMethodNames = new HashSet<>();
            for (ExecutableElement method : ElementFilter.methodsIn(builder.getEnclosedElements())) {
                if (!method.getModifiers().contains(Modifier.PUBLIC)) {
                    continue;
                }
                publicMethodNames.add(method.getSimpleName().toString());
                if (first) {
                    expectedPublicInterface.add(method);
                    expectedPublicMethodNames.add(method.getSimpleName().toString());
                }
            }

            // If there's already issues with the model, validating the interfaces just adds noise.
            if (!first && !modelList.hasErrors()) {
                Set<String> missing = new HashSet<>();
                Set<String> remaining = publicMethodNames;

                for (String method : expectedPublicMethodNames) {
                    if (!remaining.remove(method)) {
                        missing.add(method);
                    }
                }

                if (!missing.isEmpty() || !remaining.isEmpty()) {
                    String errorMessage = String.format("Incompatible public interface of builder %s:", builder.getQualifiedName());
                    if (!missing.isEmpty()) {
                        errorMessage += " missing method(s) ";
                        errorMessage += missing.toString();
                    }
                    if (!remaining.isEmpty()) {
                        errorMessage += " additional method(s) ";
                        errorMessage += remaining.toString();
                    }
                    throw new AssertionError(errorMessage);

                }
            }
            first = false;
        }

        TypeElement templateType = modelList.getTemplateType();
        String builderName = templateType.getSimpleName() + "Builder";
        CodeTypeElement abstractBuilderClass = new CodeTypeElement(Set.of(Modifier.PUBLIC, Modifier.ABSTRACT), ElementKind.CLASS, ElementUtils.findPackageElement(templateType), builderName);
        abstractBuilderClass.setSuperClass(types.BytecodeBuilder);
        for (ExecutableElement method : expectedPublicInterface) {
            Set<Modifier> modifiers = new HashSet<>(method.getModifiers());
            modifiers.add(Modifier.ABSTRACT);
            CodeExecutableElement interfaceMethod = new CodeExecutableElement(modifiers, method.getReturnType(), method.getSimpleName().toString());
            method.getParameters().forEach(param -> interfaceMethod.addParameter(param));
            interfaceMethod.setVarArgs(method.isVarArgs());
            abstractBuilderClass.add(interfaceMethod);
        }

        for (CodeTypeElement builder : builders) {
            builder.setSuperClass(abstractBuilderClass.asType());
        }

        results.add(abstractBuilderClass);

        return results;

    }

    private boolean hasExpectErrors(Element element) {
        if (!ExpectError.getExpectedErrors(element).isEmpty()) {
            return true;
        }

        for (Element enclosed : element.getEnclosedElements()) {
            if (hasExpectErrors(enclosed)) {
                return true;
            }
        }

        return false;
    }

}