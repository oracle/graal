/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.control;

import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.runtime.*;

/**
 * Controls a break from a control structure or method.
 */
public final class BreakException extends ControlFlowException {

    public static final BreakException NIL = new BreakException(NilPlaceholder.INSTANCE);

    private final Object result;

    public BreakException(Object result) {
        assert RubyContext.shouldObjectBeVisible(result);

        this.result = result;
    }

    public Object getResult() {
        return result;
    }

    private static final long serialVersionUID = -8650123232850256133L;

}
