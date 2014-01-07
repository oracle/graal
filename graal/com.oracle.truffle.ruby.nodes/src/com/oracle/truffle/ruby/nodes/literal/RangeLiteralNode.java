/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.literal;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.range.*;

@NodeInfo(shortName = "range")
@NodeChildren({@NodeChild("begin"), @NodeChild("end")})
public abstract class RangeLiteralNode extends RubyNode {

    private final boolean excludeEnd;

    public RangeLiteralNode(RubyContext context, SourceSection sourceSection, boolean excludeEnd) {
        super(context, sourceSection);
        this.excludeEnd = excludeEnd;
    }

    public RangeLiteralNode(RangeLiteralNode prev) {
        this(prev.getContext(), prev.getSourceSection(), prev.excludeEnd);
    }

    @Specialization
    public FixnumRange doFixnum(int begin, int end) {
        return new FixnumRange(getContext().getCoreLibrary().getRangeClass(), begin, end, excludeEnd);
    }

    @Generic
    public Object doGeneric(Object begin, Object end) {
        final RubyContext context = getContext();

        if ((begin instanceof Integer) && (end instanceof Integer)) {
            return doFixnum((int) begin, (int) end);
        } else {
            return new ObjectRange(context.getCoreLibrary().getRangeClass(), begin, end, excludeEnd);
        }
    }

}
