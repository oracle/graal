package com.oracle.truffle.sl.nodes;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.sl.SLLanguage;

public class SLAstRootNode extends SLRootNode {

    @Child private SLExpressionNode bodyNode;

    private final SourceSection sourceSection;
    private final TruffleString name;

    public SLAstRootNode(SLLanguage language, FrameDescriptor frameDescriptor, SLExpressionNode bodyNode, SourceSection sourceSection, TruffleString name) {
        super(language, frameDescriptor);
        this.bodyNode = bodyNode;
        this.sourceSection = sourceSection;
        this.name = name;
    }

    @Override
    public SourceSection getSourceSection() {
        return sourceSection;
    }

    @Override
    public SLExpressionNode getBodyNode() {
        return bodyNode;
    }

    @Override
    public TruffleString getTSName() {
        return name;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return bodyNode.executeGeneric(frame);
    }

}
