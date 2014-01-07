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
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.control.*;

public abstract class ReadConstantNode extends RubyNode {

    protected final String name;
    @Child protected RubyNode receiver;

    public ReadConstantNode(RubyContext context, SourceSection sourceSection, String name, RubyNode receiver) {
        super(context, sourceSection);
        this.name = name;
        this.receiver = adoptChild(receiver);
    }

    @Override
    public Object isDefined(VirtualFrame frame) {
        final RubyContext context = getContext();

        if (name.equals("Encoding")) {
            /*
             * Work-around so I don't have to load the iconv library - runners/formatters/junit.rb.
             */
            return context.makeString("constant");
        }

        Object value;

        try {
            value = context.getCoreLibrary().box(receiver.execute(frame)).getLookupNode().lookupConstant(name);
        } catch (RaiseException e) {
            /*
             * If we are looking up a constant in a constant that is itself undefined, we return Nil
             * rather than raising the error. Eg.. defined?(Defined::Undefined1::Undefined2)
             */

            if (e.getRubyException().getRubyClass() == context.getCoreLibrary().getNameErrorClass()) {
                return NilPlaceholder.INSTANCE;
            }

            throw e;
        }

        if (value == null) {
            return NilPlaceholder.INSTANCE;
        } else {
            return context.makeString("constant");
        }
    }

}
