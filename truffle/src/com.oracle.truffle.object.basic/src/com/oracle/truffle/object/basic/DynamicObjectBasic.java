/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.object.basic;

import java.io.PrintStream;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.object.DynamicObjectImpl;
import com.oracle.truffle.object.ObjectStorageOptions;
import com.oracle.truffle.object.ShapeImpl;
import com.oracle.truffle.object.basic.BasicLocations.SimpleLongFieldLocation;
import com.oracle.truffle.object.basic.BasicLocations.SimpleObjectFieldLocation;

public class DynamicObjectBasic extends DynamicObjectImpl {

    private long primitive1;
    private long primitive2;
    private long primitive3;
    private Object object1;
    private Object object2;
    private Object object3;
    private Object object4;

    private Object[] objext;
    private long[] primext;

    protected DynamicObjectBasic(Shape shape) {
        super(shape);
    }

    @Override
    protected final void initialize(Shape shape) {
        assert getObjectStore(shape) == null;
        int capacity = ((ShapeImpl) shape).getObjectArrayCapacity();
        if (capacity != 0) {
            this.setObjectStore(new Object[capacity], shape);
        }
        if (((ShapeImpl) shape).getPrimitiveArrayCapacity() != 0) {
            this.setPrimitiveStore(new long[((ShapeImpl) shape).getPrimitiveArrayCapacity()], shape);
        }
    }

    /**
     * Simpler version of {@link #resizeObjectStore} when the object is only increasing in size.
     */
    @Override
    protected final void growObjectStore(Shape oldShape, Shape newShape) {
        int oldObjectArrayCapacity = ((ShapeImpl) oldShape).getObjectArrayCapacity();
        int newObjectArrayCapacity = ((ShapeImpl) newShape).getObjectArrayCapacity();
        if (oldObjectArrayCapacity != newObjectArrayCapacity) {
            growObjectStoreIntl(oldObjectArrayCapacity, newObjectArrayCapacity, oldShape);
        }
    }

    private void growObjectStoreIntl(int oldObjectArrayCapacity, int newObjectArrayCapacity, Shape newShape) {
        Object[] newObjectStore = new Object[newObjectArrayCapacity];
        if (oldObjectArrayCapacity != 0) {
            // monotonic growth assumption
            assert oldObjectArrayCapacity < newObjectArrayCapacity;
            Object[] oldObjectStore = this.getObjectStore(newShape);
            for (int i = 0; i < oldObjectArrayCapacity; ++i) {
                newObjectStore[i] = oldObjectStore[i];
            }
        }
        this.setObjectStore(newObjectStore, newShape);
    }

    /**
     * Simpler version of {@link #resizePrimitiveStore} when the object is only increasing in size.
     */
    @Override
    protected final void growPrimitiveStore(Shape oldShape, Shape newShape) {
        assert ((ShapeImpl) newShape).hasPrimitiveArray();
        int oldPrimitiveCapacity = ((ShapeImpl) oldShape).getPrimitiveArrayCapacity();
        int newPrimitiveCapacity = ((ShapeImpl) newShape).getPrimitiveArrayCapacity();
        if (newPrimitiveCapacity == 0) {
            // due to obsolescence, we might have to reserve an empty primitive array slot
            this.setPrimitiveStore(null, newShape);
        } else if (oldPrimitiveCapacity != newPrimitiveCapacity) {
            growPrimitiveStoreIntl(oldPrimitiveCapacity, newPrimitiveCapacity, oldShape);
        }
    }

    private void growPrimitiveStoreIntl(int oldPrimitiveCapacity, int newPrimitiveCapacity, Shape newShape) {
        long[] newPrimitiveArray = new long[newPrimitiveCapacity];
        if (oldPrimitiveCapacity != 0) {
            // primitive array can shrink due to type changes
            long[] oldPrimitiveArray = this.getPrimitiveStore(newShape);
            for (int i = 0; i < Math.min(oldPrimitiveCapacity, newPrimitiveCapacity); ++i) {
                newPrimitiveArray[i] = oldPrimitiveArray[i];
            }
        }
        this.setPrimitiveStore(newPrimitiveArray, newShape);
    }

    @Override
    protected final void resizeObjectStore(Shape oldShape, Shape newShape) {
        Object[] newObjectStore = null;
        int destinationCapacity = ((ShapeImpl) newShape).getObjectArrayCapacity();
        if (destinationCapacity != 0) {
            newObjectStore = new Object[destinationCapacity];
            int sourceCapacity = ((ShapeImpl) oldShape).getObjectArrayCapacity();
            if (sourceCapacity != 0) {
                Object[] oldObjectStore = getObjectStore(newShape);
                for (int i = 0; i < Math.min(sourceCapacity, destinationCapacity); ++i) {
                    newObjectStore[i] = oldObjectStore[i];
                }
            }
        }
        this.setObjectStore(newObjectStore, newShape);
    }

    private Object[] getObjectStore(@SuppressWarnings("unused") Shape currentShape) {
        return objext;
    }

    private void setObjectStore(Object[] newArray, @SuppressWarnings("unused") Shape currentShape) {
        objext = newArray;
    }

    private long[] getPrimitiveStore(@SuppressWarnings("unused") Shape currentShape) {
        return primext;
    }

    private void setPrimitiveStore(long[] newArray, @SuppressWarnings("unused") Shape currentShape) {
        primext = newArray;
    }

    @Override
    protected final void resizePrimitiveStore(Shape oldShape, Shape newShape) {
        assert ((ShapeImpl) newShape).hasPrimitiveArray();
        long[] newPrimitiveArray = null;
        int destinationCapacity = ((ShapeImpl) newShape).getPrimitiveArrayCapacity();
        if (destinationCapacity != 0) {
            newPrimitiveArray = new long[destinationCapacity];
            int sourceCapacity = ((ShapeImpl) oldShape).getPrimitiveArrayCapacity();
            if (sourceCapacity != 0) {
                long[] oldPrimitiveArray = this.getPrimitiveStore(newShape);
                for (int i = 0; i < Math.min(sourceCapacity, destinationCapacity); ++i) {
                    newPrimitiveArray[i] = oldPrimitiveArray[i];
                }
            }
        }
        this.setPrimitiveStore(newPrimitiveArray, newShape);
    }

    /**
     * Check whether fast transition is valid.
     *
     * @see #setShapeAndGrow
     */
    @SuppressWarnings("unused")
    private boolean checkSetShape(Shape oldShape, Shape newShape) {
        Shape currentShape = getShape();
        assert oldShape != newShape : "Wrong old shape assumption?";
        assert newShape != currentShape : "Redundant shape change? shape=" + currentShape;
        assert oldShape == currentShape || oldShape.getParent() == currentShape : "Out-of-order shape change?" + "\nparentShape=" + currentShape + "\noldShape=" + oldShape + "\nnewShape=" + newShape;
        return true;
    }

    /**
     * Check whether the extension arrays are in accordance with the description in the shape.
     */
    @Override
    protected final boolean checkExtensionArrayInvariants(Shape newShape) {
        assert getShape() == newShape;
        assert (getObjectStore(newShape) == null && ((ShapeImpl) newShape).getObjectArrayCapacity() == 0) ||
                        (getObjectStore(newShape) != null && getObjectStore(newShape).length == ((ShapeImpl) newShape).getObjectArrayCapacity());
        if (((ShapeImpl) newShape).hasPrimitiveArray()) {
            assert (getPrimitiveStore(newShape) == null && ((ShapeImpl) newShape).getPrimitiveArrayCapacity() == 0) ||
                            (getPrimitiveStore(newShape) != null && getPrimitiveStore(newShape).length == ((ShapeImpl) newShape).getPrimitiveArrayCapacity());
        }
        return true;
    }

    @Override
    protected final DynamicObject cloneWithShape(Shape currentShape) {
        assert this.getShape() == currentShape;
        final DynamicObjectBasic clone = (DynamicObjectBasic) super.clone();
        if (this.getObjectStore(currentShape) != null) {
            clone.setObjectStore(this.getObjectStore(currentShape).clone(), currentShape);
        }
        if (((ShapeImpl) currentShape).hasPrimitiveArray() && this.getPrimitiveStore(currentShape) != null) {
            clone.setPrimitiveStore(this.getPrimitiveStore(currentShape).clone(), currentShape);
        }
        return clone;
    }

    @Override
    protected final void reshape(ShapeImpl newShape) {
        reshapeCount.inc();

        ShapeImpl oldShape = getShape();
        ShapeImpl commonAncestor = ShapeImpl.findCommonAncestor(oldShape, newShape);
        if (ObjectStorageOptions.TraceReshape) {
            int limit = 200;
            PrintStream out = System.out;
            out.printf("RESHAPE\nOLD %s\nNEW %s\nLCA %s\nDIFF %s\n---\n", oldShape.toStringLimit(limit), newShape.toStringLimit(limit), commonAncestor.toStringLimit(limit),
                            ShapeImpl.diff(oldShape, newShape));
        }

        DynamicObject original = this.cloneWithShape(oldShape);
        setShapeAndGrow(oldShape, newShape);
        assert !((newShape.hasPrimitiveArray() && newShape.getPrimitiveArrayCapacity() == 0)) || getPrimitiveStore(newShape) == null;
        copyProperties(original, commonAncestor);
        assert checkExtensionArrayInvariants(newShape);
    }

    static final SimpleObjectFieldLocation[] OBJECT_FIELD_LOCATIONS;
    static final SimpleLongFieldLocation[] PRIMITIVE_FIELD_LOCATIONS;

    static final SimpleObjectFieldLocation OBJECT_ARRAY_LOCATION;
    static final SimpleObjectFieldLocation PRIMITIVE_ARRAY_LOCATION;

    static {
        int index;

        index = 0;
        PRIMITIVE_FIELD_LOCATIONS = new SimpleLongFieldLocation[]{new SimpleLongFieldLocation(index++) {
            @Override
            public long getLong(DynamicObject store, boolean condition) {
                return ((DynamicObjectBasic) store).primitive1;
            }

            @Override
            public void setLongInternal(DynamicObject store, long value) {
                ((DynamicObjectBasic) store).primitive1 = value;
            }
        }, new SimpleLongFieldLocation(index++) {
            @Override
            public long getLong(DynamicObject store, boolean condition) {
                return ((DynamicObjectBasic) store).primitive2;
            }

            @Override
            public void setLongInternal(DynamicObject store, long value) {
                ((DynamicObjectBasic) store).primitive2 = value;
            }
        }, new SimpleLongFieldLocation(index++) {
            @Override
            public long getLong(DynamicObject store, boolean condition) {
                return ((DynamicObjectBasic) store).primitive3;
            }

            @Override
            public void setLongInternal(DynamicObject store, long value) {
                ((DynamicObjectBasic) store).primitive3 = value;
            }
        }};

        index = 0;
        OBJECT_FIELD_LOCATIONS = new SimpleObjectFieldLocation[]{new SimpleObjectFieldLocation(index++) {
            @Override
            public Object get(DynamicObject store, boolean condition) {
                return ((DynamicObjectBasic) store).object1;
            }

            @Override
            public void setInternal(DynamicObject store, Object value) {
                ((DynamicObjectBasic) store).object1 = value;
            }
        }, new SimpleObjectFieldLocation(index++) {
            @Override
            public Object get(DynamicObject store, boolean condition) {
                return ((DynamicObjectBasic) store).object2;
            }

            @Override
            public void setInternal(DynamicObject store, Object value) {
                ((DynamicObjectBasic) store).object2 = value;
            }
        }, new SimpleObjectFieldLocation(index++) {
            @Override
            public Object get(DynamicObject store, boolean condition) {
                return ((DynamicObjectBasic) store).object3;
            }

            @Override
            public void setInternal(DynamicObject store, Object value) {
                ((DynamicObjectBasic) store).object3 = value;
            }
        }, new SimpleObjectFieldLocation(index++) {
            @Override
            public Object get(DynamicObject store, boolean condition) {
                return ((DynamicObjectBasic) store).object4;
            }

            @Override
            public void setInternal(DynamicObject store, Object value) {
                ((DynamicObjectBasic) store).object4 = value;
            }
        }};

        OBJECT_ARRAY_LOCATION = new SimpleObjectFieldLocation(index++) {
            @Override
            public Object[] get(DynamicObject store, boolean condition) {
                return ((DynamicObjectBasic) store).objext;
            }

            @Override
            public void setInternal(DynamicObject store, Object value) {
                ((DynamicObjectBasic) store).objext = (Object[]) value;
            }
        };

        PRIMITIVE_ARRAY_LOCATION = new SimpleObjectFieldLocation(index++) {
            @Override
            public long[] get(DynamicObject store, boolean condition) {
                return ((DynamicObjectBasic) store).primext;
            }

            @Override
            public void setInternal(DynamicObject store, Object value) {
                ((DynamicObjectBasic) store).primext = (long[]) value;
            }
        };
    }
}
