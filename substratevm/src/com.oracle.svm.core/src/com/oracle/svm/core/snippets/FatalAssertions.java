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
package com.oracle.svm.core.snippets;

// Checkstyle: allow reflection

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.util.HashMap;
import java.util.Map;

import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.JavaKind;

/**
 * This class provides allocation-free substitution methods for all constructors of
 * {@link AssertionError}. Foreign calls to these methods are inserted by FatalAssertionsNodePlugin
 * in all code that must be allocation free, i.e., cannot throw a newly allocated assertion error.
 */
public class FatalAssertions {

    public static final Map<Executable, SubstrateForeignCallDescriptor> FOREIGN_CALLS;

    static {
        FOREIGN_CALLS = new HashMap<>();
        for (Constructor<?> c : AssertionError.class.getDeclaredConstructors()) {
            StringBuilder methodName = new StringBuilder("fatalAssertion");
            for (Class<?> parameterType : c.getParameterTypes()) {
                if (parameterType.isPrimitive()) {
                    methodName.append(JavaKind.fromJavaClass(parameterType).name());
                } else {
                    methodName.append(parameterType.getSimpleName());
                }
            }
            FOREIGN_CALLS.put(c, SnippetRuntime.findForeignCall(FatalAssertions.class, methodName.toString(), true));
        }
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an assertion in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void fatalAssertion(@SuppressWarnings("unused") Object receiver) {
        throw VMError.shouldNotReachHere(assertionErrorName());
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an assertion in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void fatalAssertionString(Object receiver, String detailMessage) {
        fatalAssertionObject(receiver, detailMessage);
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an assertion in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void fatalAssertionObject(@SuppressWarnings("unused") Object receiver, Object detailMessage) {
        if (detailMessage instanceof String) {
            runtimeAssertionPrefix().string((String) detailMessage).newline();
        } else {
            /* Do not convert detailMessage to a string, since that requires allocation. */
            runtimeAssertionPrefix().object(detailMessage).newline();
        }
        throw VMError.shouldNotReachHere(assertionErrorName());
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an assertion in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void fatalAssertionBoolean(@SuppressWarnings("unused") Object receiver, boolean detailMessage) {
        runtimeAssertionPrefix().bool(detailMessage).newline();
        throw VMError.shouldNotReachHere(assertionErrorName());
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an assertion in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void fatalAssertionChar(@SuppressWarnings("unused") Object receiver, char detailMessage) {
        runtimeAssertionPrefix().character(detailMessage).newline();
        throw VMError.shouldNotReachHere(assertionErrorName());
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an assertion in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void fatalAssertionInt(@SuppressWarnings("unused") Object receiver, int detailMessage) {
        runtimeAssertionPrefix().signed(detailMessage).newline();
        throw VMError.shouldNotReachHere(assertionErrorName());
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an assertion in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void fatalAssertionLong(@SuppressWarnings("unused") Object receiver, long detailMessage) {
        runtimeAssertionPrefix().signed(detailMessage).newline();
        throw VMError.shouldNotReachHere(assertionErrorName());
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an assertion in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void fatalAssertionFloat(@SuppressWarnings("unused") Object receiver, @SuppressWarnings("unused") float detailMessage) {
        runtimeAssertionPrefix().string("[float number supressed]").newline();
        throw VMError.shouldNotReachHere(assertionErrorName());
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an assertion in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void fatalAssertionDouble(@SuppressWarnings("unused") Object receiver, @SuppressWarnings("unused") double detailMessage) {
        runtimeAssertionPrefix().string("[double number supressed]").newline();
        throw VMError.shouldNotReachHere(assertionErrorName());
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an assertion in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void fatalAssertionStringThrowable(@SuppressWarnings("unused") Object receiver, String detailMessage, Throwable cause) {
        runtimeAssertionPrefix().string(detailMessage).string(" caused by ").object(cause).newline();
        throw VMError.shouldNotReachHere(assertionErrorName());
    }

    private static String assertionErrorName() {
        return AssertionError.class.getName();
    }

    private static Log runtimeAssertionPrefix() {
        return Log.log().string(assertionErrorName()).string(": ");
    }
}
