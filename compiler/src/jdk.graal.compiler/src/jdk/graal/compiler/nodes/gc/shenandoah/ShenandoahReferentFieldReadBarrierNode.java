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

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.lir.gen.ShenandoahBarrierSetLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.gc.ObjectWriteBarrierNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_64;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_64;

/**
 * A special case of the SATB barrier, needed to support soft and weak references. They are added
 * after reads of referents of SoftReference and WeakReference objects, and ensure that such
 * referents are marked live during concurrent marking.
 */
@NodeInfo(cycles = CYCLES_64, size = SIZE_64)
public class ShenandoahReferentFieldReadBarrierNode extends ObjectWriteBarrierNode implements LIRLowerable {
    public static final NodeClass<ShenandoahReferentFieldReadBarrierNode> TYPE = NodeClass.create(ShenandoahReferentFieldReadBarrierNode.class);

    public ShenandoahReferentFieldReadBarrierNode(AddressNode address, ValueNode expectedObject) {
        super(TYPE, address, expectedObject, true);
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
        LIRGeneratorTool lirGen = generator.getLIRGeneratorTool();
        ShenandoahBarrierSetLIRGeneratorTool tool = (ShenandoahBarrierSetLIRGeneratorTool) generator.getLIRGeneratorTool().getBarrierSet();
        tool.emitPreWriteBarrier(lirGen, generator.operand(getAddress()), lirGen.asAllocatable(generator.operand(getExpectedObject())), false);
    }
}
