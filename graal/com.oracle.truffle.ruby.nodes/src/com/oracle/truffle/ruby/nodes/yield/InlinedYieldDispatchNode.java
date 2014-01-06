/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.yield;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;

/**
 * Dispatch to a known method which has been inlined.
 */
public class InlinedYieldDispatchNode extends YieldDispatchNode {

    private final RubyRootNode pristineRootNode;

    private final FrameDescriptor frameDescriptor;
    private final RubyRootNode rootNode;

    public InlinedYieldDispatchNode(RubyContext context, SourceSection sourceSection, InlinableMethodImplementation method) {
        super(context, sourceSection);
        pristineRootNode = method.getPristineRootNode();
        frameDescriptor = method.getFrameDescriptor();
        rootNode = method.getCloneOfPristineRootNode();
    }

    @Override
    public Object dispatch(VirtualFrame frame, RubyProc block, Object[] argumentsObjects) {
        /*
         * We're inlining based on the root node used, checking that root node still matches, and
         * then taking the materialized frame from the block we actually got. We can't look for
         * RubyMethod or RubyProc, because they're allocated for each call passing a block.
         */

        if (!(block.getMethod().getImplementation() instanceof InlinableMethodImplementation) ||
                        ((InlinableMethodImplementation) block.getMethod().getImplementation()).getPristineRootNode() != pristineRootNode) {
            CompilerDirectives.transferToInterpreter();

            // TODO(CS): at the moment we just go back to uninit, which may cause loops

            final UninitializedYieldDispatchNode dispatch = new UninitializedYieldDispatchNode(getContext(), getSourceSection());
            replace(dispatch);
            return dispatch.dispatch(frame, block, argumentsObjects);
        }

        final InlinableMethodImplementation implementation = (InlinableMethodImplementation) block.getMethod().getImplementation();

        final RubyArguments arguments = new RubyArguments(implementation.getDeclarationFrame(), block.getSelf(), block.getBlock(), argumentsObjects);
        final VirtualFrame inlinedFrame = Truffle.getRuntime().createVirtualFrame(frame.pack(), arguments, frameDescriptor);
        return rootNode.execute(inlinedFrame);
    }
}
