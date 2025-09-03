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

import javax.lang.model.type.DeclaredType;
import java.util.Objects;

abstract class AbstractNativeServiceParser extends AbstractServiceParser {

    AbstractNativeServiceParser(NativeBridgeProcessor processor, NativeTypeCache typeCache, AbstractEndPointMethodProvider endPointMethodProvider,
                    Configuration myConfiguration, Configuration otherConfiguration) {
        super(processor, typeCache, endPointMethodProvider, Objects.requireNonNull(myConfiguration), Objects.requireNonNull(otherConfiguration));

    }

    abstract static class NativeTypeCache extends AbstractTypeCache {
        final DeclaredType cCharPointer;
        final DeclaredType cCharPointerBinaryOutput;
        final DeclaredType generateHSToNativeBridge;
        final DeclaredType generateHSToNativeFactory;
        final DeclaredType generateNativeToHSBridge;
        final DeclaredType generateNativeToNativeBridge;
        final DeclaredType generateNativeToNativeFactory;
        final DeclaredType hsIsolate;
        final DeclaredType hsIsolateThread;
        final DeclaredType hSPeer;
        final DeclaredType imageInfo;
        final DeclaredType jBooleanArray;
        final DeclaredType jByteArray;
        final DeclaredType jCharArray;
        final DeclaredType jClass;
        final DeclaredType jDoubleArray;
        final DeclaredType jFloatArray;
        final DeclaredType jIntArray;
        final DeclaredType jLongArray;
        final DeclaredType jObject;
        final DeclaredType jObjectArray;
        final DeclaredType jShortArray;
        final DeclaredType jString;
        final DeclaredType jThrowable;
        final DeclaredType jniEnv;
        final DeclaredType jniMethodScope;
        final DeclaredType jniUtil;
        final DeclaredType nativeIsolate;
        final DeclaredType nativePeer;
        final DeclaredType stackValue;
        final DeclaredType unmanagedMemory;
        final DeclaredType wordFactory;

        NativeTypeCache(AbstractProcessor processor) {
            super(processor);
            this.cCharPointer = processor.getDeclaredType("org.graalvm.nativeimage.c.type.CCharPointer");
            this.cCharPointerBinaryOutput = processor.getDeclaredType("org.graalvm.nativebridge.BinaryOutput.CCharPointerBinaryOutput");
            this.generateHSToNativeBridge = processor.getDeclaredType(HotSpotToNativeServiceParser.GENERATE_HOTSPOT_TO_NATIVE_ANNOTATION);
            this.generateHSToNativeFactory = processor.getDeclaredType(HotSpotToNativeFactoryParser.GENERATE_HOTSPOT_TO_NATIVE_ANNOTATION);
            this.generateNativeToHSBridge = processor.getDeclaredType(NativeToHotSpotServiceParser.GENERATE_NATIVE_TO_HOTSPOT_ANNOTATION);
            this.generateNativeToNativeBridge = processor.getDeclaredType(NativeToNativeServiceParser.GENERATE_NATIVE_TO_NATIVE_ANNOTATION);
            this.generateNativeToNativeFactory = processor.getDeclaredType(NativeToNativeFactoryParser.GENERATE_NATIVE_TO_NATIVE_ANNOTATION);
            this.hsIsolate = processor.getDeclaredType("org.graalvm.nativebridge.HSIsolate");
            this.hsIsolateThread = processor.getDeclaredType("org.graalvm.nativebridge.HSIsolateThread");
            this.hSPeer = processor.getDeclaredType("org.graalvm.nativebridge.HSPeer");
            this.imageInfo = processor.getDeclaredType("org.graalvm.nativeimage.ImageInfo");
            this.jBooleanArray = processor.getDeclaredType("org.graalvm.jniutils.JNI.JBooleanArray");
            this.jByteArray = processor.getDeclaredType("org.graalvm.jniutils.JNI.JByteArray");
            this.jCharArray = processor.getDeclaredType("org.graalvm.jniutils.JNI.JCharArray");
            this.jClass = processor.getDeclaredType("org.graalvm.jniutils.JNI.JClass");
            this.jDoubleArray = processor.getDeclaredType("org.graalvm.jniutils.JNI.JDoubleArray");
            this.jFloatArray = processor.getDeclaredType("org.graalvm.jniutils.JNI.JFloatArray");
            this.jIntArray = processor.getDeclaredType("org.graalvm.jniutils.JNI.JIntArray");
            this.jLongArray = processor.getDeclaredType("org.graalvm.jniutils.JNI.JLongArray");
            this.jObject = processor.getDeclaredType("org.graalvm.jniutils.JNI.JObject");
            this.jObjectArray = processor.getDeclaredType("org.graalvm.jniutils.JNI.JObjectArray");
            this.jShortArray = processor.getDeclaredType("org.graalvm.jniutils.JNI.JShortArray");
            this.jString = processor.getDeclaredType("org.graalvm.jniutils.JNI.JString");
            this.jThrowable = processor.getDeclaredType("org.graalvm.jniutils.JNI.JThrowable");
            this.jniEnv = processor.getDeclaredType("org.graalvm.jniutils.JNI.JNIEnv");
            this.jniMethodScope = processor.getDeclaredType("org.graalvm.jniutils.JNIMethodScope");
            this.jniUtil = processor.getDeclaredType("org.graalvm.jniutils.JNIUtil");
            this.nativeIsolate = processor.getDeclaredType("org.graalvm.nativebridge.NativeIsolate");
            this.nativePeer = processor.getDeclaredType("org.graalvm.nativebridge.NativePeer");
            this.stackValue = processor.getDeclaredType("org.graalvm.nativeimage.StackValue");
            this.unmanagedMemory = processor.getDeclaredType("org.graalvm.nativeimage.UnmanagedMemory");
            this.wordFactory = processor.getDeclaredType("org.graalvm.word.WordFactory");
        }
    }
}
