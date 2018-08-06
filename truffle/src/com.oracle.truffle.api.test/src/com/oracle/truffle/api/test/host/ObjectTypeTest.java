/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.host;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Layout;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.Shape;

public class ObjectTypeTest {
    private static final ObjectType OBJECT_TYPE = new ObjectType() {
        @Override
        public ForeignAccess getForeignAccessFactory(DynamicObject obj) {
            return null;
        }
    };

    @Test
    public void objectTypeRespondsToIsBoxed() {
        final Layout layout = Layout.newLayout().build();
        final Shape rootShape = layout.createShape(OBJECT_TYPE);
        final DynamicObject obj = rootShape.newInstance();
        final boolean is = HostInteropTest.isBoxed(obj);
        assertFalse("It is not boxed", is);
    }

    @Test
    public void objectTypeRespondsToIsNull() {
        final Layout layout = Layout.newLayout().build();
        final Shape rootShape = layout.createShape(OBJECT_TYPE);
        final DynamicObject obj = rootShape.newInstance();
        final boolean is = HostInteropTest.isNull(obj);
        assertFalse("It is not null", is);
    }

    @Test
    public void objectTypeRespondsToIsArray() {
        final Layout layout = Layout.newLayout().build();
        final Shape rootShape = layout.createShape(OBJECT_TYPE);
        final DynamicObject obj = rootShape.newInstance();
        final boolean is = HostInteropTest.isArray(obj);
        assertFalse("It is not array", is);
    }

}
