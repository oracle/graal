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

import java.lang.reflect.Method;

import org.graalvm.word.LocationIdentity;
import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.util.UnsignedLong;
import jdk.graal.compiler.guards.optimistic.memory.OptimisticAliasingAnalysisPhase;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.debug.BlackholeNode;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;

/// Tests cross-iteration dependencies using graph nodes without accessing raw memory.
public class OptimisticRawAliasingTest extends GraalCompilerTest {
    /// Supplies a counted loop for the modeled raw memory accesses.
    public static void descendingLoop() {
        for (long i = 2; i >= 0; i--) {
            GraalDirectives.blackhole(i);
        }
    }

    /// Supplies a loop with the same access range and the opposite iteration order.
    public static void ascendingLoop() {
        for (long i = 0; i < 3; i++) {
            GraalDirectives.blackhole(i);
        }
    }

    /// Builds the alias condition for equal-base reads and writes in either iteration order.
    private LogicNode aliasCondition(boolean descending, int writeScale) throws Exception {
        StructuredGraph graph = parseEager(descending ? "descendingLoop" : "ascendingLoop", AllowAssumptions.NO);
        BlackholeNode anchor = graph.getNodes().filter(BlackholeNode.class).first();
        Assert.assertNotNull("fixed body marker must survive parsing", anchor);
        ValueNode counter = anchor.getValue();
        ValueNode readOffset = graph.addOrUnique(new MulNode(counter, ConstantNode.forLong(8, graph)));
        ValueNode writeOffset = graph.addOrUnique(new MulNode(counter, ConstantNode.forLong(writeScale, graph)));
        ValueNode base = ConstantNode.forLong(100, graph);
        ReadNode read = graph.add(new ReadNode(graph.unique(new OffsetAddressNode(base, readOffset)), LocationIdentity.any(),
                        StampFactory.forKind(JavaKind.Int), BarrierType.NONE, MemoryOrderMode.PLAIN));
        WriteNode write = graph.add(new WriteNode(graph.unique(new OffsetAddressNode(base, writeOffset)), LocationIdentity.any(),
                        read, BarrierType.NONE, MemoryOrderMode.PLAIN));
        graph.addBeforeFixed(anchor, read);
        graph.addAfterFixed(read, write);
        LoopsData data = getDefaultHighTierContext().getLoopsDataProvider().getLoopsData(graph);
        data.detectCountedLoops();
        Assert.assertEquals(1, data.countedLoops().size());
        Loop loop = data.countedLoops().get(0);
        Assert.assertTrue(loop.counted().counterNeverOverflows());
        Assert.assertEquals(new UnsignedLong(3), loop.counted().constantMaxTripCount());
        Assert.assertTrue(loop.whole().nodes().contains(read));
        Assert.assertTrue(loop.whole().nodes().contains(write));
        Assert.assertEquals(descending ? -8 : 8, loop.getInductionVariables().get(readOffset).constantStride());
        Assert.assertEquals(descending ? -writeScale : writeScale, loop.getInductionVariables().get(writeOffset).constantStride());
        Method method = OptimisticAliasingAnalysisPhase.class.getDeclaredMethod("testLoopAccessAliasing", Loop.class,
                        ValueNode.class, WriteNode.class, boolean.class, ConstantReflectionProvider.class);
        method.setAccessible(true);
        return (LogicNode) method.invoke(null, loop, read, write, false, getConstantReflection());
    }

    /// Keeps a dependence when the first write overwrites the next iteration's read.
    @Test
    public void descendingRawWritesCannotOvertakeFutureReads() throws Exception {
        Assert.assertTrue("same-index dependent writes are independent between iterations", aliasCondition(true, 8).isContradiction());
        // Reads use 116, 108, 100 and writes use 108, 104, 100. The first write therefore
        // changes the four-byte value loaded by the second iteration.
        LogicNode aliases = aliasCondition(true, 4);
        Assert.assertTrue("descending raw access must retain the first-write/next-read dependence", aliases.isTautology());
    }

    /// Preserves independent ascending accesses and rejects writes that overtake future reads.
    @Test
    public void ascendingRawWritesStayBehindReads() throws Exception {
        Assert.assertTrue(aliasCondition(false, 8).isContradiction());
        Assert.assertTrue(aliasCondition(false, 4).isContradiction());
        Assert.assertTrue(aliasCondition(false, 16).isTautology());
    }
}
