/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

@SuppressWarnings("static-method")
final class DynamicObjectSupport {

    private DynamicObjectSupport() {
    }

    static void ensureCapacity(DynamicObject object, Shape otherShape) {
        growObjectStore(object, object.getShape(), otherShape);
        growPrimitiveStore(object, object.getShape(), otherShape);
    }

    static void grow(DynamicObject object, Shape thisShape, Shape otherShape) {
        growObjectStore(object, thisShape, otherShape);
        growPrimitiveStore(object, thisShape, otherShape);
    }

    static void resize(DynamicObject object, Shape thisShape, Shape otherShape) {
        resizeObjectStore(object, thisShape, otherShape);
        resizePrimitiveStore(object, thisShape, otherShape);
    }

    static void trimToSize(DynamicObject object, Shape thisShape) {
        trimObjectStore(object, thisShape);
        trimPrimitiveStore(object, thisShape);
    }

    static void growAndSetShape(DynamicObject object, Shape thisShape, Shape otherShape) {
        grow(object, thisShape, otherShape);
        ACCESS.setShape(object, otherShape);
    }

    static void resizeAndSetShape(DynamicObject object, Shape thisShape, Shape otherShape) {
        resize(object, thisShape, otherShape);
        ACCESS.setShape(object, otherShape);
    }

    private static void growObjectStore(DynamicObject object, Shape oldShape, Shape newShape) {
        int sourceCapacity = getObjectArrayCapacity(oldShape);
        int destinationCapacity = getObjectArrayCapacity(newShape);
        if (sourceCapacity < destinationCapacity) {
            Object[] newObjectStore = new Object[destinationCapacity];
            if (sourceCapacity != 0) {
                int sourceSize = getObjectArraySize(oldShape);
                Object[] oldObjectStore = ACCESS.getObjectArray(object);
                ACCESS.arrayCopy(oldObjectStore, newObjectStore, sourceSize);
            }
            ACCESS.setObjectArray(object, newObjectStore);
        }
    }

    private static void resizeObjectStore(DynamicObject object, Shape oldShape, Shape newShape) {
        int destinationCapacity = getObjectArrayCapacity(newShape);
        if (destinationCapacity == 0) {
            ACCESS.setObjectArray(object, null);
        } else {
            int sourceCapacity = getObjectArrayCapacity(oldShape);
            if (sourceCapacity != destinationCapacity) {
                int sourceSize = getObjectArraySize(oldShape);
                Object[] newObjectStore = new Object[destinationCapacity];
                if (sourceSize != 0) {
                    Object[] oldObjectStore = ACCESS.getObjectArray(object);
                    int destinationSize = getObjectArraySize(newShape);
                    int length = Math.min(sourceSize, destinationSize);
                    ACCESS.arrayCopy(oldObjectStore, newObjectStore, length);
                }
                ACCESS.setObjectArray(object, newObjectStore);
            }
        }
    }

    private static void growPrimitiveStore(DynamicObject object, Shape oldShape, Shape newShape) {
        int sourceCapacity = getPrimitiveArrayCapacity(oldShape);
        int destinationCapacity = getPrimitiveArrayCapacity(newShape);
        if (sourceCapacity < destinationCapacity) {
            int[] newPrimitiveArray = new int[destinationCapacity];
            if (sourceCapacity != 0) {
                int sourceSize = getPrimitiveArraySize(oldShape);
                int[] oldPrimitiveArray = ACCESS.getPrimitiveArray(object);
                ACCESS.arrayCopy(oldPrimitiveArray, newPrimitiveArray, sourceSize);
            }
            ACCESS.setPrimitiveArray(object, newPrimitiveArray);
        }
    }

    private static void resizePrimitiveStore(DynamicObject object, Shape oldShape, Shape newShape) {
        assert hasPrimitiveArray(newShape);
        int destinationCapacity = getPrimitiveArrayCapacity(newShape);
        if (destinationCapacity == 0) {
            ACCESS.setPrimitiveArray(object, null);
        } else {
            int sourceCapacity = getPrimitiveArrayCapacity(oldShape);
            if (sourceCapacity != destinationCapacity) {
                int sourceSize = getPrimitiveArraySize(oldShape);
                int[] newPrimitiveArray = new int[destinationCapacity];
                if (sourceSize != 0) {
                    int[] oldPrimitiveArray = ACCESS.getPrimitiveArray(object);
                    int destinationSize = getPrimitiveArraySize(newShape);
                    int length = Math.min(sourceSize, destinationSize);
                    ACCESS.arrayCopy(oldPrimitiveArray, newPrimitiveArray, length);
                }
                ACCESS.setPrimitiveArray(object, newPrimitiveArray);
            }
        }
    }

    private static void trimObjectStore(DynamicObject object, Shape thisShape) {
        Object[] oldObjectStore = ACCESS.getObjectArray(object);
        Shape curShape = ACCESS.getShape(object);
        assert curShape == thisShape;
        int destinationCapacity = getObjectArrayCapacity(curShape);
        if (destinationCapacity == 0) {
            if (oldObjectStore != null) {
                ACCESS.setObjectArray(object, null);
            }
            // else nothing to do
        } else {
            int sourceCapacity = oldObjectStore.length;
            if (sourceCapacity > destinationCapacity) {
                Object[] newObjectStore = new Object[destinationCapacity];
                int destinationSize = getObjectArraySize(curShape);
                ACCESS.arrayCopy(oldObjectStore, newObjectStore, destinationSize);
                ACCESS.setObjectArray(object, newObjectStore);
            }
        }
    }

    private static void trimPrimitiveStore(DynamicObject object, Shape thisShape) {
        int[] oldPrimitiveStore = ACCESS.getPrimitiveArray(object);
        Shape curShape = ACCESS.getShape(object);
        assert curShape == thisShape;
        int destinationCapacity = getPrimitiveArrayCapacity(curShape);
        if (destinationCapacity == 0) {
            if (oldPrimitiveStore != null) {
                ACCESS.setPrimitiveArray(object, null);
            }
            // else nothing to do
        } else {
            int sourceCapacity = oldPrimitiveStore.length;
            if (sourceCapacity > destinationCapacity) {
                int[] newPrimitiveStore = new int[destinationCapacity];
                int destinationSize = getPrimitiveArraySize(curShape);
                System.arraycopy(oldPrimitiveStore, 0, newPrimitiveStore, 0, destinationSize);
                ACCESS.setPrimitiveArray(object, newPrimitiveStore);
            }
        }
    }

    private static int getObjectArrayCapacity(Shape shape) {
        return ((ShapeImpl) shape).getObjectArrayCapacity();
    }

    private static int getObjectArraySize(Shape shape) {
        return ((ShapeImpl) shape).getObjectArraySize();
    }

    private static int getPrimitiveArrayCapacity(Shape shape) {
        return ((ShapeImpl) shape).getPrimitiveArrayCapacity();
    }

    private static int getPrimitiveArraySize(Shape shape) {
        return ((ShapeImpl) shape).getPrimitiveArraySize();
    }

    private static boolean hasPrimitiveArray(Shape shape) {
        return ((ShapeImpl) shape).hasPrimitiveArray();
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
