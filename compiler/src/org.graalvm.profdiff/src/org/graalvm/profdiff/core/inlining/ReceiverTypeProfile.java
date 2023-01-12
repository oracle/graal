/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.profdiff.core.inlining;

import java.util.List;
import java.util.Objects;

import org.graalvm.profdiff.core.Writer;

/**
 * Receiver-type profile information for a concrete inlining tree node.
 */
public class ReceiverTypeProfile {
    /**
     * A single profiled type of the receiver.
     */
    public static class ProfiledType {
        /**
         * The name of the receiver type.
         */
        private final String typeName;

        /**
         * The probability of the receiver being an instance of this type.
         */
        private final double probability;

        /**
         * The concrete method called when the receiver is an instance of this type.
         */
        private final String concreteMethodName;

        public ProfiledType(String typeName, double probability, String concreteMethodName) {
            this.typeName = typeName;
            this.probability = probability;
            this.concreteMethodName = concreteMethodName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ProfiledType)) {
                return false;
            }

            ProfiledType that = (ProfiledType) o;
            return Double.compare(that.probability, probability) == 0 && Objects.equals(typeName, that.typeName) &&
                            Objects.equals(concreteMethodName, that.concreteMethodName);
        }

        @Override
        public int hashCode() {
            int result = typeName != null ? typeName.hashCode() : 0;
            long temp = Double.doubleToLongBits(probability);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            result = 31 * result + (concreteMethodName != null ? concreteMethodName.hashCode() : 0);
            return result;
        }

        /**
         * Gets the name of the receiver type.
         */
        public String getTypeName() {
            return typeName;
        }

        /**
         * Gets the probability of the receiver being an instance of this type.
         */
        public double getProbability() {
            return probability;
        }

        /**
         * Gets the concrete method called when the receiver is an instance of this type.
         */
        public String getConcreteMethodName() {
            return concreteMethodName;
        }
    }

    /**
     * The maturity of the profile.
     */
    private final boolean isMature;

    /**
     * Type names with probabilities.
     */
    private final List<ProfiledType> profiledTypes;

    public ReceiverTypeProfile(boolean isMature, List<ProfiledType> profiledTypes) {
        this.isMature = isMature;
        this.profiledTypes = profiledTypes;
    }

    /**
     * Returns {@code true} iff the profile is mature.
     */
    public boolean isMature() {
        return isMature;
    }

    /**
     * Gets the list of profiled types.
     */
    public List<ProfiledType> getProfiledTypes() {
        if (profiledTypes == null) {
            return List.of();
        }
        return profiledTypes;
    }

    /**
     * Writes a representation of this profile using the destination writer. Includes each profiled
     * type on a separate line, with the probability, the name receiver type, and the name of the
     * concrete method.
     *
     * @param writer the destination writer
     */
    public void write(Writer writer) {
        for (ProfiledType profiledType : getProfiledTypes()) {
            writer.write(String.format("%5.2f%% %s", profiledType.getProbability() * 100, profiledType.getTypeName()));
            if (profiledType.concreteMethodName != null) {
                writer.writeln(" -> " + profiledType.concreteMethodName);
            } else {
                writer.writeln();
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ReceiverTypeProfile)) {
            return false;
        }

        ReceiverTypeProfile that = (ReceiverTypeProfile) o;
        return isMature == that.isMature && Objects.equals(profiledTypes, that.profiledTypes);
    }

    @Override
    public int hashCode() {
        int result = (isMature ? 1 : 0);
        result = 31 * result + (profiledTypes != null ? profiledTypes.hashCode() : 0);
        return result;
    }
}
