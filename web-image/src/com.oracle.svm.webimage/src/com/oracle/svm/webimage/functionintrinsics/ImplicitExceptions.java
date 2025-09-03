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

package com.oracle.svm.webimage.functionintrinsics;

import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;

/**
 * Support methods to throw implicit exceptions. See {@code ByteCodeExceptionLowerer}.
 */
public class ImplicitExceptions {

    public static final SnippetRuntime.SubstrateForeignCallDescriptor CHECK_NULL_POINTER = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "checkNullPointer",
                    ForeignCallDescriptor.CallSideEffect.HAS_SIDE_EFFECT,
                    LocationIdentity.ANY_LOCATION);
    public static final SnippetRuntime.SubstrateForeignCallDescriptor CHECK_ARRAY_BOUND = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "checkArrayBound",
                    ForeignCallDescriptor.CallSideEffect.HAS_SIDE_EFFECT,
                    LocationIdentity.ANY_LOCATION);

    private static final SnippetRuntime.SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SnippetRuntime.SubstrateForeignCallDescriptor[]{CHECK_NULL_POINTER, CHECK_ARRAY_BOUND};

    public static void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(FOREIGN_CALLS);
    }

    @NeverInline("outlining for smaller code size")
    public static NullPointerException createNewNullPointerException() {
        return new NullPointerException();
    }

    @NeverInline("outlining for smaller code size")
    public static ArrayIndexOutOfBoundsException createNewOutOfBoundsException() {
        return new ArrayIndexOutOfBoundsException();
    }

    @NeverInline("outlining for smaller code size")
    public static ArrayIndexOutOfBoundsException createNewOutOfBoundsExceptionWithArgs(int index, int length) {
        /*
         * JDK 11 added the length to the error message, we can do that for all Java versions to be
         * consistent.
         */
        return new ArrayIndexOutOfBoundsException("Index " + index + " out of bounds for length " + length);
    }

    @NeverInline("outlining for smaller code size")
    public static ClassCastException createNewClassCastExceptionWithArgs(Object object, Class<?> expectedClass) {
        assert object != null : "null can be cast to any type, so it cannot show up as a source of a ClassCastException";
        return new ClassCastException(object.getClass().getTypeName() + " cannot be cast to " + expectedClass.getTypeName());
    }

    @NeverInline("outlining for smaller code size")
    public static ArrayStoreException createNewArrayStoreExceptionWithArgs(Object value) {
        assert value != null : "null can be stored into any array, so it cannot show up as a source of an ArrayStoreException";
        return new ArrayStoreException(value.getClass().getTypeName());
    }

    @NeverInline("outlining for smaller code size")
    public static NegativeArraySizeException createNegativeArraySizeException(int length) {
        return new NegativeArraySizeException(String.valueOf(length));
    }

    @NeverInline("outlining for smaller code size")
    public static IncompatibleClassChangeError createNewIncompatibleClassChangeError() {
        return new IncompatibleClassChangeError();
    }

    @NeverInline("outlining for smaller code size")
    public static IllegalArgumentException createNewNegativeLengthException() {
        return new IllegalArgumentException("Negative length");
    }

    @NeverInline("outlining for smaller code size")
    public static IllegalArgumentException createNewArgumentIsNotArrayException() {
        return new IllegalArgumentException("Argument is not an array");
    }

    @NeverInline("outlining for smaller code size")
    public static ArithmeticException createNewArithmeticException() {
        return new ArithmeticException();
    }

    @NeverInline("outlining for smaller code size")
    public static ArithmeticException createNewDivisionByZeroException() {
        return new ArithmeticException("/ by zero");
    }

    @NeverInline("outlining for smaller code size")
    public static AssertionError createNewAssertionErrorNullary() {
        return new AssertionError();
    }

    @NeverInline("outlining for smaller code size")
    public static AssertionError createNewAssertionErrorObject(Object detailMessage) {
        return new AssertionError(detailMessage);
    }

    @NeverInline("outlining for smaller code size")
    public static void throwNewNullPointerException() {
        throw createNewNullPointerException();
    }

    @NeverInline("outlining for smaller code size")
    public static void throwNewOutOfBoundsException() {
        throw createNewOutOfBoundsException();
    }

    @NeverInline("outlining for smaller code size")
    public static void throwNewOutOfBoundsExceptionWithArgs(int index, int length) {
        throw createNewOutOfBoundsExceptionWithArgs(index, length);
    }

    @NeverInline("outlining for smaller code size")
    public static void throwNewClassCastExceptionWithArgs(Object object, Class<?> expectedClass) {
        throw createNewClassCastExceptionWithArgs(object, expectedClass);
    }

    @NeverInline("outlining for smaller code size")
    public static void throwNewArrayStoreExceptionWithArgs(Object value) {
        throw createNewArrayStoreExceptionWithArgs(value);
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    @NeverInline("outlining null checks")
    public static void checkNullPointer(Object obj) {
        if (obj == null) {
            throw createNewNullPointerException();
        }
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    @NeverInline("outlining array bound check")
    public static void checkArrayBound(boolean invalid) {
        if (invalid) {
            throw createNewOutOfBoundsException();
        }
    }
}
