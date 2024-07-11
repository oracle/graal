/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import java.util.Optional;

import jdk.graal.compiler.debug.DebugOptions;
import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.DominatorBasedGlobalValueNumberingPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.virtual.phases.ea.FinalPartialEscapePhase;

/**
 * Test to verify that loop optimizations and other transformations do not rewrite the safepoint
 * state (must safepoint or not) of a loop.
 */
public class LoopSafepointStateVerificationTest extends GraalCompilerTest {

    public static void snippet01() {
        for (int i = 0; i < 100; i++) {
            GraalDirectives.sideEffect();
        }
    }

    OptionValues testOptions() {
        OptionValues opt = new OptionValues(getInitialOptions(), Graph.Options.VerifyGraalGraphEdges, true);
        return opt;
    }

    @Test
    @SuppressWarnings("try")
    public void test01() {
        try (AutoCloseable c = new TTY.Filter()) {
            // Do not capture graphs for expected compilation failures.
            OptionValues opt = new OptionValues(testOptions(), GraalOptions.FullUnroll, false, DebugOptions.DumpOnError, false);
            test(opt, "snippet01");
            Assert.fail("Should have detected that the phase in this class does not retain the mustNotSafepoint flag of a loop begin");
        } catch (Throwable t) {
            assert t.getMessage().contains("previously the loop had canHaveSafepoints=false but now it has canHaveSafepoints=true");
        }
    }

    @Override
    protected Suites createSuites(OptionValues opts) {
        Suites s = super.createSuites(opts).copy();
        var pos = s.getHighTier().findPhase(DominatorBasedGlobalValueNumberingPhase.class, true);
        // first a phase that disables the safepoints
        pos.add(new BasePhase<HighTierContext>() {

            @Override
            public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
                return ALWAYS_APPLICABLE;
            }

            @Override
            protected void run(StructuredGraph graph, HighTierContext context) {
                for (LoopBeginNode lb : graph.getNodes(LoopBeginNode.TYPE)) {
                    lb.disableSafepoint();
                    lb.disableGuestSafepoint();
                }
            }

        });
        // then a phase that replaces the loop with a new loop begin node and a loop end that can
        // safepoint
        pos = s.getHighTier().findPhase(FinalPartialEscapePhase.class, true);
        pos.add(new BasePhase<HighTierContext>() {

            @Override
            public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
                return ALWAYS_APPLICABLE;
            }

            @Override
            protected void run(StructuredGraph graph, HighTierContext context) {
                var ld = context.getLoopsDataProvider().getLoopsData(graph);
                ld.detectCountedLoops();
                Loop lex = ld.countedLoops().get(0);

                LoopBeginNode lb = new LoopBeginNode();
                lb = graph.add(lb);

                /*
                 * Massage this loop: give it a new header and new loop ends: this mimics a wrong
                 * optimization that replaces a loop with something manually stitched together with
                 * different canSafepoint values.
                 */
                for (LoopEndNode len : lex.loopBegin().loopEnds().snapshot()) {
                    LoopEndNode lenCopy = new LoopEndNode(lb);
                    FixedWithNextNode fwn = (FixedWithNextNode) len.predecessor();
                    fwn.setNext(null);
                    len.safeDelete();
                    fwn.setNext(graph.add(lenCopy));

                }

                // replace the loop begin node
                for (PhiNode phi : lex.loopBegin().phis().snapshot()) {
                    phi.setMerge(lb);
                }
                lb.setStateAfter(lex.loopBegin().stateAfter());

                LoopBeginNode oldLoopBegin = lex.loopBegin();
                EndNode fwd = oldLoopBegin.forwardEnd();

                FixedNode next = oldLoopBegin.next();
                oldLoopBegin.setNext(null);
                lb.setNext(next);

                EndNode fwdEnd = graph.add(new EndNode());
                lb.addForwardEnd(fwdEnd);

                FixedWithNextNode fwn = (FixedWithNextNode) fwd.predecessor();
                fwn.setNext(null);
                GraphUtil.killCFG(fwd);
                fwn.setNext(fwdEnd);

            }

        });
        return s;
    }

    public static void snippet02() {
        for (int i9 = 0; i9 < 100; i9++) {
            GraalDirectives.sideEffect();
            for (int i8 = 0; i8 < 100; i8++) {
                GraalDirectives.sideEffect();
            }
            for (int i7 = 0; i7 < 100; i7++) {
                GraalDirectives.sideEffect();
            }
            for (int i6 = 0; i6 < 100; i6++) {
                GraalDirectives.sideEffect();
            }
            for (int i5 = 0; i5 < 100; i5++) {
                GraalDirectives.sideEffect();
                for (int i4 = 0; i4 < 100; i4++) {
                    GraalDirectives.sideEffect();
                }
                for (int i3 = 0; i3 < 100; i3++) {
                    GraalDirectives.sideEffect();
                }
                for (int i2 = 0; i2 < 100; i2++) {
                    GraalDirectives.sideEffect();
                }
                for (int i1 = 0; i1 < 100; i1++) {
                    GraalDirectives.sideEffect();
                }
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testLoopDataStructure() {
        StructuredGraph g = parseEager(getResolvedJavaMethod("snippet02"), AllowAssumptions.NO);
        assert g.verify();
    }

}
