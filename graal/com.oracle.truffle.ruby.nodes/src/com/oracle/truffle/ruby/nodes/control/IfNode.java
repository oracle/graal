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
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.nodes.cast.*;
import com.oracle.truffle.ruby.runtime.*;

/**
 * Represents a Ruby {@code if} expression. Note that in this representation we always have an
 * {@code else} part.
 */
@NodeInfo(shortName = "if")
public class IfNode extends RubyNode {

    @Child protected BooleanCastNode condition;
    @Child protected RubyNode thenBody;
    @Child protected RubyNode elseBody;

    private final BranchProfile thenProfile = new BranchProfile();
    private final BranchProfile elseProfile = new BranchProfile();

    public IfNode(RubyContext context, SourceSection sourceSection, BooleanCastNode condition, RubyNode thenBody, RubyNode elseBody) {
        super(context, sourceSection);
        this.condition = adoptChild(condition);
        this.thenBody = adoptChild(thenBody);
        this.elseBody = adoptChild(elseBody);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (condition.executeBoolean(frame)) {
            thenProfile.enter();
            return thenBody.execute(frame);
        } else {
            elseProfile.enter();
            return elseBody.execute(frame);
        }
    }

}
