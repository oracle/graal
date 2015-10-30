/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.nodes.access;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.FinalLocationException;
import com.oracle.truffle.api.object.IncompatibleLocationException;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.utilities.AlwaysValidAssumption;
import com.oracle.truffle.api.utilities.NeverValidAssumption;

public abstract class SLWritePropertyCacheNode extends Node {
    protected static final int CACHE_LIMIT = 3;

    protected final String propertyName;

    public SLWritePropertyCacheNode(String propertyName) {
        this.propertyName = propertyName;
    }

    public abstract void executeObject(DynamicObject receiver, Object value);

    @Specialization(guards = {"location != null", "shape.check(receiver)", "canSet(location, receiver, value)"}, assumptions = {"shape.getValidAssumption()"}, limit = "CACHE_LIMIT")
    public void writeExistingPropertyCached(DynamicObject receiver, Object value, //
                    @Cached("lookupLocation(receiver, value)") Location location, //
                    @Cached("receiver.getShape()") Shape shape, //
                    @Cached("ensureValid(receiver)") Assumption validAssumption) {
        try {
            validAssumption.check();
        } catch (InvalidAssumptionException e) {
            executeObject(receiver, value);
            return;
        }
        try {
            location.set(receiver, value, shape);
        } catch (IncompatibleLocationException | FinalLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Specialization(guards = {"existing == null", "shapeBefore.check(receiver)", "nonNull(shapeAfter)", "canSet(newLocation, receiver, value)"}, assumptions = {"shapeBefore.getValidAssumption()",
                    "shapeAfter.getValidAssumption()"}, limit = "CACHE_LIMIT")
    public void writeNewPropertyCached(DynamicObject receiver, Object value, //
                    @Cached("lookupLocation(receiver, value)") @SuppressWarnings("unused") Location existing, //
                    @Cached("receiver.getShape()") Shape shapeBefore, //
                    @Cached("defineProperty(receiver, value)") Shape shapeAfter, //
                    @Cached("getLocation(shapeAfter)") Location newLocation, //
                    @Cached("ensureValid(receiver)") Assumption validAssumption) {
        try {
            validAssumption.check();
        } catch (InvalidAssumptionException e) {
            executeObject(receiver, value);
            return;
        }
        try {
            newLocation.set(receiver, value, shapeBefore, shapeAfter);
        } catch (IncompatibleLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Specialization(contains = {"writeExistingPropertyCached", "writeNewPropertyCached"})
    @TruffleBoundary
    public void writeUncached(DynamicObject receiver, Object value) {
        receiver.define(propertyName, value);
    }

    protected final Location lookupLocation(DynamicObject object, Object value) {
        final Shape oldShape = object.getShape();
        final Property property = oldShape.getProperty(propertyName);

        if (property != null && property.getLocation().canSet(object, value)) {
            return property.getLocation();
        } else {
            return null;
        }
    }

    protected final Shape defineProperty(DynamicObject receiver, Object value) {
        Shape oldShape = receiver.getShape();
        Shape newShape = oldShape.defineProperty(propertyName, value, 0);
        return newShape;
    }

    protected final Location getLocation(Shape newShape) {
        return newShape.getProperty(propertyName).getLocation();
    }

    protected static Assumption ensureValid(DynamicObject receiver) {
        return receiver.updateShape() ? NeverValidAssumption.INSTANCE : AlwaysValidAssumption.INSTANCE;
    }

    protected static boolean canSet(Location location, DynamicObject receiver, Object value) {
        return location.canSet(receiver, value);
    }

    protected static boolean nonNull(Object value) {
        return value != null;
    }
}
