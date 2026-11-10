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
import jdk.graal.compiler.nodes.calc.SignExtendNode;
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
        return aliasCondition(descending, 8, writeScale, 100, 100);
    }

    /// Models different offset scales and bases, including addresses near the unsigned boundary.
    private LogicNode aliasCondition(boolean descending, long readScale, long writeScale, long readBase, long writeBase) throws Exception {
        StructuredGraph graph = parseEager(descending ? "descendingLoop" : "ascendingLoop", AllowAssumptions.NO);
        BlackholeNode anchor = graph.getNodes().filter(BlackholeNode.class).first();
        Assert.assertNotNull("fixed body marker must survive parsing", anchor);
        ValueNode counter = anchor.getValue();
        ValueNode readOffset = graph.addOrUnique(new MulNode(counter, ConstantNode.forLong(readScale, graph)));
        ValueNode writeOffset = graph.addOrUnique(new MulNode(counter, ConstantNode.forLong(writeScale, graph)));
        return aliasCondition(graph, readOffset, writeOffset, readBase, writeBase, 3);
    }

    /// Builds raw accesses whose write consumes the read, without touching native memory.
    private LogicNode aliasCondition(StructuredGraph graph, ValueNode readOffset, ValueNode writeOffset, long readBase, long writeBase, long expectedTripCount) throws Exception {
        BlackholeNode anchor = graph.getNodes().filter(BlackholeNode.class).first();
        ReadNode read = graph.add(new ReadNode(graph.unique(new OffsetAddressNode(ConstantNode.forLong(readBase, graph), readOffset)), LocationIdentity.any(),
                        StampFactory.forKind(JavaKind.Int), BarrierType.NONE, MemoryOrderMode.PLAIN));
        WriteNode write = graph.add(new WriteNode(graph.unique(new OffsetAddressNode(ConstantNode.forLong(writeBase, graph), writeOffset)), LocationIdentity.any(),
                        read, BarrierType.NONE, MemoryOrderMode.PLAIN));
        graph.addBeforeFixed(anchor, read);
        graph.addAfterFixed(read, write);
        LoopsData data = getDefaultHighTierContext().getLoopsDataProvider().getLoopsData(graph);
        data.detectCountedLoops();
        Assert.assertEquals(1, data.countedLoops().size());
        Loop loop = data.countedLoops().get(0);
        Assert.assertTrue(loop.counted().counterNeverOverflows());
        Assert.assertEquals(new UnsignedLong(expectedTripCount), loop.counted().constantMaxTripCount());
        Assert.assertTrue(loop.whole().nodes().contains(read));
        Assert.assertTrue(loop.whole().nodes().contains(write));
        Assert.assertTrue(loop.getInductionVariables().get(readOffset).isConstantStride());
        Assert.assertTrue(loop.getInductionVariables().get(writeOffset).isConstantStride());
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

    /// Supplies the counter range for addresses that overflow before widening to long.
    public static void descendingIntLoop() {
        for (int i = 2; i >= -1; i--) {
            GraalDirectives.blackhole(i);
        }
    }

    /// Preserves dependence when the first read offset overflows in int arithmetic.
    @Test
    public void descendingRawOffsetsCannotWrapBeforeWidening() throws Exception {
        Assert.assertTrue(widenedIntAliasCondition(false).isTautology());
        Assert.assertTrue(widenedIntAliasCondition(true).isTautology());
    }

    /// Models overflow in either the read or write offset, before its conversion to long.
    private LogicNode widenedIntAliasCondition(boolean overflowWrite) throws Exception {
        StructuredGraph graph = parseEager("descendingIntLoop", AllowAssumptions.NO);
        ValueNode counter = graph.getNodes().filter(BlackholeNode.class).first().getValue();
        ValueNode intReadOffset = graph.addOrUnique(new MulNode(counter, ConstantNode.forInt(0x40000000, graph)));
        ValueNode readOffset = graph.addOrUnique(new SignExtendNode(intReadOffset, Long.SIZE));
        ValueNode longCounter = graph.addOrUnique(new SignExtendNode(counter, Long.SIZE));
        ValueNode writeOffset = graph.addOrUnique(new MulNode(longCounter, ConstantNode.forLong(0x40000000L, graph)));
        // The first read wraps to 0x80000000, below the first write at 0x140000000.
        // The next read jumps up to 0x140000000 and consumes that overwritten value.
        if (overflowWrite) {
            return aliasCondition(graph, writeOffset, readOffset, 0xC0000000L, 0x100000000L, 4);
        }
        return aliasCondition(graph, readOffset, writeOffset, 0x100000000L, 0xC0000000L, 4);
    }

    /// Rejects offsets that overflow at the first or last iteration in long arithmetic.
    @Test
    public void rawOffsetsCannotWrapInLongArithmetic() throws Exception {
        long scale = 0x4000000000000000L;
        Assert.assertTrue(aliasCondition(true, scale, scale, 100, 100).isTautology());
        Assert.assertTrue(aliasCondition(false, scale, scale, 100, 100).isTautology());
    }

    /// Rejects unsigned address ranges that wrap after adding the base or the access width.
    @Test
    public void rawAddressRangesCannotWrap() throws Exception {
        Assert.assertTrue(aliasCondition(true, 8, 8, -8, -8).isTautology());
        Assert.assertTrue(aliasCondition(true, 8, 8, -17, -17).isTautology());
        Assert.assertTrue(aliasCondition(true, 8, 8, -32, -32).isContradiction());
    }
}
