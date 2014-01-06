/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.cast;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.nodes.call.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.objects.*;

/**
 * Casts an object to a Ruby Proc object.
 */
@NodeInfo(shortName = "cast-proc")
@NodeChild("child")
public abstract class ProcCastNode extends RubyNode {

    @Child protected DispatchHeadNode toProc;

    public ProcCastNode(RubyContext context, SourceSection sourceSection) {
        super(context, sourceSection);
        toProc = adoptChild(new DispatchHeadNode(context, getSourceSection(), "to_proc", false));
    }

    public ProcCastNode(ProcCastNode prev) {
        super(prev);
        toProc = adoptChild(prev.toProc);
    }

    @Specialization
    public NilPlaceholder doNil(NilPlaceholder nil) {
        return nil;
    }

    @Specialization
    public RubyProc doRubyProc(RubyProc proc) {
        return proc;
    }

    @Specialization
    public RubyProc doObject(VirtualFrame frame, RubyBasicObject object) {
        return (RubyProc) toProc.dispatch(frame, object, null);
    }

}
