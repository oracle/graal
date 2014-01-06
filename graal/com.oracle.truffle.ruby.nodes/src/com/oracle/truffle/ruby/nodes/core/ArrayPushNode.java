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
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.array.*;

public class ArrayPushNode extends RubyNode {

    @Child protected RubyNode array;
    @Child protected RubyNode pushed;

    public ArrayPushNode(RubyContext context, SourceSection sourceSection, RubyNode array, RubyNode pushed) {
        super(context, sourceSection);
        this.array = adoptChild(array);
        this.pushed = adoptChild(pushed);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        RubyArray a = (RubyArray) array.execute(frame);
        a = (RubyArray) a.dup();
        a.push(pushed.execute(frame));
        return a;
    }

}
