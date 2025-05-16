/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

final class ShapeExt extends ShapeImpl {

    /**
     * The successor shape if this shape is obsolete.
     */
    private volatile ShapeImpl successorShape;
    /**
     * This reference keeps shape transitions to obsolete shapes alive as long as the successor
     * shape is reachable in order to ensure that no new shapes are created for those transitions.
     * Otherwise, dead obsolete shapes might be recreated and then obsoleted again.
     *
     * Either null, a single ShapeImpl, or a copy-on-write ShapeImpl[] array.
     */
    private volatile Object predecessorShape;

    private static final VarHandle PREDECESSOR_SHAPE_UPDATER;
    static {
        var lookup = MethodHandles.lookup();
        try {
            PREDECESSOR_SHAPE_UPDATER = lookup.findVarHandle(ShapeExt.class, "predecessorShape", Object.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    ShapeExt(com.oracle.truffle.api.object.Layout layout, Object sharedData, Object objectType, int flags, Assumption singleContextAssumption) {
        super(layout, objectType, sharedData, flags, singleContextAssumption);
    }

    ShapeExt(com.oracle.truffle.api.object.Layout layout, Object sharedData, ShapeImpl parent, Object objectType, PropertyMap propertyMap,
                    Transition transition, BaseAllocator allocator, int flags) {
        super(layout, parent, objectType, sharedData, propertyMap, transition, allocator, flags);
    }

    @SuppressWarnings("hiding")
    @Override
    protected ShapeImpl createShape(com.oracle.truffle.api.object.Layout layout, Object sharedData, ShapeImpl parent, Object objectType, PropertyMap propertyMap,
                    Transition transition, BaseAllocator allocator, int flags) {
        return new ShapeExt(layout, sharedData, parent, objectType, propertyMap, transition, allocator, flags);
    }

    @Override
    public ShapeImpl getRoot() {
        return UnsafeAccess.unsafeCast(super.getRoot(), ShapeImpl.class, true, true);
    }

    @TruffleBoundary
    @Override
    public Shape tryMerge(Shape other) {
        // double-checked locking is safe since isValid() boils down to a volatile field load.
        if (this != other && this.isValid() && other.isValid()) {
            return Obsolescence.tryObsoleteDowncast(this, (ShapeImpl) other);
        }
        return null;
    }

    void setSuccessorShape(ShapeImpl successorShape) {
        this.successorShape = successorShape;
    }

    ShapeImpl getSuccessorShape() {
        return successorShape;
    }

    void addPredecessorShape(ShapeImpl nextShape) {
        Object prev;
        Object next;
        do {
            prev = predecessorShape;
            if (prev == null) {
                next = nextShape;
            } else if (prev instanceof ShapeImpl prevShape) {
                if (prevShape == nextShape) {
                    break;
                }
                next = new ShapeImpl[]{prevShape, nextShape};
            } else {
                ShapeImpl[] prevArray = (ShapeImpl[]) prev;
                for (ShapeImpl prevShape : prevArray) {
                    if (prevShape == nextShape) {
                        break;
                    }
                }
                ShapeImpl[] nextArray = Arrays.copyOf(prevArray, prevArray.length + 1);
                nextArray[prevArray.length] = nextShape;
                next = nextArray;
            }
        } while (!PREDECESSOR_SHAPE_UPDATER.compareAndSet(this, prev, next));
    }
}
