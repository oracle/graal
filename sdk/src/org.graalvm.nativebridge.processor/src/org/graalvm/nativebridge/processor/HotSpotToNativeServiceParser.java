/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

final class HotSpotToNativeServiceParser extends AbstractNativeServiceParser {

    static final String GENERATE_HOTSPOT_TO_NATIVE_ANNOTATION = "org.graalvm.nativebridge.GenerateHotSpotToNativeBridge";

    private HotSpotToNativeServiceParser(NativeBridgeProcessor processor, HotSpotToNativeTypeCache typeCache) {
        super(processor, typeCache, new HotSpotToNativeEndPointMethodProvider(processor.typeUtils(), typeCache),
                        createConfiguration(processor.env().getTypeUtils(), typeCache),
                        NativeToHotSpotServiceParser.createConfiguration(typeCache));
    }

    @Override
    HotSpotToNativeTypeCache getTypeCache() {
        return (HotSpotToNativeTypeCache) super.getTypeCache();
    }

    @Override
    AbstractBridgeGenerator createGenerator(DefinitionData definitionData) {
        return new HotSpotToNativeServiceGenerator(this, getTypeCache(), (HotSpotToNativeServiceDefinitionData) definitionData);
    }

    @Override
    ServiceDefinitionData createDefinitionData(DeclaredType annotatedType, AnnotationMirror annotation, DeclaredType serviceType,
                    DeclaredType peerType, DeclaredType factoryClass, boolean mutable, Collection<MethodData> toGenerate,
                    List<? extends VariableElement> annotatedTypeConstructorParams, List<? extends CodeBuilder.Parameter> peerConstructorParams,
                    ExecutableElement customDispatchAccessor, ExecutableElement customReceiverAccessor, DeclaredType marshallerConfig,
                    MarshallerData throwableMarshaller, Set<DeclaredType> annotationsToIgnore, Set<DeclaredType> annotationsForMarshallerLookup) {
        DeclaredType centryPointPredicate = (DeclaredType) getAnnotationValue(getFactoryRegistration(factoryClass), "include");
        return new HotSpotToNativeServiceDefinitionData(annotatedType, serviceType, peerType, factoryClass, mutable, toGenerate, annotatedTypeConstructorParams,
                        peerConstructorParams, customDispatchAccessor, customReceiverAccessor, centryPointPredicate, marshallerConfig,
                        throwableMarshaller, annotationsToIgnore, annotationsForMarshallerLookup);
    }

    static HotSpotToNativeServiceParser create(NativeBridgeProcessor processor) {
        return new HotSpotToNativeServiceParser(processor, new HotSpotToNativeTypeCache(processor));
    }

    static Configuration createConfiguration(Types types, NativeTypeCache typeCache) {
        return new Configuration(typeCache.generateHSToNativeBridge, Set.of(typeCache.generateHSToNativeFactory),
                        typeCache.nativePeer,
                        List.of(CodeBuilder.newParameter(typeCache.nativeIsolate, "isolate"), CodeBuilder.newParameter(types.getPrimitiveType(TypeKind.LONG), "handle")));
    }

    static final class HotSpotToNativeServiceDefinitionData extends ServiceDefinitionData {

        final DeclaredType centryPointPredicate;

        HotSpotToNativeServiceDefinitionData(DeclaredType annotatedType, DeclaredType serviceType, DeclaredType peerType, DeclaredType factory,
                        boolean mutable, Collection<MethodData> toGenerate, List<? extends VariableElement> annotatedTypeConstructorParams,
                        List<? extends CodeBuilder.Parameter> peerConstructorParams, ExecutableElement delegateAccessor,
                        ExecutableElement receiverAccessor, DeclaredType centryPointPredicate, DeclaredType marshallerConfig,
                        MarshallerData throwableMarshaller, Set<DeclaredType> ignoreAnnotations, Set<DeclaredType> marshallerAnnotations) {
            super(annotatedType, serviceType, peerType, factory, mutable, toGenerate, annotatedTypeConstructorParams, peerConstructorParams, delegateAccessor, receiverAccessor,
                            marshallerConfig, throwableMarshaller, ignoreAnnotations, marshallerAnnotations);
            this.centryPointPredicate = centryPointPredicate;
        }
    }

    static final class HotSpotToNativeEndPointMethodProvider extends AbstractEndPointMethodProvider {

        private final Types types;
        private final HotSpotToNativeTypeCache typeCache;

        HotSpotToNativeEndPointMethodProvider(Types types, HotSpotToNativeTypeCache typeCache) {
            this.types = types;
            this.typeCache = typeCache;
        }

        @Override
        TypeMirror getEntryPointMethodParameterType(MarshallerData marshaller, TypeMirror type) {
            return switch (marshaller.kind) {
                case CUSTOM -> types.getArrayType(types.getPrimitiveType(TypeKind.BYTE));
                case PEER_REFERENCE -> {
                    if (marshaller.sameDirection) {
                        yield types.getPrimitiveType(TypeKind.LONG);
                    } else {
                        yield typeCache.object;
                    }
                }
                case VALUE -> type;
                case REFERENCE -> {
                    if (marshaller.sameDirection) {
                        TypeMirror longType = types.getPrimitiveType(TypeKind.LONG);
                        yield type.getKind() == TypeKind.ARRAY ? types.getArrayType(longType) : longType;
                    } else {
                        yield type;
                    }
                }
            };
        }

        @Override
        TypeMirror getEndPointMethodParameterType(MarshallerData marshaller, TypeMirror type) {
            return Utilities.jniTypeForJavaType(getEntryPointMethodParameterType(marshaller, type), types, typeCache);
        }

        @Override
        String getEntryPointMethodName(MethodData methodData) {
            return methodData.element.getSimpleName() + "0";
        }

        @Override
        String getEndPointMethodName(MethodData methodData) {
            return methodData.element.getSimpleName().toString();
        }

        @Override
        List<TypeMirror> getEntryPointSignature(MethodData methodData, boolean hasCustomDispatch) {
            List<TypeMirror> params = new ArrayList<>();
            PrimitiveType longType = types.getPrimitiveType(TypeKind.LONG);
            params.add(longType);
            params.add(longType);
            int nonReceiverParameterStart = hasCustomDispatch ? 1 : 0;
            List<? extends TypeMirror> parameterTypes = methodData.type.getParameterTypes();
            boolean hasMarshallerData = false;
            for (int i = nonReceiverParameterStart; i < parameterTypes.size(); i++) {
                MarshallerData marshallerData = methodData.getParameterMarshaller(i);
                TypeMirror parameterType = parameterTypes.get(i);
                if (AbstractNativeServiceGenerator.isBinaryMarshallable(marshallerData, parameterType, true)) {
                    hasMarshallerData = true;
                } else {
                    TypeMirror nativeMethodParameter = getEntryPointMethodParameterType(marshallerData, parameterType);
                    params.add(nativeMethodParameter);
                }
            }
            if (hasMarshallerData) {
                params.add(types.getArrayType(types.getPrimitiveType(TypeKind.BYTE)));
            }
            return params;
        }

        @Override
        List<TypeMirror> getEndPointSignature(MethodData methodData, TypeMirror serviceType, boolean hasCustomDispatch) {
            List<TypeMirror> params = new ArrayList<>();
            params.add(typeCache.jniEnv);
            params.add(typeCache.jClass);
            params.add(types.getPrimitiveType(TypeKind.LONG));
            params.add(types.getPrimitiveType(TypeKind.LONG));
            List<? extends TypeMirror> methodParameters = methodData.type.getParameterTypes();
            int parameterStartIndex = hasCustomDispatch ? 1 : 0;
            int marshalledDataCount = 0;
            for (int i = parameterStartIndex; i < methodParameters.size(); i++) {
                MarshallerData marshallerData = methodData.getParameterMarshaller(i);
                TypeMirror parameterType = methodParameters.get(i);
                if (AbstractNativeServiceGenerator.isBinaryMarshallable(marshallerData, parameterType, true)) {
                    marshalledDataCount++;
                } else {
                    TypeMirror cType = getEndPointMethodParameterType(marshallerData, parameterType);
                    params.add(cType);
                }
            }
            if (marshalledDataCount > 0) {
                params.add(typeCache.jByteArray);
            }
            return params;
        }
    }
}
