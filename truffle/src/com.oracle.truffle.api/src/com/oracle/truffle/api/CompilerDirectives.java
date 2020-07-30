/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Directives that influence the optimizations of the Truffle compiler. All of the operations have
 * no effect when executed in the Truffle interpreter.
 *
 * @since 0.8 or earlier
 */
public final class CompilerDirectives {

    private CompilerDirectives() {
    }

    /** @since 0.8 or earlier */
    public static final double LIKELY_PROBABILITY = 0.75;
    /** @since 0.8 or earlier */
    public static final double UNLIKELY_PROBABILITY = 1.0 - LIKELY_PROBABILITY;
    /** @since 0.8 or earlier */
    public static final double SLOWPATH_PROBABILITY = 0.0001;
    /** @since 0.8 or earlier */
    public static final double FASTPATH_PROBABILITY = 1.0 - SLOWPATH_PROBABILITY;

    /**
     * Directive for the compiler to discontinue compilation at this code position and instead
     * insert a transfer to the interpreter.
     *
     * @since 0.8 or earlier
     */
    public static void transferToInterpreter() {
        if (inInterpreter()) {
            Truffle.getRuntime().notifyTransferToInterpreter();
        }
    }

    /**
     * Directive for the compiler to discontinue compilation at this code position and instead
     * insert a transfer to the interpreter, invalidating the currently executing machine code.
     *
     * @since 0.8 or earlier
     */
    public static void transferToInterpreterAndInvalidate() {
        if (inInterpreter()) {
            Truffle.getRuntime().notifyTransferToInterpreter();
        }
    }

    /**
     * Returns a boolean value indicating whether the method is executed in the interpreter.
     *
     * @return {@code true} when executed in the interpreter, {@code false} in compiled code.
     * @since 0.8 or earlier
     */
    public static boolean inInterpreter() {
        return true;
    }

    /**
     * Returns a boolean value indicating whether the method is executed in the compiled code.
     *
     * @return {@code false} when executed in the interpreter, {@code true} in compiled code.
     * @since 0.8 or earlier
     */
    public static boolean inCompiledCode() {
        return false;
    }

    /**
     * Returns a boolean value indicating whether the method is executed in the root of a Truffle
     * compilation.
     *
     * @return {@code false} when executed in the interpreter or in an inlined {@link CallTarget},
     *         {@code true} when in non-inlined compiled code.
     * @since 0.28 or earlier
     */
    public static boolean inCompilationRoot() {
        return false;
    }

    /**
     * Returns a boolean indicating whether or not a given value is seen as constant in optimized
     * code. If this method is called in the interpreter this method will always return
     * <code>true</code>.
     *
     * Note that optimizations that a compiler will apply to code that is conditional on
     * <code>isCompilationConstant</code> may be limited. For this reason
     * <code>isCompilationConstant</code> is not recommended for use to select between alternate
     * implementations of functionality depending on whether a value is constant. Instead, it is
     * intended for use as a diagnostic mechanism.
     *
     * @param value
     * @return {@code true} when given value is seen as compilation constant, {@code false} if not
     *         compilation constant.
     * @since 0.8 or earlier
     */
    public static boolean isCompilationConstant(Object value) {
        return CompilerDirectives.inInterpreter();
    }

    /**
     * Returns a boolean indicating whether or not a given value is seen as constant during the
     * initial partial evaluation phase. If this method is called in the interpreter this method
     * will always return <code>true</code>.
     *
     * @param value
     * @return {@code true} when given value is seen as compilation constant, {@code false} if not
     *         compilation constant.
     * @since 0.8 or earlier
     */
    public static boolean isPartialEvaluationConstant(Object value) {
        return CompilerDirectives.inInterpreter();
    }

    /**
     * Directive for the compiler that the given runnable should only be executed in the interpreter
     * and ignored in the compiled code.
     *
     * @param runnable the closure that should only be executed in the interpreter
     * @since 0.8 or earlier
     */
    public static void interpreterOnly(Runnable runnable) {
        runnable.run();
    }

    /**
     * Directive for the compiler that the given callable should only be executed in the
     * interpreter.
     *
     * @param callable the closure that should only be executed in the interpreter
     * @return the result of executing the closure in the interpreter and null in the compiled code
     * @throws Exception If the closure throws an exception when executed in the interpreter.
     * @since 0.8 or earlier
     */
    public static <T> T interpreterOnly(Callable<T> callable) throws Exception {
        return callable.call();
    }

    /**
     * Injects a probability for the given condition into the probability information of the
     * immediately succeeding branch instruction for the condition. The probability must be a value
     * between 0.0 and 1.0 (inclusive). The condition should not be a combined condition.
     *
     * Example usage immediately before an if statement (it specifies that the likelihood for a to
     * be greater than b is 90%):
     *
     * <code>
     * if (injectBranchProbability(0.9, a &gt; b)) {
     *    // ...
     * }
     * </code>
     *
     * Example usage for a combined condition (it specifies that the likelihood for a to be greater
     * than b is 90% and under the assumption that this is true, the likelihood for a being 0 is
     * 10%):
     *
     * <code>
     * if (injectBranchProbability(0.9, a &gt; b) &amp;&amp; injectBranchProbability(0.1, a == 0)) {
     *    // ...
     * }
     * </code>
     *
     * There are predefined constants for commonly used probabilities (see
     * {@link #LIKELY_PROBABILITY} , {@link #UNLIKELY_PROBABILITY}, {@link #SLOWPATH_PROBABILITY},
     * {@link #FASTPATH_PROBABILITY} ).
     *
     * @param probability the probability value between 0.0 and 1.0 that should be injected
     * @since 0.8 or earlier
     */
    public static boolean injectBranchProbability(double probability, boolean condition) {
        assert probability >= 0.0 && probability <= 1.0;
        return condition;
    }

    /**
     * Bails out of a compilation (e.g., for guest language features that should never be compiled).
     *
     * @param reason the reason for the bailout
     * @since 0.8 or earlier
     */
    public static void bailout(String reason) {
    }

    /**
     * Marks fields that should be considered final for a Truffle compilation although they are not
     * final while executing in the interpreter. If the field type is an array type, the compiler
     * considers reads with a constant index as constants.
     *
     * @since 0.8 or earlier
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD})
    public @interface CompilationFinal {
        /**
         * Specifies the number of array dimensions to be marked as compilation final.
         *
         * This value should be specified for all array-typed compilation-final fields and should be
         * left unspecified for other field types for which it has no meaning.
         *
         * The allowed range is from 0 to the number of declared array dimensions (inclusive).
         * Specifically, a {@code dimensions} value of 0 marks only the reference to the (outermost)
         * array as final but not its elements, a value of 1 marks the outermost array and all its
         * elements as final but not the elements of any nested arrays.
         *
         * For compatibility reasons, array-typed fields without an explicit {@code dimensions}
         * parameter default to the number of array dimensions declared in the field type.
         *
         * @since 0.14
         */
        int dimensions() default -1;
    }

    /**
     * Marks a method that it is considered as a boundary for Truffle partial evaluation.
     *
     * @since 0.8 or earlier
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
    public @interface TruffleBoundary {

        /**
         * Determines whether execution should be transferred to the interpreter if an exception is
         * thrown across this boundary, in which case the caller's compiled code is invalidated and
         * will not transfer to the interpreter on exceptions for this method again.
         *
         * @since 0.28
         */
        boolean transferToInterpreterOnException() default true;

        /**
         * Considers this Truffle boundary invoke as an inlining candidate.
         *
         * Partial evaluation cannot inline a boundary, but a later inlining pass can.
         *
         * @since 0.27
         */
        boolean allowInlining() default false;
    }

    /**
     * Marks classes as value types. Reference comparisons (==) between instances of those classes
     * have undefined semantics and can either return true or false.
     *
     * @since 0.8 or earlier
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    public @interface ValueType {
    }

    /**
     * Ensures that the given object is not virtual, i.e., not removed by Escape Analysis at the
     * point of this call.
     *
     * @param obj the object to exclude from Escape Analysis
     * @since 0.8 or earlier
     */
    public static void materialize(Object obj) {
    }

    /**
     * Ensures that the given object will be virtual (escape analyzed) at all points that are
     * dominated by the current position.
     *
     * @since 0.8 or earlier
     */
    public static void ensureVirtualized(@SuppressWarnings("unused") Object object) {
    }

    /**
     * Ensures that the given object will be virtual at the current position.
     *
     * @since 0.8 or earlier
     */
    public static void ensureVirtualizedHere(@SuppressWarnings("unused") Object object) {
    }

    /**
     * Casts the given object to the exact class represented by {@code clazz}. The cast succeeds
     * only if {@code object == null || object.getClass() == clazz} and thus fails for any subclass.
     *
     * @param object the object to be cast
     * @param clazz the class to check against, must not be null
     * @return the object after casting
     * @throws ClassCastException if the object is non-null and not exactly of the given class
     * @throws NullPointerException if the class argument is null
     * @since 0.33
     * @see Class#cast(Object)
     */
    @SuppressWarnings("unchecked")
    public static <T> T castExact(Object object, Class<T> clazz) {
        Objects.requireNonNull(clazz);
        if (object == null || object.getClass() == clazz) {
            return (T) object;
        } else {
            throw new ClassCastException();
        }
    }

    /**
     * Indicates a code path that is not supposed to be reached during compilation or
     * interpretation. Reaching this method is considered a fatal internal error and execution
     * should not continue. Transfers to interpreter and
     * {@link CompilerDirectives#transferToInterpreterAndInvalidate() invalidates} the compiled code
     * and always throws an {@link AssertionError} when invoked.
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
     * @since 20.2
     */
    public static RuntimeException shouldNotReachHere() {
        transferToInterpreterAndInvalidate();
        throw shouldNotReachHere(null, null);
    }

    /**
     * Indicates a code path that is not supposed to be reached during compilation or
     * interpretation. Reaching this method is considered a fatal internal error and execution
     * should not continue. Transfers to interpreter and
     * {@link CompilerDirectives#transferToInterpreterAndInvalidate() invalidates} the compiled code
     * and always throws an {@link AssertionError} when invoked.
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
     * @since 20.2
     */
    public static RuntimeException shouldNotReachHere(String message) {
        transferToInterpreterAndInvalidate();
        throw shouldNotReachHere(message, null);
    }

    /**
     * Indicates a code path that is not supposed to be reached during compilation or
     * interpretation. Reaching this method is considered a fatal internal error and execution
     * should not continue. Transfers to interpreter and
     * {@link CompilerDirectives#transferToInterpreterAndInvalidate() invalidates} the compiled code
     * and always throws an {@link AssertionError} when invoked.
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
     * @since 20.2
     */
    public static RuntimeException shouldNotReachHere(Throwable cause) {
        transferToInterpreterAndInvalidate();
        throw shouldNotReachHere(null, cause);
    }

    /**
     * Indicates a code path that is not supposed to be reached during compilation or
     * interpretation. Reaching this method is considered a fatal internal error and execution
     * should not continue. Transfers to interpreter and
     * {@link CompilerDirectives#transferToInterpreterAndInvalidate() invalidates} the compiled code
     * and always throws an {@link AssertionError} when invoked.
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
     * @since 20.2
     */
    public static RuntimeException shouldNotReachHere(String message, Throwable cause) {
        transferToInterpreterAndInvalidate();
        throw new ShouldNotReachHere(message, cause);
    }

    @SuppressWarnings("serial")
    static final class ShouldNotReachHere extends AssertionError {

        ShouldNotReachHere(String message, Throwable cause) {
            super(message, cause);
        }

    }

}
