/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.compiler.core.aarch64;

import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Address.AddressingMode;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.aarch64.AArch64AddressValue;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * Represents an AArch64 address in the graph.
 */
@NodeInfo
public class AArch64AddressNode extends AddressNode implements LIRLowerable {

    public static final NodeClass<AArch64AddressNode> TYPE = NodeClass.create(AArch64AddressNode.class);

    @OptionalInput private ValueNode base;

    @OptionalInput private ValueNode index;
    private AArch64Address.AddressingMode addressingMode;

    private final int bitMemoryTransferSize;
    private int displacement;
    private int scaleFactor;

    public AArch64AddressNode(int bitMemoryTransferSize, ValueNode base, ValueNode index) {
        super(TYPE);
        this.bitMemoryTransferSize = bitMemoryTransferSize;
        this.base = base;
        this.index = index;
        this.addressingMode = AddressingMode.REGISTER_OFFSET;
        this.displacement = 0;
        this.scaleFactor = 1;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        assert verify();

        LIRGeneratorTool tool = gen.getLIRGeneratorTool();

        AllocatableValue baseValue = base == null ? Value.ILLEGAL : tool.asAllocatable(gen.operand(base));
        AllocatableValue indexValue = index == null ? Value.ILLEGAL : tool.asAllocatable(gen.operand(index));

        AllocatableValue baseReference = LIRKind.derivedBaseFromValue(baseValue);
        AllocatableValue indexReference;
        if (index == null || LIRKind.isValue(indexValue.getValueKind())) {
            indexReference = null;
        } else {
            indexReference = Value.ILLEGAL;
        }

        LIRKind kind = LIRKind.combineDerived(tool.getLIRKind(stamp(NodeView.DEFAULT)), baseReference, indexReference);
        gen.setResult(this, new AArch64AddressValue(kind, bitMemoryTransferSize, baseValue, indexValue, displacement, scaleFactor, addressingMode));
    }

    @Override
    public boolean verify() {
        assertTrue(bitMemoryTransferSize == AArch64Address.ANY_SIZE || bitMemoryTransferSize == 8 || bitMemoryTransferSize == 16 || bitMemoryTransferSize == 32 || bitMemoryTransferSize == 64 ||
                        bitMemoryTransferSize == 128, "Invalid memory transfer size.");
        switch (addressingMode) {
            case IMMEDIATE_SIGNED_UNSCALED:
                assertTrue(scaleFactor == 1, "Should not have scale factor.");
                assertTrue(index == null, "Immediate address cannot use index register.");
                break;
            case IMMEDIATE_UNSIGNED_SCALED:
                assertTrue(bitMemoryTransferSize / Byte.SIZE == scaleFactor, "Invalid scale factor.");
                assertTrue(index == null, "Immediate address cannot use index register.");
                break;
            case BASE_REGISTER_ONLY:
                assertTrue(scaleFactor == 1, "Should not have scale factor.");
                assertTrue(displacement == 0 && index == null, "Base register only mode cannot have either a displacement or index register.");
                break;
            case REGISTER_OFFSET:
            case EXTENDED_REGISTER_OFFSET:
                assertTrue(scaleFactor == 1 || bitMemoryTransferSize / Byte.SIZE == scaleFactor, "Invalid scale factor.");
                assertTrue(displacement == 0 && index != null, "Register based mode cannot have a displacement.");
                break;
            default:
                fail("Pairwise and post/pre index addressing modes should not be present.");
        }
        return super.verify();
    }

    @Override
    public ValueNode getBase() {
        return base;
    }

    public void setBase(ValueNode base) {
        // allow modification before inserting into the graph
        if (isAlive()) {
            updateUsages(this.base, base);
        }
        this.base = base;
    }

    @Override
    public ValueNode getIndex() {
        return index;
    }

    public void setIndex(ValueNode index) {
        // allow modification before inserting into the graph
        if (isAlive()) {
            updateUsages(this.index, index);
        }
        this.index = index;
    }

    public long getDisplacement() {
        return displacement;
    }

    public void setDisplacement(long displacement, int scaleFactor, AArch64Address.AddressingMode addressingMode) {
        assert scaleFactor == 1 || bitMemoryTransferSize / Byte.SIZE == scaleFactor;
        this.displacement = NumUtil.safeToInt(displacement);
        this.scaleFactor = scaleFactor;
        this.addressingMode = addressingMode;
    }

    @Override
    public long getMaxConstantDisplacement() {
        return displacement;
    }

    public AddressingMode getAddressingMode() {
        return addressingMode;
    }
}
