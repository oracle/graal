/*
 * Copyright (c) 2013, 2014 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.debug;

import com.oracle.truffle.ruby.runtime.*;

/**
 * A Ruby probe situated at a Ruby local assignment.
 */
public abstract class RubyLocalProbe extends RubyProbe {

    protected final MethodLocal local;

    public RubyLocalProbe(RubyContext context, MethodLocal local, boolean oneShot) {
        super(context, oneShot);
        this.local = local;
    }

}
