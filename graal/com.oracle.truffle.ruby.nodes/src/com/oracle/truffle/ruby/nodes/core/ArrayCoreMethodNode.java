/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.core;

import com.oracle.truffle.api.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.array.*;

public abstract class ArrayCoreMethodNode extends CoreMethodNode {

    public ArrayCoreMethodNode(RubyContext context, SourceSection sourceSection) {
        super(context, sourceSection);
    }

    public ArrayCoreMethodNode(ArrayCoreMethodNode prev) {
        super(prev);
    }

    protected boolean isEmptyStore(RubyArray receiver) {
        return receiver.getArrayStore() instanceof EmptyArrayStore;
    }

    protected boolean isFixnumStore(RubyArray receiver) {
        return receiver.getArrayStore() instanceof FixnumArrayStore;
    }

    protected boolean isFixnumImmutablePairStore(RubyArray receiver) {
        return receiver.getArrayStore() instanceof FixnumImmutablePairArrayStore;
    }

    protected boolean isObjectStore(RubyArray receiver) {
        return receiver.getArrayStore() instanceof ObjectArrayStore;
    }

    protected boolean isObjectImmutablePairStore(RubyArray receiver) {
        return receiver.getArrayStore() instanceof ObjectImmutablePairArrayStore;
    }

}
