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

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Pair;

/**
 * An experiment consisting of all graal-compiled methods and metadata. Additionally, this class
 * allows its {@link CompilationUnit executed methods} to be added incrementally. This is necessary
 * to be able to create an instance of the experiment first and then bind the {@link CompilationUnit
 * executed methods} with their {@link Experiment}.
 */
public class Experiment {
    /**
     * The kind of compilation of an experiment - JIT or AOT.
     */
    public enum CompilationKind {
        /**
         * Just-in-time compilation.
         */
        JIT,

        /**
         * Ahead-of-time compilation.
         */
        AOT
    }

    /**
     * The kind of compilation of this experiment, i.e., whether it was compiled just-in-time or
     * ahead-of-time. The field is {@code null} when it is unknown whether it is JIT or AOT. This
     * information is unknown when we are reporting a single experiment without profiles.
     */
    private final CompilationKind compilationKind;

    /**
     * The execution ID of this experiment. {@code null} if unknown.
     */
    private final String executionId;

    /**
     * The ID of this experiment.
     */
    private final ExperimentId experimentId;

    /**
     * {@code true} if proftool data of the experiment is available, i.e., {@link #totalPeriod} and
     * {@link CompilationUnit#getPeriod()} contain any useful information. The {@link #executionId}
     * is also sourced from proftool.
     */
    private final boolean profileAvailable;

    /**
     * The total period of all executed methods including non-graal executions.
     */
    private final long totalPeriod;

    /**
     * A cached sum of execution periods of the compilation units. Initially {@code null} and
     * computed on demand.
     */
    private Long graalPeriod;

    /**
     * All methods of the experiment, mapped by method name.
     */
    private final EconomicMap<String, Method> methods;

    /**
     * The list of all methods collected by proftool.
     */
    private final List<ProftoolMethod> proftoolMethods;

    /**
     * Constructs an experiment with an execution profile.
     *
     * @param executionId the execution ID of the experiment
     * @param experimentId the ID of the experiment
     * @param compilationKind the compilation kind of this experiment
     * @param totalPeriod the total period of all executed methods including non-graal executions
     * @param proftoolMethods the list of all methods collected by proftool
     */
    public Experiment(
                    String executionId,
                    ExperimentId experimentId,
                    CompilationKind compilationKind,
                    long totalPeriod,
                    List<ProftoolMethod> proftoolMethods) {
        this.compilationKind = compilationKind;
        this.executionId = executionId;
        this.experimentId = experimentId;
        profileAvailable = true;
        this.totalPeriod = totalPeriod;
        this.proftoolMethods = proftoolMethods;
        methods = EconomicMap.create();
    }

    /**
     * Constructs an experiment without execution profile.
     *
     * @param experimentId the ID of the experiment
     * @param compilationKind the compilation kind of this experiment
     */
    public Experiment(ExperimentId experimentId, CompilationKind compilationKind) {
        this.compilationKind = compilationKind;
        executionId = null;
        this.experimentId = experimentId;
        profileAvailable = false;
        totalPeriod = 0;
        proftoolMethods = List.of();
        methods = EconomicMap.create();
    }

    /**
     * Gets the experiment ID.
     */
    public ExperimentId getExperimentId() {
        return experimentId;
    }

    /**
     * Gets the execution ID or {@code null} if unknown.
     */
    public String getExecutionId() {
        return executionId;
    }

    /**
     * Gets the total period of all executed code including non-graal executions.
     *
     * @return the total period of execution
     */
    public long getTotalPeriod() {
        return totalPeriod;
    }

    /**
     * Gets the sum of the periods of all graal-compiled methods.
     *
     * @return the total period of graal-compiled methods
     */
    public long getGraalPeriod() {
        if (graalPeriod != null) {
            return graalPeriod;
        }
        graalPeriod = StreamSupport.stream(methods.getValues().spliterator(), false).mapToLong(Method::getTotalPeriod).sum();
        return graalPeriod;
    }

    /**
     * Gets all methods in the experiment, mapped by method name.
     */
    public EconomicMap<String, Method> getMethodsByName() {
        return methods;
    }

    /**
     * Returns whether proftool data is available to the experiment. If it is not, there is no
     * information about the period of execution of the experiment and individual compilation units.
     * We also do not have the {@link #getExecutionId() executionId}, which also comes from
     * proftool.
     *
     * @return {@code true} if proftool data is available
     */
    public boolean isProfileAvailable() {
        return profileAvailable;
    }

    /**
     * Gets the methods containing at least one hot compilation unit in the experiment, mapped by
     * method name.
     *
     * @return methods with a hot compilation unit, mapped by method name
     */
    public EconomicMap<String, Method> getHotMethodsByName() {
        EconomicMap<String, Method> map = EconomicMap.create();
        for (Method method : methods.getValues()) {
            if (method.getHotCompilationUnits().iterator().hasNext()) {
                map.put(method.getMethodName(), method);
            }
        }
        return map;
    }

    /**
     * Writes a summary of the experiment. Includes the number of methods collected (proftool and
     * optimization log), relative period of graal-compiled methods, the number and relative period
     * of hot methods and the list of top proftool methods. Execution statistics are omitted if the
     * profile is not available.
     *
     * @param writer the destination writer
     */
    public void writeExperimentSummary(Writer writer) {
        String graalExecutionPercent = String.format("%.2f", (double) getGraalPeriod() / totalPeriod * 100);
        String graalHotExecutionPercent = String.format("%.2f", (double) sumHotGraalPeriod() / totalPeriod * 100);
        writer.write("Experiment " + experimentId);
        if (executionId != null) {
            writer.write(" with execution ID " + executionId);
        }
        if (compilationKind != null) {
            writer.writeln(" (" + compilationKind + ")");
        } else {
            writer.writeln();
        }
        writer.increaseIndent();
        writer.writeln("Collected optimization logs for " + countCompilationUnits() + " compilation units");
        if (profileAvailable) {
            writer.writeln("Collected proftool data for " + proftoolMethods.size() + " compilation units");
            writer.writeln("Graal-compiled methods account for " + graalExecutionPercent + "% of execution");
            writer.writeln(countHotCompilationUnits() + " hot compilation units account for " + graalHotExecutionPercent + "% of execution");
            writer.writeln(String.format("%.2f billion cycles total", (double) totalPeriod / ProftoolMethod.BILLION));
            writer.writeln("Top methods");
            writer.increaseIndent();
            writer.writeln("Execution     Cycles  Level      ID  Method");
            Iterable<ProftoolMethod> topMethods = () -> proftoolMethods.stream().sorted((method1, method2) -> Long.compare(method2.getPeriod(), method1.getPeriod())).limit(10).iterator();
            for (ProftoolMethod method : topMethods) {
                double execution = (double) method.getPeriod() / totalPeriod * 100;
                double cycles = (double) method.getPeriod() / ProftoolMethod.BILLION;
                String level = Objects.toString(method.getLevel(), "");
                String compilationId = Objects.toString(method.getCompilationId(), "");
                writer.writeln(String.format("%8.2f%% %10.2f %6s %7s  %s", execution, cycles, level, compilationId, method.getName()));
            }
            writer.decreaseIndent();
        }
        writer.decreaseIndent();
    }

    /**
     * Counts the compilation units in the experiment.
     *
     * @return the number of compilation units in the experiment
     */
    private long countCompilationUnits() {
        return StreamSupport.stream(methods.getValues().spliterator(), false).mapToLong(method -> method.getCompilationUnits().size()).sum();
    }

    /**
     * Counts hot compilation units in the experiment.
     *
     * @return the number of hot compilation units in the experiment
     */
    private long countHotCompilationUnits() {
        return StreamSupport.stream(getCompilationUnits().spliterator(), false).filter(CompilationUnit::isHot).count();
    }

    /**
     * Sums up the execution period of hot Graal compilation units.
     *
     * @return the total execution period of hot Graal execution units
     */
    private long sumHotGraalPeriod() {
        return StreamSupport.stream(getCompilationUnits().spliterator(), false).filter(CompilationUnit::isHot).mapToLong(CompilationUnit::getPeriod).sum();
    }

    /**
     * Gets a method by name, or creates it and adds it to the experiment if it does not exist.
     *
     * @param methodName the name of the method
     * @return the method with the given name
     */
    public Method getMethodOrCreate(String methodName) {
        Method method = methods.get(methodName);
        if (method == null) {
            method = new Method(methodName, this);
            methods.put(methodName, method);
        }
        return method;
    }

    /**
     * Creates and adds a compilation unit to this experiment. Creates a {@link Method} (if
     * necessary) and adds the compilation unit to the method.
     *
     * @param multiMethodName the name of the root method of the compilation unit (including a
     *            multi-method key if applicable)
     * @param compilationId compilation ID of the compilation unit
     * @param period the number of cycles spent executing the method (collected by proftool)
     * @param treeLoader a loader of the compilation unit's optimization and inlining tree
     * @return the added compilation unit
     */
    public CompilationUnit addCompilationUnit(String multiMethodName, String compilationId, long period, CompilationUnit.TreeLoader treeLoader) {
        graalPeriod = null;
        Pair<String, String> splitName = Method.splitMultiMethodName(multiMethodName);
        return getMethodOrCreate(splitName.getLeft()).addCompilationUnit(compilationId, period, treeLoader, splitName.getRight());
    }

    /**
     * Gets an iterable over all compilation units of this experiment.
     */
    public Iterable<CompilationUnit> getCompilationUnits() {
        Iterator<Method> methodIterator = methods.getValues().iterator();
        return () -> new Iterator<>() {
            private Iterator<CompilationUnit> compilationUnitIterator = null;

            @Override
            public boolean hasNext() {
                skipMethodsWithoutCompilationUnits();
                return compilationUnitIterator != null && compilationUnitIterator.hasNext();
            }

            @Override
            public CompilationUnit next() {
                skipMethodsWithoutCompilationUnits();
                return compilationUnitIterator.next();
            }

            private void skipMethodsWithoutCompilationUnits() {
                while (methodIterator.hasNext() && (compilationUnitIterator == null || !compilationUnitIterator.hasNext())) {
                    compilationUnitIterator = methodIterator.next().getCompilationUnits().iterator();
                }
            }
        };
    }

    /**
     * Gets an iterable over methods sorted by the execution period, with the greatest period first.
     */
    public Iterable<Method> getMethodsByDescendingPeriod() {
        return () -> StreamSupport.stream(methods.getValues().spliterator(), false).sorted(Comparator.comparingLong(method -> -method.getTotalPeriod())).iterator();
    }
}
