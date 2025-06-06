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

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.ShenandoahBarrierSetLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.LIRLowerableAccess;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_64;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_64;

/**
 * Shenandoah load-reference barriers. Those are added after reference-loads, and are used to
 * canonicalize references during concurrent evacuation. During concurrent evacuation we might see
 * both from- and to-space references to the same objects, and this barrier ensures that we only see
 * to-space references. (a.k.a. To-space invariant).
 */
@NodeInfo(cycles = CYCLES_64, size = SIZE_64)
public final class ShenandoahLoadRefBarrierNode extends ValueNode implements LIRLowerable {
    public static final NodeClass<ShenandoahLoadRefBarrierNode> TYPE = NodeClass.create(ShenandoahLoadRefBarrierNode.class);

    /**
     * Strength of the input reference, determines generated code and slow-path call.
     */
    public enum ReferenceStrength {
        STRONG,
        WEAK,
        PHANTOM;
    }

    /**
     * The input value. Typically this is a reference that has just been loaded. The barrier output
     * represents the canonicalized reference.
     */
    @Input private ValueNode value;

    /**
     * The address from which the input value has been loaded, if any/known.
     */
    @Input(InputType.Association) private AddressNode address;

    /**
     * The strength of the loaded reference.
     */
    private final ReferenceStrength strength;

    /**
     * Whether the reference is compressed.
     */
    private final boolean narrow;

    private static ReferenceStrength getReferenceStrength(BarrierType barrierType) {
        return switch (barrierType) {
            case READ, FIELD, ARRAY, NONE -> ReferenceStrength.STRONG;
            case REFERENCE_GET, WEAK_REFERS_TO -> ReferenceStrength.WEAK;
            case PHANTOM_REFERS_TO -> ReferenceStrength.PHANTOM;
            case UNKNOWN, POST_INIT_WRITE, AS_NO_KEEPALIVE_WRITE -> throw GraalError.shouldNotReachHere("Unexpected barrier type: " + barrierType);
        };
    }

    public ShenandoahLoadRefBarrierNode(ValueNode value, AddressNode address, BarrierType barrierType, boolean narrow) {
        super(TYPE, value.stamp(NodeView.DEFAULT));
        this.value = value;
        this.address = address;
        this.strength = getReferenceStrength(barrierType);
        this.narrow = narrow;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Stamp valueStamp;
        if (value instanceof LIRLowerableAccess accessValue) {
            valueStamp = accessValue.getAccessStamp(NodeView.DEFAULT);
        } else {
            valueStamp = value.stamp(NodeView.DEFAULT);
        }
        GraalError.guarantee(valueStamp.isObjectStamp(), "LRB value must be object");
        boolean notNull = ((AbstractObjectStamp) valueStamp).nonNull();
        ShenandoahBarrierSetLIRGeneratorTool tool = (ShenandoahBarrierSetLIRGeneratorTool) gen.getLIRGeneratorTool().getBarrierSet();
        gen.setResult(this, tool.emitLoadReferenceBarrier(gen.getLIRGeneratorTool(), gen.operand(value), gen.operand(address), strength, narrow, notNull));
    }
}
