/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object.basic;

import java.util.EnumSet;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Layout;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.ObjectLocation;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.object.Shape.Allocator;
import com.oracle.truffle.object.LayoutImpl;
import com.oracle.truffle.object.LayoutStrategy;
import com.oracle.truffle.object.LocationImpl.InternalLongLocation;

public class BasicLayout extends LayoutImpl {
    private final ObjectLocation[] objectFields;
    private final InternalLongLocation[] primitiveFields;
    private final Location objectArrayLocation;
    private final Location primitiveArrayLocation;

    BasicLayout(EnumSet<ImplicitCast> allowedImplicitCasts, LayoutStrategy strategy) {
        super(allowedImplicitCasts, DynamicObjectBasic.class, strategy);
        this.objectFields = DynamicObjectBasic.OBJECT_FIELD_LOCATIONS;
        this.primitiveFields = DynamicObjectBasic.PRIMITIVE_FIELD_LOCATIONS;
        this.primitiveArrayLocation = DynamicObjectBasic.PRIMITIVE_ARRAY_LOCATION;
        this.objectArrayLocation = DynamicObjectBasic.OBJECT_ARRAY_LOCATION;
    }

    static LayoutImpl createLayoutImpl(Layout.Builder builder, LayoutStrategy strategy) {
        return new BasicLayout(getAllowedImplicitCasts(builder), strategy);
    }

    @Override
    public DynamicObject newInstance(Shape shape) {
        return new DynamicObjectBasic(shape);
    }

    @Override
    public Shape createShape(ObjectType objectType, Object sharedData, int id) {
        return new ShapeBasic(this, sharedData, objectType, id);
    }

    @Override
    protected boolean hasObjectExtensionArray() {
        return true;
    }

    @Override
    protected boolean hasPrimitiveExtensionArray() {
        return true;
    }

    @Override
    protected int getObjectFieldCount() {
        return objectFields.length;
    }

    @Override
    protected int getPrimitiveFieldCount() {
        return primitiveFields.length;
    }

    @Override
    protected Location getObjectArrayLocation() {
        return objectArrayLocation;
    }

    @Override
    protected Location getPrimitiveArrayLocation() {
        return primitiveArrayLocation;
    }

    protected ObjectLocation getObjectFieldLocation(int index) {
        return objectFields[index];
    }

    protected InternalLongLocation getPrimitiveFieldLocation(int index) {
        return primitiveFields[index];
    }

    @Override
    public Allocator createAllocator() {
        LayoutImpl layout = this;
        Allocator allocator = getStrategy().createAllocator(layout);
        return allocator;
    }
}
