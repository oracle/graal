/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.control;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.objects.*;

/**
 * Rescues any exception.
 */
@NodeInfo(shortName = "rescue-any")
public class RescueAnyNode extends RescueNode {

    public RescueAnyNode(RubyContext context, SourceSection sourceSection, RubyNode body) {
        super(context, sourceSection, body);
    }

    @Override
    public boolean canHandle(VirtualFrame frame, RubyBasicObject exception) {
        return true;
    }

}
