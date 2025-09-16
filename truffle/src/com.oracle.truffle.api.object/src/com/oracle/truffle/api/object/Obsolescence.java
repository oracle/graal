/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.object.ObjectStorageOptions.MaxMergeDepth;
import static com.oracle.truffle.api.object.ObjectStorageOptions.MaxMergeDiff;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.object.ExtLocations.DoubleLocation;
import com.oracle.truffle.api.object.ExtLocations.IntLocation;
import com.oracle.truffle.api.object.ExtLocations.LongLocation;
import com.oracle.truffle.api.object.ExtLocations.ObjectLocation;

abstract class Obsolescence {

    private static final DebugCounter mergedShapeCount = DebugCounter.create("Compatible shapes merged");

    private Obsolescence() {
    }

    static boolean isSameProperty(Property thiz, Property other) {
        return other.getKey().equals(thiz.getKey()) && other.getFlags() == thiz.getFlags();
    }

    /**
     * Is one of these property Shapes an upcast of the other?
     *
     * @param other Shape to compare to
     * @return true if one shape is an upcast of the other, or the Shapes are equal
     */
    public static boolean isRelatedByUpcast(Shape thiz, Shape other) {
        CompilerAsserts.neverPartOfCompilation();
        return tryMergeShapes(thiz, other, true) != null;
    }

    /**
     * Obsolete whichever of these shapes is a downcast of the other, at whatever level is
     * necessary.
     *
     * @return the more general (not obsoleted) shape if an obsolescence was performed
     */
    public static Shape tryObsoleteDowncast(Shape thiz, Shape other) {
        CompilerAsserts.neverPartOfCompilation();
        Supplier<Shape> mergeResult = tryMergeShapes(thiz, other, false);
        if (mergeResult != null) {
            synchronized (thiz.getMutex()) {
                return mergeResult.get();
            }
        }
        return null;
    }

    private static Supplier<Shape> tryMergeShapes(Shape thiz, Shape other, boolean checkOnly) {
        CompilerAsserts.neverPartOfCompilation();
        // Check that shapes are related and have the same number of parents and properties.
        if (thiz.getLayout() != other.getLayout()) {
            return null;
        } else if (thiz.getRoot() != other.getRoot()) {
            return null;
        } else if (thiz.isShared() || other.isShared()) {
            return null;
        } else if (thiz.getSharedData() != other.getSharedData()) {
            return null;
        } else if (thiz.getDepth() >= MaxMergeDepth) {
            return null;
        } else if (thiz.getDepth() != other.getDepth()) {
            return null;
        } else if (thiz.getPropertyMap().size() != other.getPropertyMap().size()) {
            return null;
        }

        final LayoutImpl layout = thiz.getLayout();
        Shape thisParent = thiz;
        Shape otherParent = other;
        Supplier<Shape> lastMergeResult = null;
        int diff = 0;
        for (int i = 0; i < MaxMergeDepth; i++) {
            if (thisParent == otherParent) {
                // found a common ancestor, so we are done
                return lastMergeResult;
            }

            if (thisParent.getParent() == null || otherParent.getParent() == null) {
                return null;
            } else if (!thisParent.isValid() || !otherParent.isValid()) {
                return null;
            } else if (thisParent.getFlagsInternal() != otherParent.getFlagsInternal()) {
                return null;
            } else if (thisParent.getDynamicType() != otherParent.getDynamicType()) {
                return null;
            }
            assert thisParent.getDepth() == otherParent.getDepth();

            Property thisLast = thisParent.getLastProperty();
            Property otherLast = otherParent.getLastProperty();
            if (thisLast == null || otherLast == null) {
                return null;
            }
            if (!thisLast.equals(otherLast)) {
                if (!isSameProperty(thisLast, otherLast)) {
                    return null;
                }
                assert !thisLast.getLocation().equals(otherLast.getLocation());
                if (!isLocationEquivalent(thisLast.getLocation(), otherLast.getLocation())) {
                    if (++diff > MaxMergeDiff) {
                        // Bail out if too many locations differ since we would need multiple rounds
                        // of obsolescence to migrate to the (or a) more general shape.
                        // Also, not all locations are necessarily assignable in the same direction.
                        return null;
                    }
                    if (!isLocationCompatible(layout, thisLast.getLocation(), otherLast.getLocation())) {
                        return null;
                    }
                    if (checkOnly) {
                        // Dummy result indicating success.
                        lastMergeResult = () -> null;
                    } else {
                        // We want to merge at the uppermost mismatch, so don't return immediately.
                        lastMergeResult = makeMergeResult(thiz, other, thisParent, otherParent, thisLast, otherLast);
                    }
                }
            }

            thisParent = thisParent.getParent();
            otherParent = otherParent.getParent();
        }
        return null;
    }

    private static Supplier<Shape> makeMergeResult(Shape thiz, Shape other, Shape thisParent, Shape otherParent, Property thisProperty, Property otherProperty) {
        CompilerAsserts.neverPartOfCompilation();
        assert isSameProperty(thisProperty, otherProperty);
        return () -> {
            Shape succ = null;
            if (thiz.isValid() && other.isValid() && thisParent.isValid() && otherParent.isValid()) {
                LayoutImpl layout = thisParent.getLayout();
                if (isLocationAssignableFrom(layout, thisProperty.getLocation(), otherProperty.getLocation())) {
                    markObsolete(otherParent, thisParent, otherProperty, thisProperty);
                    assert !otherParent.isValid();
                    succ = thiz; // this shape is wider
                } else if (isLocationAssignableFrom(layout, otherProperty.getLocation(), thisProperty.getLocation())) {
                    markObsolete(thisParent, otherParent, thisProperty, otherProperty);
                    assert !thisParent.isValid();
                    succ = other; // other shape is wider
                }
            }
            if (succ != null) {
                mergedShapeCount.inc();
            }
            return succ;
        };
    }

    public static boolean isLocationCompatible(LayoutImpl layout, Location thisLoc, Location otherLoc) {
        return isLocationAssignableFrom(layout, thisLoc, otherLoc) || isLocationAssignableFrom(layout, otherLoc, thisLoc);
    }

    /**
     * Returns true if both locations are of the same type, disregarding their actual location.
     */
    protected static boolean isLocationEquivalent(Location thisLoc, Location otherLoc) {
        if (thisLoc instanceof IntLocation) {
            return (otherLoc instanceof IntLocation);
        } else if (thisLoc instanceof DoubleLocation) {
            return (otherLoc instanceof DoubleLocation && ((DoubleLocation) thisLoc).isImplicitCastIntToDouble() && ((DoubleLocation) otherLoc).isImplicitCastIntToDouble());
        } else if (thisLoc instanceof LongLocation) {
            return (otherLoc instanceof LongLocation && ((LongLocation) thisLoc).isImplicitCastIntToLong() && ((LongLocation) otherLoc).isImplicitCastIntToLong());
        } else if (thisLoc instanceof ObjectLocation) {
            return (otherLoc instanceof ObjectLocation &&
                            ((ObjectLocation) thisLoc).getType() == ((ObjectLocation) otherLoc).getType() &&
                            ((ObjectLocation) thisLoc).isNonNull() == ((ObjectLocation) otherLoc).isNonNull());
        } else if (thisLoc.isValue()) {
            return thisLoc.equals(otherLoc);
        } else {
            throw ExtLocations.shouldNotReachHere();
        }
    }

    protected static boolean isLocationAssignableFrom(LayoutImpl layout, Location destination, Location source) {
        if (destination instanceof IntLocation) {
            return (source instanceof IntLocation);
        } else if (destination instanceof DoubleLocation) {
            return (source instanceof DoubleLocation || (layout.isAllowedIntToDouble() && source instanceof IntLocation));
        } else if (destination instanceof LongLocation) {
            return (source instanceof LongLocation || (layout.isAllowedIntToLong() && source instanceof IntLocation));
        } else if (destination instanceof ObjectLocation dstObjLoc) {
            if (source instanceof ObjectLocation) {
                return (dstObjLoc.getType() == Object.class || dstObjLoc.getType().isAssignableFrom(((ObjectLocation) source).getType())) &&
                                (!dstObjLoc.isNonNull() || ((ObjectLocation) source).isNonNull());
            } else if (source instanceof ExtLocations.TypedLocation) {
                // Untyped object location is assignable from any primitive type location
                return dstObjLoc.getType() == Object.class;
            } else {
                return false;
            }
        } else if (destination.isValue()) {
            return destination.equals(source);
        } else {
            throw ExtLocations.shouldNotReachHere();
        }
    }

    /**
     * Mark this Shape, and all of its descendants, obsolete.
     */
    public static void markObsolete(Shape oldShape, Shape obsoletedBy, Property oldProperty, Property newProperty) {
        CompilerAsserts.neverPartOfCompilation();
        assert oldProperty != newProperty;
        assert !oldShape.isShared();

        markObsolete(oldShape, obsoletedBy);
    }

    private static void markObsolete(Shape oldShape, Shape obsoletedBy) {
        CompilerAsserts.neverPartOfCompilation();

        if (!oldShape.isValid()) {
            setObsoletedBy(oldShape, obsoletedBy);
            return;
        }

        setObsoletedBy(oldShape, obsoletedBy);
        invalidateShape(oldShape);

        Deque<Shape> workQueue = new ArrayDeque<>(4);
        addTransitionsToWorkQueue(oldShape, workQueue);

        while (!workQueue.isEmpty()) {
            Shape childOldShape = workQueue.pop();

            if (!childOldShape.isValid() || childOldShape.isShared()) {
                continue;
            }

            invalidateShape(childOldShape);

            addTransitionsToWorkQueue(childOldShape, workQueue);
        }
    }

    private static void addTransitionsToWorkQueue(Shape shape, Deque<? super Shape> workQueue) {
        shape.forEachTransition(new BiConsumer<Transition, Shape>() {
            @Override
            public void accept(Transition t, Shape s) {
                if (isDirectTransition(t)) {
                    workQueue.add(s);
                }
            }
        });
    }

    private static boolean isDirectTransition(Transition transition) {
        return transition.isDirect();
    }

    static void invalidateShape(Shape shape) {
        shape.invalidateValidAssumption();
    }

    static void setObsoletedBy(Shape shape, Shape successorShape) {
        shape.setSuccessorShape(successorShape);
        successorShape.addPredecessorShape(shape);
    }
}
