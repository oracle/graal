/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jvmti.headers;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.jni.headers.JNIEnvironment;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;

/**
 * Only a small subset of the callbacks is supported at the moment. All unsupported callbacks use
 * the type {@link CFunctionPointer}.
 */
@CContext(JvmtiDirectives.class)
@CStruct("jvmtiEventCallbacks")
public interface JvmtiEventCallbacks extends PointerBase {
    @CField
    JvmtiEventVMInitFunctionPointer getVMInit();

    @CField
    JvmtiEventVMDeathFunctionPointer getVMDeath();

    @CField
    JvmtiEventThreadStartFunctionPointer getThreadStart();

    @CField
    JvmtiEventThreadEndFunctionPointer getThreadEnd();

    @CField
    CFunctionPointer getClassFileLoadHook();

    @CField
    CFunctionPointer getClassLoad();

    @CField
    CFunctionPointer getClassPrepare();

    @CField
    JvmtiEventVMStartFunctionPointer getVMStart();

    @CField
    CFunctionPointer getException();

    @CField
    CFunctionPointer getExceptionCatch();

    @CField
    CFunctionPointer getSingleStep();

    @CField
    CFunctionPointer getFramePop();

    @CField
    CFunctionPointer getBreakpoint();

    @CField
    CFunctionPointer getFieldAccess();

    @CField
    CFunctionPointer getFieldModification();

    @CField
    CFunctionPointer getMethodEntry();

    @CField
    CFunctionPointer getMethodExit();

    @CField
    CFunctionPointer getNativeMethodBind();

    @CField
    CFunctionPointer getCompiledMethodLoad();

    @CField
    CFunctionPointer getCompiledMethodUnload();

    @CField
    CFunctionPointer getDynamicCodeGenerated();

    @CField
    CFunctionPointer getDataDumpRequest();

    @CField("reserved72")
    CFunctionPointer getReserved72();

    @CField
    JvmtiEventMonitorWaitFunctionPointer getMonitorWait();

    @CField
    JvmtiEventMonitorWaitedFunctionPointer getMonitorWaited();

    @CField
    JvmtiEventMonitorContendedEnterFunctionPointer getMonitorContendedEnter();

    @CField
    JvmtiEventMonitorContendedEnteredFunctionPointer getMonitorContendedEntered();

    @CField("reserved77")
    CFunctionPointer getReserved77();

    @CField("reserved78")
    CFunctionPointer getReserved78();

    @CField("reserved79")
    CFunctionPointer getReserved79();

    @CField
    CFunctionPointer getResourceExhausted();

    @CField
    JvmtiEventGarbageCollectionStartFunctionPointer getGarbageCollectionStart();

    @CField
    JvmtiEventGarbageCollectionFinishFunctionPointer getGarbageCollectionFinish();

    @CField
    CFunctionPointer getObjectFree();

    @CField
    CFunctionPointer getVMObjectAlloc();

    @CField("reserved85")
    CFunctionPointer getReserved85();

    @CField
    CFunctionPointer getSampledObjectAlloc();

    @CField
    CFunctionPointer getVirtualThreadStart();

    @CField
    CFunctionPointer getVirtualThreadEnd();

    interface JvmtiEventVMInitFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        void invoke(JvmtiExternalEnv jvmtiEnv, JNIEnvironment jniEnv, JThread thread);
    }

    interface JvmtiEventVMDeathFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        void invoke(JvmtiExternalEnv jvmtiEnv, JNIEnvironment jniEnv);
    }

    interface JvmtiEventVMStartFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        void invoke(JvmtiExternalEnv jvmtiEnv, JNIEnvironment jniEnv);
    }

    interface JvmtiEventGarbageCollectionFinishFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        void invoke(JvmtiExternalEnv jvmtiEnv);
    }

    interface JvmtiEventGarbageCollectionStartFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        void invoke(JvmtiExternalEnv jvmtiEnv);
    }

    interface JvmtiEventThreadStartFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        void invoke(JvmtiExternalEnv jvmtiEnv, JNIEnvironment jniEnv, JThread jthread);
    }

    interface JvmtiEventThreadEndFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        void invoke(JvmtiExternalEnv jvmtiEnv, JNIEnvironment jniEnv, JThread jthread);
    }

    interface JvmtiEventMonitorWaitFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        void invoke(JvmtiExternalEnv jvmtiEnv, JNIEnvironment jniEnv, JThread jthread, JNIObjectHandle obj, long timeout);
    }

    interface JvmtiEventMonitorWaitedFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        void invoke(JvmtiExternalEnv jvmtiEnv, JNIEnvironment jniEnv, JThread jthread, JNIObjectHandle obj, boolean timedOut);
    }

    interface JvmtiEventMonitorContendedEnterFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        void invoke(JvmtiExternalEnv jvmtiEnv, JNIEnvironment jniEnv, JThread thread, JNIObjectHandle obj);
    }

    interface JvmtiEventMonitorContendedEnteredFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        void invoke(JvmtiExternalEnv jvmtiEnv, JNIEnvironment jniEnv, JThread thread, JNIObjectHandle obj);
    }
}
