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
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;

public abstract class FrameSlotNode extends RubyNode {

    protected final FrameSlot frameSlot;

    protected FrameSlotNode(RubyContext context, SourceSection sourceSection, FrameSlot frameSlot) {
        super(context, sourceSection);
        this.frameSlot = frameSlot;
    }

    public final FrameSlot getFrameSlot() {
        return frameSlot;
    }

    @Override
    public FrameSlotNode copy() {
        return (FrameSlotNode) super.copy();
    }

    protected final void setBoolean(Frame frame, boolean value) {
        frame.setBoolean(frameSlot, value);
    }

    protected final void setFixnum(Frame frame, int value) {
        frame.setInt(frameSlot, value);
    }

    protected final void setFloat(Frame frame, double value) {
        frame.setDouble(frameSlot, value);
    }

    protected final void setObject(Frame frame, Object value) {
        frame.setObject(frameSlot, value);
    }

    protected final boolean getBoolean(Frame frame) throws FrameSlotTypeException {
        return frame.getBoolean(frameSlot);
    }

    protected final int getFixnum(Frame frame) throws FrameSlotTypeException {
        return frame.getInt(frameSlot);
    }

    protected final double getFloat(Frame frame) throws FrameSlotTypeException {
        return frame.getDouble(frameSlot);
    }

    protected final Object getObject(Frame frame) {
        try {
            return frame.getObject(frameSlot);
        } catch (FrameSlotTypeException e) {
            throw new IllegalStateException();
        }
    }

    protected final boolean isBooleanKind() {
        return isKind(FrameSlotKind.Boolean);
    }

    protected final boolean isFixnumKind() {
        return isKind(FrameSlotKind.Int);
    }

    protected final boolean isFloatKind() {
        return isKind(FrameSlotKind.Double);
    }

    protected final boolean isObjectKind() {
        if (frameSlot.getKind() != FrameSlotKind.Object) {
            CompilerDirectives.transferToInterpreter();
            frameSlot.setKind(FrameSlotKind.Object);
        }
        return true;
    }

    private boolean isKind(FrameSlotKind kind) {
        return frameSlot.getKind() == kind || initialSetKind(kind);
    }

    private boolean initialSetKind(FrameSlotKind kind) {
        if (frameSlot.getKind() == FrameSlotKind.Illegal) {
            CompilerDirectives.transferToInterpreter();
            frameSlot.setKind(kind);
            return true;
        }
        return false;
    }

}
