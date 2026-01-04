/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replaycomp;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.TriState;

/**
 * Object that cannot be directly deserialized because it invokes methods on the supplied arguments.
 * These methods cannot be replayed at the time of deserialization.
 */
sealed interface DelayedDeserializationObject {
    /**
     * Deserializes the object, returning the deserialized instance.
     *
     * @return the deserialized object
     */
    Object deserialize();

    /**
     * A {@link jdk.vm.ci.meta.Assumptions.LeafType} assumption that will be deserialized later.
     */
    final class LeafTypeWithDelayedDeserialization extends Assumptions.Assumption implements DelayedDeserializationObject {
        private final ResolvedJavaType context;

        LeafTypeWithDelayedDeserialization(ResolvedJavaType context) {
            this.context = context;
        }

        @Override
        public Object deserialize() {
            return new Assumptions.LeafType(context);
        }
    }

    /**
     * A {@link jdk.vm.ci.meta.Assumptions.ConcreteSubtype} assumption that will be deserialized
     * later.
     */
    final class ConcreteSubtypeWithDelayedDeserialization extends Assumptions.Assumption implements DelayedDeserializationObject {
        private final ResolvedJavaType context;
        private final ResolvedJavaType subtype;

        ConcreteSubtypeWithDelayedDeserialization(ResolvedJavaType context, ResolvedJavaType subtype) {
            this.context = context;
            this.subtype = subtype;
        }

        @Override
        public Object deserialize() {
            return new Assumptions.ConcreteSubtype(context, subtype);
        }
    }

    /**
     * A {@link JavaTypeProfile} that will be deserialized later.
     */
    final class JavaTypeProfileWithDelayedDeserialization implements DelayedDeserializationObject {
        private final TriState nullSeen;
        private final double notRecordedProbability;
        private final ProfiledTypeWithDelayedDeserialization[] items;

        JavaTypeProfileWithDelayedDeserialization(TriState nullSeen, double notRecordedProbability, ProfiledTypeWithDelayedDeserialization[] items) {
            this.nullSeen = nullSeen;
            this.notRecordedProbability = notRecordedProbability;
            this.items = items;
        }

        @Override
        public Object deserialize() {
            JavaTypeProfile.ProfiledType[] profiledTypes = new JavaTypeProfile.ProfiledType[items.length];
            for (int i = 0; i < items.length; i++) {
                profiledTypes[i] = items[i].deserialize();
            }
            return new JavaTypeProfile(nullSeen, notRecordedProbability, profiledTypes);
        }
    }

    /**
     * A {@link ProfiledTypeWithDelayedDeserialization} that will be deserialized later.
     */
    final class ProfiledTypeWithDelayedDeserialization implements DelayedDeserializationObject {
        private final ResolvedJavaType type;
        private final double probability;

        ProfiledTypeWithDelayedDeserialization(ResolvedJavaType type, double probability) {
            this.type = type;
            this.probability = probability;
        }

        @Override
        public JavaTypeProfile.ProfiledType deserialize() {
            return new JavaTypeProfile.ProfiledType(type, probability);
        }
    }
}
