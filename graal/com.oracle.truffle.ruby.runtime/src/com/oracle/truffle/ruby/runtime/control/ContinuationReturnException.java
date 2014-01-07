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
import com.oracle.truffle.ruby.runtime.core.*;

/**
 * Controls return from a continuation.
 */
public final class ContinuationReturnException extends ControlFlowException {

    private final RubyContinuation continuation;
    private final Object value;

    public ContinuationReturnException(RubyContinuation continuation, Object value) {
        assert continuation != null;
        assert RubyContext.shouldObjectBeVisible(value);

        this.continuation = continuation;
        this.value = value;
    }

    /**
     * Get the continuation that caused this.
     */
    public RubyContinuation getContinuation() {
        return continuation;
    }

    /**
     * Get the value that has been returned.
     */
    public Object getValue() {
        return value;
    }

    private static final long serialVersionUID = 6215834704293311504L;

}
