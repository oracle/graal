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

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.loop.LoopEx;
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

    @Test
    public void test01() {
        try (AutoCloseable c = new TTY.Filter()) {
            OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.FullUnroll, false);
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
                LoopEx lex = ld.countedLoops().get(0);

                LoopBeginNode lb = new LoopBeginNode();
                lb = graph.add(lb);

                // replace with a new copy of the loop begin node and loop ends that cannot
                // safepoint
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

}
