/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jni.functions;

import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.c.CIsolateData;
import com.oracle.svm.core.c.CIsolateDataFactory;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.jni.headers.JNIInvokeInterface;
import com.oracle.svm.core.jni.headers.JNIJavaVM;
import com.oracle.svm.core.jni.headers.JNINativeInterface;
import com.oracle.svm.core.util.VMError;

import jdk.internal.misc.Unsafe;

/**
 * Performs the initialization of the JNI function table structures at runtime.
 */
public final class JNIFunctionTables {

    public static void create() {
        ImageSingletons.add(JNIFunctionTables.class, new JNIFunctionTables());
    }

    public static JNIFunctionTables singleton() {
        return ImageSingletons.lookup(JNIFunctionTables.class);
    }

    /*
     * Space for C data structures that are passed out to C code at run time. Because these arrays
     * are in the image heap, they are never moved by the GC at run time.
     */
    private final CFunctionPointer[] invokeInterfaceDataPrototype;
    private final CFunctionPointer[] functionTableData;

    private final CIsolateData<JNIInvokeInterface> jniInvokeInterface = CIsolateDataFactory.createStruct("jniInvokeInterface", JNIInvokeInterface.class);
    private final CIsolateData<JNIJavaVM> jniJavaVM = CIsolateDataFactory.createStruct("jniJavaVM", JNIJavaVM.class);

    private static int getFunctionTableSize() {
        return SizeOf.get(JNINativeInterface.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private JNIFunctionTables() {
        invokeInterfaceDataPrototype = new CFunctionPointer[wordArrayLength(SizeOf.get(JNIInvokeInterface.class))];
        functionTableData = new CFunctionPointer[wordArrayLength(getFunctionTableSize())];
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static int wordArrayLength(int sizeInBytes) {
        int wordSize = FrameAccess.wordSize();
        VMError.guarantee(sizeInBytes % wordSize == 0);
        return sizeInBytes / wordSize;
    }

    private JNIJavaVM globalJavaVM;

    public JNIJavaVM getGlobalJavaVM() {
        JNIJavaVM javaVM = globalJavaVM;
        if (javaVM.isNull()) {
            JNIInvokeInterface invokeInterface = jniInvokeInterface.get();

            /*
             * The function pointer table filled during image generation must be in the read-only
             * part of the image heap, because code relocations are needed for it. To work around
             * this limitation, we copy the read-only table filled during image generation to a
             * writable table of the same size.
             */
            for (int i = 0; i < invokeInterfaceDataPrototype.length; i++) {
                ((WordPointer) invokeInterface).write(i, invokeInterfaceDataPrototype[i]);
            }

            javaVM = jniJavaVM.get();
            invokeInterface.setIsolate(CurrentIsolate.getIsolate());
            javaVM.setFunctions(invokeInterface);

            Unsafe.getUnsafe().storeFence();

            globalJavaVM = javaVM;
        }
        return javaVM;
    }

    private JNINativeInterface globalFunctionTable;

    public JNINativeInterface getGlobalFunctionTable() {
        JNINativeInterface functionTable = globalFunctionTable;
        if (functionTable.isNull()) {
            /*
             * The JNI function table is filled during image generation and is ready to use, so we
             * do not need to copy it to a modifiable part of the image heap.
             */
            functionTable = (JNINativeInterface) dataAddress(functionTableData);

            globalFunctionTable = functionTable;
        }
        return functionTable;
    }

    /**
     * Returns the absolute address of the first array element of the provided array. The array must
     * be in the image heap, i.e., never moved by the GC.
     */
    private static Pointer dataAddress(WordBase[] dataArray) {
        final DynamicHub hub = DynamicHub.fromClass(dataArray.getClass());
        final UnsignedWord offsetOfFirstArrayElement = LayoutEncoding.getArrayElementOffset(hub.getLayoutEncoding(), 0);
        return Word.objectToUntrackedPointer(dataArray).add(offsetOfFirstArrayElement);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void initInvokeInterfaceTable(CFunctionPointer defaultValue, Feature.CompilationAccess access) {
        initTable(invokeInterfaceDataPrototype, defaultValue, access);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void initInvokeInterfaceEntry(int offsetInBytes, CFunctionPointer value) {
        int wordSize = FrameAccess.wordSize();
        VMError.guarantee(offsetInBytes % wordSize == 0);
        invokeInterfaceDataPrototype[offsetInBytes / wordSize] = value;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void initFunctionTable(CFunctionPointer defaultValue, Feature.CompilationAccess access) {
        initTable(functionTableData, defaultValue, access);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void initFunctionEntry(int offsetInBytes, CFunctionPointer value) {
        int wordSize = FrameAccess.wordSize();
        VMError.guarantee(offsetInBytes % wordSize == 0);
        functionTableData[offsetInBytes / wordSize] = value;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static void initTable(CFunctionPointer[] table, CFunctionPointer defaultValue, Feature.CompilationAccess access) {
        for (int i = 0; i < table.length; i++) {
            table[i] = defaultValue;
        }
        /*
         * All objects in the image heap that have function pointers, i.e., relocations, must be
         * immutable at run time.
         */
        access.registerAsImmutable(table);
    }
}
