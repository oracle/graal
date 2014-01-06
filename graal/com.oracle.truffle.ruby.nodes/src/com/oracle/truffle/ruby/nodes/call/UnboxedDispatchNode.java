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
import com.oracle.truffle.ruby.runtime.objects.*;

/**
 * A node in the dispatch chain that expects the receiver to be a simple Java object such as a boxed
 * primitive, rather than a full {@link RubyBasicObject}. This allows calls to be made with a
 * receiver such as {@link Integer} without having to turn it into a {@link RubyFixnum}. Followed at
 * some point by an {@link UninitializedBoxingDispatchNode} or {@link BoxingDispatchNode} before we
 * try to dispatch on a Ruby BasicObject or the {@link UninitializedDispatchNode}.
 */
public abstract class UnboxedDispatchNode extends DispatchNode {

    public UnboxedDispatchNode(RubyContext context, SourceSection sourceSection) {
        super(context, sourceSection);
    }

    public abstract Object dispatch(VirtualFrame frame, Object receiverObject, RubyProc blockObject, Object[] argumentsObjects);

    public void setNext(@SuppressWarnings("unused") UnboxedDispatchNode next) {
        throw new UnsupportedOperationException();
    }

}
