/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge.processor;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.ElementFilter;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

abstract class AbstractFactoryParser extends AbstractBridgeParser {

    private final DeclaredType factoryAnnotationType;
    private final DeclaredType serviceAnnotationType;

    AbstractFactoryParser(NativeBridgeProcessor processor, AbstractTypeCache typeCache, DeclaredType factoryAnnotationType, DeclaredType serviceAnnotationType) {
        super(processor, typeCache, factoryAnnotationType);
        this.factoryAnnotationType = Objects.requireNonNull(factoryAnnotationType, "FactoryAnnotationType must be non-null");
        this.serviceAnnotationType = Objects.requireNonNull(serviceAnnotationType, "ServiceAnnotationType must be non-null");
    }

    abstract FactoryDefinitionData createDefinition(DeclaredType annotatedType, AnnotationMirror annotation, DeclaredType initialService,
                    DeclaredType implementation, DeclaredType marshallerConfig, MarshallerData throwableMarshaller);

    @Override
    FactoryDefinitionData parseElement(Element element) {
        TypeElement annotatedElement = (TypeElement) element;
        DeclaredType annotatedType = (DeclaredType) annotatedElement.asType();
        AnnotationMirror handledAnnotation = processor.getAnnotation(element, factoryAnnotationType);
        DeclaredType marshallers = findMarshallerConfigClass(annotatedElement, handledAnnotation);
        DeclaredType initialService = findInitialService(annotatedElement, handledAnnotation);
        DeclaredType implementation = findImplementation(annotatedElement, handledAnnotation, initialService);
        MarshallerData throwableMarshaller = MarshallerData.marshalled(getTypeCache().throwable, null, null, Collections.emptyList());
        return createDefinition(annotatedType, handledAnnotation, initialService, implementation, marshallers, throwableMarshaller);
    }

    static class FactoryDefinitionData extends DefinitionData {

        final DeclaredType initialService;
        final DeclaredType implementation;
        final DeclaredType marshallerConfig;

        FactoryDefinitionData(DeclaredType annotatedType, DeclaredType initialService, DeclaredType implementation,
                        DeclaredType marshallerConfig, MarshallerData throwableMarshaller) {
            super(annotatedType, throwableMarshaller);
            this.initialService = Objects.requireNonNull(initialService, "InitialService must be non-null.");
            this.implementation = implementation;
            this.marshallerConfig = Objects.requireNonNull(marshallerConfig, "MarshallerConfig must be non-null.");
        }
    }

    private DeclaredType findMarshallerConfigClass(Element element, AnnotationMirror handledAnnotation) {
        DeclaredType marshallers = (DeclaredType) getAnnotationValue(handledAnnotation, "marshallers");
        ExecutableElement getInstanceMethod = null;
        for (ExecutableElement executableElement : ElementFilter.methodsIn(marshallers.asElement().getEnclosedElements())) {
            Set<Modifier> modifiers = executableElement.getModifiers();
            if (!modifiers.contains(Modifier.STATIC) || modifiers.contains(Modifier.PRIVATE)) {
                continue;
            }
            if (!"getInstance".contentEquals(executableElement.getSimpleName())) {
                continue;
            }
            if (!executableElement.getParameters().isEmpty()) {
                continue;
            }
            if (!types.isSameType(getTypeCache().marshallerConfig, executableElement.getReturnType())) {
                continue;
            }
            getInstanceMethod = executableElement;
            break;
        }
        if (getInstanceMethod == null) {
            emitError(element, handledAnnotation, "Marshallers config must have a non-private static `getInstance()` method returning `MarshallerConfig`.%n" +
                            "The `getInstance` method is used by the generated a code to look up marshallers.%n" +
                            "To fix this add `static MarshallerConfig getInstance() { return INSTANCE;}` into `%s`.",
                            marshallers.asElement().getSimpleName());
        }
        return marshallers;
    }

    private DeclaredType findInitialService(TypeElement element, AnnotationMirror handledAnnotation) {
        DeclaredType initialService = (DeclaredType) getAnnotationValue(handledAnnotation, "initialService");
        PackageElement factoryPackage = Utilities.getEnclosingPackageElement(element);
        PackageElement initialServicePackage = Utilities.getEnclosingPackageElement((TypeElement) initialService.asElement());
        if (!factoryPackage.equals(initialServicePackage)) {
            CharSequence factoryName = Utilities.getTypeName(element.asType());
            CharSequence initialServiceName = Utilities.getTypeName(initialService);
            emitError(element, handledAnnotation, "Mismatched package definitions: `%s` and initial service `%s` must reside in the same package.%n" +
                            "To resolve this issue, move `%s` to the package `%s`.", factoryName, initialServiceName, initialServiceName, factoryPackage.getQualifiedName());
        }
        if (processor.getAnnotation(initialService.asElement(), serviceAnnotationType) == null) {
            CharSequence initialServiceName = Utilities.getTypeName(initialService);
            CharSequence generateName = Utilities.getTypeName(serviceAnnotationType);
            emitError(element, handledAnnotation, "Missing required annotation: Initial service `%s` must be annotated with `@%s`.%n" +
                            "To resolve this, add `@%s` to `%s`.", initialServiceName, generateName, generateName, initialServiceName);
        }
        return initialService;
    }

    private DeclaredType findImplementation(Element element, AnnotationMirror handledAnnotation, DeclaredType initialService) {
        AnnotationMirror serviceRegistration = processor.getAnnotation(initialService.asElement(), serviceAnnotationType);
        if (serviceRegistration == null) {
            return null;
        }
        DeclaredType implementation = (DeclaredType) getAnnotationValue(serviceRegistration, "implementation");
        if (implementation == null) {
            CharSequence initialServiceName = Utilities.getTypeName(initialService);
            emitError(element, handledAnnotation, "The initial service `%s` must have an `implementation` attribute set to a class that implements the service in the isolate.%n" +
                            "To resolve this, specify an `implementation` attribute in the definition of `%s`.",
                            initialServiceName, initialServiceName);
        } else {
            if (Utilities.findCustomObjectFactory(implementation) == null) {
                CharSequence implementationName = Utilities.getTypeName(implementation);
                emitError(element, handledAnnotation,
                                "The initial service implementation `%s` must have either an accessible no-argument static method `getInstance()` or a no-argument constructor.%n" +
                                                "To resolve this, add a `%s static getInstance()` method to `%s`, or ensure it has a no-argument constructor.",
                                implementationName, implementationName, implementationName);
            }
        }
        return implementation;
    }
}
