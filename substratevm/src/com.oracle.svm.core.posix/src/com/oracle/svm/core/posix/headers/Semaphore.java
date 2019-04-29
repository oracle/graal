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
package com.oracle.svm.core.posix.headers;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.Platform.DARWIN;
import org.graalvm.nativeimage.Platform.LINUX;
import org.graalvm.word.PointerBase;

/** Declarations from <semaphore.h>. */
@Platforms({DARWIN.class, LINUX.class})
@CContext(PosixDirectives.class)
@CLibrary("pthread")
public class Semaphore {
    /* { Allow names with underscores: Checkstyle: stop */

    /** An opaque semaphore. */
    public interface sem_t extends PointerBase {
        /* Opaque. */
    }

    /**
     * sem_open() creates a new POSIX semaphore or opens an existing semaphore. The semaphore is
     * identified by name. For details of the construction of name, see sem_overview(7).
     */
    @CFunction
    public static native sem_t sem_open(CCharPointer name, int oflag, int mode, int value);

    /** The value returned by {@link #sem_open} on failure. */
    @CConstant
    public static native sem_t SEM_FAILED();

    /**
     * sem_wait() decrements (locks) the semaphore pointed to by sem. If the semaphore's value is
     * greater than zero, then the decrement proceeds, and the function returns, immediately. If the
     * semaphore currently has the value zero, then the call blocks until either it becomes possible
     * to perform the decrement (i.e., the semaphore value rises above zero), or a signal handler
     * interrupts the call.
     */
    @CFunction
    public static native int sem_wait(sem_t sem);

    /**
     * sem_post() increments (unlocks) the semaphore pointed to by sem. If the semaphore's value
     * consequently becomes greater than zero, then another process or thread blocked in a
     * sem_wait(3) call will be woken up and proceed to lock the semaphore.
     */
    @CFunction(value = "sem_post", transition = CFunction.Transition.NO_TRANSITION)
    public static native int sem_post_no_transition(sem_t sem);

    /**
     * sem_close() closes the named semaphore referred to by sem, allowing any resources that the
     * system has allocated to the calling process for this semaphore to be freed.
     */
    @CFunction
    public static native int sem_close(sem_t sem);

    /**
     * The named semaphore named {@code name} is removed. If the semaphore is in use by other
     * processes, then {@code name} is immediately disassociated with the semaphore, but the
     * semaphore itself will not be removed until all references to it have been closed.
     */
    @CFunction
    public static native int sem_unlink(CCharPointer name);

    /* } Allow names with underscores: Checkstyle: resume */
}
