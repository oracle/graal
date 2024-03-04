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
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.jni.JNIObjectHandles;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;
import com.oracle.svm.core.jvmti.headers.JThread;
import com.oracle.svm.core.jvmti.headers.JThreadGroup;
import com.oracle.svm.core.jvmti.headers.JvmtiError;
import com.oracle.svm.core.jvmti.headers.JvmtiThreadInfo;
import com.oracle.svm.core.thread.JavaThreads;

public final class JvmtiThreadActionsUtil {

    private JvmtiThreadActionsUtil() {
    }

    public static int interruptThread(JThread jthread) {
        // TODO @dprcci refactor
        Thread thread;
        try {
            Object threadReference = JNIObjectHandles.getObject(jthread);
            thread = (Thread) threadReference;
        } catch (IllegalArgumentException | ClassCastException e) {
            return JvmtiError.JVMTI_ERROR_INVALID_THREAD.getCValue();
        }
        if (thread == null) {
            return JvmtiError.JVMTI_ERROR_INVALID_THREAD.getCValue();
        }
        if (!thread.isAlive()) {
            return JvmtiError.JVMTI_ERROR_THREAD_NOT_ALIVE.getCValue();
        }
        // TODO @dprcci check if works
        try {
            thread.interrupt();
        } catch (SecurityException e) {
            return JvmtiError.JVMTI_ERROR_INTERNAL.getCValue();
        }
        return JvmtiError.JVMTI_ERROR_NONE.getCValue();
    }

    public static JvmtiError getThreadInfo(JThread jthread, JvmtiThreadInfo infoPtr) {
        Thread thread;
        if (jthread.equal(WordFactory.nullPointer())) {
            thread = JavaThreads.getCurrentThreadOrNull();
        } else {
            try {
                Object threadReference = JNIObjectHandles.getObject(jthread);
                thread = (Thread) threadReference;
            } catch (IllegalArgumentException | ClassCastException e) {
                return JvmtiError.JVMTI_ERROR_INVALID_THREAD;
            }
        }
        if (thread == null) {
            return JvmtiError.JVMTI_ERROR_INVALID_THREAD;
        }

        String name = thread.getName();
        int priority = thread.getPriority();
        boolean isDaemon = thread.isDaemon();
        ThreadGroup threadGroup = thread.getThreadGroup();
        ClassLoader contextClassLoader = thread.getContextClassLoader();

        int nameModifiedUTF8Length = UninterruptibleUtils.String.modifiedUTF8Length(name, true);
        CCharPointer nameBuffer = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(WordFactory.unsigned(nameModifiedUTF8Length));
        UninterruptibleUtils.String.toModifiedUTF8(name, (Pointer) nameBuffer, ((Pointer) nameBuffer).add(nameModifiedUTF8Length), true);

        JThreadGroup threadGroupHandle = (JThreadGroup) JNIObjectHandles.createLocal(threadGroup);
        JNIObjectHandle contextClassLoaderHandle = JNIObjectHandles.createLocal(contextClassLoader);

        infoPtr.setName(nameBuffer);
        infoPtr.setPriority(priority);
        infoPtr.setIsDaemon(isDaemon);
        infoPtr.setThreadGroup(threadGroupHandle);
        infoPtr.setContextClassLoader(contextClassLoaderHandle);

        return JvmtiError.JVMTI_ERROR_NONE;
    }

}
