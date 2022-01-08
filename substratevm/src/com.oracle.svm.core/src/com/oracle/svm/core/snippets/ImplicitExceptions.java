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

import java.lang.reflect.GenericSignatureFormatError;

import com.oracle.svm.core.SubstrateDiagnostics;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.code.FactoryMethodMarker;
import com.oracle.svm.core.jdk.InternalVMMethod;
import com.oracle.svm.core.jdk.StackTraceUtils;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalInt;
import com.oracle.svm.core.util.VMError;

/**
 * This class contains support methods to throw implicit exceptions of according to the
 * specification of Java bytecode.
 *
 * All methods in this class are an implementation detail that should not be observable by users,
 * therefore these methods are filtered in exception stack traces (see {@link StackTraceUtils}).
 */
@InternalVMMethod
@FactoryMethodMarker
public class ImplicitExceptions {
    public static final String NO_STACK_MSG = "[no exception stack trace available because exception is thrown from code that must be allocation free]";

    public static final NullPointerException CACHED_NULL_POINTER_EXCEPTION = new NullPointerException(NO_STACK_MSG);
    public static final ArrayIndexOutOfBoundsException CACHED_OUT_OF_BOUNDS_EXCEPTION = new ArrayIndexOutOfBoundsException(NO_STACK_MSG);
    public static final ClassCastException CACHED_CLASS_CAST_EXCEPTION = new ClassCastException(NO_STACK_MSG);
    public static final ArrayStoreException CACHED_ARRAY_STORE_EXCEPTION = new ArrayStoreException(NO_STACK_MSG);
    public static final IllegalArgumentException CACHED_ILLEGAL_ARGUMENT_EXCEPTION = new IllegalArgumentException(NO_STACK_MSG);
    public static final NegativeArraySizeException CACHED_NEGATIVE_ARRAY_SIZE_EXCEPTION = new NegativeArraySizeException(NO_STACK_MSG);
    public static final ArithmeticException CACHED_ARITHMETIC_EXCEPTION = new ArithmeticException(NO_STACK_MSG);
    public static final AssertionError CACHED_ASSERTION_ERROR = new AssertionError(NO_STACK_MSG);

    public static final SubstrateForeignCallDescriptor CREATE_NULL_POINTER_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createNullPointerException", false);
    public static final SubstrateForeignCallDescriptor CREATE_OUT_OF_BOUNDS_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createOutOfBoundsException", false);
    public static final SubstrateForeignCallDescriptor CREATE_INTRINSIC_OUT_OF_BOUNDS_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createIntrinsicOutOfBoundsException",
                    false);
    public static final SubstrateForeignCallDescriptor CREATE_CLASS_CAST_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createClassCastException", false);
    public static final SubstrateForeignCallDescriptor CREATE_ARRAY_STORE_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createArrayStoreException", false);
    public static final SubstrateForeignCallDescriptor CREATE_ILLEGAL_ARGUMENT_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createIllegalArgumentException", false);
    public static final SubstrateForeignCallDescriptor CREATE_NEGATIVE_ARRAY_SIZE_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createNegativeArraySizeException", false);
    public static final SubstrateForeignCallDescriptor CREATE_DIVISION_BY_ZERO_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createDivisionByZeroException", false);
    public static final SubstrateForeignCallDescriptor CREATE_ASSERTION_ERROR_NULLARY = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createAssertionErrorNullary", false);
    public static final SubstrateForeignCallDescriptor CREATE_ASSERTION_ERROR_OBJECT = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createAssertionErrorObject", false);

    public static final SubstrateForeignCallDescriptor THROW_NEW_NULL_POINTER_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwNewNullPointerException", true);
    public static final SubstrateForeignCallDescriptor THROW_NEW_INTRINSIC_OUT_OF_BOUNDS_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwNewIntrinsicOutOfBoundsException",
                    true);
    public static final SubstrateForeignCallDescriptor THROW_NEW_OUT_OF_BOUNDS_EXCEPTION_WITH_ARGS = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwNewOutOfBoundsExceptionWithArgs",
                    true);
    public static final SubstrateForeignCallDescriptor THROW_NEW_CLASS_CAST_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwNewClassCastException", true);
    public static final SubstrateForeignCallDescriptor THROW_NEW_CLASS_CAST_EXCEPTION_WITH_ARGS = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwNewClassCastExceptionWithArgs", true);
    public static final SubstrateForeignCallDescriptor THROW_NEW_ARRAY_STORE_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwNewArrayStoreException", true);
    public static final SubstrateForeignCallDescriptor THROW_NEW_ARRAY_STORE_EXCEPTION_WITH_ARGS = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwNewArrayStoreExceptionWithArgs",
                    true);
    public static final SubstrateForeignCallDescriptor THROW_NEW_ILLEGAL_ARGUMENT_EXCEPTION_WITH_ARGS = SnippetRuntime.findForeignCall(ImplicitExceptions.class,
                    "throwNewIllegalArgumentExceptionWithArgs", true);
    public static final SubstrateForeignCallDescriptor THROW_NEW_NEGATIVE_ARRAY_SIZE_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwNewNegativeArraySizeException", true);
    public static final SubstrateForeignCallDescriptor THROW_NEW_ARITHMETIC_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwNewArithmeticException", true);
    public static final SubstrateForeignCallDescriptor THROW_NEW_DIVISION_BY_ZERO_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwNewDivisionByZeroException", true);
    public static final SubstrateForeignCallDescriptor THROW_NEW_ASSERTION_ERROR_NULLARY = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwNewAssertionErrorNullary", true);
    public static final SubstrateForeignCallDescriptor THROW_NEW_ASSERTION_ERROR_OBJECT = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwNewAssertionErrorObject", true);

    public static final SubstrateForeignCallDescriptor GET_CACHED_NULL_POINTER_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "getCachedNullPointerException", false);
    public static final SubstrateForeignCallDescriptor GET_CACHED_OUT_OF_BOUNDS_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "getCachedOutOfBoundsException", false);
    public static final SubstrateForeignCallDescriptor GET_CACHED_CLASS_CAST_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "getCachedClassCastException", false);
    public static final SubstrateForeignCallDescriptor GET_CACHED_ARRAY_STORE_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "getCachedArrayStoreException", false);
    public static final SubstrateForeignCallDescriptor GET_CACHED_ILLEGAL_ARGUMENT_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "getCachedIllegalArgumentException", false);
    public static final SubstrateForeignCallDescriptor GET_CACHED_NEGATIVE_ARRAY_SIZE_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "getCachedNegativeArraySizeException",
                    false);
    public static final SubstrateForeignCallDescriptor GET_CACHED_ARITHMETIC_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "getCachedArithmeticException", false);
    public static final SubstrateForeignCallDescriptor GET_CACHED_ASSERTION_ERROR = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "getCachedAssertionError", false);

    public static final SubstrateForeignCallDescriptor THROW_CACHED_NULL_POINTER_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwCachedNullPointerException", true);
    public static final SubstrateForeignCallDescriptor THROW_CACHED_OUT_OF_BOUNDS_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwCachedOutOfBoundsException", true);
    public static final SubstrateForeignCallDescriptor THROW_CACHED_CLASS_CAST_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwCachedClassCastException", true);
    public static final SubstrateForeignCallDescriptor THROW_CACHED_ARRAY_STORE_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwCachedArrayStoreException", true);
    public static final SubstrateForeignCallDescriptor THROW_CACHED_ILLEGAL_ARGUMENT_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwCachedIllegalArgumentException", true);
    public static final SubstrateForeignCallDescriptor THROW_CACHED_NEGATIVE_ARRAY_SIZE_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwCachedNegativeArraySizeException",
                    true);
    public static final SubstrateForeignCallDescriptor THROW_CACHED_ARITHMETIC_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwCachedArithmeticException", true);
    public static final SubstrateForeignCallDescriptor THROW_CACHED_ASSERTION_ERROR = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwCachedAssertionError", true);

    public static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{
                    CREATE_NULL_POINTER_EXCEPTION, CREATE_OUT_OF_BOUNDS_EXCEPTION, CREATE_INTRINSIC_OUT_OF_BOUNDS_EXCEPTION, CREATE_CLASS_CAST_EXCEPTION, CREATE_ARRAY_STORE_EXCEPTION,
                    CREATE_ILLEGAL_ARGUMENT_EXCEPTION, CREATE_NEGATIVE_ARRAY_SIZE_EXCEPTION,
                    CREATE_DIVISION_BY_ZERO_EXCEPTION, CREATE_ASSERTION_ERROR_NULLARY, CREATE_ASSERTION_ERROR_OBJECT,
                    THROW_NEW_NULL_POINTER_EXCEPTION, THROW_NEW_INTRINSIC_OUT_OF_BOUNDS_EXCEPTION, THROW_NEW_CLASS_CAST_EXCEPTION, THROW_NEW_ARRAY_STORE_EXCEPTION, THROW_NEW_ARITHMETIC_EXCEPTION,
                    THROW_NEW_OUT_OF_BOUNDS_EXCEPTION_WITH_ARGS, THROW_NEW_CLASS_CAST_EXCEPTION_WITH_ARGS, THROW_NEW_ARRAY_STORE_EXCEPTION_WITH_ARGS, THROW_NEW_ILLEGAL_ARGUMENT_EXCEPTION_WITH_ARGS,
                    THROW_NEW_NEGATIVE_ARRAY_SIZE_EXCEPTION, THROW_NEW_DIVISION_BY_ZERO_EXCEPTION, THROW_NEW_ASSERTION_ERROR_NULLARY, THROW_NEW_ASSERTION_ERROR_OBJECT,
                    GET_CACHED_NULL_POINTER_EXCEPTION, GET_CACHED_OUT_OF_BOUNDS_EXCEPTION, GET_CACHED_CLASS_CAST_EXCEPTION, GET_CACHED_ARRAY_STORE_EXCEPTION, GET_CACHED_ILLEGAL_ARGUMENT_EXCEPTION,
                    GET_CACHED_NEGATIVE_ARRAY_SIZE_EXCEPTION, GET_CACHED_ARITHMETIC_EXCEPTION, GET_CACHED_ASSERTION_ERROR,
                    THROW_CACHED_NULL_POINTER_EXCEPTION, THROW_CACHED_OUT_OF_BOUNDS_EXCEPTION, THROW_CACHED_CLASS_CAST_EXCEPTION, THROW_CACHED_ARRAY_STORE_EXCEPTION,
                    THROW_CACHED_ILLEGAL_ARGUMENT_EXCEPTION, THROW_CACHED_NEGATIVE_ARRAY_SIZE_EXCEPTION, THROW_CACHED_ARITHMETIC_EXCEPTION, THROW_CACHED_ASSERTION_ERROR,
    };

    private static final FastThreadLocalInt implicitExceptionsAreFatal = FastThreadLocalFactory.createInt("ImplicitExceptions.implicitExceptionsAreFatal");

    /**
     * Switch the current thread into a mode where implicit exceptions such as NullPointerException
     * are fatal errors. This is useful to diagnose errors in code where such exceptions are fatal
     * anyway, but allocation is not possible so no exception stack trace can be filled in - for
     * example the GC.
     */
    public static void activateImplicitExceptionsAreFatal() {
        implicitExceptionsAreFatal.set(implicitExceptionsAreFatal.get() + 1);
    }

    /**
     * The reverse operation of {@link #activateImplicitExceptionsAreFatal()}.
     */
    public static void deactivateImplicitExceptionsAreFatal() {
        implicitExceptionsAreFatal.set(implicitExceptionsAreFatal.get() - 1);
    }

    private static void vmErrorIfImplicitExceptionsAreFatal() {
        if ((implicitExceptionsAreFatal.get() > 0 || ExceptionUnwind.exceptionsAreFatal()) && !SubstrateDiagnostics.isFatalErrorHandlingThread()) {
            throw VMError.shouldNotReachHere("Implicit exception thrown in code where such exceptions are fatal errors");
        }
    }

    /** Foreign call: {@link #CREATE_NULL_POINTER_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static NullPointerException createNullPointerException() {
        vmErrorIfImplicitExceptionsAreFatal();
        return new NullPointerException();
    }

    /** Foreign call: {@link #CREATE_OUT_OF_BOUNDS_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static ArrayIndexOutOfBoundsException createIntrinsicOutOfBoundsException() {
        vmErrorIfImplicitExceptionsAreFatal();
        return new ArrayIndexOutOfBoundsException();
    }

    /** Foreign call: {@link #CREATE_OUT_OF_BOUNDS_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static ArrayIndexOutOfBoundsException createOutOfBoundsException(int index, int length) {
        vmErrorIfImplicitExceptionsAreFatal();
        /*
         * JDK 11 added the length to the error message, we can do that for all Java versions to be
         * consistent.
         */
        return new ArrayIndexOutOfBoundsException("Index " + index + " out of bounds for length " + length);
    }

    /** Foreign call: {@link #CREATE_CLASS_CAST_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static ClassCastException createClassCastException(Object object, Class<?> expectedClass) {
        assert object != null : "null can be cast to any type, so it cannot show up as a source of a ClassCastException";
        vmErrorIfImplicitExceptionsAreFatal();
        return new ClassCastException(object.getClass().getTypeName() + " cannot be cast to " + expectedClass.getTypeName());
    }

    /** Foreign call: {@link #CREATE_ARRAY_STORE_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static ArrayStoreException createArrayStoreException(Object value) {
        assert value != null : "null can be stored into any array, so it cannot show up as a source of an ArrayStoreException";
        vmErrorIfImplicitExceptionsAreFatal();
        return new ArrayStoreException(value.getClass().getTypeName());
    }

    /** Foreign call: {@link #CREATE_ILLEGAL_ARGUMENT_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static IllegalArgumentException createIllegalArgumentException(String message) {
        vmErrorIfImplicitExceptionsAreFatal();
        return new IllegalArgumentException(message);
    }

    /** Foreign call: {@link #CREATE_NEGATIVE_ARRAY_SIZE_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static NegativeArraySizeException createNegativeArraySizeException(int length) {
        vmErrorIfImplicitExceptionsAreFatal();
        return new NegativeArraySizeException(String.valueOf(length));
    }

    /** Foreign call: {@link #CREATE_DIVISION_BY_ZERO_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static ArithmeticException createDivisionByZeroException() {
        vmErrorIfImplicitExceptionsAreFatal();
        return new ArithmeticException("/ by zero");
    }

    /** Foreign call: {@link #CREATE_ASSERTION_ERROR_NULLARY}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static AssertionError createAssertionErrorNullary() {
        vmErrorIfImplicitExceptionsAreFatal();
        return new AssertionError();
    }

    /** Foreign call: {@link #CREATE_ASSERTION_ERROR_OBJECT}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static AssertionError createAssertionErrorObject(Object detailMessage) {
        vmErrorIfImplicitExceptionsAreFatal();
        return new AssertionError(detailMessage);
    }

    /** Foreign call: {@link #THROW_NEW_NULL_POINTER_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwNewNullPointerException() {
        vmErrorIfImplicitExceptionsAreFatal();
        throw new NullPointerException();
    }

    /** Foreign call: {@link #THROW_NEW_INTRINSIC_OUT_OF_BOUNDS_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwNewIntrinsicOutOfBoundsException() {
        vmErrorIfImplicitExceptionsAreFatal();
        throw new ArrayIndexOutOfBoundsException();
    }

    /** Foreign call: {@link #THROW_NEW_OUT_OF_BOUNDS_EXCEPTION_WITH_ARGS}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwNewOutOfBoundsExceptionWithArgs(int index, int length) {
        vmErrorIfImplicitExceptionsAreFatal();
        /*
         * JDK 11 added the length to the error message, we can do that for all Java versions to be
         * consistent.
         */
        throw new ArrayIndexOutOfBoundsException("Index " + index + " out of bounds for length " + length);
    }

    /** Foreign call: {@link #THROW_NEW_CLASS_CAST_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwNewClassCastException() {
        vmErrorIfImplicitExceptionsAreFatal();
        throw new ClassCastException();
    }

    /** Foreign call: {@link #THROW_NEW_CLASS_CAST_EXCEPTION_WITH_ARGS}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwNewClassCastExceptionWithArgs(Object object, Class<?> expectedClass) {
        assert object != null : "null can be cast to any type, so it cannot show up as a source of a ClassCastException";
        vmErrorIfImplicitExceptionsAreFatal();
        throw new ClassCastException(object.getClass().getTypeName() + " cannot be cast to " + expectedClass.getTypeName());
    }

    /** Foreign call: {@link #THROW_NEW_ARRAY_STORE_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwNewArrayStoreException() {
        vmErrorIfImplicitExceptionsAreFatal();
        throw new ArrayStoreException();
    }

    /** Foreign call: {@link #THROW_NEW_ARRAY_STORE_EXCEPTION_WITH_ARGS}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwNewArrayStoreExceptionWithArgs(Object value) {
        assert value != null : "null can be stored into any array, so it cannot show up as a source of an ArrayStoreException";
        vmErrorIfImplicitExceptionsAreFatal();
        throw new ArrayStoreException(value.getClass().getTypeName());
    }

    /** Foreign call: {@link #THROW_NEW_ILLEGAL_ARGUMENT_EXCEPTION_WITH_ARGS}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwNewIllegalArgumentExceptionWithArgs(String message) {
        vmErrorIfImplicitExceptionsAreFatal();
        throw new IllegalArgumentException(message);
    }

    /** Foreign call: {@link #THROW_NEW_NEGATIVE_ARRAY_SIZE_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwNewNegativeArraySizeException(int length) {
        vmErrorIfImplicitExceptionsAreFatal();
        throw new NegativeArraySizeException(String.valueOf(length));
    }

    /** Foreign call: {@link #THROW_NEW_ARITHMETIC_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwNewArithmeticException() {
        vmErrorIfImplicitExceptionsAreFatal();
        throw new ArithmeticException();
    }

    /** Foreign call: {@link #THROW_NEW_DIVISION_BY_ZERO_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwNewDivisionByZeroException() {
        vmErrorIfImplicitExceptionsAreFatal();
        throw new ArithmeticException("/ by zero");
    }

    /** Foreign call: {@link #THROW_NEW_ASSERTION_ERROR_NULLARY}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwNewAssertionErrorNullary() {
        vmErrorIfImplicitExceptionsAreFatal();
        throw new AssertionError();
    }

    /** Foreign call: {@link #THROW_NEW_ASSERTION_ERROR_OBJECT}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwNewAssertionErrorObject(Object detailMessage) {
        vmErrorIfImplicitExceptionsAreFatal();
        throw new AssertionError(detailMessage);
    }

    /** Foreign call: {@link #GET_CACHED_NULL_POINTER_EXCEPTION}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static NullPointerException getCachedNullPointerException() {
        vmErrorIfImplicitExceptionsAreFatal();
        return CACHED_NULL_POINTER_EXCEPTION;
    }

    /** Foreign call: {@link #GET_CACHED_OUT_OF_BOUNDS_EXCEPTION}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static ArrayIndexOutOfBoundsException getCachedOutOfBoundsException() {
        vmErrorIfImplicitExceptionsAreFatal();
        return CACHED_OUT_OF_BOUNDS_EXCEPTION;
    }

    /** Foreign call: {@link #GET_CACHED_CLASS_CAST_EXCEPTION}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static ClassCastException getCachedClassCastException() {
        vmErrorIfImplicitExceptionsAreFatal();
        return CACHED_CLASS_CAST_EXCEPTION;
    }

    /** Foreign call: {@link #GET_CACHED_ARRAY_STORE_EXCEPTION}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static ArrayStoreException getCachedArrayStoreException() {
        vmErrorIfImplicitExceptionsAreFatal();
        return CACHED_ARRAY_STORE_EXCEPTION;
    }

    /** Foreign call: {@link #GET_CACHED_ILLEGAL_ARGUMENT_EXCEPTION}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static IllegalArgumentException getCachedIllegalArgumentException() {
        vmErrorIfImplicitExceptionsAreFatal();
        return CACHED_ILLEGAL_ARGUMENT_EXCEPTION;
    }

    /** Foreign call: {@link #GET_CACHED_NEGATIVE_ARRAY_SIZE_EXCEPTION}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static NegativeArraySizeException getCachedNegativeArraySizeException() {
        vmErrorIfImplicitExceptionsAreFatal();
        return CACHED_NEGATIVE_ARRAY_SIZE_EXCEPTION;
    }

    /** Foreign call: {@link #GET_CACHED_ARITHMETIC_EXCEPTION}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static ArithmeticException getCachedArithmeticException() {
        vmErrorIfImplicitExceptionsAreFatal();
        return CACHED_ARITHMETIC_EXCEPTION;
    }

    /** Foreign call: {@link #GET_CACHED_ASSERTION_ERROR}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static AssertionError getCachedAssertionError() {
        vmErrorIfImplicitExceptionsAreFatal();
        return CACHED_ASSERTION_ERROR;
    }

    /** Foreign call: {@link #THROW_CACHED_NULL_POINTER_EXCEPTION}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwCachedNullPointerException() {
        vmErrorIfImplicitExceptionsAreFatal();
        throw CACHED_NULL_POINTER_EXCEPTION;
    }

    /** Foreign call: {@link #THROW_CACHED_OUT_OF_BOUNDS_EXCEPTION}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwCachedOutOfBoundsException() {
        vmErrorIfImplicitExceptionsAreFatal();
        throw CACHED_OUT_OF_BOUNDS_EXCEPTION;
    }

    /** Foreign call: {@link #THROW_CACHED_CLASS_CAST_EXCEPTION}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwCachedClassCastException() {
        vmErrorIfImplicitExceptionsAreFatal();
        throw CACHED_CLASS_CAST_EXCEPTION;
    }

    /** Foreign call: {@link #THROW_CACHED_ARRAY_STORE_EXCEPTION}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwCachedArrayStoreException() {
        vmErrorIfImplicitExceptionsAreFatal();
        throw CACHED_ARRAY_STORE_EXCEPTION;
    }

    /** Foreign call: {@link #THROW_CACHED_ILLEGAL_ARGUMENT_EXCEPTION}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwCachedIllegalArgumentException() {
        vmErrorIfImplicitExceptionsAreFatal();
        throw CACHED_ILLEGAL_ARGUMENT_EXCEPTION;
    }

    /** Foreign call: {@link #THROW_CACHED_NEGATIVE_ARRAY_SIZE_EXCEPTION}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwCachedNegativeArraySizeException() {
        vmErrorIfImplicitExceptionsAreFatal();
        throw CACHED_NEGATIVE_ARRAY_SIZE_EXCEPTION;
    }

    /** Foreign call: {@link #THROW_CACHED_ARITHMETIC_EXCEPTION}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwCachedArithmeticException() {
        vmErrorIfImplicitExceptionsAreFatal();
        throw CACHED_ARITHMETIC_EXCEPTION;
    }

    /** Foreign call: {@link #THROW_CACHED_ASSERTION_ERROR}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwCachedAssertionError() {
        vmErrorIfImplicitExceptionsAreFatal();
        throw CACHED_ASSERTION_ERROR;
    }

    // ReflectiveOperationException subclasses

    public static void throwClassNotFoundException(String message) throws ClassNotFoundException {
        throw new ClassNotFoundException(message);
    }

    public static void throwNoSuchFieldException(String message) throws NoSuchFieldException {
        throw new NoSuchFieldException(message);
    }

    public static void throwNoSuchMethodException(String message) throws NoSuchMethodException {
        throw new NoSuchMethodException(message);
    }

    // LinkageError subclasses

    public static void throwLinkageError(String message) throws LinkageError {
        throw new LinkageError(message);
    }

    public static void throwClassCircularityError(String message) throws ClassCircularityError {
        throw new ClassCircularityError(message);
    }

    public static void throwIncompatibleClassChangeError(String message) throws IncompatibleClassChangeError {
        throw new IncompatibleClassChangeError(message);
    }

    public static void throwNoSuchFieldError(String message) throws NoSuchFieldError {
        throw new NoSuchFieldError(message);
    }

    public static void throwInstantiationError(String message) throws InstantiationError {
        throw new InstantiationError(message);
    }

    public static void throwNoSuchMethodError(String message) throws NoSuchMethodError {
        throw new NoSuchMethodError(message);
    }

    public static void throwIllegalAccessError(String message) throws IllegalAccessError {
        throw new IllegalAccessError(message);
    }

    public static void throwAbstractMethodError(String message) throws AbstractMethodError {
        throw new AbstractMethodError(message);
    }

    public static void throwBootstrapMethodError(String message) throws BootstrapMethodError {
        throw new BootstrapMethodError(message);
    }

    public static void throwClassFormatError(String message) throws ClassFormatError {
        throw new ClassFormatError(message);
    }

    public static void throwGenericSignatureFormatError(String message) throws GenericSignatureFormatError {
        throw new GenericSignatureFormatError(message);
    }

    public static void throwUnsupportedClassVersionError(String message) throws UnsupportedClassVersionError {
        throw new UnsupportedClassVersionError(message);
    }

    public static void throwUnsatisfiedLinkError(String message) throws UnsatisfiedLinkError {
        throw new UnsatisfiedLinkError(message);
    }

    public static void throwNoClassDefFoundError(String message) throws NoClassDefFoundError {
        throw new NoClassDefFoundError(message);
    }

    public static void throwExceptionInInitializerError(String message) throws ExceptionInInitializerError {
        throw new ExceptionInInitializerError(message);
    }

    public static void throwVerifyError(String message) throws VerifyError {
        throw new VerifyError(message);
    }

    public static void throwVerifyError() throws VerifyError {
        throw new VerifyError();
    }

}
