/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.meta;

import com.oracle.jvmci.meta.MetaAccessProvider;
import com.oracle.jvmci.meta.ResolvedJavaType;
import com.oracle.jvmci.meta.Kind;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.hotspot.phases.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.replacements.StandardGraphBuilderPlugins.BoxPlugin;
import com.oracle.graal.replacements.nodes.*;
import com.oracle.jvmci.hotspot.*;

/**
 * Extension of {@link InvocationPlugins} that disables plugins based on runtime configuration.
 */
final class HotSpotInvocationPlugins extends InvocationPlugins {
    final HotSpotVMConfig config;

    public HotSpotInvocationPlugins(HotSpotVMConfig config, MetaAccessProvider metaAccess) {
        super(metaAccess);
        this.config = config;
    }

    @Override
    public void register(InvocationPlugin plugin, Class<?> declaringClass, String name, Class<?>... argumentTypes) {
        if (!config.usePopCountInstruction) {
            if (name.equals("bitCount")) {
                assert declaringClass.equals(Integer.class) || declaringClass.equals(Long.class);
                return;
            }
        }
        if (config.useHeapProfiler) {
            if (plugin instanceof BoxPlugin) {
                // The heap profiler wants to see all allocations related to boxing
                return;
            }
        }

        super.register(plugin, declaringClass, name, argumentTypes);
    }

    @Override
    public void checkNewNodes(GraphBuilderContext b, InvocationPlugin plugin, NodeIterable<Node> newNodes) {
        for (Node node : newNodes) {
            if (node instanceof MacroNode) {
                // MacroNode based plugins can only be used for inlining since they
                // require a valid bci should they need to replace themselves with
                // an InvokeNode during lowering.
                assert plugin.inlineOnly() : String.format("plugin that creates a %s (%s) must return true for inlineOnly(): %s", MacroNode.class.getSimpleName(), node, plugin);
            }
        }
        if (GraalOptions.ImmutableCode.getValue()) {
            for (Node node : newNodes) {
                if (node.hasUsages() && node instanceof ConstantNode) {
                    ConstantNode c = (ConstantNode) node;
                    if (c.getKind() == Kind.Object && AheadOfTimeVerificationPhase.isIllegalObjectConstant(c)) {
                        if (isClass(c)) {
                            // This will be handled later by LoadJavaMirrorWithKlassPhase
                        } else {
                            // Tolerate uses in unused FrameStates
                            if (node.usages().filter((n) -> !(n instanceof FrameState) || n.hasUsages()).isNotEmpty()) {
                                throw new AssertionError("illegal constant node in AOT: " + node);
                            }
                        }
                    }
                }
            }
        }
        super.checkNewNodes(b, plugin, newNodes);
    }

    private static boolean isClass(ConstantNode node) {
        ResolvedJavaType typeOrNull = StampTool.typeOrNull(node);
        return typeOrNull != null && "Ljava/lang/Class;".equals(typeOrNull.getName());
    }
}
