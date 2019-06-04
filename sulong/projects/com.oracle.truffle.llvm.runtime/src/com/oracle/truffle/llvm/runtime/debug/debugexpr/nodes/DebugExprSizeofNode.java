package com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.Parser;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.Type;

public class DebugExprSizeofNode extends LLVMExpressionNode {

    private Type type;

    public DebugExprSizeofNode(Type type) {
        this.type = type;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        if (type != null)
            return type.getBitSize() / 4;
        return Parser.errorObjNode.executeGeneric(frame);
    }

}
