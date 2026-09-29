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

package jdk.graal.compiler.phases.common.test;

import org.graalvm.word.LocationIdentity;
import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.IndexAddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.writesinking.data.MovableWriteState;
import jdk.graal.compiler.phases.common.writesinking.data.MovableWriteState.CacheData;
import jdk.graal.compiler.phases.common.writesinking.data.MovableWriteState.CacheEntry;
import jdk.graal.compiler.phases.common.writesinking.data.MovableWriteState.CommitOrder;
import jdk.graal.compiler.phases.common.writesinking.writesink.WriteSinkingBlockState;

import jdk.vm.ci.meta.JavaKind;

public class MovableWriteStateTest {

    @Test
    public void verifyRejectsMismatchedBase() {
        OptionValues options = new OptionValues(null, OptionValues.newOptionMap());
        StructuredGraph graph = new StructuredGraph.Builder(options, DebugContext.disabled(options)).build();
        ValueNode offset = ConstantNode.forLong(8, graph);
        OffsetAddressNode keyAddress = graph.addWithoutUnique(new OffsetAddressNode(ConstantNode.forLong(0, graph), offset));
        OffsetAddressNode dataAddress = graph.addWithoutUnique(new OffsetAddressNode(ConstantNode.forLong(16, graph), offset));
        assertAddressMismatch(graph, keyAddress, dataAddress, "base");
    }

    @Test
    public void verifyRejectsMismatchedOffset() {
        OptionValues options = new OptionValues(null, OptionValues.newOptionMap());
        StructuredGraph graph = new StructuredGraph.Builder(options, DebugContext.disabled(options)).build();
        ValueNode base = ConstantNode.forLong(0, graph);
        OffsetAddressNode keyAddress = graph.addWithoutUnique(new OffsetAddressNode(base, ConstantNode.forLong(8, graph)));
        OffsetAddressNode dataAddress = graph.addWithoutUnique(new OffsetAddressNode(base, ConstantNode.forLong(16, graph)));
        assertAddressMismatch(graph, keyAddress, dataAddress, "offset");
    }

    @Test
    public void verifyRejectsMismatchedIndex() {
        OptionValues options = new OptionValues(null, OptionValues.newOptionMap());
        StructuredGraph graph = new StructuredGraph.Builder(options, DebugContext.disabled(options)).build();
        ValueNode base = ConstantNode.defaultForKind(JavaKind.Object, graph);
        IndexAddressNode keyAddress = graph.addWithoutUnique(new IndexAddressNode(base, ConstantNode.forInt(0, graph), JavaKind.Int));
        IndexAddressNode dataAddress = graph.addWithoutUnique(new IndexAddressNode(base, ConstantNode.forInt(1, graph), JavaKind.Int));
        assertAddressMismatch(graph, keyAddress, dataAddress, "offset");
    }

    private static void assertAddressMismatch(StructuredGraph graph, AddressNode keyAddress, AddressNode dataAddress, String component) {
        CacheEntry key = new CacheEntry(keyAddress, LocationIdentity.any());
        CacheData data = new CacheData(dataAddress, ConstantNode.forInt(1, graph), BarrierType.NONE);
        MovableWriteState state = new MovableWriteState();
        GraalError error = Assert.assertThrows(GraalError.class, () -> {
            // addEntry also verifies the state when assertions are enabled.
            state.addEntry(graph, key, data);
            Assert.assertTrue(state.verify());
        });
        Assert.assertTrue(error.getMessage(), error.getMessage().contains("Write key " + component));
    }

    @Test
    public void equivalentKeysWithDistinctAddressesKeepStateConsistent() {
        OptionValues options = new OptionValues(null, OptionValues.newOptionMap());
        StructuredGraph graph = new StructuredGraph.Builder(options, DebugContext.disabled(options)).build();
        ValueNode base = ConstantNode.forLong(0, graph);
        ValueNode offset = ConstantNode.forLong(8, graph);
        OffsetAddressNode firstAddress = graph.addWithoutUnique(new OffsetAddressNode(base, offset));
        OffsetAddressNode secondAddress = graph.addWithoutUnique(new OffsetAddressNode(base, offset));
        LocationIdentity identity = LocationIdentity.any();
        CacheEntry firstEntry = new CacheEntry(firstAddress, identity);
        CacheEntry secondEntry = new CacheEntry(secondAddress, identity);
        CacheData firstData = new CacheData(firstAddress, ConstantNode.forInt(1, graph), BarrierType.NONE);
        CacheData secondData = new CacheData(secondAddress, ConstantNode.forInt(2, graph), BarrierType.NONE);

        Assert.assertNotSame(firstAddress, secondAddress);
        Assert.assertEquals(firstEntry, secondEntry);

        MovableWriteState state = new MovableWriteState();
        state.addEntry(graph, firstEntry, firstData);
        MovableWriteState beforeReplacement = state.cloneState();
        // Equal keys are interchangeable; replacement must update the address in the data.
        state.addEntry(graph, secondEntry, secondData);

        int storedEntryCount = 0;
        CacheEntry storedEntry = null;
        for (CacheEntry entry : state.getEntries()) {
            storedEntry = entry;
            storedEntryCount++;
        }
        CacheEntry orderedEntry = state.getOrdering(identity).get(0);
        Assert.assertEquals(1, storedEntryCount);
        Assert.assertEquals(secondEntry, storedEntry);
        Assert.assertEquals(secondEntry, orderedEntry);
        Assert.assertEquals(1, state.getOrdering(identity).size());
        Assert.assertSame(secondAddress, state.getCacheData(storedEntry).address);
        Assert.assertSame(secondAddress, state.getCacheData(orderedEntry).address);
        Assert.assertTrue(state.verify());
        MovableWriteState afterReplacement = state.cloneState();
        Assert.assertTrue(afterReplacement.verify());
        Assert.assertSame(firstData, beforeReplacement.getCacheData(secondEntry));
        Assert.assertSame(secondData, afterReplacement.getCacheData(firstEntry));
        Assert.assertTrue(state.containsEntry(firstEntry));
        Assert.assertSame(secondData, state.getCacheData(firstEntry));
        Assert.assertSame(secondData, state.getCacheData(secondEntry));
        Assert.assertEquals(1, afterReplacement.killWrites((key, data) -> {
            Assert.assertEquals(firstEntry, key);
            Assert.assertSame(secondAddress, data.address);
            Assert.assertSame(secondData.value, data.value);
        }));
        Assert.assertTrue(afterReplacement.isEmpty());
        Assert.assertFalse(state.isEmpty());
    }

    @Test
    public void commitUsesReplacementAddress() {
        OptionValues options = new OptionValues(null, OptionValues.newOptionMap());
        StructuredGraph graph = new StructuredGraph.Builder(options, DebugContext.disabled(options)).build();
        ReturnNode commitPoint = graph.add(new ReturnNode(null));
        graph.start().setNext(commitPoint);
        ControlFlowGraph cfg = ControlFlowGraph.newBuilder(graph).connectBlocks(true).computeLoops(true).computeFrequency(true).build();
        WriteSinkingBlockState state = new WriteSinkingBlockState(graph, cfg);

        ValueNode base = ConstantNode.forLong(0, graph);
        ValueNode offset = ConstantNode.forLong(8, graph);
        OffsetAddressNode firstAddress = graph.addWithoutUnique(new OffsetAddressNode(base, offset));
        OffsetAddressNode secondAddress = graph.addWithoutUnique(new OffsetAddressNode(base, offset));
        LocationIdentity identity = LocationIdentity.any();
        CacheEntry firstEntry = new CacheEntry(firstAddress, identity);
        CacheEntry secondEntry = new CacheEntry(secondAddress, identity);
        ValueNode firstValue = ConstantNode.forInt(1, graph);
        ValueNode secondValue = ConstantNode.forInt(2, graph);
        state.addCacheEntry(firstEntry, firstValue, new WriteNode(firstAddress, identity, firstValue, BarrierType.NONE, MemoryOrderMode.PLAIN));
        state.addCacheEntry(secondEntry, secondValue, new WriteNode(secondAddress, identity, secondValue, BarrierType.NONE, MemoryOrderMode.PLAIN));

        // The commit plan accepts an equal key, even when it came from the earlier write.
        CommitOrder order = state.spawnCommitOrder();
        order.register(firstEntry);
        Assert.assertTrue(state.commitOrder(order, commitPoint));
        WriteNode committed = (WriteNode) commitPoint.predecessor();
        Assert.assertSame(secondAddress, committed.getAddress());
        Assert.assertSame(secondValue, committed.value());
        Assert.assertEquals(1, graph.getNodes(WriteNode.TYPE).count());
        Assert.assertTrue(state.getMovableWrites().isEmpty());
        Assert.assertTrue(state.getMovableWrites().verify());
    }
}
