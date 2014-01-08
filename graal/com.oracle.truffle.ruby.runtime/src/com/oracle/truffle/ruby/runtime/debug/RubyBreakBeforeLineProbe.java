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
 * A probe for halting execution at a line before a child execution method.
 */
public final class RubyBreakBeforeLineProbe extends RubyLineProbe {

    /**
     * Creates a probe that will cause a halt just before child execution starts; a {@code oneShot}
     * probe will remove itself the first time it halts.
     */
    public RubyBreakBeforeLineProbe(RubyContext context, SourceLineLocation location, boolean oneShot) {
        super(context, location, oneShot);
    }

    @Override
    public void enter(Node astNode, VirtualFrame frame) {

        if (!isStepping()) {
            // Ordinary line breakpoints ignored during stepping so no double halts.
            if (oneShot) {
                // One-shot breakpoints retire after one activation.
                context.getDebugManager().retireLineProbe(location, this);
            }
            context.getDebugManager().haltedAt(astNode, frame.materialize());
        }
    }
}
