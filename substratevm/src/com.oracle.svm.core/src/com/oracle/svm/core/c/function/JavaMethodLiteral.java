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
package com.oracle.svm.core.c.function;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.impl.MethodLiteralCodePointer;

import com.oracle.svm.core.annotate.InvokeJavaFunctionPointer;

/**
 * Like {@link CEntryPointLiteral}, but for Java calls to methods annotated as
 * {@link InvokeJavaFunctionPointer}.
 */
public final class JavaMethodLiteral<T extends CFunctionPointer> {
    /* Field is accessed using alias. */
    @SuppressWarnings("unused") //
    private CFunctionPointer functionPointer;

    private JavaMethodLiteral(Class<?> definingClass, String methodName, Class<?>... parameterTypes) {
        this.functionPointer = new MethodLiteralCodePointer(false, definingClass, methodName, parameterTypes);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static <T extends CFunctionPointer> JavaMethodLiteral<T> create(Class<?> definingClass, String methodName, Class<?>... parameterTypes) {
        return new JavaMethodLiteral<>(definingClass, methodName, parameterTypes);
    }

    public T getFunctionPointer() {
        throw new IllegalStateException("Cannot invoke method during native image generation");
    }
}
