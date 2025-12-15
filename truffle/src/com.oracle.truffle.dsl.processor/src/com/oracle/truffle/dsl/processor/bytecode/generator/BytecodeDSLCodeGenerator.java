/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.dsl.processor.bytecode.generator.ElementHelpers.generic;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import com.oracle.truffle.dsl.processor.AnnotationProcessor;
import com.oracle.truffle.dsl.processor.ExpectError;
import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.bytecode.model.BytecodeDSLModel;
import com.oracle.truffle.dsl.processor.bytecode.model.BytecodeDSLModels;
import com.oracle.truffle.dsl.processor.generator.CodeTypeElementFactory;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.java.model.GeneratedTypeMirror;

public class BytecodeDSLCodeGenerator extends CodeTypeElementFactory<BytecodeDSLModels> {

    @Override
    public List<CodeTypeElement> create(ProcessorContext context, AnnotationProcessor<?> processor, BytecodeDSLModels modelList) {
        List<CodeTypeElement> results = new ArrayList<>();

        // For testing: when using {@code @ExpectError}, we don't want to actually generate the
        // code, since compilation will likely fail.
        if (hasExpectErrors(modelList.getTemplateType())) {
            return results;
        }

        TypeMirror abstractBuilder;
        CodeTypeElement abstractBuilderType = null;
        if (modelList.getModels().size() > 1) {
            abstractBuilderType = createAbstractBuilderType(modelList.getModels().getFirst(), modelList.getModels().getFirst().getTemplateType());
            abstractBuilder = abstractBuilderType.asType();
        } else {
            abstractBuilder = null;
        }
        for (BytecodeDSLModel model : modelList.getModels()) {
            if (modelList.hasErrors()) {
                results.add(new BytecodeRootNodeErrorElement(model, abstractBuilder));
            } else {
                results.add(new BytecodeRootNodeElement(model, abstractBuilder));
            }
        }

        if (results.size() == 1) {
            return results;
        }

        /**
         * When using {@link GenerateBytecodeTestVariants}, we generate an abstract superclass
         * defining the public interface for the Builders. Test code writes parsers using this
         * abstract builder's interface so that the parser can be used to test each variant.
         */
        Iterator<CodeTypeElement> builders = results.stream().map(result -> (CodeTypeElement) ElementUtils.findTypeElement(result, "Builder")).iterator();

        /**
         * Define the abstract methods using the first Builder, then assert that the other Builders
         * have the same set of methods.
         */
        CodeTypeElement firstBuilder = builders.next();
        Set<String> expectedPublicMethodNames = new HashSet<>();
        for (ExecutableElement method : ElementFilter.methodsIn(firstBuilder.getEnclosedElements())) {
            if (!method.getModifiers().contains(Modifier.PUBLIC)) {
                continue;
            }

            Set<Modifier> modifiers = new HashSet<>(method.getModifiers());
            modifiers.add(Modifier.ABSTRACT);
            CodeExecutableElement abstractMethod = new CodeExecutableElement(modifiers, method.getReturnType(), method.getSimpleName().toString());
            method.getParameters().forEach(param -> abstractMethod.addParameter(param));
            abstractMethod.setVarArgs(method.isVarArgs());
            abstractBuilderType.add(abstractMethod);

            expectedPublicMethodNames.add(method.getSimpleName().toString());
        }

        while (builders.hasNext()) {
            TypeElement builder = builders.next();
            Set<String> publicMethodNames = new HashSet<>();
            for (ExecutableElement method : ElementFilter.methodsIn(builder.getEnclosedElements())) {
                if (!method.getModifiers().contains(Modifier.PUBLIC)) {
                    continue;
                }
                publicMethodNames.add(method.getSimpleName().toString());
            }

            // If there's already issues with the model, validating the interfaces just adds noise.
            if (!modelList.hasErrors()) {
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
        }

        TypeMirror descriptorType = findBytecodeVariantType(abstractBuilderType.asType());
        TypeMirror returnType = generic(context.getType(List.class), descriptorType);

        CodeExecutableElement ex = new CodeExecutableElement(Set.of(Modifier.PUBLIC, Modifier.STATIC), returnType, "variants");
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn().startStaticCall(context.getType(List.class), "of");
        for (BytecodeDSLModel model : modelList.getModels()) {
            b.startGroup();
            b.newLine();
            b.startIndention();
            b.staticReference(new GeneratedTypeMirror(ElementUtils.getPackageName(model.getTemplateType()),
                            model.getName()), "BYTECODE");
            b.end(); // indention
            b.end(); // group
        }
        b.end().end();
        abstractBuilderType.add(ex);

        results.add(abstractBuilderType);

        return results;

    }

    public static CodeTypeElement createAbstractBuilderType(BytecodeDSLModel model, TypeElement templateType) {
        String abstractBuilderName = templateType.getSimpleName() + "Builder";
        CodeTypeElement abstractBuilderType = new CodeTypeElement(Set.of(Modifier.PUBLIC, Modifier.ABSTRACT), ElementKind.CLASS, ElementUtils.findPackageElement(templateType), abstractBuilderName);
        abstractBuilderType.setSuperClass(ProcessorContext.types().BytecodeBuilder);

        CodeExecutableElement constructor = GeneratorUtils.createConstructorUsingFields(Set.of(Modifier.PROTECTED), abstractBuilderType);
        constructor.getParameters().clear();
        constructor.addParameter(new CodeVariableElement(ProcessorContext.getInstance().getType(Object.class), "token"));
        constructor.createBuilder().statement("super(token)");
        abstractBuilderType.add(constructor);

        abstractBuilderType.add(new BytecodeVariantElement(templateType.asType(), model.languageClass, abstractBuilderType.asType()));

        return abstractBuilderType;
    }

    public static TypeMirror findBytecodeVariantType(TypeMirror builderType) {
        TypeElement e = findInnerClass(ElementUtils.castTypeElement(builderType), "BytecodeVariant");
        if (e == null) {
            return null;
        }
        return e.asType();
    }

    static TypeElement findInnerClass(TypeElement type, String name) {
        for (TypeElement field : ElementFilter.typesIn(type.getEnclosedElements())) {
            if (field.getSimpleName().toString().equals(name)) {
                return field;
            }
        }
        return null;
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

        if (element instanceof ExecutableElement ex) {
            for (VariableElement param : ex.getParameters()) {
                if (hasExpectErrors(param)) {
                    return true;
                }
            }
        }

        return false;
    }

    static final class BytecodeVariantElement extends CodeTypeElement {
        BytecodeVariantElement(TypeMirror rootType, TypeMirror languageClass, TypeMirror builderType) {
            super(Set.of(PUBLIC, STATIC, Modifier.ABSTRACT), ElementKind.CLASS, null, "BytecodeVariant");
            setSuperClass(generic(ProcessorContext.types().BytecodeDescriptor, rootType, languageClass, builderType));

            add(GeneratorUtils.createConstructorUsingFields(Set.of(Modifier.PROTECTED), this));
        }
    }
}
