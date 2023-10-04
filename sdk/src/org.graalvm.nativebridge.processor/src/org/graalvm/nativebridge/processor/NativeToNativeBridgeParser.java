/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.graalvm.nativebridge.processor.HotSpotToNativeBridgeParser.HotSpotToNativeDefinitionData;
import org.graalvm.nativebridge.processor.HotSpotToNativeBridgeParser.HotSpotToNativeEndPointMethodProvider;
import org.graalvm.nativebridge.processor.HotSpotToNativeBridgeParser.TypeCache;

final class NativeToNativeBridgeParser extends AbstractBridgeParser {

    static final String GENERATE_NATIVE_TO_NATIVE_ANNOTATION = "org.graalvm.nativebridge.GenerateNativeToNativeBridge";

    private NativeToNativeBridgeParser(NativeBridgeProcessor processor, TypeCache typeCache) {
        super(processor, typeCache, new HotSpotToNativeEndPointMethodProvider(processor.env().getTypeUtils(), typeCache),
                        createConfiguration(processor.env().getTypeUtils(), typeCache),
                        NativeToHotSpotBridgeParser.createConfiguration(typeCache));
    }

    @Override
    AbstractBridgeGenerator createGenerator(DefinitionData definitionData) {
        return new NativeToNativeBridgeGenerator(this, (TypeCache) typeCache, definitionData);
    }

    @Override
    DefinitionData createDefinitionData(DeclaredType annotatedType, AnnotationMirror annotation, DeclaredType serviceType,
                    Collection<MethodData> toGenerate, List<? extends VariableElement> annotatedTypeConstructorParams,
                    ExecutableElement customDispatchAccessor, ExecutableElement customReceiverAccessor,
                    VariableElement endPointHandle, DeclaredType jniConfig, MarshallerData throwableMarshaller,
                    Set<DeclaredType> annotationsToIgnore, Set<DeclaredType> annotationsForMarshallerLookup) {
        DeclaredType centryPointPredicate = (DeclaredType) getAnnotationValue(annotation, "include");
        return new HotSpotToNativeDefinitionData(annotatedType, serviceType, toGenerate, annotatedTypeConstructorParams, customDispatchAccessor, customReceiverAccessor,
                        endPointHandle, centryPointPredicate, jniConfig, throwableMarshaller, annotationsToIgnore, annotationsForMarshallerLookup);
    }

    static NativeToNativeBridgeParser create(NativeBridgeProcessor processor) {
        return new NativeToNativeBridgeParser(processor, new TypeCache(processor));
    }

    static Configuration createConfiguration(Types types, AbstractTypeCache typeCache) {
        return new Configuration(typeCache.generateNativeToNativeBridge, typeCache.nativeObject,
                        Collections.singleton(Arrays.asList(typeCache.nativeIsolate, types.getPrimitiveType(TypeKind.LONG))),
                        Collections.singleton(Collections.singletonList(typeCache.nativeObject)));
    }
}
