/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.cast;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;

/**
 * Casts a value into a boolean. Works at the language level, so doesn't call any Ruby methods to
 * cast non-core or monkey-patched objects.
 */
@NodeInfo(shortName = "cast-boolean")
@NodeChild(value = "child", type = RubyNode.class)
public abstract class BooleanCastNode extends RubyNode {

    public BooleanCastNode(RubyContext context, SourceSection sourceSection) {
        super(context, sourceSection);
    }

    public BooleanCastNode(BooleanCastNode copy) {
        super(copy.getContext(), copy.getSourceSection());
    }

    @Specialization
    public boolean doBoolean(boolean value) {
        return value;
    }

    @Specialization
    public boolean doNil(@SuppressWarnings("unused") NilPlaceholder nil) {
        return false;
    }

    @Generic
    public boolean doGeneric(Object object) {
        if (object instanceof Boolean) {
            return (boolean) object;
        } else if (object instanceof NilPlaceholder || object instanceof RubyNilClass) {
            return false;
        } else {
            return true;
        }
    }

    @Override
    public abstract boolean executeBoolean(VirtualFrame frame);

}
