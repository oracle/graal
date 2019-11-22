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
package com.oracle.svm.core.posixsubst.headers;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CFieldAddress;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.PointerBase;

//Checkstyle: stop

/**
 * Definitions manually translated from the C header file dirent.h.
 */
@CContext(PosixSubstDirectives.class)
public class Dirent {

    @CStruct(addStructKeyword = true)
    public interface dirent extends PointerBase {
        @CField
        long d_ino();

        @CFieldAddress
        CCharPointer d_name();
    }

    @CPointerTo(dirent.class)
    public interface direntPointer extends PointerBase {
        dirent read();

        void write(dirent value);
    }

    public interface DIR extends PointerBase {
    }

    @CFunction
    public static native DIR opendir(CCharPointer name);

    @CFunction(value = "fdopendir", transition = CFunction.Transition.NO_TRANSITION)
    public static native DIR fdopendir_no_transition(int fd);

    @CFunction
    public static native DIR fdopendir(int fd);

    @CFunction
    public static native int closedir(DIR dirp);

    @CFunction(value = "closedir", transition = CFunction.Transition.NO_TRANSITION)
    public static native int closedir_no_transition(DIR dirp);

    @CFunction
    public static native int readdir_r(DIR dirp, dirent entry, direntPointer result);

    @CFunction(value = "readdir_r", transition = CFunction.Transition.NO_TRANSITION)
    public static native int readdir_r_no_transition(DIR dirp, dirent entry, direntPointer result);
}
