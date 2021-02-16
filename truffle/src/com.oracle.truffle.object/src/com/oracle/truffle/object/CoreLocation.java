/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

abstract class CoreLocation extends LocationImpl {
    protected CoreLocation() {
    }

    @Override
    public String toString() {
        String typeString = (this instanceof CoreLocations.TypedLocation ? ((CoreLocations.TypedLocation) this).getType().getSimpleName() : "Object");
        return typeString + getWhereString();
    }

    @Override
    protected final boolean isIntLocation() {
        return this instanceof CoreLocations.IntLocation;
    }

    @Override
    protected final boolean isDoubleLocation() {
        return this instanceof CoreLocations.DoubleLocation;
    }

    @Override
    protected final boolean isLongLocation() {
        return this instanceof CoreLocations.LongLocation;
    }

    /**
     * Boxed values need to be compared by value not by reference.
     *
     * The first parameter should be the one with the more precise type information.
     *
     * For sets to final locations, otherValue.equals(thisValue) seems more beneficial, since we
     * usually know more about the value to be set.
     */
    @SuppressWarnings("deprecation")
    static boolean valueEquals(Object val1, Object val2) {
        return val1 == val2 || (val1 != null && equalsBoundary(val1, val2));
    }

    @TruffleBoundary // equals is blacklisted
    private static boolean equalsBoundary(Object val1, Object val2) {
        return val1.equals(val2);
    }
}
