/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved. This
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
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.ruby.runtime.*;

/**
 * A Ruby probe for halting execution at a line after a child execution method completes.
 */
public final class RubyBreakAfterLineProbe extends RubyLineProbe {

    /**
     * Creates a probe that will cause a halt when child execution is complete; a {@code oneShot}
     * probe will remove itself the first time it halts.
     */
    public RubyBreakAfterLineProbe(RubyContext context, SourceLineLocation location, boolean oneShot) {
        super(context, location, oneShot);
    }

    @Override
    public void leave(Node astNode, VirtualFrame frame) {
        if (oneShot) {
            // One-shot breakpoints retire after one activation.
            context.getDebugManager().retireLineProbe(location, this);
        }
        context.getDebugManager().haltedAt(astNode, frame.materialize());
    }

    @Override
    public void leave(Node astNode, VirtualFrame frame, boolean result) {
        leave(astNode, frame);
    }

    @Override
    public void leave(Node astNode, VirtualFrame frame, int result) {
        leave(astNode, frame);
    }

    @Override
    public void leave(Node astNode, VirtualFrame frame, double result) {
        leave(astNode, frame);
    }

    @Override
    public void leave(Node astNode, VirtualFrame frame, Object result) {
        leave(astNode, frame);
    }

    @Override
    public void leaveExceptional(Node astNode, VirtualFrame frame, Exception e) {
        leave(astNode, frame);
    }

}
