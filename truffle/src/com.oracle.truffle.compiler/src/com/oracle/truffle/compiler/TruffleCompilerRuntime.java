/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.compiler;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Defines an interface to the Truffle metadata required by a {@link TruffleCompiler} without
 * exposing a Truffle compiler directly to Truffle AST classes. This allows a Truffle runtime and
 * Truffle compiler to exists in separate heaps or even separate processes.
 */
public interface TruffleCompilerRuntime {

    /**
     * Controls behavior of {@code ExplodeLoop} annotation.
     *
     */
    enum LoopExplosionKind {
        /**
         * No loop explosion.
         */
        NONE,
        /**
         * Fully unroll all loops. The loops must have a known finite number of iterations. If a
         * loop has multiple loop ends, they are merged so that the subsequent loop iteration is
         * processed only once. For example, a loop with 4 iterations and 2 loop ends leads to
         * 1+1+1+1 = 4 copies of the loop body.
         *
         * @since 0.15
         */
        FULL_UNROLL,
        /**
         * Like {@link #FULL_UNROLL}, but in addition loop unrolling duplicates loop exits in every
         * iteration instead of merging them. Code after a loop exit is duplicated for every loop
         * exit and every loop iteration. For example, a loop with 4 iterations and 2 loop exits
         * (exit1 and exit2, where exit1 is an early return inside a loop) leads to 4 copies of the
         * loop body and 4 copies of exit1 and 1 copy if exit2. After each exit all code until a
         * return is duplicated per iteration. Beware of break statements inside loops since they
         * cause additional loop exits leading to code duplication along exit2.
         */
        FULL_UNROLL_UNTIL_RETURN,
        /**
         * Fully explode all loops. The loops must have a known finite number of iterations. If a
         * loop has multiple loop ends, they are not merged so that subsequent loop iterations are
         * processed multiple times. For example, a loop with 4 iterations and 2 loop ends leads to
         * 1+2+4+8 = 15 copies of the loop body.
         *
         * @since 0.15
         */
        FULL_EXPLODE,
        /**
         * Like {@link #FULL_EXPLODE}, but in addition explosion does not stop at loop exits. Code
         * after the loop is duplicated for every loop exit of every loop iteration. For example, a
         * loop with 4 iterations and 2 loop exits leads to 4 * 2 = 8 copies of the code after the
         * loop.
         *
         * @since 0.15
         */
        FULL_EXPLODE_UNTIL_RETURN,
        /**
         * like {@link #FULL_EXPLODE}, but copies of the loop body that have the exact same state
         * (all local variables have the same value) are merged. This reduces the number of copies
         * necessary, but can introduce loops again. This kind is useful for bytecode interpreter
         * loops.
         *
         * @since 0.15
         */
        MERGE_EXPLODE
    }

    enum InlineKind {
        /**
         * Denotes a call site that must can be inlined.
         */
        INLINE(true),
        /**
         * Denotes a call site that must not be inlined and should be implemented by a node that
         * does not speculate on the call not raising an exception.
         */
        DO_NOT_INLINE_WITH_EXCEPTION(false),

        /**
         * Denotes a call site must not be inlined and can be implemented by a node that speculates
         * the call will not throw an exception.
         */
        DO_NOT_INLINE_NO_EXCEPTION(false),

        /**
         * Denotes a call site must not be inlined and the execution should be transferred to
         * interpreter in case of an exception.
         */
        DO_NOT_INLINE_DEOPTIMIZE_ON_EXCEPTION(false),

        /**
         * Denotes a call site must not be inlined and the execution should be speculatively
         * transferred to interpreter in case of an exception, unless the speculation has failed.
         */
        DO_NOT_INLINE_WITH_SPECULATIVE_EXCEPTION(false);

        private final boolean allowsInlining;

        InlineKind(final boolean allowsInlining) {
            this.allowsInlining = allowsInlining;
        }

        public boolean allowsInlining() {
            return allowsInlining;
        }
    }

    /**
     * Notifies this runtime once {@code installedCode} has been installed in the code cache. On
     * SubstrateVM this callback is currently unused.
     *
     * @param compilable the {@link TruffleCompilable compilable} to install code into
     * @param installedCode code that has just been installed in the code cache
     */
    void onCodeInstallation(TruffleCompilable compilable, InstalledCode installedCode);

    /**
     * Returns Truffle related method information during host compilation. Do not call this method
     * directly use PartialEvaluator#getMethodInfo instead.
     *
     * TODO GR-44222 as soon as the annotation API is available in libgraal this can be moved to the
     * compiler implementation side.
     */
    PartialEvaluationMethodInfo getPartialEvaluationMethodInfo(ResolvedJavaMethod method);

    /**
     * Returns Truffle related method information during host compilation. Do not call this method
     * directly use TruffleHostEnvironment#getHostMethodInfo instead.
     *
     * TODO GR-44222 as soon as the annotation API is available in libgraal this can be moved to the
     * compiler implementation side.
     *
     * @see #getPartialEvaluationMethodInfo(ResolvedJavaMethod) for guest compilation related
     *      information.
     */
    HostMethodInfo getHostMethodInfo(ResolvedJavaMethod method);

    /**
     * Gets an object describing how a read of {@code field} can be constant folded based on Truffle
     * annotations. Do not call this method directly use PartialEvaluator#getConstantFieldInfo
     * instead.
     *
     * TODO GR-44222 as soon as the annotation API is available in libgraal this can be moved to the
     * compiler implementation side.
     *
     * @return {@code null} if there are no constant folding related Truffle annotations on
     *         {@code field}
     */
    ConstantFieldInfo getConstantFieldInfo(ResolvedJavaField field);

    /**
     * Gets the {@link TruffleCompilable} represented by {@code constant}.
     *
     * @return {@code null} if {@code constant} does not represent a {@link TruffleCompilable} or it
     *         cannot be converted to a {@link TruffleCompilable} in the calling context
     */
    TruffleCompilable asCompilableTruffleAST(JavaConstant constant);

    /**
     * Registers some dependent code on an assumption.
     *
     * As the dependent code may not yet be available, a {@link Consumer} is returned that must be
     * {@linkplain Consumer#accept(Object) notified} when the code becomes available. If there is an
     * error while compiling or installing the code, the returned consumer must be called with a
     * {@code null} argument.
     *
     * If the assumption is already invalid, then {@code null} is returned in which case the caller
     * (e.g., the compiler) must ensure the dependent code is never executed.
     *
     * @param optimizedAssumption compiler constant representing an {@code OptimizedAssumption}
     */
    Consumer<OptimizedAssumptionDependency> registerOptimizedAssumptionDependency(JavaConstant optimizedAssumption);

    /**
     * {@linkplain #formatEvent(int, String, int, String, int, Map, int) Formats} a Truffle event
     * and writes it to the {@linkplain #log(TruffleCompilable, String) log output}.
     *
     * @param compilable the currently compiled AST used as a subject
     * @param depth nesting depth of the event
     * @param event a short description of the event being traced
     * @param properties name/value pairs describing properties relevant to the event
     * @since 20.1.0
     */
    default void logEvent(TruffleCompilable compilable, int depth, String event, Map<String, Object> properties) {
        logEvent(compilable, depth, event, compilable.toString(), properties, null);
    }

    /**
     * {@linkplain #formatEvent(int, String, int, String, int, Map, int) Formats} a Truffle event
     * and writes it to the {@linkplain #log(TruffleCompilable, String) log output}.
     *
     * @param compilable the currently compiled AST
     * @param depth nesting depth of the event
     * @param event a short description of the event being traced
     * @param subject a description of the event's subject
     * @param properties name/value pairs describing properties relevant to the event
     * @param message optional additional message appended to the formatted event
     * @since 20.1.0
     */
    default void logEvent(TruffleCompilable compilable, int depth, String event, String subject, Map<String, Object> properties, String message) {
        String formattedMessage = formatEvent(depth, event, 12, subject, 60, properties, 0);
        if (message != null) {
            formattedMessage = String.format("%s%n%s", formattedMessage, message);
        }
        log(compilable, formattedMessage);
    }

    /**
     * Writes {@code message} followed by a new line to the Truffle logger.
     *
     * @param compilable the currently compiled AST
     * @param message message to log
     */
    default void log(TruffleCompilable compilable, String message) {
        log("engine", compilable, message);
    }

    void log(String loggerId, TruffleCompilable compilable, String message);

    /**
     * Formats a message describing a Truffle event as a single line of text. A representative event
     * trace line is shown below:
     *
     * <pre>
     * opt queued       :anonymous &lt;split-1563da5>                                  |ASTSize      20/   20 |Calls/Thres    7723/    3 |CallsAndLoop/Thres    7723/ 1000 |Inval#              0
     * </pre>
     *
     * @param depth nesting depth of the event (subject column is indented @{code depth * 2})
     * @param event a short description of the event being traced (e.g., "opt done")
     * @param eventWidth the minimum width of the event column
     * @param subject a description of the event's subject (e.g., name of a Truffle AST)
     * @param subjectWidth the minimum width of the subject column
     * @param properties name/value pairs describing properties relevant to the event
     * @param propertyWidth the minimum width of the column for each property
     */
    default String formatEvent(int depth, String event, int eventWidth, String subject, int subjectWidth, Map<String, Object> properties, int propertyWidth) {
        if (depth < 0) {
            throw new IllegalArgumentException("depth is negative: " + depth);
        }
        if (eventWidth < 0) {
            throw new IllegalArgumentException("eventWidth is negative: " + eventWidth);
        }
        if (subjectWidth < 0) {
            throw new IllegalArgumentException("subjectWidth is negative: " + subjectWidth);
        }
        if (propertyWidth < 0) {
            throw new IllegalArgumentException("propertyWidth is negative: " + propertyWidth);
        }
        int subjectIndent = depth * 2;
        StringBuilder sb = new StringBuilder();
        String format = "%-" + eventWidth + "s%" + (1 + subjectIndent) + "s%-" + Math.max(1, subjectWidth - subjectIndent) + "s";
        sb.append(String.format(format, event, "", subject));
        if (properties != null) {
            for (String property : properties.keySet()) {
                Object value = properties.get(property);
                if (value == null) {
                    continue;
                }
                sb.append("|");
                sb.append(property);

                String valueString;
                if (value instanceof Integer) {
                    valueString = String.format("%6d", value);
                } else if (value instanceof Double) {
                    valueString = String.format("%8.2f", value);
                } else {
                    valueString = String.valueOf(value);
                }

                int length = Math.max(1, propertyWidth - property.length());
                sb.append(String.format(" %" + length + "s", valueString));
            }
        }
        return sb.toString();
    }

    /**
     * Looks up a type in this runtime.
     *
     * @param className name of the type to lookup (same format as {@link Class#forName(String)}
     * @return the resolved type
     * @throws NoClassDefFoundError if resolution fails
     */
    default ResolvedJavaType resolveType(MetaAccessProvider metaAccess, String className) {
        return resolveType(metaAccess, className, true);
    }

    /**
     * Looks up a type in this runtime.
     *
     * @param className name of the type to lookup (same format as {@link Class#forName(String)}
     * @param required specifies if {@link NoClassDefFoundError} should be thrown or {@code null}
     *            should be returned if resolution fails
     * @return the resolved type or {@code null} if resolution fails and {@code required == false}
     * @throws NoClassDefFoundError if resolution fails and {@code required == true}
     */
    ResolvedJavaType resolveType(MetaAccessProvider metaAccess, String className, boolean required);

    /**
     * Determines if {@code type} is a value type. Reference comparisons (==) between value type
     * instances have undefined semantics and can either return true or false.
     */
    boolean isValueType(ResolvedJavaType type);

    /**
     * Determines if the exception which happened during the compilation is suppressed and should be
     * silent.
     */
    boolean isSuppressedFailure(TruffleCompilable compilable, Supplier<String> serializedException);
}
