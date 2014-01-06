/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.methods.arguments;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;

public class ReadAllArgumentsNode extends RubyNode {

    public ReadAllArgumentsNode(RubyContext context, SourceSection sourceSection) {
        super(context, sourceSection);
    }

    @Override
    public Object[] executeObjectArray(VirtualFrame frame) {
        return frame.getArguments(RubyArguments.class).getArguments();
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return executeObjectArray(frame);
    }

}
