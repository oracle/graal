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

import java.util.*;

import com.oracle.graal.api.meta.Constant;
import com.oracle.graal.api.meta.ResolvedJavaMethod;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.graph.*;
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

    /**
     * @return a (possibly cached) graph. The caller is responsible for cloning before modification.
     */
    private static StructuredGraph getOriginalGraph(final ResolvedJavaMethod method, final HighTierContext context) {
        StructuredGraph intrinsicGraph = InliningUtil.getIntrinsicGraph(context.getReplacements(), method);
        if (intrinsicGraph != null) {
            return intrinsicGraph;
        }
        StructuredGraph cachedGraph = getCachedGraph(method, context);
        if (cachedGraph != null) {
            return cachedGraph;
        }
        return null;
    }

    private static StructuredGraph buildGraph(final ResolvedJavaMethod method, final Invoke invoke, final HighTierContext context, CanonicalizerPhase canonicalizer) {
        StructuredGraph newGraph = getOriginalGraph(method, context);
        if (newGraph == null) {
            newGraph = parseBytecodes(method, context, canonicalizer);
        }
        newGraph = newGraph.copy();

        // TODO (chaeubl): copying the graph is only necessary if it is modified or if it contains
        // any invokes

        try (Debug.Scope s = Debug.scope("InlineGraph", newGraph)) {

            ArrayList<Node> parameterUsages = replaceParamsWithMoreInformativeArguments(invoke, newGraph, context);
            if (parameterUsages != null && OptCanonicalizer.getValue()) {
                assert !parameterUsages.isEmpty() : "The caller didn't have more information about arguments after all";
                canonicalizer.applyIncremental(newGraph, context, parameterUsages);
            } else {
                // TODO (chaeubl): if args are not more concrete, inlining should be avoided
                // in most cases or we could at least use the previous graph size + invoke
                // probability to check the inlining
            }

            return newGraph;
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    private static boolean isArgMoreInformativeThanParam(ValueNode arg, ParameterNode param) {
        if (arg.isConstant()) {
            return true;
        } else {
            Stamp joinedStamp = param.stamp().join(arg.stamp());
            if (joinedStamp != null && !joinedStamp.equals(param.stamp())) {
                return true;
            }
        }
        return false;
    }

    /**
     * This method detects:
     * <ul>
     * <li>
     * constants among the arguments to the <code>invoke</code></li>
     * <li>
     * arguments with more precise type than that declared by the corresponding parameter</li>
     * </ul>
     *
     * <p>
     * The corresponding parameters are updated to reflect the above information. Before doing so,
     * their usages are added to <code>parameterUsages</code> for later incremental
     * canonicalization.
     * </p>
     *
     * @return null if no incremental canonicalization is need, a list of nodes for such
     *         canonicalization otherwise.
     */
    private static ArrayList<Node> replaceParamsWithMoreInformativeArguments(final Invoke invoke, final StructuredGraph newGraph, final HighTierContext context) {
        NodeInputList<ValueNode> args = invoke.callTarget().arguments();
        ArrayList<Node> parameterUsages = null;
        List<ParameterNode> params = newGraph.getNodes(ParameterNode.class).snapshot();
        assert params.size() <= args.size();
        /*
         * param-nodes that aren't used (eg, as a result of canonicalization) don't occur in
         * `params`. Thus, in general, the sizes of `params` and `args` don't always match. Still,
         * it's always possible to pair a param-node with its corresponding arg-node using
         * param.index() as index into `args`.
         */
        for (ParameterNode param : params) {
            if (param.usages().isNotEmpty()) {
                ValueNode arg = args.get(param.index());
                if (arg.isConstant()) {
                    Constant constant = arg.asConstant();
                    parameterUsages = trackParameterUsages(param, parameterUsages);
                    // collect param usages before replacing the param
                    newGraph.replaceFloating(param, ConstantNode.forConstant(constant, context.getMetaAccess(), newGraph));
                    // param-node gone, leaving a gap in the sequence given by param.index()
                } else {
                    Stamp joinedStamp = param.stamp().join(arg.stamp());
                    if (joinedStamp != null && !joinedStamp.equals(param.stamp())) {
                        param.setStamp(joinedStamp);
                        parameterUsages = trackParameterUsages(param, parameterUsages);
                    } else {
                        assert !isArgMoreInformativeThanParam(arg, param);
                    }
                }
            }
        }
        assert (parameterUsages == null) || (!parameterUsages.isEmpty());
        return parameterUsages;
    }

    private static ArrayList<Node> trackParameterUsages(ParameterNode param, ArrayList<Node> parameterUsages) {
        ArrayList<Node> result = (parameterUsages == null) ? new ArrayList<>() : parameterUsages;
        param.usages().snapshotTo(result);
        return result;
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
     * This method builds the IR nodes for the given <code>method</code> and canonicalizes them.
     * Provided profiling info is mature, the resulting graph is cached. The caller is responsible
     * for cloning before modification.</p>
     */
    private static StructuredGraph parseBytecodes(ResolvedJavaMethod method, HighTierContext context, CanonicalizerPhase canonicalizer) {
        StructuredGraph newGraph = new StructuredGraph(method);
        try (Debug.Scope s = Debug.scope("InlineGraph", newGraph)) {
            if (context.getGraphBuilderSuite() != null) {
                context.getGraphBuilderSuite().apply(newGraph, context);
            }
            assert newGraph.start().next() != null : "graph needs to be populated by the GraphBuilderSuite";

            new DeadCodeEliminationPhase().apply(newGraph);

            if (OptCanonicalizer.getValue()) {
                canonicalizer.apply(newGraph, context);
            }

            if (context.getGraphCache() != null) {
                context.getGraphCache().put(newGraph.method(), newGraph);
            }
            return newGraph;
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
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
