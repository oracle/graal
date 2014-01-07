/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.literal.array;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.array.*;

@NodeInfo(shortName = "uninit-array-literal")
public class UninitialisedArrayLiteralNode extends ArrayLiteralNode {

    public UninitialisedArrayLiteralNode(RubyContext context, SourceSection sourceSection, RubyNode[] values) {
        super(context, sourceSection, values);
    }

    @ExplodeLoop
    @Override
    public Object execute(VirtualFrame frame) {
        CompilerDirectives.transferToInterpreter();

        final Object[] executedValues = new Object[values.length];

        for (int n = 0; n < values.length; n++) {
            executedValues[n] = values[n].execute(frame);
        }

        final RubyArray array = RubyArray.specializedFromObjects(getContext().getCoreLibrary().getArrayClass(), executedValues);
        final ArrayStore store = array.getArrayStore();

        if (store instanceof FixnumArrayStore) {
            replace(new FixnumArrayLiteralNode(getContext(), getSourceSection(), values));
        } else {
            replace(new ObjectArrayLiteralNode(getContext(), getSourceSection(), values));
        }

        return array;
    }

}
