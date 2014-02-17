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
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.debug.*;

public class ActiveLineDebugProbe extends ActiveEnterDebugProbe {

    private final SourceLineLocation sourceLine;

    public ActiveLineDebugProbe(RubyContext context, SourceLineLocation sourceLine, Assumption activeAssumption, RubyProc proc) {
        super(context, activeAssumption, proc);
        this.sourceLine = sourceLine;
    }

    @Override
    protected InactiveEnterDebugProbe createInactive() {
        final RubyContext rubyContext = (RubyContext) getContext();
        final RubyDebugManager manager = rubyContext.getRubyDebugManager();
        return new InactiveLineDebugProbe(rubyContext, sourceLine, manager.getAssumption(sourceLine));
    }

}
