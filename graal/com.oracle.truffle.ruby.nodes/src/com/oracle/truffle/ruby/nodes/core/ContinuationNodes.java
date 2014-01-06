/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.core;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;

@CoreClass(name = "Continuation")
public abstract class ContinuationNodes {

    @CoreMethod(names = "call", isSplatted = true)
    public abstract static class CallNode extends CoreMethodNode {

        public CallNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public CallNode(CallNode prev) {
            super(prev);
        }

        @Specialization
        public Object call(RubyContinuation continuation, Object[] args) {
            continuation.call(args);
            return null;
        }

    }

}
