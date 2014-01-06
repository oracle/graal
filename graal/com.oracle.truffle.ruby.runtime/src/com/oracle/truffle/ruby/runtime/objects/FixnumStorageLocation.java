/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.objects;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.runtime.*;

/**
 * A storage location for Fixnums.
 */
public class FixnumStorageLocation extends PrimitiveStorageLocation {

    public FixnumStorageLocation(ObjectLayout objectLayout, long offset, int mask) {
        super(objectLayout, offset, mask);
    }

    @Override
    public Object read(RubyBasicObject object, boolean condition) {
        try {
            return readFixnum(object, condition);
        } catch (UnexpectedResultException e) {
            return e.getResult();
        }
    }

    public int readFixnum(RubyBasicObject object, boolean condition) throws UnexpectedResultException {
        if (isSet(object)) {
            return CompilerDirectives.unsafeGetInt(object, offset, condition, this);
        } else {
            throw new UnexpectedResultException(NilPlaceholder.INSTANCE);
        }
    }

    @Override
    public void write(RubyBasicObject object, Object value) throws GeneralizeStorageLocationException {
        if (value instanceof Integer) {
            writeFixnum(object, (int) value);
        } else if (value instanceof NilPlaceholder) {
            markAsUnset(object);
        } else {
            throw new GeneralizeStorageLocationException();
        }
    }

    public void writeFixnum(RubyBasicObject object, int value) {
        CompilerDirectives.unsafePutInt(object, offset, value, null);
        markAsSet(object);
    }

    @Override
    public Class getStoredClass() {
        return Integer.class;
    }

}
