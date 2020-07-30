/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.object.dsl.test;

import org.junit.Test;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.FinalLocationException;
import com.oracle.truffle.api.object.IncompatibleLocationException;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.object.dsl.Layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ImplicitCastTest {
    private static final DynamicObjectLibrary LIBRARY = DynamicObjectLibrary.getUncached();

    @Layout
    public interface NoCastLayout {

        String LONG_VALUE_IDENTIFIER = "long-value";
        String DOUBLE_VALUE_IDENTIFIER = "double-value";

        DynamicObject createNoCast(long longValue, double doubleValue);

        long getLongValue(DynamicObject object);

        void setLongValue(DynamicObject object, long value);

        double getDoubleValue(DynamicObject object);

        void setDoubleValue(DynamicObject object, double value);

    }

    @Layout(implicitCastIntToLong = true, implicitCastIntToDouble = true)
    public interface CastLayout {

        String LONG_VALUE_IDENTIFIER = "long-value";
        String DOUBLE_VALUE_IDENTIFIER = "double-value";

        DynamicObject createCast(long longValue, double doubleValue);

        long getLongValue(DynamicObject object);

        void setLongValue(DynamicObject object, long value);

        double getDoubleValue(DynamicObject object);

        void setDoubleValue(DynamicObject object, double value);

    }

    private static final NoCastLayout NO_CAST_LAYOUT = NoCastLayoutImpl.INSTANCE;
    private static final CastLayout CAST_LAYOUT = CastLayoutImpl.INSTANCE;

    @Test(expected = IncompatibleLocationException.class)
    public void testCantAssignIntToLong() throws IncompatibleLocationException, FinalLocationException {
        final DynamicObject object = NO_CAST_LAYOUT.createNoCast(14, 14.2);
        object.getShape().getProperty(NoCastLayout.LONG_VALUE_IDENTIFIER).set(object, 14, object.getShape());
    }

    @Test(expected = IncompatibleLocationException.class)
    public void testCantAssignIntToDouble() throws IncompatibleLocationException, FinalLocationException {
        final DynamicObject object = NO_CAST_LAYOUT.createNoCast(14, 14.2);
        object.getShape().getProperty(NoCastLayout.DOUBLE_VALUE_IDENTIFIER).set(object, 14, object.getShape());
    }

    @Test
    public void testCanAssignIntToLong() {
        final DynamicObject object = CAST_LAYOUT.createCast(14, 14.2);
        final Shape shapeBefore = object.getShape();
        assertTrue(LIBRARY.putIfPresent(object, CastLayout.LONG_VALUE_IDENTIFIER, 14));
        assertEquals(shapeBefore, object.getShape());
    }

    @Test
    public void testCanAssignIntToDouble() {
        final DynamicObject object = CAST_LAYOUT.createCast(14, 14.2);
        final Shape shapeBefore = object.getShape();
        assertTrue(LIBRARY.putIfPresent(object, CastLayout.DOUBLE_VALUE_IDENTIFIER, 14));
        assertEquals(shapeBefore, object.getShape());
    }

}
