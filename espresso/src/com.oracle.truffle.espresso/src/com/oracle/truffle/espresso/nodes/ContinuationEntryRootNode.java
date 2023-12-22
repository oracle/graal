package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * This node wraps the {@link BytecodeNode} of a method in such a way that it can unpack a HostFrameRecord into
 * a {@link VirtualFrame} (i.e. the real machine stack when JIT compiled) and then invoke the bytecode method.
 */
public class ContinuationEntryRootNode extends RootNode {
    final BytecodeNode bytecodeNode;

    public ContinuationEntryRootNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, BytecodeNode bytecodeNode) {
        super(language, frameDescriptor);
        this.bytecodeNode = bytecodeNode;
    }

    @Override
    public Object execute(VirtualFrame frame) {

        return null;
    }
}
