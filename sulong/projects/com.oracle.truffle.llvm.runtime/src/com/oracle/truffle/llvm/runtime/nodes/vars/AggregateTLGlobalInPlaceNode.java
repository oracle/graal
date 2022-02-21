package com.oracle.truffle.llvm.runtime.nodes.vars;

import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.runtime.IDGenerater.BitcodeID;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMLanguage.LLVMThreadLocalValue;
import com.oracle.truffle.llvm.runtime.memory.LLVMAllocateNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public class AggregateTLGlobalInPlaceNode extends RootNode {

    @Child private AggregateLiteralInPlaceNode inPlaceNode;
    private final ContextThreadLocal<LLVMThreadLocalValue> contextThreadLocal;
    @Child private LLVMAllocateNode allocTLSection;
    private final BitcodeID bitcodeID;

    public AggregateTLGlobalInPlaceNode(LLVMLanguage llvmLanguage, AggregateLiteralInPlaceNode inPlaceNode, LLVMAllocateNode allocTLSection, BitcodeID bitcodeID) {
        super(llvmLanguage);
        this.contextThreadLocal = llvmLanguage.contextThreadLocal;
        this.inPlaceNode = inPlaceNode;
        this.allocTLSection = allocTLSection;
        this.bitcodeID = bitcodeID;
    }

    @Override
    public Object execute(VirtualFrame frame) {
       Thread thread = (Thread) frame.getArguments()[0];
       inPlaceNode.execute(frame, thread);
       LLVMPointer tlgBase = allocOrNull(allocTLSection);
       assert !tlgBase.isNull();
       contextThreadLocal.get().addSection(tlgBase, bitcodeID);
       return null;
    }

    private static LLVMPointer allocOrNull(LLVMAllocateNode allocNode) {
        if (allocNode != null) {
            return allocNode.executeWithTarget();
        } else {
            return null;
        }
    }
}
