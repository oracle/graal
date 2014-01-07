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
import com.oracle.truffle.ruby.runtime.core.*;

/**
 * Represents writing a constant into some module.
 */
@NodeInfo(shortName = "write-constant")
public class WriteConstantNode extends RubyNode {

    private final String name;
    @Child protected RubyNode module;
    @Child protected RubyNode rhs;

    public WriteConstantNode(RubyContext context, SourceSection sourceSection, String name, RubyNode module, RubyNode rhs) {
        super(context, sourceSection);
        this.name = name;
        this.module = adoptChild(module);
        this.rhs = adoptChild(rhs);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        // TODO(cs): can module ever not evaluate to a RubyModule?

        final RubyModule moduleObject = (RubyModule) module.execute(frame);

        final Object rhsValue = rhs.execute(frame);

        assert rhsValue != null;
        assert !(rhsValue instanceof String);

        moduleObject.setModuleConstant(name, rhsValue);

        return rhsValue;
    }

}
