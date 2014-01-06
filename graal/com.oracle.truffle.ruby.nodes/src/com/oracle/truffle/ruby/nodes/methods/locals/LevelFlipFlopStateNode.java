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
import com.oracle.truffle.ruby.runtime.*;

public class LevelFlipFlopStateNode extends FlipFlopStateNode {

    private final int level;
    private final FrameSlot frameSlot;

    public LevelFlipFlopStateNode(SourceSection sourceSection, int level, FrameSlot frameSlot) {
        super(sourceSection);
        this.level = level;
        this.frameSlot = frameSlot;
    }

    @Override
    public boolean getState(VirtualFrame frame) {
        final MaterializedFrame levelFrame = RubyArguments.getDeclarationFrame(frame, level);

        try {
            return levelFrame.getBoolean(frameSlot);
        } catch (FrameSlotTypeException e) {
            throw new IllegalStateException();
        }
    }

    @Override
    public void setState(VirtualFrame frame, boolean state) {
        final MaterializedFrame levelFrame = RubyArguments.getDeclarationFrame(frame, level);
        levelFrame.setBoolean(frameSlot, state);
    }

}
