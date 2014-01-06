/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.core;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;

/**
 * A list of expressions to build up into a string.
 */
@NodeInfo(shortName = "interpolated-string")
public final class InterpolatedStringNode extends RubyNode {

    @CompilationFinal private int expectedLength = 64;

    @Children protected final RubyNode[] children;

    public InterpolatedStringNode(RubyContext context, SourceSection sourceSection, RubyNode[] children) {
        super(context, sourceSection);
        this.children = adoptChildren(children);
    }

    @ExplodeLoop
    @Override
    public Object execute(VirtualFrame frame) {
        final StringBuilder builder = new StringBuilder(expectedLength);

        for (int n = 0; n < children.length; n++) {
            builder.append(children[n].execute(frame).toString());
        }

        if (builder.length() > expectedLength) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            expectedLength = builder.length() * 2;
        }

        return getContext().makeString(builder.toString());
    }

    @ExplodeLoop
    @Override
    public void executeVoid(VirtualFrame frame) {
        for (int n = 0; n < children.length; n++) {
            children[n].executeVoid(frame);
        }
    }

}
