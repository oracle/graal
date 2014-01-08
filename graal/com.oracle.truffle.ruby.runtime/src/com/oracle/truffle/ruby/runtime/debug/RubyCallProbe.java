/*
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.debug;

import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.runtime.*;

public final class RubyCallProbe extends RubyProbe {

    private final String name;

    public RubyCallProbe(RubyContext context, String name) {
        super(context, false);
        this.name = name;
    }

    @Override
    public void enter(Node astNode, VirtualFrame frame) {
        context.getDebugManager().notifyCallEntry(astNode, name);
    }

    @Override
    public void leave(Node astNode, VirtualFrame frame) {
        context.getDebugManager().notifyCallExit(astNode, name);
    }

    @Override
    public void leave(Node astNode, VirtualFrame frame, boolean result) {
        context.getDebugManager().notifyCallExit(astNode, name);
    }

    @Override
    public void leave(Node astNode, VirtualFrame frame, int result) {
        context.getDebugManager().notifyCallExit(astNode, name);
    }

    @Override
    public void leave(Node astNode, VirtualFrame frame, double result) {
        context.getDebugManager().notifyCallExit(astNode, name);
    }

    @Override
    public void leave(Node astNode, VirtualFrame frame, Object result) {
        context.getDebugManager().notifyCallExit(astNode, name);
    }

    @Override
    public void leaveExceptional(Node astNode, VirtualFrame frame, Exception e) {
        context.getDebugManager().notifyCallExit(astNode, name);
    }
}
