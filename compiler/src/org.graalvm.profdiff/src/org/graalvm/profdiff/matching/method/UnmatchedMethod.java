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

import java.util.List;

import org.graalvm.profdiff.core.CompilationUnit;
import org.graalvm.profdiff.core.Experiment;
import org.graalvm.profdiff.core.VerbosityLevel;
import org.graalvm.profdiff.util.Writer;

/**
 * Represents a method that was not matched with any other method from the other experiment.
 */
public class UnmatchedMethod {
    /**
     * The experiment to which this unmatched method belongs.
     */
    private final Experiment experiment;

    /**
     * The full signature of this unmatched method.
     */
    private final String compilationMethodName;

    /**
     * The list of hot compilations of this method.
     */
    private final List<CompilationUnit> compilationUnits;

    UnmatchedMethod(Experiment experiment, String compilationMethodName, List<CompilationUnit> compilationUnits) {
        this.experiment = experiment;
        this.compilationMethodName = compilationMethodName;
        this.compilationUnits = compilationUnits;
    }

    /**
     * Gets the experiment to which this unmatched method belongs.
     */
    public Experiment getExperiment() {
        return experiment;
    }

    /**
     * Gets the compilation method name of this method.
     *
     * @see CompilationUnit#getCompilationMethodName()
     */
    public String getCompilationMethodName() {
        return compilationMethodName;
    }

    /**
     * Writes a header identifying this method and
     * {@link Experiment#writeCompilationUnits(Writer, String) the list of compilations} of this
     * method.
     *
     * @param writer the destination writer
     * @param experiment1 the first experiment
     * @param experiment2 the second experiment
     */
    private void writeHeaderAndCompilationList(Writer writer, Experiment experiment1, Experiment experiment2) {
        writer.writeln("Method " + compilationMethodName + " is hot only in experiment " + experiment.getExperimentId());
        writer.increaseIndent();
        experiment1.writeCompilationUnits(writer, compilationMethodName);
        experiment2.writeCompilationUnits(writer, compilationMethodName);
        writer.decreaseIndent();
    }

    /**
     * Writes the full description of the method according to the current verbosity level. Includes
     * {@link #writeHeaderAndCompilationList a header and the list of compilations}. If enabled,
     * writes optimization trees for each compilation.
     *
     * @param writer the destination writer
     * @param experiment1 the first experiment
     * @param experiment2 the second experiment
     */
    public void write(Writer writer, Experiment experiment1, Experiment experiment2) {
        writeHeaderAndCompilationList(writer, experiment1, experiment2);
        VerbosityLevel verbosity = writer.getVerbosityLevel();
        if (verbosity.shouldPrintOptimizationTree() || verbosity.shouldDiffCompilations()) {
            writer.increaseIndent();
            for (CompilationUnit compilationUnit : compilationUnits) {
                compilationUnit.write(writer);
            }
            writer.decreaseIndent();
        }
    }
}
