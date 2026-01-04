/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import java.util.Optional;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.java.BytecodeParserOptions;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.debug.SideEffectNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.Phase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.FloatingReadPhase;
import jdk.graal.compiler.phases.common.HighTierLoweringPhase;
import jdk.graal.compiler.phases.common.InsertProxyPhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.phases.tiers.Suites;

public class InsertProxyReProxyTest extends GraalCompilerTest {

    public static int snippet(int limit, Object o) {
        int i = 0;
        Object j = null;
        if (limit == 0) {
            j = o;
        } else {

            while (true) {
                if (i < limit) {
                    GraalDirectives.sideEffect();
                } else {
                    j = GraalDirectives.guardingNonNull(o).getClass();
                    // force a path here that we can later remove to force the guard into the loop
                    if (GraalDirectives.sideEffect(limit) == limit) {
                        continue;
                    }
                    break;
                }
            }
        }
        GraalDirectives.blackhole(j);
        return 0;
    }

    @Test
    public void testReProxy() {
        StructuredGraph graph = parseEager("snippet", StructuredGraph.AllowAssumptions.NO);
        for (Node n : graph.getNodes()) {
            if (n instanceof IntegerEqualsNode ie && ie.getY() instanceof SideEffectNode se) {
                n.replaceAtUsages(LogicConstantNode.contradiction(graph));
                se.replaceAtUsages(ConstantNode.forInt(0, graph));
                FrameState fs = se.stateAfter();
                se.setStateAfter(null);
                fs.safeDelete();
                graph.removeFixed(se);
            }
        }
        CanonicalizerPhase.create().apply(graph, getDefaultHighTierContext());
        // do not re-proxy the proxied pi already.
        new InsertProxyPhase().apply(graph);
        assert graph.verify(true);
        new SchedulePhase(graph.getOptions()).apply(graph, getDefaultHighTierContext());
        Assert.assertEquals(1, graph.getNodes().filter(ProxyNode.class).count());
    }

    static int S;

    public static int snippet2(int limit) {
        int i = 0;
        int result = 0;
        while (true) {
            result = S;
            if (i < limit) {
                GraalDirectives.sideEffect();
            } else {

                if (i == 44) {
                    continue;
                }

                if (GraalDirectives.sideEffect(limit) == 12) {
                    GraalDirectives.sideEffect(1);
                } else if (GraalDirectives.sideEffect(limit) == 123) {
                    GraalDirectives.sideEffect(2);
                } else {
                    GraalDirectives.sideEffect(3);
                }
                if (GraalDirectives.sideEffect(limit) == 125) {
                    GraalDirectives.sideEffect(4);
                } else {
                    GraalDirectives.sideEffect(5);
                }
                break;
            }
        }
        return result;
    }

    @Test
    public void test02() {
        DebugContext debug = getDebugContext();

        OptionValues opt = new OptionValues(getInitialOptions(), BytecodeParserOptions.ParserCreateProxies, false);
        StructuredGraph graph = parseEager("snippet2", StructuredGraph.AllowAssumptions.NO, opt);

        try (DebugContext.Scope _ = debug.scope("ReProxyTest")) {

            // removing all the proxies
            for (LoopExitNode exit : graph.getNodes(LoopExitNode.TYPE)) {
                exit.removeProxies();
            }
            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After removing proxies");

            // clear all states for now, we only care about proxy generation
            graph.clearAllStateAfterForTestingOnly();

            // now find the merge
            MergeNode withThreePred = (MergeNode) graph.getNodes().filter(x -> x instanceof MergeNode && ((MergeNode) x).phiPredecessorCount() == 3).first();
            LoopBeginNode onlyLoopBegin = graph.getNodes(LoopBeginNode.TYPE).first();

            for (LoopExitNode oldLex : onlyLoopBegin.loopExits().snapshot()) {
                FixedWithNextNode pred = (FixedWithNextNode) oldLex.predecessor();
                FixedNode next = oldLex.next();
                oldLex.setNext(null);
                pred.setNext(null);
                pred.setNext(next);
                oldLex.safeDelete();
            }

            for (EndNode end : withThreePred.forwardEnds().snapshot()) {
                LoopExitNode lex = graph.add(new LoopExitNode(onlyLoopBegin));
                graph.addAfterFixed((FixedWithNextNode) end.predecessor(), lex);

            }
            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After adding strange ");

            // the cleanup the graph
            CanonicalizerPhase.create().apply(graph, getDefaultHighTierContext());

            // then add them again
            new InsertProxyPhase().apply(graph);

        } catch (Throwable e) {
            throw GraalError.shouldNotReachHere(e);
        }
    }

    boolean testMemoryGraphProxies;

    @Override
    protected Suites createSuites(OptionValues opts) {
        Suites s = super.createSuites(opts).copy();
        if (testMemoryGraphProxies) {
            var pos = s.getMidTier().findPhase(FloatingReadPhase.class, true);
            pos.add(new Phase() {
                @Override
                public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
                    return ALWAYS_APPLICABLE;
                }

                @Override
                protected void run(StructuredGraph graph) {
                    for (LoopExitNode exit : graph.getNodes(LoopExitNode.TYPE)) {
                        exit.removeProxies();
                    }
                    new InsertProxyPhase().apply(graph);
                }
            });
        }
        return s;
    }

    class TestMemoryGraphProxies implements AutoCloseable {
        TestMemoryGraphProxies() {
            testMemoryGraphProxies = true;
        }

        @Override
        public void close() {
            testMemoryGraphProxies = false;
        }
    }

    public static int snippet3(int limit) {
        int i = 0;
        int result = 0;
        while (true) {
            result = S;
            if (i < limit) {
                GraalDirectives.sideEffect();
                i++;
            } else {

                if (limit == 23) {
                    S = i;
                }
                GraalDirectives.controlFlowAnchor();

                if (i == 44) {
                    continue;
                }
                GraalDirectives.controlFlowAnchor();

                break;
            }
        }

        return result + S/* read S again to get a proxy for the kill */;
    }

    @Test
    @SuppressWarnings("try")
    public void testMemoryGraph01() {
        try (var testMemProxy = new TestMemoryGraphProxies()) {
            OptionValues optionValues = new OptionValues(getInitialOptions(), BytecodeParserOptions.ParserCreateProxies, false, GraalOptions.OptReadElimination, false, GraalOptions.LoopUnswitch,
                            false, GraalOptions.LoopPeeling, false, GraalOptions.PartialEscapeAnalysis, false);
            test(optionValues, "snippet3", 10);
        }
    }

    public static int snippet31(int limit) {
        int i = 0;
        int result = 0;
        while (true) {
            result = S;
            if (i < limit) {
                GraalDirectives.sideEffect();
                i++;
            } else {

                GraalDirectives.controlFlowAnchor();
                if (limit == 23) {
                    S = i;
                }
                GraalDirectives.controlFlowAnchor();

                if (i == 44) {
                    continue;
                } else {
                    GraalDirectives.controlFlowAnchor();
                }
                GraalDirectives.controlFlowAnchor();

                if (G == 12) {
                    L = 1;
                } else if (G == 123) {
                    L = 2;
                } else {
                    L = 3;
                }
                if (K == 125) {
                    L = 4;
                } else {
                    L = 5;
                }

                break;
            }
        }

        return result + S/* read S again to get a proxy for the kill */;
    }

    static int G;
    static int L;
    static int K;

    @Test
    @SuppressWarnings("try")
    public void testMemoryGraph02() {
        DebugContext debug = getDebugContext();

        OptionValues opt = new OptionValues(getInitialOptions(), BytecodeParserOptions.ParserCreateProxies, true);
        StructuredGraph graph = parseEager("snippet31", StructuredGraph.AllowAssumptions.NO, opt);

        try (DebugContext.Scope _ = debug.scope("ReProxyTest")) {

            // clear all states for now, we only care about proxy generation
            graph.clearAllStateAfterForTestingOnly();

            new HighTierLoweringPhase(CanonicalizerPhase.create()).apply(graph, getDefaultHighTierContext());

            // run floating reads
            new FloatingReadPhase(false, true, CanonicalizerPhase.create()).apply(graph, getDefaultMidTierContext());

            // removing all the proxies
            for (LoopExitNode exit : graph.getNodes(LoopExitNode.TYPE)) {
                exit.removeProxies();
            }
            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After removing proxies");

            // clear all states for now, we only care about proxy generation
            graph.clearAllStateAfterForTestingOnly();

            // now find the merge
            MergeNode withThreePred = (MergeNode) graph.getNodes().filter(x -> x instanceof MergeNode && ((MergeNode) x).phiPredecessorCount() == 3).first();
            LoopBeginNode onlyLoopBegin = graph.getNodes(LoopBeginNode.TYPE).first();

            for (LoopExitNode oldLex : onlyLoopBegin.loopExits().snapshot()) {
                if (oldLex.predecessor() instanceof IfNode ifNode) {
                    FixedNode next = oldLex.next();
                    oldLex.setNext(null);

                    if (next instanceof AbstractBeginNode) {
                        oldLex.replaceAtPredecessor(next);
                    } else {
                        BeginNode begin = graph.add(new BeginNode());
                        begin.setNext(next);
                        oldLex.replaceAtPredecessor(begin);
                    }

                    oldLex.safeDelete();
                } else {
                    FixedWithNextNode pred = (FixedWithNextNode) oldLex.predecessor();
                    FixedNode next = oldLex.next();
                    oldLex.setNext(null);
                    pred.setNext(null);
                    pred.setNext(next);
                    oldLex.safeDelete();
                }
            }

            for (EndNode end : withThreePred.forwardEnds().snapshot()) {
                LoopExitNode lex = graph.add(new LoopExitNode(onlyLoopBegin));
                graph.addAfterFixed((FixedWithNextNode) end.predecessor(), lex);

            }
            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After adding strange ");

            // the cleanup the graph
            CanonicalizerPhase.create().apply(graph, getDefaultHighTierContext());

            // then add them again
            new InsertProxyPhase().apply(graph);

        } catch (Throwable e) {
            throw GraalError.shouldNotReachHere(e);
        }
    }
}
