/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.object;

import java.util.Objects;

abstract class Transition {

    protected Transition() {
    }

    @Override
    public int hashCode() {
        int result = 1;
        return result;
    }

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

    public abstract boolean isDirect();

    protected boolean isWeak() {
        return false;
    }

    abstract static class PropertyTransition extends Transition {
        protected final Property property;
        protected final Object key;
        protected final int flags;

        PropertyTransition(Property property) {
            this.property = property;
            this.key = property.getKey();
            this.flags = property.getFlags();
        }

        PropertyTransition(Object key, int flags) {
            this.property = null;
            this.key = key;
            this.flags = flags;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + ((key == null) ? 0 : key.hashCode());
            result = prime * result + flags;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            PropertyTransition other = (PropertyTransition) obj;
            return Objects.equals(this.key, other.key) && this.flags == other.flags;
        }

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
        protected boolean isWeak() {
            return getProperty().getLocation().isConstant();
        }
    }

    abstract static class TypedPropertyTransition extends PropertyTransition {
        /** A {@link Location} or the value's erased type. */
        private final Object locationOrType;

        TypedPropertyTransition(Property property, Object locationOrType) {
            super(property);
            this.locationOrType = locationOrType;
        }

        TypedPropertyTransition(Object key, int flags, Object locationType) {
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
            return Objects.equals(this.locationOrType, other.locationOrType);
        }

        public Object getLocationOrType() {
            return locationOrType;
        }

        protected final String propertyToString() {
            return "\"" + key + "\"" + ":" + locationOrType + (flags == 0 ? "" : "%" + flags);
        }
    }

    static final class AddPropertyTransition extends TypedPropertyTransition {

        AddPropertyTransition(Property property, Object locationOrType) {
            super(property, locationOrType);
        }

        AddPropertyTransition(Object key, int flags, Object locationType) {
            super(key, flags, locationType);
        }

        @Override
        public boolean isDirect() {
            return true;
        }

        @Override
        public String toString() {
            return String.format("add(%s)", propertyToString());
        }
    }

    static final class RemovePropertyTransition extends TypedPropertyTransition {
        private final boolean direct;

        RemovePropertyTransition(Property property, Object locationOrType, boolean direct) {
            super(property, locationOrType);
            this.direct = direct;
        }

        @Override
        public boolean isDirect() {
            return direct;
        }

        @Override
        public String toString() {
            return String.format("remove(%s)", propertyToString());
        }
    }

    static final class ObjectTypeTransition extends Transition {
        private final Object objectType;

        ObjectTypeTransition(Object objectType) {
            this.objectType = objectType;
        }

        public Object getObjectType() {
            return objectType;
        }

        @Override
        public boolean equals(Object other) {
            return super.equals(other) && Objects.equals(objectType, ((ObjectTypeTransition) other).objectType);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + ((objectType == null) ? 0 : objectType.hashCode());
            return result;
        }

        @Override
        public boolean isDirect() {
            return true;
        }

        @Override
        protected boolean isWeak() {
            return true;
        }

        @Override
        public String toString() {
            return String.format("objectType(%s)", getObjectType());
        }
    }

    abstract static class AbstractReplacePropertyTransition extends PropertyTransition {
        private final Property after;

        AbstractReplacePropertyTransition(Property before, Property after) {
            super(before);
            this.after = after;
        }

        public Property getPropertyBefore() {
            return this.getProperty();
        }

        public Property getPropertyAfter() {
            return after;
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            AbstractReplacePropertyTransition other = (AbstractReplacePropertyTransition) obj;
            return Objects.equals(this.property, other.property) && Objects.equals(this.after, other.after);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + property.hashCode();
            result = prime * result + after.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return String.format("replace(%s,%s)", getPropertyBefore(), getPropertyAfter());
        }

        @Override
        protected boolean isWeak() {
            return getPropertyBefore().getLocation().isConstant() || getPropertyAfter().getLocation().isConstant();
        }
    }

    static final class IndirectReplacePropertyTransition extends AbstractReplacePropertyTransition {

        IndirectReplacePropertyTransition(Property before, Property after) {
            super(before, after);
        }

        @Override
        public boolean isDirect() {
            return false;
        }
    }

    static final class DirectReplacePropertyTransition extends AbstractReplacePropertyTransition {

        DirectReplacePropertyTransition(Property before, Property after) {
            super(before, after);
        }

        @Override
        public boolean isDirect() {
            return true;
        }
    }

    static final class ShareShapeTransition extends Transition {

        ShareShapeTransition() {
        }

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
