/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api;

import static com.oracle.truffle.api.CompilerDirectives.transferToInterpreterAndInvalidate;

/**
 * Assertions about the code produced by the Truffle compiler. All operations, except
 * {@link #shouldNotReachHere()}, have no effect when either executed in the interpreter or in the
 * compiled code. The assertions are checked during code generation and the Truffle compiler
 * produces for failing assertions a stack trace that identifies the code position of the assertion
 * in the context of the current compilation.
 *
 * @since 0.8 or earlier
 */
public final class CompilerAsserts {
    private CompilerAsserts() {
    }

    /**
     * Assertion that this code position should never be reached during compilation. It can be used
     * for exceptional code paths or rare code paths that should never be included in a compilation
     * unit. See {@link CompilerDirectives#transferToInterpreter()} for the corresponding compiler
     * directive.
     * <p>
     * {@link CompilerDirectives#bailout(String)} should be used if failing compilation is desired,
     * e.g., for testing. {@code neverPartOfCompilation()} must not be reachable for runtime
     * compilation, see the TruffleCheckNeverPartOfCompilation option.
     *
     * @since 0.8 or earlier
     */
    public static void neverPartOfCompilation() {
    }

    /**
     * Assertion that this code position should never be reached during compilation. It can be used
     * for exceptional code paths or rare code paths that should never be included in a compilation
     * unit. See {@link CompilerDirectives#transferToInterpreter()} for the corresponding compiler
     * directive.
     * <p>
     * {@link CompilerDirectives#bailout(String)} should be used if failing compilation is desired,
     * e.g., for testing. {@code neverPartOfCompilation()} must not be reachable for runtime
     * compilation, see the TruffleCheckNeverPartOfCompilation option.
     *
     * @param message text associated with the bailout exception
     * @since 0.8 or earlier
     */
    public static void neverPartOfCompilation(String message) {
    }

    /**
     * Assertion that the corresponding value is reduced to a constant during compilation. This is,
     * for example, useful to assert that an array has always the same size, although the actual
     * value is only known at run-time. In most cases, it is preferred to use
     * {@link CompilerAsserts#partialEvaluationConstant(Object)} and its specialized variants.
     *
     * @param value the value that must be constant during compilation
     * @since 0.8 or earlier
     * @see CompilerAsserts#partialEvaluationConstant(Object)
     */
    public static <T> void compilationConstant(Object value) {
        if (!CompilerDirectives.isCompilationConstant(value)) {
            neverPartOfCompilation("Value is not compilation constant");
        }
    }

    /**
     * Assertion that the corresponding value is reduced to a constant during the initial partial
     * evaluation phase. Compared with {@link CompilerAsserts#compilationConstant(Object)}, the
     * constantness of the value is checked much earlier in the compilation pipeline. It should
     * therefore be preferred, also because its specialized variants avoid boxing.
     *
     * @param value the value that must be constant during compilation
     * @since 0.8 or earlier
     * @see CompilerAsserts#compilationConstant(Object)
     */
    public static <T> void partialEvaluationConstant(Object value) {
    }

    /**
     * Specialized version of {@link CompilerAsserts#compilationConstant(Object)} for
     * <code>boolean</code> values.
     *
     * @param value the value that must be constant during partial evaluation.
     * @since 19.3
     * @see CompilerAsserts#partialEvaluationConstant(Object)
     */
    public static <T> void partialEvaluationConstant(boolean value) {
    }

    /**
     * Specialized version of {@link CompilerAsserts#compilationConstant(Object)} for
     * <code>int</code> values.
     *
     * @param value the value that must be constant during partial evaluation.
     * @since 19.3
     * @see CompilerAsserts#partialEvaluationConstant(Object)
     */
    public static <T> void partialEvaluationConstant(int value) {
    }

    /**
     * Specialized version of {@link CompilerAsserts#compilationConstant(Object)} for
     * <code>float</code> values.
     *
     * @param value the value that must be constant during partial evaluation.
     * @since 19.3
     * @see CompilerAsserts#partialEvaluationConstant(Object)
     */
    public static <T> void partialEvaluationConstant(float value) {
    }

    /**
     * Specialized version of {@link CompilerAsserts#compilationConstant(Object)} for
     * <code>long</code> values.
     *
     * @param value the value that must be constant during partial evaluation.
     * @since 19.3
     * @see CompilerAsserts#partialEvaluationConstant(Object)
     */
    public static <T> void partialEvaluationConstant(long value) {
    }

    /**
     * Specialized version of {@link CompilerAsserts#compilationConstant(Object)} for
     * <code>double</code> values.
     *
     * @param value the value that must be constant during partial evaluation.
     * @since 19.3
     * @see CompilerAsserts#partialEvaluationConstant(Object)
     */
    public static <T> void partialEvaluationConstant(double value) {
    }

    /**
     * Indicates a code path that is not supposed to be reached during compilation or
     * interpretation. If it is reached then it is considered fatal internal error and execution
     * typically should not continue. Transfers to interpreter and
     * {@link CompilerDirectives#transferToInterpreterAndInvalidate() invalidates} the compiled code
     * and throws an {@link AssertionError} when reached unexpectedly.
     * <p>
     * This method returns a runtime exception to be conveniently used in combination with Java
     * throw statements, for example:
     *
     * <pre>
     * if (expectedCondition) {
     *     return 42;
     * } else {
     *     throw shouldNotReachHere();
     * }
     * </pre>
     *
     * @see #neverPartOfCompilation() to throw an assertion only on compiled code paths
     * @since 20.2
     */
    public static RuntimeException shouldNotReachHere() {
        transferToInterpreterAndInvalidate();
        throw shouldNotReachHere(null, null);
    }

    /**
     * Indicates a code path that is not supposed to be reached during compilation or
     * interpretation. If it is reached then it is considered fatal internal error and execution
     * typically should not continue. Transfers to interpreter and
     * {@link CompilerDirectives#transferToInterpreterAndInvalidate() invalidates} the compiled code
     * and throws an {@link AssertionError} when reached unexpectedly.
     * <p>
     * This method returns a runtime exception to be conveniently used in combination with Java
     * throw statements, for example:
     *
     * <pre>
     * if (expectedCondition) {
     *     return 42;
     * } else {
     *     throw shouldNotReachHere("Additional message");
     * }
     * </pre>
     *
     * @param message an additional message for the exception thrown.
     * @see #neverPartOfCompilation() to throw an assertion only on compiled code paths
     * @since 20.2
     */
    public static RuntimeException shouldNotReachHere(String message) {
        transferToInterpreterAndInvalidate();
        throw shouldNotReachHere(message, null);
    }

    /**
     * Indicates a code path that is not supposed to be reached during compilation or
     * interpretation. If it is reached then it is considered fatal internal error and execution
     * typically should not continue. Transfers to interpreter and
     * {@link CompilerDirectives#transferToInterpreterAndInvalidate() invalidates} the compiled code
     * and throws an {@link AssertionError} when reached unexpectedly.
     * <p>
     * This method returns a runtime exception to be conveniently used in combination with Java
     * throw statements, for example:
     *
     * <pre>
     * if (expectedCondition) {
     *     return 42;
     * } else {
     *     throw shouldNotReachHere("Additional message");
     * }
     * </pre>
     *
     * @param cause the cause if an exception was responsible for the unexpected case.
     * @see #neverPartOfCompilation() to throw an assertion only on compiled code paths
     * @since 20.2
     */
    public static RuntimeException shouldNotReachHere(Throwable cause) {
        transferToInterpreterAndInvalidate();
        throw shouldNotReachHere(null, cause);
    }

    /**
     * Indicates a code path that is not supposed to be reached during compilation or
     * interpretation. If it is reached then it is considered fatal internal error and execution
     * typically should not continue. Transfers to interpreter and
     * {@link CompilerDirectives#transferToInterpreterAndInvalidate() invalidates} the compiled code
     * and throws an {@link AssertionError} when reached unexpectedly.
     * <p>
     * This method returns a runtime exception to be conveniently used in combination with Java
     * throw statements, for example:
     *
     * <pre>
     * if (expectedCondition) {
     *     return 42;
     * } else {
     *     throw shouldNotReachHere("Additional message");
     * }
     * </pre>
     *
     * @param message an additional message for the exception thrown.
     * @param cause the cause if an exception was responsible for the unexpected case.
     *
     * @see #neverPartOfCompilation() to throw an assertion only on compiled code paths
     * @since 20.2
     */
    public static RuntimeException shouldNotReachHere(String message, Throwable cause) {
        transferToInterpreterAndInvalidate();
        throw new ShouldNotReachHere(message, cause);
    }

    @SuppressWarnings("serial")
    static class ShouldNotReachHere extends AssertionError {

        ShouldNotReachHere(String message, Throwable cause) {
            super(message, cause);
        }

    }

}
