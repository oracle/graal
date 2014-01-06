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
import com.oracle.truffle.ruby.runtime.core.array.*;

@CoreClass(name = "Exception")
public abstract class ExceptionNodes {

    @CoreMethod(names = "initialize", minArgs = 0, maxArgs = 1)
    public abstract static class InitializeNode extends CoreMethodNode {

        public InitializeNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public InitializeNode(InitializeNode prev) {
            super(prev);
        }

        @Specialization
        public NilPlaceholder initialize(RubyException exception, @SuppressWarnings("unused") UndefinedPlaceholder message) {
            exception.initialize(getContext().makeString(" "));
            return NilPlaceholder.INSTANCE;
        }

        @Specialization
        public NilPlaceholder initialize(RubyException exception, RubyString message) {
            exception.initialize(message);
            return NilPlaceholder.INSTANCE;
        }

    }

    @CoreMethod(names = "backtrace", needsSelf = false, maxArgs = 0)
    public abstract static class BacktraceNode extends CoreMethodNode {

        public BacktraceNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public BacktraceNode(BacktraceNode prev) {
            super(prev);
        }

        @Specialization
        public RubyArray backtrace() {
            return new RubyArray(getContext().getCoreLibrary().getArrayClass());
        }

    }

    @CoreMethod(names = "message", maxArgs = 0)
    public abstract static class MessageNode extends CoreMethodNode {

        public MessageNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public MessageNode(MessageNode prev) {
            super(prev);
        }

        @Specialization
        public RubyString message(RubyException exception) {
            return exception.getMessage();
        }

    }

}
