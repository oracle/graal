/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.parser;

import com.oracle.truffle.api.debug.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.instrument.*;
import com.oracle.truffle.ruby.runtime.*;

/**
 * Minimum possible debugging support for Ruby implementation. Some facilities are needed to support
 * the Ruby trace mechanism.
 */
public final class MinimalRubyDebugContext implements DebugContext {

    private final RubyContext executionContext;
    private final RubyNodeInstrumenter instrumenter;
    private final DebugManager debugManager;

    public MinimalRubyDebugContext(RubyContext context) {
        this.executionContext = context;
        this.instrumenter = new DefaultRubyNodeInstrumenter();
        this.debugManager = new DefaultDebugManager(context);
    }

    public RubyContext getContext() {
        return executionContext;
    }

    public RubyNodeInstrumenter getNodeInstrumenter() {
        return instrumenter;
    }

    public DebugManager getDebugManager() {
        return debugManager;
    }

    public RubyASTPrinter getASTPrinter() {
        return null;
    }

    public String displayValue(Object value) {
        return value.toString();
    }

    public String displayIdentifier(FrameSlot slot) {
        return slot.getIdentifier().toString();
    }

    public void executionHalted(Node node, VirtualFrame frame) {
        assert false;
    }

}
