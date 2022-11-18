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

import org.graalvm.profdiff.util.Writer;

/**
 * Receiver-type profile information for a concrete inlining tree node.
 */
public class ReceiverTypeProfile {
    /**
     * A single profiled type of the receiver.
     */
    public static class ProfiledType {
        private final String typeName;

        private final double probability;

        private final String concreteMethodName;

        public ProfiledType(String typeName, double probability, String concreteMethodName) {
            this.typeName = typeName;
            this.probability = probability;
            this.concreteMethodName = concreteMethodName;
        }

        public String getTypeName() {
            return typeName;
        }

        public double getProbability() {
            return probability;
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

    public boolean isMature() {
        return isMature;
    }

    public List<ProfiledType> getProfiledTypes() {
        if (profiledTypes == null) {
            return List.of();
        }
        return profiledTypes;
    }

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
}
