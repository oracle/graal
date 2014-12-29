/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.object.*;
import com.oracle.truffle.sl.nodes.*;
import com.oracle.truffle.sl.runtime.*;

/**
 * The node for accessing a property of an object. When executed, this node first evaluates the
 * object expression on the left side of the dot operator and then reads the named property.
 */
public abstract class SLReadPropertyCacheNode extends Node {

    public static SLReadPropertyCacheNode create(String propertyName) {
        return new SLUninitializedReadObjectPropertyNode(propertyName);
    }

    public abstract Object executeObject(DynamicObject receiver);

    public abstract long executeLong(DynamicObject receiver) throws UnexpectedResultException;

    protected abstract static class SLReadPropertyCacheChainNode extends SLReadPropertyCacheNode {
        protected final Shape shape;
        @Child protected SLReadPropertyCacheNode next;

        public SLReadPropertyCacheChainNode(Shape shape, SLReadPropertyCacheNode next) {
            this.shape = shape;
            this.next = next;
        }

        @Override
        public final Object executeObject(DynamicObject receiver) {
            try {
                // if this assumption fails, the object needs to be updated to a valid shape
                shape.getValidAssumption().check();
            } catch (InvalidAssumptionException e) {
                return this.replace(next).executeObject(receiver);
            }

            boolean condition = shape.check(receiver);

            if (condition) {
                return executeObjectUnchecked(receiver, condition);
            } else {
                return next.executeObject(receiver);
            }
        }

        @Override
        public final long executeLong(DynamicObject receiver) throws UnexpectedResultException {
            try {
                // if this assumption fails, the object needs to be updated to a valid shape
                shape.getValidAssumption().check();
            } catch (InvalidAssumptionException e) {
                return this.replace(next).executeLong(receiver);
            }

            boolean condition = shape.check(receiver);

            if (condition) {
                return executeLongUnchecked(receiver, condition);
            } else {
                return next.executeLong(receiver);
            }
        }

        protected abstract Object executeObjectUnchecked(DynamicObject receiver, boolean condition);

        protected long executeLongUnchecked(DynamicObject receiver, boolean condition) throws UnexpectedResultException {
            return SLTypesGen.expectLong(executeObjectUnchecked(receiver, condition));
        }
    }

    protected static class SLReadObjectPropertyNode extends SLReadPropertyCacheChainNode {
        private final Location location;

        protected SLReadObjectPropertyNode(Shape shape, Location location, SLReadPropertyCacheNode next) {
            super(shape, next);
            this.location = location;
        }

        @Override
        protected Object executeObjectUnchecked(DynamicObject receiver, boolean condition) {
            return location.get(receiver, condition);
        }
    }

    protected static class SLReadBooleanPropertyNode extends SLReadPropertyCacheChainNode {
        private final BooleanLocation location;

        protected SLReadBooleanPropertyNode(Shape shape, BooleanLocation location, SLReadPropertyCacheNode next) {
            super(shape, next);
            this.location = location;
        }

        @Override
        protected Object executeObjectUnchecked(DynamicObject receiver, boolean condition) {
            return location.getBoolean(receiver, condition);
        }
    }

    protected static class SLReadLongPropertyNode extends SLReadPropertyCacheChainNode {
        private final LongLocation location;

        protected SLReadLongPropertyNode(Shape shape, LongLocation location, SLReadPropertyCacheNode next) {
            super(shape, next);
            this.location = location;
        }

        @Override
        protected Object executeObjectUnchecked(DynamicObject receiver, boolean condition) {
            return location.getLong(receiver, condition);
        }

        @Override
        protected long executeLongUnchecked(DynamicObject receiver, boolean condition) throws UnexpectedResultException {
            return location.getLong(receiver, condition);
        }
    }

    protected static class SLReadMissingPropertyNode extends SLReadPropertyCacheChainNode {
        protected SLReadMissingPropertyNode(Shape shape, SLReadPropertyCacheNode next) {
            super(shape, next);
        }

        @Override
        protected Object executeObjectUnchecked(DynamicObject receiver, boolean condition) {
            // The property was not found in the object, return null
            return SLNull.SINGLETON;
        }
    }

    protected static class SLUninitializedReadObjectPropertyNode extends SLReadPropertyCacheNode {
        protected final String propertyName;

        protected SLUninitializedReadObjectPropertyNode(String propertyName) {
            this.propertyName = propertyName;
        }

        @Override
        public Object executeObject(DynamicObject receiver) {
            CompilerDirectives.transferToInterpreterAndInvalidate();

            receiver.updateShape();

            Shape shape = receiver.getShape();
            Property property = shape.getProperty(propertyName);

            final SLReadPropertyCacheNode resolvedNode;
            if (property == null) {
                resolvedNode = new SLReadMissingPropertyNode(shape, this);
            } else if (property.getLocation() instanceof LongLocation) {
                resolvedNode = new SLReadLongPropertyNode(shape, (LongLocation) property.getLocation(), this);
            } else if (property.getLocation() instanceof BooleanLocation) {
                resolvedNode = new SLReadBooleanPropertyNode(shape, (BooleanLocation) property.getLocation(), this);
            } else {
                resolvedNode = new SLReadObjectPropertyNode(shape, property.getLocation(), this);
            }

            return this.replace(resolvedNode, "resolved '" + propertyName + "'").executeObject(receiver);
        }

        @Override
        public long executeLong(DynamicObject receiver) throws UnexpectedResultException {
            return SLTypesGen.expectLong(executeObject(receiver));
        }
    }
}
