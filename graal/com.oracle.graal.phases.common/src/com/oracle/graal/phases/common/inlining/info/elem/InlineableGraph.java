/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.common.inlining.info.elem;

import com.oracle.graal.api.meta.Constant;
import com.oracle.graal.api.meta.ResolvedJavaMethod;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.graph.NodeInputList;
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.common.CanonicalizerPhase;
import com.oracle.graal.phases.common.DeadCodeEliminationPhase;
import com.oracle.graal.phases.common.inlining.InliningUtil;
import com.oracle.graal.phases.tiers.HighTierContext;

import static com.oracle.graal.compiler.common.GraalOptions.OptCanonicalizer;

public class InlineableGraph implements Inlineable {

    private final StructuredGraph graph;

    public InlineableGraph(final ResolvedJavaMethod method, final Invoke invoke, final HighTierContext context, CanonicalizerPhase canonicalizer) {
        this.graph = buildGraph(method, invoke, context, canonicalizer);
    }

    private static StructuredGraph buildGraph(final ResolvedJavaMethod method, final Invoke invoke, final HighTierContext context, CanonicalizerPhase canonicalizer) {
        final StructuredGraph newGraph;
        final boolean parseBytecodes;

        // TODO (chaeubl): copying the graph is only necessary if it is modified or if it contains
        // any invokes
        StructuredGraph intrinsicGraph = InliningUtil.getIntrinsicGraph(context.getReplacements(), method);
        if (intrinsicGraph != null) {
            newGraph = intrinsicGraph.copy();
            parseBytecodes = false;
        } else {
            StructuredGraph cachedGraph = getCachedGraph(method, context);
            if (cachedGraph != null) {
                newGraph = cachedGraph.copy();
                parseBytecodes = false;
            } else {
                newGraph = new StructuredGraph(method);
                parseBytecodes = true;
            }
        }

        try (Debug.Scope s = Debug.scope("InlineGraph", newGraph)) {
            if (parseBytecodes) {
                parseBytecodes(newGraph, context, canonicalizer);
            }

            boolean callerHasMoreInformationAboutArguments = false;
            NodeInputList<ValueNode> args = invoke.callTarget().arguments();
            for (ParameterNode param : newGraph.getNodes(ParameterNode.class).snapshot()) {
                ValueNode arg = args.get(param.index());
                if (arg.isConstant()) {
                    Constant constant = arg.asConstant();
                    newGraph.replaceFloating(param, ConstantNode.forConstant(constant, context.getMetaAccess(), newGraph));
                    callerHasMoreInformationAboutArguments = true;
                } else {
                    Stamp joinedStamp = param.stamp().join(arg.stamp());
                    if (joinedStamp != null && !joinedStamp.equals(param.stamp())) {
                        param.setStamp(joinedStamp);
                        callerHasMoreInformationAboutArguments = true;
                    }
                }
            }

            if (!callerHasMoreInformationAboutArguments) {
                // TODO (chaeubl): if args are not more concrete, inlining should be avoided
                // in most cases or we could at least use the previous graph size + invoke
                // probability to check the inlining
            }

            if (OptCanonicalizer.getValue()) {
                canonicalizer.apply(newGraph, context);
            }

            return newGraph;
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    private static StructuredGraph getCachedGraph(ResolvedJavaMethod method, HighTierContext context) {
        if (context.getGraphCache() != null) {
            StructuredGraph cachedGraph = context.getGraphCache().get(method);
            if (cachedGraph != null) {
                return cachedGraph;
            }
        }
        return null;
    }

    /**
     * This method builds the IR nodes for <code>newGraph</code> and canonicalizes them. Provided
     * profiling info is mature, the resulting graph is cached.
     */
    private static StructuredGraph parseBytecodes(StructuredGraph newGraph, HighTierContext context, CanonicalizerPhase canonicalizer) {
        final boolean hasMatureProfilingInfo = newGraph.method().getProfilingInfo().isMature();

        if (context.getGraphBuilderSuite() != null) {
            context.getGraphBuilderSuite().apply(newGraph, context);
        }
        assert newGraph.start().next() != null : "graph needs to be populated during PhasePosition.AFTER_PARSING";

        new DeadCodeEliminationPhase().apply(newGraph);

        if (OptCanonicalizer.getValue()) {
            canonicalizer.apply(newGraph, context);
        }

        if (hasMatureProfilingInfo && context.getGraphCache() != null) {
            context.getGraphCache().put(newGraph.method(), newGraph.copy());
        }
        return newGraph;
    }

    @Override
    public int getNodeCount() {
        return graph.getNodeCount();
    }

    @Override
    public Iterable<Invoke> getInvokes() {
        return graph.getInvokes();
    }

    public StructuredGraph getGraph() {
        return graph;
    }
}
