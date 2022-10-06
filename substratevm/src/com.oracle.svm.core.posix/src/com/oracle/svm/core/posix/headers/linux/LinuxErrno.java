/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.headers.linux;

import com.oracle.svm.core.c.libc.LibCSpecific;
import com.oracle.svm.core.c.libc.BionicLibC;
import com.oracle.svm.core.c.libc.GLibC;
import com.oracle.svm.core.c.libc.MuslLibC;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.type.CIntPointer;

// Checkstyle: stop

public class LinuxErrno {

    @LibCSpecific({GLibC.class, MuslLibC.class})
    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native CIntPointer __errno_location();

    @LibCSpecific(BionicLibC.class)
    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native CIntPointer __errno();
}
