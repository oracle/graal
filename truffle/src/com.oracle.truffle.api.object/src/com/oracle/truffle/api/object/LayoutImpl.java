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

import static com.oracle.truffle.api.object.ObjectStorageOptions.UseVarHandle;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;

final class LayoutImpl extends com.oracle.truffle.api.object.Layout {
    private static final int INT_TO_DOUBLE_FLAG = 1;
    private static final int INT_TO_LONG_FLAG = 2;

    final LayoutStrategy strategy;
    final Class<? extends DynamicObject> clazz;
    private final int allowedImplicitCasts;

    private final List<FieldInfo> objectFields;
    private final List<FieldInfo> primitiveFields;

    LayoutImpl(Class<? extends DynamicObject> clazz, LayoutStrategy strategy, LayoutInfo layoutInfo, int allowedImplicitCasts) {
        this.strategy = strategy;
        this.clazz = Objects.requireNonNull(clazz);

        this.allowedImplicitCasts = allowedImplicitCasts;
        this.objectFields = layoutInfo.objectFields;
        this.primitiveFields = layoutInfo.primitiveFields;
    }

    @Override
    public Class<? extends DynamicObject> getType() {
        return clazz;
    }

    Shape newShape(Object objectType, Object sharedData, int flags, Assumption constantObjectAssumption) {
        return new Shape(this, objectType, sharedData, flags, constantObjectAssumption);
    }

    public boolean isAllowedIntToDouble() {
        return (allowedImplicitCasts & INT_TO_DOUBLE_FLAG) != 0;
    }

    public boolean isAllowedIntToLong() {
        return (allowedImplicitCasts & INT_TO_LONG_FLAG) != 0;
    }

    @SuppressWarnings("static-method")
    boolean hasObjectExtensionArray() {
        return true;
    }

    @SuppressWarnings("static-method")
    boolean hasPrimitiveExtensionArray() {
        return true;
    }

    int getObjectFieldCount() {
        return objectFields.size();
    }

    int getPrimitiveFieldCount() {
        return primitiveFields.size();
    }

    public LayoutStrategy getStrategy() {
        return strategy;
    }

    @Override
    public String toString() {
        return "Layout[" + clazz.getName() + "]" + '(' + objectFields.size() + ',' + primitiveFields.size() + ')';
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
        getFactory().registerLayoutClass(dynamicObjectClass.asSubclass(DynamicObject.class), lookup);
    }

    /**
     * @implNote this field is looked up reflectively by TruffleBaseFeature.
     */
    static final Map<Class<? extends DynamicObject>, Object> LAYOUT_INFO_MAP = new ConcurrentHashMap<>();
    /**
     * @implNote this field is looked up reflectively by TruffleBaseFeature.
     */
    static final Map<LayoutImpl.Key, LayoutImpl> LAYOUT_MAP = new ConcurrentHashMap<>();

    record Key(
                    Class<? extends DynamicObject> type,
                    int implicitCastFlags,
                    LayoutStrategy strategy) {

        // letting Java generate hashcodes has slow startup
        @Override
        public int hashCode() {
            return Objects.hash(type, implicitCastFlags, strategy);
        }

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

    private static LayoutInfo getOrCreateLayoutInfo(Class<? extends DynamicObject> dynamicObjectClass, MethodHandles.Lookup layoutLookup) {
        Objects.requireNonNull(dynamicObjectClass, "DynamicObject layout class");
        return LayoutInfo.getOrCreateNewLayoutInfo(dynamicObjectClass, layoutLookup);
    }

    private static LayoutImpl getOrCreateLayout(Class<? extends DynamicObject> clazz, MethodHandles.Lookup layoutLookup, int implicitCastFlags, LayoutStrategy strategy) {
        Objects.requireNonNull(clazz, "DynamicObject layout class");
        Key key = new Key(clazz, implicitCastFlags, strategy);
        LayoutImpl layout = LAYOUT_MAP.get(key);
        if (layout != null) {
            return layout;
        }
        LayoutImpl newLayout = new LayoutImpl(clazz, strategy, getOrCreateLayoutInfo(clazz, layoutLookup), implicitCastFlags);
        layout = LAYOUT_MAP.putIfAbsent(key, newLayout);
        return layout == null ? newLayout : layout;
    }

    static LayoutImpl createLayoutImpl(Class<? extends DynamicObject> clazz, MethodHandles.Lookup layoutLookup, int implicitCastFlags, LayoutStrategy strategy) {
        return getOrCreateLayout(clazz, layoutLookup, implicitCastFlags, strategy);
    }

    static LayoutImpl createLayoutImpl(Class<? extends DynamicObject> clazz, MethodHandles.Lookup layoutLookup, int implicitCastFlags) {
        return createLayoutImpl(clazz, layoutLookup, implicitCastFlags, ObsolescenceStrategy.singleton());
    }

    static void registerLayoutClass(Class<? extends DynamicObject> type, MethodHandles.Lookup layoutLookup) {
        createLayoutImpl(type, layoutLookup, 0);
    }

    FieldInfo getObjectField(int index) {
        return objectFields.get(index);
    }

    FieldInfo getPrimitiveField(int index) {
        return primitiveFields.get(index);
    }

    private static final class LayoutInfo {

        final Class<? extends DynamicObject> clazz;
        final List<FieldInfo> objectFields;
        final List<FieldInfo> primitiveFields;

        static LayoutInfo getOrCreateNewLayoutInfo(Class<? extends DynamicObject> dynamicObjectClass, MethodHandles.Lookup layoutLookup) {
            LayoutInfo layoutInfo = (LayoutInfo) LAYOUT_INFO_MAP.get(dynamicObjectClass);
            if (layoutInfo != null) {
                return layoutInfo;
            }

            if (ImageInfo.inImageRuntimeCode()) {
                throw new IllegalStateException("Layout not initialized ahead-of-time: " + dynamicObjectClass);
            }

            return createLayoutInfo(dynamicObjectClass, layoutLookup);
        }

        private static LayoutInfo createLayoutInfo(Class<? extends DynamicObject> dynamicObjectClass, MethodHandles.Lookup layoutLookup) {
            LayoutInfo newLayoutInfo = new LayoutInfo(dynamicObjectClass, layoutLookup);
            LayoutInfo layoutInfo = (LayoutInfo) LAYOUT_INFO_MAP.putIfAbsent(dynamicObjectClass, newLayoutInfo);
            return layoutInfo == null ? newLayoutInfo : layoutInfo;
        }

        /**
         * New layout.
         */
        LayoutInfo(Class<? extends DynamicObject> clazz, MethodHandles.Lookup layoutLookup) {
            this.clazz = clazz.asSubclass(DynamicObject.class);

            List<FieldInfo> objectFieldList = new ArrayList<>();
            List<FieldInfo> primitiveFieldList = new ArrayList<>();

            collectFields(clazz, DynamicObject.class, layoutLookup, objectFieldList, primitiveFieldList);

            Collections.sort(objectFieldList);
            Collections.sort(primitiveFieldList);

            if (objectFieldList.size() + primitiveFieldList.size() > ExtLocations.MAX_DYNAMIC_FIELDS) {
                throw new IllegalArgumentException("Too many @DynamicField annotated fields.");
            }

            this.objectFields = List.copyOf(objectFieldList);
            this.primitiveFields = List.copyOf(primitiveFieldList);
        }

        /**
         * Collects dynamic fields in class hierarchy (from high to low).
         *
         * @return the class lowermost in the hierarchy declaring dynamic fields
         */
        private static Class<? extends DynamicObject> collectFields(Class<? extends DynamicObject> clazz, Class<? extends DynamicObject> stop, MethodHandles.Lookup layoutLookup,
                        List<FieldInfo> objectFieldList, List<FieldInfo> primitiveFieldList) {
            if (clazz == DynamicObject.class || clazz == stop) {
                return clazz;
            }

            Class<? extends DynamicObject> layoutClass = collectFields(clazz.getSuperclass().asSubclass(DynamicObject.class), stop, layoutLookup, objectFieldList, primitiveFieldList);

            Class<? extends Annotation> dynamicFieldAnnotation = DynamicObject.getDynamicFieldAnnotation();
            boolean hasDynamicFields = false;
            for (Field field : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    assert !field.isAnnotationPresent(dynamicFieldAnnotation);
                    continue;
                }

                if (field.getAnnotation(dynamicFieldAnnotation) != null) {
                    checkDynamicFieldType(field);
                    assert field.getDeclaringClass() == clazz;

                    VarHandle varHandle = null;
                    if (layoutLookup != null) {
                        try {
                            MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(clazz, layoutLookup);
                            varHandle = privateLookup.findVarHandle(clazz, field.getName(), field.getType());
                        } catch (NoSuchFieldException | IllegalAccessException e) {
                            throw CompilerDirectives.shouldNotReachHere(e);
                        }
                    } else if (UseVarHandle) {
                        // Cannot use VarHandles without a Lookup.
                        continue;
                    }

                    hasDynamicFields = true;
                    if (field.getType() == Object.class) {
                        objectFieldList.add(FieldInfo.fromField(field, varHandle));
                    } else if (field.getType() == long.class) {
                        primitiveFieldList.add(FieldInfo.fromField(field, varHandle));
                    }
                }
            }

            if (hasDynamicFields) {
                layoutClass = clazz;
            }
            return layoutClass;
        }

        private static void checkDynamicFieldType(Field field) {
            if (field.getType() != Object.class && field.getType() != long.class) {
                throw new IllegalArgumentException("@DynamicField annotated field type must be either Object or long: " + field);
            }
            if (Modifier.isFinal(field.getModifiers())) {
                throw new IllegalArgumentException("@DynamicField annotated field must not be final: " + field);
            }
        }

        @Override
        public String toString() {
            return getClass().getName() + '[' + clazz.getName() + ']';
        }
    }
}
