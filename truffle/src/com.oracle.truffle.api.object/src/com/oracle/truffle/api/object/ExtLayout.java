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

import static com.oracle.truffle.api.object.ObjectStorageOptions.booleanOption;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.graalvm.nativeimage.ImageInfo;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;

final class ExtLayout extends LayoutImpl {
    public static final boolean TraceReshape = booleanOption(OPTION_PREFIX + "TraceReshape", false);
    public static final boolean PrimitiveLocations = booleanOption(OPTION_PREFIX + "PrimitiveLocations", true);
    public static final boolean IntegerLocations = booleanOption(OPTION_PREFIX + "IntegerLocations", true);
    public static final boolean DoubleLocations = booleanOption(OPTION_PREFIX + "DoubleLocations", true);
    public static final boolean LongLocations = booleanOption(OPTION_PREFIX + "LongLocations", true);
    public static final boolean BooleanLocations = booleanOption(OPTION_PREFIX + "BooleanLocations", true);
    public static final boolean InObjectFields = booleanOption(OPTION_PREFIX + "InObjectFields", true);
    public static final boolean UseVarHandle = booleanOption(OPTION_PREFIX + "UseVarHandle", false);

    public static final boolean NewFinalSpeculation = booleanOption(OPTION_PREFIX + "NewFinalSpeculation", true);
    public static final boolean NewTypeSpeculation = booleanOption(OPTION_PREFIX + "NewTypeSpeculation", true);
    /** Number of parent shapes to compare to check if compatible shapes can be merged. */
    public static final int MaxMergeDepth = Integer.getInteger(OPTION_PREFIX + "MaxMergeDepth", 32);
    /** Number of differing, compatible property locations allowed when merging shapes. */
    public static final int MaxMergeDiff = Integer.getInteger(OPTION_PREFIX + "MaxMergeDiff", 2);

    private final ExtLayoutStrategy strategy;
    private final List<FieldInfo> objectFields;
    private final List<FieldInfo> primitiveFields;
    private final int primitiveFieldMaxSize;

    ExtLayout(Class<? extends DynamicObject> dynamicObjectClass, ExtLayoutStrategy strategy, LayoutInfo layoutInfo, int allowedImplicitCasts) {
        super(dynamicObjectClass, strategy, allowedImplicitCasts);
        this.strategy = strategy;

        this.objectFields = layoutInfo.objectFields;
        this.primitiveFields = layoutInfo.primitiveFields;
        this.primitiveFieldMaxSize = layoutInfo.primitiveFieldMaxSize;
    }

    private static LayoutInfo getOrCreateLayoutInfo(Class<? extends DynamicObject> dynamicObjectClass, Lookup layoutLookup) {
        Objects.requireNonNull(dynamicObjectClass, "DynamicObject layout class");
        return LayoutInfo.getOrCreateNewLayoutInfo(dynamicObjectClass, layoutLookup);
    }

    private static ExtLayout getOrCreateLayout(Class<? extends DynamicObject> clazz, Lookup layoutLookup, int implicitCastFlags, ExtLayoutStrategy strategy) {
        Objects.requireNonNull(clazz, "DynamicObject layout class");
        Key key = new Key(clazz, implicitCastFlags, strategy);
        ExtLayout layout = (ExtLayout) LAYOUT_MAP.get(key);
        if (layout != null) {
            return layout;
        }
        ExtLayout newLayout = new ExtLayout(clazz, strategy, getOrCreateLayoutInfo(clazz, layoutLookup), implicitCastFlags);
        layout = (ExtLayout) LAYOUT_MAP.putIfAbsent(key, newLayout);
        return layout == null ? newLayout : layout;
    }

    static ExtLayout createLayoutImpl(Class<? extends DynamicObject> clazz, Lookup layoutLookup, int implicitCastFlags, ExtLayoutStrategy strategy) {
        return getOrCreateLayout(clazz, layoutLookup, implicitCastFlags, strategy);
    }

    static ExtLayout createLayoutImpl(Class<? extends DynamicObject> clazz, Lookup layoutLookup, int implicitCastFlags) {
        return createLayoutImpl(clazz, layoutLookup, implicitCastFlags, ObsolescenceStrategy.singleton());
    }

    static void registerLayoutClass(Class<? extends DynamicObject> type, Lookup layoutLookup) {
        createLayoutImpl(type, layoutLookup, 0);
    }

    @Override
    protected ShapeImpl newShape(Object objectType, Object sharedData, int flags, Assumption constantObjectAssumption) {
        return new ShapeExt(this, sharedData, objectType, flags, constantObjectAssumption);
    }

    @Override
    protected int getObjectFieldCount() {
        return objectFields.size();
    }

    FieldInfo getObjectField(int index) {
        return objectFields.get(index);
    }

    FieldInfo getPrimitiveField(int index) {
        return primitiveFields.get(index);
    }

    static int getFieldSizeByClass(Class<?> c) {
        if (c.equals(Boolean.class) || c.equals(Byte.class) || c.equals(byte.class) || c.equals(boolean.class)) {
            return 1;
        } else if (c.equals(Short.class) || c.equals(Character.class) || c.equals(char.class) || c.equals(short.class)) {
            return 2;
        } else if (c.equals(Float.class) || c.equals(Integer.class) || c.equals(float.class) || c.equals(int.class)) {
            return 4;
        } else if (c.equals(Double.class) || c.equals(Long.class) || c.equals(double.class) || c.equals(long.class)) {
            return 8;
        } else {
            throw new IllegalArgumentException("Field size for class " + c + " is not supported.");
        }
    }

    @Override
    protected int getPrimitiveFieldCount() {
        return primitiveFields.size();
    }

    public int getPrimitiveFieldMaxSize() {
        return primitiveFieldMaxSize;
    }

    @Override
    public String toString() {
        return clazz.getName() + '(' + objectFields.size() + ',' + primitiveFields.size() + ')';
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
    public ShapeImpl.BaseAllocator createAllocator() {
        ExtLayout layout = this;
        return getStrategy().createAllocator(layout);
    }

    @Override
    public ExtLayoutStrategy getStrategy() {
        return strategy;
    }

    private static final class LayoutInfo {

        final Class<? extends DynamicObject> clazz;
        final List<FieldInfo> objectFields;
        final List<FieldInfo> primitiveFields;
        final int primitiveFieldMaxSize;

        static LayoutInfo getOrCreateNewLayoutInfo(Class<? extends DynamicObject> dynamicObjectClass, Lookup layoutLookup) {
            LayoutInfo layoutInfo = (LayoutInfo) LAYOUT_INFO_MAP.get(dynamicObjectClass);
            if (layoutInfo != null) {
                return layoutInfo;
            }

            if (ImageInfo.inImageRuntimeCode()) {
                throw new IllegalStateException("Layout not initialized ahead-of-time: " + dynamicObjectClass);
            }

            return createLayoutInfo(dynamicObjectClass, layoutLookup);
        }

        private static LayoutInfo createLayoutInfo(Class<? extends DynamicObject> dynamicObjectClass, Lookup layoutLookup) {
            LayoutInfo newLayoutInfo = new LayoutInfo(dynamicObjectClass, layoutLookup);
            LayoutInfo layoutInfo = (LayoutInfo) LAYOUT_INFO_MAP.putIfAbsent(dynamicObjectClass, newLayoutInfo);
            return layoutInfo == null ? newLayoutInfo : layoutInfo;
        }

        /**
         * New layout.
         */
        LayoutInfo(Class<? extends DynamicObject> clazz, Lookup layoutLookup) {
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

            int maxFieldSize = 0;
            for (FieldInfo fieldInfo : primitiveFields) {
                int fieldSize = getFieldSizeByClass(fieldInfo.type());
                maxFieldSize = Math.max(maxFieldSize, fieldSize);
            }
            this.primitiveFieldMaxSize = maxFieldSize;
        }

        /**
         * Collects dynamic fields in class hierarchy (from high to low).
         *
         * @return the class lowermost in the hierarchy declaring dynamic fields
         */
        private static Class<? extends DynamicObject> collectFields(Class<? extends DynamicObject> clazz, Class<? extends DynamicObject> stop, Lookup layoutLookup,
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
                            Lookup privateLookup = MethodHandles.privateLookupIn(clazz, layoutLookup);
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
                    } else if (field.getType() == int.class) {
                        primitiveFieldList.add(FieldInfo.fromField(field, varHandle));
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
            if (field.getType() != Object.class && field.getType() != int.class && field.getType() != long.class) {
                throw new IllegalArgumentException("@DynamicField annotated field type must be either Object or int or long: " + field);
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
