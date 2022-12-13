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
package org.graalvm.profdiff.core;

import org.graalvm.profdiff.core.optimization.Optimization;

/**
 * Represents the requested level of detail in the output. The code dependent should query the
 * getters rather than use the constants directly.
 */
public enum VerbosityLevel {
    /**
     * Low verbosity level.
     */
    LOW(false, true, false, true, true, true, true),

    /**
     * The default verbosity level.
     */
    DEFAULT(false, true, false, true, false, false, false),

    /**
     * High verbosity level.
     */
    HIGH(false, true, true, true, false, false, false),

    /**
     * Maximum verbosity level.
     */
    MAX(true, false, true, true, false, false, false);

    /**
     * Whether the optimization tree should be printed for each hot compilation unit.
     */
    private final boolean printOptimizationTree;

    /**
     * Whether a matching of hot compilations should be created for each method and their diffs
     * printed. If a compilation does not have match, its optimization tree is printed instead
     * (similarly to {@link #printOptimizationTree}).
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
    private final boolean showOnlyDiff;

    /**
     * Constructs a verbosity level.
     *
     * @param printOptimizationTree optimization tree should be printed for each hot compilation
     * @param diffCompilations compilations should be matched and diffed
     * @param bciLongForm byte code indices should be printed in the long form
     * @param sortInliningTree he nodes of each inlining tree should be sorted
     * @param sortUnorderedPhases phases where the order of optimizations is not important should be
     *            sorted
     * @param removeVeryDetailedPhases phases that perform a lot of optimizations with little
     *            individual impact should be removed
     * @param showOnlyDiff show only the difference when trees are compared
     */
    VerbosityLevel(boolean printOptimizationTree, boolean diffCompilations, boolean bciLongForm, boolean sortInliningTree, boolean sortUnorderedPhases, boolean removeVeryDetailedPhases,
                    boolean showOnlyDiff) {
        this.printOptimizationTree = printOptimizationTree;
        this.diffCompilations = diffCompilations;
        this.bciLongForm = bciLongForm;
        this.sortInliningTree = sortInliningTree;
        this.sortUnorderedPhases = sortUnorderedPhases;
        this.removeVeryDetailedPhases = removeVeryDetailedPhases;
        this.showOnlyDiff = showOnlyDiff;
    }

    /**
     * Returns whether the optimization tree should be printed for each hot compilation unit.
     */
    public boolean shouldPrintOptimizationTree() {
        return printOptimizationTree;
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
    public boolean isBciLongForm() {
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
    public boolean shouldShowOnlyDiff() {
        return showOnlyDiff;
    }
}
