/*
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved. This
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
import com.oracle.truffle.ruby.runtime.core.*;

/**
 * A probe for instrumenting a Ruby program with a Ruby procedure to run on the return value from
 * node execution at a line.
 */
public final class RubyProcAfterLineProbe extends RubyLineProbe {

    private final RubyProc proc;

    public RubyProcAfterLineProbe(RubyContext context, SourceLineLocation location, RubyProc proc) {
        super(context, location, false);
        this.proc = proc;
    }

    @Override
    public void leave(Node astNode, VirtualFrame frame) {
        proc.call(frame.pack());
    }

    @Override
    public void leave(Node astNode, VirtualFrame frame, boolean result) {
        proc.call(frame.pack(), result);
    }

    @Override
    public void leave(Node astNode, VirtualFrame frame, int result) {
        proc.call(frame.pack(), result);
    }

    @Override
    public void leave(Node astNode, VirtualFrame frame, double result) {
        proc.call(frame.pack(), result);
    }

    @Override
    public void leave(Node astNode, VirtualFrame frame, Object result) {
        proc.call(frame.pack(), result);
    }

    @Override
    public void leaveExceptional(Node astNode, VirtualFrame frame, Exception e) {
        proc.call(frame.pack());
    }
}
