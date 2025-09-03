/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.VarHandle;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

final class DynamicObjectSupport {

    private DynamicObjectSupport() {
    }

    static void setShapeWithStoreFence(DynamicObject object, Shape shape) {
        if (shape.isShared()) {
            VarHandle.storeStoreFence();
        }
        object.setShape(shape);
    }

    static void ensureCapacity(DynamicObject object, Shape otherShape) {
        grow(object, object.getShape(), otherShape);
    }

    static void grow(DynamicObject object, Shape thisShape, Shape otherShape) {
        int sourceObjectCapacity = thisShape.getObjectArrayCapacity();
        int targetObjectCapacity = otherShape.getObjectArrayCapacity();
        if (sourceObjectCapacity < targetObjectCapacity) {
            growObjectStore(object, thisShape, sourceObjectCapacity, targetObjectCapacity);
        }
        int sourcePrimitiveCapacity = thisShape.getPrimitiveArrayCapacity();
        int targetPrimitiveCapacity = otherShape.getPrimitiveArrayCapacity();
        if (sourcePrimitiveCapacity < targetPrimitiveCapacity) {
            growPrimitiveStore(object, thisShape, sourcePrimitiveCapacity, targetPrimitiveCapacity);
        }
    }

    private static void growObjectStore(DynamicObject object, Shape thisShape, int sourceCapacity, int targetCapacity) {
        Object[] newObjectStore = new Object[targetCapacity];
        if (sourceCapacity != 0) {
            int sourceSize = thisShape.getObjectArraySize();
            Object[] oldObjectStore = object.getObjectStore();
            UnsafeAccess.arrayCopy(oldObjectStore, newObjectStore, sourceSize);
        }
        object.setObjectStore(newObjectStore);
    }

    private static void growPrimitiveStore(DynamicObject object, Shape thisShape, int sourceCapacity, int targetCapacity) {
        int[] newPrimitiveArray = new int[targetCapacity];
        if (sourceCapacity != 0) {
            int sourceSize = thisShape.getPrimitiveArraySize();
            int[] oldPrimitiveArray = object.getPrimitiveStore();
            UnsafeAccess.arrayCopy(oldPrimitiveArray, newPrimitiveArray, sourceSize);
        }
        object.setPrimitiveStore(newPrimitiveArray);
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
        int destinationCapacity = newShape.getObjectArrayCapacity();
        if (destinationCapacity == 0) {
            object.setObjectStore(null);
        } else {
            int sourceCapacity = oldShape.getObjectArrayCapacity();
            if (sourceCapacity != destinationCapacity) {
                int sourceSize = oldShape.getObjectArraySize();
                Object[] newObjectStore = new Object[destinationCapacity];
                if (sourceSize != 0) {
                    Object[] oldObjectStore = object.getObjectStore();
                    int destinationSize = newShape.getObjectArraySize();
                    int length = Math.min(sourceSize, destinationSize);
                    UnsafeAccess.arrayCopy(oldObjectStore, newObjectStore, length);
                }
                object.setObjectStore(newObjectStore);
            }
        }
    }

    private static void resizePrimitiveStore(DynamicObject object, Shape oldShape, Shape newShape) {
        assert newShape.hasPrimitiveArray();
        int destinationCapacity = newShape.getPrimitiveArrayCapacity();
        if (destinationCapacity == 0) {
            object.setPrimitiveStore(null);
        } else {
            int sourceCapacity = oldShape.getPrimitiveArrayCapacity();
            if (sourceCapacity != destinationCapacity) {
                int sourceSize = oldShape.getPrimitiveArraySize();
                int[] newPrimitiveArray = new int[destinationCapacity];
                if (sourceSize != 0) {
                    int[] oldPrimitiveArray = object.getPrimitiveStore();
                    int destinationSize = newShape.getPrimitiveArraySize();
                    int length = Math.min(sourceSize, destinationSize);
                    UnsafeAccess.arrayCopy(oldPrimitiveArray, newPrimitiveArray, length);
                }
                object.setPrimitiveStore(newPrimitiveArray);
            }
        }
    }

    private static void trimObjectStore(DynamicObject object, Shape thisShape, Shape newShape) {
        Object[] oldObjectStore = object.getObjectStore();
        int destinationCapacity = newShape.getObjectArrayCapacity();
        if (destinationCapacity == 0) {
            if (oldObjectStore != null) {
                object.setObjectStore(null);
            }
            // else nothing to do
        } else {
            int sourceCapacity = thisShape.getObjectArrayCapacity();
            if (sourceCapacity > destinationCapacity) {
                Object[] newObjectStore = new Object[destinationCapacity];
                int destinationSize = newShape.getObjectArraySize();
                UnsafeAccess.arrayCopy(oldObjectStore, newObjectStore, destinationSize);
                object.setObjectStore(newObjectStore);
            }
        }
    }

    private static void trimPrimitiveStore(DynamicObject object, Shape thisShape, Shape newShape) {
        int[] oldPrimitiveStore = object.getPrimitiveStore();
        int destinationCapacity = newShape.getPrimitiveArrayCapacity();
        if (destinationCapacity == 0) {
            if (oldPrimitiveStore != null) {
                object.setPrimitiveStore(null);
            }
            // else nothing to do
        } else {
            int sourceCapacity = thisShape.getPrimitiveArrayCapacity();
            if (sourceCapacity > destinationCapacity) {
                int[] newPrimitiveStore = new int[destinationCapacity];
                int destinationSize = newShape.getPrimitiveArraySize();
                UnsafeAccess.arrayCopy(oldPrimitiveStore, newPrimitiveStore, destinationSize);
                object.setPrimitiveStore(newPrimitiveStore);
            }
        }
    }

    static Map<Object, Object> archive(DynamicObject object) {
        Map<Object, Object> archive = new HashMap<>();
        Property[] properties = (object.getShape()).getPropertyArray();
        for (Property property : properties) {
            archive.put(property.getKey(), DynamicObjectLibrary.getUncached().getOrDefault(object, property.getKey(), null));
        }
        return archive;
    }

    static boolean verifyValues(DynamicObject object, Map<Object, Object> archive) {
        Property[] properties = (object.getShape()).getPropertyArray();
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
        if (shape.isLeaf()) {
            shape.invalidateLeafAssumption();
        }
        if (shape.allowPropertyAssumptions()) {
            shape.invalidateAllPropertyAssumptions();
        }
    }
}
