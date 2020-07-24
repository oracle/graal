/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.tools.Diagnostic.Kind;

import com.oracle.truffle.dsl.processor.ProcessorContext.ProcessCallback;
import com.oracle.truffle.dsl.processor.generator.NodeCodeGenerator;
import com.oracle.truffle.dsl.processor.generator.TypeSystemCodeGenerator;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.library.ExportsGenerator;
import com.oracle.truffle.dsl.processor.library.ExportsParser;
import com.oracle.truffle.dsl.processor.library.LibraryGenerator;
import com.oracle.truffle.dsl.processor.library.LibraryParser;
import com.oracle.truffle.dsl.processor.parser.AbstractParser;
import com.oracle.truffle.dsl.processor.parser.NodeParser;
import com.oracle.truffle.dsl.processor.parser.TypeSystemParser;

/**
 * THIS IS NOT PUBLIC API.
 */
public class TruffleProcessor extends AbstractProcessor implements ProcessCallback {

    private final Map<String, Map<String, Element>> serviceRegistrations = new LinkedHashMap<>();

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        try {
            ProcessorContext.enter(processingEnv, this);
            if (roundEnv.processingOver()) {
                for (Entry<String, Map<String, Element>> element : serviceRegistrations.entrySet()) {
                    AbstractRegistrationProcessor.generateServicesRegistration(element.getKey(), element.getValue());
                }
                serviceRegistrations.clear();
                return true;
            }
            List<AnnotationProcessor<?>> processors = createGenerators();
            currentProcessors.set(processors);
            for (AnnotationProcessor<?> generator : processors) {
                AbstractParser<?> parser = generator.getParser();
                if (parser.getAnnotationType() != null) {
                    for (Element e : roundEnv.getElementsAnnotatedWith(ElementUtils.castTypeElement(parser.getAnnotationType()))) {
                        processElement(generator, e, false);
                    }
                    DeclaredType repeat = parser.getRepeatAnnotationType();
                    if (repeat != null) {
                        for (Element e : roundEnv.getElementsAnnotatedWith(ElementUtils.castTypeElement(repeat))) {
                            processElement(generator, e, false);
                        }
                    }
                }

                for (DeclaredType annotationType : parser.getTypeDelegatedAnnotationTypes()) {
                    for (Element e : roundEnv.getElementsAnnotatedWith(ElementUtils.castTypeElement(annotationType))) {
                        Optional<TypeElement> processedType;
                        if (parser.isDelegateToRootDeclaredType()) {
                            processedType = ElementUtils.findRootEnclosingType(e);
                        } else {
                            processedType = ElementUtils.findParentEnclosingType(e);
                        }
                        processElement(generator, processedType.orElseThrow(AssertionError::new), false);
                    }
                }

                for (AnnotationProcessor<?> processor : processors) {
                    for (Entry<String, Map<String, Element>> element : processor.getServiceRegistrations().entrySet()) {
                        Map<String, Element> currentElements = serviceRegistrations.get(element.getKey());
                        if (currentElements == null) {
                            currentElements = new LinkedHashMap<>(element.getValue());
                            serviceRegistrations.put(element.getKey(), currentElements);
                        } else {
                            currentElements.putAll(element.getValue());
                        }
                    }
                }

                for (Entry<String, Map<String, Element>> element : serviceRegistrations.entrySet()) {
                    String service = element.getKey();
                    for (Entry<String, Element> serviceElement : element.getValue().entrySet()) {
                        if (AbstractRegistrationProcessor.shouldGenerateProviderFiles(serviceElement.getValue())) {
                            AbstractRegistrationProcessor.generateProviderFile(processingEnv, serviceElement.getKey(), service, serviceElement.getValue());
                        }
                    }
                }
            }
        } finally {
            ProcessorContext.leave();
            currentProcessors.set(null);
        }
        return false;
    }

    private final ThreadLocal<List<AnnotationProcessor<?>>> currentProcessors = new ThreadLocal<>();

    private static void processElement(AnnotationProcessor<?> generator, Element e, boolean callback) {
        try {
            generator.process(e, callback);
        } catch (Throwable e1) {
            handleThrowable(generator, e1, e);
        }
    }

    static void handleThrowable(AnnotationProcessor<?> generator, Throwable t, Element e) {
        String message = "Uncaught error in " + (generator != null ? generator.getClass().getSimpleName() : null) + " while processing " + e + " ";
        ProcessorContext.getInstance().getEnvironment().getMessager().printMessage(Kind.ERROR, message + ": " + ElementUtils.printException(t), e);
    }

    @Override
    public void callback(TypeElement template) {
        for (AnnotationProcessor<?> generator : currentProcessors.get()) {
            DeclaredType annotationType = generator.getParser().getAnnotationType();
            if (annotationType != null) {
                AnnotationMirror annotation = ElementUtils.findAnnotationMirror(template, annotationType);
                if (annotation != null) {
                    processElement(generator, template, true);
                }
            }
        }
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotations = new HashSet<>();
        annotations.add(TruffleTypes.Specialization_Name);
        annotations.add(TruffleTypes.Fallback_Name);
        annotations.add(TruffleTypes.TypeSystemReference_Name);
        annotations.add(TruffleTypes.Executed_Name);
        annotations.add(TruffleTypes.NodeChild_Name);
        annotations.add(TruffleTypes.NodeChildren_Name);
        annotations.add(TruffleTypes.TypeSystem_Name);
        annotations.add(TruffleTypes.GenerateLibrary_Name);
        annotations.add(TruffleTypes.ExportLibrary_Name);
        annotations.add(TruffleTypes.ExportMessage_Name);
        annotations.add(TruffleTypes.ExportLibrary_Repeat_Name);
        return annotations;
    }

    private static List<AnnotationProcessor<?>> createGenerators() {
        List<AnnotationProcessor<?>> generators = new ArrayList<>();
        generators.add(new AnnotationProcessor<>(new TypeSystemParser(), new TypeSystemCodeGenerator()));
        generators.add(new AnnotationProcessor<>(NodeParser.createDefaultParser(), new NodeCodeGenerator()));
        generators.add(new AnnotationProcessor<>(new LibraryParser(), new LibraryGenerator()));
        generators.add(new AnnotationProcessor<>(new ExportsParser(), new ExportsGenerator(new LinkedHashMap<>())));
        return generators;
    }

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        this.processingEnv = env;
        super.init(env);
    }

}
