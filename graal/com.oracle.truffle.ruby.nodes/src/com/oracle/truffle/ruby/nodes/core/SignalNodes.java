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

@CoreClass(name = "Signal")
public abstract class SignalNodes {

    @CoreMethod(names = "trap", isModuleMethod = true, needsSelf = false, minArgs = 1, maxArgs = 1)
    public abstract static class SignalNode extends CoreMethodNode {

        public SignalNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public SignalNode(SignalNode prev) {
            super(prev);
        }

        @Specialization
        public NilPlaceholder trap(@SuppressWarnings("unused") Object signal) {
            getContext().implementationMessage("Signal#trap doesn't do anything");
            return NilPlaceholder.INSTANCE;
        }

    }

}
