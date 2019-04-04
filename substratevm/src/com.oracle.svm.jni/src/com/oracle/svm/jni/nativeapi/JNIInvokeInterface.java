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
package com.oracle.svm.jni.nativeapi;

import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.word.PointerBase;

import com.oracle.svm.jni.nativeapi.JNIFunctionPointerTypes.GetEnvFunctionPointer;

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

    @CField("AttachCurrentThread")
    void setAttachCurrentThread(CFunctionPointer p);

    @CField("AttachCurrentThreadAsDaemon")
    void setAttachCurrentThreadAsDaemon(CFunctionPointer p);

    @CField("DetachCurrentThread")
    void setDetachCurrentThread(CFunctionPointer p);

    @CField("GetEnv")
    void setGetEnv(CFunctionPointer p);

    @CField("GetEnv")
    GetEnvFunctionPointer getGetEnv();

    @CField("DestroyJavaVM")
    void setDestroyJavaVM(CFunctionPointer p);

    @CField("DestroyJavaVM")
    <T extends CFunctionPointer> T getDestroyJavaVM();
}
