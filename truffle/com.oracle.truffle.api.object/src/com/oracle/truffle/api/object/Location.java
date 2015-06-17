/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.api.object;

import com.oracle.truffle.api.*;

/**
 * Property location.
 *
 * @see Shape
 * @see Property
 * @see DynamicObject
 */
public abstract class Location implements BaseLocation {
    protected static IncompatibleLocationException incompatibleLocation() throws IncompatibleLocationException {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new IncompatibleLocationException();
    }

    protected static FinalLocationException finalLocation() throws FinalLocationException {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new FinalLocationException();
    }

    public final Object get(DynamicObject store, Shape shape) {
        return get(store, checkShape(store, shape));
    }

    public Object get(DynamicObject store, boolean condition) {
        return getInternal(store);
    }

    public void set(DynamicObject store, Object value, Shape shape) throws IncompatibleLocationException, FinalLocationException {
        setInternal(store, value);
    }

    public final void set(DynamicObject store, Object value, Shape oldShape, Shape newShape) throws IncompatibleLocationException {
        if (canStore(value)) {
            store.setShapeAndGrow(oldShape, newShape);
            try {
                setInternal(store, value);
            } catch (IncompatibleLocationException ex) {
                throw new IllegalStateException();
            }
        } else {
            throw incompatibleLocation();
        }
    }

    public final void set(DynamicObject store, Object value) throws IncompatibleLocationException, FinalLocationException {
        set(store, value, null);
    }

    protected abstract Object getInternal(DynamicObject store);

    /**
     * Like {@link #set(DynamicObject, Object, Shape)}, but does not invalidate final locations. For
     * internal use only and subject to change, use {@link DynamicObjectFactory} to create objects
     * with predefined properties.
     *
     * @throws IncompatibleLocationException if value is of non-assignable type
     */
    protected abstract void setInternal(DynamicObject store, Object value) throws IncompatibleLocationException;

    /**
     * Returns {@code true} if the location can be set to the value.
     *
     * @param store the receiver object
     * @param value the value in question
     */
    public boolean canSet(DynamicObject store, Object value) {
        return canStore(value);
    }

    /**
     * Returns {@code true} if the location is compatible with the value.
     *
     * The value may still be rejected if {@link #canSet(DynamicObject, Object)} returns false.
     *
     * @param value the value in question
     */
    public boolean canStore(Object value) {
        return true;
    }

    /**
     * Returns {@code true} if this is a final location, i.e. readonly once set.
     */
    public boolean isFinal() {
        return false;
    }

    /**
     * Returns {@code true} if this is an immutable constant location.
     */
    public boolean isConstant() {
        return false;
    }

    /*
     * Abstract to force overriding.
     */
    @Override
    public abstract int hashCode();

    /*
     * Abstract to force overriding.
     */
    @Override
    public abstract boolean equals(Object obj);

    protected static boolean checkShape(DynamicObject store, Shape shape) {
        return store.getShape() == shape;
    }
}
