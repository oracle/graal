/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
