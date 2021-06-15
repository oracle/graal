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
package com.oracle.truffle.espresso.libespresso.jniapi;

import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.word.PointerBase;

import com.oracle.truffle.espresso.libespresso.jniapi.JNIFunctionPointerTypes.AttachCurrentThreadFunctionPointer;
import com.oracle.truffle.espresso.libespresso.jniapi.JNIFunctionPointerTypes.DestroyJavaVMFunctionPointer;
import com.oracle.truffle.espresso.libespresso.jniapi.JNIFunctionPointerTypes.DetachCurrentThreadFunctionPointer;
import com.oracle.truffle.espresso.libespresso.jniapi.JNIFunctionPointerTypes.GetEnvFunctionPointer;

@CContext(JNIHeaderDirectives.class)
@CStruct(value = "JNIInvokeInterface_", addStructKeyword = true)
public interface JNIInvokeInterface extends PointerBase {

    @CField("reserved0")
    ObjectHandle getContext();

    @CField("reserved0")
    void setContext(ObjectHandle context);

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
