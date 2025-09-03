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

package com.oracle.svm.hosted.webimage.wasm.nodes;

import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Counts the number of set bits using the {@code i32.popcnt} or {@code i64.popcnt} instructions.
 * <p>
 * This node is the same as {@link jdk.graal.compiler.replacements.nodes.BitCountNode} but its input
 * kind, matches its result kind.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_1, size = SIZE_1)
public class WasmPopcntNode extends UnaryNode {

    public static final NodeClass<WasmPopcntNode> TYPE = NodeClass.create(WasmPopcntNode.class);

    public WasmPopcntNode(ValueNode value) {
        super(TYPE, computeStamp(value.stamp(NodeView.DEFAULT), value), value);
    }

    @Override
    public Stamp foldStamp(Stamp newStamp) {
        ValueNode theValue = getValue();
        return computeStamp(newStamp, theValue);
    }

    static Stamp computeStamp(Stamp newStamp, ValueNode theValue) {
        assert newStamp.isCompatible(theValue.stamp(NodeView.DEFAULT)) : newStamp + " is not compatible with " + theValue.stamp(NodeView.DEFAULT);
        IntegerStamp valueStamp = (IntegerStamp) newStamp;
        assert (valueStamp.mustBeSet() & CodeUtil.mask(valueStamp.getBits())) == valueStamp.mustBeSet() : valueStamp.mustBeSet();
        assert (valueStamp.mayBeSet() & CodeUtil.mask(valueStamp.getBits())) == valueStamp.mayBeSet() : valueStamp.mayBeSet();
        return StampFactory.forInteger(theValue.getStackKind(), Long.bitCount(valueStamp.mustBeSet()), Long.bitCount(valueStamp.mayBeSet()));
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (forValue.isConstant()) {
            JavaConstant c = forValue.asJavaConstant();
            return forValue.getStackKind() == JavaKind.Int ? ConstantNode.forInt(Integer.bitCount(c.asInt())) : ConstantNode.forLong(Long.bitCount(c.asLong()));
        }
        return this;
    }
}
