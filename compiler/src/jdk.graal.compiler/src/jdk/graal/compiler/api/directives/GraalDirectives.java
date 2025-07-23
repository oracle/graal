/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.api.directives;

import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.java.AbstractNewObjectNode;
import jdk.graal.compiler.phases.common.FloatingReadPhase;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.SpeculationLog.SpeculationReason;

// JaCoCo Exclude

/**
 * Directives that influence the compilation of methods by Graal. They don't influence the semantics
 * of the code, but they are useful for unit testing and benchmarking.
 *
 * Any methods defined in this class should be intrinsified via invocation plugins.
 */
public final class GraalDirectives {

    public static final double LIKELY_PROBABILITY = 0.75;
    public static final double UNLIKELY_PROBABILITY = 1.0 - LIKELY_PROBABILITY;

    public static final double SLOWPATH_PROBABILITY = 0.0001;
    public static final double FASTPATH_PROBABILITY = 1.0 - SLOWPATH_PROBABILITY;

    /**
     * Forces a safepoint in the compiled code.
     */
    public static void safepoint() {

    }

    /**
     * Directive for the compiler to fall back to the bytecode interpreter at this point. All
     * arguments to this method must be compile-time constant.
     *
     * @param action the action to take with respect to the code being deoptimized
     * @param reason the reason to use for the deoptimization
     * @param speculation a speculation to be attached to the deoptimization
     */
    public static void deoptimize(DeoptimizationAction action, DeoptimizationReason reason, SpeculationReason speculation) {
    }

    /**
     * Directive for the compiler to fall back to the bytecode interpreter at this point. All
     * arguments to this method must be compile-time constant.
     *
     * @param action the action to take with respect to the code being deoptimized
     * @param reason the reason to use for the deoptimization
     * @param withSpeculation if true, then a speculation will be attached to the deoptimization
     */
    public static void deoptimize(DeoptimizationAction action, DeoptimizationReason reason, boolean withSpeculation) {
    }

    /**
     * Directive for the compiler to fall back to the bytecode interpreter at this point.
     * <p/>
     *
     * This is equivalent to calling
     * {@link #deoptimize(DeoptimizationAction, DeoptimizationReason, boolean)} with
     * {@link DeoptimizationAction#None}, {@link DeoptimizationReason#TransferToInterpreter} and
     * {@code false} as arguments.
     * <p/>
     *
     * This directive is typically used directly after a branch:
     *
     * <pre>
     * if (someCondition) {
     *     deoptimize();
     * }
     * </pre>
     *
     * This combination will be transformed into a guard. Code between the {@code if} and the
     * deoptimization may be removed from the compiled code.
     */
    public static void deoptimize() {
    }

    /**
     * Directive for the compiler to fall back to the bytecode interpreter at this point.
     *
     * This is equivalent to calling
     * {@link #deoptimize(DeoptimizationAction, DeoptimizationReason, boolean)} with
     * {@link DeoptimizationAction#InvalidateReprofile},
     * {@link DeoptimizationReason#TransferToInterpreter} and {@code false} as arguments.
     */
    public static void deoptimizeAndInvalidate() {
    }

    /**
     * Directive for the compiler to fall back to the bytecode interpreter at this point.
     * <p/>
     *
     * This is similar to calling {@link #deoptimize()}, but the deoptimization will use a precise
     * frame state and will be prevented from being converted to a guard or otherwise moving in the
     * control flow.
     * <p/>
     *
     * This directive is typically used if the deoptimization is at the end of a section of code
     * that should remain part of the compiled code:
     *
     * <pre>
     *     if (someCondition) {
     *         ... some code ...
     *         preciseDeoptimize();
     *     }
     * </pre>
     *
     * Unlike {@link #deoptimize()}, this construct will not be transformed into a guard, and the
     * code preceding the precise deoptimization will not be removed from the compiled code.
     */
    public static void preciseDeoptimize() {

    }

    /**
     * Returns a boolean value indicating whether the method is executed in Graal-compiled code.
     */
    public static boolean inCompiledCode() {
        return false;
    }

    /**
     * Determines if the method is called within the scope of a Graal intrinsic.
     */
    public static boolean inIntrinsic() {
        return false;
    }

    /**
     * A call to this method will never be duplicated by control flow optimizations in the compiler.
     */
    public static void controlFlowAnchor() {
    }

    /**
     * A call to this method will disable strip mining of the enclosing loop in the compiler.
     */
    public static void neverStripMine() {
    }

    /**
     * A call to this method will disable write sinking of fields in the enclosing loop in the
     * compiler.
     */
    public static void neverWriteSink() {
    }

    /**
     * A call to this method will assume a stable dimension array if {@code t} is a constant array
     * and {@code i} a constant integer.
     */
    public static <T> T assumeStableDimension(T t, @SuppressWarnings("unused") int i) {
        return t;
    }

    /**
     * A call to this method will force the compiler to assume this instruction has a visible memory
     * effect killing all memory locations.
     */
    public static void sideEffect() {
    }

    /**
     * Inject information into the compiler to assume that the input is an object created via a
     * primitive boxing operation.
     */
    public static <P> P trustedBox(P o) {
        return o;
    }

    /**
     * A call to this method will force the compiler to assume this instruction has a visible memory
     * effect killing all memory locations.
     */
    public static int sideEffect(int a) {
        return a;
    }

    /**
     * A call to this method will force the compiler to assume this instruction has a visible memory
     * effect killing all memory locations.
     */
    public static long sideEffect(long a) {
        return a;
    }

    /**
     * A call to this method will force the compiler to assume from this position on that the given
     * argument is a positive number.
     */
    public static int positivePi(int n) {
        return n;
    }

    /**
     * A call to this method will force the compiler to assume from this position on that the given
     * argument is a positive number.
     */
    public static long positivePi(long n) {
        return n;
    }

    /**
     * Injects a probability for the given condition into the profiling information of a branch
     * instruction. The probability must be a value between 0.0 and 1.0 (inclusive). This directive
     * should only be used for the condition of an if statement. The parameter condition should also
     * only denote a simple condition and not a combined condition involving &amp;&amp; or ||
     * operators.
     *
     * Example usage (it specifies that the likelihood for a to be greater than b is 90%):
     *
     * <code>
     * if (injectBranchProbability(0.9, a &gt; b)) {
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
        assert probability >= 0.0 && probability <= 1.0 : "Probability must be between [0D;1D] but is " + probability;
        return condition;
    }

    /**
     * Injects an average iteration count of a loop into the probability information of a loop exit
     * condition. The iteration count specifies how often the condition is checked, i.e. in for and
     * while loops it is one more than the body iteration count, and in do-while loops it is equal
     * to the body iteration count. The iteration count must be >= 1.0.
     *
     * Example usage (it specifies that the expected iteration count of the loop condition is 500,
     * so the iteration count of the loop body is 499):
     *
     * {@snippet :
     * for (int i = 0; injectIterationCount(500, i < array.length); i++) {
     *     // ...
     * }
     * }
     *
     * @param iterations the expected number of iterations that should be injected
     */
    public static boolean injectIterationCount(double iterations, boolean condition) {
        // the plugin handles the semantics
        return condition;
    }

    /**
     * Injects a probability into the profiling information of a switch branch. The probability must
     * be a value between 0.0 and 1.0 (inclusive). This directive should only be used as the first
     * statement of each switch branch. Either all or none of the branches should contain a call to
     * injectSwitchCaseProbability, and the sum of the values across all branches must be 1.0.
     *
     * Example usage:
     *
     * <code>
     * int a = ...;
     * switch (a) {
     *    case 0:
     *       GraalDirectives.injectSwitchCaseProbability(0.3);
     *       // ...
     *       break;
     *    case 10:
     *      GraalDirectives.injectSwitchCaseProbability(0.2);
     *      // ...
     *      break;
     *    default:
     *      GraalDirectives.injectSwitchCaseProbability(0.5);
     *      // ...
     *      break;
     * }
     * </code>
     *
     * @param probability the probability value between 0.0 and 1.0 that should be injected
     */
    public static void injectSwitchCaseProbability(double probability) {
    }

    /**
     * Consume a value, making sure the compiler doesn't optimize away the computation of this
     * value, even if it is otherwise unused.
     */
    @SuppressWarnings("unused")
    public static void blackhole(boolean value) {
    }

    /**
     * Consume a value, making sure the compiler doesn't optimize away the computation of this
     * value, even if it is otherwise unused.
     */
    @SuppressWarnings("unused")
    public static void blackhole(byte value) {
    }

    /**
     * Consume a value, making sure the compiler doesn't optimize away the computation of this
     * value, even if it is otherwise unused.
     */
    @SuppressWarnings("unused")
    public static void blackhole(short value) {
    }

    /**
     * Consume a value, making sure the compiler doesn't optimize away the computation of this
     * value, even if it is otherwise unused.
     */
    @SuppressWarnings("unused")
    public static void blackhole(char value) {
    }

    /**
     * Consume a value, making sure the compiler doesn't optimize away the computation of this
     * value, even if it is otherwise unused.
     */
    @SuppressWarnings("unused")
    public static void blackhole(int value) {
    }

    /**
     * Consume a value, making sure the compiler doesn't optimize away the computation of this
     * value, even if it is otherwise unused.
     */
    @SuppressWarnings("unused")
    public static void blackhole(long value) {
    }

    /**
     * Consume a value, making sure the compiler doesn't optimize away the computation of this
     * value, even if it is otherwise unused.
     */
    @SuppressWarnings("unused")
    public static void blackhole(float value) {
    }

    /**
     * Consume a value, making sure the compiler doesn't optimize away the computation of this
     * value, even if it is otherwise unused.
     */
    @SuppressWarnings("unused")
    public static void blackhole(double value) {
    }

    /**
     * Consume a value, making sure the compiler doesn't optimize away the computation of this
     * value, even if it is otherwise unused.
     */
    @SuppressWarnings("unused")
    public static void blackhole(Object value) {
    }

    /**
     * Forces a value to be kept in a register.
     */
    @SuppressWarnings("unused")
    public static void bindToRegister(boolean value) {
    }

    /**
     * Forces a value to be kept in a register.
     */
    @SuppressWarnings("unused")
    public static void bindToRegister(byte value) {
    }

    /**
     * Forces a value to be kept in a register.
     */
    @SuppressWarnings("unused")
    public static void bindToRegister(short value) {
    }

    /**
     * Forces a value to be kept in a register.
     */
    @SuppressWarnings("unused")
    public static void bindToRegister(char value) {
    }

    /**
     * Forces a value to be kept in a register.
     */
    @SuppressWarnings("unused")
    public static void bindToRegister(int value) {
    }

    /**
     * Forces a value to be kept in a register.
     */
    @SuppressWarnings("unused")
    public static void bindToRegister(long value) {
    }

    /**
     * Forces a value to be kept in a register.
     */
    @SuppressWarnings("unused")
    public static void bindToRegister(float value) {
    }

    /**
     * Forces a value to be kept in a register.
     */
    @SuppressWarnings("unused")
    public static void bindToRegister(double value) {
    }

    /**
     * Forces a value to be kept in a register.
     */
    @SuppressWarnings("unused")
    public static void bindToRegister(Object value) {
    }

    /**
     * Spills all caller saved registers.
     */
    public static void spillRegisters() {
    }

    /**
     * Do nothing, but also make sure the compiler doesn't do any optimizations across this call.
     *
     * For example, the compiler will constant fold the expression 5 * 3, but the expression 5 *
     * opaque(3) will result in a real multiplication, because the compiler will not see that
     * opaque(3) is a constant.
     */
    public static boolean opaque(boolean value) {
        return value;
    }

    /**
     * Do nothing, but also make sure the compiler doesn't do any optimizations across this call.
     *
     * For example, the compiler will constant fold the expression 5 * 3, but the expression 5 *
     * opaque(3) will result in a real multiplication, because the compiler will not see that
     * opaque(3) is a constant.
     */
    public static byte opaque(byte value) {
        return value;
    }

    /**
     * Do nothing, but also make sure the compiler doesn't do any optimizations across this call.
     *
     * For example, the compiler will constant fold the expression 5 * 3, but the expression 5 *
     * opaque(3) will result in a real multiplication, because the compiler will not see that
     * opaque(3) is a constant.
     */
    public static short opaque(short value) {
        return value;
    }

    /**
     * Do nothing, but also make sure the compiler doesn't do any optimizations across this call.
     *
     * For example, the compiler will constant fold the expression 5 * 3, but the expression 5 *
     * opaque(3) will result in a real multiplication, because the compiler will not see that
     * opaque(3) is a constant.
     */
    public static char opaque(char value) {
        return value;
    }

    /**
     * Do nothing, but also make sure the compiler doesn't do any optimizations across this call.
     *
     * For example, the compiler will constant fold the expression 5 * 3, but the expression 5 *
     * opaque(3) will result in a real multiplication, because the compiler will not see that
     * opaque(3) is a constant.
     */
    public static int opaque(int value) {
        return value;
    }

    /**
     * Do nothing, but also make sure the compiler doesn't do any optimizations across this call.
     *
     * For example, the compiler will constant fold the expression 5 * 3, but the expression 5 *
     * opaque(3) will result in a real multiplication, because the compiler will not see that
     * opaque(3) is a constant.
     */
    public static long opaque(long value) {
        return value;
    }

    /**
     * Do nothing, but also make sure the compiler doesn't do any optimizations across this call.
     *
     * For example, the compiler will constant fold the expression 5 * 3, but the expression 5 *
     * opaque(3) will result in a real multiplication, because the compiler will not see that
     * opaque(3) is a constant.
     */
    public static float opaque(float value) {
        return value;
    }

    /**
     * Do nothing, but also make sure the compiler doesn't do any optimizations across this call.
     *
     * For example, the compiler will constant fold the expression 5 * 3, but the expression 5 *
     * opaque(3) will result in a real multiplication, because the compiler will not see that
     * opaque(3) is a constant.
     */
    public static double opaque(double value) {
        return value;
    }

    /**
     * Do nothing, but also make sure the compiler doesn't do any optimizations across this call.
     *
     * For example, the compiler will constant fold the expression 5 * 3, but the expression 5 *
     * opaque(3) will result in a real multiplication, because the compiler will not see that
     * opaque(3) is a constant.
     */
    public static <T> T opaque(T value) {
        return value;
    }

    /**
     * Do nothing, but also make sure the compiler doesn't do any optimizations across this call
     * until the specified {@link StageFlag} has been applied.
     *
     * For example, if {@code stage == StageFlag.FLOATING_READS}, the compiler will not constant
     * fold the expression 5 * opaqueUntilAfter(3, stage) until {@link FloatingReadPhase} has been
     * applied. After that, the optimization barrier will fall away.
     */
    @SuppressWarnings("unused")
    public static boolean opaqueUntilAfter(boolean value, StageFlag stage) {
        return value;
    }

    /**
     * Do nothing, but also make sure the compiler doesn't do any optimizations across this call
     * until the specified {@link StageFlag} has been applied.
     *
     * For example, if {@code stage == StageFlag.FLOATING_READS}, the compiler will not constant
     * fold the expression 5 * opaqueUntilAfter(3, stage) until {@link FloatingReadPhase} has been
     * applied. After that, the optimization barrier will fall away.
     */
    @SuppressWarnings("unused")
    public static byte opaqueUntilAfter(byte value, StageFlag stage) {
        return value;
    }

    /**
     * Do nothing, but also make sure the compiler doesn't do any optimizations across this call
     * until the specified {@link StageFlag} has been applied.
     *
     * For example, if {@code stage == StageFlag.FLOATING_READS}, the compiler will not constant
     * fold the expression 5 * opaqueUntilAfter(3, stage) until {@link FloatingReadPhase} has been
     * applied. After that, the optimization barrier will fall away.
     */
    @SuppressWarnings("unused")
    public static short opaqueUntilAfter(short value, StageFlag stage) {
        return value;
    }

    /**
     * Do nothing, but also make sure the compiler doesn't do any optimizations across this call
     * until the specified {@link StageFlag} has been applied.
     *
     * For example, if {@code stage == StageFlag.FLOATING_READS}, the compiler will not constant
     * fold the expression 5 * opaqueUntilAfter(3, stage) until {@link FloatingReadPhase} has been
     * applied. After that, the optimization barrier will fall away.
     */
    @SuppressWarnings("unused")
    public static char opaqueUntilAfter(char value, StageFlag stage) {
        return value;
    }

    /**
     * Do nothing, but also make sure the compiler doesn't do any optimizations across this call
     * until the specified {@link StageFlag} has been applied.
     *
     * For example, if {@code stage == StageFlag.FLOATING_READS}, the compiler will not constant
     * fold the expression 5 * opaqueUntilAfter(3, stage) until {@link FloatingReadPhase} has been
     * applied. After that, the optimization barrier will fall away.
     */
    @SuppressWarnings("unused")
    public static int opaqueUntilAfter(int value, StageFlag stage) {
        return value;
    }

    /**
     * Do nothing, but also make sure the compiler doesn't do any optimizations across this call
     * until the specified {@link StageFlag} has been applied.
     *
     * For example, if {@code stage == StageFlag.FLOATING_READS}, the compiler will not constant
     * fold the expression 5 * opaqueUntilAfter(3, stage) until {@link FloatingReadPhase} has been
     * applied. After that, the optimization barrier will fall away.
     */
    @SuppressWarnings("unused")
    public static long opaqueUntilAfter(long value, StageFlag stage) {
        return value;
    }

    /**
     * Do nothing, but also make sure the compiler doesn't do any optimizations across this call
     * until the specified {@link StageFlag} has been applied.
     *
     * For example, if {@code stage == StageFlag.FLOATING_READS}, the compiler will not constant
     * fold the expression 5 * opaqueUntilAfter(3, stage) until {@link FloatingReadPhase} has been
     * applied. After that, the optimization barrier will fall away.
     */
    @SuppressWarnings("unused")
    public static float opaqueUntilAfter(float value, StageFlag stage) {
        return value;
    }

    /**
     * Do nothing, but also make sure the compiler doesn't do any optimizations across this call
     * until the specified {@link StageFlag} has been applied.
     *
     * For example, if {@code stage == StageFlag.FLOATING_READS}, the compiler will not constant
     * fold the expression 5 * opaqueUntilAfter(3, stage) until {@link FloatingReadPhase} has been
     * applied. After that, the optimization barrier will fall away.
     */
    @SuppressWarnings("unused")
    public static double opaqueUntilAfter(double value, StageFlag stage) {
        return value;
    }

    /**
     * Do nothing, but also make sure the compiler doesn't do any optimizations across this call
     * until the specified {@link StageFlag} has been applied.
     *
     * For example, if {@code stage == StageFlag.FLOATING_READS}, the compiler will not constant
     * fold the expression 5 * opaqueUntilAfter(3, stage) until {@link FloatingReadPhase} has been
     * applied. After that, the optimization barrier will fall away.
     */
    @SuppressWarnings("unused")
    public static <T> T opaqueUntilAfter(T value, StageFlag stage) {
        return value;
    }

    public static <T> T guardingNonNull(T value) {
        if (value == null) {
            deoptimize();
        }
        return value;
    }

    /**
     * Ensures that the given object allocation is represented as one that never moves, i.e., is
     * fixed and has proper exception edges. {@code object} must be an allocation represented by a
     * {@link AbstractNewObjectNode} in Graal IR. There must not be any statements between the
     * original allocation bytecode and the call to {@link #ensureAllocatedHere(Object)}.
     * Additionally, the parameter to the intrinsic must be a fresh allocation and no local variable
     * because there must not be any references to the allocation before it is marked non-movable.
     *
     * Allowed patterns are
     *
     * <pre>
     * Object[] array = GraalDirectives.ensureAllocatedHere(new Object[10]);
     * </pre>
     *
     * but not cases where there are statements between the allocation and the intrinsic
     *
     * <pre>
     * Object[] array = new Object[10];
     * sideEffect(); // prohibited to have something between allocation and intrinsic
     * GraalDirectives.ensureAllocatedHere(array);
     * </pre>
     *
     * and patterns where the argument is used as a local variable are also not allowed
     *
     * <pre>
     * Object[] array = new Object[10];// used as a local
     * GraalDirectives.ensureAllocatedHere(array);
     * </pre>
     */
    public static <T> T ensureAllocatedHere(T object) {
        return object;
    }

    /**
     * Ensures that the given object will be virtual (escape analyzed) at all points that are
     * dominated by the current position.
     */
    public static void ensureVirtualized(@SuppressWarnings("unused") Object object) {
    }

    /**
     * Ensures that the given object will be virtual at the current position.
     */
    public static void ensureVirtualizedHere(@SuppressWarnings("unused") Object object) {
    }

    /**
     * Raise a SIGTRAP that can be used as a breakpoint for a native debugger such as gdb.
     */
    public static void breakpoint() {
    }

    /**
     * Returns a boolean indicating whether or not a given value is seen as constant in optimized
     * code.
     */
    @SuppressWarnings("unused")
    public static boolean isCompilationConstant(Object value) {
        return false;
    }

    /**
     * @see #isCompilationConstant(Object)
     */
    @SuppressWarnings("unused")
    public static boolean isCompilationConstant(boolean value) {
        return false;
    }

    /**
     * @see #isCompilationConstant(Object)
     */
    @SuppressWarnings("unused")
    public static boolean isCompilationConstant(byte value) {
        return false;
    }

    /**
     * @see #isCompilationConstant(Object)
     */
    @SuppressWarnings("unused")
    public static boolean isCompilationConstant(short value) {
        return false;
    }

    /**
     * @see #isCompilationConstant(Object)
     */
    @SuppressWarnings("unused")
    public static boolean isCompilationConstant(char value) {
        return false;
    }

    /**
     * @see #isCompilationConstant(Object)
     */
    @SuppressWarnings("unused")
    public static boolean isCompilationConstant(int value) {
        return false;
    }

    /**
     * @see #isCompilationConstant(Object)
     */
    @SuppressWarnings("unused")
    public static boolean isCompilationConstant(float value) {
        return false;
    }

    /**
     * @see #isCompilationConstant(Object)
     */
    @SuppressWarnings("unused")
    public static boolean isCompilationConstant(long value) {
        return false;
    }

    /**
     * @see #isCompilationConstant(Object)
     */
    @SuppressWarnings("unused")
    public static boolean isCompilationConstant(double value) {
        return false;
    }

    /**
     * Prints a string to the log stream.
     */
    @SuppressWarnings("unused")
    public static void log(String value) {
        System.out.print(value);
    }

    /**
     * Prints a formatted string to the log stream.
     *
     * @param format a C style printf format value that can contain at most one conversion specifier
     *            (i.e., a sequence of characters starting with '%').
     * @param value the value associated with the conversion specifier
     */
    @SuppressWarnings("unused")
    public static void log(String format, long value) {
        System.out.printf(format, value);
    }

    /**
     * Prints a formatted string to the log stream.
     *
     * @param format a C style printf format value that can contain at most two conversion
     *            specifiers (i.e., a sequence of characters starting with '%').
     * @param v1 the value associated with the first conversion specifier
     * @param v2 the value associated with the second conversion specifier
     */
    @SuppressWarnings("unused")
    public static void log(String format, long v1, long v2) {
        System.out.printf(format, v1, v2);
    }

    /**
     * Prints a formatted string to the log stream.
     *
     * @param format a C style printf format value that can contain at most three conversion
     *            specifiers (i.e., a sequence of characters starting with '%').
     * @param v1 the value associated with the first conversion specifier
     * @param v2 the value associated with the second conversion specifier
     * @param v3 the value associated with the third conversion specifier
     */
    @SuppressWarnings("unused")
    public static void log(String format, long v1, long v2, long v3) {
        System.out.printf(format, v1, v2, v3);
    }
}
