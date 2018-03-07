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
package com.oracle.truffle.object;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.FinalLocationException;
import com.oracle.truffle.api.object.IncompatibleLocationException;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.LongLocation;
import com.oracle.truffle.api.object.Shape;

/** @since 0.17 or earlier */
public abstract class LocationImpl extends Location {
    /**
     * @since 0.17 or earlier
     */
    protected LocationImpl() {
    }

    /** @since 0.17 or earlier */
    public interface InternalLongLocation extends LongLocation {
        /** @since 0.17 or earlier */
        void setLongInternal(DynamicObject store, long value);

        /** @since 0.17 or earlier */
        String getWhereString();
    }

    /** @since 0.17 or earlier */
    public interface LocationVisitor {
        /** @since 0.17 or earlier */
        void visitObjectField(int index, int count);

        /** @since 0.17 or earlier */
        void visitObjectArray(int index, int count);

        /** @since 0.17 or earlier */
        void visitPrimitiveField(int index, int count);

        /** @since 0.17 or earlier */
        void visitPrimitiveArray(int index, int count);
    }

    /** @since 0.17 or earlier */
    @Override
    public void set(DynamicObject store, Object value, Shape shape) throws IncompatibleLocationException, FinalLocationException {
        setInternal(store, value);
    }

    /** @since 0.17 or earlier */
    @Override
    protected final Object getInternal(DynamicObject store) {
        throw new UnsupportedOperationException();
    }

    /** @since 0.17 or earlier */
    @Override
    protected abstract void setInternal(DynamicObject store, Object value) throws IncompatibleLocationException;

    /** @since 0.17 or earlier */
    @Override
    public boolean canSet(DynamicObject store, Object value) {
        return canStore(value) && canStoreFinal(store, value);
    }

    /** @since 0.17 or earlier */
    @Override
    public boolean canSet(Object value) {
        return canSet(null, value);
    }

    /** @since 0.17 or earlier */
    @Override
    public boolean canStore(Object value) {
        return true;
    }

    /** @since 0.17 or earlier */
    @SuppressWarnings("unused")
    protected boolean canStoreFinal(DynamicObject store, Object value) {
        return true;
    }

    /** @since 0.17 or earlier */
    @Override
    public boolean isFinal() {
        return false;
    }

    /** @since 0.17 or earlier */
    @Override
    public boolean isConstant() {
        return false;
    }

    /** @since 0.17 or earlier */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (isFinal() ? 1231 : 1237);
        return result;
    }

    /** @since 0.17 or earlier */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Location other = (Location) obj;
        if (isFinal() != other.isFinal()) {
            return false;
        }
        return true;
    }

    /** @since 0.17 or earlier */
    @Override
    public String toString() {
        String finalString = isFinal() ? "f" : "";
        String typeString = (this instanceof com.oracle.truffle.api.object.TypedLocation ? ((com.oracle.truffle.api.object.TypedLocation) this).getType().getSimpleName() : "Object");
        return finalString + typeString + getWhereString();
    }

    /** @since 0.17 or earlier */
    protected String getWhereString() {
        return "";
    }

    /**
     * Get the number of object array elements this location requires.
     *
     * @since 0.17 or earlier
     */
    public int objectArrayCount() {
        return 0;
    }

    /**
     * Get the number of in-object {@link Object} fields this location requires.
     *
     * @since 0.17 or earlier
     */
    public int objectFieldCount() {
        return 0;
    }

    /**
     * Get the number of in-object primitive fields this location requires.
     *
     * @since 0.17 or earlier
     */
    public int primitiveFieldCount() {
        return 0;
    }

    /**
     * Get the number of primitive array elements this location requires.
     *
     * @since 0.17 or earlier
     */
    public int primitiveArrayCount() {
        return 0;
    }

    /**
     * Accept a visitor for location allocation for this and every nested location.
     *
     * @param locationVisitor visitor to be notified of every allocated slot in use by this location
     * @since 0.17 or earlier
     */
    public abstract void accept(LocationVisitor locationVisitor);

    /**
     * Boxed values need to be compared by value not by reference.
     *
     * The first parameter should be the one with the more precise type information.
     *
     * For sets to final locations, otherValue.equals(thisValue) seems more beneficial, since we
     * usually know more about the value to be set.
     *
     * @since 0.17 or earlier
     */
    public static boolean valueEquals(Object val1, Object val2) {
        return val1 == val2 || (val1 != null && val1.equals(val2));
    }
}
