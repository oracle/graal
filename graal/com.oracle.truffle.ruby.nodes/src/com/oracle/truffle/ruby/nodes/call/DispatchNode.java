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
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.control.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.methods.*;
import com.oracle.truffle.ruby.runtime.objects.*;

/**
 * Any node in the dispatch chain.
 */
public class DispatchNode extends Node {

    private final RubyContext context;

    public DispatchNode(RubyContext context, SourceSection sourceSection) {
        super(sourceSection);

        assert context != null;
        assert sourceSection != null;

        this.context = context;
    }

    /**
     * Get the depth of this node in the dispatch chain. The first node below
     * {@link DispatchHeadNode} is at depth 1.
     */
    public int getDepth() {
        int depth = 1;
        Node parent = this.getParent();

        while (!(parent instanceof DispatchHeadNode)) {
            parent = parent.getParent();
            depth++;
        }

        return depth;
    }

    public Object respecialize(String reason, VirtualFrame frame, Object receiverObject, RubyProc blockObject, Object... argumentsObjects) {
        CompilerAsserts.neverPartOfCompilation();

        final int depth = getDepth();
        final DispatchHeadNode head = (DispatchHeadNode) NodeUtil.getNthParent(this, depth);

        return head.respecialize(frame, reason, receiverObject, blockObject, argumentsObjects);
    }

    /**
     * The central point for method lookup.
     */
    protected RubyMethod lookup(VirtualFrame frame, RubyBasicObject receiverBasicObject, String name) {
        final RubyMethod method = receiverBasicObject.getLookupNode().lookupMethod(name);

        final RubyBasicObject self = context.getCoreLibrary().box(frame.getArguments(RubyArguments.class).getSelf());

        if (method == null || method.isUndefined()) {
            CompilerDirectives.transferToInterpreter();
            throw new RaiseException(context.getCoreLibrary().nameErrorNoMethod(name, receiverBasicObject.toString()));
        }

        if (!method.isVisibleTo(self)) {
            CompilerDirectives.transferToInterpreter();
            throw new RaiseException(context.getCoreLibrary().noMethodError(name, receiverBasicObject.toString()));
        }

        return method;
    }

    public RubyContext getContext() {
        return context;
    }

}
