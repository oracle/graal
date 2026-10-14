/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;

import com.oracle.svm.core.posix.headers.PosixDirectives;

@CLibrary(value = "libchelper", requireStatic = true)
@CContext(PosixDirectives.class)
public class PosixLibCHelper {
    /**
     * Calls {@code execv} and captures {@code errno} before the return transition to Java can
     * overwrite it. A successful call replaces the process and does not return.
     *
     * @return the captured {@code errno} value on failure
     */
    @CFunction("svm_execv")
    public static native int execv(CCharPointer path, CCharPointerPointer argv);

    /**
     * Calls {@code execve} and captures {@code errno} before the return transition to Java can
     * overwrite it. A successful call replaces the process and does not return.
     *
     * @return the captured {@code errno} value on failure
     */
    @CFunction("svm_execve")
    public static native int execve(CCharPointer path, CCharPointerPointer argv, CCharPointerPointer envp);
}
