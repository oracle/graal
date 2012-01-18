/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.phases;

import java.util.*;

import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.phases.PhasePlan.PhasePosition;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.compiler.util.InliningUtil.InlineInfo;
import com.oracle.max.graal.compiler.util.InliningUtil.InliningCallback;
import com.oracle.max.graal.cri.*;
import com.oracle.max.graal.debug.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;


public class InliningPhase extends Phase implements InliningCallback {
    /*
     * - Detect method which only call another method with some parameters set to constants: void foo(a) -> void foo(a, b) -> void foo(a, b, c) ...
     *   These should not be taken into account when determining inlining depth.
     * - honor the result of overrideInliningDecision(0, caller, invoke.bci, method, true);
     */

    private final CiTarget target;
    private final GraalRuntime runtime;

    private final Collection<Invoke> hints;

    private final PriorityQueue<InlineInfo> inlineCandidates = new PriorityQueue<>();
    private CiAssumptions assumptions;

    private final PhasePlan plan;

    // Metrics
    private static final DebugMetric metricInliningPerformed = Debug.metric("InliningPerformed");
    private static final DebugMetric metricInliningConsidered = Debug.metric("InliningConsidered");

    public InliningPhase(CiTarget target, GraalRuntime runtime, Collection<Invoke> hints, CiAssumptions assumptions, PhasePlan plan) {
        this.target = target;
        this.runtime = runtime;
        this.hints = hints;
        this.assumptions = assumptions;
        this.plan = plan;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void run(StructuredGraph graph) {
        graph.createNodeMap();

        if (hints != null) {
            scanInvokes((Iterable<? extends Node>) Util.uncheckedCast(this.hints), 0, graph);
        } else {
            scanInvokes(graph.getNodes(InvokeNode.class), 0, graph);
            scanInvokes(graph.getNodes(InvokeWithExceptionNode.class), 0, graph);
        }

        while (!inlineCandidates.isEmpty() && graph.getNodeCount() < GraalOptions.MaximumDesiredSize) {
            InlineInfo info = inlineCandidates.remove();
            double penalty = Math.pow(GraalOptions.InliningSizePenaltyExp, graph.getNodeCount() / (double) GraalOptions.MaximumDesiredSize) / GraalOptions.InliningSizePenaltyExp;
            if (info.weight > GraalOptions.MaximumInlineWeight / (1 + penalty * GraalOptions.InliningSizePenalty)) {
                if (GraalOptions.TraceInlining) {
                    TTY.println("not inlining (cut off by weight):");
                    while (info != null) {
                        TTY.println("    %f %s", info.weight, info);
                        info = inlineCandidates.poll();
                    }
                }
                return;
            }
            Iterable<Node> newNodes = null;
            if (info.invoke.node().isAlive()) {
                try {
                    info.inline(graph, runtime, this);
                    if (GraalOptions.TraceInlining) {
                        TTY.println("inlining %f: %s", info.weight, info);
                    }
                    Debug.dump(graph, "after inlining %s", info);
                    // get the new nodes here, the canonicalizer phase will reset the mark
                    newNodes = graph.getNewNodes();
                    if (GraalOptions.OptCanonicalizer) {
                        new CanonicalizerPhase(target, runtime, true, assumptions).apply(graph);
                    }
                    if (GraalOptions.Intrinsify) {
                        new IntrinsificationPhase(runtime).apply(graph);
                    }
                    metricInliningPerformed.increment();
                } catch (CiBailout bailout) {
                    // TODO determine if we should really bail out of the whole compilation.
                    throw bailout;
                } catch (AssertionError e) {
                    throw new GraalInternalError(e).addContext(info.toString());
                } catch (RuntimeException e) {
                    throw new GraalInternalError(e).addContext(info.toString());
                } catch (GraalInternalError e) {
                    throw e.addContext(info.toString());
                }
            }
            if (newNodes != null && info.level <= GraalOptions.MaximumInlineLevel) {
                scanInvokes(newNodes, info.level + 1, graph);
            }
        }
    }

    private void scanInvokes(Iterable<? extends Node> newNodes, int level, StructuredGraph graph) {
        graph.mark();
        for (Node node : newNodes) {
            if (node != null) {
                if (node instanceof Invoke) {
                    Invoke invoke = (Invoke) node;
                    scanInvoke(invoke, level);
                }
                for (Node usage : node.usages().snapshot()) {
                    if (usage instanceof Invoke) {
                        Invoke invoke = (Invoke) usage;
                        scanInvoke(invoke, level);
                    }
                }
            }
        }
    }

    private void scanInvoke(Invoke invoke, int level) {
        InlineInfo info = InliningUtil.getInlineInfo(invoke, level, runtime, assumptions, this);
        if (info != null) {
            metricInliningConsidered.increment();
            inlineCandidates.add(info);
        }
    }

    public static final Map<RiMethod, Integer> parsedMethods = new HashMap<>();

    @Override
    public StructuredGraph buildGraph(RiResolvedMethod method) {
        StructuredGraph newGraph = new StructuredGraph(method);

        if (plan != null) {
            plan.runPhases(PhasePosition.AFTER_PARSING, newGraph);
        }

        if (GraalOptions.ProbabilityAnalysis) {
            new DeadCodeEliminationPhase().apply(newGraph);
            new ComputeProbabilityPhase().apply(newGraph);
        }
        new CanonicalizerPhase(target, runtime, assumptions).apply(newGraph);
        return newGraph;
    }

    @Override
    public double inliningWeight(RiResolvedMethod caller, RiResolvedMethod method, Invoke invoke) {
        double ratio;
        if (hints != null && hints.contains(invoke)) {
            ratio = 1000000;
        } else {
            if (GraalOptions.ProbabilityAnalysis) {
                ratio = invoke.node().probability();
            } else {
                RiTypeProfile profile = caller.typeProfile(invoke.bci());
                if (profile != null && profile.count > 0) {
                    RiResolvedMethod parent = invoke.stateAfter().method();
                    ratio = profile.count / (float) parent.invocationCount();
                } else {
                    ratio = 1;
                }
            }
        }

        final double normalSize;
        // TODO(ls) get rid of this magic, it's here to emulate the old behavior for the time being
        if (ratio < 0.01) {
            ratio = 0.01;
        }
        if (ratio < 0.5) {
            normalSize = 10 * ratio / 0.5;
        } else if (ratio < 2) {
            normalSize = 10 + (35 - 10) * (ratio - 0.5) / 1.5;
        } else if (ratio < 20) {
            normalSize = 35;
        } else if (ratio < 40) {
            normalSize = 35 + (350 - 35) * (ratio - 20) / 20;
        } else {
            normalSize = 350;
        }

        int count;
        if (GraalOptions.ParseBeforeInlining) {
            if (!parsedMethods.containsKey(method)) {
                StructuredGraph newGraph = new StructuredGraph(method);
                if (plan != null) {
                    plan.runPhases(PhasePosition.AFTER_PARSING, newGraph);
                }
                new CanonicalizerPhase(target, runtime, assumptions).apply(newGraph);
                count = graphComplexity(newGraph);
                parsedMethods.put(method, count);
            } else {
                count = parsedMethods.get(method);
            }
        } else {
            count = method.codeSize();
        }

        return count / normalSize;
    }


    public static int graphComplexity(StructuredGraph graph) {
        int result = 0;
        for (Node node : graph.getNodes()) {
            if (node instanceof ConstantNode || node instanceof LocalNode || node instanceof BeginNode || node instanceof ReturnNode || node instanceof UnwindNode) {
                result += 0;
            } else if (node instanceof PhiNode) {
                result += 5;
            } else if (node instanceof MergeNode || node instanceof Invoke || node instanceof LoopEndNode || node instanceof EndNode) {
                result += 0;
            } else if (node instanceof ControlSplitNode) {
                result += ((ControlSplitNode) node).blockSuccessorCount();
            } else {
                result += 1;
            }
        }
        return Math.max(1, result);
    }


    @Override
    public void recordConcreteMethodAssumption(RiResolvedMethod method, RiResolvedType context, RiResolvedMethod impl) {
        assumptions.recordConcreteMethod(method, context, impl);
    }

}
