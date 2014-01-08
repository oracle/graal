/*
 * Copyright (c) 2013, 2014 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.literal;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;

@NodeInfo(shortName = "fixnum")
public class FixnumLiteralNode extends RubyNode {

    private final int value;

    public FixnumLiteralNode(RubyContext context, SourceSection sourceSection, int value) {
        super(context, sourceSection);
        this.value = value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return executeFixnum(frame);
    }

    @Override
    public int executeFixnum(VirtualFrame frame) {
        return value;
    }

    public int getValue() {
        return value;
    }

}
