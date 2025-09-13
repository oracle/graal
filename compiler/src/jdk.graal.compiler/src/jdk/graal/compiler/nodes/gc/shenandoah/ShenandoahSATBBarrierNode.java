/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.gc.shenandoah;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_64;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_64;

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.lir.gen.ShenandoahBarrierSetLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.gc.ObjectWriteBarrierNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * Shenandoah SATB barrier. Supports concurrent marking, by implementing the so-called
 * snapshot-at-the-beginning (SATB). The barrier ensures that we see a consistent and complete
 * marking bitmap after concurrent marking, that has at least all objects marked live that have been
 * live at the beginning of marking (hence the name). This barrier is very similar to G1's
 * pre-write-barrier.
 */
@NodeInfo(cycles = CYCLES_64, size = SIZE_64)
public final class ShenandoahSATBBarrierNode extends ObjectWriteBarrierNode implements LIRLowerable {
    public static final NodeClass<ShenandoahSATBBarrierNode> TYPE = NodeClass.create(ShenandoahSATBBarrierNode.class);

    /**
     * Whether the reference is compressed.
     */
    private final boolean narrow;

    public ShenandoahSATBBarrierNode(AddressNode address, ValueNode expectedObject, boolean narrow) {
        super(TYPE, address, expectedObject, true);
        this.narrow = narrow;
    }

    public ValueNode getExpectedObject() {
        return getValue();
    }

    @Override
    public Kind getKind() {
        return Kind.PRE_BARRIER;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        ValueNode expectedObject = getExpectedObject();
        if (expectedObject == null || !expectedObject.isJavaConstant() || !expectedObject.asJavaConstant().isNull()) {
            AllocatableValue operand = Value.ILLEGAL;
            boolean nonNull = false;
            LIRGeneratorTool lirGen = generator.getLIRGeneratorTool();
            if (expectedObject != null) {
                operand = lirGen.asAllocatable(generator.operand(expectedObject));
                nonNull = ((ObjectStamp) expectedObject.stamp(NodeView.DEFAULT)).nonNull();
                GraalError.guarantee(expectedObject.stamp(NodeView.DEFAULT) instanceof ObjectStamp, "expecting full size object");
            }
            ShenandoahBarrierSetLIRGeneratorTool tool = (ShenandoahBarrierSetLIRGeneratorTool) generator.getLIRGeneratorTool().getBarrierSet();
            tool.emitPreWriteBarrier(lirGen, generator.operand(getAddress()), operand, narrow, nonNull);
        }
    }
}
