/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.common;

import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleCompilationExceptionsAreFatal;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TrufflePerformanceWarningsAreFatal;

import java.util.Map;
import java.util.function.Consumer;

import org.graalvm.compiler.api.runtime.GraalRuntime;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo;
import org.graalvm.compiler.nodes.graphbuilderconf.LoopExplosionPlugin;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
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
     * Gets the singleton runtime instance if it is available other returns {@code null}.
     */
    static TruffleCompilerRuntime getRuntimeIfAvailable() {
        if (TruffleRuntimeInstance.INSTANCE instanceof TruffleCompilerRuntime) {
            return TruffleCompilerRuntimeInstance.INSTANCE;
        }
        return null;
    }

    /**
     * Gets the singleton runtime instance.
     */
    static TruffleCompilerRuntime getRuntime() {
        return TruffleCompilerRuntimeInstance.INSTANCE;
    }

    /**
     * Determines whether an exception during a Truffle compilation should result in calling
     * {@link System#exit(int)}.
     */
    static boolean areTruffleCompilationExceptionsFatal() {
        /*
         * Automatically enable TruffleCompilationExceptionsAreFatal when asserts are enabled but
         * respect TruffleCompilationExceptionsAreFatal if it's been explicitly set.
         */
        boolean truffleCompilationExceptionsAreFatal = TruffleCompilerOptions.getValue(TruffleCompilationExceptionsAreFatal);
        assert TruffleCompilationExceptionsAreFatal.hasBeenSet(TruffleCompilerOptions.getOptions()) || (truffleCompilationExceptionsAreFatal = true) == true;
        return truffleCompilationExceptionsAreFatal || TruffleCompilerOptions.getValue(TrufflePerformanceWarningsAreFatal);
    }

    /**
     * Value returned by {@link TruffleCompilerRuntime#getConstantFieldInfo(ResolvedJavaField)}
     * describing how a field read can be constant folded based on Truffle annotations.
     */
    class ConstantFieldInfo {

        /**
         * Denotes a field is annotated by {@code com.oracle.truffle.api.nodes.Node.Child}.
         */
        public static final ConstantFieldInfo CHILD = new ConstantFieldInfo(-1);

        /**
         * Denotes a field is annotated by {@code com.oracle.truffle.api.nodes.Node.Children}.
         */
        public static final ConstantFieldInfo CHILDREN = new ConstantFieldInfo(-2);

        private final int dimensions;

        /**
         * Determines if this object is {@link #CHILD}.
         */
        public boolean isChild() {
            return dimensions == -1;
        }

        /**
         * Determines if this object is {@link #CHILDREN}.
         */
        public boolean isChildren() {
            return dimensions == -2;
        }

        /**
         * Gets the number of array dimensions to be marked as compilation final. This value is only
         * non-zero for array type fields.
         *
         * @return a value between 0 and the number of declared array dimensions (inclusive)
         */
        public int getDimensions() {
            return Math.max(0, dimensions);
        }

        /**
         * Gets a {@link ConstantFieldInfo} object for a field.
         *
         * @param dimensions the number of array dimensions to be marked as compilation final
         */
        public static ConstantFieldInfo forDimensions(int dimensions) {
            if (dimensions < 0) {
                throw new IllegalArgumentException("Negative dimensions not allowed");
            }
            return new ConstantFieldInfo(dimensions);
        }

        private ConstantFieldInfo(int dimensions) {
            this.dimensions = dimensions;
        }
    }

    /**
     * Gets an object describing how a read of {@code field} can be constant folded based on Truffle
     * annotations.
     *
     * @return {@code null} if there are no constant folding related Truffle annotations on
     *         {@code field}
     */
    ConstantFieldInfo getConstantFieldInfo(ResolvedJavaField field);

    /**
     * Queries how loops in {@code method} with constant number of invocations should be unrolled.
     */
    LoopExplosionPlugin.LoopExplosionKind getLoopExplosionKind(ResolvedJavaMethod method);

    /**
     * Gets the primary {@link TruffleCompiler} instance associated with this runtime, creating it
     * in a thread-safe manner first if necessary.
     */
    TruffleCompiler getTruffleCompiler();

    /**
     * Gets a new {@link TruffleCompiler} instance.
     *
     * @throws UnsupportedOperationException if this runtime does not support creating
     *             {@link TruffleCompiler} instances apart from the one returned by
     *             {@link #getTruffleCompiler()}
     */
    TruffleCompiler newTruffleCompiler();

    /**
     * Gets a plan for inlining in terms of a Truffle AST call graph.
     */
    TruffleInliningPlan createInliningPlan(CompilableTruffleAST compilable);

    CompilableTruffleAST asCompilableTruffleAST(JavaConstant constant);

    GraalRuntime getGraalRuntime();

    /**
     * Gets the compiler constant representing the target of {@code callNode}.
     */
    JavaConstant getCallTargetForCallNode(JavaConstant callNode);

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
     * {@linkplain #formatEvent(String, int, String, int, String, int, Map, int) Formats} a Truffle
     * event and writes it to the {@linkplain #log(String) log output}.
     */
    default void logEvent(int depth, String event, String subject, Map<String, Object> properties) {
        log(formatEvent("[truffle]", depth, event, 16, subject, 60, properties, 20));
    }

    /**
     * Formats a message describing a Truffle event as a single line of text. A representative event
     * trace line is shown below:
     *
     * <pre>
     * [truffle] opt queued       :anonymous <split-1563da5>                                  |ASTSize      20/   20 |Calls/Thres    7723/    3 |CallsAndLoop/Thres    7723/ 1000 |Inval#              0
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
    default String formatEvent(String caption, int depth, String event, int eventWidth, String subject, int subjectWidth, Map<String, Object> properties, int propertyWidth) {
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
        String format = "%s %-" + eventWidth + "s%" + (1 + subjectIndent) + "s%-" + Math.max(1, subjectWidth - subjectIndent) + "s";
        sb.append(String.format(format, caption, event, "", subject));
        if (properties != null) {
            for (String property : properties.keySet()) {
                Object value = properties.get(property);
                if (value == null) {
                    continue;
                }
                sb.append('|');
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
                sb.append(String.format(" %" + length + "s ", valueString));
            }
        }
        return sb.toString();
    }

    /**
     * Writes {@code message} followed by a new line to the Truffle log stream.
     */
    void log(String message);

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
     * Gets the initial option values for this runtime.
     */
    OptionValues getInitialOptions();

    /**
     * Gets an object describing whether and how a method can be inlined based on Truffle
     * directives.
     *
     * @param original candidate for inlining
     * @param duringPartialEvaluation whether the inlining context is partial evaluation
     */
    InlineInfo getInlineInfo(ResolvedJavaMethod original, boolean duringPartialEvaluation);

    /**
     * Determines if {@code type} is a value type. Reference comparisons (==) between value type
     * instances have undefined semantics and can either return true or false.
     */
    boolean isValueType(ResolvedJavaType type);

    /**
     * Gets the Java kind corresponding to a {@code FrameSlotKind.tag} value.
     */
    JavaKind getJavaKindForFrameSlotKind(int frameSlotKindTag);

    /**
     * Gets the {@code FrameSlotKind.tag} corresponding to a {@link JavaKind} value.
     */
    int getFrameSlotKindTagForJavaKind(JavaKind kind);

    /**
     * Gets the number of valid {@code FrameSlotKind.tag} values. The valid values are contiguous
     * from 0 up to but not including the return value.
     */
    int getFrameSlotKindTagsCount();

    /**
     * Determines if {@code method} is annotated by {@code TruffleBoundary}.
     */
    boolean isTruffleBoundary(ResolvedJavaMethod method);
}
