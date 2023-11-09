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

import org.graalvm.collections.Pair;
import jdk.graal.compiler.java.StableMethodNameFormatter;
import org.graalvm.profdiff.core.inlining.InliningPath;
import org.graalvm.profdiff.core.inlining.InliningTreeNode;
import org.graalvm.profdiff.core.optimization.Optimization;

/**
 * Represents a named Java method, which may have been compiled by Graal several times. The class is
 * a container for the list of compilations of the method.
 *
 * Native Image can create multi-methods, i.e., specialized variants of methods for different
 * compilation scenarios. Each multi-method is associated with a multi-method key. This class
 * represents a method rather than a multi-method. As a consequence, the name of this method does
 * not contain a multi-method key. Instead, this instance comprises all multi-methods created from
 * this method.
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
     * Constructs a method.
     *
     * @param methodName the name of the method
     * @param experiment the experiment to which the method belongs
     */
    public Method(String methodName, Experiment experiment) {
        if (methodName.contains(StableMethodNameFormatter.MULTI_METHOD_KEY_SEPARATOR)) {
            throw new IllegalArgumentException("The provided argument is a multi-method name: " + methodName);
        }
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
     * @param multiMethodKey the multi-method key if it is a compilation of a multi-method,
     *            otherwise {@code null}
     * @return the added compilation unit
     */
    public CompilationUnit addCompilationUnit(String compilationId, long period, CompilationUnit.TreeLoader treeLoader, String multiMethodKey) {
        CompilationUnit compilationUnit = new CompilationUnit(this, compilationId, period, treeLoader, multiMethodKey);
        compilationUnits.add(compilationUnit);
        totalPeriod += period;
        return compilationUnit;
    }

    /**
     * Creates and adds a compilation fragment to the method.
     *
     * @param parentCompilationUnit the parent compilation unit of the fragment
     * @param rootNode the root inlining node of the compilation fragment
     * @return the added compilation fragment
     */
    public CompilationFragment addCompilationFragment(CompilationUnit parentCompilationUnit, InliningTreeNode rootNode) {
        CompilationFragment compilationFragment = new CompilationFragment(this, parentCompilationUnit, rootNode);
        compilationUnits.add(compilationFragment);
        return compilationFragment;
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
     * Gets an iterable over {@link CompilationFragment the compilation fragments} of this method.
     */
    public Iterable<CompilationFragment> getCompilationFragments() {
        return () -> compilationUnits.stream().filter(compilation -> compilation instanceof CompilationFragment).map(compilation -> (CompilationFragment) compilation).iterator();
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
            writer.write(String.format("%5s", compilationUnit.getCompilationId()));
            if (compilationUnit.getMultiMethodKey() != null) {
                writer.write(" of multi-method ");
                writer.write(compilationUnit.getMultiMethodKey());
            }
            if (experiment.isProfileAvailable()) {
                writer.write(" consumed ");
                writer.write(compilationUnit.createExecutionSummary());
                if (compilationUnit.isHot()) {
                    writer.write(" *hot*");
                }
            }
            writer.writeln();
            if (compilationUnit instanceof CompilationFragment fragment) {
                boolean first = true;
                for (InliningPath.PathElement element : fragment.getPathFromRoot().elements()) {
                    writer.write(first ? "  |_ a fragment of " : "                   ");
                    writer.write(element.methodName());
                    if (element.callsiteBCI() == Optimization.UNKNOWN_BCI) {
                        writer.writeln();
                    } else {
                        writer.write(" at bci ");
                        writer.writeln(Integer.toString(element.callsiteBCI()));
                    }
                    first = false;
                }
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

    /**
     * Splits the given multi-method name into a method name and multi-method key.
     *
     * For example, if the argument is {@code java.util.HashMap.size%%key()}, this method returns
     * {@code java.util.HashMap.size()} and {@code key}.
     *
     * If the provided method name does not contain a multi-method key, {@code null} is returned in
     * place of the multi-method key.
     *
     * @param multiMethodName a multi-method name
     * @return the method name and multi-method key
     */
    public static Pair<String, String> splitMultiMethodName(String multiMethodName) {
        int separatorBegin = multiMethodName.indexOf(StableMethodNameFormatter.MULTI_METHOD_KEY_SEPARATOR);
        if (separatorBegin == -1) {
            return Pair.create(multiMethodName, null);
        }
        int keyBegin = separatorBegin + StableMethodNameFormatter.MULTI_METHOD_KEY_SEPARATOR.length();
        int keyEnd = multiMethodName.lastIndexOf('(');
        if (keyEnd == -1 || keyBegin > keyEnd) {
            throw new IllegalArgumentException("Malformed multi-method name: " + multiMethodName);
        }
        String methodName = multiMethodName.substring(0, separatorBegin) + multiMethodName.substring(keyEnd);
        String multiMethodKey = multiMethodName.substring(keyBegin, keyEnd);
        return Pair.create(methodName, multiMethodKey);
    }

    /**
     * Returns a method name (removing the multi-method key) from the given multi-method name.
     *
     * @param multiMethodName the name of a multi method (optionally containing a multi-method key)
     * @return method name corresponding to the given multi-method
     */
    public static String removeMultiMethodKey(String multiMethodName) {
        return splitMultiMethodName(multiMethodName).getLeft();
    }
}
