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

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.object.*;

public abstract class SLWritePropertyCacheNode extends Node {

    protected final String propertyName;

    public SLWritePropertyCacheNode(String propertyName) {
        this.propertyName = propertyName;
    }

    public abstract void executeObject(DynamicObject receiver, Object value);

    @Specialization(guards = "location.isValid(receiver, value)", assumptions = "location.getAssumptions()")
    public void writeCached(DynamicObject receiver, Object value,   //
                    @Cached("createCachedWrite(receiver, value)") CachedWriteLocation location) {
        if (location.writeUnchecked(receiver, value)) {
            // write successful
        } else {
            executeObject(receiver, value);
        }
    }

    @Specialization(contains = "writeCached")
    @TruffleBoundary
    public void writeGeneric(DynamicObject receiver, Object value,   //
                    @Cached("new(createCachedWrite(receiver, value))") LRUCachedWriteLocation lru) {
        CachedWriteLocation location = lru.location;
        if (!location.isValid(receiver, value) || !location.areAssumptionsValid()) {
            location = createCachedWrite(receiver, value);
            lru.location = location;
        }
        if (location.writeUnchecked(receiver, value)) {
            // write successful
        } else {
            executeObject(receiver, value);
        }
    }

    protected CachedWriteLocation createCachedWrite(DynamicObject receiver, Object value) {
        while (receiver.updateShape()) {
            // multiple shape updates might be needed.
        }

        Shape oldShape = receiver.getShape();
        Shape newShape;
        Property property = oldShape.getProperty(propertyName);

        if (property != null && property.getLocation().canSet(receiver, value)) {
            newShape = oldShape;
        } else {
            receiver.define(propertyName, value, 0);
            newShape = receiver.getShape();
            property = newShape.getProperty(propertyName);
        }

        if (!oldShape.check(receiver)) {
            return createCachedWrite(receiver, value);
        }

        return new CachedWriteLocation(oldShape, newShape, property.getLocation());

    }

    protected static final class CachedWriteLocation {

        private final Shape oldShape;
        private final Shape newShape;
        private final Location location;
        private final Assumption validLocation = Truffle.getRuntime().createAssumption();

        public CachedWriteLocation(Shape oldShape, Shape newShape, Location location) {
            this.oldShape = oldShape;
            this.newShape = newShape;
            this.location = location;
        }

        public boolean areAssumptionsValid() {
            return validLocation.isValid() && oldShape.getValidAssumption().isValid() && newShape.getValidAssumption().isValid();
        }

        public Assumption[] getAssumptions() {
            return new Assumption[]{oldShape.getValidAssumption(), newShape.getValidAssumption(), validLocation};
        }

        public boolean isValid(DynamicObject receiver, Object value) {
            return oldShape.check(receiver) && location.canSet(receiver, value);
        }

        public boolean writeUnchecked(DynamicObject receiver, Object value) {
            try {
                if (oldShape == newShape) {
                    location.set(receiver, value, oldShape);
                } else {
                    location.set(receiver, value, oldShape, newShape);
                }
                return true;
            } catch (IncompatibleLocationException | FinalLocationException e) {
                validLocation.invalidate();
                return false;
            }
        }
    }

    protected static final class LRUCachedWriteLocation {

        private CachedWriteLocation location;

        public LRUCachedWriteLocation(CachedWriteLocation location) {
            this.location = location;
        }

    }

}
