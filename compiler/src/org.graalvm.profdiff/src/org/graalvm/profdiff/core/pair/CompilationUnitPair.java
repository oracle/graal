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
import org.graalvm.profdiff.core.Writer;

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
        this.compilationUnit1 = compilationUnit1;
        this.compilationUnit2 = compilationUnit2;
        if (compilationUnit1 != null && compilationUnit2 != null && !compilationUnit1.getMethod().getMethodName().equals(compilationUnit2.getMethod().getMethodName())) {
            throw new IllegalArgumentException("The compilation units must be linked to the same method.");
        }
        if (compilationUnit1 == null && compilationUnit2 == null) {
            throw new IllegalArgumentException("At least one of the compilation units must not be null.");
        }
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
     * Writes the headers of hot compilations units (zero, one, or two) to the destination writer.
     *
     * @param writer the destination writer
     */
    public void writeHeadersForHotCompilations(Writer writer) {
        if (bothHot()) {
            writer.write(compilationUnit1.toString());
            writer.writeln(" vs");
            writer.writeln(compilationUnit2.toString());
        } else if (someHot()) {
            CompilationUnit hotCompilationUnit = compilationUnit1 != null && compilationUnit1.isHot() ? compilationUnit1 : compilationUnit2;
            writer.writeln(hotCompilationUnit.toString());
        }
    }
}
