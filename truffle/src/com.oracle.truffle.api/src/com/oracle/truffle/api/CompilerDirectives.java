/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Objects;
import java.util.concurrent.Callable;

import com.oracle.truffle.api.nodes.ControlFlowException;

/**
 * Directives that influence the optimizations of the Truffle compiler. All of the operations have
 * no effect when executed in the Truffle interpreter.
 *
 * @since 0.8 or earlier
 */
public final class CompilerDirectives {
    /**
     * @deprecated accidentally public - don't use
     * @since 0.8 or earlier
     */
    @Deprecated
    public CompilerDirectives() {
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
         * Determines whether this method throws a {@link ControlFlowException}.
         *
         * @since 0.8 or earlier
         * @deprecated use {@link #transferToInterpreterOnException()}
         */
        @Deprecated
        boolean throwsControlFlowException() default false;

        /**
         * Determines whether execution should be transferred to the interpreter in the case that an
         * exception is thrown across this boundary.
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
}
