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
package com.oracle.svm.core.jdk;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "java.lang.invoke.MethodHandle")
final class Target_java_lang_invoke_MethodHandle {

    /*
     * We are defensive and handle native methods by marking them as deleted. If they are reachable,
     * the user is certainly doing something wrong. But we do not want to fail with a linking error.
     */

    @Delete
    private native Object invokeExact(Object... args) throws Throwable;

    @Delete
    private native Object invoke(Object... args) throws Throwable;

    @Delete
    private native Object invokeBasic(Object... args) throws Throwable;

    @Delete
    private static native Object linkToVirtual(Object... args) throws Throwable;

    @Delete
    private static native Object linkToStatic(Object... args) throws Throwable;

    @Delete
    private static native Object linkToSpecial(Object... args) throws Throwable;

    @Delete
    private static native Object linkToInterface(Object... args) throws Throwable;
}
