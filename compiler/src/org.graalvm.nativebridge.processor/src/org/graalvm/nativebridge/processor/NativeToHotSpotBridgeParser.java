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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

public final class NativeToHotSpotBridgeParser extends AbstractBridgeParser {

    static final String GENERATE_NATIVE_TO_HOTSPOT_ANNOTATION = "org.graalvm.nativebridge.GenerateNativeToHotSpotBridge";

    private final TypeCache typeCache;
    private final NativeToHotSpotBridgeGenerator generator;

    private NativeToHotSpotBridgeParser(NativeBridgeProcessor processor, TypeCache typeCache) {
        super(processor, typeCache, typeCache.generateNativeToHSBridge, typeCache.hSObject);
        this.typeCache = typeCache;
        this.generator = new NativeToHotSpotBridgeGenerator(this, typeCache);
    }

    @Override
    Iterable<List<? extends TypeMirror>> getSubClassReferenceConstructorTypes() {
        return Collections.singleton(Arrays.asList(typeCache.jniEnv, typeCache.jObject));
    }

    @Override
    Iterable<List<? extends TypeMirror>> getHandleReferenceConstructorTypes() {
        return Arrays.asList(Collections.singletonList(typeCache.hSObject),
                        Arrays.asList(typeCache.hSObject, typeCache.jniEnv));
    }

    @Override
    List<TypeMirror> getExceptionHandlerTypes() {
        return Collections.singletonList(typeCache.jNIExceptionHandlerContext);
    }

    @Override
    AbstractBridgeGenerator getGenerator() {
        return generator;
    }

    static NativeToHotSpotBridgeParser create(NativeBridgeProcessor processor) {
        return new NativeToHotSpotBridgeParser(processor, new TypeCache(processor));
    }

    static final class TypeCache extends AbstractTypeCache {

        final DeclaredType jNIExceptionHandler;
        final DeclaredType jNIExceptionHandlerContext;
        final DeclaredType generateNativeToHSBridge;
        final DeclaredType hotSpotCalls;
        final DeclaredType hSObject;
        final DeclaredType jNIClassCache;
        final DeclaredType jNIMethod;
        final DeclaredType jValue;
        final DeclaredType runtimeException;
        final DeclaredType stackValue;

        TypeCache(NativeBridgeProcessor processor) {
            super(processor);
            this.jNIExceptionHandler = (DeclaredType) processor.getType("org.graalvm.jniutils.JNIExceptionWrapper.ExceptionHandler");
            this.jNIExceptionHandlerContext = (DeclaredType) processor.getType("org.graalvm.jniutils.JNIExceptionWrapper.ExceptionHandlerContext");
            this.generateNativeToHSBridge = (DeclaredType) processor.getType(GENERATE_NATIVE_TO_HOTSPOT_ANNOTATION);
            this.hotSpotCalls = (DeclaredType) processor.getType("org.graalvm.jniutils.HotSpotCalls");
            this.hSObject = (DeclaredType) processor.getType("org.graalvm.jniutils.HSObject");
            this.jNIClassCache = (DeclaredType) processor.getType("org.graalvm.nativebridge.JNIClassCache");
            this.jNIMethod = (DeclaredType) processor.getType("org.graalvm.jniutils.HotSpotCalls.JNIMethod");
            this.jValue = (DeclaredType) processor.getType("org.graalvm.jniutils.JNI.JValue");
            this.runtimeException = (DeclaredType) processor.getType("java.lang.RuntimeException");
            this.stackValue = (DeclaredType) processor.getType("org.graalvm.nativeimage.StackValue");
        }
    }
}
