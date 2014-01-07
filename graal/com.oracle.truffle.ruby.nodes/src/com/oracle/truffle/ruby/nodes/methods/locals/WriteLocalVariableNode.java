/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.methods.locals;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;

@NodeChild(value = "rhs", type = RubyNode.class)
public abstract class WriteLocalVariableNode extends FrameSlotNode implements WriteNode {

    public WriteLocalVariableNode(RubyContext context, SourceSection sourceSection, FrameSlot frameSlot) {
        super(context, sourceSection, frameSlot);
    }

    protected WriteLocalVariableNode(WriteLocalVariableNode prev) {
        this(prev.getContext(), prev.getSourceSection(), prev.frameSlot);
    }

    @Specialization(guards = "isBooleanKind")
    public boolean doFixnum(VirtualFrame frame, boolean value) {
        setBoolean(frame, value);
        return value;
    }

    @Specialization(guards = "isFixnumKind")
    public int doFixnum(VirtualFrame frame, int value) {
        setFixnum(frame, value);
        return value;
    }

    @Specialization(guards = "isFloatKind")
    public double doFloat(VirtualFrame frame, double value) {
        setFloat(frame, value);
        return value;
    }

    @Specialization(guards = "isObjectKind")
    public Object doObject(VirtualFrame frame, Object value) {
        setObject(frame, value);
        return value;
    }

    @Override
    public RubyNode makeReadNode() {
        return ReadLocalVariableNodeFactory.create(getContext(), getSourceSection(), frameSlot);
    }

}
