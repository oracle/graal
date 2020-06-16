/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.type.CIntPointer;

// Checkstyle: stop

/**
 * Definitions manually translated from the C header sys/wait.h.
 */
@CContext(PosixDirectives.class)
public class Wait {

    @CFunction
    public static native int waitpid(int pid, CIntPointer stat_loc, int options);

    /*
     * The following are C macros (sys/wait.h) for interpreting the status from waitpid() and
     * wait(), but their behavior and the composition of the status is the same across platforms so
     * we replicate them (OpenJDK does that too)
     */

    public static boolean WIFEXITED(int status) {
        return (((status) & 0xFF) == 0);
    }

    public static int WEXITSTATUS(int status) {
        return (((status) >> 8) & 0xFF);
    }

    public static boolean WIFSIGNALED(int status) {
        return (((status) & 0xFF) > 0 && ((status) & 0xFF00) == 0);
    }

    public static int WTERMSIG(int status) {
        return ((status) & 0x7F);
    }
}
