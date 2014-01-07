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

@CoreClass(name = "Process")
public abstract class ProcessNodes {

    @CoreMethod(names = "pid", isModuleMethod = true, needsSelf = false, maxArgs = 0)
    public abstract static class PidNode extends CoreMethodNode {

        public PidNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public PidNode(PidNode prev) {
            super(prev);
        }

        @Specialization
        public int pid() {
            return getContext().getPOSIX().getpid();
        }

    }

}
