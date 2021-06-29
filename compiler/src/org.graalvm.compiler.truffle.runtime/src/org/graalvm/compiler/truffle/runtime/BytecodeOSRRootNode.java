package org.graalvm.compiler.truffle.runtime;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;

final class BytecodeOSRRootNode extends BaseOSRRootNode {
    @Child
    private BytecodeOSRNode bytecodeOSRNode;
    private final int target;

    BytecodeOSRRootNode(BytecodeOSRNode bytecodeOSRNode, int target, TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
        this.bytecodeOSRNode = bytecodeOSRNode;
        this.target = target;
    }

    @Override
    public Object executeOSR(VirtualFrame frame) {
        Frame parentFrame = (Frame) frame.getArguments()[0];
        return bytecodeOSRNode.executeOSR(frame, parentFrame, target);
    }

    @Override
    public String toString() {
        return bytecodeOSRNode.toString() + "<OSR>";
    }
}
