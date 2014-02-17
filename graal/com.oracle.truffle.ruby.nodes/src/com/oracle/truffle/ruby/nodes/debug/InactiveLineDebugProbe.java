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
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.debug.*;

public class InactiveLineDebugProbe extends InactiveEnterDebugProbe {

    private final SourceLineLocation sourceLine;

    public InactiveLineDebugProbe(RubyContext context, SourceLineLocation sourceLine, Assumption inactiveAssumption) {
        super(context, inactiveAssumption);
        this.sourceLine = sourceLine;
    }

    @Override
    protected ActiveEnterDebugProbe createActive() {
        final RubyContext rubyContext = (RubyContext) getContext();
        final RubyDebugManager manager = rubyContext.getRubyDebugManager();
        return new ActiveLineDebugProbe(rubyContext, sourceLine, manager.getAssumption(sourceLine), manager.getBreakpoint(sourceLine));
    }

}
