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

import java.math.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;

@NodeInfo(shortName = "object")
public class ObjectLiteralNode extends RubyNode {

    private final Object object;

    public ObjectLiteralNode(RubyContext context, SourceSection sourceSection, Object object) {
        super(context, sourceSection);

        assert RubyContext.shouldObjectBeVisible(object);
        assert !(object instanceof Integer);
        assert !(object instanceof Double);
        assert !(object instanceof BigInteger);
        assert !(object instanceof String);
        assert !(object instanceof RubyString);

        this.object = object;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return object;
    }

}
