/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.libjvm.nativeapi;

import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.word.PointerBase;

import com.oracle.truffle.espresso.libjvm.nativeapi.JNIFunctionPointerTypes.AttachCurrentThreadFunctionPointer;
import com.oracle.truffle.espresso.libjvm.nativeapi.JNIFunctionPointerTypes.DestroyJavaVMFunctionPointer;
import com.oracle.truffle.espresso.libjvm.nativeapi.JNIFunctionPointerTypes.DetachCurrentThreadFunctionPointer;
import com.oracle.truffle.espresso.libjvm.nativeapi.JNIFunctionPointerTypes.GetEnvFunctionPointer;

@CContext(JNIHeaderDirectives.class)
@CStruct(value = "JNIInvokeInterface_", addStructKeyword = true)
public interface JNIInvokeInterface extends PointerBase {

    /**
     * The {@link Isolate} represented by the {@link JNIJavaVM} to which this
     * {@link JNIInvokeInterface} function table belongs. Because {@link JNIJavaVM} has no spare
     * fields itself, we use this field and therefore need a separate function table for each
     * isolate.
     */
    @CField("reserved0")
    Isolate getIsolate();

    @CField("reserved0")
    void setIsolate(Isolate isolate);

    @CField("reserved1")
    JNIJavaVM getEspressoJavaVM();

    @CField("reserved1")
    void setEspressoJavaVM(JNIJavaVM isolate);

    @CField("AttachCurrentThread")
    void setAttachCurrentThread(AttachCurrentThreadFunctionPointer p);

    @CField("AttachCurrentThread")
    AttachCurrentThreadFunctionPointer getAttachCurrentThread();

    @CField("AttachCurrentThreadAsDaemon")
    void setAttachCurrentThreadAsDaemon(AttachCurrentThreadFunctionPointer p);

    @CField("AttachCurrentThreadAsDaemon")
    AttachCurrentThreadFunctionPointer getAttachCurrentThreadAsDaemon();

    @CField("DetachCurrentThread")
    void setDetachCurrentThread(DetachCurrentThreadFunctionPointer p);

    @CField("DetachCurrentThread")
    DetachCurrentThreadFunctionPointer getDetachCurrentThread();

    @CField("GetEnv")
    void setGetEnv(GetEnvFunctionPointer p);

    @CField("GetEnv")
    GetEnvFunctionPointer getGetEnv();

    @CField("DestroyJavaVM")
    void setDestroyJavaVM(DestroyJavaVMFunctionPointer p);

    @CField("DestroyJavaVM")
    DestroyJavaVMFunctionPointer getDestroyJavaVM();
}
