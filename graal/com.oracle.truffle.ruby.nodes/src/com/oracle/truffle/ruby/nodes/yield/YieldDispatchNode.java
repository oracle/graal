/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.yield;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;

/**
 * A node in the yield dispatch chain.
 */
public abstract class YieldDispatchNode extends Node {

    private final RubyContext context;

    public YieldDispatchNode(RubyContext context, SourceSection sourceSection) {
        super(sourceSection);

        assert context != null;
        assert sourceSection != null;

        this.context = context;
    }

    public abstract Object dispatch(VirtualFrame frame, RubyProc block, Object[] argumentsObjects);

    public RubyContext getContext() {
        return context;
    }

}
