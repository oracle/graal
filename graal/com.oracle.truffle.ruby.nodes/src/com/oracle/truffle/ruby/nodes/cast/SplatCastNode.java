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
import com.oracle.truffle.ruby.runtime.core.array.*;

/**
 * Splat as used to cast a value to an array if it isn't already, as in {@code *value}.
 */
@NodeInfo(shortName = "cast-splat")
@NodeChild("child")
public abstract class SplatCastNode extends RubyNode {

    public SplatCastNode(RubyContext context, SourceSection sourceSection) {
        super(context, sourceSection);
    }

    public SplatCastNode(SplatCastNode prev) {
        super(prev);
    }

    protected abstract RubyNode getChild();

    @Specialization
    public RubyArray doArray(RubyArray array) {
        return array;
    }

    @Specialization
    public RubyArray doObject(Object object) {
        if (object instanceof RubyArray) {
            return (RubyArray) object;
        } else {
            return RubyArray.specializedFromObject(getContext().getCoreLibrary().getArrayClass(), object);
        }
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        getChild().executeVoid(frame);
    }

}
