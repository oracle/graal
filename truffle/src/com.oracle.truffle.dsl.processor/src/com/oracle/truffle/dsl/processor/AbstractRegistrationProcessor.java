/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.transform.FixWarningsVisitor;
import com.oracle.truffle.dsl.processor.java.transform.GenerateOverrideVisitor;
import com.oracle.truffle.dsl.processor.model.Template;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

abstract class AbstractRegistrationProcessor extends AbstractProcessor {

    private final Map<String, TypeElement> registrations = new HashMap<>();
    private final List<TypeElement> legacyRegistrations = new ArrayList<>();

    @Override
    public final SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @SuppressWarnings({"deprecation", "unchecked"})
    @Override
    public final boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        ProcessorContext.setThreadLocalInstance(new ProcessorContext(processingEnv, null));
        try {
            if (roundEnv.processingOver()) {
                generateServicesRegistration(registrations);
                generateLegacyRegistration(legacyRegistrations);
                registrations.clear();
                legacyRegistrations.clear();
                return true;
            }
            String[] supportedAnnotations = this.getClass().getAnnotation(SupportedAnnotationTypes.class).value();
            Class<? extends Annotation> registrationAnnotationClass;
            try {
                TypeElement supportedAnnotation = processingEnv.getElementUtils().getTypeElement(supportedAnnotations[0]);
                if (supportedAnnotation == null) {
                    throw new IllegalStateException("Cannot resolve " + supportedAnnotations[0]);
                }
                registrationAnnotationClass = (Class<? extends Annotation>) Class.forName(processingEnv.getElementUtils().getBinaryName(supportedAnnotation).toString());
            } catch (ClassNotFoundException cnf) {
                throw new IllegalStateException(cnf);
            }
            TypeMirror registration = ProcessorContext.getInstance().getType(registrationAnnotationClass);
            for (Element e : roundEnv.getElementsAnnotatedWith(registrationAnnotationClass)) {
                AnnotationMirror mirror = ElementUtils.findAnnotationMirror(e.getAnnotationMirrors(), registration);
                Annotation annotation = e.getAnnotation(registrationAnnotationClass);
                if (annotation != null && e.getKind() == ElementKind.CLASS) {
                    if (validateRegistration(e, mirror, annotation)) {
                        TypeElement annotatedElement = (TypeElement) e;
                        if (requiresLegacyRegistration(annotatedElement)) {
                            legacyRegistrations.add(annotatedElement);
                        } else {
                            registrations.put(generateProvider(annotatedElement), annotatedElement);
                        }
                    }
                }
            }
            return true;
        } finally {
            ProcessorContext.setThreadLocalInstance(null);
        }
    }

    abstract boolean validateRegistration(Element annotatedElement, AnnotationMirror registrationMirror, Annotation registration);

    abstract Class<?> getProviderClass();

    abstract Iterable<AnnotationMirror> getProviderAnnotations(TypeElement annotatedElement);

    abstract void implementMethod(TypeElement annotatedElement, CodeExecutableElement methodToImplement);

    abstract String getRegistrationFileName();

    abstract void storeRegistrations(Properties into, Iterable<? extends TypeElement> annotatedElements);

    final void assertNoErrorExpected(Element e) {
        ExpectError.assertNoErrorExpected(processingEnv, e);
    }

    final void emitError(String msg, Element e) {
        if (ExpectError.isExpectedError(processingEnv, e, msg)) {
            return;
        }
        processingEnv.getMessager().printMessage(Kind.ERROR, msg, e);
    }

    final void emitError(String msg, Element e, AnnotationMirror mirror, AnnotationValue value) {
        if (ExpectError.isExpectedError(processingEnv, e, msg)) {
            return;
        }
        processingEnv.getMessager().printMessage(Kind.ERROR, msg, e, mirror, value);
    }

    final void emitWarning(String msg, Element e) {
        if (ExpectError.isExpectedError(processingEnv, e, msg)) {
            return;
        }
        processingEnv.getMessager().printMessage(Kind.WARNING, msg, e);
    }

    final void emitWarning(String msg, Element e, AnnotationMirror mirror, AnnotationValue value) {
        if (ExpectError.isExpectedError(processingEnv, e, msg)) {
            return;
        }
        processingEnv.getMessager().printMessage(Kind.WARNING, msg, e, mirror, value);
    }

    private static boolean requiresLegacyRegistration(TypeElement annotatedElement) {
        return annotatedElement.getAnnotation(GenerateLegacyRegistration.class) != null;
    }

    private String generateProvider(TypeElement annotatedElement) {
        ProcessorContext context = ProcessorContext.getInstance();
        Template model = new Template(context, annotatedElement, null) {
        };
        TypeElement providerElement = context.getTypeElement(getProviderClass());
        CodeTypeElement providerClass = GeneratorUtils.createClass(model, null, EnumSet.of(Modifier.PUBLIC),
                        createProviderSimpleName(annotatedElement), null);
        providerClass.getImplements().add(providerElement.asType());
        for (Element method : ElementFilter.methodsIn(providerElement.getEnclosedElements())) {
            CodeExecutableElement implementedMethod = CodeExecutableElement.clone((ExecutableElement) method);
            implementedMethod.getModifiers().remove(Modifier.ABSTRACT);
            implementMethod(annotatedElement, implementedMethod);
            providerClass.add(implementedMethod);
        }

        for (AnnotationMirror annotationMirror : getProviderAnnotations(annotatedElement)) {
            providerClass.addAnnotationMirror(annotationMirror);
        }
        DeclaredType overrideType = (DeclaredType) context.getType(Override.class);
        providerClass.accept(new GenerateOverrideVisitor(overrideType), null);
        providerClass.accept(new FixWarningsVisitor(annotatedElement, overrideType), null);
        providerClass.accept(new CodeWriter(context.getEnvironment(), annotatedElement), null);
        return providerClass.getQualifiedName().toString();
    }

    private static String createProviderSimpleName(TypeElement annotatedElement) {
        StringBuilder nameBuilder = new StringBuilder();
        List<Element> hierarchy = ElementUtils.getElementHierarchy(annotatedElement);
        for (ListIterator<Element> it = hierarchy.listIterator(hierarchy.size()); it.hasPrevious();) {
            Element enc = it.previous();
            if (enc.getKind().isClass() || enc.getKind().isInterface()) {
                nameBuilder.append(enc.getSimpleName());
            }
        }
        nameBuilder.append("Provider");
        return nameBuilder.toString();
    }

    private void generateServicesRegistration(Map<String, ? extends TypeElement> providerFqns) {
        ProcessorContext context = ProcessorContext.getInstance();
        ProcessingEnvironment env = context.getEnvironment();
        Elements elements = env.getElementUtils();
        Name providerBinName = elements.getBinaryName(context.getTypeElement(getProviderClass()));
        String filename = "META-INF/services/" + providerBinName;
        List<String> providerClassNames = new ArrayList<>(providerFqns.size());
        for (String providerFqn : providerFqns.keySet()) {
            TypeElement te = ElementUtils.getTypeElement(env, providerFqn);
            providerClassNames.add(elements.getBinaryName(te).toString());
        }
        Collections.sort(providerClassNames);
        if (!providerClassNames.isEmpty()) {
            try {
                FileObject file = env.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", filename, providerFqns.values().toArray(new Element[providerFqns.size()]));
                try (PrintWriter out = new PrintWriter(new OutputStreamWriter(file.openOutputStream(), "UTF-8"))) {
                    out.println("# Generated by " + LanguageRegistrationProcessor.class.getName());
                    for (String providerClassName : providerClassNames) {
                        out.println(providerClassName);
                    }
                }
            } catch (IOException e) {
                if (e instanceof FilerException) {
                    if (e.getMessage().startsWith("Source file already created")) {
                        // ignore source file already created errors
                        return;
                    }
                }
                env.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage(), providerFqns.values().iterator().next());
            }
        }
    }

    private void generateLegacyRegistration(List<TypeElement> annotatedElements) {
        String filename = getRegistrationFileName();
        // sorted properties
        Properties p = new SortedProperties();
        storeRegistrations(p, annotatedElements);
        if (!p.isEmpty()) {
            try {
                FileObject file = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", filename, annotatedElements.toArray(new Element[0]));
                try (OutputStream os = file.openOutputStream()) {
                    p.store(os, "Generated by " + getClass().getName());
                }
            } catch (IOException e) {
                if (e instanceof FilerException) {
                    if (e.getMessage().startsWith("Source file already created")) {
                        // ignore source file already created errors
                        return;
                    }
                }
                processingEnv.getMessager().printMessage(Kind.ERROR, e.getMessage(), annotatedElements.get(0));
            }
        }
    }

    @SuppressWarnings("serial")
    static class SortedProperties extends Properties {
        @Override
        public synchronized Enumeration<Object> keys() {
            return Collections.enumeration(new TreeSet<>(super.keySet()));
        }
    }
}
