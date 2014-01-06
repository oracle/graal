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
import com.oracle.truffle.ruby.runtime.methods.*;

/**
 * An unboxed node in the dispatch chain that dispatches if the node is a boolean. In normal unboxed
 * dispatch we look at the Java class of the receiver. However, in Ruby true and false are two
 * separate classes, so in this situation we have to dispatch on the value, as well as the Java
 * class when we are dealing with booleans.
 * <p>
 * TODO(CS): it would be nice if we could {@link RubyNode#executeBoolean} the receiver, but by the
 * time we get to this dispatch node the receiver is already executed.
 */
public class BooleanDispatchNode extends UnboxedDispatchNode {

    private final Assumption falseUnmodifiedAssumption;
    private final RubyMethod falseMethod;

    private final Assumption trueUnmodifiedAssumption;
    private final RubyMethod trueMethod;

    @Child protected UnboxedDispatchNode next;

    public BooleanDispatchNode(RubyContext context, SourceSection sourceSection, Assumption falseUnmodifiedAssumption, RubyMethod falseMethod, Assumption trueUnmodifiedAssumption,
                    RubyMethod trueMethod, UnboxedDispatchNode next) {
        super(context, sourceSection);

        assert falseUnmodifiedAssumption != null;
        assert falseMethod != null;
        assert trueUnmodifiedAssumption != null;
        assert trueMethod != null;

        this.falseUnmodifiedAssumption = falseUnmodifiedAssumption;
        this.falseMethod = falseMethod;

        this.trueUnmodifiedAssumption = trueUnmodifiedAssumption;
        this.trueMethod = trueMethod;

        this.next = adoptChild(next);
    }

    @Override
    public Object dispatch(VirtualFrame frame, Object receiverObject, RubyProc blockObject, Object[] argumentsObjects) {
        // Check it's a boolean

        if (!(receiverObject instanceof Boolean)) {
            return next.dispatch(frame, receiverObject, blockObject, argumentsObjects);
        }

        // Check the value

        Assumption unmodifiedAssumption;
        RubyMethod method;

        if ((boolean) receiverObject) {
            unmodifiedAssumption = trueUnmodifiedAssumption;
            method = trueMethod;
        } else {
            unmodifiedAssumption = falseUnmodifiedAssumption;
            method = falseMethod;
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
