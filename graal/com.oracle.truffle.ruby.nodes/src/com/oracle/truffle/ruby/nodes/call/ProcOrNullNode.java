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
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;

/**
 * Wraps some node that will produce either a {@link RubyProc} or a {@link NilPlaceholder} and
 * returns {@code null} in case of the latter. Used in parts of the dispatch chain.
 */
@NodeInfo(shortName = "proc-or-null")
@NodeChild(value = "child", type = RubyNode.class)
public abstract class ProcOrNullNode extends RubyNode {

    public ProcOrNullNode(RubyContext context, SourceSection sourceSection) {
        super(context, sourceSection);
    }

    public ProcOrNullNode(ProcOrNullNode prev) {
        super(prev);
    }

    @Specialization
    public Object doNil(@SuppressWarnings("unused") NilPlaceholder nil) {
        return null;
    }

    @Specialization
    public Object doProc(RubyProc proc) {
        return proc;
    }

    @Override
    public RubyProc executeRubyProc(VirtualFrame frame) {
        final Object proc = execute(frame);

        // The standard asRubyProc test doesn't allow for null
        assert proc == null || RubyTypesGen.RUBYTYPES.isRubyProc(proc);

        return (RubyProc) proc;
    }

}
