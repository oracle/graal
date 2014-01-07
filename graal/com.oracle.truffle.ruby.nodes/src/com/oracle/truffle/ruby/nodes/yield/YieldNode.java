/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.yield;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;

/**
 * Yield to the current block.
 */
@NodeInfo(shortName = "yield")
public class YieldNode extends RubyNode {

    @Children protected final RubyNode[] arguments;
    @Child protected YieldDispatchNode dispatch;

    public YieldNode(RubyContext context, SourceSection sourceSection, RubyNode[] arguments) {
        super(context, sourceSection);
        this.arguments = adoptChildren(arguments);
        dispatch = adoptChild(new UninitializedYieldDispatchNode(getContext(), getSourceSection()));
    }

    @ExplodeLoop
    @Override
    public final Object execute(VirtualFrame frame) {
        final Object[] argumentsObjects = new Object[arguments.length];

        for (int i = 0; i < arguments.length; i++) {
            argumentsObjects[i] = arguments[i].execute(frame);
        }

        final RubyProc block = frame.getArguments(RubyArguments.class).getBlock();

        if (block == null) {
            CompilerDirectives.transferToInterpreter();
            // TODO(CS): convert to the proper Ruby exception
            throw new RuntimeException("No block to yield to");
        }

        return dispatch.dispatch(frame, block, argumentsObjects);
    }

    @Override
    public Object isDefined(VirtualFrame frame) {
        final RubyArguments args = frame.getArguments(RubyArguments.class);

        if (args.getBlock() == null) {
            return NilPlaceholder.INSTANCE;
        } else {
            return getContext().makeString("yield");
        }
    }

}
