/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
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
import com.oracle.truffle.ruby.runtime.*;

/**
 * A probe for invoking a breakpoint shell before a child execution method.
 */
public final class RubyBreakBeforeProbe extends RubyProbe {

    public RubyBreakBeforeProbe(RubyContext context) {
        super(context);
    }

    @Override
    public void enter(Node astNode, VirtualFrame frame) {
        context.getDebugManager().haltedAt(astNode, frame.materialize());
    }

}
