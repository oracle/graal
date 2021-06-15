/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.object.Location;
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

    protected boolean hasConstantLocation() {
        return false;
    }

    /** @since 0.17 or earlier */
    public abstract static class PropertyTransition extends Transition {
        protected final Property property;
        protected final Object key;
        protected final int flags;

        /** @since 0.17 or earlier */
        protected PropertyTransition(Property property) {
            this.property = property;
            this.key = property.getKey();
            this.flags = property.getFlags();
        }

        protected PropertyTransition(Object key, int flags) {
            this.property = null;
            this.key = key;
            this.flags = flags;
        }

        /** @since 0.17 or earlier */
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + ((key == null) ? 0 : key.hashCode());
            result = prime * result + flags;
            return result;
        }

        /** @since 0.17 or earlier */
        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            PropertyTransition other = (PropertyTransition) obj;
            if (!Objects.equals(this.key, other.key)) {
                return false;
            }
            if (this.flags != other.flags) {
                return false;
            }
            return true;
        }

        /** @since 0.17 or earlier */
        public Property getProperty() {
            return Objects.requireNonNull(property);
        }

        public Object getPropertyKey() {
            return key;
        }

        public int getPropertyFlags() {
            return flags;
        }

        @Override
        protected boolean hasConstantLocation() {
            return getProperty().getLocation().isConstant();
        }
    }

    protected abstract static class TypedPropertyTransition extends PropertyTransition {
        /** A {@link Location} or the value's erased type. */
        private final Object locationOrType;

        protected TypedPropertyTransition(Property property, Object locationOrType) {
            super(property);
            this.locationOrType = locationOrType;
        }

        protected TypedPropertyTransition(Object key, int flags, Object locationType) {
            super(key, flags);
            this.locationOrType = locationType;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + ((locationOrType == null) ? 0 : locationOrType.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            TypedPropertyTransition other = (TypedPropertyTransition) obj;
            if (!Objects.equals(this.locationOrType, other.locationOrType)) {
                return false;
            }
            return true;
        }

        public Object getLocationOrType() {
            return locationOrType;
        }

        protected final String propertyToString() {
            return "\"" + key + "\"" + ":" + locationOrType + (flags == 0 ? "" : "%" + flags);
        }
    }

    /** @since 0.17 or earlier */
    public static final class AddPropertyTransition extends TypedPropertyTransition {
        /** @since 0.17 or earlier */
        public AddPropertyTransition(Property property, Object locationOrType) {
            super(property, locationOrType);
        }

        public AddPropertyTransition(Object key, int flags, Object locationType) {
            super(key, flags, locationType);
        }

        /** @since 0.17 or earlier */
        @Override
        public boolean isDirect() {
            return true;
        }

        /** @since 0.17 or earlier */
        @Override
        public String toString() {
            return String.format("add(%s)", propertyToString());
        }
    }

    /** @since 0.17 or earlier */
    public static final class RemovePropertyTransition extends TypedPropertyTransition {
        private final boolean direct;

        public RemovePropertyTransition(Property property, Object locationOrType, boolean direct) {
            super(property, locationOrType);
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
            return String.format("remove(%s)", propertyToString());
        }
    }

    /** @since 0.17 or earlier */
    public static final class ObjectTypeTransition extends Transition {
        private final Object objectType;

        /** @since 0.17 or earlier */
        public ObjectTypeTransition(Object objectType) {
            this.objectType = objectType;
        }

        /** @since 0.17 or earlier */
        public Object getObjectType() {
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
            if (!super.equals(obj)) {
                return false;
            }
            AbstractReplacePropertyTransition other = (AbstractReplacePropertyTransition) obj;
            if (!Objects.equals(this.property, other.property)) {
                return false;
            }
            if (!Objects.equals(this.after, other.after)) {
                return false;
            }
            return true;
        }

        /** @since 0.17 or earlier */
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + property.hashCode();
            result = prime * result + after.hashCode();
            return result;
        }

        /** @since 0.17 or earlier */
        @Override
        public String toString() {
            return String.format("replace(%s,%s)", getPropertyBefore(), getPropertyAfter());
        }

        @Override
        protected boolean hasConstantLocation() {
            return getPropertyBefore().getLocation().isConstant() || getPropertyAfter().getLocation().isConstant();
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
