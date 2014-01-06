/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.cast;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;

/**
 * Creates a regex from a string.
 */
@NodeInfo(shortName = "cast-string-to-regexp")
@NodeChild("string")
public abstract class StringToRegexpNode extends RubyNode {

    public StringToRegexpNode(RubyContext context, SourceSection sourceSection) {
        super(context, sourceSection);
    }

    public StringToRegexpNode(StringToRegexpNode prev) {
        super(prev);
    }

    @Specialization
    public RubyRegexp doString(RubyString string) {
        return new RubyRegexp(getContext().getCoreLibrary().getRegexpClass(), string.toString());
    }

}
