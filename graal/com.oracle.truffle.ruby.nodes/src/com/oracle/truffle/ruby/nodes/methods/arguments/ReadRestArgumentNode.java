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

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.core.array.*;

/**
 * Read the rest of arguments after a certain point into an array.
 */
@NodeInfo(shortName = "read-rest-of-arguments")
public class ReadRestArgumentNode extends RubyNode {

    private final int index;

    public ReadRestArgumentNode(RubyContext context, SourceSection sourceSection, int index) {
        super(context, sourceSection);
        this.index = index;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final RubyArguments rubyArguments = frame.getArguments(RubyArguments.class);

        final Object[] arguments = rubyArguments.getArguments();

        final RubyClass arrayClass = getContext().getCoreLibrary().getArrayClass();

        if (arguments.length <= index) {
            return new RubyArray(arrayClass);
        } else if (index == 0) {
            return new RubyArray(arrayClass, new ObjectArrayStore(arguments));
        } else {
            return new RubyArray(arrayClass, new ObjectArrayStore(Arrays.copyOfRange(arguments, index, arguments.length)));
        }
    }
}
