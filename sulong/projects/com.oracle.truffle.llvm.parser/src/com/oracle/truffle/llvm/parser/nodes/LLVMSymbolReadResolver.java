/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.parser.nodes;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.parser.model.symbols.constants.Constant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.NullConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.BigIntegerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.IntegerConstant;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.GetStackSpaceFactory;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.symbols.SSAValue;

public final class LLVMSymbolReadResolver {

    private final boolean storeSSAValueInSlot;
    private final LLVMParserRuntime runtime;
    private final NodeFactory nodeFactory;
    private final FrameDescriptor.Builder builder;
    private final GetStackSpaceFactory getStackSpaceFactory;
    private final DataLayout dataLayout;

    public LLVMSymbolReadResolver(LLVMParserRuntime runtime, FrameDescriptor.Builder builder, GetStackSpaceFactory getStackSpaceFactory, DataLayout dataLayout, boolean storeSSAValueInSlot) {
        this.runtime = runtime;
        this.storeSSAValueInSlot = storeSSAValueInSlot;
        this.nodeFactory = runtime.getNodeFactory();
        this.builder = builder;
        this.getStackSpaceFactory = getStackSpaceFactory;
        this.dataLayout = dataLayout;
    }

    public int findOrAddFrameSlot(SSAValue value) {
        if (SSAValue.isFrameSlotAllocated(value)) {
            return SSAValue.getFrameSlot(value);
        } else {
            Object info = storeSSAValueInSlot ? value : null;
            int slot = builder.addSlot(Type.getFrameSlotKind(value.getType()), null, info);
            SSAValue.allocateFrameSlot(value, slot);
            return slot;
        }
    }

    public static Integer evaluateIntegerConstant(SymbolImpl constant) {
        if (constant instanceof IntegerConstant) {
            assert ((IntegerConstant) constant).getValue() == (int) ((IntegerConstant) constant).getValue();
            return (int) ((IntegerConstant) constant).getValue();
        } else if (constant instanceof BigIntegerConstant) {
            return ((BigIntegerConstant) constant).getValue().intValueExact();
        } else if (constant instanceof NullConstant) {
            return 0;
        } else {
            return null;
        }
    }

    public static Long evaluateLongIntegerConstant(SymbolImpl constant) {
        if (constant instanceof IntegerConstant) {
            return ((IntegerConstant) constant).getValue();
        } else if (constant instanceof BigIntegerConstant) {
            return ((BigIntegerConstant) constant).getValue().longValueExact();
        } else if (constant instanceof NullConstant) {
            return 0L;
        } else {
            return null;
        }
    }

    public interface OptimizedResolver {
        LLVMExpressionNode resolve(SymbolImpl symbol, int excludeOtherIndex, SymbolImpl other, SymbolImpl... others);
    }

    /**
     * Turns a base value and a list of indices into a list of "get element pointer" operations, and
     * allows callers to intercept the resolution of values to nodes (used for frame slot
     * optimization in LLVMBitcodeInstructionVisitor).
     */
    public LLVMExpressionNode resolveElementPointer(SymbolImpl base, SymbolImpl[] indices, OptimizedResolver resolver) {
        LLVMExpressionNode[] indexNodes = new LLVMExpressionNode[indices.length];
        Long[] indexConstants = new Long[indices.length];
        Type[] indexTypes = new Type[indices.length];

        for (int i = indices.length - 1; i >= 0; i--) {
            SymbolImpl indexSymbol = indices[i];
            indexConstants[i] = evaluateLongIntegerConstant(indexSymbol);
            indexTypes[i] = indexSymbol.getType();
            if (indexConstants[i] == null) {
                indexNodes[i] = resolver.resolve(indexSymbol, i, base, indices);
            }
        }

        LLVMExpressionNode currentAddress = resolver.resolve(base, -1, null, indices);
        Type currentType = base.getType();

        return CommonNodeFactory.createNestedElementPointerNode(nodeFactory, dataLayout, indexNodes, indexConstants, indexTypes, currentAddress, currentType);
    }

    public LLVMExpressionNode resolve(SymbolImpl symbol) {
        if (symbol == null) {
            return null;
        }
        if (symbol instanceof Constant) {
            return ((Constant) symbol).createNode(runtime, dataLayout, getStackSpaceFactory);
        } else if (symbol instanceof SSAValue) {
            SSAValue value = (SSAValue) symbol;
            int slot = findOrAddFrameSlot(value);
            return CommonNodeFactory.createFrameRead(value.getType(), slot);
        } else {
            throw new LLVMParserException("Cannot resolve symbol: " + symbol);
        }
    }
}
