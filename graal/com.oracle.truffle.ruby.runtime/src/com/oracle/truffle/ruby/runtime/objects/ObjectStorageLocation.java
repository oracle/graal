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

import com.oracle.truffle.ruby.runtime.*;

/**
 * A storage location for any object.
 */
public class ObjectStorageLocation extends StorageLocation {

    private final int index;

    public ObjectStorageLocation(ObjectLayout objectLayout, int index) {
        super(objectLayout);
        this.index = index;
    }

    @Override
    public boolean isSet(RubyBasicObject object) {
        return object.objectStorageLocations[index] != null;
    }

    @Override
    public Object read(RubyBasicObject object, boolean condition) {
        final Object result = object.objectStorageLocations[index];

        if (result == null) {
            return NilPlaceholder.INSTANCE;
        } else {
            return result;
        }
    }

    @Override
    public void write(RubyBasicObject object, Object value) {
        object.objectStorageLocations[index] = value;
    }

    @Override
    public Class getStoredClass() {
        return Object.class;
    }

}
