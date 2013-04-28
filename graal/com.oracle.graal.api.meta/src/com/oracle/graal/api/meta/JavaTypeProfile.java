/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.meta;

import java.io.*;
import java.util.*;

import com.oracle.graal.api.meta.ProfilingInfo.*;

/**
 * This profile object represents the type profile at a specific BCI. The precision of the supplied
 * values may vary, but a runtime that provides this information should be aware that it will be
 * used to guide performance-critical decisions like speculative inlining, etc.
 */
public final class JavaTypeProfile implements Serializable {

    private static final long serialVersionUID = -6877016333706838441L;

    /**
     * A profiled type that has a probability. Profiled types are naturally sorted in descending
     * order of their probabilities.
     */
    public static final class ProfiledType implements Comparable<ProfiledType>, Serializable {

        private static final long serialVersionUID = 7838575753661305744L;

        public static final ProfiledType[] EMPTY_ARRAY = new ProfiledType[0];

        private final ResolvedJavaType type;
        private final double probability;

        public ProfiledType(ResolvedJavaType type, double probability) {
            assert type != null;
            assert probability >= 0.0D && probability <= 1.0D;
            this.type = type;
            this.probability = probability;
        }

        /**
         * Returns the type for this profile entry.
         */
        public ResolvedJavaType getType() {
            return type;
        }

        /**
         * Returns the estimated probability of {@link #getType()}.
         * 
         * @return double value >= 0.0 and <= 1.0
         */
        public double getProbability() {
            return probability;
        }

        @Override
        public int compareTo(ProfiledType o) {
            if (getProbability() > o.getProbability()) {
                return -1;
            } else if (getProbability() < o.getProbability()) {
                return 1;
            }
            return 0;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            long temp;
            temp = Double.doubleToLongBits(probability);
            result = prime * result + (int) (temp ^ (temp >>> 32));
            result = prime * result + type.hashCode();
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
            ProfiledType other = (ProfiledType) obj;
            if (Double.doubleToLongBits(probability) != Double.doubleToLongBits(other.probability)) {
                return false;
            }
            return type.equals(other.type);
        }

        @Override
        public String toString() {
            return "{" + type.getName() + ", " + probability + "}";
        }
    }

    private final TriState nullSeen;
    private final double notRecordedProbability;
    private final ProfiledType[] ptypes;

    /**
     * Determines if an array of profiled types are sorted in descending order of their
     * probabilities.
     */
    private static boolean isSorted(ProfiledType[] ptypes) {
        for (int i = 1; i < ptypes.length; i++) {
            if (ptypes[i - 1].getProbability() < ptypes[i].getProbability()) {
                return false;
            }
        }
        return true;
    }

    public JavaTypeProfile(TriState nullSeen, double notRecordedProbability, ProfiledType... ptypes) {
        this.nullSeen = nullSeen;
        this.ptypes = ptypes;
        assert notRecordedProbability != Double.NaN;
        this.notRecordedProbability = notRecordedProbability;
        assert isSorted(ptypes);
    }

    /**
     * Returns the estimated probability of all types that could not be recorded due to profiling
     * limitations.
     * 
     * @return double value >= 0.0 and <= 1.0
     */
    public double getNotRecordedProbability() {
        return notRecordedProbability;
    }

    /**
     * Returns whether a null value was at the type check.
     */
    public TriState getNullSeen() {
        return nullSeen;
    }

    /**
     * A list of types for which the runtime has recorded probability information. Note that this
     * includes both positive and negative types where a positive type is a subtype of the checked
     * type and a negative type is not.
     */
    public ProfiledType[] getTypes() {
        return ptypes;
    }

    /**
     * Searches for an entry of a given resolved Java type.
     * 
     * @param type the type for which an entry should be searched
     * @return the entry or null if no entry for this type can be found
     */
    public ProfiledType findEntry(ResolvedJavaType type) {
        if (ptypes != null) {
            for (ProfiledType pt : ptypes) {
                if (pt.getType() == type) {
                    return pt;
                }
            }
        }
        return null;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("JavaTypeProfile[");
        builder.append(this.nullSeen);
        builder.append(", ");
        if (ptypes != null) {
            for (ProfiledType pt : ptypes) {
                builder.append(pt.toString());
                builder.append(", ");
            }
        }
        builder.append(this.notRecordedProbability);
        builder.append("]");
        return builder.toString();
    }

    public JavaTypeProfile restrict(JavaTypeProfile otherProfile) {
        if (otherProfile.getNotRecordedProbability() > 0.0) {
            // Not useful for restricting since there is an unknown set of types occuring.
            return this;
        }

        if (this.getNotRecordedProbability() > 0.0) {
            // We are unrestricted, so the other profile is always a better estimate.
            return otherProfile;
        }

        ArrayList<ProfiledType> result = new ArrayList<>();
        for (int i = 0; i < getTypes().length; i++) {
            ProfiledType ptype = getTypes()[i];
            ResolvedJavaType type = ptype.getType();
            if (otherProfile.isIncluded(type)) {
                result.add(ptype);
            }
        }

        TriState newNullSeen = (otherProfile.getNullSeen() == TriState.FALSE) ? TriState.FALSE : this.nullSeen;
        double newNotRecorded = this.notRecordedProbability;
        return createAdjustedProfile(result, newNullSeen, newNotRecorded);
    }

    public boolean isIncluded(ResolvedJavaType type) {
        if (this.getNotRecordedProbability() > 0.0) {
            return true;
        } else {
            for (int i = 0; i < getTypes().length; i++) {
                ProfiledType ptype = getTypes()[i];
                ResolvedJavaType curType = ptype.getType();
                if (curType == type) {
                    return true;
                }
            }
        }
        return false;
    }

    public JavaTypeProfile restrict(ResolvedJavaType declaredType, boolean nonNull) {
        ArrayList<ProfiledType> result = new ArrayList<>();
        for (int i = 0; i < getTypes().length; i++) {
            ProfiledType ptype = getTypes()[i];
            ResolvedJavaType type = ptype.getType();
            if (declaredType.isAssignableFrom(type)) {
                result.add(ptype);
            }
        }

        TriState newNullSeen = (nonNull) ? TriState.FALSE : this.nullSeen;
        double newNotRecorded = this.getNotRecordedProbability();
        // Assume for the types not recorded, the incompatibility rate is the same.
        if (getTypes().length != 0) {
            newNotRecorded *= ((double) result.size() / (double) getTypes().length);
        }
        return createAdjustedProfile(result, newNullSeen, newNotRecorded);
    }

    private JavaTypeProfile createAdjustedProfile(ArrayList<ProfiledType> result, TriState newNullSeen, double newNotRecorded) {
        if (result.size() != this.getTypes().length || newNotRecorded != getNotRecordedProbability() || newNullSeen != this.nullSeen) {
            if (result.size() == 0) {
                return new JavaTypeProfile(newNullSeen, 1.0, ProfiledType.EMPTY_ARRAY);
            }
            double probabilitySum = 0.0;
            for (int i = 0; i < result.size(); i++) {
                probabilitySum += result.get(i).getProbability();
            }
            probabilitySum += newNotRecorded;

            double factor = 1.0 / probabilitySum; // Normalize to 1.0
            assert factor > 1.0;
            ProfiledType[] newResult = new ProfiledType[result.size()];
            for (int i = 0; i < newResult.length; ++i) {
                ProfiledType curType = result.get(i);
                newResult[i] = new ProfiledType(curType.getType(), Math.min(1.0, curType.getProbability() * factor));
            }
            double newNotRecordedTypeProbability = Math.min(1.0, newNotRecorded * factor);
            return new JavaTypeProfile(newNullSeen, newNotRecordedTypeProbability, newResult);
        }
        return this;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other instanceof JavaTypeProfile) {
            JavaTypeProfile javaTypeProfile = (JavaTypeProfile) other;
            if (javaTypeProfile.nullSeen != nullSeen) {
                return false;
            }
            if (javaTypeProfile.notRecordedProbability != notRecordedProbability) {
                return false;
            }
            if (javaTypeProfile.ptypes.length != ptypes.length) {
                return false;
            }

            for (int i = 0; i < ptypes.length; ++i) {
                if (!ptypes[i].equals(javaTypeProfile.ptypes[i])) {
                    return false;
                }
            }

            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return nullSeen.hashCode() + (int) Double.doubleToLongBits(notRecordedProbability) + ptypes.length * 13;
    }
}
