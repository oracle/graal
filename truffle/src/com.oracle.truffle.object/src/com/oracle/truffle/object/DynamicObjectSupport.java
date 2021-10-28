/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.object.LayoutImpl.ACCESS;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;

@SuppressWarnings("deprecation")
final class DynamicObjectSupport {

    private DynamicObjectSupport() {
    }

    static void ensureCapacity(DynamicObject object, Shape otherShape) {
        grow(object, object.getShape(), otherShape);
    }

    static void grow(DynamicObject object, Shape thisShape, Shape otherShape) {
        ShapeImpl thisShapeImpl = (ShapeImpl) thisShape;
        ShapeImpl otherShapeImpl = (ShapeImpl) otherShape;
        int sourceObjectCapacity = thisShapeImpl.getObjectArrayCapacity();
        int targetObjectCapacity = otherShapeImpl.getObjectArrayCapacity();
        if (sourceObjectCapacity < targetObjectCapacity) {
            growObjectStore(object, thisShapeImpl, sourceObjectCapacity, targetObjectCapacity);
        }
        int sourcePrimitiveCapacity = thisShapeImpl.getPrimitiveArrayCapacity();
        int targetPrimitiveCapacity = otherShapeImpl.getPrimitiveArrayCapacity();
        if (sourcePrimitiveCapacity < targetPrimitiveCapacity) {
            growPrimitiveStore(object, thisShapeImpl, sourcePrimitiveCapacity, targetPrimitiveCapacity);
        }
    }

    private static void growObjectStore(DynamicObject object, ShapeImpl thisShape, int sourceCapacity, int targetCapacity) {
        Object[] newObjectStore = new Object[targetCapacity];
        if (sourceCapacity != 0) {
            int sourceSize = thisShape.getObjectArraySize();
            Object[] oldObjectStore = ACCESS.getObjectArray(object);
            ACCESS.arrayCopy(oldObjectStore, newObjectStore, sourceSize);
        }
        ACCESS.setObjectArray(object, newObjectStore);
    }

    private static void growPrimitiveStore(DynamicObject object, ShapeImpl thisShape, int sourceCapacity, int targetCapacity) {
        int[] newPrimitiveArray = new int[targetCapacity];
        if (sourceCapacity != 0) {
            int sourceSize = thisShape.getPrimitiveArraySize();
            int[] oldPrimitiveArray = ACCESS.getPrimitiveArray(object);
            ACCESS.arrayCopy(oldPrimitiveArray, newPrimitiveArray, sourceSize);
        }
        ACCESS.setPrimitiveArray(object, newPrimitiveArray);
    }

    static void resize(DynamicObject object, Shape thisShape, Shape otherShape) {
        resizeObjectStore(object, thisShape, otherShape);
        resizePrimitiveStore(object, thisShape, otherShape);
    }

    static void trimToSize(DynamicObject object, Shape thisShape, Shape otherShape) {
        trimObjectStore(object, thisShape, otherShape);
        trimPrimitiveStore(object, thisShape, otherShape);
    }

    private static void resizeObjectStore(DynamicObject object, Shape oldShape, Shape newShape) {
        ShapeImpl oldShapeImpl = (ShapeImpl) oldShape;
        ShapeImpl newShapeImpl = (ShapeImpl) newShape;
        int destinationCapacity = newShapeImpl.getObjectArrayCapacity();
        if (destinationCapacity == 0) {
            ACCESS.setObjectArray(object, null);
        } else {
            int sourceCapacity = oldShapeImpl.getObjectArrayCapacity();
            if (sourceCapacity != destinationCapacity) {
                int sourceSize = oldShapeImpl.getObjectArraySize();
                Object[] newObjectStore = new Object[destinationCapacity];
                if (sourceSize != 0) {
                    Object[] oldObjectStore = ACCESS.getObjectArray(object);
                    int destinationSize = newShapeImpl.getObjectArraySize();
                    int length = Math.min(sourceSize, destinationSize);
                    ACCESS.arrayCopy(oldObjectStore, newObjectStore, length);
                }
                ACCESS.setObjectArray(object, newObjectStore);
            }
        }
    }

    private static void resizePrimitiveStore(DynamicObject object, Shape oldShape, Shape newShape) {
        ShapeImpl oldShapeImpl = (ShapeImpl) oldShape;
        ShapeImpl newShapeImpl = (ShapeImpl) newShape;
        assert newShapeImpl.hasPrimitiveArray();
        int destinationCapacity = newShapeImpl.getPrimitiveArrayCapacity();
        if (destinationCapacity == 0) {
            ACCESS.setPrimitiveArray(object, null);
        } else {
            int sourceCapacity = oldShapeImpl.getPrimitiveArrayCapacity();
            if (sourceCapacity != destinationCapacity) {
                int sourceSize = oldShapeImpl.getPrimitiveArraySize();
                int[] newPrimitiveArray = new int[destinationCapacity];
                if (sourceSize != 0) {
                    int[] oldPrimitiveArray = ACCESS.getPrimitiveArray(object);
                    int destinationSize = newShapeImpl.getPrimitiveArraySize();
                    int length = Math.min(sourceSize, destinationSize);
                    ACCESS.arrayCopy(oldPrimitiveArray, newPrimitiveArray, length);
                }
                ACCESS.setPrimitiveArray(object, newPrimitiveArray);
            }
        }
    }

    private static void trimObjectStore(DynamicObject object, Shape thisShape, Shape newShape) {
        ShapeImpl thisShapeImpl = (ShapeImpl) thisShape;
        ShapeImpl newShapeImpl = (ShapeImpl) newShape;
        Object[] oldObjectStore = ACCESS.getObjectArray(object);
        int destinationCapacity = newShapeImpl.getObjectArrayCapacity();
        if (destinationCapacity == 0) {
            if (oldObjectStore != null) {
                ACCESS.setObjectArray(object, null);
            }
            // else nothing to do
        } else {
            int sourceCapacity = thisShapeImpl.getObjectArrayCapacity();
            if (sourceCapacity > destinationCapacity) {
                Object[] newObjectStore = new Object[destinationCapacity];
                int destinationSize = newShapeImpl.getObjectArraySize();
                ACCESS.arrayCopy(oldObjectStore, newObjectStore, destinationSize);
                ACCESS.setObjectArray(object, newObjectStore);
            }
        }
    }

    private static void trimPrimitiveStore(DynamicObject object, Shape thisShape, Shape newShape) {
        ShapeImpl thisShapeImpl = (ShapeImpl) thisShape;
        ShapeImpl newShapeImpl = (ShapeImpl) newShape;
        int[] oldPrimitiveStore = ACCESS.getPrimitiveArray(object);
        int destinationCapacity = newShapeImpl.getPrimitiveArrayCapacity();
        if (destinationCapacity == 0) {
            if (oldPrimitiveStore != null) {
                ACCESS.setPrimitiveArray(object, null);
            }
            // else nothing to do
        } else {
            int sourceCapacity = thisShapeImpl.getPrimitiveArrayCapacity();
            if (sourceCapacity > destinationCapacity) {
                int[] newPrimitiveStore = new int[destinationCapacity];
                int destinationSize = newShapeImpl.getPrimitiveArraySize();
                ACCESS.arrayCopy(oldPrimitiveStore, newPrimitiveStore, destinationSize);
                ACCESS.setPrimitiveArray(object, newPrimitiveStore);
            }
        }
    }

    static Map<Object, Object> archive(DynamicObject object) {
        Map<Object, Object> archive = new HashMap<>();
        Property[] properties = ((ShapeImpl) object.getShape()).getPropertyArray();
        for (Property property : properties) {
            archive.put(property.getKey(), DynamicObjectLibrary.getUncached().getOrDefault(object, property.getKey(), null));
        }
        return archive;
    }

    static boolean verifyValues(DynamicObject object, Map<Object, Object> archive) {
        Property[] properties = ((ShapeImpl) object.getShape()).getPropertyArray();
        for (Property property : properties) {
            Object key = property.getKey();
            Object before = archive.get(key);
            Object after = DynamicObjectLibrary.getUncached().getOrDefault(object, key, null);
            assert Objects.equals(after, before) : "before != after for key: " + key;
        }
        return true;
    }

    @TruffleBoundary
    static void invalidateAllPropertyAssumptions(Shape shape) {
        ShapeImpl shapeImpl = (ShapeImpl) shape;
        if (shapeImpl.isLeaf()) {
            shapeImpl.invalidateLeafAssumption();
        }
        if (shapeImpl.allowPropertyAssumptions()) {
            shapeImpl.invalidateAllPropertyAssumptions();
        }
    }
}
