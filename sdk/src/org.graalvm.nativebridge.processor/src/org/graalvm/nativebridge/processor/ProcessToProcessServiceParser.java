/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.Collection;
import java.util.List;
import java.util.Set;

final class ProcessToProcessServiceParser extends AbstractServiceParser {

    static final String GENERATE_FOREIGN_PROCESS_ANNOTATION = "org.graalvm.nativebridge.GenerateProcessToProcessBridge";

    private ProcessToProcessServiceParser(NativeBridgeProcessor processor, ProcessToProcessTypeCache typeCache, Configuration configuration) {
        super(processor, typeCache, new ForeignProcessEndPointMethodProvider(typeCache), configuration, configuration);
    }

    @Override
    ProcessToProcessTypeCache getTypeCache() {
        return (ProcessToProcessTypeCache) super.getTypeCache();
    }

    @Override
    ServiceDefinitionData createDefinitionData(DeclaredType annotatedType, AnnotationMirror annotation, DeclaredType serviceType, DeclaredType peerType, DeclaredType factoryClass, boolean mutable,
                    Collection<MethodData> toGenerate, List<? extends VariableElement> annotatedTypeConstructorParams, List<? extends CodeBuilder.Parameter> peerConstructorParams,
                    ExecutableElement customDispatchAccessor, ExecutableElement customReceiverAccessor, DeclaredType marshallerConfig, MarshallerData throwableMarshaller,
                    Set<DeclaredType> annotationsToIgnore,
                    Set<DeclaredType> annotationsForMarshallerLookup) {
        verifyFactoryClass(annotatedType, annotation, factoryClass);
        return new ServiceDefinitionData(annotatedType, serviceType, peerType, factoryClass, mutable, toGenerate, annotatedTypeConstructorParams,
                        peerConstructorParams, customDispatchAccessor, customReceiverAccessor, marshallerConfig, throwableMarshaller, annotationsToIgnore,
                        annotationsForMarshallerLookup);
    }

    private void verifyFactoryClass(DeclaredType annotatedType, AnnotationMirror annotation, DeclaredType factoryClass) {
        if (factoryClass == null) {
            return;
        }
        TypeElement annotatedElement = (TypeElement) annotatedType.asElement();
        TypeElement factoryElement = (TypeElement) factoryClass.asElement();
        AnnotationMirror factoryRegistration = processor.getAnnotation(factoryElement, getTypeCache().generateProcessToProcessFactory);
        if (factoryRegistration == null) {
            return;
        }
        List<DeclaredType> services = AbstractProcessor.getAnnotationValueList(factoryRegistration, "services", DeclaredType.class);
        services.add((DeclaredType) getAnnotationValue(factoryRegistration, "initialService"));
        for (DeclaredType service : services) {
            if (types.isSameType(annotatedType, service)) {
                return;
            }
        }
        CharSequence serviceName = Utilities.getTypeName(annotatedType);
        CharSequence factoryName = Utilities.getTypeName(factoryClass);
        CharSequence factoryRegistrationName = Utilities.getTypeName(getTypeCache().generateProcessToProcessFactory);
        emitError(annotatedElement, annotation, "Invalid service registration: The service definition `%s` is not listed in `@%s.services` of `%s`.%n" +
                        "To resolve this, include `%s` in the `services` attribute of `@%s` in `%s`.",
                        serviceName, factoryRegistrationName, factoryName, serviceName, factoryRegistrationName, factoryName);
    }

    @Override
    AbstractBridgeGenerator createGenerator(DefinitionData definitionData) {
        return new ProcessToProcessServiceGenerator(this, getTypeCache(), (ServiceDefinitionData) definitionData);
    }

    static ProcessToProcessServiceParser create(NativeBridgeProcessor processor) {
        ProcessToProcessTypeCache typeCache = new ProcessToProcessTypeCache(processor);
        Configuration configuration = new Configuration(typeCache.generateProcessToProcessBridge, Set.of(typeCache.generateProcessToProcessFactory),
                        typeCache.processPeer,
                        List.of(CodeBuilder.newParameter(typeCache.processIsolate, "isolate"), CodeBuilder.newParameter(processor.typeUtils().getPrimitiveType(TypeKind.LONG), "handle")));
        return new ProcessToProcessServiceParser(processor, typeCache, configuration);
    }

    static final class ForeignProcessEndPointMethodProvider extends AbstractEndPointMethodProvider {

        private final ProcessToProcessTypeCache typeCache;

        ForeignProcessEndPointMethodProvider(ProcessToProcessTypeCache typeCache) {
            this.typeCache = typeCache;
        }

        @Override
        String getEntryPointMethodName(MethodData methodData) {
            // No entry point method
            return null;
        }

        @Override
        List<TypeMirror> getEntryPointSignature(MethodData methodData, boolean hasCustomDispatch) {
            throw new UnsupportedOperationException("Foreign process bridge has no entry point method.");
        }

        @Override
        TypeMirror getEntryPointMethodParameterType(MarshallerData marshaller, TypeMirror type) {
            throw new UnsupportedOperationException("Foreign process bridge has no entry point method.");
        }

        @Override
        String getEndPointMethodName(MethodData methodData) {
            String name = methodData.element.getSimpleName().toString();
            if (methodData.hasOverload()) {
                name = name + '$' + methodData.overloadId;
            }
            return name;
        }

        @Override
        List<TypeMirror> getEndPointSignature(MethodData methodData, TypeMirror serviceType, boolean hasCustomDispatch) {
            return List.of(typeCache.processIsolate, typeCache.binaryInput);
        }

        @Override
        TypeMirror getEndPointMethodParameterType(MarshallerData marshaller, TypeMirror type) {
            throw new UnsupportedOperationException("Foreign process bridge has all parameters marshalled.");
        }
    }
}
