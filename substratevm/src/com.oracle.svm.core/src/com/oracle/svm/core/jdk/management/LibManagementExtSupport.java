/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk.management;

import static com.oracle.svm.core.c.function.CEntryPointOptions.NoEpilogue;
import static com.oracle.svm.core.c.function.CEntryPointOptions.NoPrologue;
import static org.graalvm.nativeimage.c.function.CFunction.Transition.NO_TRANSITION;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.Publish;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.headers.LibC;

public final class LibManagementExtSupport {
    private static final CGlobalData<CCharPointer> ERRMSG_FORMAT = CGlobalDataFactory.createCString("errno: %d error: %s\n");

    /**
     * Reimplementation of the native {@code throw_internal_error} function in Java.
     */
    @Uninterruptible(reason = "No Java context.")
    @CEntryPoint(name = "throw_internal_error", include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.SymbolOnly)
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class)
    private static void throwInternalError(IsolateThread env, CCharPointer msg) {
        /*
         * Ported from `src/jdk.management/share/native/libmanagement_ext/management_ext.c`.
         */
        CCharPointer errmsg = StackValue.get(128);

        snprintf(errmsg, WordFactory.unsigned(128), ERRMSG_FORMAT.get(), LibC.errno(), msg);
        jnuThrowInternalError(env, errmsg);
    }

    @CFunction(transition = NO_TRANSITION)
    private static native int snprintf(CCharPointer str, UnsignedWord size, CCharPointer format, int errno, CCharPointer msg);

    @CLibrary(value = "java", requireStatic = true)
    @CFunction(value = "JNU_ThrowInternalError", transition = NO_TRANSITION)
    private static native void jnuThrowInternalError(IsolateThread env, CCharPointer msg);
}
