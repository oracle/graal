/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.os;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.Platform;

/**
 * Whether certain C pre-processor macros are defined on particular platforms. These are annotated
 * with {@link Fold} so they can be used to bracket os-dependent conditional compilation.
 *
 * The use of {@link Fold} does not support cross-compilation.
 */
public class IsDefined {
    /* { Allow names with underscores: Checkstyle: stop */

    @Fold
    public static final boolean isDarwin() {
        return Platform.includedIn(Platform.DARWIN.class);
    }

    @Fold
    public static final boolean isLinux() {
        return Platform.includedIn(Platform.LINUX.class);
    }

    @Fold
    public static final boolean __solaris__() {
        return false;
    }

    @Fold
    public static final boolean __OpenBSD__() {
        return false;
    }

    @Fold
    public static final boolean WIN32() {
        return false;
    }

    @Fold
    public static final boolean _AIX() {
        return false;
    }

    /*
     * Platform-specific #ifdef's
     */

    @Fold
    public static boolean socket_AF_INET6() {
        return isDarwin() || isLinux();
    }

    @Fold
    public static boolean socket_AF_UNIX() {
        return isDarwin() || isLinux();
    }

    /*
     * Apple #ifdefs.
     */

    /** From 'gcc -dM -E empty.h'. */
    @Fold
    public static boolean __APPLE__() {
        return isDarwin();
    }

    /** This is defined in ./common/autoconf/generated-configure.sh in the OpenJDK sources. */
    @Fold
    public static boolean MACOSX() {
        return isDarwin();
    }

    /** This is defined in ./common/autoconf/generated-configure.sh in the OpenJDK sources. */
    @Fold
    public static boolean _ALLBSD_SOURCE() {
        return isDarwin();
    }

    @Fold
    public static boolean sysctl_KIPC_MAXSOCKBUF() {
        return isDarwin();
    }

    /*
     * Linux #ifdefs.
     */

    /** From 'gcc -dM -E empty.h'. */
    @Fold
    public static boolean __linux__() {
        return isLinux();
    }

    /** This is defined in ./common/autoconf/generated-configure.sh in the OpenJDK sources. */
    @Fold
    public static boolean LINUX() {
        return isLinux();
    }

    @Fold
    public static boolean ip_IPTOS_TOS_MASK() {
        return isLinux();
    }

    @Fold
    public static boolean ip_IPTOS_PREC_MASK() {
        return isLinux();
    }

    /*
     * Solaris #ifdefs.
     */

    @Fold
    public static boolean if_LIFNAMSIZ() {
        return __solaris__();
    }

    /* } Allow names with underscores: Checkstyle: resume */
}
