/*
 * Copyright (c) 2013, 2014 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.debug;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.subsystems.*;

/**
 * A "trace" probe that has no runtime cost until activated, at which time it invokes a trace
 * message.
 */
public final class RubyTraceProbe extends RubyProbe {

    private final Assumption notTracingAssumption;

    @CompilerDirectives.CompilationFinal private boolean tracingEverEnabled = false;

    public RubyTraceProbe(RubyContext context) {
        super(context, false);
        this.notTracingAssumption = context.getTraceManager().getNotTracingAssumption();
    }

    @Override
    public void enter(Node astNode, VirtualFrame frame) {
        if (!tracingEverEnabled) {
            try {
                notTracingAssumption.check();
            } catch (InvalidAssumptionException e) {
                tracingEverEnabled = true;
            }
        }
        final TraceManager traceManager = context.getTraceManager();
        if (tracingEverEnabled && traceManager.hasTraceProc()) {
            final SourceSection sourceSection = astNode.getEncapsulatingSourceSection();
            traceManager.trace("line", sourceSection.getSource().getName(), sourceSection.getStartLine(), 0, null, null);
        }
    }

}
