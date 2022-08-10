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

import java.util.ArrayList;
import java.util.List;

import org.graalvm.profdiff.core.CompilationUnit;
import org.graalvm.profdiff.core.Experiment;

/**
 * Represents a matching between methods of two experiments and the matching of their respective
 * compilations. The matching is built incrementally by adding matched/unmatched methods.
 */
public class MethodMatching {
    private final ArrayList<MatchedMethod> matchedMethods = new ArrayList<>();

    private final ArrayList<UnmatchedMethod> unmatchedMethods = new ArrayList<>();

    /**
     * Adds a matched method to this matching.
     *
     * @param compilationMethodName the compilation method name of the newly added method
     * @param compilationUnits1 hot compilation units of the method in the first experiment
     * @param compilationUnits2 hot compilation units of the method in the second experiment
     * @return the added matched method
     */
    public MatchedMethod addMatchedMethod(String compilationMethodName, List<CompilationUnit> compilationUnits1, List<CompilationUnit> compilationUnits2) {
        MatchedMethod matchedMethod = new MatchedMethod(compilationMethodName, compilationUnits1, compilationUnits2);
        matchedMethods.add(matchedMethod);
        return matchedMethod;
    }

    /**
     * Gets the list of pairs of matched methods, each of which holds a matching of its
     * compilations.
     *
     * @return the list of matched methods
     */
    public List<MatchedMethod> getMatchedMethods() {
        return matchedMethods;
    }

    /**
     * Adds an unmatched method to this matching.
     *
     * @param compilationMethodName the compilation method name of the unmatched method to be added
     * @param experiment the experiment to which the unmatched method belongs
     * @param compilationUnits the compilation units of the method in its {@code experiment}
     */
    public void addUnmatchedMethod(String compilationMethodName, Experiment experiment, List<CompilationUnit> compilationUnits) {
        UnmatchedMethod unmatchedMethod = new UnmatchedMethod(experiment, compilationMethodName, compilationUnits);
        unmatchedMethods.add(unmatchedMethod);
    }

    /**
     * Gets the list of the methods that do not have a pair.
     *
     * @return the list of methods without a pair
     */
    public List<UnmatchedMethod> getUnmatchedMethods() {
        return unmatchedMethods;
    }
}
