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
package org.graalvm.profdiff.matching.method;

import org.graalvm.profdiff.core.CompilationUnit;
import org.graalvm.profdiff.util.Writer;

/**
 * Represents a pair of two matched hot compilation units of the same method.
 */
public class MatchedCompilationUnit {
    /**
     * The compilation unit from the first experiment.
     */
    private final CompilationUnit compilationUnit1;

    /**
     * The compilation unit from the second experiment.
     */
    private final CompilationUnit compilationUnit2;

    /**
     * Gets the matched compilation unit from the first experiment.
     */
    public CompilationUnit getFirstCompilationUnit() {
        return compilationUnit1;
    }

    /**
     * Gets the matched compilation unit from the second experiment.
     */
    public CompilationUnit getSecondCompilationUnit() {
        return compilationUnit2;
    }

    MatchedCompilationUnit(CompilationUnit compilationUnit1, CompilationUnit compilationUnit2) {
        assert compilationUnit1.isHot() && compilationUnit2.isHot();
        this.compilationUnit1 = compilationUnit1;
        this.compilationUnit2 = compilationUnit2;
    }

    /**
     * Writes a string describing this pair of matched compilations.
     *
     * @param writer the destination writer
     */
    public void writeHeader(Writer writer) {
        writer.writeln("Compilation " + compilationUnit1.getCompilationId() + " (" + compilationUnit1.createExecutionSummary() + ") in experiment " +
                        compilationUnit1.getExperiment().getExperimentId() +
                        " vs compilation " + compilationUnit2.getCompilationId() + " (" + compilationUnit2.createExecutionSummary() + ") in experiment " +
                        compilationUnit2.getExperiment().getExperimentId());
    }
}
