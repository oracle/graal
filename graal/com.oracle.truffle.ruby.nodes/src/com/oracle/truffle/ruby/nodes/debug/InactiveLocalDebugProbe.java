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
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.debug.*;

public class InactiveLocalDebugProbe extends InactiveLeaveDebugProbe {

    private final MethodLocal methodLocal;

    public InactiveLocalDebugProbe(RubyContext context, MethodLocal methodLocal, Assumption inactiveAssumption) {
        super(context, inactiveAssumption);
        this.methodLocal = methodLocal;
    }

    @Override
    protected ActiveLeaveDebugProbe createActive() {
        final RubyContext rubyContext = (RubyContext) getContext();
        final RubyDebugManager manager = rubyContext.getRubyDebugManager();
        return new ActiveLocalDebugProbe(rubyContext, methodLocal, manager.getAssumption(methodLocal), manager.getBreakpoint(methodLocal));
    }

}
