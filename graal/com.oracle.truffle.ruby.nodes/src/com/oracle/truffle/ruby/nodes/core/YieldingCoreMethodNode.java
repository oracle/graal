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
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.ruby.nodes.yield.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;

public abstract class YieldingCoreMethodNode extends CoreMethodNode {

    @Child protected YieldDispatchNode dispatchNode;

    public YieldingCoreMethodNode(RubyContext context, SourceSection sourceSection) {
        super(context, sourceSection);
        dispatchNode = adoptChild(new UninitializedYieldDispatchNode(context, getSourceSection()));
    }

    public YieldingCoreMethodNode(YieldingCoreMethodNode prev) {
        super(prev);
        dispatchNode = adoptChild(prev.dispatchNode);
    }

    public Object yield(VirtualFrame frame, RubyProc block, Object... arguments) {
        return dispatchNode.dispatch(frame, block, arguments);
    }

    public boolean yieldBoolean(VirtualFrame frame, RubyProc block, Object... arguments) {
        return GeneralConversions.toBoolean(dispatchNode.dispatch(frame, block, arguments));
    }

}
