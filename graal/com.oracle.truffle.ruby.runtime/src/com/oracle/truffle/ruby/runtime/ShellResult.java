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

import com.oracle.truffle.api.frame.*;

/**
 * The result of executing a line in a shell is a result value and the final frame containing any
 * local variables set.
 */
public class ShellResult {

    private final Object result;
    private final MaterializedFrame frame;

    public ShellResult(Object result, MaterializedFrame frame) {
        assert RubyContext.shouldObjectBeVisible(result);
        assert frame != null;

        this.result = result;
        this.frame = frame;
    }

    public Object getResult() {
        return result;
    }

    public MaterializedFrame getFrame() {
        return frame;
    }

}
