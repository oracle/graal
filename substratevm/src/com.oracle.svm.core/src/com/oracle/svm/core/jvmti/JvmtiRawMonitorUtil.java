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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.c.NonmovableObjectArray;
import com.oracle.svm.core.jvmti.headers.JRawMonitorId;
import com.oracle.svm.core.jvmti.headers.JRawMonitorIdPointer;
import com.oracle.svm.core.jvmti.headers.JvmtiError;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.monitor.JvmtiRawMonitorHelper;
import com.oracle.svm.core.monitor.MonitorInflationCause;
import com.oracle.svm.core.monitor.MultiThreadedMonitorSupport;
import com.oracle.svm.core.nmt.NmtCategory;

import jdk.graal.compiler.api.replacements.Fold;

public final class JvmtiRawMonitorUtil implements JvmtiMemoryManager {

    // TODO @dprcci Lots of copy pasted code because 2 values would need to be returned when
    // refactored into a function.
    // Current best idea is group the code and use a StackValue to write the error

    private final VMMutex mutex;
    private static final int INITIAL_NB_AVAILABLE_SLOTS = 4;
    private static final int ARRAY_GROWTH_FACTOR = 2;
    private NonmovableObjectArray<Object> monitorObjectsArray;
    private NonmovableArray<Integer> freeIndicesList;
    private int nextAvailableIndex;
    private int monitorArraySize;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JvmtiRawMonitorUtil() {
        mutex = new VMMutex("Jvmti RawMonitor");
        nextAvailableIndex = 0;
        monitorArraySize = INITIAL_NB_AVAILABLE_SLOTS;
    }

    public void initialize() {
        monitorObjectsArray = NonmovableArrays.createObjectArray(Object[].class, INITIAL_NB_AVAILABLE_SLOTS, NmtCategory.JVMTI);
        freeIndicesList = NonmovableArrays.createIntArray(INITIAL_NB_AVAILABLE_SLOTS, NmtCategory.JVMTI);
        initFreeList(freeIndicesList);
    }

    @Override
    public void releaseAllUnmanagedMemory() {
        tearDown();
    }

    @Fold
    public static JvmtiRawMonitorUtil singleton() {
        return ImageSingletons.lookup(JvmtiRawMonitorUtil.class);
    }

    public static JvmtiError createRawMonitor(CCharPointer name, JRawMonitorIdPointer monitorPtr) {
        return singleton().createRawMonitorInternal(monitorPtr);
    }

    public static JvmtiError destroyRawMonitor(JRawMonitorId monitorId) {
        return singleton().destroyRawMonitorInternal(monitorId);
    }

    public static JvmtiError rawMonitorEnter(JRawMonitorId monitorId) {
        return singleton().rawMonitorEnterInternal(monitorId);
    }

    public static JvmtiError rawMonitorExit(JRawMonitorId monitorId) {
        return singleton().rawMonitorExitInternal(monitorId);
    }

    public static JvmtiError rawMonitorWait(JRawMonitorId monitorId, long millis) {
        return singleton().rawMonitorWaitInternal(monitorId, millis);
    }

    public static JvmtiError rawMonitorNotify(JRawMonitorId monitorId) {
        return singleton().rawMonitorNotifyInternal(monitorId, false);
    }

    public static JvmtiError rawMonitorNotifyAll(JRawMonitorId monitorId) {
        return singleton().rawMonitorNotifyInternal(monitorId, true);
    }

    private JvmtiError rawMonitorNotifyInternal(JRawMonitorId monitorId, boolean notifyAll) {
        int monitorIdx = (int) monitorId.rawValue();
        if (isInvalidMonitorIdx(monitorIdx)) {
            return JvmtiError.JVMTI_ERROR_INVALID_MONITOR;
        }
        Object monitor = NonmovableArrays.getObject(monitorObjectsArray, monitorIdx);
        if (!MultiThreadedMonitorSupport.singleton().isLockedByCurrentThread(monitor)) {
            return JvmtiError.JVMTI_ERROR_NOT_MONITOR_OWNER;
        }
        MultiThreadedMonitorSupport.singleton().notify(monitor, notifyAll);
        return JvmtiError.JVMTI_ERROR_NONE;
    }

    private JvmtiError rawMonitorWaitInternal(JRawMonitorId monitorId, long millis) {
        int monitorIdx = (int) monitorId.rawValue();
        if (isInvalidMonitorIdx(monitorIdx)) {
            return JvmtiError.JVMTI_ERROR_INVALID_MONITOR;
        }
        Object monitor = NonmovableArrays.getObject(monitorObjectsArray, monitorIdx);
        if (!MultiThreadedMonitorSupport.singleton().isLockedByCurrentThread(monitor)) {
            return JvmtiError.JVMTI_ERROR_NOT_MONITOR_OWNER;
        }
        // exception is thrown by wait(), but JVM TI does not specify behaviour in that case
        if (millis < 0) {
            millis = 0L;
        }
        // TODO @dprcci hard to get rid of the thrown exceptions without reimplementation
        try {
            MultiThreadedMonitorSupport.singleton().wait(monitor, millis);
        } catch (InterruptedException e) {
            return JvmtiError.JVMTI_ERROR_INTERRUPT;
        }
        return JvmtiError.JVMTI_ERROR_NONE;
    }

    private JvmtiError rawMonitorExitInternal(JRawMonitorId monitorId) {
        int monitorIdx = (int) monitorId.rawValue();
        if (isInvalidMonitorIdx(monitorIdx)) {
            return JvmtiError.JVMTI_ERROR_INVALID_MONITOR;
        }
        // TODO @dprcci handle entering during Agent_OnLoad
        Object monitor = NonmovableArrays.getObject(monitorObjectsArray, monitorIdx);
        if (!MultiThreadedMonitorSupport.singleton().isLockedByCurrentThread(monitor)) {
            return JvmtiError.JVMTI_ERROR_NOT_MONITOR_OWNER;
        }
        MultiThreadedMonitorSupport.singleton().monitorExit(monitor, MonitorInflationCause.JVMTI_ENTER);
        return JvmtiError.JVMTI_ERROR_NONE;
    }

    private JvmtiError rawMonitorEnterInternal(JRawMonitorId monitorId) {
        int monitorIdx = (int) monitorId.rawValue();
        if (isInvalidMonitorIdx(monitorIdx)) {
            return JvmtiError.JVMTI_ERROR_INVALID_MONITOR;
        }
        // TODO @dprcci handle entering during Agent_OnLoad
        Object monitor = NonmovableArrays.getObject(monitorObjectsArray, monitorIdx);
        MultiThreadedMonitorSupport.singleton().monitorEnter(monitor, MonitorInflationCause.JVMTI_ENTER);
        return JvmtiError.JVMTI_ERROR_NONE;
    }

    private JvmtiError createRawMonitorInternal(JRawMonitorIdPointer monitorPtr) {
        try {
            mutex.lock();
            // TODO @dprcci probably reimplement monitors without java Objects?
            Object obj = new Object();
            JvmtiError err = JvmtiRawMonitorHelper.getOrCreateRawMonitor(obj);
            if (err != JvmtiError.JVMTI_ERROR_NONE) {
                return err;
            }
            int monitorIdx = getNextFreeMonitorIdx();
            NonmovableArrays.setObject(monitorObjectsArray, monitorIdx, obj);
            ((Pointer) monitorPtr).writeInt(0, monitorIdx);
            return JvmtiError.JVMTI_ERROR_NONE;
        } finally {
            mutex.unlock();
        }
    }

    private JvmtiError destroyRawMonitorInternal(JRawMonitorId monitorId) {
        try {
            mutex.lock();
            int monitorIdx = (int) monitorId.rawValue();
            if (isInvalidMonitorIdx(monitorIdx)) {
                return JvmtiError.JVMTI_ERROR_INVALID_MONITOR;
            }
            Object monitor = NonmovableArrays.getObject(monitorObjectsArray, monitorIdx);
            if (!MultiThreadedMonitorSupport.singleton().isLockedByCurrentThread(monitor)) {
                return JvmtiError.JVMTI_ERROR_NOT_MONITOR_OWNER;
            }
            JvmtiError err = JvmtiRawMonitorHelper.exitCompletely(monitor);
            if (err != JvmtiError.JVMTI_ERROR_NONE) {
                return err;
            }
            if (!MultiThreadedMonitorSupport.singleton().isLockedByAnyThread(monitor)) {
                return JvmtiError.JVMTI_ERROR_NOT_MONITOR_OWNER;
            }
            NonmovableArrays.setInt(freeIndicesList, --nextAvailableIndex, monitorIdx);
            return JvmtiError.JVMTI_ERROR_NONE;
        } finally {
            mutex.unlock();
        }
    }

    private void initFreeList(NonmovableArray<Integer> freeList) {
        for (int i = 0; i < INITIAL_NB_AVAILABLE_SLOTS; i++) {
            NonmovableArrays.setInt(freeList, i, i);
        }
    }

    private void increaseMonitorArraySize() {
        int newMonitorArraySize = monitorArraySize * ARRAY_GROWTH_FACTOR;

        NonmovableObjectArray<Object> newMonitorArray = NonmovableArrays.createObjectArray(Object[].class, newMonitorArraySize, NmtCategory.JVMTI);
        NonmovableArrays.arraycopy(monitorObjectsArray, 0, newMonitorArray, 0, monitorArraySize);
        NonmovableArrays.releaseUnmanagedArray(monitorObjectsArray);
        monitorObjectsArray = newMonitorArray;

        NonmovableArray<Integer> newFreeIndicesList = NonmovableArrays.createIntArray(newMonitorArraySize, NmtCategory.JVMTI);
        NonmovableArrays.arraycopy(freeIndicesList, 0, newFreeIndicesList, 0, monitorArraySize);
        NonmovableArrays.releaseUnmanagedArray(freeIndicesList);
        freeIndicesList = newFreeIndicesList;

        monitorArraySize = newMonitorArraySize;
    }

    public void tearDown() {
        if (monitorObjectsArray.isNonNull()) {
            NonmovableArrays.releaseUnmanagedArray(monitorObjectsArray);
        }
        if (freeIndicesList.isNonNull()) {
            NonmovableArrays.releaseUnmanagedArray(freeIndicesList);
        }
    }

    private int getNextFreeMonitorIdx() {
        if (nextAvailableIndex + 1 == monitorArraySize) {
            increaseMonitorArraySize();
        }
        return NonmovableArrays.getInt(freeIndicesList, nextAvailableIndex++);
    }

    private boolean isInvalidMonitorIdx(int monitorIdx) {
        if (monitorIdx >= monitorArraySize) {
            return true;
        }
        for (int i = nextAvailableIndex; i < monitorArraySize; i++) {
            if (NonmovableArrays.getInt(freeIndicesList, i) == monitorIdx) {
                return true;
            }
        }
        return false;
    }

}
