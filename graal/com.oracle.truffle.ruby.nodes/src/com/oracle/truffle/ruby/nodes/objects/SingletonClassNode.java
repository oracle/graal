/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.objects;

import java.math.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.objects.*;

/**
 * Reads the singleton (meta, eigen) class of an object.
 */
@NodeInfo(shortName = "singleton")
public class SingletonClassNode extends RubyNode {

    @Child protected RubyNode child;

    public SingletonClassNode(RubyContext context, SourceSection sourceSection, RubyNode child) {
        super(context, sourceSection);
        this.child = adoptChild(child);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final Object childResult = child.execute(frame);

        final RubyContext context = getContext();

        if (childResult instanceof NilPlaceholder) {
            return context.getCoreLibrary().getNilClass();
        } else if (childResult instanceof BigInteger) {
            // TODO(CS): this is problematic - do Bignums have singletons or not?
            return context.getCoreLibrary().box(childResult).getSingletonClass();
        } else {
            return ((RubyBasicObject) childResult).getSingletonClass();
        }
    }

}
