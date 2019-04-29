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
package com.oracle.svm.jni.functions;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicPointer;
import com.oracle.svm.jni.nativeapi.JNIInvokeInterface;
import com.oracle.svm.jni.nativeapi.JNIJavaVM;
import com.oracle.svm.jni.nativeapi.JNINativeInterface;

/**
 * Performs the initialization of the JNI function table structures at runtime.
 */
public final class JNIFunctionTables {

    static void create() {
        ImageSingletons.add(JNIFunctionTables.class, new JNIFunctionTables());
    }

    public static JNIFunctionTables singleton() {
        return ImageSingletons.lookup(JNIFunctionTables.class);
    }

    private JNIFunctionTables() {
    }

    void initialize(JNIStructFunctionsInitializer<JNIInvokeInterface> invokes, JNIStructFunctionsInitializer<JNINativeInterface> functionTable) {
        assert this.invokesInitializer == null && this.functionTableInitializer == null;
        this.invokesInitializer = invokes;
        this.functionTableInitializer = functionTable;
    }

    @UnknownObjectField(types = JNIStructFunctionsInitializer.class) //
    private JNIStructFunctionsInitializer<JNIInvokeInterface> invokesInitializer;

    private JNIJavaVM globalJavaVM;

    public JNIJavaVM getGlobalJavaVM() {
        if (globalJavaVM.isNull()) {
            JNIInvokeInterface invokes = UnmanagedMemory.calloc(SizeOf.get(JNIInvokeInterface.class));
            invokesInitializer.initialize(invokes);
            invokes.setIsolate(CurrentIsolate.getIsolate());
            globalJavaVM = UnmanagedMemory.calloc(SizeOf.get(JNIJavaVM.class));
            globalJavaVM.setFunctions(invokes);
            RuntimeSupport.getRuntimeSupport().addTearDownHook(() -> {
                UnmanagedMemory.free(globalJavaVM.getFunctions());
                UnmanagedMemory.free(globalJavaVM);
                globalJavaVM = WordFactory.nullPointer();
            });
        }
        return globalJavaVM;
    }

    @UnknownObjectField(types = JNIStructFunctionsInitializer.class) //
    private JNIStructFunctionsInitializer<JNINativeInterface> functionTableInitializer;

    private final AtomicPointer<JNINativeInterface> globalFunctionTable = new AtomicPointer<>();

    public JNINativeInterface getGlobalFunctionTable() {
        JNINativeInterface functionTable = globalFunctionTable.get();
        if (functionTable.isNull()) {
            functionTable = UnmanagedMemory.malloc(SizeOf.get(JNINativeInterface.class));
            functionTableInitializer.initialize(functionTable);
            if (globalFunctionTable.compareAndSet(WordFactory.nullPointer(), functionTable)) {
                RuntimeSupport.getRuntimeSupport().addTearDownHook(() -> UnmanagedMemory.free(globalFunctionTable.get()));
            } else { // lost the race
                UnmanagedMemory.free(functionTable);
                functionTable = globalFunctionTable.get();
            }
        }
        return functionTable;
    }

}
