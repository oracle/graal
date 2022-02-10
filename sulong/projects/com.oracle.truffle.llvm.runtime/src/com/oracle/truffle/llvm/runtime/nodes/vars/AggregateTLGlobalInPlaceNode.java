package com.oracle.truffle.llvm.runtime.nodes.vars;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;

public class AggregateTLGlobalInPlaceNode extends RootNode {

    @Child @CompilationFinal private AggregateLiteralInPlaceNode inPlaceNode;

    public AggregateTLGlobalInPlaceNode(LLVMLanguage llvmLanguage, AggregateLiteralInPlaceNode inPlaceNode) {
        super(llvmLanguage);
        this.inPlaceNode = inPlaceNode;
    }

    @Override
    public Object execute(VirtualFrame frame) {
       Thread thread = (Thread) frame.getArguments()[0];
       inPlaceNode.execute(frame, thread);
       return null;
    }
}
