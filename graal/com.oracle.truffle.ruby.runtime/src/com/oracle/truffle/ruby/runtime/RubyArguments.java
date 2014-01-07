/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.runtime.core.*;

/**
 * Arguments and other context passed to a Ruby method. Includes the central Ruby context object,
 * optionally the scope at the point of declaration (forming a closure), the value of self, a passed
 * block, and the formal arguments.
 */
public final class RubyArguments extends Arguments {

    private final MaterializedFrame declarationFrame;
    private final Object self;
    private final RubyProc block;
    private final Object[] arguments;

    public RubyArguments(MaterializedFrame declarationFrame, Object self, RubyProc block, Object... arguments) {
        assert self != null;
        assert arguments != null;

        this.declarationFrame = declarationFrame;
        this.self = self;
        this.block = block;
        this.arguments = arguments;
    }

    public MaterializedFrame getDeclarationFrame() {
        return declarationFrame;
    }

    /**
     * Get the declaration frame a certain number of levels up from the current frame, where the
     * current frame is 0.
     */
    public static MaterializedFrame getDeclarationFrame(VirtualFrame frame, int level) {
        assert level > 0;

        MaterializedFrame parentFrame = frame.getArguments(RubyArguments.class).getDeclarationFrame();
        return getDeclarationFrame(parentFrame, level - 1);
    }

    /**
     * Get the declaration frame a certain number of levels up from the current frame, where the
     * current frame is 0.
     */
    @ExplodeLoop
    private static MaterializedFrame getDeclarationFrame(MaterializedFrame frame, int level) {
        assert frame != null;
        assert level >= 0;

        MaterializedFrame parentFrame = frame;

        for (int n = 0; n < level; n++) {
            parentFrame = parentFrame.getArguments(RubyArguments.class).getDeclarationFrame();
        }

        return parentFrame;
    }

    public Object getSelf() {
        return self;
    }

    public RubyProc getBlock() {
        return block;
    }

    public Object[] getArguments() {
        return arguments;
    }

}
