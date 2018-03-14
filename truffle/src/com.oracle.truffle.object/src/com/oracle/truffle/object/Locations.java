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
        @Override
        public boolean canStore(Object val) {
            return valueEquals(this.value, val);
        }

        /** @since 0.17 or earlier */
        @Override
        public final void setInternal(DynamicObject store, Object value) throws IncompatibleLocationException {
            if (!canStore(value)) {
                CompilerDirectives.transferToInterpreter();
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
