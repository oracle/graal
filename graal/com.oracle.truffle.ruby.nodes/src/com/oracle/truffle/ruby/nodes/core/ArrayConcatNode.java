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
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.array.*;

/**
 * Concatenate arrays.
 */
@NodeInfo(shortName = "array-concat")
public final class ArrayConcatNode extends RubyNode {

    @Children protected final RubyNode[] children;

    public ArrayConcatNode(RubyContext context, SourceSection sourceSection, RubyNode[] children) {
        super(context, sourceSection);
        assert children.length > 1;
        this.children = adoptChildren(children);
    }

    @ExplodeLoop
    @Override
    public Object execute(VirtualFrame frame) {
        final RubyArray array = new RubyArray(getContext().getCoreLibrary().getArrayClass());

        for (int n = 0; n < children.length; n++) {
            final Object childObject = children[n].execute(frame);

            if (childObject instanceof RubyArray) {
                // setRangeArray has special cases for setting a zero-length range at the end
                final int end = array.size();
                array.setRangeArrayExclusive(end, end, (RubyArray) childObject);
            } else {
                array.push(childObject);
            }
        }

        return array;
    }

    @ExplodeLoop
    @Override
    public void executeVoid(VirtualFrame frame) {
        for (int n = 0; n < children.length; n++) {
            children[n].executeVoid(frame);
        }
    }

}
