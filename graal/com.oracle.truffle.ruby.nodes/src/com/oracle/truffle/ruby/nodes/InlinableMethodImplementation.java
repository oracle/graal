/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.call.*;
import com.oracle.truffle.ruby.runtime.methods.*;

/**
 * A method implementation that also carries the pristine root node and frame descriptor, from which
 * we can inline.
 * 
 * @see InlinedUnboxedDispatchNode
 * @see InlinedBoxedDispatchNode
 * @see InlineHeuristic
 */
public class InlinableMethodImplementation extends CallTargetMethodImplementation {

    private final FrameDescriptor frameDescriptor;
    private final RubyRootNode pristineRootNode;

    public final boolean alwaysInline;
    public final boolean shouldAppendCallNode;

    public InlinableMethodImplementation(CallTarget callTarget, MaterializedFrame declarationFrame, FrameDescriptor frameDescriptor, RubyRootNode pristineRootNode, boolean alwaysInline,
                    boolean shouldAppendCallNode) {
        super(callTarget, declarationFrame);

        assert frameDescriptor != null;
        assert pristineRootNode != null;

        this.frameDescriptor = frameDescriptor;
        this.pristineRootNode = pristineRootNode;
        this.alwaysInline = alwaysInline;
        this.shouldAppendCallNode = shouldAppendCallNode;
    }

    public FrameDescriptor getFrameDescriptor() {
        return frameDescriptor;
    }

    public RubyRootNode getPristineRootNode() {
        return pristineRootNode;
    }

    public RubyRootNode getCloneOfPristineRootNode() {
        return NodeUtil.cloneNode(pristineRootNode);
    }

    public boolean alwaysInline() {
        return alwaysInline;
    }

    public boolean getShouldAppendCallNode() {
        return shouldAppendCallNode;
    }

}
