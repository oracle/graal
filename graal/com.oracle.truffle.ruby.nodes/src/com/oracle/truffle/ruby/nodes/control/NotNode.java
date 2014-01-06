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
import com.oracle.truffle.ruby.nodes.cast.*;
import com.oracle.truffle.ruby.runtime.*;

/**
 * Represents a Ruby {@code not} or {@code !} expression.
 */
@NodeInfo(shortName = "not")
public class NotNode extends RubyNode {

    @Child protected BooleanCastNode child;

    public NotNode(RubyContext context, SourceSection sourceSection, BooleanCastNode child) {
        super(context, sourceSection);
        this.child = adoptChild(child);
    }

    @Override
    public boolean executeBoolean(VirtualFrame frame) {
        return !child.executeBoolean(frame);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return executeBoolean(frame);
    }

}
