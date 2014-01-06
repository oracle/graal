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
import com.oracle.truffle.ruby.nodes.cast.*;
import com.oracle.truffle.ruby.nodes.methods.locals.*;
import com.oracle.truffle.ruby.runtime.*;

@NodeInfo(shortName = "flip-flop")
public class FlipFlopNode extends RubyNode {

    @Child protected BooleanCastNode begin;
    @Child protected BooleanCastNode end;
    @Child protected FlipFlopStateNode stateNode;

    private final boolean exclusive;

    public FlipFlopNode(RubyContext context, SourceSection sourceSection, BooleanCastNode begin, BooleanCastNode end, FlipFlopStateNode stateNode, boolean exclusive) {
        super(context, sourceSection);
        this.begin = adoptChild(begin);
        this.end = adoptChild(end);
        this.stateNode = adoptChild(stateNode);
        this.exclusive = exclusive;
    }

    @Override
    public boolean executeBoolean(VirtualFrame frame) {
        if (exclusive) {
            if (stateNode.getState(frame)) {
                if (end.executeBoolean(frame)) {
                    stateNode.setState(frame, false);
                }

                return true;
            } else {
                final boolean newState = begin.executeBoolean(frame);
                stateNode.setState(frame, newState);
                return newState;
            }
        } else {
            if (stateNode.getState(frame)) {
                if (end.executeBoolean(frame)) {
                    stateNode.setState(frame, false);
                }

                return true;
            } else {
                if (begin.executeBoolean(frame)) {
                    stateNode.setState(frame, !end.executeBoolean(frame));
                    return true;
                }

                return false;
            }
        }
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return executeBoolean(frame);
    }

}
