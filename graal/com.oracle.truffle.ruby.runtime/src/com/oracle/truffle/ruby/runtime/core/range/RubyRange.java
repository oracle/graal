/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.core.range;

import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.core.array.*;

public abstract class RubyRange extends RubyObject {

    public RubyRange(RubyClass rangeClass) {
        super(rangeClass);
    }

    public abstract RubyArray toArray();

    public abstract boolean doesExcludeEnd();

}
