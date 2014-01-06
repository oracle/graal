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

public abstract class ReadLevelVariableNode extends FrameSlotNode implements ReadNode {

    private final int varLevel;

    public ReadLevelVariableNode(RubyContext context, SourceSection sourceSection, FrameSlot slot, int level) {
        super(context, sourceSection, slot);
        this.varLevel = level;
    }

    public ReadLevelVariableNode(ReadLevelVariableNode prev) {
        this(prev.getContext(), prev.getSourceSection(), prev.frameSlot, prev.varLevel);
    }

    @Specialization(rewriteOn = {FrameSlotTypeException.class})
    public boolean doBoolean(VirtualFrame frame) throws FrameSlotTypeException {
        MaterializedFrame levelFrame = RubyArguments.getDeclarationFrame(frame, varLevel);
        return getBoolean(levelFrame);
    }

    @Specialization(rewriteOn = {FrameSlotTypeException.class})
    public int doFixnum(VirtualFrame frame) throws FrameSlotTypeException {
        MaterializedFrame levelFrame = RubyArguments.getDeclarationFrame(frame, varLevel);
        return getFixnum(levelFrame);
    }

    @Specialization(rewriteOn = {FrameSlotTypeException.class})
    public double doFloat(VirtualFrame frame) throws FrameSlotTypeException {
        MaterializedFrame levelFrame = RubyArguments.getDeclarationFrame(frame, varLevel);
        return getFloat(levelFrame);
    }

    @Specialization
    public Object doObject(VirtualFrame frame) {
        MaterializedFrame levelFrame = RubyArguments.getDeclarationFrame(frame, varLevel);
        return getObject(levelFrame);
    }

    public int getVarLevel() {
        return varLevel;
    }

    @Override
    public RubyNode makeWriteNode(RubyNode rhs) {
        return WriteLevelVariableNodeFactory.create(getContext(), getSourceSection(), frameSlot, varLevel, rhs);
    }

}
