/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.graphbuilderconf;

import java.util.*;

import jdk.internal.jvmci.meta.*;

import com.oracle.graal.compiler.common.*;
import com.oracle.graal.nodes.*;

public class GraphBuilderConfiguration {

    public static class Plugins {
        private final InvocationPlugins invocationPlugins;
        private NodePlugin[] nodePlugins;
        private ParameterPlugin[] parameterPlugins;
        private InlineInvokePlugin[] inlineInvokePlugins;
        private LoopExplosionPlugin loopExplosionPlugin;

        /**
         * Creates a copy of a given set of plugins. The {@link InvocationPlugins} in
         * {@code copyFrom} become the {@linkplain InvocationPlugins#getParent() default}
         * {@linkplain #getInvocationPlugins() invocation plugins} in this object.
         */
        public Plugins(Plugins copyFrom) {
            this.invocationPlugins = new InvocationPlugins(copyFrom.invocationPlugins);
            this.nodePlugins = copyFrom.nodePlugins;
            this.parameterPlugins = copyFrom.parameterPlugins;
            this.inlineInvokePlugins = copyFrom.inlineInvokePlugins;
            this.loopExplosionPlugin = copyFrom.loopExplosionPlugin;
        }

        /**
         * Creates a new set of plugins.
         *
         * @param invocationPlugins the {@linkplain #getInvocationPlugins() invocation plugins} in
         *            this object
         */
        public Plugins(InvocationPlugins invocationPlugins) {
            this.invocationPlugins = invocationPlugins;
            this.nodePlugins = new NodePlugin[0];
            this.parameterPlugins = new ParameterPlugin[0];
            this.inlineInvokePlugins = new InlineInvokePlugin[0];
        }

        public InvocationPlugins getInvocationPlugins() {
            return invocationPlugins;
        }

        public NodePlugin[] getNodePlugins() {
            return nodePlugins;
        }

        public void appendNodePlugin(NodePlugin plugin) {
            nodePlugins = Arrays.copyOf(nodePlugins, nodePlugins.length + 1);
            nodePlugins[nodePlugins.length - 1] = plugin;
        }

        public void prependNodePlugin(NodePlugin plugin) {
            NodePlugin[] newPlugins = new NodePlugin[nodePlugins.length + 1];
            System.arraycopy(nodePlugins, 0, newPlugins, 1, nodePlugins.length);
            newPlugins[0] = plugin;
            nodePlugins = newPlugins;
        }

        public void clearNodePlugin() {
            nodePlugins = new NodePlugin[0];
        }

        public ParameterPlugin[] getParameterPlugins() {
            return parameterPlugins;
        }

        public void appendParameterPlugin(ParameterPlugin plugin) {
            parameterPlugins = Arrays.copyOf(parameterPlugins, parameterPlugins.length + 1);
            parameterPlugins[parameterPlugins.length - 1] = plugin;
        }

        public void prependParameterPlugin(ParameterPlugin plugin) {
            ParameterPlugin[] newPlugins = new ParameterPlugin[parameterPlugins.length + 1];
            System.arraycopy(parameterPlugins, 0, newPlugins, 1, parameterPlugins.length);
            newPlugins[0] = plugin;
            parameterPlugins = newPlugins;
        }

        public void clearParameterPlugin() {
            parameterPlugins = new ParameterPlugin[0];
        }

        public InlineInvokePlugin[] getInlineInvokePlugins() {
            return inlineInvokePlugins;
        }

        public void appendInlineInvokePlugin(InlineInvokePlugin plugin) {
            inlineInvokePlugins = Arrays.copyOf(inlineInvokePlugins, inlineInvokePlugins.length + 1);
            inlineInvokePlugins[inlineInvokePlugins.length - 1] = plugin;
        }

        public void prependInlineInvokePlugin(InlineInvokePlugin plugin) {
            InlineInvokePlugin[] newPlugins = new InlineInvokePlugin[inlineInvokePlugins.length + 1];
            System.arraycopy(inlineInvokePlugins, 0, newPlugins, 1, inlineInvokePlugins.length);
            newPlugins[0] = plugin;
            inlineInvokePlugins = newPlugins;
        }

        public void clearInlineInvokePlugins() {
            inlineInvokePlugins = new InlineInvokePlugin[0];
        }

        public LoopExplosionPlugin getLoopExplosionPlugin() {
            return loopExplosionPlugin;
        }

        public void setLoopExplosionPlugin(LoopExplosionPlugin plugin) {
            this.loopExplosionPlugin = plugin;
        }
    }

    private static final ResolvedJavaType[] EMPTY = new ResolvedJavaType[]{};

    private final boolean eagerResolving;
    private final boolean omitAllExceptionEdges;
    private final boolean omitAssertions;
    private final ResolvedJavaType[] skippedExceptionTypes;
    private final DebugInfoMode debugInfoMode;
    private final boolean clearNonLiveLocals;
    private boolean useProfiling;
    private final Plugins plugins;

    public static enum DebugInfoMode {
        SafePointsOnly,
        /**
         * This mode inserts {@link SimpleInfopointNode}s in places where no safepoints would be
         * inserted: inlining boundaries, and line number switches.
         * <p>
         * In this mode the infopoint only have a location (method and bytecode index) and no
         * values.
         * <p>
         * This is useful to have better program counter to bci mapping and has no influence on the
         * generated code. However it can increase the amount of metadata and does not allow access
         * to accessing values at runtime.
         */
        Simple,
        /**
         * In this mode, {@link FullInfopointNode}s are generated in the same locations as in
         * {@link #Simple} mode but the infopoints have access to the runtime values.
         * <p>
         * This is relevant when code is to be generated for native, machine-code level debugging
         * but can have a limit the amount of optimization applied to the code.
         */
        Full,
    }

    protected GraphBuilderConfiguration(boolean eagerResolving, boolean omitAllExceptionEdges, boolean omitAssertions, DebugInfoMode debugInfoMode, ResolvedJavaType[] skippedExceptionTypes,
                    boolean clearNonLiveLocals, Plugins plugins) {
        this.eagerResolving = eagerResolving;
        this.omitAllExceptionEdges = omitAllExceptionEdges;
        this.omitAssertions = omitAssertions;
        this.debugInfoMode = debugInfoMode;
        this.skippedExceptionTypes = skippedExceptionTypes;
        this.clearNonLiveLocals = clearNonLiveLocals;
        this.useProfiling = true;
        this.plugins = plugins;
    }

    /**
     * Creates a copy of this configuration with all its plugins. The {@link InvocationPlugins} in
     * this configuration become the {@linkplain InvocationPlugins#getParent() parent} of the
     * {@link InvocationPlugins} in the copy.
     */
    public GraphBuilderConfiguration copy() {
        Plugins newPlugins = new Plugins(plugins);
        GraphBuilderConfiguration result = new GraphBuilderConfiguration(eagerResolving, omitAllExceptionEdges, omitAssertions, debugInfoMode, skippedExceptionTypes, clearNonLiveLocals, newPlugins);
        result.useProfiling = useProfiling;
        return result;
    }

    public boolean getUseProfiling() {
        return useProfiling;
    }

    public void setUseProfiling(boolean b) {
        this.useProfiling = b;
    }

    public GraphBuilderConfiguration withSkippedExceptionTypes(ResolvedJavaType[] newSkippedExceptionTypes) {
        return new GraphBuilderConfiguration(eagerResolving, omitAllExceptionEdges, omitAssertions, debugInfoMode, newSkippedExceptionTypes, clearNonLiveLocals, plugins);
    }

    public GraphBuilderConfiguration withOmitAllExceptionEdges(boolean newOmitAllExceptionEdges) {
        return new GraphBuilderConfiguration(eagerResolving, newOmitAllExceptionEdges, omitAssertions, debugInfoMode, skippedExceptionTypes, clearNonLiveLocals, plugins);
    }

    public GraphBuilderConfiguration withOmitAssertions(boolean newOmitAssertions) {
        return new GraphBuilderConfiguration(eagerResolving, omitAllExceptionEdges, newOmitAssertions, debugInfoMode, skippedExceptionTypes, clearNonLiveLocals, plugins);
    }

    public GraphBuilderConfiguration withDebugInfoMode(DebugInfoMode newDebugInfoMode) {
        ResolvedJavaType[] newSkippedExceptionTypes = skippedExceptionTypes == EMPTY ? EMPTY : Arrays.copyOf(skippedExceptionTypes, skippedExceptionTypes.length);
        return new GraphBuilderConfiguration(eagerResolving, omitAllExceptionEdges, omitAssertions, newDebugInfoMode, newSkippedExceptionTypes, clearNonLiveLocals, plugins);
    }

    public GraphBuilderConfiguration withClearNonLiveLocals(boolean newClearNonLiveLocals) {
        return new GraphBuilderConfiguration(eagerResolving, omitAllExceptionEdges, omitAssertions, debugInfoMode, skippedExceptionTypes, newClearNonLiveLocals, plugins);
    }

    public ResolvedJavaType[] getSkippedExceptionTypes() {
        return skippedExceptionTypes;
    }

    public boolean eagerResolving() {
        return eagerResolving;
    }

    public boolean omitAllExceptionEdges() {
        return omitAllExceptionEdges;
    }

    public boolean omitAssertions() {
        return omitAssertions;
    }

    public boolean insertNonSafepointDebugInfo() {
        return debugInfoMode.ordinal() >= DebugInfoMode.Simple.ordinal();
    }

    public boolean insertFullDebugInfo() {
        return debugInfoMode.ordinal() >= DebugInfoMode.Full.ordinal();
    }

    public boolean insertSimpleDebugInfo() {
        return debugInfoMode == DebugInfoMode.Simple;
    }

    public boolean clearNonLiveLocals() {
        return clearNonLiveLocals;
    }

    public static GraphBuilderConfiguration getDefault(Plugins plugins) {
        return new GraphBuilderConfiguration(false, false, false, DebugInfoMode.SafePointsOnly, EMPTY, GraalOptions.OptClearNonLiveLocals.getValue(), plugins);
    }

    public static GraphBuilderConfiguration getInfopointDefault(Plugins plugins) {
        return new GraphBuilderConfiguration(true, false, false, DebugInfoMode.Simple, EMPTY, GraalOptions.OptClearNonLiveLocals.getValue(), plugins);
    }

    public static GraphBuilderConfiguration getEagerDefault(Plugins plugins) {
        return new GraphBuilderConfiguration(true, false, false, DebugInfoMode.SafePointsOnly, EMPTY, GraalOptions.OptClearNonLiveLocals.getValue(), plugins);
    }

    public static GraphBuilderConfiguration getInfopointEagerDefault(Plugins plugins) {
        return new GraphBuilderConfiguration(true, false, false, DebugInfoMode.Simple, EMPTY, GraalOptions.OptClearNonLiveLocals.getValue(), plugins);
    }

    public static GraphBuilderConfiguration getSnippetDefault(Plugins plugins) {
        return new GraphBuilderConfiguration(true, true, false, DebugInfoMode.SafePointsOnly, EMPTY, GraalOptions.OptClearNonLiveLocals.getValue(), plugins);
    }

    public static GraphBuilderConfiguration getFullDebugDefault(Plugins plugins) {
        return new GraphBuilderConfiguration(true, false, false, DebugInfoMode.Full, EMPTY, GraalOptions.OptClearNonLiveLocals.getValue(), plugins);
    }

    /**
     * Returns {@code true} if it is an error for a class/field/method resolution to fail. The
     * default is the same result as returned by {@link #eagerResolving()}. However, it may be
     * overridden to allow failure even when {@link #eagerResolving} is {@code true}.
     */
    public boolean unresolvedIsError() {
        return eagerResolving;
    }

    public Plugins getPlugins() {
        return plugins;
    }
}
