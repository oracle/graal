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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.object.*;
import com.oracle.truffle.sl.runtime.*;

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
    @SuppressWarnings("unused")
    protected long doCachedLong(DynamicObject receiver, //
                    @Cached("receiver.getShape()") Shape shape, //
                    @Cached("getLongLocation(shape)") LongLocation longLocation) {
        return longLocation.getLong(receiver, true);
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
    protected static Object doCachedObject(DynamicObject receiver, //
                    @Cached("receiver.getShape()") Shape shape, //
                    @Cached("shape.getProperty(propertyName)") Property property) {
        if (property == null) {
            return SLNull.SINGLETON;
        } else {
            return property.get(receiver, shape);
        }
    }

    /*
     * The generic case is used if the number of shapes accessed overflows the limit.
     */
    @Specialization(contains = "doCachedObject")
    @TruffleBoundary
    protected Object doGeneric(DynamicObject receiver, @Cached("new()") LRUPropertyLookup lruCache) {
        if (!lruCache.shape.check(receiver)) {
            Shape receiverShape = receiver.getShape();
            lruCache.shape = receiverShape;
            lruCache.property = receiverShape.getProperty(propertyName);
        }
        if (lruCache.property != null) {
            return lruCache.property.get(receiver, true);
        } else {
            return SLNull.SINGLETON;
        }
    }

    protected static class LRUPropertyLookup {

        private Shape shape;
        private Property property;

        public LRUPropertyLookup() {
        }

    }

}
