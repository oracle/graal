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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.truffle.api.Assumption;

abstract class LayoutImpl extends com.oracle.truffle.api.object.Layout {
    private static final int INT_TO_DOUBLE_FLAG = 1;
    private static final int INT_TO_LONG_FLAG = 2;

    protected final LayoutStrategy strategy;
    protected final Class<? extends DynamicObject> clazz;
    private final int allowedImplicitCasts;

    protected LayoutImpl(Class<? extends DynamicObject> clazz, LayoutStrategy strategy, int implicitCastFlags) {
        this.strategy = strategy;
        this.clazz = Objects.requireNonNull(clazz);

        this.allowedImplicitCasts = implicitCastFlags;
    }

    @Override
    public Class<? extends DynamicObject> getType() {
        return clazz;
    }

    @Override
    protected final Shape buildShape(Object dynamicType, Object sharedData, int flags, Assumption singleContextAssumption) {
        return newShape(dynamicType, sharedData, flags, null);
    }

    protected abstract ShapeImpl newShape(Object objectType, Object sharedData, int flags, Assumption singleContextAssumption);

    public boolean isAllowedIntToDouble() {
        return (allowedImplicitCasts & INT_TO_DOUBLE_FLAG) != 0;
    }

    public boolean isAllowedIntToLong() {
        return (allowedImplicitCasts & INT_TO_LONG_FLAG) != 0;
    }

    protected abstract boolean hasObjectExtensionArray();

    protected abstract boolean hasPrimitiveExtensionArray();

    protected abstract int getObjectFieldCount();

    protected abstract int getPrimitiveFieldCount();

    public abstract ShapeImpl.BaseAllocator createAllocator();

    public LayoutStrategy getStrategy() {
        return strategy;
    }

    @Override
    public String toString() {
        return "Layout[" + clazz.getName() + "]";
    }

    /**
     * Resets the state for native image generation.
     *
     * @implNote this method is called reflectively by downstream projects.
     * @since 25.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    static void resetNativeImageState() {
        assert ImageInfo.inImageBuildtimeCode() : "Only supported during image generation";
        LAYOUT_MAP.clear();
        LAYOUT_INFO_MAP.clear();
    }

    /**
     * Preinitializes DynamicObject layouts for native image generation.
     *
     * @implNote this method is called reflectively by downstream projects.
     * @since 25.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    static void initializeDynamicObjectLayout(Class<?> dynamicObjectClass, MethodHandles.Lookup lookup) {
        assert ImageInfo.inImageBuildtimeCode() : "Only supported during image generation";
        ((CoreLayoutFactory) getFactory()).registerLayoutClass(dynamicObjectClass.asSubclass(DynamicObject.class), lookup);
    }

    protected static final Map<Class<? extends DynamicObject>, Object> LAYOUT_INFO_MAP = new ConcurrentHashMap<>();
    protected static final Map<LayoutImpl.Key, LayoutImpl> LAYOUT_MAP = new ConcurrentHashMap<>();

    protected record Key(
                    Class<? extends DynamicObject> type,
                    int implicitCastFlags,
                    LayoutStrategy strategy) {

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Key other)) {
                return false;
            }
            return this.type == other.type && this.implicitCastFlags == other.implicitCastFlags && this.strategy == other.strategy;
        }
    }
}
