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
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;

/**
 * Read a post-optional argument.
 */
@NodeInfo(shortName = "read-post-optional-argument")
public class ReadPostArgumentNode extends RubyNode {

    private final int indexFromEnd;

    public ReadPostArgumentNode(RubyContext context, SourceSection sourceSection, int indexFromEnd) {
        super(context, sourceSection);
        this.indexFromEnd = indexFromEnd;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final Object[] arguments = frame.getArguments(RubyArguments.class).getArguments();
        final int effectiveIndex = arguments.length - 1 - indexFromEnd;
        assert effectiveIndex < arguments.length;
        return arguments[effectiveIndex];
    }

}
