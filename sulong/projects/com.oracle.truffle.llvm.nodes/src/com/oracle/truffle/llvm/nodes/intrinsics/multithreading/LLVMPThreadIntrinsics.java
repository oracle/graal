package com.oracle.truffle.llvm.nodes.intrinsics.multithreading;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMBuiltin;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public class LLVMPThreadIntrinsics {
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadMyTest extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object arg) {
            return 35; // just to test return 35
        }
    }
}
