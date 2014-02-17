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
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;

public abstract class ActiveLeaveDebugProbe extends RubyProbe {

    private final Assumption activeAssumption;

    private final InlinableMethodImplementation inlinable;
    private final RubyRootNode inlinedRoot;

    public ActiveLeaveDebugProbe(RubyContext context, Assumption activeAssumption, RubyProc proc) {
        super(context, false);
        this.activeAssumption = activeAssumption;
        inlinable = ((InlinableMethodImplementation) proc.getMethod().getImplementation());
        inlinedRoot = inlinable.getCloneOfPristineRootNode();
    }

    @Override
    public void leave(Node astNode, VirtualFrame frame, Object result) {
        try {
            activeAssumption.check();
        } catch (InvalidAssumptionException e) {
            replace(createInactive());
            return;
        }

        final RubyArguments arguments = new RubyArguments(inlinable.getDeclarationFrame(), NilPlaceholder.INSTANCE, null, result);
        final VirtualFrame inlinedFrame = Truffle.getRuntime().createVirtualFrame(frame.pack(), arguments, inlinable.getFrameDescriptor());
        inlinedRoot.execute(inlinedFrame);
    }

    protected abstract InactiveLeaveDebugProbe createInactive();

}
