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

@NodeInfo(shortName = "fixnum-array-literal")
public class FixnumArrayLiteralNode extends ArrayLiteralNode {

    public FixnumArrayLiteralNode(RubyContext context, SourceSection sourceSection, RubyNode[] values) {
        super(context, sourceSection, values);
    }

    @ExplodeLoop
    @Override
    public RubyArray executeArray(VirtualFrame frame) {
        final int[] executedValues = new int[values.length];

        for (int n = 0; n < values.length; n++) {
            try {
                executedValues[n] = values[n].executeFixnum(frame);
            } catch (UnexpectedResultException e) {
                final Object[] executedObjects = new Object[n];

                for (int i = 0; i < n; i++) {
                    executedObjects[i] = executedValues[i];
                }

                return makeGeneric(frame, executedObjects);
            }
        }

        return new RubyArray(getContext().getCoreLibrary().getArrayClass(), new FixnumArrayStore(executedValues));
    }

    @ExplodeLoop
    @Override
    public void executeVoid(VirtualFrame frame) {
        for (int n = 0; n < values.length; n++) {
            values[n].executeVoid(frame);
        }
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return executeArray(frame);
    }

}
