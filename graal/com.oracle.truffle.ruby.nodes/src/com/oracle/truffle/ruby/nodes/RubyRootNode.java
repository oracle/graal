/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

/**
 * The root node in an AST for a method. Unlike {@link RubyNode}, this has a single entry point,
 * {@link #execute}, which Truffle knows about and can create a {@link CallTarget} from.
 */
public class RubyRootNode extends RootNode {

    protected final String indicativeName;
    @Child protected RubyNode body;

    public RubyRootNode(SourceSection sourceSection, FrameDescriptor descriptor, String indicativeName, RubyNode body) {
        super(sourceSection, descriptor);

        assert indicativeName != null;
        assert body != null;

        this.body = adoptChild(body);
        this.indicativeName = indicativeName;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return body.execute(frame);
    }

    @Override
    public String toString() {
        final SourceSection sourceSection = getSourceSection();
        final String source = sourceSection == null ? "<unknown>" : sourceSection.toString();
        return "Method " + indicativeName + ":" + source + "@" + Integer.toHexString(hashCode());
    }

}
