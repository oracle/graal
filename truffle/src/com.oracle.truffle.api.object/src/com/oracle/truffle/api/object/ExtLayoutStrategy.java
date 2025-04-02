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

import com.oracle.truffle.api.object.ExtLocations.AbstractObjectLocation;
import com.oracle.truffle.api.object.ExtLocations.InstanceLocation;
import com.oracle.truffle.api.object.ExtLocations.TypeAssumption;
import com.oracle.truffle.api.object.LocationImpl.LocationVisitor;

abstract class ExtLayoutStrategy extends LayoutStrategy {
    protected ExtLayoutStrategy() {
    }

    protected static boolean assertLocationInRange(final ShapeImpl shape, final Location location) {
        final ExtLayout layout = (ExtLayout) shape.getLayout();
        if (location instanceof LocationImpl) {
            ((LocationImpl) location).accept(new LocationVisitor() {
                @Override
                public void visitPrimitiveField(int index, int count) {
                    assert index + count <= layout.getPrimitiveFieldCount();
                }

                @Override
                public void visitObjectField(int index, int count) {
                    assert index + count <= layout.getObjectFieldCount();
                }

                @Override
                public void visitPrimitiveArray(int index, int count) {
                }

                @Override
                public void visitObjectArray(int index, int count) {
                }
            });
        }
        return true;
    }

    @Override
    protected Location createLocationForValue(ShapeImpl shape, Object value, int putFlags) {
        return ((ExtAllocator) shape.allocator()).locationForValue(value, value != null, putFlags);
    }

    @Override
    protected int getLocationOrdinal(Location location) {
        return ExtLocations.getLocationOrdinal(((ExtLocation) location));
    }

    @Override
    protected ShapeImpl removeProperty(ShapeImpl shape, Property property) {
        if (property.getLocation() instanceof InstanceLocation) {
            ((InstanceLocation) property.getLocation()).maybeInvalidateFinalAssumption();
        }
        return super.removeProperty(shape, property);
    }

    @Override
    protected ShapeImpl defineProperty(ShapeImpl oldShape, Object key, Object value, int propertyFlags, Property existing, int putFlags) {
        return super.defineProperty(oldShape, key, value, propertyFlags, existing, putFlags);
    }

    @Override
    protected ShapeImpl definePropertyGeneralize(ShapeImpl oldShape, Property oldProperty, Object value, int putFlags) {
        return super.definePropertyGeneralize(oldShape, oldProperty, value, putFlags);
    }

    @Override
    protected abstract boolean updateShape(DynamicObject object);

    private static void ensureSameTypeOrMoreGeneral(Location generalLocation, Location specificLocation) {
        if (generalLocation == specificLocation) {
            return;
        }
        if (generalLocation instanceof AbstractObjectLocation objLocGen && specificLocation instanceof AbstractObjectLocation objLocSpe) {
            TypeAssumption assumGen = objLocGen.getTypeAssumption();
            TypeAssumption assumSpe = objLocSpe.getTypeAssumption();
            if (assumGen != assumSpe) {
                if (!assumGen.type.isAssignableFrom(assumSpe.type) || assumGen.nonNull && !assumSpe.nonNull) {
                    // If assignable check failed, merge type assumptions to ensure safety.
                    // Otherwise, we might unsafe cast based on a wrong type assumption.
                    objLocGen.mergeTypeAssumption(assumSpe);
                }
            }
        }
    }

    @Override
    protected void ensureSameTypeOrMoreGeneral(Property generalProperty, Property specificProperty) {
        super.ensureSameTypeOrMoreGeneral(generalProperty, specificProperty);
        ensureSameTypeOrMoreGeneral(generalProperty.getLocation(), specificProperty.getLocation());
    }
}
