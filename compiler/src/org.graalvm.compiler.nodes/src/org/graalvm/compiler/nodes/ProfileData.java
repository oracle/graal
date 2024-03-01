/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

import java.util.Arrays;

import jdk.vm.ci.meta.ProfilingInfo;

/**
 * Base class for knowledge about profiled branch probabilities or loop frequencies. Besides the
 * numerical probabilities/frequencies, instances of the concrete subclasses also indicate
 * {@linkplain ProfileSource the source of that information}.
 *
 * See the concrete subclasses for details: {@link BranchProbabilityData},
 * {@link LoopFrequencyData}, {@link SwitchProbabilityData}
 */
public abstract class ProfileData {

    /**
     * The smallest possible difference between two numerical probabilities/frequencies. Should be
     * used as a threshold for floating-point comparisons.
     */
    public static final double EPSILON = 1e-9;

    protected final ProfileSource profileSource;

    protected ProfileData(ProfileSource profileSource) {
        this.profileSource = profileSource;
    }

    /**
     * The source of nodes' knowledge about their branch probabilities or loop frequencies, in
     * decreasing order of trust. Information injected via annotations is most trusted, followed by
     * information from {@linkplain ProfilingInfo#isMature() mature} profiling info. All other
     * sources of probabilities/frequencies are unknown.
     */
    public enum ProfileSource {
        /**
         * The profiling information was injected via annotations, or in some other way during
         * compilation based on domain knowledge (e.g., exception paths are very improbable).
         */
        INJECTED,
        /**
         * The profiling information comes from a profiling execution of the current program.
         */
        PROFILED,
        /**
         * The profiling information was collected from a profiling execution of a different
         * program. For example, profiles of JDK methods collected from all benchmark runs and
         * aggregated. These profiles are then applied to JDK methods without a {@link #PROFILED}
         * profile.
         */
        ADOPTED,
        /**
         * The profiling information comes from the ML model.
         */
        INFERRED,
        /**
         * The profiling information comes from immature profiling information or some unknown
         * source.
         */
        UNKNOWN;

        /**
         * Combine the sources of knowledge about profiles. This returns the most trusted source of
         * the two, e.g., it treats a combination of profiled and unknown information as profiled
         * overall.
         *
         * For example, when deriving a loop's frequency from a trusted exit probability, we want to
         * treat the derived frequency as trusted as well, even if the loop contains some other
         * control flow with unknown branch probabilities.
         */
        public ProfileSource combine(ProfileSource other) {
            if (this.ordinal() < other.ordinal()) {
                return this;
            } else {
                return other;
            }
        }

        public static boolean isTrusted(ProfileSource source) {
            return source == INJECTED || source == PROFILED || source == ADOPTED || source == INFERRED;
        }

        public boolean isInjected() {
            return this == INJECTED;
        }

        public boolean isProfiled() {
            return this == PROFILED;
        }

        public boolean isAdopted() {
            return this == ADOPTED;
        }

        public boolean isInferred() {
            return this == INFERRED;
        }

        public boolean isUnknown() {
            return this == UNKNOWN;
        }
    }

    public ProfileSource getProfileSource() {
        return profileSource;
    }

    /**
     * Profile data for one successor of a node. Also used for two-way branches like {@link IfNode}
     * or {@link WithExceptionNode}. When used for a branch, the probability stored within is the
     * probability of a "designated" successor (e.g., the {@code true} or non-exceptional
     * successor), the other successor's probability can be computed from it.
     *
     * As instances of this class are immutable, any of the smart constructors may return shared
     * instances.
     */
    public static final class BranchProbabilityData extends ProfileData {
        private final double designatedSuccessorProbability;

        private static final BranchProbabilityData UNKNOWN_BRANCH_PROFILE = new BranchProbabilityData(0.5, ProfileSource.UNKNOWN);

        private BranchProbabilityData(double designatedSuccessorProbability, ProfileSource profileSource) {
            super(profileSource);
            this.designatedSuccessorProbability = designatedSuccessorProbability;
        }

        /**
         * Returns a profile data object with the given designated successor probability and profile
         * source.
         */
        public static BranchProbabilityData create(double designatedSuccessorProbability, ProfileSource profileSource) {
            if (designatedSuccessorProbability == 0.5 && profileSource == ProfileSource.UNKNOWN) {
                return UNKNOWN_BRANCH_PROFILE;
            }
            return new BranchProbabilityData(designatedSuccessorProbability, profileSource);
        }

        public double getDesignatedSuccessorProbability() {
            return designatedSuccessorProbability;
        }

        public double getNegatedProbability() {
            return 1.0 - designatedSuccessorProbability;
        }

        public BranchProbabilityData copy(double newProbability) {
            if (newProbability == designatedSuccessorProbability) {
                return this;
            }
            return BranchProbabilityData.create(newProbability, profileSource);
        }

        public BranchProbabilityData negated() {
            return copy(getNegatedProbability());
        }

        public static BranchProbabilityData injected(double probability) {
            return BranchProbabilityData.create(probability, ProfileSource.INJECTED);
        }

        public static BranchProbabilityData injected(double probability, boolean negated) {
            return negated ? injected(1.0 - probability) : injected(probability);
        }

        public static BranchProbabilityData profiled(double probability) {
            return BranchProbabilityData.create(probability, ProfileSource.PROFILED);
        }

        public static BranchProbabilityData adopted(double probability) {
            return BranchProbabilityData.create(probability, ProfileSource.ADOPTED);
        }

        public static BranchProbabilityData inferred(double probability) {
            return BranchProbabilityData.create(probability, ProfileSource.INFERRED);
        }

        /**
         * Returns a singleton branch profile object with an unknown source and designated successor
         * probability 0.5.
         */
        public static BranchProbabilityData unknown() {
            return UNKNOWN_BRANCH_PROFILE;
        }

        /**
         * Computes combined profile data for short-circuiting evaluation of {@code a || b}.
         */
        public static BranchProbabilityData combineShortCircuitOr(BranchProbabilityData a, BranchProbabilityData b) {
            double p1 = a.getDesignatedSuccessorProbability();
            double p2 = b.getDesignatedSuccessorProbability();
            double combinedProbability = p1 + (1 - p1) * p2;
            ProfileSource combinedSource = a.getProfileSource().combine(b.getProfileSource());
            return BranchProbabilityData.create(combinedProbability, combinedSource);
        }

        /**
         * Computes combined profile data for not short-circuiting evaluation of
         * {@code this && !other}.
         */
        public BranchProbabilityData combineAndWithNegated(BranchProbabilityData other) {
            double p = getDesignatedSuccessorProbability() * other.getNegatedProbability();
            return BranchProbabilityData.create(p, getProfileSource().combine(other.profileSource));
        }

        @Override
        public String toString() {
            return profileSource + " designatedSuccessorProbability: " + designatedSuccessorProbability;
        }
    }

    /**
     * Profile data for loop frequencies.
     */
    public static final class LoopFrequencyData extends ProfileData {
        private final double loopFrequency;

        public static final LoopFrequencyData DEFAULT = new LoopFrequencyData(1.0, ProfileSource.UNKNOWN);

        private LoopFrequencyData(double loopFrequency, ProfileSource profileSource) {
            super(profileSource);
            assert loopFrequency >= 1.0;
            this.loopFrequency = loopFrequency;
        }

        public static LoopFrequencyData create(double loopFrequency, ProfileSource profileSource) {
            if (loopFrequency == 1.0 && profileSource == ProfileSource.UNKNOWN) {
                return DEFAULT;
            }
            return new LoopFrequencyData(loopFrequency, profileSource);
        }

        public double getLoopFrequency() {
            return loopFrequency;
        }

        public LoopFrequencyData copy(double newFrequency) {
            return LoopFrequencyData.create(newFrequency, getProfileSource());
        }

        /**
         * Returns a new profile data with the loop frequency decremented by {@code decrement} (but
         * not sinking below the minimal frequency of 1.0).
         */
        public LoopFrequencyData decrementFrequency(double decrement) {
            double newFrequency = Math.max(1.0, getLoopFrequency() - decrement);
            return copy(newFrequency);
        }

        @Override
        public String toString() {
            return profileSource + " loopFrequency: " + loopFrequency;
        }
    }

    /**
     * Profile data for multi-way switches.
     */
    public static final class SwitchProbabilityData extends ProfileData {
        private final double[] keyProbabilities;

        private SwitchProbabilityData(double[] keyProbabilities, ProfileSource profileSource) {
            super(profileSource);
            this.keyProbabilities = keyProbabilities;
        }

        public static SwitchProbabilityData create(double[] keyProbabilities, ProfileSource profileSource) {
            return new SwitchProbabilityData(keyProbabilities, profileSource);
        }

        public double[] getKeyProbabilities() {
            return keyProbabilities;
        }

        public SwitchProbabilityData copy(double[] newKeyProbabilities) {
            return new SwitchProbabilityData(newKeyProbabilities, profileSource);
        }

        public static SwitchProbabilityData profiled(double[] keyProbabilities) {
            return new SwitchProbabilityData(keyProbabilities, ProfileSource.PROFILED);
        }

        public static SwitchProbabilityData unknown(double[] keyProbabilities) {
            return new SwitchProbabilityData(keyProbabilities, ProfileSource.UNKNOWN);
        }

        @Override
        public String toString() {
            return profileSource + " keyProbabilities: " + Arrays.toString(keyProbabilities);
        }
    }
}
