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

@CoreClass(name = "Fiber")
public abstract class FiberNodes {

    @CoreMethod(names = "resume", isSplatted = true)
    public abstract static class ResumeNode extends CoreMethodNode {

        public ResumeNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ResumeNode(ResumeNode prev) {
            super(prev);
        }

        @Specialization
        public Object resume(RubyFiber fiberBeingResumed, Object[] args) {
            final RubyFiber sendingFiber = getContext().getFiberManager().getCurrentFiber();

            fiberBeingResumed.resume(sendingFiber, args);

            return sendingFiber.waitForResume();
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
        public NilPlaceholder initialize(RubyFiber fiber, RubyProc block) {
            fiber.initialize(block);
            return NilPlaceholder.INSTANCE;
        }

    }

    @CoreMethod(names = "yield", isModuleMethod = true, needsSelf = false, isSplatted = true)
    public abstract static class YieldNode extends CoreMethodNode {

        public YieldNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public YieldNode(YieldNode prev) {
            super(prev);
        }

        @Specialization
        public Object yield(Object[] args) {
            final RubyFiber yieldingFiber = getContext().getFiberManager().getCurrentFiber();
            final RubyFiber fiberYieldedTo = yieldingFiber.lastResumedByFiber;

            fiberYieldedTo.resume(yieldingFiber, args);

            return yieldingFiber.waitForResume();
        }

    }

}
