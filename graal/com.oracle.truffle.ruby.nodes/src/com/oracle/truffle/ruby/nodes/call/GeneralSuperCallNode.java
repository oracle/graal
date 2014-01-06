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
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.control.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.core.array.*;
import com.oracle.truffle.ruby.runtime.methods.*;
import com.oracle.truffle.ruby.runtime.objects.*;

/**
 * Represents a super call - that is a call with self as the receiver, but the superclass of self
 * used for lookup. Currently implemented without any caching, and needs to be replaced with the
 * same caching mechanism as for normal calls without complicating the existing calls too much.
 */
@NodeInfo(shortName = "general-super-call")
public class GeneralSuperCallNode extends RubyNode {

    private final String name;
    private final boolean isSplatted;
    @Child protected RubyNode block;
    @Children protected final RubyNode[] arguments;

    public GeneralSuperCallNode(RubyContext context, SourceSection sourceSection, String name, RubyNode block, RubyNode[] arguments, boolean isSplatted) {
        super(context, sourceSection);

        assert name != null;
        assert arguments != null;
        assert !isSplatted || arguments.length == 1;

        this.name = name;
        this.block = adoptChild(block);
        this.arguments = adoptChildren(arguments);
        this.isSplatted = isSplatted;
    }

    @ExplodeLoop
    @Override
    public final Object execute(VirtualFrame frame) {
        // This method is only a simple implementation - it needs proper caching

        CompilerAsserts.neverPartOfCompilation();

        final RubyBasicObject self = (RubyBasicObject) frame.getArguments(RubyArguments.class).getSelf();

        // Execute the arguments

        final Object[] argumentsObjects = new Object[arguments.length];

        CompilerAsserts.compilationConstant(arguments.length);
        for (int i = 0; i < arguments.length; i++) {
            argumentsObjects[i] = arguments[i].execute(frame);
        }

        // Execute the block

        RubyProc blockObject;

        if (block != null) {
            final Object blockTempObject = block.execute(frame);

            if (blockTempObject instanceof NilPlaceholder) {
                blockObject = null;
            } else {
                blockObject = (RubyProc) blockTempObject;
            }
        } else {
            blockObject = null;
        }

        // Lookup method

        final RubyClass selfClass = self.getRubyClass();
        final RubyMethod method = selfClass.getSuperclass().lookupMethod(name);

        if (method == null || method.isUndefined()) {
            CompilerDirectives.transferToInterpreter();
            throw new RaiseException(getContext().getCoreLibrary().nameErrorNoMethod(name, self.toString()));
        }

        if (!method.isVisibleTo(self)) {
            CompilerDirectives.transferToInterpreter();
            throw new RaiseException(getContext().getCoreLibrary().noMethodError("(unknown)"));
        }

        // Call the method

        if (isSplatted) {
            final RubyArray argumentsArray = (RubyArray) argumentsObjects[0];
            return method.call(frame.pack(), self, blockObject, argumentsArray.asList().toArray());
        } else {
            return method.call(frame.pack(), self, blockObject, argumentsObjects);
        }
    }

    @Override
    public Object isDefined(VirtualFrame frame) {
        final RubyContext context = getContext();

        try {
            final RubyBasicObject self = context.getCoreLibrary().box(frame.getArguments(RubyArguments.class).getSelf());
            final RubyBasicObject receiverRubyObject = context.getCoreLibrary().box(self);

            final RubyMethod method = receiverRubyObject.getRubyClass().getSuperclass().lookupMethod(name);

            if (method == null || method.isUndefined() || !method.isVisibleTo(self)) {
                return NilPlaceholder.INSTANCE;
            } else {
                return context.makeString("super");
            }
        } catch (Exception e) {
            return NilPlaceholder.INSTANCE;
        }
    }

}
