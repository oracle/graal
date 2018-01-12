/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.util.Map;
import java.util.function.Consumer;

import org.graalvm.compiler.api.runtime.GraalRuntime;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo;
import org.graalvm.compiler.nodes.graphbuilderconf.LoopExplosionPlugin;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.InstalledCode;
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

    boolean enableInfopoints();

    GraalRuntime getGraalRuntime();

    /**
     * Gets the compiler constant representing the target of {@code callNode}.
     */
    JavaConstant getCallTargetForCallNode(JavaConstant callNode);

    Consumer<InstalledCode> registerInstalledCodeEntryForAssumption(JavaConstant optimizedAssumptionConstant);

    /**
     * Formats a message and some extra properties into a single line of text and writes it to the
     * Truffle log output stream. A representative trace line is shown below:
     *
     * <pre>
     * [truffle] opt queued       :anonymous <split-1563da5>                                  |ASTSize      20/   20 |Calls/Thres    7723/    3 |CallsAndLoop/Thres    7723/ 1000 |Inval#              0
     * </pre>
     *
     * @param indent amount of indentation to prepend to the line ({@code indent * 2} space
     *            characters)
     * @param msg a short description of the event being traced (e.g., "opt done")
     * @param details a more detailed description of the event (e.g., name of a Truffle AST)
     * @param properties name/value pairs describing properties relevant to the event
     */
    default void log(int indent, String msg, String details, Map<String, Object> properties) {
        int spaceIndent = indent * 2;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[truffle] %-16s ", msg));
        for (int i = 0; i < spaceIndent; i++) {
            sb.append(' ');
        }
        sb.append(String.format("%-" + (60 - spaceIndent) + "s", details));
        if (properties != null) {
            for (String property : properties.keySet()) {
                Object value = properties.get(property);
                if (value == null) {
                    continue;
                }
                sb.append('|');
                sb.append(property);

                StringBuilder propertyBuilder = new StringBuilder();
                if (value instanceof Integer) {
                    propertyBuilder.append(String.format("%6d", value));
                } else if (value instanceof Double) {
                    propertyBuilder.append(String.format("%8.2f", value));
                } else {
                    propertyBuilder.append(value);
                }

                int length = Math.max(1, 20 - property.length());
                sb.append(String.format(" %" + length + "s ", propertyBuilder.toString()));
            }
        }
        log(sb.toString());
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
     */
    InlineInfo getInlineInfo(ResolvedJavaMethod original);

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
