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
        assert frame.getArguments().length > 0;
        assert frame.getArguments()[0] instanceof Thread;
        Thread thread = (Thread) frame.getArguments()[0];
        LLVMPointer tlgBase = allocOrNull(allocTLSection);
        contextThreadLocal.get().addSection(tlgBase, bitcodeID);
        inPlaceNode.execute(frame, thread);
        return null;
    }

    private static LLVMPointer allocOrNull(LLVMAllocateNode allocNode) {
        if (allocNode != null) {
            return allocNode.executeWithTarget();
        } else {
            return null;
        }
    }

    @Override
    public String getName() {
        return "TLS" + '/' + bitcodeID.getId();
    }
}
