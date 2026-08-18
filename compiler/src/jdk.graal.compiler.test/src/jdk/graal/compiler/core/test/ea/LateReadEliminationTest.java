/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test.ea;

import java.util.Optional;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.FieldLocationIdentity;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.FixReadsPhase;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import org.junit.Assert;
import org.junit.Test;

public class LateReadEliminationTest extends GraalCompilerTest {

    static class Y {
        X x;
    }

    static class X {
        final int x;

        X(int x) {
            this.x = x;
        }
    }

    @Override
    protected Suites createSuites(OptionValues opts) {
        Suites s = super.createSuites(opts).copy();
        s.getLowTier().findPhase(FixReadsPhase.class, true).add(new BasePhase<LowTierContext>() {

            @Override
            public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
                return ALWAYS_APPLICABLE;
            }

            @Override
            protected void run(StructuredGraph graph, LowTierContext context) {
                for (Node n : graph.getNodes().snapshot()) {
                    if (n instanceof ReadNode) {
                        /*
                         * Override the location identity of all the fields of the tests in this
                         * class to be immutable. We do this to test late read elimination of
                         * immutable field accesses.
                         */
                        final boolean immutable = true;
                        ReadNode old = (ReadNode) n;
                        FieldLocationIdentity fli = (FieldLocationIdentity) old.getLocationIdentity();
                        ReadNode nr = new ReadNode(old.getAddress(), new FieldLocationIdentity(fli.getField(), immutable), old.stamp(NodeView.DEFAULT), old.getBarrierType(), old.getMemoryOrder());
                        graph.replaceFixedWithFixed((FixedWithNextNode) n,
                                        graph.add(nr));
                    }
                }
            }

        });
        return s;
    }

    public static int lateRETest(int doReading, Y y) {
        /*
         * We inject a branch probability though we never go down the route of the actual else
         * branch. This test just verifies that immutable folding works but does not exercise code.
         */
        if (GraalDirectives.injectBranchProbability(0.5, doReading == 0)) {
            return 0;
        } else {
            /*
             * We artificially mark all reads in this block as immutable, therefore they can be
             * folded by late read elimination even in the presence of side effects killing ANY.
             */
            int res = y.x.x;
            GraalDirectives.sideEffect();
            res += y.x.x;
            GraalDirectives.sideEffect();
            res += y.x.x;
            GraalDirectives.sideEffect();
            res += y.x.x;
            GraalDirectives.sideEffect();
            return res;
        }
    }

    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        super.checkLowTierGraph(graph);
        Assert.assertEquals(2, graph.getNodes().filter(ReadNode.class).count());
    }

    @Test
    public void testLateRE() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.EarlyGVN, false);
        Y y = new Y();
        y.x = new X(42);
        test(opt, "lateRETest", 0, y);
    }
}
