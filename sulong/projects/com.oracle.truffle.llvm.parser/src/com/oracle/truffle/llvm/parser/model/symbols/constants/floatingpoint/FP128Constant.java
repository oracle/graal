package com.oracle.truffle.llvm.parser.model.symbols.constants.floatingpoint;

import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.model.visitors.SymbolVisitor;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.GetStackSpaceFactory;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;

import java.util.Arrays;

public final class FP128Constant extends FloatingPointConstant {

    private final byte[] value;

    FP128Constant(byte[] value) {
        super(PrimitiveType.PPC_FP128);
        this.value = value;
    }

    @Override
    public void accept(SymbolVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return Arrays.toString(value);
    }

    private static final int HEX_MASK = 0xf;

    private static final int BYTE_MSB_SHIFT = 6;

    @Override
    public String getStringValue() {
        final StringBuilder builder = new StringBuilder("");
        for (byte aValue : value) {
            builder.append(String.format("%x%x", (aValue >>> BYTE_MSB_SHIFT) &
                            HEX_MASK, aValue & HEX_MASK));
        }
        return builder.toString();
    }

    @Override
    public LLVMExpressionNode createNode(LLVMParserRuntime runtime, DataLayout dataLayout,
                    GetStackSpaceFactory stackFactory) {
        return CommonNodeFactory.createSimpleConstantNoArray(value,
                        getType());
    }
}
