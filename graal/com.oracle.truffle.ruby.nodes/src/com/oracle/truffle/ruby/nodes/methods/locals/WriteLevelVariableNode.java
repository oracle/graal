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
public abstract class WriteLevelVariableNode extends FrameSlotNode implements WriteNode {

    private final int varLevel;

    public WriteLevelVariableNode(RubyContext context, SourceSection sourceSection, FrameSlot frameSlot, int level) {
        super(context, sourceSection, frameSlot);
        this.varLevel = level;
    }

    protected WriteLevelVariableNode(WriteLevelVariableNode prev) {
        this(prev.getContext(), prev.getSourceSection(), prev.frameSlot, prev.varLevel);
    }

    @Specialization(guards = "isBooleanKind")
    public boolean doBoolean(VirtualFrame frame, boolean value) {
        MaterializedFrame levelFrame = RubyArguments.getDeclarationFrame(frame, varLevel);
        setBoolean(levelFrame, value);
        return value;
    }

    @Specialization(guards = "isFixnumKind")
    public int doFixnum(VirtualFrame frame, int value) {
        MaterializedFrame levelFrame = RubyArguments.getDeclarationFrame(frame, varLevel);
        setFixnum(levelFrame, value);
        return value;
    }

    @Specialization(guards = "isFloatKind")
    public double doFloat(VirtualFrame frame, double value) {
        MaterializedFrame levelFrame = RubyArguments.getDeclarationFrame(frame, varLevel);
        setFloat(levelFrame, value);
        return value;
    }

    @Specialization(guards = "isObjectKind")
    public Object doObject(VirtualFrame frame, Object value) {
        MaterializedFrame levelFrame = RubyArguments.getDeclarationFrame(frame, varLevel);
        setObject(levelFrame, value);
        return value;
    }

    @Override
    public RubyNode makeReadNode() {
        return ReadLevelVariableNodeFactory.create(getContext(), getSourceSection(), frameSlot, varLevel);
    }
}
