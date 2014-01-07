/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.control;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;

/**
 * Represents a Ruby {@code and} or {@code &&} expression.
 */
@NodeInfo(shortName = "and")
@NodeChildren({@NodeChild("left"), @NodeChild("right")})
public abstract class AndNode extends RubyNode {

    public AndNode(RubyContext context, SourceSection sourceSection) {
        super(context, sourceSection);
    }

    public AndNode(AndNode copy) {
        super(copy.getContext(), copy.getSourceSection());
    }

    @ShortCircuit("right")
    public boolean needsRightNode(Object a) {
        return GeneralConversions.toBoolean(a);
    }

    @ShortCircuit("right")
    public boolean needsRightNode(boolean a) {
        return a;
    }

    @Specialization
    public boolean doBoolean(boolean a, boolean hasB, boolean b) {
        return hasB ? b : a;
    }

    @Specialization
    public Object doObject(boolean a, boolean hasB, Object b) {
        return hasB ? b : a;
    }

    @Generic
    public Object doGeneric(Object a, boolean hasB, Object b) {
        return hasB ? b : a;
    }
}
