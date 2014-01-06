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

/**
 * Represents an ensure clause in exception handling. Represented separately to the try part.
 */
@NodeInfo(shortName = "ensure")
public class EnsureNode extends RubyNode {

    @Child protected RubyNode tryPart;
    @Child protected RubyNode ensurePart;

    public EnsureNode(RubyContext context, SourceSection sourceSection, RubyNode tryPart, RubyNode ensurePart) {
        super(context, sourceSection);
        this.tryPart = adoptChild(tryPart);
        this.ensurePart = adoptChild(ensurePart);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        try {
            return tryPart.execute(frame);
        } finally {
            ensurePart.executeVoid(frame);
        }
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        try {
            tryPart.executeVoid(frame);
        } finally {
            ensurePart.executeVoid(frame);
        }
    }

}
