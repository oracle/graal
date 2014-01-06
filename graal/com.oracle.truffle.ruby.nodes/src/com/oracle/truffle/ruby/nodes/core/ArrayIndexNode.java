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
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.array.*;

/**
 * Index an array, without using any method lookup. This isn't a call - it's an operation on a core
 * class.
 */
@NodeInfo(shortName = "array-index")
@NodeChildren({@NodeChild(value = "array", type = RubyNode.class)})
public abstract class ArrayIndexNode extends RubyNode {

    final int index;

    public ArrayIndexNode(RubyContext context, SourceSection sourceSection, int index) {
        super(context, sourceSection);
        this.index = index;
    }

    public ArrayIndexNode(ArrayIndexNode prev) {
        super(prev);
        index = prev.index;
    }

    @Specialization(guards = "isEmptyStore", order = 1)
    public NilPlaceholder indexEmpty(@SuppressWarnings("unused") RubyArray array) {
        return NilPlaceholder.INSTANCE;
    }

    @Specialization(guards = "isFixnumStore", rewriteOn = UnexpectedResultException.class, order = 2)
    public int indexFixnum(RubyArray array) throws UnexpectedResultException {
        final FixnumArrayStore store = (FixnumArrayStore) array.getArrayStore();
        return store.getFixnum(ArrayUtilities.normaliseIndex(store.size(), index));
    }

    @Specialization(guards = "isFixnumStore", order = 3)
    public Object indexMaybeFixnum(RubyArray array) {
        final FixnumArrayStore store = (FixnumArrayStore) array.getArrayStore();

        try {
            return store.getFixnum(ArrayUtilities.normaliseIndex(store.size(), index));
        } catch (UnexpectedResultException e) {
            return e.getResult();
        }
    }

    @Specialization(guards = "isFixnumImmutablePairStore", rewriteOn = UnexpectedResultException.class, order = 4)
    public int indexFixnumImmutablePair(RubyArray array) throws UnexpectedResultException {
        final FixnumImmutablePairArrayStore store = (FixnumImmutablePairArrayStore) array.getArrayStore();
        return store.getFixnum(ArrayUtilities.normaliseIndex(store.size(), index));
    }

    @Specialization(guards = "isFixnumImmutablePairStore", order = 5)
    public Object indexMaybeFixnumImmutablePair(RubyArray array) {
        final FixnumImmutablePairArrayStore store = (FixnumImmutablePairArrayStore) array.getArrayStore();

        try {
            return store.getFixnum(ArrayUtilities.normaliseIndex(store.size(), index));
        } catch (UnexpectedResultException e) {
            return e.getResult();
        }
    }

    @Specialization(guards = "isObjectStore", order = 6)
    public Object indexObject(RubyArray array) {
        final ObjectArrayStore store = (ObjectArrayStore) array.getArrayStore();
        return store.get(ArrayUtilities.normaliseIndex(store.size(), index));
    }

    @Specialization(guards = "isObjectImmutablePairStore", order = 7)
    public Object indexObjectImmutablePair(RubyArray array) {
        final ObjectImmutablePairArrayStore store = (ObjectImmutablePairArrayStore) array.getArrayStore();
        return store.get(ArrayUtilities.normaliseIndex(store.size(), index));
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
