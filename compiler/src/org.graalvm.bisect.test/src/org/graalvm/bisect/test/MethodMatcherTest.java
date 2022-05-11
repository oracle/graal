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
package org.graalvm.bisect.test;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.graalvm.bisect.core.optimization.OptimizationPhase;
import org.graalvm.bisect.core.optimization.OptimizationPhaseImpl;
import org.graalvm.bisect.matching.method.MethodMatcher;
import org.graalvm.bisect.matching.method.MethodMatching;
import org.graalvm.bisect.matching.method.GreedyMethodMatcher;
import org.graalvm.bisect.core.ExecutedMethod;
import org.graalvm.bisect.core.ExecutedMethodImpl;
import org.graalvm.bisect.core.Experiment;
import org.graalvm.bisect.core.ExperimentId;
import org.graalvm.bisect.core.ExperimentImpl;
import org.junit.Test;

public class MethodMatcherTest {
    @Test
    public void testGreedyMethodMatcher() {
        OptimizationPhase rootPhase = new OptimizationPhaseImpl("RootPhase");
        ExecutedMethod foo1 = new ExecutedMethodImpl("foo1", "foo", rootPhase,1);
        ExecutedMethod foo2 = new ExecutedMethodImpl("foo2", "foo", rootPhase,2);
        ExecutedMethod foo3 = new ExecutedMethodImpl("foo3", "foo", rootPhase,3);
        ExecutedMethod bar1 = new ExecutedMethodImpl("bar1", "bar", rootPhase,3);
        List<ExecutedMethod> methods1 = List.of(foo1, foo2, foo3, bar1);
        Experiment experiment1 = new ExperimentImpl(methods1, "1", ExperimentId.ONE, 100, 100);

        ExecutedMethod foo4 = new ExecutedMethodImpl("foo4", "foo", rootPhase,1);
        ExecutedMethod bar2 = new ExecutedMethodImpl("bar2", "bar", rootPhase,2);
        ExecutedMethod baz1 = new ExecutedMethodImpl("baz1", "baz", rootPhase, 3);
        List<ExecutedMethod> methods2 = List.of(foo4, bar2, baz1);
        Experiment experiment2 = new ExperimentImpl(methods2, "2", ExperimentId.TWO, 100, 100);

        MethodMatcher matcher = new GreedyMethodMatcher();
        MethodMatching matching = matcher.match(experiment1, experiment2);

        // only hot methods should be considered, therefore we expect zero matches and no extra methods
        assertEquals(0, matching.getMatchedMethods().size());
        assertEquals(0, matching.getExtraMethods().size());

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
        MethodMatching.MatchedMethod matchedFoo = matching.getMatchedMethods().get(0);
        assertEquals("foo", matchedFoo.getCompilationMethodName());
        assertEquals(1, matchedFoo.getMatchedExecutedMethods().size());
        assertEquals("foo3", matchedFoo.getMatchedExecutedMethods().get(0).getMethod1().getCompilationId());
        assertEquals("foo4", matchedFoo.getMatchedExecutedMethods().get(0).getMethod2().getCompilationId());
        assertEquals(1, matchedFoo.getExtraExecutedMethods().size());
        assertEquals("foo2", matchedFoo.getExtraExecutedMethods().get(0).getExecutedMethod().getCompilationId());
        assertEquals(2, matching.getExtraMethods().size());
    }
}
