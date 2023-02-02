/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.graalvm.nativeimage.ImageInfo;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.object.CoreLocations.LongLocation;
import com.oracle.truffle.object.CoreLocations.ObjectLocation;

@SuppressWarnings("deprecation")
class DefaultLayout extends LayoutImpl {
    private final ObjectLocation[] objectFields;
    private final LongLocation[] primitiveFields;

    static final ObjectLocation[] NO_OBJECT_FIELDS = new ObjectLocation[0];
    static final LongLocation[] NO_LONG_FIELDS = new LongLocation[0];

    private static final Map<Key, DefaultLayout> LAYOUT_MAP = new ConcurrentHashMap<>();

    DefaultLayout(Class<? extends DynamicObject> dynamicObjectClass, LayoutStrategy strategy, int implicitCastFlags, ObjectLocation[] objectFields, LongLocation[] primitiveFields) {
        super(dynamicObjectClass, strategy, implicitCastFlags);
        this.objectFields = objectFields;
        this.primitiveFields = primitiveFields;
    }

    DefaultLayout(Class<? extends DynamicObject> dynamicObjectClass, LayoutStrategy strategy, int implicitCastFlags) {
        super(dynamicObjectClass, strategy, implicitCastFlags);
        if (DynamicObject.class == dynamicObjectClass) {
            this.objectFields = NO_OBJECT_FIELDS;
            this.primitiveFields = NO_LONG_FIELDS;
        } else if (DynamicObject.class.isAssignableFrom(dynamicObjectClass)) {
            LayoutInfo layoutInfo = LayoutInfo.getOrCreateLayoutInfo(dynamicObjectClass);
            this.objectFields = layoutInfo.objectFields;
            this.primitiveFields = layoutInfo.primitiveFields;
        } else {
            throw new IllegalArgumentException(dynamicObjectClass.getName());
        }
    }

    static LayoutImpl createCoreLayout(Class<? extends DynamicObject> type, int implicitCastFlags) {
        return getOrCreateLayout(type, implicitCastFlags);
    }

    private static DefaultLayout getOrCreateLayout(Class<? extends DynamicObject> type, int implicitCastFlags) {
        Objects.requireNonNull(type, "DynamicObject layout class");
        Key key = new Key(type, implicitCastFlags);
        DefaultLayout layout = LAYOUT_MAP.get(key);
        if (layout != null) {
            return layout;
        }
        DefaultLayout newLayout = new DefaultLayout(type, DefaultStrategy.SINGLETON, implicitCastFlags);
        layout = LAYOUT_MAP.putIfAbsent(key, newLayout);
        return layout == null ? newLayout : layout;
    }

    static void registerLayoutClass(Class<? extends DynamicObject> type) {
        createCoreLayout(type, 0);
    }

    @Override
    protected boolean isLegacyLayout() {
        return false;
    }

    static UnsupportedOperationException unsupported() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new UnsupportedOperationException("not supported by this object layout");
    }

    @Override
    protected ShapeImpl newShape(Object objectType, Object sharedData, int flags, Assumption singleContextAssumption) {
        return new ShapeBasic(this, sharedData, objectType, flags, singleContextAssumption);
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

    protected ObjectLocation getObjectFieldLocation(int index) {
        return objectFields[index];
    }

    protected LongLocation getPrimitiveFieldLocation(int index) {
        return primitiveFields[index];
    }

    protected int getLongFieldSize() {
        return CoreLocations.LONG_FIELD_SLOT_SIZE;
    }

    @Override
    public ShapeImpl.BaseAllocator createAllocator() {
        LayoutImpl layout = this;
        return getStrategy().createAllocator(layout);
    }

    private static final class Key {
        final Class<? extends DynamicObject> type;
        final int implicitCastFlags;

        Key(Class<? extends DynamicObject> type, int implicitCastFlags) {
            this.type = type;
            this.implicitCastFlags = implicitCastFlags;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + implicitCastFlags;
            result = prime * result + ((type == null) ? 0 : type.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Key)) {
                return false;
            }
            Key other = (Key) obj;
            return this.type == other.type && this.implicitCastFlags == other.implicitCastFlags;
        }
    }

    private static final class LayoutInfo {
        final ObjectLocation[] objectFields;
        final LongLocation[] primitiveFields;

        private static final ConcurrentMap<Class<? extends DynamicObject>, LayoutInfo> LAYOUT_INFO_MAP = new ConcurrentHashMap<>();

        static LayoutInfo getOrCreateLayoutInfo(Class<? extends DynamicObject> dynamicObjectClass) {
            LayoutInfo layoutInfo = LAYOUT_INFO_MAP.get(dynamicObjectClass);
            if (layoutInfo != null) {
                return layoutInfo;
            }

            if (ImageInfo.inImageRuntimeCode()) {
                throw new IllegalStateException("Layout not initialized ahead-of-time: " + dynamicObjectClass);
            }

            return createLayoutInfo(dynamicObjectClass);
        }

        private static LayoutInfo createLayoutInfo(Class<? extends DynamicObject> dynamicObjectClass) {
            Class<? extends DynamicObject> subclass = dynamicObjectClass.asSubclass(DynamicObject.class);
            List<ObjectLocation> objectFieldList = new ArrayList<>();
            List<LongLocation> longFieldList = new ArrayList<>();
            Class<? extends DynamicObject> superclass = collectFields(subclass, objectFieldList, longFieldList);

            if (objectFieldList.size() + longFieldList.size() > CoreLocations.MAX_DYNAMIC_FIELDS) {
                throw new IllegalArgumentException("Too many @DynamicField annotated fields.");
            }

            LayoutInfo newLayoutInfo;
            if (superclass != subclass) {
                // This class does not declare any dynamic fields; reuse info from superclass
                newLayoutInfo = getOrCreateLayoutInfo(superclass);
            } else {
                newLayoutInfo = new LayoutInfo(objectFieldList, longFieldList);
            }
            LayoutInfo layoutInfo = LAYOUT_INFO_MAP.putIfAbsent(dynamicObjectClass, newLayoutInfo);
            return layoutInfo == null ? newLayoutInfo : layoutInfo;
        }

        private LayoutInfo(List<ObjectLocation> objectFieldList, List<LongLocation> longFieldList) {
            this.objectFields = objectFieldList.toArray(NO_OBJECT_FIELDS);
            this.primitiveFields = longFieldList.toArray(NO_LONG_FIELDS);
        }

        /**
         * Collects dynamic fields in class hierarchy (from high to low).
         *
         * @return the class lowermost in the hierarchy declaring dynamic fields
         */
        private static Class<? extends DynamicObject> collectFields(Class<? extends DynamicObject> clazz, List<ObjectLocation> objectFieldList, List<LongLocation> primitiveFieldList) {
            if (clazz == DynamicObject.class) {
                return clazz;
            }

            Class<? extends DynamicObject> layoutClass = collectFields(clazz.getSuperclass().asSubclass(DynamicObject.class), objectFieldList, primitiveFieldList);

            Class<? extends Annotation> dynamicFieldAnnotation = ACCESS.getDynamicFieldAnnotation();
            boolean hasDynamicFields = false;
            for (Field field : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    assert !field.isAnnotationPresent(dynamicFieldAnnotation);
                    continue;
                }

                if (field.getAnnotation(dynamicFieldAnnotation) != null) {
                    checkDynamicFieldType(field);
                    assert field.getDeclaringClass() == clazz;

                    hasDynamicFields = true;
                    if (field.getType() == Object.class) {
                        objectFieldList.add(new CoreLocations.DynamicObjectFieldLocation(objectFieldList.size(), field));
                    } else if (field.getType() == long.class) {
                        long offset = UnsafeAccess.objectFieldOffset(field);
                        if (offset % Long.BYTES == 0) {
                            primitiveFieldList.add(new CoreLocations.DynamicLongFieldLocation(primitiveFieldList.size(), offset, clazz));
                        }
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
            return "LayoutInfo [objectFields=" + Arrays.toString(objectFields) + ", primitiveFields=" + Arrays.toString(primitiveFields) + "]";
        }
    }

    /**
     * Resets the state for native image generation.
     */
    static void resetNativeImageState() {
        LAYOUT_MAP.clear();
        LayoutInfo.LAYOUT_INFO_MAP.clear();
    }
}
