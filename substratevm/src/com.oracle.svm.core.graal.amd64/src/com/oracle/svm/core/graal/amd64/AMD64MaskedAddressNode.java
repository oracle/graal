/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.amd64;

import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import com.oracle.svm.core.ReservedRegisters;

import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.amd64.AMD64AddressNode;
import jdk.graal.compiler.core.amd64.AMD64CompressAddressLowering;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.amd64.AMD64AddressValue;
import jdk.graal.compiler.lir.amd64.AMD64LFenceOp;
import jdk.graal.compiler.lir.amd64.AMD64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.ValueAnchorNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * This node is used in the context of the {@link AMD64MemoryMaskingAndFencing} mitigation. It
 * substitutes a normal {@link AMD64AddressNode} and makes sure that memory is accessed safely from
 * a speculative execution point of view. Addresses that reference memory to the heap are logically
 * masked to be on the heap and use the form heapBase + offset. Addresses that do not reference
 * memory on the heap, or whose heap/off-heap nature is unknown, are protected with an LFENCE.
 */
@NodeInfo
public class AMD64MaskedAddressNode extends AMD64AddressNode implements LIRLowerable {

    public static final NodeClass<AMD64MaskedAddressNode> TYPE = NodeClass.create(AMD64MaskedAddressNode.class);

    @Input private ValueNode heapBase;

    /**
     * The mask that will be applied to the index used in a [base + index] memory access. Its value
     * should be (2^x - 1) such that (mask & index) will clear the upper bits only.
     */
    private final long mask;
    private final boolean isOffHeapOrUnknown;

    /*
     * The anchor is needed since we don't have derived references in SVM, thus we need to use
     * unknown references during the generation of this node. To avoid having references alive
     * across safepoints we must use an anchor node to fix the address to its user.
     */
    @Input(InputType.Anchor) private ValueAnchorNode anchorNode;

    public AMD64MaskedAddressNode(ValueNode base, ValueNode compressedIndex, ValueNode heapBase, long mask, int displacement, Stride stride, boolean isOffHeapOrUnknown, ValueAnchorNode anchorNode) {
        super(TYPE, base, compressedIndex, stride);
        this.displacement = displacement;
        this.mask = mask;
        this.heapBase = heapBase;
        this.isOffHeapOrUnknown = isOffHeapOrUnknown;
        this.anchorNode = anchorNode;

        assert (CodeUtil.isPowerOf2(mask + 1) && mask != 0);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();

        boolean isRelativeToHeapBase = (base instanceof AMD64CompressAddressLowering.HeapBaseNode);

        // Getting the base value and reference
        AllocatableValue baseValue = base == null ? Value.ILLEGAL : tool.asAllocatable(gen.operand(base));
        // Getting the index value and reference
        AllocatableValue indexValue = index == null ? Value.ILLEGAL : tool.asAllocatable(gen.operand(index));

        AllocatableValue indexReference;
        if (index == null) {
            indexReference = null;
        } else if (stride.equals(Stride.S1)) {
            indexReference = LIRKind.derivedBaseFromValue(indexValue);
        } else {
            if (LIRKind.isValue(indexValue)) {
                indexReference = null;
            } else {
                indexReference = Value.ILLEGAL;
            }
        }

        LIRKind kind = LIRKind.combineDerived(tool.getLIRKind(stamp(NodeView.DEFAULT)), indexReference, null);

        if (base == null && index == null) {
            gen.setResult(this, new AMD64AddressValue(kind, baseValue, indexValue, stride, displacement));
        } else if (isOffHeapOrUnknown) {
            tool.append(new AMD64LFenceOp());
            gen.setResult(this, new AMD64AddressValue(kind, baseValue, indexValue, stride, displacement, true));
        } else {
            // Creating the space to temporarily store the index before masking.
            AllocatableValue tempIndex = tool.newVariable(LIRKind.unknownReference(AMD64Kind.QWORD));
            AllocatableValue maskedIndex = tool.newVariable(LIRKind.unknownReference(AMD64Kind.QWORD));

            AMD64MaskAddressOp maskedAddress = new AMD64MaskAddressOp(maskedIndex, baseValue, indexValue, tempIndex, displacement, stride, mask, isRelativeToHeapBase);
            tool.append(maskedAddress);

            AllocatableValue r14base;
            if (isRelativeToHeapBase) {
                r14base = tool.asAllocatable(gen.operand(base));
            } else {
                r14base = tool.asAllocatable(gen.operand(heapBase));
            }

            gen.setResult(this, new AMD64AddressValue(kind, r14base, maskedIndex, Stride.S1, 0, true));
        }
    }
}

/**
 * This class emits masking patterns needed to protect memory accesses in the context of
 * {@link AMD64MemoryMaskingAndFencing}. The class can emit two different patterns, depending on the
 * address base. If the base is the isolate heap base, then the emitted pattern is:
 *
 * <pre>
 * lea reg1, [reg2 * stride + disp]     // "uncompress" the index
 * mov reg3, MASK                       // load the mask used to protect the index
 * and reg3, reg1                       // constrain the index based on the heap size
 * </pre>
 *
 * If the base is not the isolate heap base, then we will compute the index to be an offset from the
 * isolate heap base. In this case, the emitted pattern is in the following form:
 *
 * <pre>
 * lea reg1, [reg2 * reg3 * stride + disp] // load the full address
 * sub reg1, heap_base                     // compute the offset from the heap_base
 * mov reg4, MASK                          // load the mask
 * and reg4, reg1                          // constrain the index based on the heap size
 * </pre>
 *
 * The final index can then be used in a memory access: OP reg, [heap_base + masked_index].
 */
@Opcode("AMD64_MASK_ADDRESS")
final class AMD64MaskAddressOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64MaskAddressOp> TYPE = LIRInstructionClass.create(AMD64MaskAddressOp.class);

    @LIRInstruction.Def({REG}) private Value result;
    @LIRInstruction.Use({REG, ILLEGAL}) private Value base;
    @LIRInstruction.Use({REG, ILLEGAL}) private Value index;
    @Temp({REG}) private Value indexTmp;
    private final int displacement;
    private final long mask;
    private final Stride stride;
    private final boolean isRelativeToHeapBase;

    AMD64MaskAddressOp(AllocatableValue result, Value base, Value index, Value indexTmp, int displacement, Stride stride, long mask, boolean isRelativeToHeapBase) {
        super(TYPE);
        this.result = result;
        this.base = base;
        this.displacement = displacement;
        this.index = index;
        this.mask = mask;
        this.stride = stride;
        this.indexTmp = indexTmp;
        this.isRelativeToHeapBase = isRelativeToHeapBase;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {

        if (isRelativeToHeapBase) {
            // This is the case where the address we are trying to mask is already an offset from
            // the heap base.
            masm.leaq(asRegister(indexTmp), new AMD64Address(Register.None, asRegister(index), stride, displacement));
            masm.movq(asRegister(result), mask);
            masm.andq(asRegister(result), asRegister(indexTmp));
        } else {
            Register baseRegister = base.equals(Value.ILLEGAL) ? Register.None : asRegister(base);
            Register indexRegister = index.equals(Value.ILLEGAL) ? Register.None : asRegister(index);

            if (index.equals(Value.ILLEGAL)) {
                indexRegister = baseRegister;
                baseRegister = Register.None;
            }

            masm.leaq(asRegister(indexTmp), new AMD64Address(baseRegister, indexRegister, stride, displacement));
            masm.subq(asRegister(indexTmp), ReservedRegisters.singleton().getHeapBaseRegister());
            masm.movq(asRegister(result), mask);
            masm.andq(asRegister(result), asRegister(indexTmp));
        }

        assert AMD64MemoryMaskingAndFencing.isEnabled() : "masked addresses must only be emitted when memory masking and fencing is enabled";

    }
}
