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
import com.oracle.truffle.ruby.nodes.literal.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.control.*;

@NodeInfo(shortName = "next")
public class NextNode extends RubyNode {

    @Child private RubyNode child;

    public NextNode(RubyContext context, SourceSection sourceSection, RubyNode child) {
        super(context, sourceSection);

        this.child = adoptChild(child);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (child instanceof NilNode) {
            throw NextException.NIL;
        } else {
            throw new NextException(child.execute(frame));
        }
    }
}
