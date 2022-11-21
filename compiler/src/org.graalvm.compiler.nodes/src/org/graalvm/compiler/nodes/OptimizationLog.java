/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

import java.util.function.Function;
import java.util.function.Supplier;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.compiler.debug.CompilationListener;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeSuccessorList;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Unifies counting, logging and dumping in optimization phases. If enabled, collects info about
 * optimizations performed in a single compilation and dumps them to the standard output, JSON
 * files, and/or IGV.
 */
public interface OptimizationLog extends CompilationListener {

    /**
     * Represents a node in the tree of optimizations. The tree of optimizations consists of
     * optimization phases and individual optimizations. Extending {@link Node} allows the tree to
     * be dumped to IGV.
     */
    @NodeInfo(cycles = NodeCycles.CYCLES_IGNORED, size = NodeSize.SIZE_IGNORED)
    abstract class OptimizationTreeNode extends Node {
        public static final NodeClass<OptimizationTreeNode> TYPE = NodeClass.create(OptimizationTreeNode.class);

        protected OptimizationTreeNode(NodeClass<? extends OptimizationTreeNode> c) {
            super(c);
        }

        /**
         * Converts the optimization subtree to an object that can be formatted as JSON.
         *
         * @return a representation of the optimization subtree that can be formatted as JSON
         */
        abstract EconomicMap<String, Object> asJSONMap(Function<ResolvedJavaMethod, String> methodNameFormatter);
    }

    /**
     * Describes a performed optimization. Allows to incrementally add properties and then report
     * the performed optimization.
     */
    interface OptimizationEntry {
        /**
         * Adds a property of the performed optimization to this entry. If the evaluation of the
         * property should be avoided when logging is disabled use
         * {@link #withLazyProperty(String, Supplier)} instead.
         *
         * @param key the name of the property
         * @param value the value of the property
         * @return this
         */
        OptimizationEntry withProperty(String key, Object value);

        /**
         * Adds a supplier-provided property of the performed optimization to this entry. The
         * supplier is evaluated only if logging is enabled. If the evaluation of the property is
         * trivial, use {@link #withProperty(String, Object)} instead.
         *
         * @param key the name of the property
         * @param valueSupplier the supplier of the value
         * @param <V> the value type of the property
         * @return this
         */
        <V> OptimizationEntry withLazyProperty(String key, Supplier<V> valueSupplier);

        /**
         * Increments a {@link org.graalvm.compiler.debug.CounterKey counter},
         * {@link DebugContext#log(String) logs} at {@link DebugContext#DETAILED_LEVEL},
         * {@link DebugContext#dump(int, Object, String) dumps} at
         * {@link DebugContext#DETAILED_LEVEL} and appends to the optimization log if each
         * respective feature is enabled. This method is equivalent to
         * {@link #report(int, Class, String, Node)} at {@link DebugContext#DETAILED_LEVEL}.
         *
         * @param optimizationClass the class that performed the optimization
         * @param eventName the name of the event that occurred
         * @param node the node that is most relevant to the reported optimization
         */
        default void report(Class<?> optimizationClass, String eventName, Node node) {
            report(DebugContext.DETAILED_LEVEL, optimizationClass, eventName, node);
        }

        /**
         * Increments a {@link org.graalvm.compiler.debug.CounterKey counter},
         * {@link DebugContext#log(String) logs} at the specified level,
         * {@link DebugContext#dump(int, Object, String) dumps} at the specified level and appends
         * to the optimization log if each respective feature is enabled.
         *
         * @param logLevel the log level to use for logging and dumping (e.g.
         *            {@link DebugContext#DETAILED_LEVEL})
         * @param optimizationClass the class that performed the optimization
         * @param eventName the name of the event that occurred
         * @param node the node that is most relevant to the reported optimization
         */
        void report(int logLevel, Class<?> optimizationClass, String eventName, Node node);
    }

    /**
     * A dummy optimization entry that does not store nor evaluate its properties. Used in case the
     * optimization log is disabled. The rationale is that it should not do any work if the log is
     * disabled.
     */
    final class OptimizationEntryDummy implements OptimizationEntry {
        private OptimizationEntryDummy() {

        }

        @Override
        public <V> OptimizationEntry withLazyProperty(String key, Supplier<V> valueSupplier) {
            return this;
        }

        @Override
        public OptimizationEntry withProperty(String key, Object value) {
            return this;
        }

        @Override
        public void report(Class<?> optimizationClass, String eventName, Node node) {

        }

        @Override
        public void report(int logLevel, Class<?> optimizationClass, String eventName, Node node) {
            assert logLevel >= MINIMUM_LOG_LEVEL;
        }
    }

    /**
     * The scope of an entered optimization phase that is also a node in the optimization tree,
     * i.e., it has child {@link OptimizationTreeNode nodes}.
     */
    interface OptimizationPhaseScope extends DebugContext.CompilerPhaseScope {
        CharSequence getPhaseName();

        NodeSuccessorList<OptimizationTreeNode> getChildren();
    }

    /**
     * Keeps track of virtualized allocations and materializations during partial escape analysis.
     */
    interface PartialEscapeLog {
        /**
         * Notifies the log that an allocation was virtualized.
         *
         * @param virtualObjectNode the virtualized node
         */
        void allocationRemoved(VirtualObjectNode virtualObjectNode);

        /**
         * Notifies the log that an object was materialized.
         *
         * @param virtualObjectNode the object that was materialized
         */
        void objectMaterialized(VirtualObjectNode virtualObjectNode);
    }

    /**
     * A dummy implementation of the optimization log that does nothing. Used in case when
     * {@link #isAnyLoggingEnabled(DebugContext) no logging is enabled} to decrease runtime
     * overhead.
     */
    final class OptimizationLogDummy implements OptimizationLog {
        private OptimizationLogDummy() {

        }

        /**
         * Returns {@code null} rather than a dummy because it can be assumed the
         * {@link OptimizationLog} is not set as the compilation listener when all logging is
         * disabled.
         */
        @Override
        public OptimizationPhaseScope enterPhase(CharSequence name, int nesting) {
            return null;
        }

        @Override
        public void notifyInlining(ResolvedJavaMethod caller, ResolvedJavaMethod callee, boolean succeeded, CharSequence message, int bci) {

        }

        @Override
        public boolean isOptimizationLogEnabled() {
            return false;
        }

        @Override
        public <V> OptimizationEntry withLazyProperty(String key, Supplier<V> valueSupplier) {
            return OPTIMIZATION_ENTRY_DUMMY;
        }

        @Override
        public OptimizationEntry withProperty(String key, Object value) {
            return OPTIMIZATION_ENTRY_DUMMY;
        }

        /**
         * Performs no reporting, because all logging is disabled.
         */
        @Override
        public void report(int logLevel, Class<?> optimizationClass, String eventName, Node node) {
            assert logLevel >= MINIMUM_LOG_LEVEL;
        }

        /**
         * Returns {@code null} rather than a dummy because the logging effect that uses the
         * {@link PartialEscapeLog} is not added and therefore never applied when the optimization
         * log is disabled.
         */
        @Override
        public PartialEscapeLog getPartialEscapeLog() {
            return null;
        }

        @Override
        public Graph getOptimizationTree() {
            return null;
        }

        @Override
        public OptimizationPhaseScope getCurrentPhase() {
            return null;
        }

        /**
         * Returns a {@link DebugCloseable#VOID_CLOSEABLE} because the optimization log is disabled
         * and there is nothing to do.
         */
        @Override
        public DebugCloseable enterPartialEscapeAnalysis() {
            return DebugCloseable.VOID_CLOSEABLE;
        }

        /**
         * Does not set itself as the compilation listener and returns a scope that does nothing,
         * because the optimization log is disabled.
         *
         * @param methodNameFormatter a function that formats method names (ignored)
         * @return a scope that does nothing
         */
        @Override
        public DebugCloseable listen(Function<ResolvedJavaMethod, String> methodNameFormatter) {
            return DebugCloseable.VOID_CLOSEABLE;
        }
    }

    /**
     * The minimum log level to report optimizations at.
     */
    int MINIMUM_LOG_LEVEL = DebugContext.DETAILED_LEVEL;

    OptimizationEntryDummy OPTIMIZATION_ENTRY_DUMMY = new OptimizationEntryDummy();

    OptimizationLogDummy OPTIMIZATION_LOG_DUMMY = new OptimizationLogDummy();

    /**
     * Returns {@code true} iff {@link DebugOptions#OptimizationLog the optimization log} is enabled
     * according to the provided option values. This option concerns only the structured
     * optimization log; {@link DebugContext#counter(CharSequence) counters},
     * {@link DebugContext#dump(int, Object, String) dumping} and the textual
     * {@link DebugContext#log(String) log} are controlled by their respective options.
     *
     * @param optionValues the option values
     * @return whether {@link DebugOptions#OptimizationLog optimization log} is enabled
     */
    static boolean isOptimizationLogEnabled(OptionValues optionValues) {
        EconomicSet<DebugOptions.OptimizationLogTarget> targets = DebugOptions.OptimizationLog.getValue(optionValues);
        return targets != null && !targets.isEmpty();
    }

    /**
     * Returns {@code true} iff {@link DebugOptions#OptimizationLog the optimization log} is
     * enabled.
     *
     * @return whether {@link DebugOptions#OptimizationLog the optimization log} is enabled
     * @see OptimizationLog#isOptimizationLogEnabled(OptionValues)
     */
    boolean isOptimizationLogEnabled();

    /**
     * Returns {@code true} iff at least one logging feature unified by the optimization log is
     * enabled for this method.
     *
     * @param debugContext the debug context that is tested
     * @return {@code true} iff any logging is enabled
     */
    static boolean isAnyLoggingEnabled(DebugContext debugContext) {
        return debugContext.isLogEnabledForMethod() || debugContext.isDumpEnabledForMethod() ||
                        DebugOptions.Count.getValue(debugContext.getOptions()) != null ||
                        debugContext.hasUnscopedCounters() ||
                        isOptimizationLogEnabled(debugContext.getOptions());
    }

    /**
     * Returns an instance of the optimization for a given graph. The instance is
     * {@link OptimizationLogDummy a dummy} if no logging feature is enabled to minimize runtime
     * overhead. Otherwise, an instance of the optimization log is created and it is bound with the
     * given graph.
     *
     * @param graph the graph that will be bound with the instance (if no logging feature is
     *            enabled)
     * @return an instance of the optimization log
     */
    static OptimizationLog getInstance(StructuredGraph graph) {
        if (isAnyLoggingEnabled(graph.getDebug())) {
            return new OptimizationLogImpl(graph);
        }
        return OPTIMIZATION_LOG_DUMMY;
    }

    /**
     * Builds an {@link OptimizationEntry} with an additional property of the performed
     * optimization. If the evaluation of the property should be avoided when logging is disabled,
     * use {@link #withLazyProperty(String, Supplier)} instead.
     *
     * @param key the name of the property
     * @param value the value of the property
     * @return the created optimization entry with the property
     */
    OptimizationEntry withProperty(String key, Object value);

    /**
     * Builds an {@link OptimizationEntry} with an additional property of the performed optimization
     * provided by a supplier. The supplier is evaluated only if logging is enabled. If the
     * evaluation of the property is trivial, use {@link #withProperty(String, Object)} instead.
     *
     * @param key the name of the property
     * @param valueSupplier the supplier of the value
     * @param <V> the value type of the property
     * @return the created optimization entry with the property
     */
    <V> OptimizationEntry withLazyProperty(String key, Supplier<V> valueSupplier);

    /**
     * Increments a {@link org.graalvm.compiler.debug.CounterKey counter},
     * {@link DebugContext#log(String) logs} at {@link DebugContext#DETAILED_LEVEL},
     * {@link DebugContext#dump(int, Object, String) dumps} at {@link DebugContext#DETAILED_LEVEL}
     * and adds a node to the optimization tree if each respective feature is enabled. This method
     * is equivalent to {@link #report(int, Class, String, Node)} at
     * {@link DebugContext#DETAILED_LEVEL}.
     *
     * If you want to associate a named property with the performed optimization, start by calling
     * {@link #withProperty(String, Object)} or {@link #withLazyProperty(String, Supplier)} and then
     * {{@link OptimizationEntry#report report} the returned {@link OptimizationEntry optimization
     * entry}.
     *
     * @param optimizationClass the class that performed the optimization
     * @param eventName the name of the event that occurred
     * @param node the node that is most relevant to the reported optimization
     */
    default void report(Class<?> optimizationClass, String eventName, Node node) {
        report(DebugContext.DETAILED_LEVEL, optimizationClass, eventName, node);
    }

    /**
     * Increments a {@link org.graalvm.compiler.debug.CounterKey counter},
     * {@link DebugContext#log(String) logs} at the specified level,
     * {@link DebugContext#dump(int, Object, String) dumps} at the specified level and adds a node
     * to the optimization tree if each respective feature is enabled.
     *
     * If you want to associate a named property with the performed optimization, start by calling
     * {@link #withProperty(String, Object)} or {@link #withLazyProperty(String, Supplier)} and then
     * {@link OptimizationEntry#report report} the returned {@link OptimizationEntry optimization
     * entry}.
     *
     * @param logLevel the log level to use for logging and dumping (e.g.
     *            {@link DebugContext#DETAILED_LEVEL})
     * @param optimizationClass the class that performed the optimization
     * @param eventName the name of the event that occurred
     * @param node the node that is most relevant to the reported optimization
     */
    void report(int logLevel, Class<?> optimizationClass, String eventName, Node node);

    /**
     * Gets the log that keeps track of virtualized allocations during partial escape analysis. Must
     * be called after {@link #enterPartialEscapeAnalysis()} and before the {@link DebugCloseable}
     * is closed.
     *
     * @return the log that keeps track of virtualized allocations during partial escape analysis
     */
    PartialEscapeLog getPartialEscapeLog();

    /**
     * Gets the tree of optimizations.
     *
     * @see OptimizationTreeNode
     */
    Graph getOptimizationTree();

    @Override
    OptimizationPhaseScope enterPhase(CharSequence name, int nesting);

    /**
     * Gets the scope of the most recently opened phase (from unclosed phases) or {@code null} if
     * the optimization log is not enabled.
     *
     * @return the scope of the most recently opened phase (from unclosed phases) or {@code null}
     */
    OptimizationPhaseScope getCurrentPhase();

    /**
     * Notifies the log that partial escape analysis will be entered and returns a
     * {@link DebugCloseable} that should be closed after the analysis. If the optimization log is
     * enabled, it prepares an object that keeps track of virtualized allocations. After closing,
     * virtualized allocations are reported.
     */
    DebugCloseable enterPartialEscapeAnalysis();

    /**
     * Opens a {@link DebugCloseable} and sets itself as the compilation listener, if the
     * optimization log is enabled. When the closable is closed, the compilation listener is reset
     * to {@code null} and the optimization tree is printed according to the
     * {@link DebugOptions#OptimizationLog OptimizationLog} option.
     *
     * @param methodNameFormatter a function that formats method names
     * @return a closable in whose lifespan the optimization log is set as the compilation listener
     */
    DebugCloseable listen(Function<ResolvedJavaMethod, String> methodNameFormatter);
}
