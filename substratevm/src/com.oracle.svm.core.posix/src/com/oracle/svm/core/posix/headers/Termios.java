/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.nativeimage.c.struct.AllowNarrowingCast;
import org.graalvm.nativeimage.c.struct.AllowWideningCast;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.word.PointerBase;

/** Definitions manually translated from the C header file termios.h. */
@CContext(PosixDirectives.class)
public class Termios {
    // { Allow names with all upper-case and underscores: Checkstyle: stop

    @CStruct(addStructKeyword = true)
    public interface termios extends PointerBase {

        //
        // typedef unsigned int tcflag_t;
        // struct termios {
        // ...
        // tcflag_t c_lflag; /* local mode flags */
        // ...
        // };
        //

        @CField
        @AllowWideningCast
        long get_c_lflag();

        @CField
        @AllowNarrowingCast
        void set_c_lflag(long value);
    }

    @CConstant
    public static native int ECHO();

    @CConstant
    public static native int TCSANOW();

    /** Get the terminal attributes. */
    @CFunction
    public static native int tcgetattr(int fd, Termios.termios termios_p);

    /** Set the terminal attributes. */
    @CFunction
    public static native int tcsetattr(int fd, int optional_actions, Termios.termios termios_p);

    // } Allow names with all upper-case and underscores: Checkstyle: resume
}
