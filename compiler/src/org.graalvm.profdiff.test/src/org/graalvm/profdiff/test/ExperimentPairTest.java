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
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.profdiff.core.CompilationUnit;
import org.graalvm.profdiff.core.Experiment;
import org.graalvm.profdiff.core.ExperimentId;
import org.graalvm.profdiff.core.pair.CompilationUnitPair;
import org.graalvm.profdiff.core.pair.ExperimentPair;
import org.graalvm.profdiff.core.pair.MethodPair;
import org.junit.Test;

public class ExperimentPairTest {
    private static <T> List<T> asList(Iterable<T> iterable) {
        List<T> list = new ArrayList<>();
        for (T elem : iterable) {
            list.add(elem);
        }
        return list;
    }

    @Test
    public void methodMatching() {
        Experiment experiment1 = new Experiment(ExperimentId.ONE, Experiment.CompilationKind.JIT);
        CompilationUnit bar1 = experiment1.addCompilationUnit("bar", "bar1", 0, null);
        experiment1.addCompilationUnit("foo", "foo1", 1, null);
        CompilationUnit foo2 = experiment1.addCompilationUnit("foo", "foo2", 2, null);
        CompilationUnit foo3 = experiment1.addCompilationUnit("foo", "foo3", 3, null);

        Experiment experiment2 = new Experiment(ExperimentId.TWO, Experiment.CompilationKind.JIT);
        CompilationUnit foo4 = experiment2.addCompilationUnit("foo", "foo4", 0, null);
        CompilationUnit bar2 = experiment2.addCompilationUnit("bar", "bar2", 10, null);
        CompilationUnit baz1 = experiment2.addCompilationUnit("baz", "baz1", 20, null);

        ExperimentPair pair = new ExperimentPair(experiment1, experiment2);
        List<MethodPair> methodPairs = asList(pair.getHotMethodPairsByDescendingPeriod());

        // only hot methods should be considered, therefore we expect zero pairs
        assertEquals(0, methodPairs.size());

        foo2.setHot(true);
        foo3.setHot(true);
        foo4.setHot(true);
        bar1.setHot(true);
        bar2.setHot(true);
        baz1.setHot(true);

        // expected result:
        // baz: baz1 unpaired
        // bar: bar1 matched with bar2
        // foo: foo3 matched with foo4, foo2 unpaired
        methodPairs = asList(pair.getHotMethodPairsByDescendingPeriod());
        assertEquals(3, methodPairs.size());

        List<CompilationUnitPair> bazCompilations = asList(methodPairs.get(0).getHotCompilationUnitPairsByDescendingPeriod());
        assertEquals(1, bazCompilations.size());
        assertNull(bazCompilations.get(0).getCompilationUnit1());
        assertEquals(baz1, bazCompilations.get(0).getCompilationUnit2());

        List<CompilationUnitPair> barCompilations = asList(methodPairs.get(1).getHotCompilationUnitPairsByDescendingPeriod());
        assertEquals(1, barCompilations.size());
        assertEquals(bar1, barCompilations.get(0).getCompilationUnit1());
        assertEquals(bar2, barCompilations.get(0).getCompilationUnit2());

        List<CompilationUnitPair> fooCompilations = asList(methodPairs.get(2).getHotCompilationUnitPairsByDescendingPeriod());
        assertEquals(2, fooCompilations.size());
        assertEquals(foo3, fooCompilations.get(0).getCompilationUnit1());
        assertEquals(foo4, fooCompilations.get(0).getCompilationUnit2());
        assertEquals(foo2, fooCompilations.get(1).getCompilationUnit1());
        assertNull(fooCompilations.get(1).getCompilationUnit2());
    }
}
