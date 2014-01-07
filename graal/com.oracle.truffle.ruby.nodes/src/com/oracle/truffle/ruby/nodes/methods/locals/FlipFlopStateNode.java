/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.methods.locals;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

public abstract class FlipFlopStateNode extends Node {

    public FlipFlopStateNode(SourceSection sourceSection) {
        super(sourceSection);
    }

    public abstract boolean getState(VirtualFrame frame);

    public abstract void setState(VirtualFrame frame, boolean state);

}
