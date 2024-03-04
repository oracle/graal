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

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.jni.JNIObjectHandles;
import com.oracle.svm.core.jni.JNIThreadLocalEnvironment;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;
import com.oracle.svm.core.jvmti.headers.JThread;
import com.oracle.svm.core.jvmti.headers.JvmtiEvent;
import com.oracle.svm.core.jvmti.headers.JvmtiEventCallbacks;
import com.oracle.svm.core.jvmti.headers.JvmtiPhase;
import com.oracle.svm.core.log.Log;

public final class JvmtiPostEvents {

    private static final int EVENT_NOT_FOUND = -1;
    private static final int NO_CALLBACK_SET = -2;
    private static final int ARGUMENT_ERROR = -3;

    public static void postVMInit() {
        invokeCallbackForEvent(JvmtiEvent.JVMTI_EVENT_VM_INIT);
    }

    public static void postVMStart() {
        invokeCallbackForEvent(JvmtiEvent.JVMTI_EVENT_VM_START);
    }

    public static void postVMDeath() {
        // TODO @dprcci create and add call to JvmtiTagMap::flush_all_object_free_events();
        invokeCallbackForEvent(JvmtiEvent.JVMTI_EVENT_VM_DEATH);
        JvmtiEnvManager.singleton().setPhase(JvmtiPhase.JVMTI_PHASE_DEAD());
    }

    public static void postThreadStart(Thread thread) {
        invokeCallbackForEvent(JvmtiEvent.JVMTI_EVENT_THREAD_START, thread);
    }

    public static void postThreadEnd(Thread thread) {
        invokeCallbackForEvent(JvmtiEvent.JVMTI_EVENT_THREAD_END, thread);
    }

    public static void postGarbageCollectionStart() {
        invokeCallbackForEvent(JvmtiEvent.JVMTI_EVENT_GARBAGE_COLLECTION_START);
    }

    public static void postGarbageCollectionFinish() {
        invokeCallbackForEvent(JvmtiEvent.JVMTI_EVENT_GARBAGE_COLLECTION_FINISH);
    }

    public static void postMonitorWait(Thread thread, Object obj, long timeout) {
        invokeCallbackForEvent(JvmtiEvent.JVMTI_EVENT_MONITOR_WAIT, thread, obj, timeout);
    }

    public static void postMonitorWaited(Thread thread, Object obj, boolean timedout) {
        invokeCallbackForEvent(JvmtiEvent.JVMTI_EVENT_MONITOR_WAITED, thread, obj, timedout);
    }

    public static void postMonitorContendedEnter(Thread thread, Object obj) {
        invokeCallbackForEvent(JvmtiEvent.JVMTI_EVENT_MONITOR_CONTENDED_ENTER, thread, obj);
    }

    public static void postMonitorContendedEntered(Thread thread, Object obj) {
        invokeCallbackForEvent(JvmtiEvent.JVMTI_EVENT_MONITOR_CONTENDED_ENTERED, thread, obj);
    }

    private static void invokeCallbackForEvent(JvmtiEvent event, Object... args) {
        if (!SubstrateOptions.JVMTI.getValue()) {
            return;
        }

        JvmtiEnvManager manager = JvmtiEnvManager.singleton();
        if (!manager.hasAnyEnvironments()) {
            return;
        }
        for (JvmtiEnv current = manager.getHeadEnvironment(); current.isNonNull(); current = current.getNextEnv()) {
            if (JvmtiEnvEventEnabledUtils.isUserEventEnabled(JvmtiEnvUtil.getEnvEventEnabled(current), event)) {
                // assert event.getNbParameters() == args.length;
                int res = invokeCallbackFunction(current, event, args);
                checkError(res, event, args);
            }
        }
    }

    private static int invokeCallbackFunction(JvmtiEnv env, JvmtiEvent event, Object... args) {
        switch (event) {
            case JVMTI_EVENT_VM_DEATH -> {
                JvmtiEventCallbacks.JvmtiEventVMDeathFunctionPointer vmDeathCallback = JvmtiEnvUtil.getEventCallbacks(env).getVMDeath();
                if (vmDeathCallback.isNonNull()) {
                    return vmDeathCallback.invoke(JvmtiEnvUtil.toExternal(env), JNIThreadLocalEnvironment.getAddress());
                }
            }
            case JVMTI_EVENT_VM_START -> {
                JvmtiEventCallbacks.JvmtiEventVMStartFunctionPointer vmStartCallback = JvmtiEnvUtil.getEventCallbacks(env).getVMStart();
                if (vmStartCallback.isNonNull()) {
                    return vmStartCallback.invoke(JvmtiEnvUtil.toExternal(env), JNIThreadLocalEnvironment.getAddress());
                }
            }
            case JVMTI_EVENT_VM_INIT -> {
                JvmtiEventCallbacks.JvmtiEventVMInitFunctionPointer vmInitCallback = JvmtiEnvUtil.getEventCallbacks(env).getVMInit();
                if (vmInitCallback.isNonNull()) {
                    return vmInitCallback.invoke(JvmtiEnvUtil.toExternal(env), JNIThreadLocalEnvironment.getAddress(), (JThread) JNIObjectHandles.createLocal(Thread.currentThread()));
                }
            }
            case JVMTI_EVENT_GARBAGE_COLLECTION_START -> {
                JvmtiEventCallbacks.JvmtiEventGarbageCollectionStartFunctionPointer gcStart = JvmtiEnvUtil.getEventCallbacks(env).getGarbageCollectionStart();
                if (gcStart.isNonNull()) {
                    return gcStart.invoke(JvmtiEnvUtil.toExternal(env));
                }
            }
            case JVMTI_EVENT_GARBAGE_COLLECTION_FINISH -> {
                JvmtiEventCallbacks.JvmtiEventGarbageCollectionFinishFunctionPointer gcFinish = JvmtiEnvUtil.getEventCallbacks(env).getGarbageCollectionFinish();
                if (gcFinish.isNonNull()) {
                    return gcFinish.invoke(JvmtiEnvUtil.toExternal(env));
                }
            }
            case JVMTI_EVENT_THREAD_START -> {
                JvmtiEventCallbacks.JvmtiEventThreadStartFunctionPointer threadStart = JvmtiEnvUtil.getEventCallbacks(env).getThreadStart();
                if (threadStart.isNonNull()) {
                    if (!validArgumentTypes(args[0], Thread.class)) {
                        return ARGUMENT_ERROR;
                    }
                    JThread jthread = (JThread) JNIObjectHandles.createLocal(args[0]);
                    return threadStart.invoke(JvmtiEnvUtil.toExternal(env), JNIThreadLocalEnvironment.getAddress(), jthread);
                }
            }
            case JVMTI_EVENT_THREAD_END -> {
                JvmtiEventCallbacks.JvmtiEventThreadEndFunctionPointer threadEnd = JvmtiEnvUtil.getEventCallbacks(env).getThreadEnd();
                if (threadEnd.isNonNull()) {
                    if (!validArgumentTypes(args[0], Thread.class)) {
                        return ARGUMENT_ERROR;
                    }
                    JThread jthread = (JThread) JNIObjectHandles.createLocal(args[0]);
                    return threadEnd.invoke(JvmtiEnvUtil.toExternal(env), JNIThreadLocalEnvironment.getAddress(), jthread);
                }
            }
            case JVMTI_EVENT_MONITOR_WAIT -> {
                JvmtiEventCallbacks.JvmtiEventMonitorWaitFunctionPointer monitorWait = JvmtiEnvUtil.getEventCallbacks(env).getMonitorWait();
                if (monitorWait.isNonNull()) {
                    if (!validArgumentTypes(args[0], Thread.class) || !validArgumentTypes(args[2], Long.class)) {
                        return ARGUMENT_ERROR;
                    }
                    JThread jthread = (JThread) JNIObjectHandles.createLocal(args[0]);
                    JNIObjectHandle jobject = JNIObjectHandles.createLocal(args[1]);
                    long timeout = (Long) args[2];
                    return monitorWait.invoke(JvmtiEnvUtil.toExternal(env), JNIThreadLocalEnvironment.getAddress(), jthread, jobject, timeout);
                }

            }
            case JVMTI_EVENT_MONITOR_WAITED -> {
                JvmtiEventCallbacks.JvmtiEventMonitorWaitedFunctionPointer monitorWaited = JvmtiEnvUtil.getEventCallbacks(env).getMonitorWaited();
                if (monitorWaited.isNonNull()) {
                    if (!validArgumentTypes(args[0], Thread.class) || !validArgumentTypes(args[2], Boolean.class)) {
                        return ARGUMENT_ERROR;
                    }
                    JThread jthread = (JThread) JNIObjectHandles.createLocal(args[0]);
                    JNIObjectHandle jobject = JNIObjectHandles.createLocal(args[1]);
                    boolean timedout = (Boolean) args[2];
                    return monitorWaited.invoke(JvmtiEnvUtil.toExternal(env), JNIThreadLocalEnvironment.getAddress(), jthread, jobject, timedout);
                }
            }
            case JVMTI_EVENT_MONITOR_CONTENDED_ENTER -> {
                JvmtiEventCallbacks.JvmtiEventMonitorContendedEnterFunctionPointer monitorContendedEnter = JvmtiEnvUtil.getEventCallbacks(env).getMonitorContendedEnter();
                if (monitorContendedEnter.isNonNull()) {
                    if (!validArgumentTypes(args[0], Thread.class)) {
                        return ARGUMENT_ERROR;
                    }
                    JThread jthread = (JThread) JNIObjectHandles.createLocal(args[0]);
                    JNIObjectHandle jobject = JNIObjectHandles.createLocal(args[1]);
                    return monitorContendedEnter.invoke(JvmtiEnvUtil.toExternal(env), JNIThreadLocalEnvironment.getAddress(), jthread, jobject);
                }
            }
            case JVMTI_EVENT_MONITOR_CONTENDED_ENTERED -> {
                JvmtiEventCallbacks.JvmtiEventMonitorContendedEnteredFunctionPointer monitorContendedEntered = JvmtiEnvUtil.getEventCallbacks(env).getMonitorContendedEntered();
                if (monitorContendedEntered.isNonNull()) {
                    if (!validArgumentTypes(args[0], Thread.class)) {
                        return ARGUMENT_ERROR;
                    }
                    JThread jthread = (JThread) JNIObjectHandles.createLocal(args[0]);
                    JNIObjectHandle jobject = JNIObjectHandles.createLocal(args[1]);
                    return monitorContendedEntered.invoke(JvmtiEnvUtil.toExternal(env), JNIThreadLocalEnvironment.getAddress(), jthread, jobject);
                }
            }
            default -> {
                return EVENT_NOT_FOUND;
            }
        }
        return NO_CALLBACK_SET;
    }

    private static <T> boolean validArgumentTypes(Object arg, Class<T> type) {
        return type.isInstance(arg);
    }

    private static void checkError(int error, JvmtiEvent event, Object... args) {
        if (error == EVENT_NOT_FOUND) {
            Log.log().string("Event: " + event + " is not implemented yet");
        } else if (error == NO_CALLBACK_SET) {
            Log.log().string("Event: " + event + " has no callback set");
        }
    }

}
