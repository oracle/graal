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
 * Controls moving to the next iteration in a control structure or method.
 */
public final class NextException extends ControlFlowException {

    public static final NextException NIL = new NextException(NilPlaceholder.INSTANCE);

    private final Object result;

    public NextException(Object result) {
        assert RubyContext.shouldObjectBeVisible(result);

        this.result = result;
    }

    public Object getResult() {
        return result;
    }

    private static final long serialVersionUID = -302759969186731457L;

}
