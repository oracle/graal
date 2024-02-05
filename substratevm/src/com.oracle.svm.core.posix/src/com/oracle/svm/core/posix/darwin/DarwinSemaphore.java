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

package com.oracle.svm.core.posix.darwin;

// Checkstyle: stop

import com.oracle.svm.core.posix.headers.PosixDirectives;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.PointerBase;

/**
 * Manually translated definitions from the C header file semaphore.h.
 */
@CContext(PosixDirectives.class)
public class DarwinSemaphore {

    @CConstant
    public static native int KERN_ABORTED();

    @CConstant
    public static native int SYNC_POLICY_FIFO();

    @CPointerTo(semaphore_t.class)
    public interface semaphore_tPointer extends PointerBase {
        semaphore_t read();
    }

    @CStruct(value = "semaphore", addStructKeyword = true, isIncomplete = true)
    public interface semaphore_t extends PointerBase {
    }

    @CFunction(transition = CFunction.Transition.TO_NATIVE)
    public static native int semaphore_wait(semaphore_t semaphore);

    public static class NoTransition {
        @CFunction(transition = CFunction.Transition.NO_TRANSITION)
        public static native int semaphore_create(WordPointer task, semaphore_tPointer semaphore, int policy, int value);

        @CFunction(transition = CFunction.Transition.NO_TRANSITION)
        public static native int semaphore_destroy(WordPointer task, semaphore_t semaphore);

        @CFunction(transition = CFunction.Transition.NO_TRANSITION)
        public static native int semaphore_signal(semaphore_t semaphore);
    }
}
