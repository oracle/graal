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
 * A storage location for Floats.
 */
public class FloatStorageLocation extends PrimitiveStorageLocation {

    public FloatStorageLocation(ObjectLayout objectLayout, long offset, int mask) {
        super(objectLayout, offset, mask);
    }

    @Override
    public Object read(RubyBasicObject object, boolean condition) {
        try {
            return readFloat(object, condition);
        } catch (UnexpectedResultException e) {
            return e.getResult();
        }
    }

    public double readFloat(RubyBasicObject object, boolean condition) throws UnexpectedResultException {
        if (isSet(object)) {
            return CompilerDirectives.unsafeGetDouble(object, offset, condition, this);
        } else {
            throw new UnexpectedResultException(NilPlaceholder.INSTANCE);
        }
    }

    @Override
    public void write(RubyBasicObject object, Object value) throws GeneralizeStorageLocationException {
        if (value instanceof Double) {
            writeFloat(object, (double) value);
        } else if (value instanceof NilPlaceholder) {
            markAsUnset(object);
        } else {
            throw new GeneralizeStorageLocationException();
        }
    }

    public void writeFloat(RubyBasicObject object, Double value) {
        CompilerDirectives.unsafePutDouble(object, offset, value, null);
        markAsSet(object);
    }

    @Override
    public Class getStoredClass() {
        return Double.class;
    }

}
