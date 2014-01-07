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
 * Controls throwing a value. Note that throwing is different to raising in Ruby, which is the
 * reason we have both {@link ThrowException} and {@link RaiseException}.
 */
public class ThrowException extends ControlFlowException {

    private final Object tag;
    private final Object value;

    public ThrowException(Object tag, Object value) {
        assert tag != null;
        assert RubyContext.shouldObjectBeVisible(value);

        this.tag = tag;
        this.value = value;
    }

    public Object getTag() {
        return tag;
    }

    public Object getValue() {
        return value;
    }

    private static final long serialVersionUID = 8693305627979840677L;

}
