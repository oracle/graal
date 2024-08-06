/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
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
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.compiler.CompilerFactory;
import com.oracle.truffle.dsl.processor.java.compiler.JDTCompiler;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.transform.FixWarningsVisitor;
import com.oracle.truffle.dsl.processor.java.transform.GenerateOverrideVisitor;
import com.oracle.truffle.dsl.processor.model.Template;

abstract class AbstractRegistrationProcessor extends AbstractProcessor {

    private final Map<String, Element> registrations = new HashMap<>();

    @Override
    public final SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public final boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        try (ProcessorContext context = ProcessorContext.enter(processingEnv)) {
            String providerServiceBinName = processingEnv.getElementUtils().getBinaryName(context.getTypeElement(getProviderClass())).toString();
            if (roundEnv.processingOver()) {
                generateServicesRegistration(providerServiceBinName, registrations);
                registrations.clear();
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
                        if (accepts(e, mirror) && validateRegistration(e, mirror)) {
                            TypeElement annotatedElement = (TypeElement) e;
                            String providerImplBinName = generateProvider(annotatedElement);
                            registrations.put(providerImplBinName, annotatedElement);
                            if (shouldGenerateProviderFiles(annotatedElement)) {
                                generateProviderFile(processingEnv, providerImplBinName, providerServiceBinName, annotatedElement);
                            }
                        }
                    }
                }
            }
            return true;
        }
    }

    @SuppressWarnings("unused")
    boolean accepts(Element annotatedElement, AnnotationMirror registrationMirror) {
        return true;
    }

    abstract boolean validateRegistration(Element annotatedElement, AnnotationMirror registrationMirror);

    abstract DeclaredType getProviderClass();

    abstract Iterable<AnnotationMirror> getProviderAnnotations(TypeElement annotatedElement);

    abstract void implementMethod(TypeElement annotatedElement, CodeExecutableElement methodToImplement);

    static void assertNoErrorExpected(Element e) {
        ExpectError.assertNoErrorExpected(e);
    }

    final void emitError(String msg, Element e) {
        if (ExpectError.isExpectedError(e, msg)) {
            return;
        }
        processingEnv.getMessager().printMessage(Kind.ERROR, msg, e);
    }

    final void emitError(String msg, Element e, AnnotationMirror mirror, AnnotationValue value) {
        if (ExpectError.isExpectedError(e, msg)) {
            return;
        }
        processingEnv.getMessager().printMessage(Kind.ERROR, msg, e, mirror, value);
    }

    final void emitWarning(String msg, Element e) {
        if (ExpectError.isExpectedError(e, msg)) {
            return;
        }
        processingEnv.getMessager().printMessage(Kind.WARNING, msg, e);
    }

    boolean validateInternalResources(Element annotatedElement, AnnotationMirror mirror, ProcessorContext context) {
        AnnotationValue value = ElementUtils.getAnnotationValue(mirror, "internalResources", true);
        TruffleTypes types = context.getTypes();
        Map<String, TypeElement> usedResourceIds = new HashMap<>();
        for (TypeMirror internalResource : ElementUtils.getAnnotationValueList(TypeMirror.class, mirror, "internalResources")) {
            TypeElement internalResourceElement = ElementUtils.fromTypeMirror(internalResource);
            Set<Modifier> modifiers = internalResourceElement.getModifiers();
            if (internalResourceElement.getEnclosingElement().getKind() != ElementKind.PACKAGE && !modifiers.contains(Modifier.STATIC)) {
                emitError(String.format("The class %s must be a static inner-class or a top-level class. To resolve this, make the %s static or top-level class.",
                                getScopedName(internalResourceElement), internalResourceElement.getSimpleName()), annotatedElement, mirror, value);
                return false;
            }
            if (!ElementUtils.isVisible(annotatedElement, internalResourceElement)) {
                PackageElement targetPackage = ElementUtils.findPackageElement(annotatedElement);
                emitError(String.format("The class %s must be public or package protected in the %s package. To resolve this, make the %s public or move it to the %s package.",
                                getScopedName(internalResourceElement), targetPackage.getQualifiedName(), getScopedName(internalResourceElement), targetPackage.getQualifiedName()),
                                annotatedElement, mirror, value);
                return false;
            }
            AnnotationMirror id = ElementUtils.findAnnotationMirror(internalResourceElement.getAnnotationMirrors(), types.InternalResource_Id);
            if (id == null) {
                String idSimpleName = ElementUtils.getSimpleName(types.InternalResource_Id);
                emitError(String.format("The class %s must be annotated by the @%s annotation. To resolve this, add '@%s(\"resource-id\")' annotation.",
                                getScopedName(internalResourceElement), idSimpleName, idSimpleName), annotatedElement, mirror, value);
                return false;
            }
            boolean optional = ElementUtils.getAnnotationValue(Boolean.class, id, "optional");
            if (optional) {
                String resourceClzName = getScopedName(internalResourceElement);
                emitError(String.format("Optional internal resources must not be registered using '@Registration' annotation. " +
                                "To resolve this, remove the '%s' from 'internalResources' the or make the '%s' non-optional by removing 'optional = true'.",
                                resourceClzName, resourceClzName), annotatedElement, mirror, value);
                return false;
            }
            String resourceComponentId = ElementUtils.getAnnotationValue(String.class, id, "componentId");
            String registrationComponentId = ElementUtils.getAnnotationValue(String.class, mirror, "id");
            if (!resourceComponentId.isEmpty() && !resourceComponentId.equals(registrationComponentId)) {
                String idSimpleName = ElementUtils.getSimpleName(types.InternalResource_Id);
                emitError(String.format("The '@%s.componentId' for an required internal resources must be unset or equal to '@Registration.id'. " +
                                "To resolve this, remove the '@%s.componentId = \"%s\"'.",
                                idSimpleName, idSimpleName, resourceComponentId), annotatedElement, mirror, value);
                return false;
            }
            String idValue = ElementUtils.getAnnotationValue(String.class, id, "value");
            TypeElement prev = usedResourceIds.put(idValue, internalResourceElement);
            if (prev != null) {
                String prevResourceClzName = getScopedName(prev);
                String newResourceClzName = getScopedName(internalResourceElement);
                String idSimpleName = ElementUtils.getSimpleName(types.InternalResource_Id);
                emitError(String.format("Internal resources must have unique ids within the component. But %s and %s use the same id %s. To resolve this, change the @%s value on %s or %s.",
                                prevResourceClzName, newResourceClzName, idValue, idSimpleName, prevResourceClzName, newResourceClzName), annotatedElement, mirror, value);
                return false;
            }
            boolean foundConstructor = false;
            for (ExecutableElement constructor : ElementFilter.constructorsIn(internalResourceElement.getEnclosedElements())) {
                if (!ElementUtils.isVisible(annotatedElement, constructor)) {
                    continue;
                }
                if (!constructor.getParameters().isEmpty()) {
                    continue;
                }
                foundConstructor = true;
                break;
            }
            if (!foundConstructor) {
                emitError(String.format("The class %s must have a no argument public constructor. To resolve this, add public %s() constructor.",
                                getScopedName(internalResourceElement), ElementUtils.getSimpleName(internalResourceElement)), annotatedElement, mirror, value);
                return false;
            }
        }
        return true;
    }

    static CodeAnnotationMirror copyAnnotations(AnnotationMirror mirror, Predicate<ExecutableElement> filter) {
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

    private String generateProvider(TypeElement annotatedElement) {
        ProcessorContext context = ProcessorContext.getInstance();
        Template model = new Template(context, annotatedElement, null) {
        };
        TypeElement providerElement = context.getTypeElement(getProviderClass());
        CodeTypeElement providerClass = GeneratorUtils.createClass(model, null, EnumSet.of(Modifier.PUBLIC),
                        createProviderSimpleName(annotatedElement), providerElement.asType());
        providerClass.getModifiers().add(Modifier.FINAL);
        for (ExecutableElement method : ElementFilter.methodsIn(providerElement.getEnclosedElements())) {
            CodeExecutableElement implementedMethod = CodeExecutableElement.clone(method);
            implementedMethod.getModifiers().remove(Modifier.ABSTRACT);
            implementMethod(annotatedElement, implementedMethod);
            providerClass.add(implementedMethod);
        }

        for (AnnotationMirror annotationMirror : getProviderAnnotations(annotatedElement)) {
            providerClass.addAnnotationMirror(annotationMirror);
        }
        DeclaredType overrideType = (DeclaredType) context.getType(Override.class);
        providerClass.accept(new GenerateOverrideVisitor(overrideType), null);
        providerClass.accept(new FixWarningsVisitor(overrideType), null);
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
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(file.openOutputStream(), StandardCharsets.UTF_8));
            writer.println(serviceClassName);
            writer.close();
        } catch (IOException e) {
            handleIOError(e, env, originatingElements[0]);
        }
    }

    static void generateGetServicesClassNames(AnnotationMirror registration, CodeTreeBuilder builder, ProcessorContext context) {
        List<TypeMirror> services = ElementUtils.getAnnotationValueList(TypeMirror.class, registration, "services");
        Types types = context.getEnvironment().getTypeUtils();
        builder.startReturn();
        builder.startStaticCall(context.getType(List.class), "of");
        for (TypeMirror service : services) {
            builder.startGroup().doubleQuote(ElementUtils.getBinaryName((TypeElement) ((DeclaredType) types.erasure(service)).asElement())).end();
        }
        builder.end(2);
    }

    static void generateGetInternalResourceIds(AnnotationMirror registration, CodeTreeBuilder builder, ProcessorContext context) {
        List<TypeMirror> resources = ElementUtils.getAnnotationValueList(TypeMirror.class, registration, "internalResources");
        builder.startReturn();
        builder.startStaticCall(context.getType(List.class), "of");
        Set<String> resourceIds = getResourcesById(resources, context).keySet();
        for (String resourceId : resourceIds) {
            builder.doubleQuote(resourceId);
        }
        builder.end(2);
    }

    static void generateCreateInternalResource(AnnotationMirror registration, VariableElement resourceIdParameter, CodeTreeBuilder builder, ProcessorContext context) {
        List<TypeMirror> resources = ElementUtils.getAnnotationValueList(TypeMirror.class, registration, "internalResources");
        String resourceIdParameterName = resourceIdParameter.getSimpleName().toString();
        if (resources.isEmpty()) {
            generateThrowIllegalArgumentException(builder, context, resourceIdParameterName, Set.of());
        } else {
            builder.startSwitch().string(resourceIdParameterName).end().startBlock();
            Map<String, TypeMirror> resourcesByName = getResourcesById(resources, context);
            for (Map.Entry<String, TypeMirror> e : resourcesByName.entrySet()) {
                builder.startCase().doubleQuote(e.getKey()).end();
                builder.startCaseBlock();
                builder.startReturn().startNew(e.getValue()).end(2);
                builder.end();
            }
            builder.caseDefault();
            builder.startCaseBlock();
            generateThrowIllegalArgumentException(builder, context, resourceIdParameterName, resourcesByName.keySet());
            builder.end(2);
        }
    }

    private static void generateThrowIllegalArgumentException(CodeTreeBuilder builder, ProcessorContext context, String resourceIdParameterName, Set<String> supportedIds) {
        builder.startThrow().startNew(context.getType(IllegalArgumentException.class)).startStaticCall(context.getType(String.class), "format");
        builder.doubleQuote("Unsupported internal resource id %s, supported ids are " + String.join(", ", supportedIds));
        builder.string(resourceIdParameterName);
        builder.end(3);
    }

    private static Map<String, TypeMirror> getResourcesById(List<TypeMirror> resources, ProcessorContext context) {
        Map<String, TypeMirror> res = new LinkedHashMap<>();
        TruffleTypes types = context.getTypes();
        for (TypeMirror resource : resources) {
            AnnotationMirror id = ElementUtils.findAnnotationMirror(ElementUtils.castTypeElement(resource).getAnnotationMirrors(), types.InternalResource_Id);
            String idValue = ElementUtils.getAnnotationValue(String.class, id, "value");
            res.put(idValue, resource);
        }
        return res;
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
            TypeElement te = ElementUtils.getTypeElement(providerFqn);
            if (te == null) {
                providerClassNames.add(providerFqn);
            } else {
                providerClassNames.add(elements.getBinaryName(te).toString());
            }
        }
        Collections.sort(providerClassNames);
        if (!providerClassNames.isEmpty()) {
            try {
                FileObject file = env.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", filename, providerRegistrations.values().toArray(new Element[0]));
                try (PrintWriter out = new PrintWriter(new OutputStreamWriter(file.openOutputStream(), StandardCharsets.UTF_8))) {
                    for (String providerClassName : providerClassNames) {
                        out.println(providerClassName);
                    }
                }
            } catch (IOException e) {
                handleIOError(e, env, providerRegistrations.values().iterator().next());
            }
        }
    }

    static String getScopedName(TypeElement element) {
        StringBuilder name = new StringBuilder();
        Element current = element;
        while (current.getKind().isClass() || current.getKind().isInterface()) {
            if (name.length() > 0) {
                name.insert(0, '.');
            }
            name.insert(0, ElementUtils.getSimpleName((TypeElement) current));
            current = current.getEnclosingElement();
        }
        return name.toString();
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

    static boolean shouldGenerateProviderFiles(Element currentElement) {
        return CompilerFactory.getCompiler(currentElement) instanceof JDTCompiler;
    }
}
