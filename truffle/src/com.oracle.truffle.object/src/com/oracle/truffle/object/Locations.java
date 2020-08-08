/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.FinalLocationException;
import com.oracle.truffle.api.object.IncompatibleLocationException;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;

/**
 * Property location.
 *
 * @see Location
 * @see Shape
 * @see Property
 * @see DynamicObject
 * @since 0.17 or earlier
 */
@Deprecated
public abstract class Locations {
    /**
     * @since 0.17 or earlier
     */
    protected Locations() {
    }

    /** @since 0.17 or earlier */
    public abstract static class ValueLocation extends LocationImpl {

        private final Object value;

        /** @since 0.17 or earlier */
        public ValueLocation(Object value) {
            assert !(value instanceof Location);
            this.value = value;
        }

        /** @since 0.17 or earlier */
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + ((value == null) ? 0 : 0 /* value.hashCode() */);
            return result;
        }

        /** @since 0.17 or earlier */
        @Override
        public boolean equals(Object obj) {
            return super.equals(obj) && Objects.equals(value, ((ValueLocation) obj).value);
        }

        /** @since 0.17 or earlier */
        @Override
        public final Object get(DynamicObject store, boolean condition) {
            return value;
        }

        /** @since 0.17 or earlier */
        @Override
        public final void set(DynamicObject store, Object value, Shape shape) throws IncompatibleLocationException, FinalLocationException {
            if (!canStore(value)) {
                throw finalLocation();
            }
        }

        /** @since 0.17 or earlier */
        @SuppressWarnings("deprecation")
        @Override
        public boolean canStore(Object val) {
            return CoreLocation.valueEquals(this.value, val);
        }

        /** @since 0.17 or earlier */
        @Override
        public final void setInternal(DynamicObject store, Object value, boolean condition) throws IncompatibleLocationException {
            if (!canStore(value)) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new UnsupportedOperationException();
            }
        }

        /** @since 0.17 or earlier */
        @Override
        public String toString() {
            return "=" + String.valueOf(value);
        }

        /** @since 0.17 or earlier */
        @Override
        public final void accept(LocationVisitor locationVisitor) {
        }

        /** @since 0.18 */
        @Override
        public final boolean isValue() {
            return true;
        }
    }

    /** @since 0.17 or earlier */
    public static final class ConstantLocation extends ValueLocation {

        /** @since 0.17 or earlier */
        public ConstantLocation(Object value) {
            super(value);
        }

        /** @since 0.17 or earlier */
        @Override
        public boolean isConstant() {
            return true;
        }
    }

    /** @since 0.17 or earlier */
    public static final class DeclaredLocation extends ValueLocation {

        /** @since 0.17 or earlier */
        public DeclaredLocation(Object value) {
            super(value);
        }

        /** @since 0.18 */
        @Override
        public boolean isDeclared() {
            return true;
        }
    }
}
