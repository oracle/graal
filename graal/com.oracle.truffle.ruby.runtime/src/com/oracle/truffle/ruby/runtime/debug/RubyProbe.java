/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.debug;

import com.oracle.truffle.api.nodes.instrument.*;
import com.oracle.truffle.ruby.runtime.*;

/**
 * A "probe node" implemented specifically for the Ruby implementation; subclasses need only
 * override those members of {@link InstrumentationProbeEvents} for which some action is needed.
 */
public abstract class RubyProbe extends InstrumentationProbeNode.DefaultProbeNode {

    protected final RubyContext context;

    public RubyProbe(RubyContext context) {
        this.context = context;
        assert context != null;
    }

    public RubyContext getContext() {
        return context;
    }
}
