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
     * The default verbosity level.
     */
    DEFAULT(false, true, false),

    /**
     * High verbosity level.
     */
    HIGH(false, true, true),

    /**
     * Maximum verbosity level.
     */
    MAX(true, false, true);

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
     * Constructs a verbosity level.
     *
     * @param printOptimizationTree optimization tree should be printed for each hot compilation
     * @param diffCompilations compilations should be matched and diffed
     * @param bciLongForm byte code indices should be printed in the long form
     */
    VerbosityLevel(boolean printOptimizationTree, boolean diffCompilations, boolean bciLongForm) {
        this.printOptimizationTree = printOptimizationTree;
        this.diffCompilations = diffCompilations;
        this.bciLongForm = bciLongForm;
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
}
