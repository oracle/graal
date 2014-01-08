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

import com.oracle.truffle.api.source.*;
import com.oracle.truffle.ruby.runtime.*;

/**
 * A Ruby probe situated at a Ruby "line".
 */
public abstract class RubyLineProbe extends RubyProbe {

    protected final SourceLineLocation location;

    /**
     * Creates a probe that will cause a halt just before child execution starts; a {@code oneShot}
     * probe will remove itself the first time it halts.
     */
    public RubyLineProbe(RubyContext context, SourceLineLocation location, boolean oneShot) {
        super(context, oneShot);
        this.location = location;
    }

    public SourceLineLocation getLineLocation() {
        return location;
    }

}
