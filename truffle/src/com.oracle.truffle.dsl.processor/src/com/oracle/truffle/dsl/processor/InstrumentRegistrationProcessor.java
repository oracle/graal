/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
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
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.dsl.processor.LanguageRegistrationProcessor.SortedProperties;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.transform.FixWarningsVisitor;
import com.oracle.truffle.dsl.processor.java.transform.GenerateOverrideVisitor;
import com.oracle.truffle.dsl.processor.model.Template;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import javax.annotation.processing.FilerException;

@SupportedAnnotationTypes("com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration")
public final class InstrumentRegistrationProcessor extends AbstractProcessor {
    private final Map<String, TypeElement> registrations = new HashMap<>();
    private final List<TypeElement> legacyRegistrations = new ArrayList<>();

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    private static final int NUMBER_OF_PROPERTIES_PER_ENTRY = 4;

    private static String generateProvider(TypeElement instrument) {
        ProcessorContext context = ProcessorContext.getInstance();
        Elements elements = context.getEnvironment().getElementUtils();
        Template instrumentModel = new Template(context, instrument, null) {
        };
        TypeElement providerElement = context.getTypeElement(TruffleInstrument.Provider.class);
        CodeTypeElement providerClass = GeneratorUtils.createClass(instrumentModel, null, EnumSet.of(Modifier.PUBLIC),
                        createProviderSimpleName(instrument), null);
        providerClass.getImplements().add(providerElement.asType());
        AnnotationMirror registration = ElementUtils.findAnnotationMirror(instrument.getAnnotationMirrors(), ProcessorContext.getInstance().getType(Registration.class));
        for (Element method : ElementFilter.methodsIn(providerElement.getEnclosedElements())) {
            CodeExecutableElement implementedMethod = CodeExecutableElement.clone((ExecutableElement) method);
            implementedMethod.getModifiers().remove(Modifier.ABSTRACT);
            CodeTreeBuilder builder = implementedMethod.createBuilder();
            switch (method.getSimpleName().toString()) {
                case "create":
                    builder.startReturn().startNew(instrument.asType()).end().end();
                    break;
                case "getInstrumentClassName":
                    builder.startReturn().doubleQuote(elements.getBinaryName(instrument).toString()).end();
                    break;
                default:
                    throw new IllegalStateException("Unsupported method: " + method.getSimpleName());
            }
            providerClass.add(implementedMethod);
        }

        providerClass.addAnnotationMirror(registration);
        DeclaredType overrideType = (DeclaredType) context.getType(Override.class);
        providerClass.accept(new GenerateOverrideVisitor(overrideType), null);
        providerClass.accept(new FixWarningsVisitor(instrument, overrideType), null);
        providerClass.accept(new CodeWriter(context.getEnvironment(), instrument), null);
        return providerClass.getQualifiedName().toString();
    }

    private static String createProviderSimpleName(TypeElement language) {
        StringBuilder nameBuilder = new StringBuilder();
        List<Element> hierarchy = ElementUtils.getElementHierarchy(language);
        for (ListIterator<Element> it = hierarchy.listIterator(hierarchy.size()); it.hasPrevious();) {
            Element enc = it.previous();
            if (enc.getKind().isClass() || enc.getKind().isInterface()) {
                nameBuilder.append(enc.getSimpleName());
            }
        }
        nameBuilder.append("Provider");
        return nameBuilder.toString();
    }

    private static void generateServicesRegistration(Map<String, ? extends TypeElement> providerFqns) {
        ProcessorContext context = ProcessorContext.getInstance();
        ProcessingEnvironment env = context.getEnvironment();
        Elements elements = env.getElementUtils();
        Name providerBinName = elements.getBinaryName(context.getTypeElement(TruffleInstrument.Provider.class));
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
                    out.println("# Generated by " + InstrumentRegistrationProcessor.class.getName());
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
                env.getMessager().printMessage(Kind.ERROR, e.getMessage(), providerFqns.values().iterator().next());
            }
        }
    }

    private void generateLegacyRegistration(List<TypeElement> instruments) {
        String filename = "META-INF/truffle/instrument";
        Properties p = new SortedProperties();
        int numInstruments = loadIfFileAlreadyExists(filename, p);

        for (TypeElement l : instruments) {
            Registration annotation = l.getAnnotation(Registration.class);
            if (annotation == null) {
                continue;
            }

            int instNum = findInstrument(annotation.id(), p);
            if (instNum == 0) { // not found
                numInstruments += 1;
                instNum = numInstruments;
            }

            String prefix = "instrument" + instNum + ".";
            String className = processingEnv.getElementUtils().getBinaryName(l).toString();

            p.setProperty(prefix + "id", annotation.id());
            p.setProperty(prefix + "name", annotation.name());
            p.setProperty(prefix + "version", annotation.version());
            p.setProperty(prefix + "className", className);
            p.setProperty(prefix + "internal", Boolean.toString(annotation.internal()));

            int serviceCounter = 0;
            for (AnnotationMirror anno : l.getAnnotationMirrors()) {
                final String annoName = anno.getAnnotationType().asElement().toString();
                if (Registration.class.getCanonicalName().equals(annoName)) {
                    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : anno.getElementValues().entrySet()) {
                        final Name attrName = entry.getKey().getSimpleName();
                        if (attrName.contentEquals("services")) {
                            AnnotationValue attrValue = entry.getValue();
                            List<?> classes = (List<?>) attrValue.getValue();
                            for (Object clazz : classes) {
                                AnnotationValue clazzValue = (AnnotationValue) clazz;
                                p.setProperty(prefix + "service" + serviceCounter++, clazzValue.getValue().toString());
                            }
                        }
                    }
                }
            }
        }
        if (numInstruments > 0) {
            try {
                FileObject file = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", filename);
                try (OutputStream os = file.openOutputStream()) {
                    p.store(os, "Generated by " + InstrumentRegistrationProcessor.class.getName());
                }
            } catch (IOException e) {
                processingEnv.getMessager().printMessage(Kind.ERROR, e.getMessage(), instruments.get(0));
            }
        }
    }

    private static int findInstrument(String id, Properties p) {
        int cnt = 1;
        String val;
        while ((val = p.getProperty("instrument" + cnt + ".id")) != null) {
            if (id.equals(val)) {
                return cnt;
            }
            cnt += 1;
        }
        return 0;
    }

    private int loadIfFileAlreadyExists(String filename, Properties p) {
        try {
            FileObject file = processingEnv.getFiler().getResource(
                            StandardLocation.CLASS_OUTPUT, "", filename);
            p.load(file.openInputStream());

            return p.keySet().size() / NUMBER_OF_PROPERTIES_PER_ENTRY;
        } catch (IOException e) {
            // Ignore error. It is ok if the file does not exist
            return 0;
        }
    }

    static void loadExistingTypes(ProcessingEnvironment env, List<TypeElement> instruments, String filename, String pre) {
        Set<String> typeNames = new HashSet<>();
        for (TypeElement type : instruments) {
            typeNames.add(ElementUtils.getQualifiedName(type));
        }

        Properties current = new Properties();
        try {
            FileObject object = env.getFiler().getResource(StandardLocation.CLASS_OUTPUT, "", filename);
            current.load(object.openInputStream());
        } catch (IOException e1) {
            env.getMessager().printMessage(Kind.NOTE, filename + e1.getMessage(), null);
            // does not exist yet.
            // better way to detect this?
        }

        for (int cnt = 1;; cnt++) {
            String prefix = pre + cnt + ".";
            String className = current.getProperty(prefix + "className");
            if (className == null) {
                break;
            }
            env.getMessager().printMessage(Kind.NOTE, filename + className, null);
            TypeElement foundType = ElementUtils.getTypeElement(env, className);
            if (foundType != null && !typeNames.contains(ElementUtils.getQualifiedName(foundType))) {
                instruments.add(foundType);
            }
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        ProcessorContext.setThreadLocalInstance(new ProcessorContext(processingEnv, null));
        try {
            if (roundEnv.processingOver()) {
                generateServicesRegistration(registrations);
                generateLegacyRegistration(legacyRegistrations);
                registrations.clear();
                legacyRegistrations.clear();
                return true;
            }
            for (Element e : roundEnv.getElementsAnnotatedWith(Registration.class)) {
                Registration annotation = e.getAnnotation(Registration.class);
                if (annotation != null && e.getKind() == ElementKind.CLASS) {
                    if (!e.getModifiers().contains(Modifier.PUBLIC)) {
                        emitError("Registered instrument class must be public", e);
                        continue;
                    }
                    if (e.getEnclosingElement().getKind() != ElementKind.PACKAGE && !e.getModifiers().contains(Modifier.STATIC)) {
                        emitError("Registered instrument inner-class must be static", e);
                        continue;
                    }
                    TypeMirror truffleInstrument = ProcessorContext.getInstance().getType(TruffleInstrument.class);
                    TypeMirror truffleInstrumentProvider = ProcessorContext.getInstance().getType(TruffleInstrument.Provider.class);
                    boolean processingTruffleInstrument;
                    if (processingEnv.getTypeUtils().isAssignable(e.asType(), truffleInstrument)) {
                        processingTruffleInstrument = true;
                    } else if (processingEnv.getTypeUtils().isAssignable(e.asType(), truffleInstrumentProvider)) {
                        processingTruffleInstrument = false;
                    } else {
                        emitError("Registered instrument class must subclass TruffleInstrument", e);
                        continue;
                    }
                    assertNoErrorExpected(e);
                    if (processingTruffleInstrument) {
                        TypeElement languageTypeElement = (TypeElement) e;
                        if (requiresLegacyRegistration(languageTypeElement)) {
                            legacyRegistrations.add(languageTypeElement);
                        } else {
                            registrations.put(generateProvider(languageTypeElement), languageTypeElement);
                        }
                    }
                }
            }
            return true;
        } finally {
            ProcessorContext.setThreadLocalInstance(null);
        }
    }

    private static boolean requiresLegacyRegistration(TypeElement language) {
        return language.getAnnotation(GenerateLegacyRegistration.class) != null;
    }

    void assertNoErrorExpected(Element e) {
        ExpectError.assertNoErrorExpected(processingEnv, e);
    }

    void emitError(String msg, Element e) {
        if (ExpectError.isExpectedError(processingEnv, e, msg)) {
            return;
        }
        processingEnv.getMessager().printMessage(Kind.ERROR, msg, e);
    }

}
