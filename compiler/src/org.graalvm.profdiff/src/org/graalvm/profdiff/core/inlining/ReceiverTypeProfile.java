/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.profdiff.core.Writer;

/**
 * Receiver-type profile information for a concrete inlining tree node.
 *
 * @param isMature {@code true} iff the profile is mature
 * @param profiledTypes type names with probabilities
 */
public record ReceiverTypeProfile(boolean isMature, List<ProfiledType> profiledTypes) {

    public ReceiverTypeProfile(boolean isMature, List<ProfiledType> profiledTypes) {
        this.isMature = isMature;
        this.profiledTypes = profiledTypes == null ? List.of() : profiledTypes;
    }

    /**
     * A single profiled type of the receiver.
     *
     * @param typeName the name of the receiver type
     * @param probability the probability of the receiver being an instance of this type
     * @param concreteMethodName the concrete method called when the receiver is an instance of this
     *            type
     */
    public record ProfiledType(String typeName, double probability, String concreteMethodName) {
    }

    /**
     * Writes a representation of this profile using the destination writer. Includes each profiled
     * type on a separate line, with the probability, the name receiver type, and the name of the
     * concrete method.
     *
     * @param writer the destination writer
     */
    public void write(Writer writer) {
        for (ProfiledType profiledType : profiledTypes()) {
            writer.write(String.format("%5.2f%% %s", profiledType.probability * 100, profiledType.typeName));
            if (profiledType.concreteMethodName != null) {
                writer.writeln(" -> " + profiledType.concreteMethodName);
            } else {
                writer.writeln();
            }
        }
    }
}
