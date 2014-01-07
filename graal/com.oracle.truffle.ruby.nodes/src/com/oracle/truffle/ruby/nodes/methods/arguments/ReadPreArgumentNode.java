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
 * Read pre-optional argument.
 */
@NodeInfo(shortName = "read-pre-optional-argument")
public class ReadPreArgumentNode extends RubyNode {

    private final int index;
    private final boolean undefinedIfNotPresent;

    private final BranchProfile notPresentProfile = new BranchProfile();

    public ReadPreArgumentNode(RubyContext context, SourceSection sourceSection, int index, boolean undefinedIfNotPresent) {
        super(context, sourceSection);
        this.index = index;
        this.undefinedIfNotPresent = undefinedIfNotPresent;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final Object[] arguments = frame.getArguments(RubyArguments.class).getArguments();

        if (undefinedIfNotPresent) {
            if (index >= arguments.length) {
                notPresentProfile.enter();
                return UndefinedPlaceholder.INSTANCE;
            }
        } else {
            assert index < arguments.length;
        }

        return arguments[index];
    }

}
