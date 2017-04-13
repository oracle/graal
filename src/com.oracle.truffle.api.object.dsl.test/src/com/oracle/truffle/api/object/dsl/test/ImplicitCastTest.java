/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.object.dsl.test;

import org.junit.Test;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.FinalLocationException;
import com.oracle.truffle.api.object.IncompatibleLocationException;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.object.dsl.Layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ImplicitCastTest {

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
        assertTrue(object.set(CastLayout.LONG_VALUE_IDENTIFIER, 14));
        assertEquals(shapeBefore, object.getShape());
    }

    @Test
    public void testCanAssignIntToDouble() {
        final DynamicObject object = CAST_LAYOUT.createCast(14, 14.2);
        final Shape shapeBefore = object.getShape();
        assertTrue(object.set(CastLayout.DOUBLE_VALUE_IDENTIFIER, 14));
        assertEquals(shapeBefore, object.getShape());
    }

}
