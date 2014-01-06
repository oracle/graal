/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.call;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.methods.*;
import com.oracle.truffle.ruby.runtime.objects.*;

/**
 * A node in the dispatch chain that does no caching and looks up methods from scratch each time it
 * is called.
 */
public class GeneralBoxedDispatchNode extends BoxedDispatchNode {

    private final String name;

    public GeneralBoxedDispatchNode(RubyContext context, SourceSection sourceSection, String name) {
        super(context, sourceSection);

        assert name != null;

        this.name = name;
    }

    @Override
    public Object dispatch(VirtualFrame frame, RubyBasicObject receiverObject, RubyProc blockObject, Object[] argumentsObjects) {
        /*
         * TODO(CS): we should probably have some kind of cache here - even if it's just a hash map.
         * MRI and JRuby do and might avoid some pathological cases.
         */

        final RubyMethod method = lookup(frame, receiverObject, name);
        return method.call(frame.pack(), receiverObject, blockObject, argumentsObjects);
    }

}
