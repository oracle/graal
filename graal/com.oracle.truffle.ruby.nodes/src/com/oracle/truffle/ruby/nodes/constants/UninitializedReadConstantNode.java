/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.constants;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.control.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.objects.*;

/**
 * Represents an uninitialized constant read from some object. After the first read it will be
 * specialized to some other node. This is the starting point for all constant reads.
 */
@NodeInfo(shortName = "uninitialized-read-constant")
public class UninitializedReadConstantNode extends ReadConstantNode {

    public UninitializedReadConstantNode(RubyContext context, SourceSection sourceSection, String name, RubyNode receiver) {
        super(context, sourceSection, name, receiver);
    }

    /**
     * This execute method allows us to pass in the already executed receiver object, so that during
     * uninitialization it is not executed once by the specialized node and again by this node.
     */
    public Object execute(RubyBasicObject receiverObject) {
        CompilerAsserts.neverPartOfCompilation();

        final RubyContext context = receiverObject.getRubyClass().getContext();

        Object value;

        value = receiverObject.getLookupNode().lookupConstant(name);

        if (value == null && receiverObject instanceof RubyModule) {
            /*
             * FIXME(CS): I'm obviously doing something wrong with constant lookup in nested modules
             * here, but explicitly looking in the Module itself, not its lookup node, seems to fix
             * it for now.
             */

            value = ((RubyModule) receiverObject).lookupConstant(name);
        }

        if (value == null) {
            throw new RaiseException(context.getCoreLibrary().nameErrorUninitializedConstant(name));
        }

        replace(new CachedReadConstantNode(context, getSourceSection(), name, receiver, receiverObject.getRubyClass(), value));

        assert RubyContext.shouldObjectBeVisible(value);

        return value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        CompilerDirectives.transferToInterpreter();

        final RubyContext context = getContext();

        final Object receiverObject = receiver.execute(frame);
        final RubyBasicObject receiverRubyObject = context.getCoreLibrary().box(receiverObject);

        return execute(receiverRubyObject);
    }

}
