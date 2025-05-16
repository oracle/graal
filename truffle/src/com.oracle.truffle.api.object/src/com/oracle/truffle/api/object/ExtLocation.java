/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.object;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.ExtLocations.DoubleLocation;
import com.oracle.truffle.api.object.ExtLocations.IntLocation;
import com.oracle.truffle.api.object.ExtLocations.LongLocation;
import com.oracle.truffle.api.object.ExtLocations.ObjectLocation;

abstract class ExtLocation extends LocationImpl {
    @Override
    protected long getLong(DynamicObject store, boolean guard) throws UnexpectedResultException {
        return super.getLong(store, guard);
    }

    @Override
    protected double getDouble(DynamicObject store, boolean guard) throws UnexpectedResultException {
        return super.getDouble(store, guard);
    }

    @Override
    protected boolean getBoolean(DynamicObject store, boolean guard) throws UnexpectedResultException {
        return super.getBoolean(store, guard);
    }

    @Override
    protected int getInt(DynamicObject store, boolean guard) throws UnexpectedResultException {
        return super.getInt(store, guard);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected abstract void set(DynamicObject store, Object value, boolean guard, boolean init) throws com.oracle.truffle.api.object.IncompatibleLocationException;

    @Override
    protected String getWhereString() {
        return super.getWhereString();
    }

    @Override
    protected final boolean isIntLocation() {
        return this instanceof IntLocation;
    }

    @Override
    protected final boolean isDoubleLocation() {
        return this instanceof DoubleLocation;
    }

    @Override
    protected final boolean isLongLocation() {
        return this instanceof LongLocation;
    }

    @Override
    protected boolean isObjectLocation() {
        return this instanceof ObjectLocation;
    }

    /**
     * Boxed values need to be compared by value not by reference.
     *
     * The first parameter should be the one with the more precise type information.
     *
     * For sets to final locations, otherValue.equals(thisValue) seems more beneficial, since we
     * usually know more about the value to be set.
     */
    static boolean valueEquals(Object val1, Object val2) {
        return val1 == val2 || (val1 != null && equalsBoundary(val1, val2));
    }

    @TruffleBoundary // Object.equals is a boundary
    private static boolean equalsBoundary(Object val1, Object val2) {
        return val1.equals(val2);
    }

    int getOrdinal() {
        throw new IllegalArgumentException(this.getClass().getName());
    }
}
