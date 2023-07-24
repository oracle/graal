/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.profdiff.test;

import org.graalvm.profdiff.core.CompilationUnit;
import org.graalvm.profdiff.core.Experiment;
import org.graalvm.profdiff.core.ExperimentId;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class CompilationUnitTest {
    @Test
    public void stringRepresentation() {
        Experiment experiment1 = new Experiment(ExperimentId.ONE, Experiment.CompilationKind.JIT);
        CompilationUnit cu1 = experiment1.addCompilationUnit("foo.Bar()", "10", 0, null);
        assertEquals("Compilation unit    10 in experiment 1", cu1.toString());
        CompilationUnit cu2 = experiment1.addCompilationUnit("foo.Bar%%Baz()", "200", 0, null);
        assertEquals("Compilation unit   200 of multi-method Baz in experiment 1", cu2.toString());
        Experiment experiment2 = new Experiment("1000", ExperimentId.TWO, Experiment.CompilationKind.JIT, 100, List.of());
        CompilationUnit cu3 = experiment2.addCompilationUnit("foo.Bar()", "3000", 20, null);
        assertEquals("Compilation unit  3000 consumed 100.00% of Graal execution, 20.00% of total in experiment 2", cu3.toString());
        CompilationUnit cu4 = experiment2.addCompilationUnit("foo.Bar%%Baz()", "40000", 20, null);
        assertEquals("Compilation unit 40000 of multi-method Baz consumed 50.00% of Graal execution, 20.00% of total in experiment 2", cu4.toString());
    }
}
