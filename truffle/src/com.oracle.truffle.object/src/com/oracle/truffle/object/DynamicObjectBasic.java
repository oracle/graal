/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.PrintStream;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.object.CoreLocations.SimpleLongFieldLocation;
import com.oracle.truffle.object.CoreLocations.SimpleObjectFieldLocation;

@SuppressWarnings("deprecation")
public class DynamicObjectBasic extends DynamicObjectImpl {

    long primitive1;
    long primitive2;
    long primitive3;
    Object object1;
    Object object2;
    Object object3;
    Object object4;

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
            this.setPrimitiveStore(new int[((ShapeImpl) shape).getPrimitiveArrayCapacity()], shape);
        }
    }

    private Object[] getObjectStore(@SuppressWarnings("unused") Shape currentShape) {
        return LayoutImpl.ACCESS.getObjectArray(this);
    }

    private void setObjectStore(Object[] newArray, @SuppressWarnings("unused") Shape currentShape) {
        LayoutImpl.ACCESS.setObjectArray(this, newArray);
    }

    private int[] getPrimitiveStore(@SuppressWarnings("unused") Shape currentShape) {
        return LayoutImpl.ACCESS.getPrimitiveArray(this);
    }

    private void setPrimitiveStore(int[] newArray, @SuppressWarnings("unused") Shape currentShape) {
        LayoutImpl.ACCESS.setPrimitiveArray(this, newArray);
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

    @SuppressWarnings("deprecation")
    @Override
    protected final void reshape(ShapeImpl newShape) {
        ShapeImpl oldShape = (ShapeImpl) getShape();
        ShapeImpl commonAncestor = ShapeImpl.findCommonAncestor(oldShape, newShape);
        if (com.oracle.truffle.object.ObjectStorageOptions.TraceReshape) {
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

    static final BasicObjectFieldLocation[] OBJECT_FIELD_LOCATIONS;
    static final BasicLongFieldLocation[] PRIMITIVE_FIELD_LOCATIONS;

    abstract static class BasicLongFieldLocation extends SimpleLongFieldLocation {
        protected BasicLongFieldLocation(int index) {
            super(index);
        }

        @Override
        public final Class<? extends DynamicObject> getDeclaringClass() {
            return DynamicObjectBasic.class;
        }

        @Override
        public final int primitiveFieldCount() {
            return 1;
        }

        @Override
        public final void accept(LocationVisitor locationVisitor) {
            locationVisitor.visitPrimitiveField(getIndex(), 1);
        }
    }

    abstract static class BasicObjectFieldLocation extends SimpleObjectFieldLocation {
        protected BasicObjectFieldLocation(int index) {
            super(index);
        }

        @Override
        public final Class<? extends DynamicObject> getDeclaringClass() {
            return DynamicObjectBasic.class;
        }
    }

    static {
        int index;

        index = 0;
        PRIMITIVE_FIELD_LOCATIONS = new BasicLongFieldLocation[]{new BasicLongFieldLocation(index++) {
            @Override
            public long getLong(DynamicObject store, boolean guard) {
                return ((DynamicObjectBasic) store).primitive1;
            }

            @Override
            public void setLong(DynamicObject store, long value, boolean guard, boolean init) {
                ((DynamicObjectBasic) store).primitive1 = value;
            }
        }, new BasicLongFieldLocation(index++) {
            @Override
            public long getLong(DynamicObject store, boolean guard) {
                return ((DynamicObjectBasic) store).primitive2;
            }

            @Override
            public void setLong(DynamicObject store, long value, boolean guard, boolean init) {
                ((DynamicObjectBasic) store).primitive2 = value;
            }
        }, new BasicLongFieldLocation(index++) {
            @Override
            public long getLong(DynamicObject store, boolean guard) {
                return ((DynamicObjectBasic) store).primitive3;
            }

            @Override
            public void setLong(DynamicObject store, long value, boolean guard, boolean init) {
                ((DynamicObjectBasic) store).primitive3 = value;
            }
        }};

        index = 0;
        OBJECT_FIELD_LOCATIONS = new BasicObjectFieldLocation[]{new BasicObjectFieldLocation(index++) {
            @Override
            public Object get(DynamicObject store, boolean guard) {
                return ((DynamicObjectBasic) store).object1;
            }

            @Override
            public void set(DynamicObject store, Object value, boolean guard, boolean init) {
                ((DynamicObjectBasic) store).object1 = value;
            }
        }, new BasicObjectFieldLocation(index++) {
            @Override
            public Object get(DynamicObject store, boolean guard) {
                return ((DynamicObjectBasic) store).object2;
            }

            @Override
            public void set(DynamicObject store, Object value, boolean guard, boolean init) {
                ((DynamicObjectBasic) store).object2 = value;
            }
        }, new BasicObjectFieldLocation(index++) {
            @Override
            public Object get(DynamicObject store, boolean guard) {
                return ((DynamicObjectBasic) store).object3;
            }

            @Override
            public void set(DynamicObject store, Object value, boolean guard, boolean init) {
                ((DynamicObjectBasic) store).object3 = value;
            }
        }, new BasicObjectFieldLocation(index++) {
            @Override
            public Object get(DynamicObject store, boolean guard) {
                return ((DynamicObjectBasic) store).object4;
            }

            @Override
            public void set(DynamicObject store, Object value, boolean guard, boolean init) {
                ((DynamicObjectBasic) store).object4 = value;
            }
        }};
    }
}
