/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasmgc.phases;

import com.oracle.svm.hosted.webimage.wasm.phases.SingleThreadedAtomicsPhase;

import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ProfileData;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.java.AbstractCompareAndSwapNode;
import jdk.graal.compiler.nodes.java.LogicCompareAndSwapNode;
import jdk.graal.compiler.nodes.java.ValueCompareAndSwapNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;

public class WasmGCSingleThreadedAtomicsPhase extends SingleThreadedAtomicsPhase {
    /**
     * Replaces the given {@link AbstractCompareAndSwapNode} with a read, comparison, and write with
     * the equivalent semantics, except that it is not atomic.
     * <p>
     * For {@link LogicCompareAndSwapNode}:
     *
     * <pre>{@code
     * oldValue = read(address)
     * if (oldValue == expectedValue) {
     *     write(address, newValue)
     *     cas = 1
     * }
     * cas = 0
     * }</pre>
     *
     * For {@link ValueCompareAndSwapNode}:
     *
     * <pre>{@code
     * oldValue = read(address)
     * if (oldValue == expectedValue) {
     *     write(address, newValue)
     * }
     * cas = oldValue
     * }</pre>
     */
    @Override
    protected void processCAS(CoreProviders providers, AbstractCompareAndSwapNode cas) {
        StructuredGraph graph = cas.graph();
        Stamp memoryStamp = cas.getAccessStamp(NodeView.DEFAULT);
        Stamp valueStamp = cas.stamp(NodeView.DEFAULT);
        boolean isLogic = cas instanceof LogicCompareAndSwapNode;

        FixedWithNextNode oldValue = graph.add(new ReadNode(cas.getAddress(), cas.getLocationIdentity(), memoryStamp, cas.getBarrierType(), cas.getMemoryOrder()));
        AbstractMergeNode merge = graph.add(new MergeNode());
        FixedWithNextNode write = graph.add(new WriteNode(cas.getAddress(), cas.getLocationIdentity(), cas.getNewValue(), cas.getBarrierType(), cas.getMemoryOrder()));

        AbstractBeginNode successBegin = BeginNode.begin(write);
        EndNode successEnd = graph.add(new EndNode());
        write.setNext(successEnd);
        EndNode failEnd = graph.add(new EndNode());
        AbstractBeginNode failBegin = BeginNode.begin(failEnd);

        LogicNode condition = CompareNode.createCompareNode(graph, CanonicalCondition.EQ, oldValue, cas.getExpectedValue(), providers.getConstantReflection(), NodeView.DEFAULT);

        IfNode ifNode = graph.add(new IfNode(condition, successBegin, failBegin, ProfileData.BranchProbabilityData.injected(BranchProbabilityNode.FREQUENT_PROBABILITY)));

        /*
         * The logic variant produces 1 if the swap succeeded and 0 otherwise. The value variant
         * always returns the old value.
         */
        ValueNode finalValue;
        if (isLogic) {
            ValueNode successValue = ConstantNode.forBoolean(true, graph);
            ValueNode failValue = ConstantNode.forBoolean(false, graph);
            finalValue = graph.addOrUnique(new ValuePhiNode(valueStamp, merge, successValue, failValue));
        } else {
            finalValue = oldValue;
        }
        cas.replaceAtUsages(finalValue);

        cas.replaceAtPredecessor(oldValue);
        graph.addAfterFixed(oldValue, ifNode);

        merge.addForwardEnd(successEnd);
        merge.addForwardEnd(failEnd);

        graph.replaceFixedWithFixed(cas, merge);
    }
}
