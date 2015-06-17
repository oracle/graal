/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import com.oracle.truffle.api.object.*;

public abstract class LocationImpl extends Location {

    public interface EffectivelyFinalLocation<T extends Location> {
        T toNonFinalLocation();
    }

    public interface TypedObjectLocation<T extends Location & ObjectLocation> extends ObjectLocation {
        T toUntypedLocation();
    }

    public interface InternalLongLocation extends LongLocation {
        void setLongInternal(DynamicObject store, long value);
    }

    public interface LocationVisitor {
        void visitObjectField(int index, int count);

        void visitObjectArray(int index, int count);

        void visitPrimitiveField(int index, int count);

        void visitPrimitiveArray(int index, int count);
    }

    @Override
    public void set(DynamicObject store, Object value, Shape shape) throws IncompatibleLocationException, FinalLocationException {
        setInternal(store, value);
    }

    @Override
    protected final Object getInternal(DynamicObject store) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected abstract void setInternal(DynamicObject store, Object value) throws IncompatibleLocationException;

    @Override
    public final boolean canSet(DynamicObject store, Object value) {
        return canStore(value) && canStoreFinal(store, value);
    }

    @Override
    public boolean canStore(Object value) {
        return true;
    }

    @SuppressWarnings("unused")
    protected boolean canStoreFinal(DynamicObject store, Object value) {
        return true;
    }

    @Override
    public boolean isFinal() {
        return false;
    }

    @Override
    public boolean isConstant() {
        return false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (isFinal() ? 1231 : 1237);
        return result;
    }

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

    @Override
    public String toString() {
        String finalString = isFinal() ? "f" : "";
        String typeString = this instanceof IntLocation ? "i" : (this instanceof DoubleLocation ? "d" : (this instanceof BooleanLocation ? "b"
                        : (this instanceof TypedLocation ? ((TypedLocation) this).getType().getSimpleName() : "o")));
        return finalString + typeString + getWhereString();
    }

    protected String getWhereString() {
        return "";
    }

    /**
     * Get the number of object array elements this location requires.
     */
    public int objectArrayCount() {
        return 0;
    }

    /**
     * Get the number of in-object {@link Object} fields this location requires.
     */
    public int objectFieldCount() {
        return 0;
    }

    /**
     * Get the number of in-object primitive fields this location requires.
     */
    public int primitiveFieldCount() {
        return 0;
    }

    /**
     * Get the number of primitive array elements this location requires.
     */
    public int primitiveArrayCount() {
        return 0;
    }

    /**
     * Accept a visitor for location allocation for this and every nested location.
     *
     * @param locationVisitor visitor to be notified of every allocated slot in use by this location
     */
    public abstract void accept(LocationVisitor locationVisitor);

    /**
     * Boxed values need to be compared by value not by reference.
     *
     * The first parameter should be the one with the more precise type information.
     *
     * For sets to final locations, otherValue.equals(thisValue) seems more beneficial, since we
     * usually know more about the value to be set.
     */
    public static boolean valueEquals(Object val1, Object val2) {
        return val1 == val2 || (val1 != null && val1.equals(val2));
    }
}
