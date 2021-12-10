/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.nativebridge.processor;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

final class HotSpotToNativeBridgeParser extends AbstractBridgeParser {

    static final String GENERATE_HOTSPOT_TO_NATIVE_ANNOTATION = "org.graalvm.nativebridge.GenerateHotSpotToNativeBridge";

    private final TypeCache typeCache;
    private final HotSpotToNativeBridgeGenerator generator;

    private HotSpotToNativeBridgeParser(NativeBridgeProcessor processor, TypeCache typeCache) {
        super(processor, typeCache, typeCache.generateHSToNativeBridge, typeCache.nativeObject);
        this.typeCache = typeCache;
        this.generator = new HotSpotToNativeBridgeGenerator(this, typeCache);
    }

    @Override
    Iterable<List<? extends TypeMirror>> getSubClassReferenceConstructorTypes() {
        return Collections.singleton(Arrays.asList(typeCache.nativeIsolate, types.getPrimitiveType(TypeKind.LONG)));
    }

    @Override
    Iterable<List<? extends TypeMirror>> getHandleReferenceConstructorTypes() {
        return Collections.singleton(Collections.singletonList(typeCache.nativeObject));
    }

    @Override
    List<TypeMirror> getExceptionHandlerTypes() {
        return Arrays.asList(typeCache.jniEnv, typeCache.throwable);
    }

    @Override
    AbstractBridgeGenerator getGenerator() {
        return generator;
    }

    @Override
    DefinitionData createDefinitionData(DeclaredType annotatedType, AnnotationMirror annotation, DeclaredType serviceType, Collection<MethodData> toGenerate,
                    List<? extends VariableElement> annotatedTypeConstructorParams,
                    ExecutableElement delegateAccessor, ExecutableElement receiverAccessor, ExecutableElement exceptionHandler, VariableElement endPointHandle) {
        DeclaredType centryPointPredicate = (DeclaredType) getAnnotationValue(annotation, "include");
        DeclaredType jniConfig = (DeclaredType) getAnnotationValue(annotation, "jniConfig");
        return new HotSpotToNativeDefinitionData(annotatedType, serviceType, toGenerate, annotatedTypeConstructorParams, delegateAccessor, receiverAccessor, exceptionHandler, endPointHandle,
                        centryPointPredicate, jniConfig);
    }

    static HotSpotToNativeBridgeParser create(NativeBridgeProcessor processor) {
        return new HotSpotToNativeBridgeParser(processor, new TypeCache(processor));
    }

    static final class HotSpotToNativeDefinitionData extends DefinitionData {

        final DeclaredType centryPointPredicate;

        HotSpotToNativeDefinitionData(DeclaredType annotatedType, DeclaredType serviceType, Collection<MethodData> toGenerate,
                        List<? extends VariableElement> annotatedTypeConstructorParams, ExecutableElement delegateAccessor, ExecutableElement receiverAccessor,
                        ExecutableElement exceptionHandler, VariableElement endPointHandle, DeclaredType centryPointPredicate, DeclaredType jniConfig) {
            super(annotatedType, serviceType, toGenerate, annotatedTypeConstructorParams, delegateAccessor, receiverAccessor, exceptionHandler, endPointHandle, jniConfig);
            this.centryPointPredicate = centryPointPredicate;
        }
    }

    static class TypeCache extends AbstractTypeCache {

        final DeclaredType centryPoint;
        final DeclaredType generateHSToNativeBridge;
        final DeclaredType hsObject;
        final DeclaredType isolateThreadContext;
        final DeclaredType jniExceptionWrapper;
        final DeclaredType nativeIsolate;
        final DeclaredType nativeIsolateThread;
        final DeclaredType nativeObject;
        final DeclaredType nativeObjectHandles;
        final DeclaredType wordFactory;

        TypeCache(NativeBridgeProcessor processor) {
            super(processor);
            this.centryPoint = (DeclaredType) processor.getType("org.graalvm.nativeimage.c.function.CEntryPoint");
            this.generateHSToNativeBridge = (DeclaredType) processor.getType(GENERATE_HOTSPOT_TO_NATIVE_ANNOTATION);
            this.hsObject = (DeclaredType) processor.getType("org.graalvm.jniutils.HSObject");
            this.isolateThreadContext = (DeclaredType) processor.getType("org.graalvm.nativeimage.c.function.CEntryPoint.IsolateThreadContext");
            this.jniExceptionWrapper = (DeclaredType) processor.getType("org.graalvm.jniutils.JNIExceptionWrapper");
            this.nativeIsolate = (DeclaredType) processor.getType("org.graalvm.nativebridge.NativeIsolate");
            this.nativeIsolateThread = (DeclaredType) processor.getType("org.graalvm.nativebridge.NativeIsolateThread");
            this.nativeObject = (DeclaredType) processor.getType("org.graalvm.nativebridge.NativeObject");
            this.nativeObjectHandles = (DeclaredType) processor.getType("org.graalvm.nativebridge.NativeObjectHandles");
            this.wordFactory = (DeclaredType) processor.getType("org.graalvm.word.WordFactory");
        }
    }
}
