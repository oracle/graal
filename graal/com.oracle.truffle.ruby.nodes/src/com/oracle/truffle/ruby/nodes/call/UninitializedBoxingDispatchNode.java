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
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;

/**
 * A node in the dispatch chain that transfers to interpreter and then boxes the receiver.
 */
public class UninitializedBoxingDispatchNode extends UnboxedDispatchNode {

    @Child protected BoxedDispatchNode next;

    public UninitializedBoxingDispatchNode(RubyContext context, SourceSection sourceSection, BoxedDispatchNode next) {
        super(context, sourceSection);

        this.next = adoptChild(next);
    }

    @Override
    public Object dispatch(VirtualFrame frame, Object receiverObject, RubyProc blockObject, Object[] argumentsObjects) {
        CompilerDirectives.transferToInterpreter();

        /*
         * If the next dispatch node is something other than the uninitialized dispatch node then we
         * need to replace this node because it's now on the fast path. If the receiver was already
         * boxed.
         * 
         * Note that with this scheme it will take a couple of calls for the chain to become fully
         * specialized.
         */

        if (next instanceof UninitializedDispatchNode) {
            this.replace(new BoxingDispatchNode(getContext(), getSourceSection(), next));
        }

        return next.dispatch(frame, getContext().getCoreLibrary().box(receiverObject), blockObject, argumentsObjects);
    }

}
