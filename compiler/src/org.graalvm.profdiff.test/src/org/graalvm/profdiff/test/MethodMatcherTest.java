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
package org.graalvm.profdiff.test;

import static org.junit.Assert.assertEquals;

import org.graalvm.profdiff.core.CompilationUnit;
import org.graalvm.profdiff.core.ExperimentId;
import org.graalvm.profdiff.core.Experiment;
import org.graalvm.profdiff.core.optimization.OptimizationPhase;
import org.graalvm.profdiff.matching.method.GreedyMethodMatcher;
import org.graalvm.profdiff.matching.method.MatchedMethod;
import org.graalvm.profdiff.matching.method.MethodMatcher;
import org.graalvm.profdiff.matching.method.MethodMatching;
import org.junit.Test;

public class MethodMatcherTest {
    @Test
    public void testGreedyMethodMatcher() {
        OptimizationPhase rootPhase = new OptimizationPhase("RootPhase");
        Experiment experiment1 = new Experiment("1", ExperimentId.ONE, 100, 100);
        CompilationUnit foo1 = new CompilationUnit("foo1", "foo", rootPhase, 1, experiment1);
        CompilationUnit foo2 = new CompilationUnit("foo2", "foo", rootPhase, 2, experiment1);
        CompilationUnit foo3 = new CompilationUnit("foo3", "foo", rootPhase, 3, experiment1);
        CompilationUnit bar1 = new CompilationUnit("bar1", "bar", rootPhase, 3, experiment1);
        experiment1.addCompilationUnit(foo1);
        experiment1.addCompilationUnit(foo2);
        experiment1.addCompilationUnit(foo3);
        experiment1.addCompilationUnit(bar1);

        Experiment experiment2 = new Experiment("2", ExperimentId.TWO, 100, 100);
        CompilationUnit foo4 = new CompilationUnit("foo4", "foo", rootPhase, 1, experiment2);
        CompilationUnit bar2 = new CompilationUnit("bar2", "bar", rootPhase, 2, experiment2);
        CompilationUnit baz1 = new CompilationUnit("baz1", "baz", rootPhase, 3, experiment2);
        experiment2.addCompilationUnit(foo4);
        experiment2.addCompilationUnit(bar2);
        experiment2.addCompilationUnit(baz1);

        MethodMatcher matcher = new GreedyMethodMatcher();
        MethodMatching matching = matcher.match(experiment1, experiment2);

        // only hot methods should be considered, therefore we expect zero matches and no extra
        // methods
        assertEquals(0, matching.getMatchedMethods().size());
        assertEquals(0, matching.getUnmatchedMethods().size());

        foo2.setHot(true);
        foo3.setHot(true);
        foo4.setHot(true);
        bar2.setHot(true);
        baz1.setHot(true);
        matching = matcher.match(experiment1, experiment2);

        // expected result:
        // foo: foo3 matched with foo4, foo2 extra
        // bar: bar2 extra
        // baz: baz extra
        assertEquals(1, matching.getMatchedMethods().size());
        MatchedMethod matchedFoo = matching.getMatchedMethods().get(0);
        assertEquals("foo", matchedFoo.getCompilationMethodName());
        assertEquals(1, matchedFoo.getMatchedCompilationUnits().size());
        assertEquals("foo3", matchedFoo.getMatchedCompilationUnits().get(0).getFirstCompilationUnit().getCompilationId());
        assertEquals("foo4", matchedFoo.getMatchedCompilationUnits().get(0).getSecondCompilationUnit().getCompilationId());
        assertEquals(1, matchedFoo.getUnmatchedCompilationUnits().size());
        assertEquals("foo2", matchedFoo.getUnmatchedCompilationUnits().get(0).getCompilationId());
        assertEquals(2, matching.getUnmatchedMethods().size());
    }
}
