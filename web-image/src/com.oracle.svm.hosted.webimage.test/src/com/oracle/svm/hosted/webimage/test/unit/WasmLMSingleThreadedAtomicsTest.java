/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.test.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.graalvm.word.LocationIdentity;
import org.junit.Test;

import com.oracle.svm.hosted.webimage.wasm.phases.WasmLMSingleThreadedAtomicsPhase;
import com.oracle.svm.hosted.webimage.wasm.snippets.SingleThreadedAtomics;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.java.AbstractCompareAndSwapNode;
import jdk.graal.compiler.nodes.java.LogicCompareAndSwapNode;
import jdk.graal.compiler.nodes.java.ValueCompareAndSwapNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.test.AddExports;

@AddExports("org.graalvm.nativeimage/org.graalvm.nativeimage.impl")
public class WasmLMSingleThreadedAtomicsTest {

    private static final class TestPhase extends WasmLMSingleThreadedAtomicsPhase {
        void lower(AbstractCompareAndSwapNode cas) {
            processCAS(null, cas);
        }
    }

    private static void checkCAS(int bits, boolean logic, ForeignCallDescriptor descriptor) {
        var options = GraalCompilerTest.getInitialOptions();
        try (DebugContext debug = new DebugContext.Builder(options).build()) {
            StructuredGraph graph = new StructuredGraph.Builder(options, debug, AllowAssumptions.NO).build();
            OffsetAddressNode address = graph.unique(new OffsetAddressNode(ConstantNode.forLong(0, graph), ConstantNode.forLong(0, graph)));
            ConstantNode expected = ConstantNode.forIntegerBits(bits, 1, graph);
            ConstantNode replacement = ConstantNode.forIntegerBits(bits, 2, graph);
            AbstractCompareAndSwapNode cas = graph.add(logic
                            ? new LogicCompareAndSwapNode(address, expected, replacement, LocationIdentity.any(), BarrierType.NONE, MemoryOrderMode.VOLATILE)
                            : new ValueCompareAndSwapNode(address, expected, replacement, LocationIdentity.any(), BarrierType.NONE, MemoryOrderMode.VOLATILE));
            assertEquals(bits, ((IntegerStamp) cas.getAccessStamp(NodeView.DEFAULT)).getBits());
            var resultStamp = cas.stamp(NodeView.DEFAULT);
            ReturnNode ret = graph.add(new ReturnNode(cas));
            graph.start().setNext(cas);
            cas.setNext(ret);
            assertTrue(graph.verify());

            new TestPhase().lower(cas);

            assertEquals(0, graph.getNodes().filter(AbstractCompareAndSwapNode.class).count());
            assertEquals(1, graph.getNodes().filter(ForeignCallNode.class).count());
            ForeignCallNode call = graph.getNodes().filter(ForeignCallNode.class).first();
            assertSame(descriptor, call.getDescriptor());
            assertEquals(resultStamp, call.stamp(NodeView.DEFAULT));
            assertSame(call, ret.result());
        }
    }

    @Test
    public void logicalByteCAS() {
        checkCAS(8, true, SingleThreadedAtomics.LOGIC_COMPARE_AND_SWAP_BYTE);
    }

    @Test
    public void logicalCharCAS() {
        checkCAS(16, true, SingleThreadedAtomics.LOGIC_COMPARE_AND_SWAP_CHAR);
    }

    @Test
    public void logicalIntCAS() {
        checkCAS(32, true, SingleThreadedAtomics.LOGIC_COMPARE_AND_SWAP_INT);
    }

    @Test
    public void logicalLongCAS() {
        checkCAS(64, true, SingleThreadedAtomics.LOGIC_COMPARE_AND_SWAP_LONG);
    }

    @Test
    public void valueByteCAS() {
        checkCAS(8, false, SingleThreadedAtomics.VALUE_COMPARE_AND_SWAP_BYTE);
    }

    @Test
    public void valueCharCAS() {
        checkCAS(16, false, SingleThreadedAtomics.VALUE_COMPARE_AND_SWAP_CHAR);
    }

    @Test
    public void valueIntCAS() {
        checkCAS(32, false, SingleThreadedAtomics.VALUE_COMPARE_AND_SWAP_INT);
    }

    @Test
    public void valueLongCAS() {
        checkCAS(64, false, SingleThreadedAtomics.VALUE_COMPARE_AND_SWAP_LONG);
    }
}
