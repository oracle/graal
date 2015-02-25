/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.schedule.*;
import com.oracle.graal.phases.schedule.SchedulePhase.SchedulingStrategy;
import com.oracle.graal.phases.tiers.*;

public class LongNodeChainTest extends GraalCompilerTest {

    public static final int N = 100000;

    @Ignore
    @Test
    public void testLongAddChain() {
        HighTierContext context = new HighTierContext(getProviders(), null, getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL);
        StructuredGraph graph = new StructuredGraph(AllowAssumptions.NO);
        ValueNode constant = graph.unique(ConstantNode.forPrimitive(JavaConstant.INT_1));
        ValueNode value = constant;
        for (int i = 0; i < N; ++i) {
            value = graph.unique(new AddNode(constant, value));
        }
        ReturnNode returnNode = graph.add(new ReturnNode(value));
        graph.start().setNext(returnNode);

        for (SchedulingStrategy s : SchedulingStrategy.values()) {
            new SchedulePhase(s).apply(graph);
        }

        new CanonicalizerPhase(false).apply(graph, context);
        JavaConstant asConstant = (JavaConstant) returnNode.result().asConstant();
        Assert.assertEquals(N + 1, asConstant.asInt());
    }
}
