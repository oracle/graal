/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.object.basic;

import com.oracle.truffle.api.object.*;
import com.oracle.truffle.object.*;

public final class ShapeBasic extends ShapeImpl {
    public ShapeBasic(Layout layout, Object sharedData, ObjectType operations, int id) {
        super(layout, operations, sharedData, id);
    }

    public ShapeBasic(Layout layout, Object sharedData, ShapeImpl parent, ObjectType objectType, PropertyMap propertyMap, Transition transition, Allocator allocator, int id) {
        super(layout, parent, objectType, sharedData, propertyMap, transition, allocator, id);
    }

    @SuppressWarnings("hiding")
    @Override
    protected ShapeImpl createShape(Layout layout, Object sharedData, ShapeImpl parent, ObjectType objectType, PropertyMap propertyMap, Transition transition, Allocator allocator, int id) {
        return new ShapeBasic(layout, sharedData, parent, objectType, propertyMap, transition, allocator, id);
    }

    @Override
    public ShapeImpl replaceProperty(Property oldProperty, Property newProperty) {
        return directReplaceProperty(oldProperty, newProperty);
    }
}
