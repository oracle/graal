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
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.ElementFilter;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.graalvm.nativebridge.processor.AbstractProcessor.getAnnotationValueList;

final class ProcessToProcessFactoryParser extends AbstractFactoryParser {

    static final String GENERATE_FOREIGN_PROCESS_ANNOTATION = "org.graalvm.nativebridge.GenerateProcessToProcessFactory";

    private ProcessToProcessFactoryParser(NativeBridgeProcessor processor, ProcessToProcessTypeCache typeCache) {
        super(processor, typeCache, typeCache.generateProcessToProcessFactory, typeCache.generateProcessToProcessBridge);
    }

    @Override
    ProcessToProcessTypeCache getTypeCache() {
        return (ProcessToProcessTypeCache) super.getTypeCache();
    }

    @Override
    FactoryDefinitionData createDefinition(DeclaredType annotatedType, AnnotationMirror annotation, DeclaredType initialService, DeclaredType implementation,
                    DeclaredType marshallerConfig, MarshallerData throwableMarshaller) {
        List<DeclaredType> services = getServices(annotatedType, annotation);
        boolean containsInitialService = false;
        Set<String> duplicates = new HashSet<>();
        for (DeclaredType service : services) {
            if (types.isSameType(initialService, service)) {
                containsInitialService = true;
            }
            String serviceName = ((TypeElement) service.asElement()).getQualifiedName().toString();
            if (!duplicates.add(serviceName)) {
                CharSequence generateFactoryName = Utilities.getTypeName(getTypeCache().generateProcessToProcessFactory);
                CharSequence factoryName = Utilities.getTypeName(annotatedType);
                emitError(annotatedType.asElement(), annotation, "Duplicate entry `%s` found in `@%s.services` for `%s` registration.%n" +
                                "To resolve this, remove the duplicate.", serviceName, generateFactoryName, factoryName);
            }
        }
        if (!containsInitialService) {
            services.add(initialService);
        }
        return new ProcessToProcessFactoryDefinitionData(annotatedType, initialService, implementation, marshallerConfig, throwableMarshaller, services);
    }

    @Override
    AbstractBridgeGenerator createGenerator(DefinitionData definitionData) {
        return new ProcessToProcessFactoryGenerator(this, (ProcessToProcessFactoryDefinitionData) definitionData, getTypeCache());
    }

    static final class ProcessToProcessFactoryDefinitionData extends FactoryDefinitionData {

        List<DeclaredType> services;

        ProcessToProcessFactoryDefinitionData(DeclaredType annotatedType, DeclaredType initialService, DeclaredType implementation,
                        DeclaredType marshallerConfig, MarshallerData throwableMarshaller,
                        List<DeclaredType> services) {
            super(annotatedType, initialService, implementation, marshallerConfig, throwableMarshaller);
            this.services = Objects.requireNonNull(services, "Services must be non-null");
        }
    }

    static ProcessToProcessFactoryParser create(NativeBridgeProcessor processor) {
        ProcessToProcessTypeCache typeCache = new ProcessToProcessTypeCache(processor);
        return new ProcessToProcessFactoryParser(processor, typeCache);
    }

    private List<DeclaredType> getServices(DeclaredType annotatedType, AnnotationMirror annotation) {
        List<DeclaredType> services = getAnnotationValueList(annotation, "services", DeclaredType.class);
        Element annotatedElement = annotatedType.asElement();
        ProcessToProcessTypeCache cache = getTypeCache();
        for (DeclaredType service : services) {
            Element serviceElement = service.asElement();
            if (isHandWritten(serviceElement)) {
                continue;
            }
            AnnotationMirror serviceRegistrationAnnotation = processor.getAnnotation(serviceElement, cache.generateProcessToProcessBridge);
            if (serviceRegistrationAnnotation == null) {
                CharSequence generateFactoryName = Utilities.getTypeName(cache.generateProcessToProcessFactory);
                CharSequence generateServiceName = Utilities.getTypeName(cache.generateProcessToProcessBridge);
                CharSequence serviceName = Utilities.getTypeName(service);
                emitError(annotatedElement, annotation, "Invalid service registration: `%s` is listed in `@%s.services` but is missing the required `@%s` annotation.%n" +
                                "To resolve this, annotate `%s` with `@%s`.",
                                serviceName, generateFactoryName, generateServiceName, serviceName, generateServiceName);
            } else if (!types.isSameType(annotatedType, (DeclaredType) getAnnotationValue(serviceRegistrationAnnotation, "factory"))) {
                CharSequence generateFactoryName = Utilities.getTypeName(cache.generateProcessToProcessFactory);
                CharSequence generateServiceName = Utilities.getTypeName(cache.generateProcessToProcessBridge);
                CharSequence annotatedName = Utilities.getTypeName(annotatedType);
                CharSequence serviceName = Utilities.getTypeName(service);
                emitError(annotatedElement, annotation, "Configuration mismatch: `%s` is listed in `@%s.services`, but its `@%s.factory` attribute does not match `%s`.%n" +
                                "To resolve this, set `@%s.factory = %s` in `%s`.",
                                serviceName, generateFactoryName, generateServiceName, annotatedName, generateServiceName, annotatedName, serviceName);
            }
        }
        return services;
    }

    boolean isHandWritten(Element service) {
        ProcessToProcessTypeCache cache = getTypeCache();
        if (processor.getAnnotation(service, cache.generateProcessToProcessBridge) == null) {
            for (ExecutableElement method : ElementFilter.methodsIn(service.getEnclosedElements())) {
                if ("dispatch".contentEquals(method.getSimpleName()) && method.getModifiers().contains(Modifier.STATIC)) {
                    List<? extends VariableElement> parameters = method.getParameters();
                    if (parameters.size() == 3 &&
                                    parameters.get(0).asType().equals(types.getPrimitiveType(TypeKind.INT)) &&
                                    parameters.get(1).asType().equals(cache.processIsolate) &&
                                    parameters.get(2).asType().equals(cache.binaryInput) &&
                                    method.getReturnType().equals(cache.binaryOutput)) {
                        // Handwritten service
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
