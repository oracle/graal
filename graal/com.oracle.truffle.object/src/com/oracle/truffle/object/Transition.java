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
package com.oracle.truffle.object;

import java.util.*;

import com.oracle.truffle.api.object.*;

public abstract class Transition {
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

    public abstract static class PropertyTransition extends Transition {
        private final Property property;

        public PropertyTransition(Property property) {
            this.property = property;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + ((property == null) ? 0 : property.hashCode());
            return result;
        }

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

        public Property getProperty() {
            return property;
        }
    }

    public static final class AddPropertyTransition extends PropertyTransition {
        public AddPropertyTransition(Property property) {
            super(property);
        }

        @Override
        public boolean isDirect() {
            return true;
        }
    }

    public static final class RemovePropertyTransition extends PropertyTransition {
        public RemovePropertyTransition(Property property) {
            super(property);
        }

        @Override
        public boolean isDirect() {
            return false;
        }
    }

    public static final class ObjectTypeTransition extends Transition {
        private final ObjectType objectType;

        public ObjectTypeTransition(ObjectType objectType) {
            this.objectType = objectType;
        }

        public ObjectType getObjectType() {
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
    }

    public abstract static class AbstractReplacePropertyTransition extends PropertyTransition {
        private final Property after;

        public AbstractReplacePropertyTransition(Property before, Property after) {
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
            return super.equals(obj) && this.after.equals(((AbstractReplacePropertyTransition) obj).after);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + after.hashCode();
            return result;
        }
    }

    public static final class IndirectReplacePropertyTransition extends AbstractReplacePropertyTransition {
        public IndirectReplacePropertyTransition(Property before, Property after) {
            super(before, after);
        }

        @Override
        public boolean isDirect() {
            return false;
        }
    }

    public static final class DirectReplacePropertyTransition extends AbstractReplacePropertyTransition {
        public DirectReplacePropertyTransition(Property before, Property after) {
            super(before, after);
        }

        @Override
        public boolean isDirect() {
            return true;
        }
    }

    public static final class ReservePrimitiveArrayTransition extends Transition {
        public ReservePrimitiveArrayTransition() {
        }

        @Override
        public boolean isDirect() {
            return true;
        }
    }

    public String getShortName() {
        return this.getClass().getSimpleName().replaceFirst("Transition$", "").toLowerCase();
    }
}
