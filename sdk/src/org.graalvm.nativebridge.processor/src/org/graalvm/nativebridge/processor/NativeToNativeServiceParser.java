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
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Types;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.graalvm.nativebridge.processor.HotSpotToNativeServiceParser.HotSpotToNativeServiceDefinitionData;
import org.graalvm.nativebridge.processor.HotSpotToNativeServiceParser.HotSpotToNativeEndPointMethodProvider;

final class NativeToNativeServiceParser extends AbstractNativeServiceParser {

    static final String GENERATE_NATIVE_TO_NATIVE_ANNOTATION = "org.graalvm.nativebridge.GenerateNativeToNativeBridge";

    private NativeToNativeServiceParser(NativeBridgeProcessor processor, HotSpotToNativeTypeCache typeCache) {
        super(processor, typeCache, new HotSpotToNativeEndPointMethodProvider(processor.typeUtils(), typeCache),
                        createConfiguration(processor.typeUtils(), typeCache),
                        NativeToHotSpotServiceParser.createConfiguration(typeCache));
    }

    @Override
    HotSpotToNativeTypeCache getTypeCache() {
        return (HotSpotToNativeTypeCache) super.getTypeCache();
    }

    @Override
    AbstractBridgeGenerator createGenerator(DefinitionData definitionData) {
        return new NativeToNativeServiceGenerator(this, getTypeCache(), (HotSpotToNativeServiceDefinitionData) definitionData);
    }

    @Override
    ServiceDefinitionData createDefinitionData(DeclaredType annotatedType, AnnotationMirror annotation, DeclaredType serviceType,
                    DeclaredType peerType, DeclaredType factoryClass, boolean mutable, Collection<MethodData> toGenerate,
                    List<? extends VariableElement> annotatedTypeConstructorParams, List<? extends CodeBuilder.Parameter> peerConstructorParams,
                    ExecutableElement customDispatchAccessor, ExecutableElement customReceiverAccessor, DeclaredType marshallerConfig,
                    MarshallerData throwableMarshaller, Set<DeclaredType> annotationsToIgnore, Set<DeclaredType> annotationsForMarshallerLookup) {
        DeclaredType centryPointPredicate = (DeclaredType) getAnnotationValue(getFactoryRegistration(factoryClass), "include");
        return new HotSpotToNativeServiceDefinitionData(annotatedType, serviceType, peerType, factoryClass, mutable, toGenerate, annotatedTypeConstructorParams, peerConstructorParams,
                        customDispatchAccessor, customReceiverAccessor, centryPointPredicate, marshallerConfig, throwableMarshaller, annotationsToIgnore, annotationsForMarshallerLookup);
    }

    static NativeToNativeServiceParser create(NativeBridgeProcessor processor) {
        return new NativeToNativeServiceParser(processor, new HotSpotToNativeTypeCache(processor));
    }

    static Configuration createConfiguration(Types types, NativeTypeCache typeCache) {
        return new Configuration(typeCache.generateNativeToNativeBridge, Set.of(typeCache.generateNativeToNativeFactory),
                        typeCache.nativePeer,
                        List.of(CodeBuilder.newParameter(typeCache.nativeIsolate, "isolate"), CodeBuilder.newParameter(types.getPrimitiveType(TypeKind.LONG), "handle")));
    }
}
