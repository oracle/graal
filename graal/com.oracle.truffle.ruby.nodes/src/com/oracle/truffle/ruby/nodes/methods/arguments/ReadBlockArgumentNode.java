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
import com.oracle.truffle.ruby.runtime.core.*;

/**
 * Read the block as a {@code Proc}.
 */
@NodeInfo(shortName = "read-block-argument")
public class ReadBlockArgumentNode extends RubyNode {

    private final boolean undefinedIfNotPresent;

    public ReadBlockArgumentNode(RubyContext context, SourceSection sourceSection, boolean undefinedIfNotPresent) {
        super(context, sourceSection);
        this.undefinedIfNotPresent = undefinedIfNotPresent;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final RubyArguments arguments = frame.getArguments(RubyArguments.class);
        final RubyProc block = arguments.getBlock();

        if (block == null) {
            if (undefinedIfNotPresent) {
                return UndefinedPlaceholder.INSTANCE;
            } else {
                return NilPlaceholder.INSTANCE;
            }
        } else {
            return block;
        }
    }

}
