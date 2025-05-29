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
package com.oracle.svm.core.posix.linux;

import com.oracle.svm.core.posix.headers.PosixDirectives;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import jdk.graal.compiler.api.replacements.Fold;

import org.graalvm.nativeimage.c.function.CLibrary;

// Checkstyle: stop

@CLibrary(value = "libchelper", requireStatic = true)
@CContext(PosixDirectives.class)
public class LinuxLibCHelper {
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int getThreadId();

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native long getThreadUserTimeSlow(int tid);

    @CConstant
    public static native int MREMAP_FIXED();

    @CConstant
    public static native int MREMAP_MAYMOVE();

    @Fold
    public static int MREMAP_DONTUNMAP() {
        /*
         * Hardcode this constant, as it was introduced in Linux 5.7 and may not always be available
         * on a target system. If so, an mremap call fails with EINVAL.
         */
        return 4;
    }

    public static class NoTransitions {
        @CFunction(transition = Transition.NO_TRANSITION)
        public static native Pointer mremapP(PointerBase oldAddress, UnsignedWord oldSize, UnsignedWord newSize, int flags, PointerBase newAddress);
    }
}
