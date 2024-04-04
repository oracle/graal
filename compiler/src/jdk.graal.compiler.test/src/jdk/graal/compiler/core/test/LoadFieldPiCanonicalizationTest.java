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

import java.util.Objects;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.nodes.DeoptimizingGuard;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.ConditionalEliminationPhase;
import jdk.graal.compiler.phases.common.HighTierLoweringPhase;
import jdk.graal.compiler.phases.tiers.Suites;

public class LoadFieldPiCanonicalizationTest extends GraalCompilerTest {

    @Override
    protected Suites createSuites(OptionValues opts) {
        Suites s = super.createSuites(opts).copy();

        var pos = s.getHighTier().findPhase(HighTierLoweringPhase.class, true);
        /*
         * Massage the phase plan a bit so verification is simpler in checkGraphs methods.
         */
        CanonicalizerPhase c = CanonicalizerPhase.create();
        pos.add(c);
        pos.add(new ConditionalEliminationPhase(c, true));
        pos.add(c);

        return s;
    }

    private abstract static class Base {
        int baseField;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Base base = (Base) o;
            return baseField == base.baseField;
        }

        @Override
        public int hashCode() {
            return Objects.hash(baseField);
        }
    }

    private static class A extends Base {
        A(int f) {
            this.baseField = f;
        }
    }

    private static class B extends A {
        B(int f) {
            super(f);
        }
    }

    private enum GraphCheck {
        HIGH_TIER,
        MID_TIER
    }

    private GraphCheck graphCheck = GraphCheck.HIGH_TIER;
    private boolean expectHighTierNullChecks = false;

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        super.checkHighTierGraph(graph);

        if (graphCheck != GraphCheck.HIGH_TIER) {
            return;
        }

        /*
         * In these tests, we always expect a single instanceof check that proves a type and its
         * non-nullness.
         */
        Assert.assertEquals("instanceof checks", 1, graph.getNodes().filter(InstanceOfNode.class).count());
        Assert.assertEquals("if nodes", 1, graph.getNodes().filter(IfNode.class).count());
        Assert.assertEquals("pi nodes", 1, graph.getNodes().filter(PiNode.class).count());

        if (!expectHighTierNullChecks) {
            /* In particular, we don't want explicit null checks or guards of any kind. */
            Assert.assertEquals("null checks", 0, graph.getNodes().filter(IsNullNode.class).count());
            Assert.assertEquals("guard nodes", 0, graph.getNodes().filter(n -> n instanceof DeoptimizingGuard).count());
        }
    }

    @Override
    protected void checkMidTierGraph(StructuredGraph graph) {
        super.checkMidTierGraph(graph);

        if (graphCheck != GraphCheck.MID_TIER) {
            return;
        }

        /*
         * We want field stores to be uniqued. This is only possible after frame state assignment.
         * So we handle writes in these mid tier checks.
         */
        Assert.assertEquals("writes", 1, graph.getNodes().filter(WriteNode.class).count());
    }

    public static int getFieldNullCheckSnippet(Object o) {
        if (o instanceof A a) {
            return a.baseField;
        }
        return -1;
    }

    public static int getFieldNullCheckOldStyleSnippet(Object o) {
        if (o instanceof A) {
            A a = (A) o;
            return a.baseField;
        }
        return -1;
    }

    private static OptionValues getOptions() {
        OptionValues opt = new OptionValues(getInitialOptions(), ConditionalEliminationPhase.Options.FieldAccessSkipPreciseTypes, true);
        return opt;
    }

    @Test
    public void getFieldNullCheck() {
        test(getOptions(), "getFieldNullCheckSnippet", new A(42));
    }

    @Test
    public void getFieldNullCheckOldStyle() {
        test(getOptions(), "getFieldNullCheckOldStyleSnippet", new A(42));
    }

    public static int uniqueGetFieldSnippet(Object base) {
        if (base instanceof A a) {
            /*
             * The two branches access the same field. The field load should be uniqued, and the
             * inner branch should go away.
             */
            if (a instanceof B b) {
                return b.baseField;
            } else {
                return a.baseField;
            }
        } else {
            return -1;
        }
    }

    public static int uniqueGetFieldFromParameterSnippet(Base base) {
        if (base instanceof A a) {
            if (a instanceof B b) {
                return b.baseField;
            } else {
                return a.baseField;
            }
        } else {
            return -1;
        }
    }

    @Test
    public void uniqueGetField() {
        test(getOptions(), "uniqueGetFieldSnippet", new B(42));
        test(getOptions(), "uniqueGetFieldSnippet", new A(23));
    }

    @Test
    public void uniqueGetFieldFromParameter() {
        /*
         * In this test we unique the LoadFields as expected, but high tier lowering still inserts a
         * redundant null check. That null check goes away early in the mid tier.
         */
        expectHighTierNullChecks = true;
        try {
            test(getOptions(), "uniqueGetFieldFromParameterSnippet", new B(42));
            test(getOptions(), "uniqueGetFieldFromParameterSnippet", new A(23));
        } finally {
            expectHighTierNullChecks = false;
        }
    }

    public static Object uniquePutFieldSnippet(Object base, int value) {
        if (base instanceof A a) {
            /*
             * The two branches access the same field. The field store should be uniqued, and the
             * inner branch should go away.
             */
            if (a instanceof B b) {
                b.baseField = value;
            } else {
                a.baseField = value;
            }
        }
        return base;
    }

    public static Base uniquePutFieldFromParameterSnippet(Base base, int value) {
        if (base instanceof A a) {
            if (a instanceof B b) {
                b.baseField = value;
            } else {
                a.baseField = value;
            }
        }
        return base;
    }

    @Test
    public void uniquePutField() {
        graphCheck = GraphCheck.MID_TIER;
        try {
            ArgSupplier b = () -> new B(42);
            ArgSupplier a = () -> new A(23);
            test(getOptions(), "uniquePutFieldSnippet", b, 56);
            test(getOptions(), "uniquePutFieldSnippet", a, 56);
        } finally {
            graphCheck = GraphCheck.HIGH_TIER;
        }
    }

    @Test
    public void uniquePutFieldFromParameter() {
        /*
         * In this test we unique the LoadFields as expected, but high tier lowering still inserts a
         * redundant null check. That null check goes away early in the mid tier.
         */
        graphCheck = GraphCheck.MID_TIER;
        try {
            ArgSupplier b = () -> new B(42);
            ArgSupplier a = () -> new A(23);
            test(getOptions(), "uniquePutFieldFromParameterSnippet", b, 56);
            test(getOptions(), "uniquePutFieldFromParameterSnippet", a, 56);
        } finally {
            graphCheck = GraphCheck.HIGH_TIER;
        }
    }
}
