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

/**
 * The node for accessing a property of an object. When executed, this node first evaluates the
 * object expression on the left side of the dot operator and then reads the named property.
 */
public abstract class SLWritePropertyCacheNode extends Node {

    public static SLWritePropertyCacheNode create(String propertyName) {
        return new SLUninitializedWritePropertyNode(propertyName);
    }

    public abstract void executeObject(DynamicObject receiver, Object value);

    public abstract void executeLong(DynamicObject receiver, long value);

    public abstract void executeBoolean(DynamicObject receiver, boolean value);

    protected abstract static class SLWritePropertyCacheChainNode extends SLWritePropertyCacheNode {
        protected final Shape oldShape;
        protected final Shape newShape;
        @Child protected SLWritePropertyCacheNode next;

        public SLWritePropertyCacheChainNode(Shape oldShape, Shape newShape, SLWritePropertyCacheNode next) {
            this.oldShape = oldShape;
            this.newShape = newShape;
            this.next = next;
        }

        @Override
        public final void executeObject(DynamicObject receiver, Object value) {
            try {
                // if this assumption fails, the object needs to be updated to a valid shape
                oldShape.getValidAssumption().check();
                newShape.getValidAssumption().check();
            } catch (InvalidAssumptionException e) {
                this.replace(next).executeObject(receiver, value);
                return;
            }

            boolean condition = oldShape.check(receiver) && checkValue(receiver, value);

            if (condition) {
                executeObjectUnchecked(receiver, value);
            } else {
                next.executeObject(receiver, value);
            }
        }

        @Override
        public final void executeLong(DynamicObject receiver, long value) {
            try {
                // if this assumption fails, the object needs to be updated to a valid shape
                oldShape.getValidAssumption().check();
                newShape.getValidAssumption().check();
            } catch (InvalidAssumptionException e) {
                this.replace(next).executeLong(receiver, value);
                return;
            }

            boolean condition = oldShape.check(receiver) && checkValue(receiver, value);

            if (condition) {
                executeLongUnchecked(receiver, value);
            } else {
                next.executeLong(receiver, value);
            }
        }

        @Override
        public final void executeBoolean(DynamicObject receiver, boolean value) {
            try {
                // if this assumption fails, the object needs to be updated to a valid shape
                oldShape.getValidAssumption().check();
                newShape.getValidAssumption().check();
            } catch (InvalidAssumptionException e) {
                this.replace(next).executeBoolean(receiver, value);
                return;
            }

            boolean condition = oldShape.check(receiver) && checkValue(receiver, value);

            if (condition) {
                executeBooleanUnchecked(receiver, value);
            } else {
                next.executeBoolean(receiver, value);
            }
        }

        @SuppressWarnings("unused")
        protected boolean checkValue(DynamicObject receiver, Object value) {
            return true;
        }

        protected abstract void executeObjectUnchecked(DynamicObject receiver, Object value);

        protected void executeLongUnchecked(DynamicObject receiver, long value) {
            executeObjectUnchecked(receiver, value);
        }

        protected void executeBooleanUnchecked(DynamicObject receiver, boolean value) {
            executeObjectUnchecked(receiver, value);
        }
    }

    protected static class SLWriteObjectPropertyNode extends SLWritePropertyCacheChainNode {
        private final Location location;

        protected SLWriteObjectPropertyNode(Shape oldShape, Shape newShape, Location location, SLWritePropertyCacheNode next) {
            super(oldShape, newShape, next);
            this.location = location;
        }

        @Override
        protected void executeObjectUnchecked(DynamicObject receiver, Object value) {
            try {
                if (oldShape == newShape) {
                    location.set(receiver, value, oldShape);
                } else {
                    location.set(receiver, value, oldShape, newShape);
                }
            } catch (IncompatibleLocationException | FinalLocationException e) {
                replace(next).executeObject(receiver, value);
            }
        }

        @Override
        protected boolean checkValue(DynamicObject receiver, Object value) {
            return location.canSet(receiver, value);
        }
    }

    protected static class SLWriteBooleanPropertyNode extends SLWritePropertyCacheChainNode {
        private final BooleanLocation location;

        protected SLWriteBooleanPropertyNode(Shape oldShape, Shape newShape, BooleanLocation location, SLWritePropertyCacheNode next) {
            super(oldShape, newShape, next);
            this.location = location;
        }

        @Override
        protected void executeObjectUnchecked(DynamicObject receiver, Object value) {
            try {
                if (oldShape == newShape) {
                    location.set(receiver, value, oldShape);
                } else {
                    location.set(receiver, value, oldShape, newShape);
                }
            } catch (IncompatibleLocationException | FinalLocationException e) {
                replace(next).executeObject(receiver, value);
            }
        }

        @Override
        protected void executeBooleanUnchecked(DynamicObject receiver, boolean value) {
            try {
                if (oldShape == newShape) {
                    location.setBoolean(receiver, value, oldShape);
                } else {
                    location.setBoolean(receiver, value, oldShape, newShape);
                }
            } catch (FinalLocationException e) {
                replace(next).executeBoolean(receiver, value);
            }
        }

        @Override
        protected boolean checkValue(DynamicObject receiver, Object value) {
            return value instanceof Boolean;
        }
    }

    protected static class SLWriteLongPropertyNode extends SLWritePropertyCacheChainNode {
        private final LongLocation location;

        protected SLWriteLongPropertyNode(Shape oldShape, Shape newShape, LongLocation location, SLWritePropertyCacheNode next) {
            super(oldShape, newShape, next);
            this.location = location;
        }

        @Override
        protected void executeObjectUnchecked(DynamicObject receiver, Object value) {
            try {
                if (oldShape == newShape) {
                    location.set(receiver, value, oldShape);
                } else {
                    location.set(receiver, value, oldShape, newShape);
                }
            } catch (IncompatibleLocationException | FinalLocationException e) {
                replace(next).executeObject(receiver, value);
            }
        }

        @Override
        protected void executeLongUnchecked(DynamicObject receiver, long value) {
            try {
                if (oldShape == newShape) {
                    location.setLong(receiver, value, oldShape);
                } else {
                    location.setLong(receiver, value, oldShape, newShape);
                }
            } catch (FinalLocationException e) {
                replace(next).executeLong(receiver, value);
            }
        }

        @Override
        protected boolean checkValue(DynamicObject receiver, Object value) {
            return value instanceof Long;
        }
    }

    protected static class SLUninitializedWritePropertyNode extends SLWritePropertyCacheNode {
        protected final String propertyName;

        protected SLUninitializedWritePropertyNode(String propertyName) {
            this.propertyName = propertyName;
        }

        @Override
        public void executeObject(DynamicObject receiver, Object value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();

            if (receiver.updateShape()) {
                // shape changed, retry cache again
                getTopNode().executeObject(receiver, value);
                return;
            }

            Shape oldShape = receiver.getShape();
            Shape newShape;
            Property property = oldShape.getProperty(propertyName);

            final SLWritePropertyCacheNode resolvedNode;
            if (property != null && property.getLocation().canSet(receiver, value)) {
                newShape = oldShape;
            } else {
                receiver.define(propertyName, value, 0);
                newShape = receiver.getShape();
                property = newShape.getProperty(propertyName);
            }

            if (property.getLocation() instanceof LongLocation) {
                resolvedNode = new SLWriteLongPropertyNode(oldShape, newShape, (LongLocation) property.getLocation(), this);
            } else if (property.getLocation() instanceof BooleanLocation) {
                resolvedNode = new SLWriteBooleanPropertyNode(oldShape, newShape, (BooleanLocation) property.getLocation(), this);
            } else {
                resolvedNode = new SLWriteObjectPropertyNode(oldShape, newShape, property.getLocation(), this);
            }

            this.replace(resolvedNode, "resolved '" + propertyName + "'").executeObject(receiver, value);
        }

        private SLWritePropertyCacheNode getTopNode() {
            SLWritePropertyCacheNode top = this;
            while (top.getParent() instanceof SLWritePropertyCacheNode) {
                top = (SLWritePropertyCacheNode) top.getParent();
            }
            return top;
        }

        @Override
        public void executeLong(DynamicObject receiver, long value) {
            executeObject(receiver, value);
        }

        @Override
        public void executeBoolean(DynamicObject receiver, boolean value) {
            executeObject(receiver, value);
        }
    }
}
