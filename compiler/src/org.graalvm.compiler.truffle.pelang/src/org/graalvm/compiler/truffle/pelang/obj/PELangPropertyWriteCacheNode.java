/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.pelang.obj;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.FinalLocationException;
import com.oracle.truffle.api.object.IncompatibleLocationException;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.Shape;

public abstract class PELangPropertyWriteCacheNode extends PELangPropertyCacheNode {

    public abstract void executeWrite(DynamicObject receiver, String name, Object value);

    @Specialization(limit = "CACHE_LIMIT", //
                    guards = {
                                    "cachedName.equals(name)",
                                    "checkShape(shape, receiver)",
                                    "location != null"
                    }, //
                    assumptions = {
                                    "shape.getValidAssumption()"
                    })
    @SuppressWarnings("unused")
    protected static void writeExistingPropertyCached(
                    DynamicObject receiver,
                    String name,
                    Object value,
                    @Cached("name") String cachedName,
                    @Cached("lookupShape(receiver)") Shape shape,
                    @Cached("lookupLocation(shape, name, value)") Location location) {
        try {
            location.set(receiver, value, shape);

        } catch (IncompatibleLocationException | FinalLocationException e) {
            // can not happen as guards ensure that the value can be stored
            throw new IllegalStateException(e);
        }
    }

    @Specialization(limit = "CACHE_LIMIT", //
                    guards = {
                                    "cachedName.equals(name)",
                                    "checkShape(oldShape, receiver)",
                                    "oldLocation == null"
                    }, //
                    assumptions = {
                                    "oldShape.getValidAssumption()",
                                    "newShape.getValidAssumption()"
                    })
    @SuppressWarnings("unused")
    protected static void writeNewPropertyCached(
                    DynamicObject receiver,
                    String name,
                    Object value,
                    @Cached("name") String cachedName,
                    @Cached("lookupShape(receiver)") Shape oldShape,
                    @Cached("lookupLocation(oldShape, name, value)") Location oldLocation,
                    @Cached("defineProperty(oldShape, name, value)") Shape newShape,
                    @Cached("lookupLocation(newShape, name)") Location newLocation) {
        try {
            newLocation.set(receiver, value, oldShape, newShape);

        } catch (IncompatibleLocationException e) {
            // can not happen as guards ensure that the value can be stored
            throw new IllegalStateException(e);
        }
    }

    @TruffleBoundary
    @Specialization(replaces = {"writeExistingPropertyCached", "writeNewPropertyCached"}, //
                    guards = {"receiver.getShape().isValid()"})
    protected static void writeUncached(DynamicObject receiver, String name, Object value) {
        receiver.define(name, value);
    }

    @TruffleBoundary
    @Specialization(guards = {"!receiver.getShape().isValid()"})
    protected void updateShape(DynamicObject receiver, String name, Object value) {
        CompilerDirectives.transferToInterpreter();
        receiver.updateShape();
        writeUncached(receiver, name, value);
    }

    public static PELangPropertyWriteCacheNode createNode() {
        return PELangPropertyWriteCacheNodeGen.create();
    }

}
