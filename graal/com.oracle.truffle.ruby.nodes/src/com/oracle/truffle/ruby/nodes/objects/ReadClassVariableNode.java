/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.objects;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;

@NodeInfo(shortName = "read-class-variable")
public class ReadClassVariableNode extends RubyNode {

    protected final String name;
    @Child protected RubyNode module;

    public ReadClassVariableNode(RubyContext context, SourceSection sourceSection, String name, RubyNode module) {
        super(context, sourceSection);
        this.name = name;
        this.module = adoptChild(module);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final RubyObject object = (RubyObject) module.execute(frame);

        RubyModule moduleObject;

        // TODO(CS): this cannot be right

        if (object instanceof RubyModule) {
            moduleObject = (RubyModule) object;
        } else {
            moduleObject = object.getRubyClass();
        }

        final Object value = moduleObject.lookupClassVariable(name);

        if (value == null) {
            // TODO(CS): is this right?
            return NilPlaceholder.INSTANCE;
        }

        return value;
    }

    @Override
    public Object isDefined(VirtualFrame frame) {
        final RubyContext context = getContext();

        final RubyObject object = (RubyObject) module.execute(frame);

        RubyModule moduleObject;

        if (object instanceof RubyModule) {
            moduleObject = (RubyModule) object;
        } else {
            moduleObject = object.getRubyClass();
        }

        final Object value = moduleObject.lookupClassVariable(name);

        if (value == null) {
            return NilPlaceholder.INSTANCE;
        } else {
            return context.makeString("class variable");
        }
    }

}
