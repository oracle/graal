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
package org.graalvm.bisect.matching.method;

import java.util.List;

import org.graalvm.bisect.core.ExecutedMethod;
import org.graalvm.bisect.core.Experiment;
import org.graalvm.bisect.util.Writer;

/**
 * Represents a Java method that was not matched with any other Java method from the other experiment.
 */
public class ExtraMethod {
    /**
     * Gets the experiment to which this Java method belongs.
     *
     * @return the experiment of this Java method
     */
    public Experiment getExperiment() {
        return experiment;
    }

    private final Experiment experiment;

    /**
     * Gets the compilation method name of this Java method.
     *
     * @return the compilation name of this method
     * @see ExecutedMethod#getCompilationMethodName()
     */
    public String getCompilationMethodName() {
        return compilationMethodName;
    }

    private final String compilationMethodName;

    /**
     * The list of hot compilations of this method.
     */
    private final List<ExecutedMethod> executedMethods;

    ExtraMethod(Experiment experiment, String compilationMethodName, List<ExecutedMethod> executedMethods) {
        this.experiment = experiment;
        this.compilationMethodName = compilationMethodName;
        this.executedMethods = executedMethods;
    }

    /**
     * Writes a header identifying this method and
     * {@link Experiment#writeMethodCompilationList(Writer, String) the list of compilations} of
     * this method.
     *
     * @param writer the destination writer
     * @param experiment1 the first experiment
     * @param experiment2 the second experiment
     */
    public void writeHeaderAndCompilationList(Writer writer, Experiment experiment1, Experiment experiment2) {
        writer.writeln("Method " + compilationMethodName + " is hot only in experiment " + experiment.getExperimentId());
        writer.increaseIndent();
        experiment1.writeMethodCompilationList(writer, compilationMethodName);
        experiment2.writeMethodCompilationList(writer, compilationMethodName);
        writer.decreaseIndent();
    }

    /**
     * Writes the full description of the method. Includes {@link #writeHeaderAndCompilationList a
     * header and the list of compilations} and optimization trees for each compilation.
     *
     * @param writer the destination writer
     * @param experiment1 the first experiment
     * @param experiment2 the second experiment
     */
    public void write(Writer writer, Experiment experiment1, Experiment experiment2) {
        writeHeaderAndCompilationList(writer, experiment1, experiment2);
        writer.increaseIndent();
        for (ExecutedMethod method : executedMethods) {
            writer.writeln("Compilation " + method.getCompilationId() + " (" + method.createSummaryOfMethodExecution() + ") in experiment " + method.getExperiment().getExperimentId());
            writer.increaseIndent();
            method.getRootPhase().writeRecursive(writer);
            writer.decreaseIndent();
        }
        writer.decreaseIndent();
    }
}
