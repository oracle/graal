/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.methods.arguments;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.array.*;

/**
 * Switches between loading arguments as normal and doing a destructure. See
 * testBlockArgumentsDestructure and MethodTranslator.
 */
@NodeInfo(shortName = "block-destructure-switch")
public class BlockDestructureSwitchNode extends RubyNode {

    @Child protected RubyNode loadIndividualArguments;
    @Child protected RubyNode destructureArguments;
    @Child protected RubyNode body;

    public BlockDestructureSwitchNode(RubyContext context, SourceSection sourceSection, RubyNode loadIndividualArguments, RubyNode destructureArguments, RubyNode body) {
        super(context, sourceSection);
        this.loadIndividualArguments = adoptChild(loadIndividualArguments);
        this.destructureArguments = adoptChild(destructureArguments);
        this.body = adoptChild(body);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final RubyArguments arguments = frame.getArguments(RubyArguments.class);

        if (arguments.getArguments().length == 1 && arguments.getArguments()[0] instanceof RubyArray) {
            destructureArguments.executeVoid(frame);
        } else {
            loadIndividualArguments.executeVoid(frame);
        }

        return body.execute(frame);
    }

}
