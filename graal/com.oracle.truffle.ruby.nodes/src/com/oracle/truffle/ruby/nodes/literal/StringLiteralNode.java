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
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;

@NodeInfo(shortName = "string")
public class StringLiteralNode extends RubyNode {

    private final String string;

    public StringLiteralNode(RubyContext context, SourceSection sourceSection, String string) {
        super(context, sourceSection);

        assert string != null;

        this.string = string;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return getContext().makeString(string);
    }

}
