/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

// Checkstyle: stop

/**
 * Definitions manually translated from the C header file sys/mman.h.
 */
@CContext(PosixDirectives.class)
public class Mman {

    @CConstant
    public static native int PROT_READ();

    @CConstant
    public static native int PROT_WRITE();

    @CConstant
    public static native int PROT_EXEC();

    @CConstant
    public static native int PROT_NONE();

    @CConstant
    public static native int MAP_SHARED();

    @CConstant
    public static native int MAP_PRIVATE();

    @CConstant
    public static native int MAP_FIXED();

    @CConstant
    public static native int MAP_ANON();

    @CConstant
    public static native int MAP_NORESERVE();

    @CConstant
    public static native PointerBase MAP_FAILED();

    @CFunction
    public static native Pointer mmap(PointerBase addr, UnsignedWord len, int prot, int flags, int fd, long offset);

    @CFunction
    public static native int munmap(PointerBase addr, UnsignedWord len);

    public static class NoTransitions {
        @CFunction(transition = Transition.NO_TRANSITION)
        public static native Pointer mmap(PointerBase addr, UnsignedWord len, int prot, int flags, int fd, long offset);

        @CFunction(transition = Transition.NO_TRANSITION)
        public static native int munmap(PointerBase addr, UnsignedWord len);

        @CFunction(transition = Transition.NO_TRANSITION)
        public static native int mprotect(PointerBase addr, UnsignedWord len, int prot);
    }
}
