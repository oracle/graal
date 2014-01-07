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

/**
 * Interface to a Ruby parser.
 */
public interface RubyParser {

    public static enum ParserContext {
        TOP_LEVEL, SHELL, MODULE
    }

    RubyParserResult parse(RubyContext context, Source source, ParserContext parserContext, MaterializedFrame parentFrame);

}
