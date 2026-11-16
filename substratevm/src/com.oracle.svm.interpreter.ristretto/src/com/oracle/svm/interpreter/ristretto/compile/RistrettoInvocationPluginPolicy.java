/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.ristretto.compile;

import java.util.Set;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Invocation-plugin allowances for bytecodes covered by exception handlers in the current method.
 * Compiler directives retain their graph semantics. Other entries either need no Java exception
 * successor or preserve exception behavior through exception-aware nodes, parser checks, or valid
 * deoptimization state. Assertion-status calls retain their dynamic invocation.
 */
public final class RistrettoInvocationPluginPolicy {
    private static final String GRAAL_DIRECTIVES_CLASS_NAME = "jdk.graal.compiler.api.directives.GraalDirectives";
    private static final String RISTRETTO_DIRECTIVES_CLASS_NAME = "com.oracle.svm.interpreter.ristretto.RistrettoDirectives";

    private static final Set<String> METHODS_ALLOWED_WITH_LOCAL_EXCEPTION_HANDLER = Set.of(
                    "java.lang.Object.clone()",
                    "java.lang.Integer.reverseBytes(int)",
                    "java.lang.Integer.reverse(int)",
                    "java.lang.Integer.bitCount(int)",
                    "java.lang.Integer.numberOfLeadingZeros(int)",
                    "java.lang.Integer.numberOfTrailingZeros(int)",
                    "java.lang.Integer.compareUnsigned(int, int)",
                    "java.lang.Long.reverseBytes(long)",
                    "java.lang.Long.reverse(long)",
                    "java.lang.Long.bitCount(long)",
                    "java.lang.Long.numberOfLeadingZeros(long)",
                    "java.lang.Long.numberOfTrailingZeros(long)",
                    "java.lang.Long.compareUnsigned(long, long)",
                    "java.lang.Float.floatToRawIntBits(float)",
                    "java.lang.Float.floatToIntBits(float)",
                    "java.lang.Float.intBitsToFloat(int)",
                    "java.lang.Double.doubleToRawLongBits(double)",
                    "java.lang.Double.doubleToLongBits(double)",
                    "java.lang.Double.longBitsToDouble(long)",
                    "java.lang.Math.abs(int)",
                    "java.lang.Math.abs(long)",
                    "java.lang.Math.abs(float)",
                    "java.lang.Math.abs(double)",
                    "java.lang.Math.sqrt(double)",
                    "java.lang.System.getSecurityManager()",
                    "java.lang.invoke.MethodHandles.lookup()",
                    "java.lang.Class.getName()",
                    "java.lang.Class.desiredAssertionStatus()",
                    "java.lang.Object.getClass()",
                    "java.lang.Class.isArray()",
                    "java.lang.Boolean.booleanValue()",
                    "java.lang.Byte.byteValue()",
                    "java.lang.Short.shortValue()",
                    "java.lang.Character.charValue()",
                    "java.lang.Integer.intValue()",
                    "java.lang.Long.longValue()",
                    "java.lang.Float.floatValue()",
                    "java.lang.Double.doubleValue()");

    /**
     * Returns the immutable set of explicit allowances, formatted as {@code %H.%n(%p)}. Compiler
     * directive families are allowed separately and are not enumerated in this set.
     */
    public static Set<String> getMethodsAllowedWithLocalExceptionHandler() {
        return METHODS_ALLOWED_WITH_LOCAL_EXCEPTION_HANDLER;
    }

    /**
     * Returns whether the target's plugin may be attempted under a local exception handler. A plugin
     * may still decline, for example when a constant-folding requirement is not met.
     */
    public static boolean canApplyWithLocalExceptionHandler(ResolvedJavaMethod targetMethod) {
        String methodName = targetMethod.format("%H.%n(%p)");
        return methodName.startsWith(GRAAL_DIRECTIVES_CLASS_NAME + ".") || methodName.startsWith(RISTRETTO_DIRECTIVES_CLASS_NAME + ".") ||
                        METHODS_ALLOWED_WITH_LOCAL_EXCEPTION_HANDLER.contains(methodName);
    }
}
