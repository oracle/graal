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

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.HAS_SIDE_EFFECT;
import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.NO_SIDE_EFFECT;

import java.lang.reflect.GenericSignatureFormatError;

import com.oracle.svm.core.SubstrateDiagnostics;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.code.FactoryMethodMarker;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.jdk.InternalVMMethod;
import com.oracle.svm.core.jdk.StackTraceUtils;
import com.oracle.svm.core.option.SubstrateOptionsParser;
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
    public static final IncompatibleClassChangeError CACHED_INCOMPATIBLE_CLASS_CHANGE_ERROR = new IncompatibleClassChangeError(NO_STACK_MSG);
    public static final IllegalArgumentException CACHED_ILLEGAL_ARGUMENT_EXCEPTION = new IllegalArgumentException(NO_STACK_MSG);
    public static final NegativeArraySizeException CACHED_NEGATIVE_ARRAY_SIZE_EXCEPTION = new NegativeArraySizeException(NO_STACK_MSG);
    public static final ArithmeticException CACHED_ARITHMETIC_EXCEPTION = new ArithmeticException(NO_STACK_MSG);
    public static final AssertionError CACHED_ASSERTION_ERROR = new AssertionError(NO_STACK_MSG);
    public static final StackOverflowError CACHED_STACK_OVERFLOW_ERROR = new StackOverflowError(NO_STACK_MSG);
    public static final IllegalMonitorStateException CACHED_ILLEGAL_MONITOR_STATE_EXCEPTION = new IllegalMonitorStateException(NO_STACK_MSG);

    public static final SubstrateForeignCallDescriptor CREATE_NULL_POINTER_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createNullPointerException", HAS_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor CREATE_OUT_OF_BOUNDS_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createOutOfBoundsException", HAS_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor CREATE_INTRINSIC_OUT_OF_BOUNDS_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createIntrinsicOutOfBoundsException",
                    HAS_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor CREATE_CLASS_CAST_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createClassCastException", HAS_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor CREATE_ARRAY_STORE_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createArrayStoreException", HAS_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor CREATE_INCOMPATIBLE_CLASS_CHANGE_ERROR = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createIncompatibleClassChangeError",
                    HAS_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor CREATE_ILLEGAL_ARGUMENT_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createIllegalArgumentException", HAS_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor CREATE_NEGATIVE_ARRAY_SIZE_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createNegativeArraySizeException",
                    HAS_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor CREATE_DIVISION_BY_ZERO_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createDivisionByZeroException", HAS_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor CREATE_INTEGER_OVERFLOW_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createIntegerOverflowException", HAS_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor CREATE_LONG_OVERFLOW_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createLongOverflowException", HAS_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor CREATE_ASSERTION_ERROR_NULLARY = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createAssertionErrorNullary", HAS_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor CREATE_ASSERTION_ERROR_OBJECT = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createAssertionErrorObject", HAS_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor CREATE_ILLEGAL_MONITOR_STATE_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createIllegalMonitorStateException",
                    HAS_SIDE_EFFECT);

    public static final SubstrateForeignCallDescriptor THROW_NEW_NULL_POINTER_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwNewNullPointerException", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor THROW_NEW_INTRINSIC_OUT_OF_BOUNDS_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwNewIntrinsicOutOfBoundsException",
                    NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor THROW_NEW_OUT_OF_BOUNDS_EXCEPTION_WITH_ARGS = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwNewOutOfBoundsExceptionWithArgs",
                    NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor THROW_NEW_CLASS_CAST_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwNewClassCastException", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor THROW_NEW_CLASS_CAST_EXCEPTION_WITH_ARGS = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwNewClassCastExceptionWithArgs",
                    NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor THROW_NEW_ARRAY_STORE_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwNewArrayStoreException", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor THROW_NEW_ARRAY_STORE_EXCEPTION_WITH_ARGS = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwNewArrayStoreExceptionWithArgs",
                    NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor THROW_NEW_INCOMPATIBLE_CLASS_CHANGE_ERROR = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwNewIncompatibleClassChangeError",
                    NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor THROW_NEW_ILLEGAL_ARGUMENT_EXCEPTION_WITH_ARGS = SnippetRuntime.findForeignCall(ImplicitExceptions.class,
                    "throwNewIllegalArgumentExceptionWithArgs", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor THROW_NEW_NEGATIVE_ARRAY_SIZE_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwNewNegativeArraySizeException",
                    NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor THROW_NEW_ARITHMETIC_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwNewArithmeticException", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor THROW_NEW_DIVISION_BY_ZERO_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwNewDivisionByZeroException",
                    NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor THROW_NEW_INTEGER_OVERFLOW_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwNewIntegerOverflowException",
                    NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor THROW_NEW_LONG_OVERFLOW_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwNewLongOverflowException", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor THROW_NEW_ASSERTION_ERROR_NULLARY = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwNewAssertionErrorNullary", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor THROW_NEW_ASSERTION_ERROR_OBJECT = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwNewAssertionErrorObject", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor THROW_NEW_ILLEGAL_MONITOR_STATE_EXCEPTION_WITH_ARGS = SnippetRuntime.findForeignCall(ImplicitExceptions.class,
                    "throwNewIllegalMonitorStateExceptionWithArgs", NO_SIDE_EFFECT);

    public static final SubstrateForeignCallDescriptor CREATE_OPT_NULL_POINTER_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createOptNullPointerException", HAS_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor CREATE_OPT_OUT_OF_BOUNDS_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createOptOutOfBoundsException", HAS_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor CREATE_OPT_CLASS_CAST_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createOptClassCastException", HAS_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor CREATE_OPT_ARRAY_STORE_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createOptArrayStoreException", HAS_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor CREATE_OPT_INCOMPATIBLE_CLASS_CHANGE_ERROR = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "createOptIncompatibleClassChangeError",
                    HAS_SIDE_EFFECT);

    public static final SubstrateForeignCallDescriptor THROW_OPT_NULL_POINTER_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwOptNullPointerException", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor THROW_OPT_OUT_OF_BOUNDS_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwOptOutOfBoundsException", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor THROW_OPT_CLASS_CAST_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwOptClassCastException", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor THROW_OPT_ARRAY_STORE_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwOptArrayStoreException", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor THROW_OPT_INCOMPATIBLE_CLASS_CHANGE_ERROR = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwOptIncompatibleClassChangeError",
                    NO_SIDE_EFFECT);

    public static final SubstrateForeignCallDescriptor GET_CACHED_NULL_POINTER_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "getCachedNullPointerException", HAS_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor GET_CACHED_OUT_OF_BOUNDS_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "getCachedOutOfBoundsException", HAS_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor GET_CACHED_CLASS_CAST_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "getCachedClassCastException", HAS_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor GET_CACHED_ARRAY_STORE_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "getCachedArrayStoreException", HAS_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor GET_CACHED_INCOMPATIBLE_CLASS_CHANGE_ERROR = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "getCachedIncompatibleClassChangeError",
                    HAS_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor GET_CACHED_ILLEGAL_ARGUMENT_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "getCachedIllegalArgumentException",
                    HAS_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor GET_CACHED_NEGATIVE_ARRAY_SIZE_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "getCachedNegativeArraySizeException",
                    HAS_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor GET_CACHED_ARITHMETIC_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "getCachedArithmeticException", HAS_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor GET_CACHED_ASSERTION_ERROR = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "getCachedAssertionError", HAS_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor GET_CACHED_ILLEGAL_MONITOR_STATE_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "getCachedIllegalMonitorStateException",
                    HAS_SIDE_EFFECT);

    public static final SubstrateForeignCallDescriptor THROW_CACHED_NULL_POINTER_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwCachedNullPointerException",
                    NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor THROW_CACHED_OUT_OF_BOUNDS_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwCachedOutOfBoundsException",
                    NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor THROW_CACHED_CLASS_CAST_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwCachedClassCastException", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor THROW_CACHED_ARRAY_STORE_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwCachedArrayStoreException", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor THROW_CACHED_INCOMPATIBLE_CLASS_CHANGE_ERROR = SnippetRuntime.findForeignCall(ImplicitExceptions.class,
                    "throwCachedIncompatibleClassChangeError", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor THROW_CACHED_ILLEGAL_ARGUMENT_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwCachedIllegalArgumentException",
                    NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor THROW_CACHED_NEGATIVE_ARRAY_SIZE_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwCachedNegativeArraySizeException",
                    NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor THROW_CACHED_ARITHMETIC_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwCachedArithmeticException", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor THROW_CACHED_ASSERTION_ERROR = SnippetRuntime.findForeignCall(ImplicitExceptions.class, "throwCachedAssertionError", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor THROW_CACHED_ILLEGAL_MONITOR_STATE_EXCEPTION = SnippetRuntime.findForeignCall(ImplicitExceptions.class,
                    "throwCachedIllegalMonitorStateException",
                    NO_SIDE_EFFECT);

    public static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{
                    CREATE_NULL_POINTER_EXCEPTION, CREATE_OUT_OF_BOUNDS_EXCEPTION, CREATE_INTRINSIC_OUT_OF_BOUNDS_EXCEPTION, CREATE_CLASS_CAST_EXCEPTION, CREATE_ARRAY_STORE_EXCEPTION,
                    CREATE_INCOMPATIBLE_CLASS_CHANGE_ERROR,
                    CREATE_ILLEGAL_ARGUMENT_EXCEPTION, CREATE_NEGATIVE_ARRAY_SIZE_EXCEPTION,
                    CREATE_DIVISION_BY_ZERO_EXCEPTION, CREATE_INTEGER_OVERFLOW_EXCEPTION, CREATE_LONG_OVERFLOW_EXCEPTION, CREATE_ASSERTION_ERROR_NULLARY, CREATE_ASSERTION_ERROR_OBJECT,
                    CREATE_ILLEGAL_MONITOR_STATE_EXCEPTION,
                    THROW_NEW_NULL_POINTER_EXCEPTION, THROW_NEW_INTRINSIC_OUT_OF_BOUNDS_EXCEPTION, THROW_NEW_CLASS_CAST_EXCEPTION, THROW_NEW_ARRAY_STORE_EXCEPTION,
                    THROW_NEW_INCOMPATIBLE_CLASS_CHANGE_ERROR, THROW_NEW_ARITHMETIC_EXCEPTION,
                    THROW_NEW_OUT_OF_BOUNDS_EXCEPTION_WITH_ARGS, THROW_NEW_CLASS_CAST_EXCEPTION_WITH_ARGS, THROW_NEW_ARRAY_STORE_EXCEPTION_WITH_ARGS, THROW_NEW_ILLEGAL_ARGUMENT_EXCEPTION_WITH_ARGS,
                    THROW_NEW_NEGATIVE_ARRAY_SIZE_EXCEPTION, THROW_NEW_DIVISION_BY_ZERO_EXCEPTION, THROW_NEW_INTEGER_OVERFLOW_EXCEPTION, THROW_NEW_LONG_OVERFLOW_EXCEPTION,
                    THROW_NEW_ASSERTION_ERROR_NULLARY, THROW_NEW_ASSERTION_ERROR_OBJECT, THROW_NEW_ILLEGAL_MONITOR_STATE_EXCEPTION_WITH_ARGS,
                    GET_CACHED_NULL_POINTER_EXCEPTION, GET_CACHED_OUT_OF_BOUNDS_EXCEPTION, GET_CACHED_CLASS_CAST_EXCEPTION, GET_CACHED_ARRAY_STORE_EXCEPTION,
                    GET_CACHED_INCOMPATIBLE_CLASS_CHANGE_ERROR, GET_CACHED_ILLEGAL_ARGUMENT_EXCEPTION,
                    GET_CACHED_NEGATIVE_ARRAY_SIZE_EXCEPTION, GET_CACHED_ARITHMETIC_EXCEPTION, GET_CACHED_ASSERTION_ERROR, GET_CACHED_ILLEGAL_MONITOR_STATE_EXCEPTION,
                    THROW_CACHED_NULL_POINTER_EXCEPTION, THROW_CACHED_OUT_OF_BOUNDS_EXCEPTION, THROW_CACHED_CLASS_CAST_EXCEPTION, THROW_CACHED_ARRAY_STORE_EXCEPTION,
                    THROW_CACHED_INCOMPATIBLE_CLASS_CHANGE_ERROR,
                    THROW_CACHED_ILLEGAL_ARGUMENT_EXCEPTION, THROW_CACHED_NEGATIVE_ARRAY_SIZE_EXCEPTION, THROW_CACHED_ARITHMETIC_EXCEPTION, THROW_CACHED_ASSERTION_ERROR,
                    THROW_CACHED_ILLEGAL_MONITOR_STATE_EXCEPTION,
                    CREATE_OPT_NULL_POINTER_EXCEPTION, CREATE_OPT_OUT_OF_BOUNDS_EXCEPTION, CREATE_OPT_CLASS_CAST_EXCEPTION, CREATE_OPT_ARRAY_STORE_EXCEPTION,
                    CREATE_OPT_INCOMPATIBLE_CLASS_CHANGE_ERROR,
                    THROW_OPT_NULL_POINTER_EXCEPTION, THROW_OPT_OUT_OF_BOUNDS_EXCEPTION, THROW_OPT_CLASS_CAST_EXCEPTION, THROW_OPT_ARRAY_STORE_EXCEPTION, THROW_OPT_INCOMPATIBLE_CLASS_CHANGE_ERROR
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

    private static void vmErrorIfImplicitExceptionsAreFatal(boolean cachedException) {
        if (cachedException && SubstrateOptions.ImplicitExceptionWithoutStacktraceIsFatal.getValue()) {
            throw VMError.shouldNotReachHere("AssertionError without stack trace.");
        } else if ((implicitExceptionsAreFatal.get() > 0 || ExceptionUnwind.exceptionsAreFatal()) && !SubstrateDiagnostics.isFatalErrorHandlingThread()) {
            throw VMError.shouldNotReachHere("Implicit exception thrown in code where such exceptions are fatal errors");
        }
    }

    /** Foreign call: {@link #CREATE_NULL_POINTER_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static NullPointerException createNullPointerException() {
        vmErrorIfImplicitExceptionsAreFatal(false);
        return new NullPointerException();
    }

    /** Foreign call: {@link #CREATE_OUT_OF_BOUNDS_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static ArrayIndexOutOfBoundsException createIntrinsicOutOfBoundsException() {
        vmErrorIfImplicitExceptionsAreFatal(false);
        return new ArrayIndexOutOfBoundsException();
    }

    /** Foreign call: {@link #CREATE_OUT_OF_BOUNDS_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static ArrayIndexOutOfBoundsException createOutOfBoundsException(int index, int length) {
        vmErrorIfImplicitExceptionsAreFatal(false);
        return new ArrayIndexOutOfBoundsException("Index " + index + " out of bounds for length " + length);
    }

    /** Foreign call: {@link #CREATE_CLASS_CAST_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static ClassCastException createClassCastException(Object object, Object expectedClass) {
        assert object != null : "null can be cast to any type, so it cannot show up as a source of a ClassCastException";
        vmErrorIfImplicitExceptionsAreFatal(false);
        String expectedClassName;
        if (expectedClass instanceof Class) {
            expectedClassName = ((Class<?>) expectedClass).getTypeName();
        } else {
            expectedClassName = String.valueOf(expectedClass);
        }
        return new ClassCastException(object.getClass().getTypeName() + " cannot be cast to " + expectedClassName);
    }

    /** Foreign call: {@link #CREATE_ARRAY_STORE_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static ArrayStoreException createArrayStoreException(Object value) {
        assert value != null : "null can be stored into any array, so it cannot show up as a source of an ArrayStoreException";
        vmErrorIfImplicitExceptionsAreFatal(false);
        return new ArrayStoreException(value.getClass().getTypeName());
    }

    /** Foreign call: {@link #CREATE_INCOMPATIBLE_CLASS_CHANGE_ERROR}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static IncompatibleClassChangeError createIncompatibleClassChangeError() {
        vmErrorIfImplicitExceptionsAreFatal(false);
        return new IncompatibleClassChangeError();
    }

    /** Foreign call: {@link #CREATE_ILLEGAL_ARGUMENT_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static IllegalArgumentException createIllegalArgumentException(String message) {
        vmErrorIfImplicitExceptionsAreFatal(false);
        return new IllegalArgumentException(message);
    }

    /** Foreign call: {@link #CREATE_NEGATIVE_ARRAY_SIZE_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static NegativeArraySizeException createNegativeArraySizeException(int length) {
        vmErrorIfImplicitExceptionsAreFatal(false);
        return new NegativeArraySizeException(String.valueOf(length));
    }

    /** Foreign call: {@link #CREATE_DIVISION_BY_ZERO_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static ArithmeticException createDivisionByZeroException() {
        vmErrorIfImplicitExceptionsAreFatal(false);
        return new ArithmeticException("/ by zero");
    }

    // Checkstyle: allow inconsistent exceptions and errors

    /** Foreign call: {@link #CREATE_INTEGER_OVERFLOW_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static ArithmeticException createIntegerOverflowException() {
        vmErrorIfImplicitExceptionsAreFatal(false);
        return new ArithmeticException("integer overflow");
    }

    /** Foreign call: {@link #CREATE_LONG_OVERFLOW_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static ArithmeticException createLongOverflowException() {
        vmErrorIfImplicitExceptionsAreFatal(false);
        return new ArithmeticException("long overflow");
    }

    // Checkstyle: disallow inconsistent exceptions and errors

    /** Foreign call: {@link #CREATE_ASSERTION_ERROR_NULLARY}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static AssertionError createAssertionErrorNullary() {
        vmErrorIfImplicitExceptionsAreFatal(false);
        return new AssertionError();
    }

    /** Foreign call: {@link #CREATE_ASSERTION_ERROR_OBJECT}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static AssertionError createAssertionErrorObject(Object detailMessage) {
        vmErrorIfImplicitExceptionsAreFatal(false);
        return new AssertionError(detailMessage);
    }

    /** Foreign call: {@link #CREATE_ILLEGAL_MONITOR_STATE_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static IllegalMonitorStateException createIllegalMonitorStateException(String message) {
        vmErrorIfImplicitExceptionsAreFatal(false);
        return new IllegalMonitorStateException(message);
    }

    /** Foreign call: {@link #THROW_NEW_NULL_POINTER_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwNewNullPointerException() {
        vmErrorIfImplicitExceptionsAreFatal(false);
        throw new NullPointerException();
    }

    /** Foreign call: {@link #THROW_NEW_INTRINSIC_OUT_OF_BOUNDS_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwNewIntrinsicOutOfBoundsException() {
        vmErrorIfImplicitExceptionsAreFatal(false);
        throw new ArrayIndexOutOfBoundsException();
    }

    /** Foreign call: {@link #THROW_NEW_OUT_OF_BOUNDS_EXCEPTION_WITH_ARGS}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwNewOutOfBoundsExceptionWithArgs(int index, int length) {
        vmErrorIfImplicitExceptionsAreFatal(false);
        throw new ArrayIndexOutOfBoundsException("Index " + index + " out of bounds for length " + length);
    }

    /** Foreign call: {@link #THROW_NEW_CLASS_CAST_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwNewClassCastException() {
        vmErrorIfImplicitExceptionsAreFatal(false);
        throw new ClassCastException();
    }

    /** Foreign call: {@link #THROW_NEW_CLASS_CAST_EXCEPTION_WITH_ARGS}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwNewClassCastExceptionWithArgs(Object object, Object expectedClass) {
        assert object != null : "null can be cast to any type, so it cannot show up as a source of a ClassCastException";
        vmErrorIfImplicitExceptionsAreFatal(false);
        String expectedClassName;
        if (expectedClass instanceof Class) {
            expectedClassName = ((Class<?>) expectedClass).getTypeName();
        } else {
            expectedClassName = String.valueOf(expectedClass);
        }
        throw new ClassCastException(object.getClass().getTypeName() + " cannot be cast to " + expectedClassName);
    }

    /** Foreign call: {@link #THROW_NEW_ARRAY_STORE_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwNewArrayStoreException() {
        vmErrorIfImplicitExceptionsAreFatal(false);
        throw new ArrayStoreException();
    }

    /** Foreign call: {@link #THROW_NEW_ARRAY_STORE_EXCEPTION_WITH_ARGS}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwNewArrayStoreExceptionWithArgs(Object value) {
        assert value != null : "null can be stored into any array, so it cannot show up as a source of an ArrayStoreException";
        vmErrorIfImplicitExceptionsAreFatal(false);
        throw new ArrayStoreException(value.getClass().getTypeName());
    }

    /** Foreign call: {@link #THROW_NEW_INCOMPATIBLE_CLASS_CHANGE_ERROR}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwNewIncompatibleClassChangeError() {
        vmErrorIfImplicitExceptionsAreFatal(false);
        throw new IncompatibleClassChangeError();
    }

    /** Foreign call: {@link #THROW_NEW_ILLEGAL_ARGUMENT_EXCEPTION_WITH_ARGS}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwNewIllegalArgumentExceptionWithArgs(String message) {
        vmErrorIfImplicitExceptionsAreFatal(false);
        throw new IllegalArgumentException(message);
    }

    /** Foreign call: {@link #THROW_NEW_NEGATIVE_ARRAY_SIZE_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwNewNegativeArraySizeException(int length) {
        vmErrorIfImplicitExceptionsAreFatal(false);
        throw new NegativeArraySizeException(String.valueOf(length));
    }

    /** Foreign call: {@link #THROW_NEW_ARITHMETIC_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwNewArithmeticException() {
        vmErrorIfImplicitExceptionsAreFatal(false);
        throw new ArithmeticException();
    }

    /** Foreign call: {@link #THROW_NEW_DIVISION_BY_ZERO_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwNewDivisionByZeroException() {
        vmErrorIfImplicitExceptionsAreFatal(false);
        throw new ArithmeticException("/ by zero");
    }

    // Checkstyle: allow inconsistent exceptions and errors

    /** Foreign call: {@link #THROW_NEW_INTEGER_OVERFLOW_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwNewIntegerOverflowException() {
        vmErrorIfImplicitExceptionsAreFatal(false);
        throw new ArithmeticException("integer overflow");
    }

    /** Foreign call: {@link #THROW_NEW_LONG_OVERFLOW_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwNewLongOverflowException() {
        vmErrorIfImplicitExceptionsAreFatal(false);
        throw new ArithmeticException("long overflow");
    }

    // Checkstyle: disallow inconsistent exceptions and errors

    /** Foreign call: {@link #THROW_NEW_ASSERTION_ERROR_NULLARY}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwNewAssertionErrorNullary() {
        vmErrorIfImplicitExceptionsAreFatal(false);
        throw new AssertionError();
    }

    /** Foreign call: {@link #THROW_NEW_ASSERTION_ERROR_OBJECT}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwNewAssertionErrorObject(Object detailMessage) {
        vmErrorIfImplicitExceptionsAreFatal(false);
        throw new AssertionError(detailMessage);
    }

    /** Foreign call: {@link #THROW_CACHED_ILLEGAL_MONITOR_STATE_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwNewIllegalMonitorStateExceptionWithArgs(String message) {
        vmErrorIfImplicitExceptionsAreFatal(false);
        throw new IllegalMonitorStateException(message);
    }

    private static final String IMPRECISE_STACK_MSG = "Stack trace is imprecise, the top frames are missing and/or have wrong line numbers. To get precise stack traces, build the image with option " +
                    SubstrateOptionsParser.commandArgument(SubstrateOptions.ReduceImplicitExceptionStackTraceInformation, "-");

    /** Foreign call: {@link #CREATE_OPT_NULL_POINTER_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static NullPointerException createOptNullPointerException() {
        vmErrorIfImplicitExceptionsAreFatal(false);
        return new NullPointerException(IMPRECISE_STACK_MSG);
    }

    /** Foreign call: {@link #CREATE_OPT_OUT_OF_BOUNDS_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static ArrayIndexOutOfBoundsException createOptOutOfBoundsException() {
        vmErrorIfImplicitExceptionsAreFatal(false);
        return new ArrayIndexOutOfBoundsException(IMPRECISE_STACK_MSG);
    }

    /** Foreign call: {@link #CREATE_OPT_CLASS_CAST_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static ClassCastException createOptClassCastException() {
        vmErrorIfImplicitExceptionsAreFatal(false);
        return new ClassCastException(IMPRECISE_STACK_MSG);
    }

    /** Foreign call: {@link #CREATE_OPT_ARRAY_STORE_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static ArrayStoreException createOptArrayStoreException() {
        vmErrorIfImplicitExceptionsAreFatal(false);
        return new ArrayStoreException(IMPRECISE_STACK_MSG);
    }

    /** Foreign call: {@link #CREATE_OPT_INCOMPATIBLE_CLASS_CHANGE_ERROR}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static IncompatibleClassChangeError createOptIncompatibleClassChangeError() {
        vmErrorIfImplicitExceptionsAreFatal(false);
        return new IncompatibleClassChangeError(IMPRECISE_STACK_MSG);
    }

    /** Foreign call: {@link #THROW_OPT_NULL_POINTER_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwOptNullPointerException() {
        vmErrorIfImplicitExceptionsAreFatal(false);
        throw new NullPointerException(IMPRECISE_STACK_MSG);
    }

    /** Foreign call: {@link #THROW_OPT_OUT_OF_BOUNDS_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwOptOutOfBoundsException() {
        vmErrorIfImplicitExceptionsAreFatal(false);
        throw new ArrayIndexOutOfBoundsException(IMPRECISE_STACK_MSG);
    }

    /** Foreign call: {@link #THROW_OPT_CLASS_CAST_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwOptClassCastException() {
        vmErrorIfImplicitExceptionsAreFatal(false);
        throw new ClassCastException(IMPRECISE_STACK_MSG);
    }

    /** Foreign call: {@link #THROW_OPT_ARRAY_STORE_EXCEPTION}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwOptArrayStoreException() {
        vmErrorIfImplicitExceptionsAreFatal(false);
        throw new ArrayStoreException(IMPRECISE_STACK_MSG);
    }

    /** Foreign call: {@link #THROW_OPT_INCOMPATIBLE_CLASS_CHANGE_ERROR}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwOptIncompatibleClassChangeError() {
        vmErrorIfImplicitExceptionsAreFatal(false);
        throw new IncompatibleClassChangeError(IMPRECISE_STACK_MSG);
    }

    /** Foreign call: {@link #GET_CACHED_NULL_POINTER_EXCEPTION}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static NullPointerException getCachedNullPointerException() {
        vmErrorIfImplicitExceptionsAreFatal(true);
        return CACHED_NULL_POINTER_EXCEPTION;
    }

    /** Foreign call: {@link #GET_CACHED_OUT_OF_BOUNDS_EXCEPTION}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static ArrayIndexOutOfBoundsException getCachedOutOfBoundsException() {
        vmErrorIfImplicitExceptionsAreFatal(true);
        return CACHED_OUT_OF_BOUNDS_EXCEPTION;
    }

    /** Foreign call: {@link #GET_CACHED_CLASS_CAST_EXCEPTION}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static ClassCastException getCachedClassCastException() {
        vmErrorIfImplicitExceptionsAreFatal(true);
        return CACHED_CLASS_CAST_EXCEPTION;
    }

    /** Foreign call: {@link #GET_CACHED_ARRAY_STORE_EXCEPTION}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static ArrayStoreException getCachedArrayStoreException() {
        vmErrorIfImplicitExceptionsAreFatal(true);
        return CACHED_ARRAY_STORE_EXCEPTION;
    }

    /** Foreign call: {@link #GET_CACHED_INCOMPATIBLE_CLASS_CHANGE_ERROR}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static IncompatibleClassChangeError getCachedIncompatibleClassChangeError() {
        vmErrorIfImplicitExceptionsAreFatal(true);
        return CACHED_INCOMPATIBLE_CLASS_CHANGE_ERROR;
    }

    /** Foreign call: {@link #GET_CACHED_ILLEGAL_ARGUMENT_EXCEPTION}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static IllegalArgumentException getCachedIllegalArgumentException() {
        vmErrorIfImplicitExceptionsAreFatal(true);
        return CACHED_ILLEGAL_ARGUMENT_EXCEPTION;
    }

    /** Foreign call: {@link #GET_CACHED_NEGATIVE_ARRAY_SIZE_EXCEPTION}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static NegativeArraySizeException getCachedNegativeArraySizeException() {
        vmErrorIfImplicitExceptionsAreFatal(true);
        return CACHED_NEGATIVE_ARRAY_SIZE_EXCEPTION;
    }

    /** Foreign call: {@link #GET_CACHED_ARITHMETIC_EXCEPTION}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static ArithmeticException getCachedArithmeticException() {
        vmErrorIfImplicitExceptionsAreFatal(true);
        return CACHED_ARITHMETIC_EXCEPTION;
    }

    /** Foreign call: {@link #GET_CACHED_ASSERTION_ERROR}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static AssertionError getCachedAssertionError() {
        vmErrorIfImplicitExceptionsAreFatal(true);
        return CACHED_ASSERTION_ERROR;
    }

    /** Foreign call: {@link #GET_CACHED_ILLEGAL_MONITOR_STATE_EXCEPTION}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static IllegalMonitorStateException getCachedIllegalMonitorStateException() {
        vmErrorIfImplicitExceptionsAreFatal(true);
        return CACHED_ILLEGAL_MONITOR_STATE_EXCEPTION;
    }

    /** Foreign call: {@link #THROW_CACHED_NULL_POINTER_EXCEPTION}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwCachedNullPointerException() {
        vmErrorIfImplicitExceptionsAreFatal(true);
        throw CACHED_NULL_POINTER_EXCEPTION;
    }

    /** Foreign call: {@link #THROW_CACHED_OUT_OF_BOUNDS_EXCEPTION}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwCachedOutOfBoundsException() {
        vmErrorIfImplicitExceptionsAreFatal(true);
        throw CACHED_OUT_OF_BOUNDS_EXCEPTION;
    }

    /** Foreign call: {@link #THROW_CACHED_CLASS_CAST_EXCEPTION}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwCachedClassCastException() {
        vmErrorIfImplicitExceptionsAreFatal(true);
        throw CACHED_CLASS_CAST_EXCEPTION;
    }

    /** Foreign call: {@link #THROW_CACHED_ARRAY_STORE_EXCEPTION}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwCachedArrayStoreException() {
        vmErrorIfImplicitExceptionsAreFatal(true);
        throw CACHED_ARRAY_STORE_EXCEPTION;
    }

    /** Foreign call: {@link #THROW_CACHED_INCOMPATIBLE_CLASS_CHANGE_ERROR}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwCachedIncompatibleClassChangeError() {
        vmErrorIfImplicitExceptionsAreFatal(true);
        throw CACHED_INCOMPATIBLE_CLASS_CHANGE_ERROR;
    }

    /** Foreign call: {@link #THROW_CACHED_ILLEGAL_ARGUMENT_EXCEPTION}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwCachedIllegalArgumentException() {
        vmErrorIfImplicitExceptionsAreFatal(true);
        throw CACHED_ILLEGAL_ARGUMENT_EXCEPTION;
    }

    /** Foreign call: {@link #THROW_CACHED_NEGATIVE_ARRAY_SIZE_EXCEPTION}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwCachedNegativeArraySizeException() {
        vmErrorIfImplicitExceptionsAreFatal(true);
        throw CACHED_NEGATIVE_ARRAY_SIZE_EXCEPTION;
    }

    /** Foreign call: {@link #THROW_CACHED_ARITHMETIC_EXCEPTION}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwCachedArithmeticException() {
        vmErrorIfImplicitExceptionsAreFatal(true);
        throw CACHED_ARITHMETIC_EXCEPTION;
    }

    /** Foreign call: {@link #THROW_CACHED_ASSERTION_ERROR}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwCachedAssertionError() {
        vmErrorIfImplicitExceptionsAreFatal(true);
        throw CACHED_ASSERTION_ERROR;
    }

    /** Foreign call: {@link #THROW_CACHED_ILLEGAL_MONITOR_STATE_EXCEPTION}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called to report an implicit exception in code that must not allocate.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwCachedIllegalMonitorStateException() {
        vmErrorIfImplicitExceptionsAreFatal(true);
        throw CACHED_ILLEGAL_MONITOR_STATE_EXCEPTION;
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
