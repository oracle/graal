/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.control;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;

/**
 * This node has a pair of children - one required and one elidable result. The required node is
 * always executed, but its result is discarded. Therefore it should perform some useful side
 * effects. The elidable node is executed, and its result value returned, if an execute method with
 * a non-void type is used. It is not executed at all if a void typed execute method is used.
 * Therefore it should not perform any observable side effects.
 */
@NodeInfo(shortName = "elidable-result")
public class ElidableResultNode extends RubyNode {

    @Child protected RubyNode required;
    @Child protected RubyNode elidableResult;

    public ElidableResultNode(RubyContext context, SourceSection sourceSection, RubyNode required, RubyNode elidableResult) {
        super(context, sourceSection);
        this.required = adoptChild(required);
        this.elidableResult = adoptChild(elidableResult);
    }

    @Override
    public int executeFixnum(VirtualFrame frame) throws UnexpectedResultException {
        required.executeVoid(frame);
        return elidableResult.executeFixnum(frame);
    }

    @Override
    public double executeFloat(VirtualFrame frame) throws UnexpectedResultException {
        required.executeVoid(frame);
        return elidableResult.executeFloat(frame);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        required.executeVoid(frame);
        return elidableResult.execute(frame);
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        required.execute(frame);
    }

    @Override
    public Object isDefined(VirtualFrame frame) {
        return elidableResult.isDefined(frame);
    }

}
