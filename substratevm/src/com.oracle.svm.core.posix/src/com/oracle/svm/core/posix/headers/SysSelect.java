/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.Platform.DARWIN;
import org.graalvm.nativeimage.Platform.LINUX;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.word.PointerBase;

@Platforms({DARWIN.class, LINUX.class})
@CContext(PosixDirectives.class)
public class SysSelect {
    /* ( Allow names with underscores: Checkstyle: stop */

    /*
     * An fd_set is an opaque set manipulated by macros. E.g., FD_ZERO, FD_SET, etc., that are
     * accessed via C functions in
     * graal/substratevm/src/com.oracle.svm.native.libchelper/src/macrosAsFunctions.c.
     */
    @CStruct
    public interface fd_set extends PointerBase {
    }

    @CConstant
    public static native int FD_SETSIZE();

    @CConstant
    public static native int NFDBITS();

    @CFunction
    public static native int select(int nfds, fd_set readfds, fd_set writefds,
                    fd_set exceptfds, Time.timeval timeout);

    /*
     * Macros from <sys/select.h> made available a C functions via implementations in
     * graal/substratevm/src/com.oracle.svm.native.libchelper/src/macrosAsFunctions.c
     */

    @CFunction(value = "sys_select_FD_SET", transition = Transition.NO_TRANSITION)
    public static native void FD_SET(int fd, PointerBase fdset);

    @CFunction(value = "sys_select_FD_ZERO", transition = Transition.NO_TRANSITION)
    public static native void FD_ZERO(PointerBase fdset);

    /* } Allow names with underscores: Checkstyle: resume */
}
