/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
        /** @since 0.17 or earlier */
        public RemovePropertyTransition(Property property) {
            super(property);
        }

        /** @since 0.17 or earlier */
        @Override
        public boolean isDirect() {
            return false;
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
}
