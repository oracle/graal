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

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectFactory;
import com.oracle.truffle.api.object.dsl.test.alternate_package.InheritedShapeBaseLayout;
import com.oracle.truffle.api.object.dsl.test.alternate_package.InheritedShapeBaseLayoutImpl;
import com.oracle.truffle.api.object.dsl.test.alternate_package.InheritedShapeTopLayout;
import com.oracle.truffle.api.object.dsl.test.alternate_package.InheritedShapeTopLayoutImpl;

public class InheritedShapePropertiesTest {

    private static final InheritedShapeBaseLayout BASE_LAYOUT = InheritedShapeBaseLayoutImpl.INSTANCE;
    private static final InheritedShapeTopLayout TOP_LAYOUT = InheritedShapeTopLayoutImpl.INSTANCE;

    @Test
    public void testBase() {
        final DynamicObjectFactory factory = BASE_LAYOUT.createInheritedShapeBaseShape(14);
        final DynamicObject object = BASE_LAYOUT.createInheritedShapeBase(factory);
        Assert.assertEquals(14, BASE_LAYOUT.getA(object));
    }

    @Test
    public void testTop() {
        final DynamicObjectFactory factory = TOP_LAYOUT.createInheritedShapeTopShape(14, 2);
        final DynamicObject object = TOP_LAYOUT.createInheritedShapeTop(factory);
        Assert.assertEquals(14, TOP_LAYOUT.getA(object));
        Assert.assertEquals(2, TOP_LAYOUT.getB(object));
    }

}
