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
package org.graalvm.compiler.core.test;

import org.junit.Test;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.virtual.phases.ea.FinalPartialEscapePhase;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;

/**
 * Tests that correct removal of always deoptimizing {@link FixedGuardNode}s in the presence of
 * floating reads. This test modifies the graph after PEA to introduce a {@link FixedGuardNode} in a
 * branch which is dominated by another {@link FixedGuardNode} with the inverted condition.
 * Replacing the always-deopt guard must ensure a proper cleanup of all attached floating reads.
 * This is tested by a floating read for which the removed guard ensures non-nullness of the read
 * object. If this read would survive the removal of the guard, a segfault would be triggered. The
 * guard branch is deleted in midtier after conditional elimination.
 */
public class FixedGuardSimplificationTest extends GraalCompilerTest {

    public static class A {
        B b;

        public A(B b) {
            this.b = b;
        }
    }

    public static class B {
        A a;
    }

    public static void snippet(A a, A a2, int x) {
        guardingNull(a);
        if (x == 0) {
            // a2 is replaced by a1 after FinalPEA
            guardingNonNull(a2);
            B b = a.b;
            A ba = b.a;
            GraalDirectives.blackhole(ba);
        }
    }

    @Override
    protected Suites createSuites(OptionValues opts) {
        Suites s = super.createSuites(opts);
        s.getHighTier().findPhase(FinalPartialEscapePhase.class).add(new BasePhase<HighTierContext>() {

            @Override
            protected void run(StructuredGraph graph, HighTierContext context) {
                FixedGuardNode guard = graph.getNodes().filter(FixedGuardNode.class).first();
                FixedGuardNode placeholderGuard = graph.getNodes().filter(FixedGuardNode.class).snapshot().get(1);

                /*
                 * Makes the inner guard use the inverted condition of the dominating, outer guard
                 * and adds the corresponding Pi. This can happen during PartialEscapeAnalysis.
                 */
                placeholderGuard.setCondition(guard.getCondition(), true);
                ValueNode nullProven = (ValueNode) guard.getCondition().asNode().inputs().first();
                PiNode pi = (PiNode) graph.addWithoutUnique(PiNode.create(nullProven, StampFactory.objectNonNull(), placeholderGuard));
                LoadFieldNode lf = graph.getNodes().filter(LoadFieldNode.class).first();
                lf.setObject(pi);
            }

            @Override
            public java.util.Optional<NotApplicable> notApplicableTo(GraphState graphState) {
                return ALWAYS_APPLICABLE;
            }
        });
        return s;
    }

    private static <T> T guardingNull(T value) {
        if (value != null) {
            GraalDirectives.deoptimize(DeoptimizationAction.InvalidateRecompile, DeoptimizationReason.UnreachedCode, true);
        }
        return value;
    }

    private static <T> T guardingNonNull(T value) {
        if (value == null) {
            GraalDirectives.deoptimize(DeoptimizationAction.InvalidateRecompile, DeoptimizationReason.UnreachedCode, true);
        }
        return value;
    }

    @Test
    public void test() throws InvalidInstalledCodeException {
        InstalledCode code = getCode(getResolvedJavaMethod("snippet"));
        boolean exceptionSeen = false;
        try {
            code.executeVarargs(null, new A(new B()), 0);
        } catch (NullPointerException npe) {
            exceptionSeen = true;
        }
        assert exceptionSeen : "Test expects a NPE to be thrown!";
        assert !code.isValid() : "Test expects the code to have deopted!";
    }
}
