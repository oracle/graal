/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object;

import java.util.Objects;

import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.Property;

/** @since 0.17 or earlier */
public abstract class Transition {
    /**
     * @since 0.17 or earlier
     */
    protected Transition() {
    }

    /** @since 0.17 or earlier */
    @Override
    public int hashCode() {
        int result = 1;
        return result;
    }

    /** @since 0.17 or earlier */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        return true;
    }

    /** @since 0.17 or earlier */
    public abstract boolean isDirect();

    /** @since 0.17 or earlier */
    public abstract static class PropertyTransition extends Transition {
        private final Property property;

        /** @since 0.17 or earlier */
        public PropertyTransition(Property property) {
            this.property = property;
        }

        /** @since 0.17 or earlier */
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + ((property == null) ? 0 : property.hashCode());
            return result;
        }

        /** @since 0.17 or earlier */
        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            PropertyTransition other = (PropertyTransition) obj;
            if (!Objects.equals(property, other.property)) {
                return false;
            }
            return true;
        }

        /** @since 0.17 or earlier */
        public Property getProperty() {
            return property;
        }
    }

    /** @since 0.17 or earlier */
    public static final class AddPropertyTransition extends PropertyTransition {
        /** @since 0.17 or earlier */
        public AddPropertyTransition(Property property) {
            super(property);
        }

        /** @since 0.17 or earlier */
        @Override
        public boolean isDirect() {
            return true;
        }

        /** @since 0.17 or earlier */
        @Override
        public String toString() {
            return String.format("add(%s)", getProperty());
        }
    }

    /** @since 0.17 or earlier */
    public static final class RemovePropertyTransition extends PropertyTransition {
        private final boolean direct;

        public RemovePropertyTransition(Property property, boolean direct) {
            super(property);
            this.direct = direct;
        }

        /** @since 0.17 or earlier */
        @Override
        public boolean isDirect() {
            return direct;
        }

        /** @since 0.17 or earlier */
        @Override
        public String toString() {
            return String.format("remove(%s)", getProperty());
        }
    }

    /** @since 0.17 or earlier */
    public static final class ObjectTypeTransition extends Transition {
        private final ObjectType objectType;

        /** @since 0.17 or earlier */
        public ObjectTypeTransition(ObjectType objectType) {
            this.objectType = objectType;
        }

        /** @since 0.17 or earlier */
        public ObjectType getObjectType() {
            return objectType;
        }

        /** @since 0.17 or earlier */
        @Override
        public boolean equals(Object other) {
            return super.equals(other) && Objects.equals(objectType, ((ObjectTypeTransition) other).objectType);
        }

        /** @since 0.17 or earlier */
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + ((objectType == null) ? 0 : objectType.hashCode());
            return result;
        }

        /** @since 0.17 or earlier */
        @Override
        public boolean isDirect() {
            return true;
        }

        /** @since 0.17 or earlier */
        @Override
        public String toString() {
            return String.format("objectType(%s)", getObjectType());
        }
    }

    /** @since 0.17 or earlier */
    public abstract static class AbstractReplacePropertyTransition extends PropertyTransition {
        private final Property after;

        /** @since 0.17 or earlier */
        public AbstractReplacePropertyTransition(Property before, Property after) {
            super(before);
            this.after = after;
        }

        /** @since 0.17 or earlier */
        public Property getPropertyBefore() {
            return this.getProperty();
        }

        /** @since 0.17 or earlier */
        public Property getPropertyAfter() {
            return after;
        }

        /** @since 0.17 or earlier */
        @Override
        public boolean equals(Object obj) {
            return super.equals(obj) && this.after.equals(((AbstractReplacePropertyTransition) obj).after);
        }

        /** @since 0.17 or earlier */
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + after.hashCode();
            return result;
        }

        /** @since 0.17 or earlier */
        @Override
        public String toString() {
            return String.format("replace(%s,%s)", getPropertyBefore(), getPropertyAfter());
        }
    }

    /** @since 0.17 or earlier */
    public static final class IndirectReplacePropertyTransition extends AbstractReplacePropertyTransition {
        /** @since 0.17 or earlier */
        public IndirectReplacePropertyTransition(Property before, Property after) {
            super(before, after);
        }

        /** @since 0.17 or earlier */
        @Override
        public boolean isDirect() {
            return false;
        }
    }

    /** @since 0.17 or earlier */
    public static final class DirectReplacePropertyTransition extends AbstractReplacePropertyTransition {
        /** @since 0.17 or earlier */
        public DirectReplacePropertyTransition(Property before, Property after) {
            super(before, after);
        }

        /** @since 0.17 or earlier */
        @Override
        public boolean isDirect() {
            return true;
        }
    }

    /** @since 0.17 or earlier */
    public static final class ReservePrimitiveArrayTransition extends Transition {
        /** @since 0.17 or earlier */
        public ReservePrimitiveArrayTransition() {
        }

        /** @since 0.17 or earlier */
        @Override
        public boolean isDirect() {
            return true;
        }
    }

    /** @since 0.18 */
    public static final class ShareShapeTransition extends Transition {
        /** @since 0.18 */
        public ShareShapeTransition() {
        }

        /** @since 0.18 */
        @Override
        public boolean isDirect() {
            return true;
        }
    }

    static final class ObjectFlagsTransition extends Transition {
        private final int objectFlags;

        ObjectFlagsTransition(int newFlags) {
            this.objectFlags = newFlags;
        }

        public int getObjectFlags() {
            return objectFlags;
        }

        @Override
        public boolean equals(Object other) {
            return super.equals(other) && (this.objectFlags == ((ObjectFlagsTransition) other).objectFlags);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + objectFlags;
            return result;
        }

        @Override
        public boolean isDirect() {
            return true;
        }

        @Override
        public String toString() {
            return String.format("objectFlags(%s)", getObjectFlags());
        }
    }
}
