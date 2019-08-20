/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
