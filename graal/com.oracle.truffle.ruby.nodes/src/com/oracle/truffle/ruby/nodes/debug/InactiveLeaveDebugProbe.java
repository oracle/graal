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

public abstract class InactiveLeaveDebugProbe extends RubyProbe {

    private final Assumption inactiveAssumption;

    public InactiveLeaveDebugProbe(RubyContext context, Assumption inactiveAssumption) {
        super(context, false);
        this.inactiveAssumption = inactiveAssumption;
    }

    @Override
    public void leave(Node astNode, VirtualFrame frame, Object result) {
        try {
            inactiveAssumption.check();
        } catch (InvalidAssumptionException e) {
            final ActiveLeaveDebugProbe activeNode = createActive();
            replace(activeNode);
            activeNode.leave(astNode, frame, result);
        }
    }

    protected abstract ActiveLeaveDebugProbe createActive();

}
