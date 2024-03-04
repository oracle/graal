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
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.jni.JNIObjectHandles;
import com.oracle.svm.core.jvmti.headers.JThread;
import com.oracle.svm.core.jvmti.headers.JvmtiError;
import com.oracle.svm.core.jvmti.headers.VoidPointerPointer;
import com.oracle.svm.core.jvmti.utils.JvmtiLocalStorageUtil;

import jdk.graal.compiler.api.replacements.Fold;

public final class JvmtiThreadLocalStorage extends JvmtiLocalStorageUtil<JThread, VoidPointer> {

    private static final int INITIAL_CAPACITY = 8;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JvmtiThreadLocalStorage() {
        super("Jvmti Thread Local Storage");
    }

    public void initialize() {
        super.initialize(INITIAL_CAPACITY);
    }

    @Fold
    public static JvmtiThreadLocalStorage singleton() {
        return ImageSingletons.lookup(JvmtiThreadLocalStorage.class);
    }

    public static JvmtiError setThreadLocalStorage(JThread thread, VoidPointer data) {
        JvmtiError threadError = isValidThread(thread);
        if (threadError != JvmtiError.JVMTI_ERROR_NONE) {
            return threadError;
        }
        return singleton().setThreadLocalStorageInternal(thread, data);
    }

    public static JvmtiError getThreadLocalStorage(JThread thread, VoidPointerPointer dataPtr) {
        JvmtiError threadError = isValidThread(thread);
        if (threadError != JvmtiError.JVMTI_ERROR_NONE) {
            return threadError;
        }
        return singleton().getThreadLocalStorageInternal(thread, dataPtr);
    }

    private JvmtiError getThreadLocalStorageInternal(JThread thread, VoidPointerPointer dataPtr) {
        thread = ensureCorrectJThread(thread);
        VoidPointer data = super.contains(thread) ? super.get(thread) : WordFactory.nullPointer();
        ((Pointer) dataPtr).writeWord(0, data);
        return JvmtiError.JVMTI_ERROR_NONE;
    }

    private JvmtiError setThreadLocalStorageInternal(JThread thread, VoidPointer data) {
        thread = ensureCorrectJThread(thread);
        super.put(thread, data);
        return JvmtiError.JVMTI_ERROR_NONE;
    }

    private static JThread ensureCorrectJThread(JThread thread) {
        return thread.equal(WordFactory.nullPointer()) ? (JThread) JNIObjectHandles.createLocal(Thread.currentThread()) : thread;
    }

    private static JvmtiError isValidThread(JThread jthread) {
        Thread thread;
        try {
            Object threadReference = JNIObjectHandles.getObject(jthread);
            thread = (Thread) threadReference;
        } catch (IllegalArgumentException | ClassCastException e) {
            return JvmtiError.JVMTI_ERROR_INVALID_THREAD;
        }
        if (thread == null) {
            thread = Thread.currentThread();
        }
        if (!thread.isAlive()) {
            return JvmtiError.JVMTI_ERROR_THREAD_NOT_ALIVE;
        }
        return JvmtiError.JVMTI_ERROR_NONE;
    }
}
