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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

public final class NativeToHotSpotServiceParser extends AbstractNativeServiceParser {

    static final String GENERATE_NATIVE_TO_HOTSPOT_ANNOTATION = "org.graalvm.nativebridge.GenerateNativeToHotSpotBridge";

    private NativeToHotSpotServiceParser(NativeBridgeProcessor processor, TypeCache typeCache) {
        super(processor, typeCache, new NativeToHotSpotEndPointMethodProvider(processor.typeUtils(), typeCache),
                        createConfiguration(typeCache),
                        HotSpotToNativeServiceParser.createConfiguration(processor.typeUtils(), typeCache));
    }

    @Override
    TypeCache getTypeCache() {
        return (TypeCache) super.getTypeCache();
    }

    @Override
    AbstractBridgeGenerator createGenerator(DefinitionData definitionData) {
        return new NativeToHotSpotServiceGenerator(this, getTypeCache(), (ServiceDefinitionData) definitionData);
    }

    static NativeToHotSpotServiceParser create(NativeBridgeProcessor processor) {
        return new NativeToHotSpotServiceParser(processor, new TypeCache(processor));
    }

    static Configuration createConfiguration(NativeTypeCache typeCache) {
        return new Configuration(typeCache.generateNativeToHSBridge, Set.of(typeCache.generateHSToNativeFactory, typeCache.generateNativeToNativeFactory),
                        typeCache.hSPeer,
                        List.of(CodeBuilder.newParameter(typeCache.jniEnv, "env"), CodeBuilder.newParameter(typeCache.jObject, "handle")));
    }

    static final class TypeCache extends NativeTypeCache {

        final DeclaredType alwaysIncluded;
        final DeclaredType booleanSupplier;
        final DeclaredType currentIsolate;
        final DeclaredType jNIEntryPoint;
        final DeclaredType jNIExceptionHandler;
        final DeclaredType jNIExceptionHandlerContext;
        final DeclaredType hotSpotCalls;
        final DeclaredType jNIClassCache;
        final DeclaredType jNIMethod;
        final DeclaredType jValue;
        final DeclaredType notIncludedAutomatically;
        final DeclaredType runtimeException;

        TypeCache(NativeBridgeProcessor processor) {
            super(processor);
            this.alwaysIncluded = (DeclaredType) processor.getType("org.graalvm.nativeimage.c.function.CEntryPoint.AlwaysIncluded");
            this.booleanSupplier = (DeclaredType) processor.getType("java.util.function.BooleanSupplier");
            this.currentIsolate = (DeclaredType) processor.getType("org.graalvm.nativeimage.CurrentIsolate");
            this.jNIEntryPoint = (DeclaredType) processor.getType("org.graalvm.jniutils.JNIEntryPoint");
            this.jNIExceptionHandler = (DeclaredType) processor.getType("org.graalvm.jniutils.JNIExceptionWrapper.ExceptionHandler");
            this.jNIExceptionHandlerContext = (DeclaredType) processor.getType("org.graalvm.jniutils.JNIExceptionWrapper.ExceptionHandlerContext");
            this.hotSpotCalls = (DeclaredType) processor.getType("org.graalvm.jniutils.JNICalls");
            this.jNIClassCache = (DeclaredType) processor.getType("org.graalvm.nativebridge.JNIClassCache");
            this.jNIMethod = (DeclaredType) processor.getType("org.graalvm.jniutils.JNICalls.JNIMethod");
            this.jValue = (DeclaredType) processor.getType("org.graalvm.jniutils.JNI.JValue");
            this.notIncludedAutomatically = (DeclaredType) processor.getType("org.graalvm.nativeimage.c.function.CEntryPoint.NotIncludedAutomatically");
            this.runtimeException = (DeclaredType) processor.getType("java.lang.RuntimeException");
        }
    }

    private static final class NativeToHotSpotEndPointMethodProvider extends AbstractEndPointMethodProvider {

        private final Types types;
        private final TypeCache typeCache;

        NativeToHotSpotEndPointMethodProvider(Types types, TypeCache typeCache) {
            this.types = types;
            this.typeCache = typeCache;
        }

        @Override
        TypeMirror getEntryPointMethodParameterType(MarshallerData marshaller, TypeMirror type) {
            throw new UnsupportedOperationException("Native to HotSpot has no entry point method.");
        }

        @Override
        TypeMirror getEndPointMethodParameterType(MarshallerData marshaller, TypeMirror type) {
            return switch (marshaller.kind) {
                case CUSTOM -> types.getArrayType(types.getPrimitiveType(TypeKind.BYTE));
                case PEER_REFERENCE -> {
                    if (marshaller.sameDirection) {
                        yield typeCache.object;
                    } else {
                        yield types.getPrimitiveType(TypeKind.LONG);
                    }
                }
                case VALUE -> type;
                case REFERENCE -> {
                    if (marshaller.sameDirection) {
                        yield type;
                    } else {
                        TypeMirror longType = types.getPrimitiveType(TypeKind.LONG);
                        yield type.getKind() == TypeKind.ARRAY ? types.getArrayType(longType) : longType;
                    }
                }
            };
        }

        @Override
        String getEntryPointMethodName(MethodData methodData) {
            // No entry point method
            return null;
        }

        @Override
        String getEndPointMethodName(MethodData methodData) {
            return methodData.element.getSimpleName().toString();
        }

        @Override
        List<TypeMirror> getEntryPointSignature(MethodData methodData, boolean hasCustomDispatch) {
            // No entry point method
            return null;
        }

        @Override
        List<TypeMirror> getEndPointSignature(MethodData methodData, TypeMirror serviceType, boolean hasCustomDispatch) {
            List<? extends TypeMirror> parameterTypes = methodData.type.getParameterTypes();
            List<TypeMirror> signature = new ArrayList<>(1 + parameterTypes.size());
            if (!hasCustomDispatch) {
                signature.add(serviceType);
            }
            if (NativeToHotSpotServiceGenerator.needsExplicitIsolateParameter(methodData)) {
                signature.add(types.getPrimitiveType(TypeKind.LONG));
            }
            int marshalledParametersCount = 0;
            for (int i = 0; i < parameterTypes.size(); i++) {
                MarshallerData marshallerData = methodData.getParameterMarshaller(i);
                TypeMirror parameterType = parameterTypes.get(i);
                if (AbstractNativeServiceGenerator.isBinaryMarshallable(marshallerData, parameterType, false)) {
                    marshalledParametersCount++;
                } else {
                    signature.add(getEndPointMethodParameterType(marshallerData, parameterType));
                }
            }
            if (marshalledParametersCount > 0) {
                signature.add(types.getArrayType(types.getPrimitiveType(TypeKind.BYTE)));
            }
            return signature;
        }
    }
}
