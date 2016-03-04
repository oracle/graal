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
import com.oracle.truffle.api.object.dsl.Layout;
import com.oracle.truffle.api.object.dsl.Nullable;

import org.junit.Assert;

public class NullabilityTest {

    @Layout
    public interface NullabilityTestLayout {

        DynamicObject createNullabilityTest(String notNullable, @Nullable String nullable);

        String getNotNullable(DynamicObject object);

        void setNotNullable(DynamicObject object, String value);

        String getNullable(DynamicObject object);

        void setNullable(DynamicObject object, String value);

    }

    private static final NullabilityTestLayout LAYOUT = NullabilityTestLayoutImpl.INSTANCE;

    @Test
    public void testCreateNonNull() {
        final DynamicObject object = LAYOUT.createNullabilityTest("foo", "bar");
        Assert.assertNotNull(object);
        Assert.assertEquals("foo", LAYOUT.getNotNullable(object));
        Assert.assertEquals("bar", LAYOUT.getNullable(object));
    }

    @Test
    public void testCreateAlllowedNull() {
        final DynamicObject object = LAYOUT.createNullabilityTest("foo", null);
        Assert.assertNotNull(object);
        Assert.assertEquals("foo", LAYOUT.getNotNullable(object));
        Assert.assertNull(LAYOUT.getNullable(object));
    }

    @Test
    public void testSetNonNullableToNonNull() {
        final DynamicObject object = LAYOUT.createNullabilityTest("foo", "bar");
        Assert.assertNotNull(object);
        Assert.assertEquals("foo", LAYOUT.getNotNullable(object));
        LAYOUT.setNotNullable(object, "baz");
        Assert.assertEquals("baz", LAYOUT.getNotNullable(object));
    }

    @Test
    public void testSetNullableToNull() {
        final DynamicObject object = LAYOUT.createNullabilityTest("foo", "bar");
        Assert.assertNotNull(object);
        Assert.assertEquals("bar", LAYOUT.getNullable(object));
        LAYOUT.setNullable(object, null);
        Assert.assertNull(LAYOUT.getNullable(object));
    }

}
