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

import java.util.Arrays;
import java.util.Collections;

import javax.lang.model.type.DeclaredType;

public final class NativeToHotSpotBridgeParser extends AbstractBridgeParser {

    static final String GENERATE_NATIVE_TO_HOTSPOT_ANNOTATION = "org.graalvm.nativebridge.GenerateNativeToHotSpotBridge";

    private NativeToHotSpotBridgeParser(NativeBridgeProcessor processor, TypeCache typeCache) {
        super(processor, typeCache,
                        createConfiguration(typeCache),
                        HotSpotToNativeBridgeParser.createConfiguration(processor.env().getTypeUtils(), typeCache));
    }

    @Override
    AbstractBridgeGenerator createGenerator(DefinitionData definitionData) {
        return new NativeToHotSpotBridgeGenerator(this, (TypeCache) typeCache, definitionData);
    }

    static NativeToHotSpotBridgeParser create(NativeBridgeProcessor processor) {
        return new NativeToHotSpotBridgeParser(processor, new TypeCache(processor));
    }

    static Configuration createConfiguration(AbstractTypeCache typeCache) {
        return new Configuration(typeCache.generateNativeToHSBridge, typeCache.hSObject,
                        Collections.singleton(Arrays.asList(typeCache.jniEnv, typeCache.jObject)),
                        Arrays.asList(Collections.singletonList(typeCache.hSObject), Arrays.asList(typeCache.hSObject, typeCache.jniEnv)));
    }

    static final class TypeCache extends AbstractTypeCache {

        final DeclaredType currentIsolate;
        final DeclaredType jNIEntryPoint;
        final DeclaredType jNIExceptionHandler;
        final DeclaredType jNIExceptionHandlerContext;
        final DeclaredType hotSpotCalls;
        final DeclaredType hSObject;
        final DeclaredType jNIClassCache;
        final DeclaredType jNIMethod;
        final DeclaredType jValue;
        final DeclaredType runtimeException;

        TypeCache(NativeBridgeProcessor processor) {
            super(processor);
            this.currentIsolate = (DeclaredType) processor.getType("org.graalvm.nativeimage.CurrentIsolate");
            this.jNIEntryPoint = (DeclaredType) processor.getType("org.graalvm.jniutils.JNIEntryPoint");
            this.jNIExceptionHandler = (DeclaredType) processor.getType("org.graalvm.jniutils.JNIExceptionWrapper.ExceptionHandler");
            this.jNIExceptionHandlerContext = (DeclaredType) processor.getType("org.graalvm.jniutils.JNIExceptionWrapper.ExceptionHandlerContext");
            this.hotSpotCalls = (DeclaredType) processor.getType("org.graalvm.jniutils.JNICalls");
            this.hSObject = (DeclaredType) processor.getType("org.graalvm.jniutils.HSObject");
            this.jNIClassCache = (DeclaredType) processor.getType("org.graalvm.nativebridge.JNIClassCache");
            this.jNIMethod = (DeclaredType) processor.getType("org.graalvm.jniutils.JNICalls.JNIMethod");
            this.jValue = (DeclaredType) processor.getType("org.graalvm.jniutils.JNI.JValue");
            this.runtimeException = (DeclaredType) processor.getType("java.lang.RuntimeException");
        }
    }
}
