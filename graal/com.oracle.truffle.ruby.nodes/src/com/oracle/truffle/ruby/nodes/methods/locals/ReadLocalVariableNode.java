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

public abstract class ReadLocalVariableNode extends FrameSlotNode implements ReadNode {

    public ReadLocalVariableNode(RubyContext context, SourceSection sourceSection, FrameSlot slot) {
        super(context, sourceSection, slot);
    }

    public ReadLocalVariableNode(ReadLocalVariableNode prev) {
        this(prev.getContext(), prev.getSourceSection(), prev.frameSlot);
    }

    @Specialization(rewriteOn = {FrameSlotTypeException.class})
    public boolean doBoolean(VirtualFrame frame) throws FrameSlotTypeException {
        return getBoolean(frame);
    }

    @Specialization(rewriteOn = {FrameSlotTypeException.class})
    public int doFixnum(VirtualFrame frame) throws FrameSlotTypeException {
        return getFixnum(frame);
    }

    @Specialization(rewriteOn = {FrameSlotTypeException.class})
    public double doFloat(VirtualFrame frame) throws FrameSlotTypeException {
        return getFloat(frame);
    }

    @Specialization
    public Object doObject(VirtualFrame frame) {
        return getObject(frame);
    }

    @Override
    public RubyNode makeWriteNode(RubyNode rhs) {
        return WriteLocalVariableNodeFactory.create(getContext(), getSourceSection(), frameSlot, rhs);
    }

}
