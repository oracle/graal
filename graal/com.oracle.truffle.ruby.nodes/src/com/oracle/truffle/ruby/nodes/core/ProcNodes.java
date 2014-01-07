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
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;

@CoreClass(name = "Proc")
public abstract class ProcNodes {

    @CoreMethod(names = {"call", "[]"}, isSplatted = true)
    public abstract static class CallNode extends CoreMethodNode {

        public CallNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public CallNode(CallNode prev) {
            super(prev);
        }

        @Specialization
        public Object call(VirtualFrame frame, RubyProc proc, Object[] args) {
            return proc.call(frame.getCaller(), args);
        }

    }

    @CoreMethod(names = "initialize", needsBlock = true, maxArgs = 0)
    public abstract static class InitializeNode extends CoreMethodNode {

        public InitializeNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public InitializeNode(InitializeNode prev) {
            super(prev);
        }

        @Specialization
        public NilPlaceholder initialize(VirtualFrame frame, RubyProc proc, RubyProc block) {
            final RubyArguments callerArguments = frame.getCaller().unpack().getArguments(RubyArguments.class);
            proc.initialize(RubyProc.Type.PROC, callerArguments.getSelf(), callerArguments.getBlock(), block.getMethod());
            return NilPlaceholder.INSTANCE;
        }

    }

    @CoreMethod(names = "lambda?", maxArgs = 0)
    public abstract static class LambdaNode extends CoreMethodNode {

        public LambdaNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public LambdaNode(LambdaNode prev) {
            super(prev);
        }

        @Specialization
        public boolean lambda(RubyProc proc) {
            return proc.getType() == RubyProc.Type.LAMBDA;
        }

    }

}
