/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
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
import java.util.function.Predicate;

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
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.compiler.CompilerFactory;
import com.oracle.truffle.dsl.processor.java.compiler.JDTCompiler;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.transform.FixWarningsVisitor;
import com.oracle.truffle.dsl.processor.java.transform.GenerateOverrideVisitor;
import com.oracle.truffle.dsl.processor.model.Template;

abstract class AbstractRegistrationProcessor extends AbstractProcessor {

    private final Map<String, Element> registrations = new HashMap<>();
    private final List<TypeElement> legacyRegistrations = new ArrayList<>();

    @Override
    public final SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @SuppressWarnings({"deprecation", "unchecked"})
    @Override
    public final boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        ProcessorContext.enter(processingEnv);
        try {
            ProcessorContext context = ProcessorContext.getInstance();
            String providerServiceBinName = processingEnv.getElementUtils().getBinaryName(context.getTypeElement(getProviderClass())).toString();
            if (roundEnv.processingOver()) {
                generateServicesRegistration(providerServiceBinName, registrations);
                generateLegacyRegistration(legacyRegistrations);
                registrations.clear();
                legacyRegistrations.clear();
                return true;
            }
            String[] supportedAnnotations = this.getClass().getAnnotation(SupportedAnnotationTypes.class).value();
            TypeElement supportedAnnotation = processingEnv.getElementUtils().getTypeElement(supportedAnnotations[0]);
            if (supportedAnnotation == null) {
                throw new IllegalStateException("Cannot resolve " + supportedAnnotations[0]);
            }
            Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(supportedAnnotation);
            if (!annotatedElements.isEmpty()) {
                for (Element e : annotatedElements) {
                    AnnotationMirror mirror = ElementUtils.findAnnotationMirror(e, supportedAnnotation.asType());
                    if (mirror != null && e.getKind() == ElementKind.CLASS) {
                        if (validateRegistration(e, mirror)) {
                            TypeElement annotatedElement = (TypeElement) e;
                            if (requiresLegacyRegistration(annotatedElement)) {
                                legacyRegistrations.add(annotatedElement);
                            } else {
                                String providerImplBinName = generateProvider(annotatedElement);
                                registrations.put(providerImplBinName, annotatedElement);
                                if (shouldGenerateProviderFiles(annotatedElement)) {
                                    generateProviderFile(processingEnv, providerImplBinName, providerServiceBinName, annotatedElement);
                                }
                            }
                        }
                    }
                }
            }
            return true;
        } finally {
            ProcessorContext.leave();
        }
    }

    abstract boolean validateRegistration(Element annotatedElement, AnnotationMirror registrationMirror);

    abstract DeclaredType getProviderClass();

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

    static AnnotationMirror copyAnnotations(AnnotationMirror mirror, Predicate<ExecutableElement> filter) {
        CodeAnnotationMirror res = new CodeAnnotationMirror(mirror.getAnnotationType());
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e : mirror.getElementValues().entrySet()) {
            ExecutableElement executable = e.getKey();
            AnnotationValue value = e.getValue();
            if (filter.test(executable)) {
                res.setElementValue(executable, value);
            }
        }
        return res;
    }

    private static boolean requiresLegacyRegistration(TypeElement annotatedElement) {
        for (AnnotationMirror mirror : annotatedElement.getAnnotationMirrors()) {
            Element annotationType = mirror.getAnnotationType().asElement();
            if ("GenerateLegacyRegistration".contentEquals(annotationType.getSimpleName())) {
                return true;
            }
        }
        return false;
    }

    private String generateProvider(TypeElement annotatedElement) {
        ProcessorContext context = ProcessorContext.getInstance();
        Template model = new Template(context, annotatedElement, null) {
        };
        TypeElement providerElement = context.getTypeElement(getProviderClass());
        CodeTypeElement providerClass = GeneratorUtils.createClass(model, null, EnumSet.of(Modifier.PUBLIC),
                        createProviderSimpleName(annotatedElement), null);
        providerClass.getModifiers().add(Modifier.FINAL);
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

    static void generateProviderFile(ProcessingEnvironment env, String providerClassName, String serviceClassName, Element... originatingElements) {
        assert originatingElements.length > 0;
        String filename = "META-INF/truffle-registrations/" + providerClassName;
        try {
            FileObject file = env.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", filename, originatingElements);
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(file.openOutputStream(), "UTF-8"));
            writer.println(serviceClassName);
            writer.close();
        } catch (IOException e) {
            handleIOError(e, env, originatingElements[0]);
        }
    }

    /**
     * Determines if a given exception is (most likely) caused by
     * <a href="https://bugs.eclipse.org/bugs/show_bug.cgi?id=367599">Bug 367599</a>.
     */
    private static boolean isBug367599(Throwable t) {
        if (t instanceof FilerException) {
            for (StackTraceElement ste : t.getStackTrace()) {
                if (ste.toString().contains("org.eclipse.jdt.internal.apt.pluggable.core.filer.IdeFilerImpl.create")) {
                    // See: https://bugs.eclipse.org/bugs/show_bug.cgi?id=367599
                    return true;
                }
            }
        }
        return t.getCause() != null && isBug367599(t.getCause());
    }

    static void generateServicesRegistration(String providerBinName, Map<String, Element> providerRegistrations) {
        ProcessorContext context = ProcessorContext.getInstance();
        ProcessingEnvironment env = context.getEnvironment();
        Elements elements = env.getElementUtils();
        String filename = "META-INF/services/" + providerBinName;
        List<String> providerClassNames = new ArrayList<>(providerRegistrations.size());
        for (String providerFqn : providerRegistrations.keySet()) {
            TypeElement te = ElementUtils.getTypeElement(env, providerFqn);
            if (te == null) {
                providerClassNames.add(providerFqn);
            } else {
                providerClassNames.add(elements.getBinaryName(te).toString());
            }
        }
        Collections.sort(providerClassNames);
        if (!providerClassNames.isEmpty()) {
            try {
                FileObject file = env.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", filename, providerRegistrations.values().toArray(new Element[providerRegistrations.size()]));
                try (PrintWriter out = new PrintWriter(new OutputStreamWriter(file.openOutputStream(), "UTF-8"))) {
                    for (String providerClassName : providerClassNames) {
                        out.println(providerClassName);
                    }
                }
            } catch (IOException e) {
                handleIOError(e, env, providerRegistrations.values().iterator().next());
            }
        }
    }

    private static void handleIOError(IOException e, ProcessingEnvironment env, Element element) {
        if (e instanceof FilerException) {
            if (e.getMessage().startsWith("Source file already created") || e.getMessage().startsWith("Resource already created")) {
                // ignore source file already created errors
                return;
            }
        }
        env.getMessager().printMessage(isBug367599(e) ? Kind.NOTE : Kind.ERROR, e.getMessage(), element);
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
                handleIOError(e, processingEnv, annotatedElements.get(0));
            }
        }
    }

    static boolean shouldGenerateProviderFiles(Element currentElement) {
        return CompilerFactory.getCompiler(currentElement) instanceof JDTCompiler;
    }

    @SuppressWarnings("serial")
    static class SortedProperties extends Properties {
        @Override
        public synchronized Enumeration<Object> keys() {
            return Collections.enumeration(new TreeSet<>(super.keySet()));
        }
    }
}
