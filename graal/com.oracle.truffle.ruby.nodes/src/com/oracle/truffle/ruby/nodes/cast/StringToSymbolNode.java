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
 * Creates a symbol from a string.
 */
@NodeInfo(shortName = "cast-string-to-symbol")
@NodeChild("string")
public abstract class StringToSymbolNode extends RubyNode {

    public StringToSymbolNode(RubyContext context, SourceSection sourceSection) {
        super(context, sourceSection);
    }

    public StringToSymbolNode(StringToSymbolNode prev) {
        super(prev);
    }

    @Specialization
    public RubySymbol doString(RubyString string) {
        return new RubySymbol(getContext().getCoreLibrary().getSymbolClass(), string.toString());
    }

}
