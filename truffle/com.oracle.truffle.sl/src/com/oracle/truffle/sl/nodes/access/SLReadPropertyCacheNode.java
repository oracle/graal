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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.LongLocation;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.sl.runtime.SLNull;

public abstract class SLReadPropertyCacheNode extends Node {

    protected static final int CACHE_LIMIT = 3;

    protected final String propertyName;

    public SLReadPropertyCacheNode(String propertyName) {
        this.propertyName = propertyName;
    }

    public static SLReadPropertyCacheNode create(String propertyName) {
        return SLReadPropertyCacheNodeGen.create(propertyName);
    }

    public abstract Object executeObject(DynamicObject receiver);

    public abstract long executeLong(DynamicObject receiver) throws UnexpectedResultException;

    /*
     * We use a separate long specialization to avoid boxing for long.
     */
    @Specialization(limit = "CACHE_LIMIT", guards = {"longLocation != null", "shape.check(receiver)"}, assumptions = "shape.getValidAssumption()")
    protected long doCachedLong(DynamicObject receiver,   //
                    @Cached("receiver.getShape()") Shape shape,   //
                    @Cached("getLongLocation(shape)") LongLocation longLocation) {
        return longLocation.getLong(receiver, shape);
    }

    protected LongLocation getLongLocation(Shape shape) {
        Property property = shape.getProperty(propertyName);
        if (property != null && property.getLocation() instanceof LongLocation) {
            return (LongLocation) property.getLocation();
        }
        return null;
    }

    /*
     * As soon as we have seen an object read, we cannot avoid boxing long anymore therefore we can
     * contain all long cache entries.
     */
    @Specialization(limit = "CACHE_LIMIT", contains = "doCachedLong", guards = "shape.check(receiver)", assumptions = "shape.getValidAssumption()")
    protected static Object doCachedObject(DynamicObject receiver,   //
                    @Cached("receiver.getShape()") Shape shape,   //
                    @Cached("shape.getProperty(propertyName)") Property property) {
        if (property == null) {
            return SLNull.SINGLETON;
        } else {
            return property.get(receiver, shape);
        }
    }

    @Specialization(guards = "updateShape(receiver)")
    public Object updateShapeAndRead(DynamicObject receiver) {
        return executeObject(receiver);
    }

    /*
     * The generic case is used if the number of shapes accessed overflows the limit.
     */
    @Specialization(contains = {"doCachedObject", "updateShapeAndRead"})
    @TruffleBoundary
    protected Object doGeneric(DynamicObject receiver) {
        return receiver.get(receiver, SLNull.SINGLETON);
    }

    protected static boolean updateShape(DynamicObject object) {
        CompilerDirectives.transferToInterpreter();
        return object.updateShape();
    }

}
