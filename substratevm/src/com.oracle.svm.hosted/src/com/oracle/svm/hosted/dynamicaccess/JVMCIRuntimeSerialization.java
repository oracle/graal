/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.dynamicaccess;

import java.util.Arrays;

import org.graalvm.nativeimage.hosted.RuntimeSerialization;

import com.oracle.svm.util.OriginalClassProvider;

import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Mirror of {@link RuntimeSerialization} using JVMCI types.
 * <p>
 * This API is deprecated; use {@link JVMCIReflectiveAccess} instead.
 */
public final class JVMCIRuntimeSerialization {
    /**
     * @see RuntimeSerialization#registerIncludingAssociatedClasses(Class)
     */
    public static void registerIncludingAssociatedClasses(ResolvedJavaType type) {
        RuntimeSerialization.registerIncludingAssociatedClasses(OriginalClassProvider.getJavaClass(type));
    }

    /**
     * @see RuntimeSerialization#register(Class...)
     */
    public static void register(ResolvedJavaType... types) {
        for (ResolvedJavaType type : types) {
            RuntimeSerialization.register(OriginalClassProvider.getJavaClass(type));
        }
    }

    /**
     * @see RuntimeSerialization#registerLambdaCapturingClass(Class)
     */
    public static void registerLambdaCapturingClass(ResolvedJavaType lambdaCapturingType) {
        RuntimeSerialization.registerLambdaCapturingClass(OriginalClassProvider.getJavaClass(lambdaCapturingType));
    }

    /**
     * @see RuntimeSerialization#registerProxyClass(Class...)
     */
    public static void registerProxyClass(ResolvedJavaType... implementedInterfaces) {
        Class<?>[] reflectionImplementedInterfaces = Arrays.stream(implementedInterfaces).map(OriginalClassProvider::getJavaClass).toArray(Class[]::new);
        RuntimeSerialization.registerProxyClass(reflectionImplementedInterfaces);
    }

    private JVMCIRuntimeSerialization() {
    }
}
