/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.debug;

import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.runtime.*;

/**
 * A Ruby probe for invoking a breakpoint shell after a child execution method completes.
 */
public final class RubyBreakAfterProbe extends RubyProbe {

    public RubyBreakAfterProbe(RubyContext context) {
        super(context);
    }

    @Override
    public void leave(Node astNode, VirtualFrame frame) {
        context.getDebugManager().haltedAt(astNode, frame.materialize());
    }

    @Override
    public void leave(Node astNode, VirtualFrame frame, boolean result) {
        context.getDebugManager().haltedAt(astNode, frame.materialize());
    }

    @Override
    public void leave(Node astNode, VirtualFrame frame, int result) {
        context.getDebugManager().haltedAt(astNode, frame.materialize());
    }

    @Override
    public void leave(Node astNode, VirtualFrame frame, double result) {
        context.getDebugManager().haltedAt(astNode, frame.materialize());
    }

    @Override
    public void leave(Node astNode, VirtualFrame frame, Object result) {
        context.getDebugManager().haltedAt(astNode, frame.materialize());
    }

    @Override
    public void leaveExceptional(Node astNode, VirtualFrame frame, Exception e) {
        context.getDebugManager().haltedAt(astNode, frame.materialize());
    }

}
