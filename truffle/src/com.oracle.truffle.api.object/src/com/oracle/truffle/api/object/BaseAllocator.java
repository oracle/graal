/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

abstract sealed class BaseAllocator implements LocationImpl.LocationVisitor permits ExtAllocator {
    protected final LayoutImpl layout;
    protected int objectArraySize;
    protected int objectFieldSize;
    protected int primitiveFieldSize;
    protected int primitiveArraySize;
    protected int depth;
    protected boolean shared;

    protected BaseAllocator(LayoutImpl layout) {
        this.layout = layout;
    }

    protected BaseAllocator(Shape shape) {
        this(shape.getLayout());
        this.objectArraySize = shape.getObjectArraySize();
        this.objectFieldSize = shape.getObjectFieldSize();
        this.primitiveFieldSize = shape.getPrimitiveFieldSize();
        this.primitiveArraySize = shape.getPrimitiveArraySize();
        this.depth = shape.getDepth();
        this.shared = shape.isShared();
    }

    protected abstract Location moveLocation(Location oldLocation);

    /**
     * Creates a new location from a constant value. The value is stored in the shape rather than in
     * the object.
     *
     * @param value the constant value
     */
    public Location constantLocation(Object value) {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a new declared location with a default value. A declared location only assumes a type
     * after the first set (initialization).
     * <p>
     * Used by tests.
     *
     * @param value the default value
     */
    public Location declaredLocation(Object value) {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a new location compatible with the given initial value.
     * <p>
     * Used by tests.
     */
    public abstract Location locationForValue(Object value);

    protected abstract Location locationForValueUpcast(Object value, Location oldLocation, int putFlags);

    protected <T extends Location> T advance(T location0) {
        if (location0 instanceof LocationImpl location) {
            location.accept(this);
            assert layout.hasPrimitiveExtensionArray() || primitiveArraySize == 0;
        }
        depth++;
        return location0;
    }

    /**
     * Reserves space for the given location, so that it will not be available to subsequently
     * allocated locations.
     */
    public BaseAllocator addLocation(Location location) {
        advance(location);
        return this;
    }

    @Override
    public void visitObjectField(int index, int count) {
        objectFieldSize = Math.max(objectFieldSize, index + count);
    }

    @Override
    public void visitObjectArray(int index, int count) {
        objectArraySize = Math.max(objectArraySize, index + count);
    }

    @Override
    public void visitPrimitiveArray(int index, int count) {
        primitiveArraySize = Math.max(primitiveArraySize, index + count);
    }

    @Override
    public void visitPrimitiveField(int index, int count) {
        primitiveFieldSize = Math.max(primitiveFieldSize, index + count);
    }
}
