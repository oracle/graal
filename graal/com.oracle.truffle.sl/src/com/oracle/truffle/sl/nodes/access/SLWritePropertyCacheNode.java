/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.sl.nodes.access;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.*;
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
    public void writeCached(DynamicObject receiver, Object value, //
                    @Cached("createCachedWrite(receiver, value)") CachedWriteLocation location) {
        if (location.writeUnchecked(receiver, value)) {
            // write successful
        } else {
            executeObject(receiver, value);
        }
    }

    @Specialization(contains = "writeCached")
    @TruffleBoundary
    public void writeGeneric(DynamicObject receiver, Object value, //
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
