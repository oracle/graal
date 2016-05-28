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
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.sl.nodes.interop.SLForeignToSLTypeNode;
import com.oracle.truffle.sl.nodes.interop.SLForeignToSLTypeNodeGen;
import com.oracle.truffle.sl.runtime.SLNull;
import com.oracle.truffle.sl.runtime.SLUndefinedNameException;

@SuppressWarnings("unused")
public abstract class SLReadPropertyCacheNode extends SLPropertyCacheNode {

    public abstract Object executeRead(VirtualFrame frame, Object receiver, Object name);

    /**
     * Polymorphic inline cache for a limited number of distinct property names and shapes.
     */
    @Specialization(limit = "CACHE_LIMIT", //
                    guards = {
                                    "cachedName.equals(name)",
                                    "shapeCheck(shape, receiver)"
                    }, //
                    assumptions = {
                                    "shape.getValidAssumption()"
                    })
    protected Object readCached(DynamicObject receiver, Object name,
                    @Cached("name") Object cachedName,
                    @Cached("lookupShape(receiver)") Shape shape,
                    @Cached("lookupLocation(shape, name)") Location location) {

        return location.get(receiver, shape);
    }

    protected static Location lookupLocation(Shape shape, Object name) {
        /* Initialization of cached values always happens in a slow path. */
        CompilerAsserts.neverPartOfCompilation();

        Property property = shape.getProperty(name);
        if (property == null) {
            /* Property does not exist. */
            throw new SLUndefinedNameException("property", name);
        }

        return property.getLocation();
    }

    /**
     * The generic case is used if the number of shapes accessed overflows the limit of the
     * polymorphic inline cache.
     */
    @TruffleBoundary
    @Specialization(contains = {"readCached"}, guards = {"isValidSimpleLanguageObject(receiver)"})
    protected Object readUncached(DynamicObject receiver, Object name) {

        Object result = receiver.get(name);
        if (result == null) {
            /* Property does not exist. */
            throw new SLUndefinedNameException("property", name);
        }
        return result;
    }

    /**
     * When no specialization fits, the receiver is either not an object (which is a type error), or
     * the object has a shape that has been invalidated.
     */
    @Fallback
    protected Object updateShape(Object r, Object name) {
        /*
         * Slow path that we do not handle in compiled code. But no need to invalidate compiled
         * code.
         */
        CompilerDirectives.transferToInterpreter();

        if (!(r instanceof DynamicObject)) {
            /* Non-object types do not have properties. */
            throw new SLUndefinedNameException("property", name);
        }
        DynamicObject receiver = (DynamicObject) r;
        receiver.updateShape();
        return readUncached(receiver, name);

    }

    /*
     * All code below is only needed for language interoperability.
     */

    /** The child node to access the foreign object. */
    @Child private Node foreignRead;

    /** The child node to convert the result of the foreign object access to an SL value. */
    @Child private SLForeignToSLTypeNode toSLType;

    /**
     * If the receiver object is a foreign value we use Truffle's interop API to access the foreign
     * data.
     */
    @Specialization(guards = "isForeignObject(receiver)")
    protected Object readForeign(VirtualFrame frame, TruffleObject receiver, Object name) {
        // Lazily insert the foreign object access nodes upon the first execution.
        if (foreignRead == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // SL maps a property access to a READ message if the receiver is a foreign object.
            this.foreignRead = insert(Message.READ.createNode());
            this.toSLType = insert(SLForeignToSLTypeNodeGen.create(null));
        }
        try {
            // Perform the foreign object access.
            Object result = ForeignAccess.sendRead(foreignRead, frame, receiver, name);
            // Convert the result to an SL value.
            Object slValue = toSLType.executeWithTarget(frame, result);
            return slValue;
        } catch (UnknownIdentifierException | UnsupportedMessageException e) {
            // In case the foreign access is not successful, we return null.
            return SLNull.SINGLETON;
        }
    }
}
