/*
 * Copyright (c) 2013, 2014 Oracle and/or its affiliates. All rights reserved. This
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
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.core.array.*;
import com.oracle.truffle.ruby.runtime.methods.*;
import com.oracle.truffle.ruby.runtime.objects.*;

/**
 * A call node that has a chain of dispatch nodes.
 * <p>
 * The dispatch chain starts as {@link CallNode} -&gt; {@link DispatchHeadNode} -&gt;
 * {@link UninitializedBoxingDispatchNode} -&gt; {@link UninitializedDispatchNode}.
 * <p>
 * When the {@link UninitializedDispatchNode} is reached a new node is inserted into the chain. If
 * the node dispatches based on some unboxed value (unboxed as in it's not a Ruby object, just a
 * Java object) such as {@link Integer}, then that node is inserted before the
 * {@link UninitializedBoxingDispatchNode}, otherwise if it dispatches based on some Ruby
 * BasicObject, it is inserted afterwards.
 * <p>
 * The {@link UninitializedBoxingDispatchNode} becomes a {@link BoxingDispatchNode} when we find
 * that the boxing has to be done on the fast path - when there is some boxed dispatch node.
 * <p>
 * So the general format is {@link CallNode} -&gt; {@link DispatchHeadNode} -&gt; zero or more
 * unboxed dispatches -&gt; {@link UninitializedBoxingDispatchNode} | {@link BoxingDispatchNode}
 * -&gt; zero or more boxed dispatches -&gt; {@link UninitializedDispatchNode}.
 * <p>
 * There are several special cases of unboxed and boxed dispatch nodes based on the types and
 * methods involved.
 * <p>
 * If we have too many dispatch nodes we replace the whole chain with {@link DispatchHeadNode} -&gt;
 * {@link BoxingDispatchNode} -&gt; {@link GeneralBoxedDispatchNode}.
 * <p>
 * This system allows us to dispatch based purely on Java class, before we have to turn the object
 * into a full {@link RubyBasicObject} and consider the full Ruby lookup process, and something such
 * as a math call which may work on Fixnum or Float to work as just a couple of applications of
 * {@code instanceof} and assumption checks.
 */
public class CallNode extends RubyNode {

    @Child protected RubyNode receiver;
    @Child protected ProcOrNullNode block;
    @Children protected final RubyNode[] arguments;

    private final String name;
    private final boolean isSplatted;

    @Child protected DispatchHeadNode dispatchHead;

    public CallNode(RubyContext context, SourceSection section, String name, RubyNode receiver, RubyNode block, boolean isSplatted, RubyNode[] arguments) {
        super(context, section);

        assert receiver != null;
        assert arguments != null;
        assert name != null;

        this.receiver = adoptChild(receiver);

        if (block == null) {
            this.block = null;
        } else {
            this.block = adoptChild(ProcOrNullNodeFactory.create(context, section, block));
        }

        this.arguments = adoptChildren(arguments);
        this.name = name;
        this.isSplatted = isSplatted;

        dispatchHead = adoptChild(new DispatchHeadNode(context, section, name, isSplatted));
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final Object receiverObject = receiver.execute(frame);
        final Object[] argumentsObjects = executeArguments(frame);
        final RubyProc blockObject = executeBlock(frame);

        return dispatchHead.dispatch(frame, receiverObject, blockObject, argumentsObjects);
    }

    private RubyProc executeBlock(VirtualFrame frame) {
        if (block != null) {
            return block.executeRubyProc(frame);
        } else {
            return null;
        }
    }

    @ExplodeLoop
    private Object[] executeArguments(VirtualFrame frame) {
        final Object[] argumentsObjects = new Object[arguments.length];

        for (int i = 0; i < arguments.length; i++) {
            argumentsObjects[i] = arguments[i].execute(frame);
            assert RubyContext.shouldObjectBeVisible(argumentsObjects[i]) : argumentsObjects[i].getClass();
        }

        if (isSplatted) {
            assert argumentsObjects[0] instanceof RubyArray;
            return ((RubyArray) argumentsObjects[0]).toObjectArray();
        } else {
            return argumentsObjects;
        }
    }

    @Override
    public Object isDefined(VirtualFrame frame) {
        final RubyContext context = getContext();

        Object receiverObject;

        try {
            /*
             * TODO(CS): Getting a node via an accessor like this doesn't work with Truffle at the
             * moment and will cause frame escape errors, so we don't use it in compilation mode.
             */

            CompilerAsserts.neverPartOfCompilation();

            receiverObject = receiver.execute(frame);
        } catch (Exception e) {
            return NilPlaceholder.INSTANCE;
        }

        final RubyBasicObject receiverBasicObject = context.getCoreLibrary().box(receiverObject);

        final RubyMethod method = receiverBasicObject.getLookupNode().lookupMethod(name);

        final RubyBasicObject self = context.getCoreLibrary().box(frame.getArguments(RubyArguments.class).getSelf());

        if (method == null || method.isUndefined()) {
            return NilPlaceholder.INSTANCE;
        }

        if (!method.isVisibleTo(self)) {
            return NilPlaceholder.INSTANCE;
        }

        return context.makeString("method");
    }

    public String getName() {
        return name;
    }
}
