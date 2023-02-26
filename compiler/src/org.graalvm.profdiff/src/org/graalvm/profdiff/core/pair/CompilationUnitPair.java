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
package org.graalvm.profdiff.core.pair;

import org.graalvm.profdiff.core.CompilationUnit;
import org.graalvm.profdiff.core.ExperimentId;

/**
 * A pair of compilations of the same method in two experiments. At most one of the compilations may
 * be {@code null}.
 */
public class CompilationUnitPair {
    /**
     * A compilation unit from the first experiment.
     */
    private final CompilationUnit compilationUnit1;

    /**
     * A compilation unit from the second experiment.
     */
    private final CompilationUnit compilationUnit2;

    /**
     * Constructs a pair of compilation units. The first one must belong to {@link ExperimentId#ONE}
     * and the second one to {@link ExperimentId#TWO}. They must be compilations of the same root
     * method.
     *
     * @param compilationUnit1 a compilation unit from the first experiment
     * @param compilationUnit2 a compilation unit from the second experiment
     */
    public CompilationUnitPair(CompilationUnit compilationUnit1, CompilationUnit compilationUnit2) {
        assert compilationUnit1 != null || compilationUnit2 != null;
        assert !bothNotNull() || compilationUnit1.getMethod().getMethodName().equals(compilationUnit2.getMethod().getMethodName());
        this.compilationUnit1 = compilationUnit1;
        this.compilationUnit2 = compilationUnit2;
    }

    /**
     * Gets the compilation unit from the first experiment.
     */
    public CompilationUnit getCompilationUnit1() {
        return compilationUnit1;
    }

    /**
     * Gets the compilation unit from the second experiment.
     */
    public CompilationUnit getCompilationUnit2() {
        return compilationUnit2;
    }

    /**
     * Returns {@code true} if both compilation units are not {@code null}.
     */
    public boolean bothNotNull() {
        return compilationUnit1 != null && compilationUnit2 != null;
    }

    /**
     * Returns {@code true} if both compilation units are not {@code null} and both are hot.
     */
    public boolean bothHot() {
        return bothNotNull() && compilationUnit1.isHot() && compilationUnit2.isHot();
    }

    /**
     * Returns {@code true} if at least one of the compilation units is hot.
     */
    public boolean someHot() {
        return (compilationUnit1 != null && compilationUnit1.isHot()) || (compilationUnit2 != null && compilationUnit2.isHot());
    }

    /**
     * Returns the first non-null compilation unit.
     */
    public CompilationUnit firstNonNull() {
        return compilationUnit1 == null ? compilationUnit2 : compilationUnit1;
    }

    /**
     * Formats a header containing the compilation ID, an execution summary, the experiment ID of
     * each hot compilation in the pair. Returns {@code null} if the pair does not contain a hot
     * compilation. The execution summary is omitted if the profile is not available.
     *
     * @return a header for hot compilations or {@code null}
     */
    public String formatHeaderForHotCompilations() {
        if (!someHot()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("Compilation ");
        if (bothHot()) {
            sb.append(compilationUnit1.getCompilationId());
            if (compilationUnit1.getMethod().getExperiment().isProfileAvailable()) {
                sb.append(" (").append(compilationUnit1.createExecutionSummary()).append(")");
            }
            sb.append(" in experiment ").append(compilationUnit1.getMethod().getExperiment().getExperimentId()).append(" vs compilation ").append(compilationUnit2.getCompilationId());
            if (compilationUnit2.getMethod().getExperiment().isProfileAvailable()) {
                sb.append(" (").append(compilationUnit2.createExecutionSummary()).append(")");
            }
            sb.append(" in experiment ").append(compilationUnit2.getMethod().getExperiment().getExperimentId());
        } else {
            CompilationUnit compilationUnit = compilationUnit1 != null && compilationUnit1.isHot() ? compilationUnit1 : compilationUnit2;
            sb.append(compilationUnit.getCompilationId()).append(" is ").append(compilationUnit.isHot() ? "hot" : "present").append(" only in experiment ").append(
                            compilationUnit.getMethod().getExperiment().getExperimentId());
        }
        return sb.toString();
    }
}
