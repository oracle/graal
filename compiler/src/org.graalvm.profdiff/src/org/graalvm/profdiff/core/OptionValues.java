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
package org.graalvm.profdiff.core;

import org.graalvm.profdiff.core.optimization.Optimization;

/**
 * Values of tunable options of the program.
 */
public class OptionValues {
    /**
     * The policy which designates hot compilation units.
     */
    private final HotCompilationUnitPolicy hotCompilationUnitPolicy;

    /**
     * {@code true} iff {@link OptimizationContextTree an optimization context tree} should be built
     * and displayed instead of a separate inlining and optimization tree.
     */
    private final boolean optimizationContextTreeEnabled;

    /**
     * Whether a matching of hot compilations should be created for each method and their diffs
     * printed.
     */
    private final boolean diffCompilations;

    /**
     * Byte code indices should be printed in the long form, i.e., as
     * {@link Optimization#getPosition() the whole ordered position map}, rather than as a single
     * bci of the last inlinee.
     */
    private final boolean bciLongForm;

    /**
     * Inlining tree should be sorted (i.e., the children of each node should be sorted).
     */
    private final boolean sortInliningTree;

    /**
     * Optimizations and subphases performed by optimization phases that are considered unordered
     * (i.e., the order of their performed optimizations is not important) should be sorted in each
     * optimization tree.
     */
    private final boolean sortUnorderedPhases;

    /**
     * Remove optimization subtrees of the phases that perform a lot of optimizations with little
     * individual impact.
     */
    private final boolean removeVeryDetailedPhases;

    /**
     * Show only the difference when trees are compared.
     */
    private final boolean pruneIdentities;

    /**
     * Create {@link CompilationFragment compilation fragments}.
     */
    private final boolean createFragments;

    /**
     * Always print the reasoning for inlining decisions.
     */
    private final boolean alwaysPrintInlinerReasoning;

    /**
     * Constructs option values.
     *
     * @param hotCompilationUnitPolicy the policy which designates hot compilation units
     * @param optimizationContextTreeEnabled the optimization-context tree should be built and
     *            displayed
     * @param diffCompilations compilations should be matched and diffed
     * @param bciLongForm byte code indices should be printed in the long form
     * @param sortInliningTree the nodes of each inlining tree should be sorted
     * @param sortUnorderedPhases phases where the order of optimizations is not important should be
     *            sorted
     * @param removeVeryDetailedPhases phases that perform a lot of optimizations with little
     *            individual impact should be removed
     * @param pruneIdentities only differences when trees are compared
     * @param createFragments create {@link CompilationFragment compilation fragments}
     * @param alwaysPrintInlinerReasoning always print the reasoning for inlining decisions
     */
    public OptionValues(HotCompilationUnitPolicy hotCompilationUnitPolicy, boolean optimizationContextTreeEnabled,
                    boolean diffCompilations, boolean bciLongForm, boolean sortInliningTree, boolean sortUnorderedPhases,
                    boolean removeVeryDetailedPhases, boolean pruneIdentities, boolean createFragments,
                    boolean alwaysPrintInlinerReasoning) {
        this.hotCompilationUnitPolicy = hotCompilationUnitPolicy;
        this.optimizationContextTreeEnabled = optimizationContextTreeEnabled;
        this.diffCompilations = diffCompilations;
        this.bciLongForm = bciLongForm;
        this.sortInliningTree = sortInliningTree;
        this.sortUnorderedPhases = sortUnorderedPhases;
        this.removeVeryDetailedPhases = removeVeryDetailedPhases;
        this.pruneIdentities = pruneIdentities;
        this.createFragments = createFragments;
        this.alwaysPrintInlinerReasoning = alwaysPrintInlinerReasoning;
    }

    /**
     * Constructs option values with the default {@link HotCompilationUnitPolicy} and all options
     * {@code false}.
     */
    public OptionValues() {
        this(new HotCompilationUnitPolicy(), false, false, false, false, false, false, false, false, false);
    }

    /**
     * Returns a builder for {@link OptionValues}.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final HotCompilationUnitPolicy hotCompilationUnitPolicy;
        private boolean optimizationContextTreeEnabled;
        private boolean diffCompilations;
        private boolean bciLongForm;
        private boolean alwaysPrintInlinerReasoning;

        private Builder() {
            this.hotCompilationUnitPolicy = new HotCompilationUnitPolicy();
        }

        /**
         * Builds and returns the {@link OptionValues}.
         */
        public OptionValues build() {
            return new OptionValues(hotCompilationUnitPolicy, optimizationContextTreeEnabled, diffCompilations, bciLongForm, false, false, false, false, false, alwaysPrintInlinerReasoning);
        }

        public Builder withOptimizationContextTreeEnabled(boolean newOptimizationContextTreeEnabled) {
            this.optimizationContextTreeEnabled = newOptimizationContextTreeEnabled;
            return this;
        }

        public Builder withDiffCompilations(boolean newDiffCompilations) {
            this.diffCompilations = newDiffCompilations;
            return this;
        }

        public Builder withBCILongForm(boolean newBciLongForm) {
            this.bciLongForm = newBciLongForm;
            return this;
        }

        public Builder withAlwaysPrintInlinerReasoning(boolean newAlwaysPrintInlinerReasoning) {
            this.alwaysPrintInlinerReasoning = newAlwaysPrintInlinerReasoning;
            return this;
        }
    }

    /**
     * Returns {@code true} iff {@link OptimizationContextTree an optimization context tree} should
     * be built and displayed instead of a separate inlining and optimization tree.
     */
    public boolean isOptimizationContextTreeEnabled() {
        return optimizationContextTreeEnabled;
    }

    /**
     * Returns the policy which designates hot compilation units.
     */
    public HotCompilationUnitPolicy getHotCompilationUnitPolicy() {
        return hotCompilationUnitPolicy;
    }

    /**
     * Returns whether compilations should be matched and diffed.
     */
    public boolean shouldDiffCompilations() {
        return diffCompilations;
    }

    /**
     * Returns whether byte code indices should be printed in the long form.
     */
    public boolean isBCILongForm() {
        return bciLongForm;
    }

    /**
     * Returns whether the nodes of each inlining tree should be sorted.
     */
    public boolean shouldSortInliningTree() {
        return sortInliningTree;
    }

    /**
     * Returns whether phases where the order of optimizations is not important should be sorted.
     */
    public boolean shouldSortUnorderedPhases() {
        return sortUnorderedPhases;
    }

    /**
     * Returns whether the phases that perform a lot of optimizations with little individual impact
     * should be removed in each optimization tree.
     */
    public boolean shouldRemoveVeryDetailedPhases() {
        return removeVeryDetailedPhases;
    }

    /**
     * Returns whether only the difference should be shown when trees are compared.
     */
    public boolean shouldPruneIdentities() {
        return pruneIdentities;
    }

    /**
     * Returns {@code true} iff {@link CompilationFragment compilation fragments} should be created.
     */
    public boolean shouldCreateFragments() {
        return createFragments;
    }

    /**
     * Returns {@code true} iff the reasoning for inlining decisions should be always printed.
     */
    public boolean shouldAlwaysPrintInlinerReasoning() {
        return alwaysPrintInlinerReasoning;
    }
}
