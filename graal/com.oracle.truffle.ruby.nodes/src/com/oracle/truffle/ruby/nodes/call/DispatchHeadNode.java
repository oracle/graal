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
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.methods.*;
import com.oracle.truffle.ruby.runtime.objects.*;

/**
 * The head of a chain of dispatch nodes. Can be used with {@link CallNode} or on its own.
 */
public class DispatchHeadNode extends DispatchNode {

    private final RubyContext context;
    private final String name;
    private final boolean isSplatted;

    @Child protected UnboxedDispatchNode dispatch;

    public DispatchHeadNode(RubyContext context, SourceSection sourceSection, String name, boolean isSplatted) {
        super(context, sourceSection);

        assert context != null;
        assert name != null;

        this.context = context;
        this.name = name;
        this.isSplatted = isSplatted;

        final UninitializedDispatchNode uninitializedDispatch = new UninitializedDispatchNode(context, sourceSection, name);
        dispatch = adoptChild(new UninitializedBoxingDispatchNode(context, sourceSection, uninitializedDispatch));
    }

    public Object dispatch(VirtualFrame frame, Object receiverObject, RubyProc blockObject, Object... argumentsObjects) {
        return dispatch.dispatch(frame, receiverObject, blockObject, argumentsObjects);
    }

    /**
     * Replace the entire dispatch chain with a fresh chain. Used when the situation has changed in
     * such a significant way that it's best to start again rather than add new specializations to
     * the chain. Used for example when methods appear to have been monkey-patched.
     */
    public Object respecialize(VirtualFrame frame, String reason, Object receiverObject, RubyProc blockObject, Object... argumentObjects) {
        CompilerAsserts.neverPartOfCompilation();

        replace(new DispatchHeadNode(context, getSourceSection(), name, isSplatted), reason);

        final RubyBasicObject receiverBasicObject = context.getCoreLibrary().box(receiverObject);

        final RubyMethod method = lookup(frame, receiverBasicObject, name);
        return method.call(frame.pack(), receiverBasicObject, blockObject, argumentObjects);
    }

    public UnboxedDispatchNode getDispatch() {
        return dispatch;
    }

}
