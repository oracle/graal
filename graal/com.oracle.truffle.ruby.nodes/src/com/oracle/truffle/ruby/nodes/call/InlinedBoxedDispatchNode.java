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

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.lookup.*;
import com.oracle.truffle.ruby.runtime.objects.*;

/**
 * A node in the dispatch chain that comes after the boxing point and caches a method on a full
 * boxed {@link RubyBasicObject}, matching it by looking at the lookup node and assuming it has not
 * been modified.
 */
public class InlinedBoxedDispatchNode extends BoxedDispatchNode {

    private final LookupNode expectedLookupNode;
    private final Assumption unmodifiedAssumption;

    private final InlinableMethodImplementation method;
    private final RubyRootNode rootNode;

    @Child protected BoxedDispatchNode next;

    public InlinedBoxedDispatchNode(RubyContext context, SourceSection sourceSection, LookupNode expectedLookupNode, InlinableMethodImplementation method, BoxedDispatchNode next) {
        super(context, sourceSection);

        assert expectedLookupNode != null;
        assert method != null;

        this.expectedLookupNode = expectedLookupNode;
        unmodifiedAssumption = expectedLookupNode.getUnmodifiedAssumption();
        this.method = method;
        this.rootNode = method.getCloneOfPristineRootNode();
        this.next = adoptChild(next);
    }

    @Override
    public Object dispatch(VirtualFrame frame, RubyBasicObject receiverObject, RubyProc blockObject, Object[] argumentsObjects) {
        // Check the lookup node is what we expect

        if (receiverObject.getLookupNode() != expectedLookupNode) {
            return next.dispatch(frame, receiverObject, blockObject, argumentsObjects);
        }

        // Check the class has not been modified

        try {
            unmodifiedAssumption.check();
        } catch (InvalidAssumptionException e) {
            return respecialize("class modified", frame, receiverObject, blockObject, argumentsObjects);
        }

        // Call the method

        Object[] modifiedArgumentsObjects;

        CompilerAsserts.compilationConstant(method.getShouldAppendCallNode());

        if (method.getShouldAppendCallNode()) {
            modifiedArgumentsObjects = Arrays.copyOf(argumentsObjects, argumentsObjects.length + 1);
            modifiedArgumentsObjects[modifiedArgumentsObjects.length - 1] = this;
        } else {
            modifiedArgumentsObjects = argumentsObjects;
        }

        final RubyArguments arguments = new RubyArguments(method.getDeclarationFrame(), receiverObject, blockObject, modifiedArgumentsObjects);
        final VirtualFrame inlinedFrame = Truffle.getRuntime().createVirtualFrame(frame.pack(), arguments, method.getFrameDescriptor());
        return rootNode.execute(inlinedFrame);
    }
}
