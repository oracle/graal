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
import com.oracle.truffle.ruby.runtime.core.*;

@NodeInfo(shortName = "hash")
public class HashLiteralNode extends RubyNode {

    @Children protected final RubyNode[] keys;
    @Children protected final RubyNode[] values;

    public HashLiteralNode(SourceSection sourceSection, RubyNode[] keys, RubyNode[] values, RubyContext context) {
        super(context, sourceSection);
        assert keys.length == values.length;
        this.keys = adoptChildren(keys);
        this.values = adoptChildren(values);
    }

    @ExplodeLoop
    @Override
    public Object execute(VirtualFrame frame) {
        final RubyHash hash = new RubyHash(getContext().getCoreLibrary().getHashClass());

        for (int n = 0; n < keys.length; n++) {
            hash.put(keys[n].execute(frame), values[n].execute(frame));
        }

        return hash;
    }

    @Override
    public Object isDefined(VirtualFrame frame) {
        return getContext().makeString("expression");
    }

}
