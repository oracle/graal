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
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;

public class InlinedUnboxedDispatchNode extends UnboxedDispatchNode {

    private final Class expectedClass;
    private final Assumption unmodifiedAssumption;

    private final InlinableMethodImplementation method;
    private final RubyRootNode rootNode;

    @Child protected UnboxedDispatchNode next;

    public InlinedUnboxedDispatchNode(RubyContext context, SourceSection sourceSection, Class expectedClass, Assumption unmodifiedAssumption, InlinableMethodImplementation method,
                    UnboxedDispatchNode next) {
        super(context, sourceSection);

        assert expectedClass != null;
        assert method != null;

        this.expectedClass = expectedClass;
        this.unmodifiedAssumption = unmodifiedAssumption;
        this.method = method;
        this.rootNode = method.getCloneOfPristineRootNode();
        this.next = adoptChild(next);
    }

    @Override
    public Object dispatch(VirtualFrame frame, Object receiverObject, RubyProc blockObject, Object[] argumentsObjects) {
        // Check the class is what we expect

        if (receiverObject.getClass() != expectedClass) {
            return next.dispatch(frame, receiverObject, blockObject, argumentsObjects);
        }

        // Check the class has not been modified

        try {
            unmodifiedAssumption.check();
        } catch (InvalidAssumptionException e) {
            return respecialize("class modified", frame, receiverObject, blockObject, argumentsObjects);
        }

        // Call the method

        final RubyArguments arguments = new RubyArguments(method.getDeclarationFrame(), receiverObject, blockObject, argumentsObjects);
        final VirtualFrame inlinedFrame = Truffle.getRuntime().createVirtualFrame(frame.pack(), arguments, method.getFrameDescriptor());
        return rootNode.execute(inlinedFrame);
    }

    @Override
    public void setNext(UnboxedDispatchNode next) {
        this.next = adoptChild(next);
    }

}
