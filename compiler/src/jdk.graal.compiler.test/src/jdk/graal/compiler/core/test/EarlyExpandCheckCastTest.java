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
package jdk.graal.compiler.core.test;

import java.util.ArrayList;
import java.util.ListIterator;
import java.util.Optional;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.extended.OpaqueNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.EarlyExpandCheckCastPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import org.junit.Assert;
import org.junit.Test;

/**
 * Exercises early checkcast expansion on values loaded from a list inside a loop.
 * Before the phase, the compiler sees the cast as a combined check:
 *
 * <pre>
 * Object value = arrayList.get(i);
 * sum += (Integer) value;
 * </pre>
 *
 * After expansion, the null check and type check are exposed as separate control-flow paths:
 *
 * <pre>
 * if (value == null) {
 *     // The cast succeeds with a null result.
 * } else if (value instanceof Integer) {
 *     sum += (Integer) value;
 * } else {
 *     deopt(ClassCastException);
 * }
 * </pre>
 *
 * The tests verify that this shape is available to subsequent high-tier optimizations and that
 * the resulting code preserves the original behavior.
 */
public class EarlyExpandCheckCastTest extends GraalCompilerTest {

    @Override
    protected Suites createSuites(OptionValues opts) {
        Suites s = super.createSuites(opts);
        ListIterator<BasePhase<? super HighTierContext>> pos = s.getHighTier().findPhase(EarlyExpandCheckCastPhase.class);
        pos.previous();
        // assert we see the expected check cast instanceof shapes
        pos.add(new BasePhase<HighTierContext>() {
            @Override
            public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
                return ALWAYS_APPLICABLE;
            }

            @Override
            protected void run(StructuredGraph graph, HighTierContext context) {
                boolean oneCheckCastIOFFound = false;
                for (Node n : graph.getNodes()) {
                    if (n instanceof InstanceOfNode) {
                        if (((InstanceOfNode) n).allowsNull()) {
                            oneCheckCastIOFFound = true;
                            break;
                        }
                    }
                }
                Assert.assertTrue("Must find at least one checkcast instanceof", oneCheckCastIOFFound);
            }
        });
        return s;
    }

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        int iofWithNull = graph.getNodes().filter(InstanceOfNode.class).filter(x -> ((InstanceOfNode) x).allowsNull()).count();
        int iofNonNull = graph.getNodes().filter(InstanceOfNode.class).filter(x -> !((InstanceOfNode) x).allowsNull()).count();
        int nullChecks = graph.getNodes().filter(IsNullNode.class).count();
        Assert.assertTrue("Must not have checkcast null checks left", iofWithNull == 0);
        Assert.assertTrue("Must not instanceofs without null left", iofNonNull > 0);
        Assert.assertTrue("Must have explicit null checks in the graph", nullChecks > 0);
        super.checkHighTierGraph(graph);
    }

    private static ArrayList<Integer> createIntegerSequence(int max) {
        ArrayList<Integer> integers = new ArrayList<>();
        for (int i = 0; i < max; i++) {
            integers.add(i);
        }
        return integers;
    }

    public static int iterate(@SuppressWarnings("rawtypes") ArrayList arrayList) {
        int sum = 0;
        for (int i = 0; GraalDirectives.injectIterationCount(1000, i < arrayList.size()); ++i) {
            sum += (Integer) arrayList.get(i);
        }
        return sum;
    }

    @Test
    public void test01() {
        test("iterate", createIntegerSequence(1000));
    }

    public static int iterateGeneric(ArrayList<Integer> arrayList) {
        int sum = 0;
        for (int i = 0; GraalDirectives.injectIterationCount(1000, i < arrayList.size()); ++i) {
            sum += arrayList.get(i);
        }
        return sum;
    }

    @Test
    public void test01Generic() {
        test("iterateGeneric", createIntegerSequence(1000));
    }

    @Test
    public void test02() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.StressExplicitExceptionCode, true);
        test(opt, "iterate", createIntegerSequence(1000));
    }

    public static int iterate2(@SuppressWarnings("rawtypes") ArrayList arrayList) {
        int sum = 0;
        for (int i = 0; GraalDirectives.injectIterationCount(1000, i < arrayList.size()); ++i) {
            sum += (Integer) arrayList.get(i);
            sum += (Integer) arrayList.get(i);
        }
        return sum;
    }

    @Test
    public void test03() {
        test("iterate2", createIntegerSequence(1000));
    }

    public static int iterate3(@SuppressWarnings("rawtypes") ArrayList arrayList) {
        int sum = 0;
        for (int i = 0; GraalDirectives.injectIterationCount(1000, i < arrayList.size()); ++i) {
            Object o = arrayList.get(i);
            if (i > 123) {
                Integer i1 = (Integer) o;
                sum += i1;
            } else {
                Integer i2 = (Integer) o;
                sum += i2;
            }
        }
        return sum;
    }

    @Test
    public void test04() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.StressExplicitExceptionCode, true, GraalOptions.OptConvertDeoptsToGuards, false);
        test(opt, "iterate3", createIntegerSequence(1000));
    }

    public static int S;

    public static int nonCanon1() {
        Object phi = 0;
        if (S == 123) {
            phi = Integer.valueOf(123);
        } else {
            phi = null;
        }
        return (Integer) GraalDirectives.opaque(phi);
    }

    @Test
    public void testNonCanon1() {
        StructuredGraph g = parseEager(getResolvedJavaMethod("nonCanon1"), AllowAssumptions.YES);
        for (Node n : g.getNodes()) {
            if (n instanceof OpaqueNode) {
                n.replaceAtUsagesAndDelete(((OpaqueNode) n).getValue());
            }
        }
        new EarlyExpandCheckCastPhase(CanonicalizerPhase.create()).apply(g, getDefaultHighTierContext());
    }

}
