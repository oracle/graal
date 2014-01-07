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
import com.oracle.truffle.ruby.runtime.control.*;

/**
 * Represents an explicit return. The return ID indicates where we should be returning to - this can
 * be non-trivial if you have blocks.
 */
@NodeInfo(shortName = "return")
public class ReturnNode extends RubyNode {

    private final long returnID;
    @Child protected RubyNode value;

    public ReturnNode(RubyContext context, SourceSection sourceSection, long returnID, RubyNode value) {
        super(context, sourceSection);
        this.returnID = returnID;
        this.value = adoptChild(value);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        throw new ReturnException(returnID, value.execute(frame));
    }
}
