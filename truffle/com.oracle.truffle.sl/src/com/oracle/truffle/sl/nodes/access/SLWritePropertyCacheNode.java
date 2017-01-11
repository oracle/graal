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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.FinalLocationException;
import com.oracle.truffle.api.object.IncompatibleLocationException;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.sl.runtime.SLUndefinedNameException;

@SuppressWarnings("unused")
public abstract class SLWritePropertyCacheNode extends SLPropertyCacheNode {

    public abstract void executeWrite(VirtualFrame frame, Object receiver, Object name, Object value);

    /**
     * Polymorphic inline cache for writing a property that already exists (no shape change is
     * necessary).
     */
    @Specialization(limit = "CACHE_LIMIT", //
                    guards = {
                                    "cachedName.equals(name)",
                                    "shapeCheck(shape, receiver)",
                                    "location != null",
                                    "canSet(location, value)"
                    }, //
                    assumptions = {
                                    "shape.getValidAssumption()"
                    })
    protected static void writeExistingPropertyCached(DynamicObject receiver, Object name, Object value,
                    @Cached("name") Object cachedName,
                    @Cached("lookupShape(receiver)") Shape shape,
                    @Cached("lookupLocation(shape, name, value)") Location location) {
        try {
            location.set(receiver, value, shape);

        } catch (IncompatibleLocationException | FinalLocationException ex) {
            /* Our guards ensure that the value can be stored, so this cannot happen. */
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Polymorphic inline cache for writing a property that does not exist yet (shape change is
     * necessary).
     */
    @Specialization(limit = "CACHE_LIMIT", //
                    guards = {
                                    "namesEqual(cachedName, name)",
                                    "shapeCheck(oldShape, receiver)",
                                    "oldLocation == null",
                                    "canStore(newLocation, value)"
                    }, //
                    assumptions = {
                                    "oldShape.getValidAssumption()",
                                    "newShape.getValidAssumption()"
                    })
    protected static void writeNewPropertyCached(DynamicObject receiver, Object name, Object value,
                    @Cached("name") Object cachedName,
                    @Cached("lookupShape(receiver)") Shape oldShape,
                    @Cached("lookupLocation(oldShape, name, value)") Location oldLocation,
                    @Cached("defineProperty(oldShape, name, value)") Shape newShape,
                    @Cached("lookupLocation(newShape, name)") Location newLocation) {
        try {
            newLocation.set(receiver, value, oldShape, newShape);

        } catch (IncompatibleLocationException ex) {
            /* Our guards ensure that the value can be stored, so this cannot happen. */
            throw new IllegalStateException(ex);
        }
    }

    /** Try to find the given property in the shape. */
    protected static Location lookupLocation(Shape shape, Object name) {
        CompilerAsserts.neverPartOfCompilation();

        Property property = shape.getProperty(name);
        if (property == null) {
            /* Property does not exist yet, so a shape change is necessary. */
            return null;
        }

        return property.getLocation();
    }

    /**
     * Try to find the given property in the shape. Also returns null when the value cannot be store
     * into the location.
     */
    protected static Location lookupLocation(Shape shape, Object name, Object value) {
        Location location = lookupLocation(shape, name);
        if (location == null || !location.canSet(value)) {
            /* Existing property has an incompatible type, so a shape change is necessary. */
            return null;
        }

        return location;
    }

    protected static Shape defineProperty(Shape oldShape, Object name, Object value) {
        return oldShape.defineProperty(name, value, 0);
    }

    /**
     * There is a subtle difference between {@link Location#canSet} and {@link Location#canStore}.
     * We need {@link Location#canSet} for the guard of {@link #writeExistingPropertyCached} because
     * there we call {@link Location#set}. We use the more relaxed {@link Location#canStore} for the
     * guard of {@link SLWritePropertyCacheNode#writeNewPropertyCached} because there we perform a
     * shape transition, i.e., we are not actually setting the value of the new location - we only
     * transition to this location as part of the shape change.
     */
    protected static boolean canSet(Location location, Object value) {
        return location.canSet(value);
    }

    /** See {@link #canSet} for the difference between the two methods. */
    protected static boolean canStore(Location location, Object value) {
        return location.canStore(value);
    }

    /**
     * The generic case is used if the number of shapes accessed overflows the limit of the
     * polymorphic inline cache.
     */
    @TruffleBoundary
    @Specialization(replaces = {"writeExistingPropertyCached", "writeNewPropertyCached"}, guards = {"isValidSLObject(receiver)"})
    protected static void writeUncached(DynamicObject receiver, Object name, Object value) {
        receiver.define(name, value);
    }

    /**
     * When no specialization fits, the receiver is either not an object (which is a type error), or
     * the object has a shape that has been invalidated.
     */
    @Fallback
    protected static void updateShape(Object r, Object name, Object value) {
        /*
         * Slow path that we do not handle in compiled code. But no need to invalidate compiled
         * code.
         */
        CompilerDirectives.transferToInterpreter();

        if (!(r instanceof DynamicObject)) {
            /* Non-object types do not have properties. */
            throw SLUndefinedNameException.undefinedProperty(name);
        }
        DynamicObject receiver = (DynamicObject) r;
        receiver.updateShape();
        writeUncached(receiver, name, value);
    }

    /**
     * Language interoperability: If the receiver object is a foreign value we use Truffle's interop
     * API to access the foreign data.
     */
    @Specialization(guards = "isForeignObject(receiver)")
    protected static void writeForeign(VirtualFrame frame, TruffleObject receiver, Object name, Object value,
                    // The child node to access the foreign object
                    @Cached("createForeignWriteNode()") Node foreignWriteNode) {

        try {
            /* Perform the foreign object access. */
            ForeignAccess.sendWrite(foreignWriteNode, frame, receiver, name, value);

        } catch (UnknownIdentifierException | UnsupportedTypeException | UnsupportedMessageException e) {
            /* Foreign access was not successful. */
            throw SLUndefinedNameException.undefinedProperty(name);
        }
    }

    protected static Node createForeignWriteNode() {
        return Message.WRITE.createNode();
    }
}
