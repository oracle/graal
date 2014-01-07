/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.methods;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;

@NodeInfo(shortName = "alias")
public class AliasNode extends RubyNode {

    @Child protected RubyNode module;
    final String newName;
    final String oldName;

    public AliasNode(RubyContext context, SourceSection sourceSection, RubyNode module, String newName, String oldName) {
        super(context, sourceSection);
        this.module = adoptChild(module);
        this.newName = newName;
        this.oldName = oldName;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        final RubyModule moduleObject = (RubyModule) module.execute(frame);
        moduleObject.alias(newName, oldName);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        executeVoid(frame);
        return NilPlaceholder.INSTANCE;
    }

}
