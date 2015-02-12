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

import java.lang.annotation.*;
import java.util.concurrent.*;

/**
 * Directives that influence the optimizations of the Truffle compiler. All of the operations have
 * no effect when executed in the Truffle interpreter.
 */
public final class CompilerDirectives {

    public static final double LIKELY_PROBABILITY = 0.75;
    public static final double UNLIKELY_PROBABILITY = 1.0 - LIKELY_PROBABILITY;

    public static final double SLOWPATH_PROBABILITY = 0.0001;
    public static final double FASTPATH_PROBABILITY = 1.0 - SLOWPATH_PROBABILITY;

    /**
     * Directive for the compiler to discontinue compilation at this code position and instead
     * insert a transfer to the interpreter.
     */
    public static void transferToInterpreter() {
        if (inInterpreter()) {
            Truffle.getRuntime().notifyTransferToInterpreter();
        }
    }

    /**
     * Directive for the compiler to discontinue compilation at this code position and instead
     * insert a transfer to the interpreter, invalidating the currently executing machine code.
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
     */
    public static boolean inInterpreter() {
        return true;
    }

    /**
     * Returns a boolean value indicating whether the method is executed in the compiled code.
     *
     * @return {@code false} when executed in the interpreter, {@code true} in compiled code.
     */
    public static boolean inCompiledCode() {
        return false;
    }

    /**
     * Returns a boolean indicating whether or not a given value is seen as constant in optimized
     * code. If this method is called in the interpreter this method will always return
     * <code>false</code>. This API may be used in combination with {@link #inCompiledCode()} to
     * implement compilation constant assertions in the following way:
     *
     * <pre>
     * <code>
     * void assertCompilationConstant(Object value) {
     *   if (inCompiledCode()) {
     *     if (!isCompilationConstant(value)) {
     *       throw new AssertionError("Given value is not constant");
     *     }
     *   }
     * }
     * </code>
     * </pre>
     *
     * Note that optimizations that a compiler will apply to code that is conditional on
     * <code>isCompilationConstant</code> may be limited. For this reason
     * <code>isCompilationConstant</code> is not recommended for use to select between alternate
     * implementations of functionality depending on whether a value is constant. Instead, it is
     * intended for use as a diagnostic mechanism, such as illustrated above.
     *
     * @param value
     * @return {@code true} when given value is seen as compilation constant, {@code false} if not
     *         compilation constant.
     */
    public static boolean isCompilationConstant(Object value) {
        return false;
    }

    /**
     * Directive for the compiler that the given runnable should only be executed in the interpreter
     * and ignored in the compiled code.
     *
     * @param runnable the closure that should only be executed in the interpreter
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
     */
    public static boolean injectBranchProbability(double probability, boolean condition) {
        assert probability >= 0.0 && probability <= 1.0;
        return condition;
    }

    /**
     * Bails out of a compilation (e.g., for guest language features that should never be compiled).
     *
     * @param reason the reason for the bailout
     */
    public static void bailout(String reason) {
    }

    /**
     * Marks fields that should be considered final for a Truffle compilation although they are not
     * final while executing in the interpreter.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD})
    public @interface CompilationFinal {
    }

    /**
     * Marks a method that it is considered as a boundary for Truffle partial evaluation.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
    public @interface TruffleBoundary {
    }

    /**
     * Marks classes as value types. Reference comparisons (==) between instances of those classes
     * have undefined semantics and can either return true or false.
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
     */
    public static void materialize(Object obj) {
    }
}
