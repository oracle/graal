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

import java.util.Set;

import org.graalvm.bisect.core.ExecutedMethod;
import org.graalvm.bisect.core.ExecutedMethodImpl;
import org.graalvm.bisect.core.ExperimentId;
import org.graalvm.bisect.core.ExperimentImpl;
import org.graalvm.bisect.core.HotMethodPolicy;
import org.graalvm.bisect.core.optimization.OptimizationPhase;
import org.graalvm.bisect.core.optimization.OptimizationPhaseImpl;
import org.junit.Test;

public class HotMethodPolicyTest {
    @Test
    public void testHotMethodPolicy() {
        OptimizationPhase rootPhase = new OptimizationPhaseImpl("RootPhase");
        ExperimentImpl experiment = new ExperimentImpl("1", ExperimentId.ONE, 100, 100);
        experiment.addExecutedMethod(new ExecutedMethodImpl("foo1", "foo", rootPhase, 5, experiment));
        experiment.addExecutedMethod(new ExecutedMethodImpl("foo2", "foo", rootPhase, 35, experiment));
        experiment.addExecutedMethod(new ExecutedMethodImpl("foo3", "foo", rootPhase, 30, experiment));
        experiment.addExecutedMethod(new ExecutedMethodImpl("bar1", "bar", rootPhase, 20, experiment));
        experiment.addExecutedMethod(new ExecutedMethodImpl("baz1", "bar", rootPhase, 10, experiment));

        HotMethodPolicy hotMethodPolicy = new HotMethodPolicy();
        hotMethodPolicy.markHotMethods(experiment);

        hotMethodPolicy.setHotMethodMinLimit(1);
        hotMethodPolicy.setHotMethodMaxLimit(10);
        hotMethodPolicy.setHotMethodPercentile(0.9);

        Set<String> hotMethods = Set.of("foo2", "foo3", "bar1");
        for (ExecutedMethod method : experiment.getExecutedMethods()) {
            assertEquals(hotMethods.contains(method.getCompilationId()), method.isHot());
        }
    }
}
