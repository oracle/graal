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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.graalvm.profdiff.util.Writer;

/**
 * Represents a named Java method, which may have been compiled by Graal several times. The class is
 * a container for the list of compilations of the method.
 */
public class Method {
    /**
     * The full signature of the compiled root method including parameter types as reported in the
     * optimization log.
     */
    private final String methodName;

    /**
     * The experiment to which compilation unit belongs.
     */
    private final Experiment experiment;

    /**
     * The list of Graal compilations of this method.
     */
    private final List<CompilationUnit> compilationUnits;

    /**
     * The sum of execution periods of the individual compilation units.
     */
    private long totalPeriod;

    /**
     * Constructs a compilation unit.
     *
     * @param methodName the name of the method
     * @param experiment the experiment to which the method belongs
     */
    public Method(String methodName, Experiment experiment) {
        compilationUnits = new ArrayList<>();
        this.methodName = methodName;
        this.experiment = experiment;
        totalPeriod = 0;
    }

    /**
     * Gets the experiment to which this compilation unit belongs.
     */
    public Experiment getExperiment() {
        return experiment;
    }

    /**
     * Gets the full signature of the root method of this compilation unit including parameter types
     * as reported in the optimization log.
     *
     * Example of a returned string:
     *
     * <pre>
     * java.lang.Math.max(int, int)
     * </pre>
     *
     * @return the compilation method name
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * Creates and adds a compilation unit to the method.
     *
     * @param compilationId the compilation ID of the compilation unit
     * @param treeLoader a loader of the compilation unit's optimization and inlining tree
     * @return the added compilation unit
     */
    public CompilationUnit addCompilationUnit(String compilationId,
                    long period, CompilationUnit.TreeLoader treeLoader) {
        CompilationUnit compilationUnit = new CompilationUnit(this, compilationId, period, treeLoader);
        compilationUnits.add(compilationUnit);
        totalPeriod += period;
        return compilationUnit;
    }

    /**
     * Gets the sum of execution periods of the method's compilation units.
     */
    public long getTotalPeriod() {
        return totalPeriod;
    }

    /**
     * Gets the list of compilation units of this method.
     */
    public List<CompilationUnit> getCompilationUnits() {
        return compilationUnits;
    }

    /**
     * Gets an iterable over hot compilation units of this method.
     */
    public Iterable<CompilationUnit> getHotCompilationUnits() {
        return () -> compilationUnits.stream().filter(CompilationUnit::isHot).iterator();
    }

    /**
     * Writes the list of compilations (including compilation ID, share of the execution and hotness
     * for each compilation) for a method with a header identifying this experiment. If there is no
     * {@link Experiment#isProfileAvailable() profile available}, information about execution is
     * omitted.
     *
     * @param writer the destination writer
     */
    public void writeCompilationList(Writer writer) {
        writer.writeln("In experiment " + experiment.getExperimentId());
        writer.increaseIndent();
        if (compilationUnits.isEmpty()) {
            writer.writeln("No compilations");
            writer.decreaseIndent();
            return;
        }
        writer.write(compilationUnits.size() + " compilations");
        if (experiment.isProfileAvailable()) {
            long hotMethodCount = compilationUnits.stream().filter(CompilationUnit::isHot).count();
            writer.writeln(" (" + hotMethodCount + " of which are hot)");
        } else {
            writer.writeln();
        }
        writer.writeln("Compilations");
        writer.increaseIndent();
        Iterable<CompilationUnit> sortedCompilationUnits = () -> compilationUnits.stream().sorted(Comparator.comparingLong(compilationUnit -> -compilationUnit.getPeriod())).iterator();
        for (CompilationUnit compilationUnit : sortedCompilationUnits) {
            writer.write(compilationUnit.getCompilationId());
            if (experiment.isProfileAvailable()) {
                writer.writeln(" (" + compilationUnit.createExecutionSummary() + ((compilationUnit.isHot()) ? ") *hot*" : ")"));
            } else {
                writer.writeln();
            }
        }
        writer.decreaseIndent(2);
    }

    /**
     * Returns {@code true} if the method contains at least one hot compilation unit.
     */
    public boolean isHot() {
        return getHotCompilationUnits().iterator().hasNext();
    }

    /**
     * Gets an iterable over hot compilation units of this method, sorted by the execution period
     * (descending).
     */
    public Iterable<CompilationUnit> getHotCompilationUnitsByDescendingPeriod() {
        return () -> compilationUnits.stream().filter(CompilationUnit::isHot).sorted(Comparator.comparingLong(unit -> -unit.getPeriod())).iterator();
    }
}
