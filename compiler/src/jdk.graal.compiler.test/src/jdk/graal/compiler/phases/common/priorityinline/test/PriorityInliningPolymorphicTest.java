/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common.priorityinline.test;

import java.util.ListIterator;

import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.core.test.TestGraphBuilderPhase;
import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.vm.ci.meta.DeoptimizationReason;

public class PriorityInliningPolymorphicTest extends GraalCompilerTest {

    @BytecodeParserNeverInline
    public static int callee(I obj) {
        return obj.foo(); // [A: 0.99, B: 0.01]
    }

    public static int caller(I obj) {
        return (GraalDirectives.injectBranchProbability(0.1, obj instanceof A) ? bar() : 0) + callee(obj);
    }

    @Test
    public void testPolymorphic() {
        I a = new A();
        I b0 = new B(a);
        I b1 = new B(b0);
        for (int i = 0; i < 5_000; i++) {
            callee(i % 100 != 0 ? a : i % 200 == 0 ? b0 : b1);
        }

        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();

        StructuredGraph calleeGraph = parseEager("callee", StructuredGraph.AllowAssumptions.YES);
        new PriorityInliningPhase(canonicalizer, getInitialOptions()).apply(calleeGraph, getDefaultHighTierContext());

        StructuredGraph callerGraph = parseEager("caller", StructuredGraph.AllowAssumptions.YES);
        new PriorityInliningPhase(canonicalizer, getInitialOptions()).apply(callerGraph, getDefaultHighTierContext());

        // Assert that if the caller graph contains a TypeCheckedInliningViolated DeoptimizeNode,
        // the callee graph should also contain one to avoid deoptimization loop.
        if (getTypeCheckedInliningViolatedDeoptCount(callerGraph) > 0) {
            assertTrue(getTypeCheckedInliningViolatedDeoptCount(calleeGraph) > 0);
        }
    }

    private static int getTypeCheckedInliningViolatedDeoptCount(StructuredGraph graph) {
        int count = 0;
        for (DeoptimizeNode node : graph.getNodes(DeoptimizeNode.TYPE)) {
            if (node.getReason() == DeoptimizationReason.TypeCheckedInliningViolated) {
                count++;
            }
        }
        return count;
    }

    @BytecodeParserNeverInline
    public static int bar() {
        return 1;
    }

    public interface I {
        int foo();
    }

    public static class A implements I {
        @Override
        public int foo() {
            return bar();
        }
    }

    public static class B implements I {

        private final I obj;

        public B(I obj) {
            this.obj = obj;
        }

        @Override
        public int foo() {
            return bar() + obj.foo(); // no profile, create indirect call
        }

    }

    public static class C implements I {
        @Override
        public int foo() {
            return bar();
        }
    }

    @Override
    protected PhaseSuite<HighTierContext> getDefaultGraphBuilderSuite() {
        // Pass @BytecodeParserNeverInline plugins to the inliner.
        PhaseSuite<HighTierContext> suite = super.getDefaultGraphBuilderSuite();
        ListIterator<BasePhase<? super HighTierContext>> iterator = suite.findPhase(GraphBuilderPhase.class);
        GraphBuilderConfiguration gbConfCopy = editGraphBuilderConfiguration(GraphBuilderConfiguration.getDefault(getDefaultGraphBuilderPlugins()));
        iterator.remove();
        iterator.add(new TestGraphBuilderPhase(gbConfCopy));
        return suite;
    }

}
