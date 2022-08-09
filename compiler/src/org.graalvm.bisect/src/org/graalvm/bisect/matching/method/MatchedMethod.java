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

import java.util.ArrayList;
import java.util.List;

import org.graalvm.bisect.core.CompilationUnit;
import org.graalvm.bisect.core.Experiment;
import org.graalvm.bisect.util.Writer;

/**
 * Represents one pair of methods and a matching between their respective compilations.
 */
public class MatchedMethod {
    /**
     * The full signature of the matched methods (they must have the same signature).
     */
    private final String compilationMethodName;

    /**
     * Hot compilation units of the method in the first experiment.
     */
    private final List<CompilationUnit> compilationUnits1;

    /**
     * Hot compilation units of the method in the second experiment.
     */
    private final List<CompilationUnit> compilationUnits2;

    /**
     * Pairs of matched compilation units of this method.
     */
    private final List<MatchedCompilationUnit> matchedCompilationUnits = new ArrayList<>();

    /**
     * Unmatched compilation units of this method.
     */
    private final List<UnmatchedCompilationUnit> unmatchedCompilationUnits = new ArrayList<>();

    /**
     * Constructs a matched method.
     *
     * @param compilationMethodName full signature of the matched methods
     * @param compilationUnits1 hot compilation units of the method in the first experiment
     * @param compilationUnits2 hot compilation units of the method in the second experiment
     */
    MatchedMethod(String compilationMethodName, List<CompilationUnit> compilationUnits1, List<CompilationUnit> compilationUnits2) {
        this.compilationMethodName = compilationMethodName;
        this.compilationUnits1 = compilationUnits1;
        this.compilationUnits2 = compilationUnits2;
    }

    /**
     * Gets the full signature of the matched methods (they must have the same signature).
     *
     * @return the compilation method name
     * @see CompilationUnit#getCompilationMethodName()
     */
    public String getCompilationMethodName() {
        return compilationMethodName;
    }

    /**
     * Gets the list of pairs of matched compilations of this method.
     */
    public List<MatchedCompilationUnit> getMatchedCompilationUnits() {
        return matchedCompilationUnits;
    }

    /**
     * Gets the list of compilations of this method that do not have a match.
     */
    public List<UnmatchedCompilationUnit> getUnmatchedCompilationUnits() {
        return unmatchedCompilationUnits;
    }

    public MatchedCompilationUnit addMatchedCompilationUnit(CompilationUnit compilationUnit1, CompilationUnit compilationUnit2) {
        MatchedCompilationUnit matchedCompilationUnit = new MatchedCompilationUnit(compilationUnit1, compilationUnit2);
        matchedCompilationUnits.add(matchedCompilationUnit);
        return matchedCompilationUnit;
    }

    public UnmatchedCompilationUnit addUnmatchedCompilationUnit(CompilationUnit compilationUnit) {
        UnmatchedCompilationUnit unmatchedCompilationUnit = new UnmatchedCompilationUnit(compilationUnit);
        unmatchedCompilationUnits.add(unmatchedCompilationUnit);
        return unmatchedCompilationUnit;
    }

    /**
     * Writes the name of the method and the list of compilations unit for each experiment.
     *
     * @param writer the destination writer
     * @param experiment1 the first experiment
     * @param experiment2 the second experiment
     */
    public void writeHeaderAndCompilationUnits(Writer writer, Experiment experiment1, Experiment experiment2) {
        writer.writeln("Method " + compilationMethodName);
        writer.increaseIndent();
        experiment1.writeCompilationUnits(writer, compilationMethodName);
        experiment2.writeCompilationUnits(writer, compilationMethodName);
        writer.decreaseIndent();
    }

    /**
     * Writes the header and the optimization tree for each unmatched compilation unit.
     *
     * @param writer the destination writer
     */
    public void writeUnmatchedCompilationUnits(Writer writer) {
        for (UnmatchedCompilationUnit unmatchedCompilationUnit : unmatchedCompilationUnits) {
            unmatchedCompilationUnit.writeHeader(writer);
            writer.increaseIndent();
            unmatchedCompilationUnit.getExecutedMethod().getRootPhase().writeRecursive(writer);
            writer.decreaseIndent();
        }
    }

    /**
     * Writes all hot compilation units including optimization trees from both experiments to the
     * destination writer.
     *
     * @param writer the destination writer
     */
    public void writeAllHotCompilationUnits(Writer writer) {
        for (CompilationUnit compilationUnit : compilationUnits1) {
            compilationUnit.write(writer);
        }
        for (CompilationUnit compilationUnit : compilationUnits2) {
            compilationUnit.write(writer);
        }
    }
}
