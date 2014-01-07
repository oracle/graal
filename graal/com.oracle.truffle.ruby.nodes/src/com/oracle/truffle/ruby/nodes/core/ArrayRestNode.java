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
 * Take the rest of values in an array after an index, without using any method lookup. This isn't a
 * call - it's an operation on a core class.
 */
@NodeInfo(shortName = "array-rest")
public final class ArrayRestNode extends RubyNode {

    final int begin;
    @Child protected RubyNode array;

    public ArrayRestNode(RubyContext context, SourceSection sourceSection, int begin, RubyNode array) {
        super(context, sourceSection);
        this.begin = begin;
        this.array = adoptChild(array);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final RubyArray arrayObject = (RubyArray) array.execute(frame);
        return arrayObject.getRangeExclusive(begin, arrayObject.size());
    }

}
