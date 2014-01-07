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
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.methods.*;

/**
 * A node in the dispatch chain that comes before the boxing point and caches a method on a Java
 * object, matching it by looking at the class and assuming it has not been modified.
 */
public class CachedUnboxedDispatchNode extends UnboxedDispatchNode {

    private final Class expectedClass;
    private final Assumption unmodifiedAssumption;
    private final RubyMethod method;

    @Child protected UnboxedDispatchNode next;

    public CachedUnboxedDispatchNode(RubyContext context, SourceSection sourceSection, Class expectedClass, Assumption unmodifiedAssumption, RubyMethod method, UnboxedDispatchNode next) {
        super(context, sourceSection);

        assert expectedClass != null;
        assert unmodifiedAssumption != null;
        assert method != null;

        this.expectedClass = expectedClass;
        this.unmodifiedAssumption = unmodifiedAssumption;
        this.method = method;
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

        return method.call(frame.pack(), receiverObject, blockObject, argumentsObjects);
    }

    @Override
    public void setNext(UnboxedDispatchNode next) {
        this.next = adoptChild(next);
    }

}
