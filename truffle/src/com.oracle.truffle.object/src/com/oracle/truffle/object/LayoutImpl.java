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
package com.oracle.truffle.object;

import java.util.EnumSet;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Layout;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.object.Shape.Allocator;

/** @since 0.17 or earlier */
public abstract class LayoutImpl extends Layout {
    private static final int INT_TO_DOUBLE_FLAG = 1;
    private static final int INT_TO_LONG_FLAG = 2;

    /** @since 0.17 or earlier */
    protected final LayoutStrategy strategy;
    /** @since 0.17 or earlier */
    protected final Class<? extends DynamicObject> clazz;
    private final int allowedImplicitCasts;

    /** @since 0.17 or earlier */
    protected LayoutImpl(EnumSet<ImplicitCast> allowedImplicitCasts, Class<? extends DynamicObjectImpl> clazz, LayoutStrategy strategy) {
        this.strategy = strategy;
        this.clazz = clazz;

        this.allowedImplicitCasts = (allowedImplicitCasts.contains(ImplicitCast.IntToDouble) ? INT_TO_DOUBLE_FLAG : 0) | (allowedImplicitCasts.contains(ImplicitCast.IntToLong) ? INT_TO_LONG_FLAG : 0);
    }

    /** @since 0.17 or earlier */
    @Override
    public abstract DynamicObject newInstance(Shape shape);

    /** @since 0.17 or earlier */
    @Override
    public Class<? extends DynamicObject> getType() {
        return clazz;
    }

    /** @since 0.17 or earlier */
    @Override
    public final Shape createShape(ObjectType objectType, Object sharedData) {
        return createShape(objectType, sharedData, 0);
    }

    /** @since 0.17 or earlier */
    @Override
    public final Shape createShape(ObjectType objectType) {
        return createShape(objectType, null);
    }

    /** @since 0.17 or earlier */
    public boolean isAllowedIntToDouble() {
        return (allowedImplicitCasts & INT_TO_DOUBLE_FLAG) != 0;
    }

    /** @since 0.17 or earlier */
    public boolean isAllowedIntToLong() {
        return (allowedImplicitCasts & INT_TO_LONG_FLAG) != 0;
    }

    /** @since 0.17 or earlier */
    protected abstract boolean hasObjectExtensionArray();

    /** @since 0.17 or earlier */
    protected abstract boolean hasPrimitiveExtensionArray();

    /** @since 0.17 or earlier */
    protected abstract int getObjectFieldCount();

    /** @since 0.17 or earlier */
    protected abstract int getPrimitiveFieldCount();

    /** @since 0.17 or earlier */
    protected abstract Location getObjectArrayLocation();

    /** @since 0.17 or earlier */
    protected abstract Location getPrimitiveArrayLocation();

    /** @since 0.17 or earlier */
    @Deprecated
    protected int objectFieldIndex(@SuppressWarnings("unused") Location location) {
        throw new UnsupportedOperationException();
    }

    /** @since 0.17 or earlier */
    @Override
    public abstract Allocator createAllocator();

    /** @since 0.17 or earlier */
    public LayoutStrategy getStrategy() {
        return strategy;
    }

    @Override
    public String toString() {
        return "Layout[" + clazz.getName() + "]";
    }

    static final class CoreAccess extends Access {
        private CoreAccess() {
        }
    }

    static final CoreAccess ACCESS = new CoreAccess();
}
