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
package com.oracle.svm.core.jvmti;

import com.oracle.svm.core.jni.JNIObjectHandles;
import com.oracle.svm.core.jni.JNIThreadLocalEnvironment;
import com.oracle.svm.core.jvmti.headers.JThread;
import com.oracle.svm.core.jvmti.headers.JvmtiEvent;
import com.oracle.svm.core.jvmti.headers.JvmtiEventCallbacks.JvmtiEventVMDeathFunctionPointer;
import com.oracle.svm.core.jvmti.headers.JvmtiEventCallbacks.JvmtiEventVMInitFunctionPointer;
import com.oracle.svm.core.jvmti.headers.JvmtiEventCallbacks.JvmtiEventVMStartFunctionPointer;

/** The methods in this class can be used to trigger JVMTI events. */
public final class JvmtiEvents {
    public static void postVMInit() {
        JvmtiEnvs manager = JvmtiEnvs.singleton();
        manager.enterEnvIteration();
        try {
            for (JvmtiEnv cur = manager.getHead(); cur.isNonNull(); cur = cur.getNext()) {
                if (JvmtiEnvUtil.isEventEnabled(cur, JvmtiEvent.JVMTI_EVENT_VM_INIT)) {
                    JvmtiEventVMInitFunctionPointer callback = JvmtiEnvUtil.getEventCallbacks(cur).getVMInit();
                    if (callback.isNonNull()) {
                        JThread currentThread = (JThread) JNIObjectHandles.createLocal(Thread.currentThread());
                        try {
                            callback.invoke(JvmtiEnvUtil.toExternal(cur), JNIThreadLocalEnvironment.getAddress(), currentThread);
                        } finally {
                            JNIObjectHandles.deleteLocalRef(currentThread);
                        }
                    }
                }
            }
        } finally {
            manager.leaveEnvIteration();
        }
    }

    public static void postVMStart() {
        JvmtiEnvs manager = JvmtiEnvs.singleton();
        manager.enterEnvIteration();
        try {
            for (JvmtiEnv cur = manager.getHead(); cur.isNonNull(); cur = cur.getNext()) {
                if (JvmtiEnvUtil.isEventEnabled(cur, JvmtiEvent.JVMTI_EVENT_VM_START)) {
                    JvmtiEventVMStartFunctionPointer callback = JvmtiEnvUtil.getEventCallbacks(cur).getVMStart();
                    if (callback.isNonNull()) {
                        callback.invoke(JvmtiEnvUtil.toExternal(cur), JNIThreadLocalEnvironment.getAddress());
                    }
                }
            }
        } finally {
            manager.leaveEnvIteration();
        }
    }

    public static void postVMDeath() {
        JvmtiEnvs manager = JvmtiEnvs.singleton();
        manager.enterEnvIteration();
        try {
            for (JvmtiEnv cur = manager.getHead(); cur.isNonNull(); cur = cur.getNext()) {
                if (JvmtiEnvUtil.isEventEnabled(cur, JvmtiEvent.JVMTI_EVENT_VM_DEATH)) {
                    JvmtiEventVMDeathFunctionPointer callback = JvmtiEnvUtil.getEventCallbacks(cur).getVMDeath();
                    if (callback.isNonNull()) {
                        callback.invoke(JvmtiEnvUtil.toExternal(cur), JNIThreadLocalEnvironment.getAddress());
                    }
                }
            }
        } finally {
            manager.leaveEnvIteration();
        }
    }
}
