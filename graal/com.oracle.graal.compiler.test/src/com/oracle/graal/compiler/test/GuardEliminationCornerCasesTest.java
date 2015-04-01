/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.test;

import org.junit.*;

import com.oracle.graal.api.directives.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.schedule.*;
import com.oracle.graal.phases.tiers.*;

public class GuardEliminationCornerCasesTest extends GraalCompilerTest {

    static class A {

    }

    static class B extends A {

    }

    static class C extends B {

    }

    static class D extends C {

    }

    @SuppressWarnings({"static-method", "unused"})
    private int testMethod(Object a) {
        if (a instanceof A) {
            if (a instanceof C) {
                if (a instanceof B) {
                    B b = (B) a;
                    if (b instanceof C) {
                        return 1;
                    } else {
                        GraalDirectives.deoptimize();
                    }
                }
            } else {
                GraalDirectives.deoptimize();
            }
        }
        return 0;
    }

    @Test
    public void testFloatingGuards() {
        HighTierContext context = getDefaultHighTierContext();
        StructuredGraph graph = parseEager("testMethod", AllowAssumptions.YES);
        new ConvertDeoptimizeToGuardPhase().apply(graph, context);
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
        Debug.dump(graph, "after parsing");

        GuardNode myGuardNode = null;
        for (Node n : graph.getNodes()) {
            if (n instanceof GuardNode) {
                GuardNode guardNode = (GuardNode) n;
                LogicNode condition = guardNode.condition();
                if (condition instanceof InstanceOfNode) {
                    InstanceOfNode instanceOfNode = (InstanceOfNode) condition;
                    if (instanceOfNode.getValue() instanceof ValueProxy) {
                        myGuardNode = guardNode;
                        break;
                    }
                }
            }
        }

        AbstractBeginNode myBegin = (AbstractBeginNode) myGuardNode.getAnchor();
        AbstractBeginNode prevBegin = BeginNode.prevBegin((FixedNode) myBegin.predecessor());
        myGuardNode.setAnchor(prevBegin);

        Debug.dump(graph, "after manual modification");
        graph.reverseUsageOrder();
        new ConditionalEliminationPhase().apply(graph);
        new SchedulePhase(SchedulePhase.SchedulingStrategy.EARLIEST).apply(graph);
    }
}
