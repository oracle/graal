/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.call;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.objects.*;

/**
 * A node in the dispatch chain that boxes the receiver into a full Ruby {@link RubyBasicObject}.
 * This node is initially created as an {@link UninitializedBoxingDispatchNode} and only becomes
 * this node when we know that we do need to box on the fast path. Within this node we specialized
 * for the case that the receiver is always already boxed.
 */
public class BoxingDispatchNode extends UnboxedDispatchNode {

    @Child protected BoxedDispatchNode next;

    private final BranchProfile boxBranch = new BranchProfile();

    public BoxingDispatchNode(RubyContext context, SourceSection sourceSection, BoxedDispatchNode next) {
        super(context, sourceSection);

        this.next = adoptChild(next);
    }

    @Override
    public Object dispatch(VirtualFrame frame, Object receiverObject, RubyProc blockObject, Object[] argumentsObjects) {
        RubyBasicObject boxedReceiverObject;

        if (receiverObject instanceof RubyBasicObject) {
            boxedReceiverObject = (RubyBasicObject) receiverObject;
        } else {
            boxBranch.enter();
            boxedReceiverObject = getContext().getCoreLibrary().box(receiverObject);
        }

        return next.dispatch(frame, boxedReceiverObject, blockObject, argumentsObjects);
    }

}
