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

import org.graalvm.bisect.core.ExecutedMethod;
import org.graalvm.bisect.core.Experiment;
import org.graalvm.bisect.util.Writer;

/**
 * Represents one pair of methods and a matching between their respective compilations.
 */
public class MatchedMethod {
    /**
     * Gets the signature of the matched methods. Both methods must have the same signature.
     *
     * @return the compilation method name
     * @see ExecutedMethod#getCompilationMethodName()
     */
    public String getCompilationMethodName() {
        return compilationMethodName;
    }

    /**
     * Gets the list of pairs of matched compilations of this method.
     *
     * @return the list of pairs of matched compilations
     */
    public List<MatchedExecutedMethod> getMatchedExecutedMethods() {
        return matchedExecutedMethods;
    }

    /**
     * Gets the list of compilations of this method that do not have a match.
     *
     * @return the list of compilations of this method that were not matched
     */
    public List<ExtraExecutedMethod> getExtraExecutedMethods() {
        return extraExecutedMethods;
    }

    private final String compilationMethodName;
    private final ArrayList<MatchedExecutedMethod> matchedExecutedMethods = new ArrayList<>();
    private final ArrayList<ExtraExecutedMethod> extraExecutedMethods = new ArrayList<>();

    MatchedMethod(String compilationMethodName) {
        this.compilationMethodName = compilationMethodName;
    }

    public MatchedExecutedMethod addMatchedExecutedMethod(ExecutedMethod method1, ExecutedMethod method2) {
        MatchedExecutedMethod matchedExecutedMethod = new MatchedExecutedMethod(method1, method2);
        matchedExecutedMethods.add(matchedExecutedMethod);
        return matchedExecutedMethod;
    }

    public ExtraExecutedMethod addExtraExecutedMethod(ExecutedMethod method) {
        ExtraExecutedMethod extraExecutedMethod = new ExtraExecutedMethod(method);
        extraExecutedMethods.add(extraExecutedMethod);
        return extraExecutedMethod;
    }

    /**
     * Writes the name of the method and the list of compilations for each experiment.
     *
     * @param writer the destination writer
     * @param experiment1 the first experiment
     * @param experiment2 the second experiment
     */
    public void writeHeaderAndCompilationLists(Writer writer, Experiment experiment1, Experiment experiment2) {
        writer.writeln("Method " + compilationMethodName);
        writer.increaseIndent();
        experiment1.writeMethodCompilationList(writer, compilationMethodName);
        experiment2.writeMethodCompilationList(writer, compilationMethodName);
        writer.decreaseIndent();
    }

    /**
     * Writes the header and the optimization tree for each extra (unpaired) executed method.
     *
     * @param writer the destination writer
     */
    public void writeExtraExecutedMethods(Writer writer) {
        for (ExtraExecutedMethod extraExecutedMethod : extraExecutedMethods) {
            extraExecutedMethod.writeHeader(writer);
            writer.increaseIndent();
            extraExecutedMethod.getExecutedMethod().getRootPhase().writeRecursive(writer);
            writer.decreaseIndent();
        }
    }
}
