/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.methods.arguments;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.methods.*;

/**
 * Check arguments meet the arity of the method.
 */
@NodeInfo(shortName = "check-arity")
public class CheckArityNode extends RubyNode {

    private final Arity arity;

    public CheckArityNode(RubyContext context, SourceSection sourceSection, Arity arity) {
        super(context, sourceSection);
        this.arity = arity;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        final RubyArguments arguments = frame.getArguments(RubyArguments.class);
        arity.checkArguments(getContext(), arguments.getArguments());
    }

    @Override
    public Object execute(VirtualFrame frame) {
        executeVoid(frame);
        return NilPlaceholder.INSTANCE;
    }

}
