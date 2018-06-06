/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;

public class ImplicitExceptions {
    private static final String NO_STACK_MSG = "[no exception stack trace available because exception is thrown from code that must be allocation free]";

    public static final NullPointerException CACHED_NULL_POINTER_EXCEPTION = new NullPointerException(NO_STACK_MSG);
    public static final ArrayIndexOutOfBoundsException CACHED_OUT_OF_BOUNDS_EXCEPTION = new ArrayIndexOutOfBoundsException(NO_STACK_MSG);
    public static final ClassCastException CACHED_CLASS_CAST_EXCEPTION = new ClassCastException(NO_STACK_MSG);
    public static final ArrayStoreException CACHED_ARRAY_STORE_EXCEPTION = new ArrayStoreException(NO_STACK_MSG);
    public static final ArithmeticException CACHED_ARITHMETIC_EXCEPTION = new ArithmeticException(NO_STACK_MSG);

    public static final SubstrateForeignCallDescriptor CREATE_NULL_POINTER_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createNullPointerException", false);
    public static final SubstrateForeignCallDescriptor CREATE_OUT_OF_BOUNDS_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createOutOfBoundsException", false);
    public static final SubstrateForeignCallDescriptor CREATE_CLASS_CAST_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createClassCastException", false);
    public static final SubstrateForeignCallDescriptor CREATE_ARRAY_STORE_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createArrayStoreException", false);
    public static final SubstrateForeignCallDescriptor CREATE_DIVISION_BY_ZERO_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createDivisionByZeroException", false);

    public static final SubstrateForeignCallDescriptor THROW_NEW_NULL_POINTER_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwNewNullPointerException", true);
    public static final SubstrateForeignCallDescriptor THROW_NEW_OUT_OF_BOUNDS_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwNewOutOfBoundsException", true);
    public static final SubstrateForeignCallDescriptor THROW_NEW_CLASS_CAST_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwNewClassCastException", true);
    public static final SubstrateForeignCallDescriptor THROW_NEW_ARRAY_STORE_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwNewArrayStoreException", true);
    public static final SubstrateForeignCallDescriptor THROW_NEW_ARITHMETIC_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwNewArithmeticException", true);

    public static final SubstrateForeignCallDescriptor THROW_CACHED_NULL_POINTER_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwCachedNullPointerException", true);
    public static final SubstrateForeignCallDescriptor THROW_CACHED_OUT_OF_BOUNDS_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwCachedOutOfBoundsException", true);
    public static final SubstrateForeignCallDescriptor THROW_CACHED_CLASS_CAST_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwCachedClassCastException", true);
    public static final SubstrateForeignCallDescriptor THROW_CACHED_ARRAY_STORE_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwCachedArrayStoreException", true);
    public static final SubstrateForeignCallDescriptor THROW_CACHED_ARITHMETIC_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwCachedArithmeticException", true);

    public static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{
                    CREATE_NULL_POINTER_EXCEPTION, CREATE_OUT_OF_BOUNDS_EXCEPTION, CREATE_CLASS_CAST_EXCEPTION, CREATE_ARRAY_STORE_EXCEPTION, CREATE_DIVISION_BY_ZERO_EXCEPTION,
                    THROW_NEW_NULL_POINTER_EXCEPTION, THROW_NEW_OUT_OF_BOUNDS_EXCEPTION, THROW_NEW_CLASS_CAST_EXCEPTION, THROW_NEW_ARRAY_STORE_EXCEPTION, THROW_NEW_ARITHMETIC_EXCEPTION,
                    THROW_CACHED_NULL_POINTER_EXCEPTION, THROW_CACHED_OUT_OF_BOUNDS_EXCEPTION, THROW_CACHED_CLASS_CAST_EXCEPTION, THROW_CACHED_ARRAY_STORE_EXCEPTION, THROW_CACHED_ARITHMETIC_EXCEPTION,
    };

    /** Foreign call: {@link #CREATE_NULL_POINTER_EXCEPTION}. */
    @SubstrateForeignCallTarget
    private static NullPointerException createNullPointerException() {
        return new NullPointerException();
    }

    /** Foreign call: {@link #CREATE_OUT_OF_BOUNDS_EXCEPTION}. */
    @SubstrateForeignCallTarget
    private static ArrayIndexOutOfBoundsException createOutOfBoundsException(int index, int length) {
        /*
         * JDK 11 added the length to the error message, we can do that for all Java versions to be
         * consistent.
         */
        return new ArrayIndexOutOfBoundsException("Index " + index + " out of bounds for length " + length);
    }

    /** Foreign call: {@link #CREATE_CLASS_CAST_EXCEPTION}. */
    @SubstrateForeignCallTarget
    private static ClassCastException createClassCastException(Object object, Class<?> expectedClass) {
        assert object != null : "null can be cast to any type, so it cannot show up as a source of a ClassCastException";
        return new ClassCastException(object.getClass().getTypeName() + " cannot be cast to " + expectedClass.getTypeName());
    }

    /** Foreign call: {@link #CREATE_ARRAY_STORE_EXCEPTION}. */
    @SubstrateForeignCallTarget
    private static ArrayStoreException createArrayStoreException(Object value) {
        assert value != null : "null can be stored into any array, so it cannot show up as a source of an ArrayStoreException";
        return new ArrayStoreException(value.getClass().getTypeName());
    }

    /** Foreign call: {@link #CREATE_DIVISION_BY_ZERO_EXCEPTION}. */
    @SubstrateForeignCallTarget
    private static ArithmeticException createDivisionByZeroException() {
        return new ArithmeticException("/ by zero");
    }

    /** Foreign call: {@link #THROW_NEW_NULL_POINTER_EXCEPTION}. */
    @SubstrateForeignCallTarget
    private static void throwNewNullPointerException() {
        throw new NullPointerException();
    }

    /** Foreign call: {@link #THROW_NEW_OUT_OF_BOUNDS_EXCEPTION}. */
    @SubstrateForeignCallTarget
    private static void throwNewOutOfBoundsException() {
        throw new ArrayIndexOutOfBoundsException();
    }

    /** Foreign call: {@link #THROW_NEW_CLASS_CAST_EXCEPTION}. */
    @SubstrateForeignCallTarget
    private static void throwNewClassCastException() {
        throw new ClassCastException();
    }

    /** Foreign call: {@link #THROW_NEW_ARRAY_STORE_EXCEPTION}. */
    @SubstrateForeignCallTarget
    private static void throwNewArrayStoreException() {
        throw new ArrayStoreException();
    }

    /** Foreign call: {@link #THROW_NEW_ARITHMETIC_EXCEPTION}. */
    @SubstrateForeignCallTarget
    private static void throwNewArithmeticException() {
        throw new ArithmeticException();
    }

    /** Foreign call: {@link #THROW_CACHED_NULL_POINTER_EXCEPTION}. */
    @SubstrateForeignCallTarget
    private static void throwCachedNullPointerException() {
        throw CACHED_NULL_POINTER_EXCEPTION;
    }

    /** Foreign call: {@link #THROW_CACHED_OUT_OF_BOUNDS_EXCEPTION}. */
    @SubstrateForeignCallTarget
    private static void throwCachedOutOfBoundsException() {
        throw CACHED_OUT_OF_BOUNDS_EXCEPTION;
    }

    /** Foreign call: {@link #THROW_CACHED_CLASS_CAST_EXCEPTION}. */
    @SubstrateForeignCallTarget
    private static void throwCachedClassCastException() {
        throw CACHED_CLASS_CAST_EXCEPTION;
    }

    /** Foreign call: {@link #THROW_CACHED_ARRAY_STORE_EXCEPTION}. */
    @SubstrateForeignCallTarget
    private static void throwCachedArrayStoreException() {
        throw CACHED_ARRAY_STORE_EXCEPTION;
    }

    /** Foreign call: {@link #THROW_CACHED_ARITHMETIC_EXCEPTION}. */
    @SubstrateForeignCallTarget
    private static void throwCachedArithmeticException() {
        throw CACHED_ARITHMETIC_EXCEPTION;
    }
}
