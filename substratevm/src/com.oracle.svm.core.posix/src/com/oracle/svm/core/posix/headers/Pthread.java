/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.headers;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.posix.headers.Time.timespec;
import com.oracle.svm.core.thread.VMThreads.OSThreadHandle;
import com.oracle.svm.core.thread.VMThreads.OSThreadId;

// Checkstyle: stop

/**
 * Definitions manually translated from the C header file pthread.h.
 */
@CContext(PosixDirectives.class)
@CLibrary("pthread")
public class Pthread {

    public interface pthread_t extends OSThreadHandle, OSThreadId {
    }

    @CPointerTo(nameOfCType = "pthread_t")
    public interface pthread_tPointer extends PointerBase {
        pthread_t read();
    }

    @CStruct
    public interface pthread_attr_t extends PointerBase {
    }

    @CStruct
    public interface pthread_mutex_t extends PointerBase {
    }

    public interface pthread_mutexattr_t extends PointerBase {
    }

    @CStruct
    public interface pthread_cond_t extends PointerBase {
    }

    @CStruct
    public interface pthread_condattr_t extends PointerBase {
    }

    @CConstant
    public static native int PTHREAD_CREATE_JOINABLE();

    @CConstant
    public static native UnsignedWord PTHREAD_STACK_MIN();

    @CFunction
    public static native int pthread_create(pthread_tPointer newthread, pthread_attr_t attr, WordBase start_routine, WordBase arg);

    @CFunction
    public static native int pthread_join(pthread_t th, WordPointer thread_return);

    @CFunction(value = "pthread_join", transition = Transition.NO_TRANSITION)
    public static native int pthread_join_no_transition(pthread_t th, WordPointer thread_return);

    @CFunction(transition = Transition.NO_TRANSITION)
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static native pthread_t pthread_self();

    @CFunction
    public static native int pthread_attr_init(pthread_attr_t attr);

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int pthread_attr_destroy(pthread_attr_t attr);

    @CFunction
    public static native int pthread_attr_setdetachstate(pthread_attr_t attr, int detachstate);

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int pthread_attr_getguardsize(pthread_attr_t attr, WordPointer guardsize);

    @CFunction
    public static native int pthread_attr_setstacksize(pthread_attr_t attr, UnsignedWord stacksize);

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int pthread_attr_getstack(pthread_attr_t attr, WordPointer stackaddr, WordPointer stacksize);

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int pthread_getattr_np(pthread_t th, pthread_attr_t attr);

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int pthread_mutex_init(pthread_mutex_t mutex, pthread_mutexattr_t mutexattr);

    @CFunction(transition = Transition.TO_NATIVE)
    public static native int pthread_mutex_lock(pthread_mutex_t mutex);

    @CFunction(value = "pthread_mutex_lock", transition = Transition.NO_TRANSITION)
    public static native int pthread_mutex_lock_no_transition(pthread_mutex_t mutex);

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int pthread_mutex_unlock(pthread_mutex_t mutex);

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int pthread_cond_init(pthread_cond_t cond, pthread_condattr_t cond_attr);

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int pthread_cond_signal(pthread_cond_t cond);

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int pthread_cond_broadcast(pthread_cond_t cond);

    @CFunction
    public static native int pthread_cond_wait(pthread_cond_t cond, pthread_mutex_t mutex);

    @CFunction(value = "pthread_cond_wait", transition = Transition.NO_TRANSITION)
    public static native int pthread_cond_wait_no_transition(pthread_cond_t cond, pthread_mutex_t mutex);

    @CFunction
    public static native int pthread_cond_timedwait(pthread_cond_t cond, pthread_mutex_t mutex, timespec abstime);

    @CFunction(value = "pthread_cond_timedwait", transition = Transition.NO_TRANSITION)
    public static native int pthread_cond_timedwait_no_transition(pthread_cond_t cond, pthread_mutex_t mutex, timespec abstime);

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int pthread_condattr_init(pthread_condattr_t attr);

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int pthread_condattr_setclock(pthread_condattr_t attr, int clock_id);

    @CFunction
    public static native int pthread_kill(pthread_t thread, Signal.SignalEnum sig);
}
