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
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;

/**
 * Read an optional argument.
 */
@NodeInfo(shortName = "read-optional-argument")
public class ReadOptionalArgumentNode extends RubyNode {

    private final int index;
    private final int minimum;
    @Child protected RubyNode defaultValue;

    private final BranchProfile defaultValueProfile = new BranchProfile();

    public ReadOptionalArgumentNode(RubyContext context, SourceSection sourceSection, int index, int minimum, RubyNode defaultValue) {
        super(context, sourceSection);
        this.index = index;
        this.minimum = minimum;
        this.defaultValue = adoptChild(defaultValue);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final Object[] arguments = frame.getArguments(RubyArguments.class).getArguments();

        if (arguments.length < minimum) {
            defaultValueProfile.enter();
            return defaultValue.execute(frame);
        } else {
            assert index < arguments.length;
            return arguments[index];
        }
    }

}
